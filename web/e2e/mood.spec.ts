import { test, expect } from '@playwright/test';

test.describe('Mood route', () => {
  test('unauthenticated /mood bounces to login', async ({ page }) => {
    await page.goto('/mood');
    await expect(page).toHaveURL(/\/(login)?$/);
  });
});
