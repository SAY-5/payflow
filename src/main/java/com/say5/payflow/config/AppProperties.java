package com.say5.payflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payflow")
public class AppProperties {
    private Auth auth = new Auth();
    private Idempotency idempotency = new Idempotency();
    private Webhook webhook = new Webhook();
    private Cors cors = new Cors();

    public Auth getAuth() { return auth; }
    public Idempotency getIdempotency() { return idempotency; }
    public Webhook getWebhook() { return webhook; }
    public Cors getCors() { return cors; }

    public static class Auth {
        private String jwtSecret = "dev-secret-at-least-sixteen-chars-xxxxx";
        private int jwtTtlMinutes = 60;

        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
        public int getJwtTtlMinutes() { return jwtTtlMinutes; }
        public void setJwtTtlMinutes(int jwtTtlMinutes) { this.jwtTtlMinutes = jwtTtlMinutes; }
    }

    public static class Idempotency {
        private int ttlDays = 7;
        public int getTtlDays() { return ttlDays; }
        public void setTtlDays(int ttlDays) { this.ttlDays = ttlDays; }
    }

    public static class Webhook {
        private int maxAgeSeconds = 300;
        /** Allow the deprecated unscoped /webhooks/stripe endpoint. Off
         *  in prod by default — multi-merchant deployments must use the
         *  per-merchant /webhooks/stripe/{id} URL Stripe assigns. */
        private boolean allowLegacy = true;
        public int getMaxAgeSeconds() { return maxAgeSeconds; }
        public void setMaxAgeSeconds(int maxAgeSeconds) { this.maxAgeSeconds = maxAgeSeconds; }
        public boolean isAllowLegacy() { return allowLegacy; }
        public void setAllowLegacy(boolean allowLegacy) { this.allowLegacy = allowLegacy; }
    }

    public static class Cors {
        private String origins = "http://localhost:5173";
        public String getOrigins() { return origins; }
        public void setOrigins(String origins) { this.origins = origins; }
    }
}
