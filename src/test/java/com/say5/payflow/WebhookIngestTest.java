package com.say5.payflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.say5.payflow.api.JwtService;
import com.say5.payflow.persistence.MerchantRepo;
import com.say5.payflow.webhook.StripeSignature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@ActiveProfiles("test")
class WebhookIngestTest {

    @Autowired WebApplicationContext ctx;
    @Autowired MerchantRepo merchants;
    @Autowired JwtService jwt;
    @Autowired ObjectMapper om;
    MockMvc mvc;
    static final UUID DEMO = UUID.fromString("00000000-0000-0000-0000-000000000de0");

    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        setup();
        String body = "{\"id\":\"evt_bogus\",\"type\":\"payment_intent.succeeded\"}";
        MvcResult r = mvc.perform(post("/webhooks/stripe")
                .header("Stripe-Signature", "t=1000,v1=deadbeef")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn();
        assertEquals(400, r.getResponse().getStatus());
    }

    @Test
    void acceptsValidSignatureAndIngests() throws Exception {
        setup();
        String secret = merchants.findById(DEMO).orElseThrow().getWebhookSecret();
        String body = """
          {"id":"evt_valid_1","type":"payment_intent.succeeded",
           "data":{"object":{"id":"pi_nonexistent"}}}
          """;
        long ts = Instant.now().getEpochSecond();
        String sig = StripeSignature.sign(body.getBytes(), secret, ts);

        MvcResult r = mvc.perform(post("/webhooks/stripe")
                .header("Stripe-Signature", sig)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn();
        assertEquals(200, r.getResponse().getStatus());
        assertFalse(om.readTree(r.getResponse().getContentAsString()).get("duplicate").asBoolean());
    }

    @Test
    void duplicateEventIdReportsDuplicate() throws Exception {
        setup();
        String secret = merchants.findById(DEMO).orElseThrow().getWebhookSecret();
        String body = """
          {"id":"evt_dup_1","type":"payment_intent.succeeded",
           "data":{"object":{"id":"pi_x"}}}
          """;
        long ts = Instant.now().getEpochSecond();
        String sig = StripeSignature.sign(body.getBytes(), secret, ts);
        mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", sig)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        MvcResult r = mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", sig)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        assertTrue(om.readTree(r.getResponse().getContentAsString()).get("duplicate").asBoolean());
    }
}
