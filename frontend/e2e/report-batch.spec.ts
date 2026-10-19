import { test, expect, type Page } from '@playwright/test';
async function setup(page: Page, scopedOnly = false, delayed?: { enabled: boolean; gate: Promise<void>; seen: () => void }) {
  const keys: string[] = []; let failFirst = !delayed;
  let current = { id: 11, classId: 1, eventId: 7, status: 'SUCCEEDED', totalCount: 1, succeededCount: 1, failedCount: 0, skippedCount: 0, pendingCount: 0, processingCount: 0, finishedCount: 1 };
  await page.addInitScript(() => localStorage.setItem('zhixue_token', 'offline-teacher'));
  await page.route('**/api/v1/**', async route => {
    if (!['fetch', 'xhr'].includes(route.request().resourceType())) return route.continue();
    const url = new URL(route.request().url()); const path = url.pathname.replace('/api/v1', '');
    let data: unknown = null; let status = 200;
    const pagination = (records: unknown[]) => ({ records, total: records.length, pages: 1, currentPage: 1, pageSize: 50 });
    if (path === '/user/me') data = { id: 99, username: 'teacher', realName: '教师', userType: 'TEACHER', active: true };
    else if (path === '/teacher/classes') data = [{ id: 1, name: '一班', status: 'ACTIVE', role: 'OWNER' }];
    else if (path === '/teacher/events/7/stats') data = { taskId: 7, classId: 1, title: '旧作业', status: 'CLOSED', availability: 'CLOSED', startAt: null, dueAt: null, questionCount: 1, students: [], questions: [], totalStudents: 0, participatedStudents: 0, completedStudents: 0, notStartedStudents: 0, partialStudents: 0, completionRate: 0, participationRate: 0, accuracy: 0, answeredQuestions: 0, correctQuestions: 0 };
    else if (path === '/teacher/report-batches' && route.request().method() === 'POST') {
      keys.push(route.request().postDataJSON().idempotencyKey);
      if (failFirst) { failFirst = false; status = 503; }
      else { current = { ...current, id: current.id + 1 }; data = current; }
    } else if (path === '/teacher/report-batches') data = pagination(scopedOnly && url.searchParams.get('eventId') !== '7'
      ? Array.from({ length: 50 }, (_, n) => ({ ...current, id: 100 + n, eventId: 100 + n })) : [current]);
    else if (/\/teacher\/report-batches\/\d+\/items$/.test(path)) data = pagination([{ id: 101, userId: 1, studentName: path.includes('/11/') ? '旧作业学生' : '新批次学生', status: 'SUCCEEDED', attempts: 1, maxAttempts: 3, manualRetryCount: 0, reportId: 88 }]);
    else if (/\/teacher\/report-batches\/\d+$/.test(path)) {
      const snapshot = { ...current, id: Number(path.split('/').at(-1)) };
      if (snapshot.id === 11 && delayed?.enabled) { delayed.seen(); await delayed.gate; }
      data = snapshot;
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ code: status, message: status === 200 ? '成功' : '临时故障', data }) });
  });
  await page.goto('teacher/assignments/7'); return keys;
}
test('旧作业按任务查询报告，不受教师最近50个批次限制', async ({ page }) => {
  await setup(page, true); await expect(page.getByText('旧作业学生', { exact: true })).toBeVisible();
});
test('新批次使用新幂等键，同一次失败创建的重试复用原键', async ({ page }) => {
  const keys = await setup(page);
  await page.getByRole('button', { name: '生成新批次', exact: true }).click();
  await expect(page.getByText('临时故障', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '重试创建', exact: true }).click();
  await expect(page.getByRole('button', { name: '生成新批次', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: '生成新批次', exact: true }).click();
  await expect.poll(() => keys.length).toBe(3);
  expect(keys[0]).toBe(keys[1]); expect(keys[2]).not.toBe(keys[0]);
});

test('旧批次慢刷新不能覆盖新建批次', async ({ page }) => {
  let release!: () => void; let seen!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  const arrived = new Promise<void>(resolve => { seen = resolve; });
  const delayed = { enabled: false, gate, seen };
  await setup(page, false, delayed);
  await expect(page.getByText('旧作业学生', { exact: true })).toBeVisible();
  delayed.enabled = true;
  await page.getByRole('button', { name: '刷新进度', exact: true }).click();
  await arrived;
  try {
    await page.getByRole('button', { name: '生成新批次', exact: true }).click();
    await expect(page.getByText('新批次学生', { exact: true })).toBeVisible();
    const response = page.waitForResponse(res => new URL(res.url()).pathname.endsWith('/report-batches/11'));
    release(); await (await response).finished();
    await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
    await expect(page.getByText('新批次学生', { exact: true })).toBeVisible();
  } finally { release(); }
});
