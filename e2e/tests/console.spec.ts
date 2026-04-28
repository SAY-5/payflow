import { test, expect } from "@playwright/test";

/**
 * End-to-end against the real React console + real Spring Boot backend.
 * Login → create charge → see it in the table → refund → see it.
 */

test("login, create a charge, refund it", async ({ page }) => {
  await page.goto("/");
  await page.locator(".login-card").waitFor();
  await page.locator("#api-key").fill("demo-api-key");
  await page.getByRole("button", { name: /enter console/i }).click();

  // Dashboard renders.
  await page.locator(".tx-table").waitFor({ timeout: 15_000 });

  // Create a charge.
  await page.locator("#amount").fill("19.95");
  await page.locator("#description").fill("e2e test order");
  await page.getByRole("button", { name: /^charge$/i }).click();

  // Wait for the row to appear in the table.
  const row = page.locator(".tx-table tbody tr", { hasText: "e2e test order" });
  await expect(row).toBeVisible({ timeout: 10_000 });
  await expect(row.locator(".pill.succeeded")).toBeVisible();

  // Open the detail pane by clicking the row.
  await row.click();
  await expect(page.locator(".detail-amount")).toContainText("$19.95");

  // Issue a refund (full).
  await page.locator("#refund-reason").fill("e2e refund");
  await page.locator(".refund-form button.primary").click();

  // The refund-status message appears.
  await expect(page.locator(".refund-form")).toContainText(/Refund.*succeeded/i, {
    timeout: 10_000,
  });
});

test("login screen rejects an empty key", async ({ page }) => {
  await page.goto("/");
  await page.locator(".login-card").waitFor();
  await page.locator("#api-key").fill("");
  // Button should be disabled.
  const enter = page.getByRole("button", { name: /enter console/i });
  await expect(enter).toBeDisabled();
});

test("login screen surfaces an error for a bad key", async ({ page }) => {
  await page.goto("/");
  await page.locator(".login-card").waitFor();
  await page.locator("#api-key").fill("not-the-demo-key");
  await page.getByRole("button", { name: /enter console/i }).click();
  await expect(page.locator(".login-card").getByText(/invalid api key/i))
    .toBeVisible({ timeout: 10_000 });
});
