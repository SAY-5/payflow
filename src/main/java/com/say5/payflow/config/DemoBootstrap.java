package com.say5.payflow.config;

import com.say5.payflow.persistence.Merchant;
import com.say5.payflow.persistence.MerchantRepo;
import com.say5.payflow.service.ApiKeyHasher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.UUID;

/**
 * Seed the demo merchant with a freshly-computed PBKDF2 hash on startup.
 *
 * Done in code (rather than via Flyway SQL) so we never ship a committed
 * key hash with a hard-coded salt. Skipped in production.
 */
@Configuration
@Profile("!prod")
public class DemoBootstrap {
    private static final UUID DEMO_ID = UUID.fromString("00000000-0000-0000-0000-000000000de0");
    private static final String DEMO_KEY = "demo-api-key";

    @Bean
    public CommandLineRunner seedDemoMerchant(MerchantRepo merchants) {
        return args -> {
            if (merchants.findById(DEMO_ID).isPresent()) return;
            merchants.save(new Merchant(
                DEMO_ID,
                "Demo Merchant",
                ApiKeyHasher.hash(DEMO_KEY),
                ApiKeyHasher.fingerprint(DEMO_KEY),
                "whsec_demo_do_not_use_in_prod"
            ));
        };
    }
}
