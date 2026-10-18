import { test, expect } from '@playwright/test';

const base = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:18080/api/v1/';
const url = (path: string) => new URL(path, base).toString();

test('student has a free 3D experiment entry independent of tasks', async ({ browser, request }) => {
  const tag = Date.now();
  const username = `exp_student_${tag}`;
  const password = 'Experiment-fixture-2026';
  const registration = await request.post(url('auth/register'), {
    data: { username, realName: `实验学生${tag}`, password, userType: 'STUDENT' },
  });
  expect(registration.ok()).toBeTruthy();

  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(url('auth/login'));
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码', { exact: true }).fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(/student\/home$/);

  await page.getByRole('link', { name: /3D 实验/ }).first().click();
  await expect(page).toHaveURL(/student\/experiments$/);
  await expect(page.locator('.exp-sidebar')).toBeVisible();
  await expect(page.locator('.exp-item')).toHaveCount(4);
  await expect(page.locator('.exp-frame')).toHaveAttribute('src', /three-physical-project/);

  await page.getByRole('button', { name: '宇宙航天', exact: true }).click();
  await expect(page.locator('.exp-item')).toHaveCount(4);
  await expect(page.locator('.exp-frame')).toHaveAttribute('src', /(Kepler|Cosmis)/);
  await context.close();
});
