import { test, expect } from '@playwright/test';

test.describe('Extract route', () => {
  test('unauthenticated /extract bounces to login', async ({ page }) => {
    await page.goto('/extract');
    await expect(page).toHaveURL(/\/(login)?$/);
  });
});
