/**
 * Capture one login page and five authenticated demo pages for the README.
 * From the repository root: BASE_URL=http://47.110.38.57 node scripts/capture_readme_shots.mjs
 * Install dependencies first: npm --prefix frontend ci && npm --prefix frontend exec playwright install chromium
 */
import { createRequire } from 'node:module';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(path.join(ROOT, 'frontend', 'package.json'));
const { chromium, expect } = require('@playwright/test');
const BASE = (process.env.BASE_URL || 'http://localhost:5173').replace(/\/$/, '');
const OUT = path.join(ROOT, 'docs', 'images');
const staging = await fs.mkdtemp(path.join(ROOT, '.readme-shots-'));
const shots = [
  { name: 'login', route: '/auth/login', marker: 'form' },
  { name: 'student-home', route: '/student/home', role: 'student', marker: '.student-summary' },
  { name: 'student-wrong-book', route: '/student/wrong-book', role: 'student', marker: '.wb-page' },
  { name: 'teacher-dashboard', route: '/teacher/dashboard', role: 'teacher', marker: '.overview-strip' },
  { name: 'teacher-questions', route: '/teacher/questions', role: 'teacher', marker: '.qm-layout' },
  { name: 'teacher-assignment', role: 'teacher', marker: '.detail-page' },
];
const browser = await chromium.launch({ headless: true });
try {
  for (const shot of shots) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1, locale: 'zh-CN' });
    try {
      const page = await context.newPage();
      const apiErrors = [];
      page.on('response', response => {
        if (response.url().includes('/api/v1/') && response.status() >= 400) apiErrors.push(response.status());
      });
      await page.goto(`${BASE}/auth/login`, { waitUntil: 'networkidle' });
      const loginURL = new URL(page.url());
      if (loginURL.origin !== new URL(BASE).origin || !loginURL.pathname.endsWith('/auth/login')) throw new Error('Unexpected login URL');
      const appBase = loginURL.origin + loginURL.pathname.slice(0, -'/auth/login'.length);
      if (shot.role) {
        await page.locator('input[autocomplete="username"]').fill(shot.role === 'teacher' ? 'teacher' : 'stu01');
        await page.locator('input[type="password"]').fill(shot.role === 'teacher' ? (process.env.TEACHER_PASSWORD || 'Teacher@2026') : (process.env.STUDENT_PASSWORD || 'Student@2026'));
        await page.getByRole('button', { name: '登录', exact: true }).click();
        const destination = shot.role === 'teacher' ? '/teacher/dashboard' : '/student/home';
        await page.waitForURL(url => url.origin === loginURL.origin && url.pathname === new URL(appBase + destination).pathname, { timeout: 15000 });
        await expect(page.locator('input[type="password"]')).toHaveCount(0);
      }
      let route = shot.route;
      if (shot.name === 'teacher-assignment') {
        const link = page.locator('a[href*="/teacher/assignments/"]').first();
        await expect(link).toBeVisible();
        const detail = new URL(await link.getAttribute('href'), appBase + '/');
        const prefix = new URL(appBase).pathname.replace(/\/$/, '');
        route = detail.pathname.slice(prefix.length);
        if (detail.origin !== loginURL.origin || !/^\/teacher\/assignments\/\d+$/.test(route)) throw new Error('No valid assignment detail link');
      }
      await page.goto(`${appBase}${route}`, { waitUntil: 'networkidle' });
      await expect(page).toHaveURL(`${appBase}${route}`);
      if (shot.role) await expect(page.locator('input[type="password"]')).toHaveCount(0);
      await expect(page.locator(shot.marker).first()).toBeVisible();
      await expect(page.getByText(/正在加载…|加载错题记录…|加载中…/, { exact: true })).toHaveCount(0);
      await page.evaluate(() => document.fonts.ready);
      if (apiErrors.length) throw new Error(`API failed: ${apiErrors.join(', ')}`);
      await page.screenshot({ path: path.join(staging, `${shot.name}.png`), animations: 'disabled' });
      console.log(`captured ${shot.name}: ${page.url()}`);
    } finally {
      await context.close();
    }
  }
  // A failed capture leaves the existing README images untouched.
  await fs.mkdir(OUT, { recursive: true });
  for (const shot of shots) await fs.copyFile(path.join(staging, `${shot.name}.png`), path.join(OUT, `${shot.name}.png`));
  console.log(`Saved all ${shots.length} screenshots to ${OUT}`);
} finally {
  await browser.close();
  await fs.rm(staging, { recursive: true, force: true });
}
