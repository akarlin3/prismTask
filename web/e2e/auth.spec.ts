import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test('shows login page for unauthenticated users', async ({ page }) => {
    await page.goto('/');
    // Should redirect to login
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: /sign in|log in|welcome/i })).toBeVisible();
  });

  test('login form has required fields', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel(/password/i).first()).toBeVisible();
    await expect(page.locator('button[type="submit"]').getByText(/sign in|log in/i)).toBeVisible();
  });

  test('shows register page with link from login', async ({ page }) => {
    await page.goto('/login');
    const registerLink = page.getByRole('link', { name: /sign up|register|create/i });
    await expect(registerLink).toBeVisible();
  });

  test('register page has required fields', async ({ page }) => {
    await page.goto('/register');
    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel(/password/i).first()).toBeVisible();
    await expect(page.locator('button[type="submit"]').getByText(/sign up|register|create/i)).toBeVisible();
  });
});
