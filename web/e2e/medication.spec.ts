import { test, expect } from '@playwright/test';

test.describe('Medication route', () => {
  test('unauthenticated /medication bounces to login', async ({ page }) => {
    await page.goto('/medication');
    await expect(page).toHaveURL(/\/(login)?$/);
  });
});
