import { test, expect } from './support/isolatedTest';

const base = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:18080/api/v1/';
const url = (path: string) => new URL(path, base).toString();

test('real name is not a login identifier; username remains authoritative', async ({ browser, request }) => {
  const tag = Date.now();
  const username = `login_user_${tag}`;
  const realName = `登录姓名_${tag}`;
  const password = 'Login-fixture-2026';
  const registration = await request.post(url('auth/register'), {
    data: { username, realName, password, userType: 'STUDENT' },
  });
  expect(registration.ok()).toBeTruthy();

  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(url('auth/login'));
  await page.getByPlaceholder('请输入用户名').fill(realName);
  await page.getByPlaceholder('请输入密码', { exact: true }).fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(/auth\/login$/);
  await expect(page.locator('body')).not.toContainText('登录成功');

  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码', { exact: true }).fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(/student\/home$/);
  await context.close();
});
