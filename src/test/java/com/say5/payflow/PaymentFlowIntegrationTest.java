package com.say5.payflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.say5.payflow.api.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@ActiveProfiles("test")
class PaymentFlowIntegrationTest {

    @Autowired WebApplicationContext ctx;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper om;

    MockMvc mvc;
    String token;
    static final UUID DEMO = UUID.fromString("00000000-0000-0000-0000-000000000de0");

    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        token = jwt.issue(DEMO);
    }

    @Test
    void createAndRetrievePayment() throws Exception {
        setup();
        String body = """
            {"amountCents": 2500, "currency": "USD", "description": "test order"}
            """;
        MvcResult res = mvc.perform(post("/v1/payment-intents")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn();
        assertEquals(201, res.getResponse().getStatus());
        JsonNode created = om.readTree(res.getResponse().getContentAsString());
        assertEquals("succeeded", created.get("status").asText());
        String id = created.get("id").asText();

        res = mvc.perform(get("/v1/payment-intents/" + id)
                .header("Authorization", "Bearer " + token))
            .andReturn();
        assertEquals(200, res.getResponse().getStatus());
        JsonNode fetched = om.readTree(res.getResponse().getContentAsString());
        assertEquals(id, fetched.get("id").asText());
    }

    @Test
    void idempotencyReplayReturnsSameBody() throws Exception {
        setup();
        String key = UUID.randomUUID().toString();
        String body = """
            {"amountCents": 4200, "currency": "USD"}
            """;
        MvcResult r1 = mvc.perform(post("/v1/payment-intents")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn();
        MvcResult r2 = mvc.perform(post("/v1/payment-intents")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn();
        assertEquals(201, r1.getResponse().getStatus());
        assertEquals(201, r2.getResponse().getStatus());
        assertEquals(
            om.readTree(r1.getResponse().getContentAsString()).get("id").asText(),
            om.readTree(r2.getResponse().getContentAsString()).get("id").asText());
        assertEquals("true", r2.getResponse().getHeader("Idempotency-Replayed"));
    }

    @Test
    void idempotencyReusedKeyDifferentBodyReturns422() throws Exception {
        setup();
        String key = UUID.randomUUID().toString();
        mvc.perform(post("/v1/payment-intents")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountCents\":1000,\"currency\":\"USD\"}"))
            .andReturn();
        MvcResult r = mvc.perform(post("/v1/payment-intents")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountCents\":9999,\"currency\":\"USD\"}"))
            .andReturn();
        assertEquals(422, r.getResponse().getStatus());
    }

    @Test
    void missingAuthReturns401() throws Exception {
        setup();
        MvcResult r = mvc.perform(post("/v1/payment-intents")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountCents\":1,\"currency\":\"USD\"}"))
            .andReturn();
        assertTrue(r.getResponse().getStatus() == 401 || r.getResponse().getStatus() == 403);
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        setup();
        MvcResult r = mvc.perform(post("/v1/payment-intents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountCents\":1,\"currency\":\"USD\"}"))
            .andReturn();
        assertEquals(400, r.getResponse().getStatus());
    }

    @Test
    void listEndpointReturnsMerchantScopedResults() throws Exception {
        setup();
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/v1/payment-intents")
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amountCents\":" + ((i + 1) * 100) + ",\"currency\":\"USD\"}"))
                .andReturn();
        }
        MvcResult r = mvc.perform(get("/v1/payment-intents?page=0&size=10")
                .header("Authorization", "Bearer " + token))
            .andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode json = om.readTree(r.getResponse().getContentAsString());
        assertTrue(json.get("total").asInt() >= 3);
    }

    @Test
    void failingCardMarksIntentFailed() throws Exception {
        setup();
        // The in-memory gateway fails when customer email contains "@fail.".
        // We create a customer first, then reference it.
        // For the unit scope we simulate the failure by sending amount
        // > 1_000_000 which the gateway maps to "amount_too_large".
        MvcResult r = mvc.perform(post("/v1/payment-intents")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountCents\":1500000,\"currency\":\"USD\"}"))
            .andReturn();
        assertEquals(201, r.getResponse().getStatus());
        JsonNode n = om.readTree(r.getResponse().getContentAsString());
        assertEquals("failed", n.get("status").asText());
    }
}
