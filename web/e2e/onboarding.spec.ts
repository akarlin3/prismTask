import { test, expect } from '@playwright/test';

/**
 * Smoke tests for the onboarding route. Same pattern as the other
 * specs in this folder — no sign-in, just confirms the route wires
 * up correctly and protected-route redirects behave.
 */
test.describe('Onboarding route', () => {
  test('unauthenticated /onboarding bounces to login', async ({ page }) => {
    await page.goto('/onboarding');
    await expect(page).toHaveURL(/\/(login)?$/);
  });
});
