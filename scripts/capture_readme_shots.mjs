/**
 * 截取 README 演示图。用法：
 *   cd frontend && node ../scripts/capture_readme_shots.mjs
 */
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const OUT = path.resolve(process.cwd(), '..', 'docs', 'images');
const BASE = 'http://localhost:5173';
fs.mkdirSync(OUT, { recursive: true });

const shots = [
  {
    name: 'login',
    url: `${BASE}/auth/login`,
    async prepare() {},
  },
  {
    name: 'student-home',
    url: `${BASE}/student/home`,
    login: { username: 'student1', password: 'Student@2026' },
  },
  {
    name: 'student-wrong-book',
    url: `${BASE}/student/wrong-book`,
    login: { username: 'student1', password: 'Student@2026' },
  },
  {
    name: 'teacher-dashboard',
    url: `${BASE}/teacher/dashboard`,
    login: { username: 'teacher', password: 'Teacher@2026' },
  },
  {
    name: 'teacher-questions',
    url: `${BASE}/teacher/questions`,
    login: { username: 'teacher', password: 'Teacher@2026' },
  },
  {
    name: 'teacher-assignment',
    url: `${BASE}/teacher/assignments/1`,
    login: { username: 'teacher', password: 'Teacher@2026' },
  },
];

async function login(page, { username, password }) {
  await page.goto(`${BASE}/auth/login`, { waitUntil: 'networkidle' });
  const user = page.locator('input').first();
  await user.fill(username);
  const pass = page.locator('input[type="password"]').first();
  await pass.fill(password);
  await page.getByRole('button', { name: /登录|login/i }).first().click();
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(800);
}

const browser = await chromium.launch({ headless: true });
for (const shot of shots) {
  const ctx = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
    locale: 'zh-CN',
  });
  const page = await ctx.newPage();
  try {
    if (shot.login) await login(page, shot.login);
    await page.goto(shot.url, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1200);
    const file = path.join(OUT, `${shot.name}.png`);
    await page.screenshot({ path: file, fullPage: false });
    console.log('saved', file);
  } catch (e) {
    console.error('fail', shot.name, e.message);
  }
  await ctx.close();
}
await browser.close();
