import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 60_000,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: "http://127.0.0.1:5173",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: [
    {
      command:
        "cd .. && PAYFLOW_DB_URL=jdbc:h2:mem:e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,KEY,ACTION,AT,VALUE;DATABASE_TO_LOWER=TRUE " +
        "PAYFLOW_DB_USER=sa PAYFLOW_DB_PASSWORD= " +
        "SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver " +
        "SPRING_FLYWAY_LOCATIONS=classpath:db/migration-test " +
        "SPRING_PROFILES_ACTIVE=test " +
        "PAYFLOW_JWT_SECRET=test-secret-at-least-sixteen-chars-xxxxxxx " +
        "mvn -q spring-boot:run",
      port: 8080,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
    {
      command: "cd ../frontend && npm run dev -- --host 127.0.0.1 --port 5173",
      port: 5173,
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
    },
  ],
});
