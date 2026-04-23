import { test, expect } from '@playwright/test';

test.describe('Analytics route', () => {
  test('unauthenticated /analytics bounces to login', async ({ page }) => {
    await page.goto('/analytics');
    await expect(page).toHaveURL(/\/(login)?$/);
  });
});
