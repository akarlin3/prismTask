import { test, expect } from '@playwright/test';

test.describe('Navigation', () => {
  test('login page renders correctly', async ({ page }) => {
    await page.goto('/login');
    await expect(page).toHaveTitle(/PrismTask/);
  });

  test('register page renders correctly', async ({ page }) => {
    await page.goto('/register');
    await expect(page).toHaveTitle(/PrismTask/);
  });

  test('unknown routes redirect to login when not authenticated', async ({ page }) => {
    await page.goto('/nonexistent-page');
    // Should redirect to either / (then to /login) or directly to /login
    await expect(page).toHaveURL(/\/(login)?$/);
  });

  test('has correct meta tags', async ({ page }) => {
    await page.goto('/login');
    const description = await page.getAttribute('meta[name="description"]', 'content');
    expect(description).toBeTruthy();
    expect(description).toContain('task management');
  });

  test('has PWA manifest link', async ({ page }) => {
    await page.goto('/login');
    const manifest = await page.getAttribute('link[rel="manifest"]', 'href');
    expect(manifest).toBe('/manifest.json');
  });
});
