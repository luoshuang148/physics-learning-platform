import { test, expect, type Page } from '@playwright/test';
async function catalog(page: Page) {
  const requests: string[] = [];
  await page.addInitScript(() => localStorage.setItem('zhixue_token', 'catalog-fixture'));
  await page.route('**/api/v1/**', async (route) => {
    if (!['fetch', 'xhr'].includes(route.request().resourceType())) return route.continue();
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    requests.push(path + url.search);
    let data: unknown = [];
    if (path === '/user/me') data = { id: 1, realName: '王老师', userType: 'TEACHER' };
    if (path === '/teacher/classes')
      data = [{ id: 1, name: '一班', role: 'OWNER', status: 'ACTIVE' }];
    if (path === '/questions/teacher/page') {
      const pageNo = Number(url.searchParams.get('page') ?? 1);
      const all = Array.from({ length: 21 }, (_, i) => ({
        id: 101 + i,
        content: '题目 ' + (101 + i),
        difficulty: 2,
        correctOption: 'A',
        hasImage: true,
      }));
      data = { records: all.slice((pageNo - 1) * 20, pageNo * 20), total: 21, pages: 2 };
    }
    if (path === '/questions/teacher/get/101')
      data = {
        id: 101,
        content: '完整题目及公式',
        options: { A: 'a', B: 'b', C: 'c', D: 'd' },
        correctOption: 'A',
        difficulty: 2,
        imageBase64:
          'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j4JkAAAAASUVORK5CYII=',
      };
    if (path.endsWith('/history')) data = { records: [], total: 0, pages: 0 };
    if (path === '/questions/knowledge-points') data = [];
    if (/\/questions\/teacher\/\d+\/index-status$/.test(path))
      data = { indexed: false, status: 'NONE', lastError: null };
    return route.fulfill({ json: { code: 200, data } });
  });
  return requests;
}
test('bank uses bounded pages and fetches full question only when editing', async ({ page }) => {
  const requests = await catalog(page);
  await page.goto('teacher/questions');
  await expect(page.locator('.qm-item')).toHaveCount(20);
  await expect(page.locator('.qm-item img')).toHaveCount(0);
  expect(requests.some((url) => url.startsWith('/questions/teacher/get/all'))).toBeFalsy();
  await page.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(page.locator('.qm-item')).toHaveCount(1);
  await expect(page.locator('.qm-item')).toContainText('#121');
  await page.getByRole('button', { name: '上一页', exact: true }).click();
  await page
    .locator('.qm-item')
    .filter({ hasText: '#101' })
    .getByRole('button', { name: '编辑', exact: true })
    .click();
  await expect(page.locator('.qm-form-panel')).toBeVisible({ timeout: 10000 });
  await expect(page.locator('.qm-form-panel textarea').first()).toHaveValue('完整题目及公式', { timeout: 10000 });
  await expect(page.locator('.image-preview')).toBeVisible();
});
test('assignment selection stays intact across question pages', async ({ page }) => {
  await catalog(page);
  await page.goto('teacher/event-manage');
  await page.getByRole('button', { name: '新建任务', exact: true }).click();
  await page.getByRole('button', { name: '从题库选题', exact: true }).click();
  await expect(page.locator('.bank-item')).toHaveCount(20);
  await page.locator('.bank-item').filter({ hasText: '#101' }).getByRole('checkbox').check();
  await page
    .getByRole('dialog', { name: '从题库选题' })
    .getByRole('button', { name: '下一页', exact: true })
    .click();
  await expect(page.locator('.bank-item')).toHaveCount(1);
  await page.locator('.bank-item').getByRole('checkbox').check();
  await page.getByRole('button', { name: '完成选题', exact: true }).click();
  await expect(page.locator('.selected-content')).toHaveText(['题目 101', '题目 121']);
});
