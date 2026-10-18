import { test, expect, type Page } from '@playwright/test';

const students = [1, 2, 3].map(id => ({
  id, username: `student_${id}`, realName: `学生${id}`, className: '一班',
  userType: 'STUDENT', active: true, createTime: '2026-10-18T00:00:00Z', lastLogin: null,
}));
const records = (id: number) => [true, false, null].map((correct, index) => ({
  recordId: `${id}-${index}`, userId: id, questionId: index + 1, correct,
  answeredAt: '2026-10-18T00:00:00Z', questionPreview: `学生${id}的题目${index}`,
  userAnswer: 'A', errorReason: null, eventId: null, practice: false, attemptOrder: index + 1,
  sessionId: `session-${id}`,
}));
const detail = (id: number, index: number) => ({
  ...records(id)[index],
  questionContent: `学生${id}的题目${index}`,
  correctAnswer: 'B', questionOptions: { A: 'a', B: 'b' }, questionAnalysis: '解析',
  difficulty: '简单', imageBase64: null, sessionId: `session-${id}`,
});

async function setup(page: Page, delayedStudent?: { gate: Promise<void>; fail: boolean; enabled: boolean }) {
  const requests: string[] = [];
  await page.addInitScript(() => {
    localStorage.setItem('zhixue_token', 'offline-teacher-fixture');
  });
  await page.route('**/api/v1/**', async route => {
    const resourceType = route.request().resourceType();
    if (resourceType !== 'fetch' && resourceType !== 'xhr') return route.continue();
    const parsed = new URL(route.request().url());
    const path = parsed.pathname.replace('/api/v1', '');
    requests.push(path);
    let data: unknown = null;
    let status = 200;
    const history = path.match(/^\/answer-records\/user\/(\d+)\/history$/);
    const answerDetail = path.match(/^\/answer-records\/(\d+-\d+)$/);
    if (path === '/user/me') data = { ...students[0], id: 99, userType: 'TEACHER', realName: '教师' };
    else if (path === '/user/teacher/all') data = students;
    else if (path === '/answer-records/teacher/student-stats') data = [
      { userId: 1, totalCount: 3, wrongCount: 1 }, { userId: 2, totalCount: 3, wrongCount: 1 },
    ];
    else if (history) {
      const id = Number(history[1]);
      if (id === 2 && delayedStudent?.enabled) await delayedStudent.gate;
      if (id === 2 && delayedStudent?.enabled && delayedStudent.fail) {
        status = 500; data = null;
      } else {
        const wrongOnly = parsed.searchParams.get('wrongOnly') === 'true';
        const rows = id === 3 ? [] : records(id).filter(row => !wrongOnly || row.correct === false);
        data = { records: rows, total: rows.length, pages: 1, currentPage: 1, pageSize: 20 };
      }
    } else if (answerDetail) {
      const [id, index] = answerDetail[1].split('-').map(Number);
      data = detail(id, index);
    } else if (path.startsWith('/ai/teacher/report/')) {
      const id = Number(path.split('/').at(-1));
      if (id === 2 && delayedStudent?.enabled) await delayedStudent.gate;
      if (id === 2 && delayedStudent?.enabled && delayedStudent.fail) {
        status = 500; data = null;
      } else {
        data = id === 3 ? null : { latestReportContent: `学生${id}的报告`, reportCreateTime: '2026-10-18T00:00:00Z' };
      }
    }
    await route.fulfill({ status, contentType: 'application/json',
      body: JSON.stringify({ code: status, message: status === 200 ? '操作成功' : '旧请求失败', data }) });
  });
  return requests;
}

test('student list uses aggregate statistics and loads selected records once', async ({ page }) => {
  const requests = await setup(page);
  await page.goto('teacher/student-analysis');
  await expect(page.locator('.student-item')).toHaveCount(3);
  await expect(page.locator('.record-item')).toHaveCount(3);
  await expect(page.locator('.student-item').filter({ hasText: '学生3' })).toContainText('0 条记录');
  expect(requests.filter(path => path === '/answer-records/teacher/student-stats')).toHaveLength(1);
  expect(requests.filter(path => path.startsWith('/answer-records/user/'))).toEqual(['/answer-records/user/1/history']);
  await page.getByRole('button', { name: '错题记录', exact: true }).click();
  await expect(page.locator('.record-item')).toHaveCount(1);
  expect(requests.filter(path => path === '/answer-records/user/1/history')).toHaveLength(2);
});

for (const fail of [false, true]) {
  test(`late ${fail ? 'failed' : 'successful'} selection cannot overwrite the current student`, async ({ page }) => {
    let release!: () => void;
    const gate = new Promise<void>(resolve => { release = resolve; });
    const delayed = { gate, fail, enabled: false };
    const requests = await setup(page, delayed);
    await page.goto('teacher/student-analysis');
    await expect(page.locator('.student-item')).toHaveCount(3);
    delayed.enabled = true;
    await page.locator('.student-item').filter({ hasText: '学生2' }).click();
    await expect.poll(() => requests.includes('/answer-records/user/2/history')).toBe(true);
    await page.locator('.student-item').filter({ hasText: '学生3' }).click();
    await expect(page.locator('.records-card')).toContainText('该学生当前没有答题记录');
    const oldRecord = page.waitForResponse(response => response.url().includes('/answer-records/user/2/history'));
    const oldReport = page.waitForResponse(response => response.url().endsWith('/ai/teacher/report/2'));
    release();
    await Promise.all([oldRecord, oldReport]);
    // Browser round-trip after both callbacks lets Vue apply any stale mutations.
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    await expect(page.locator('.detail-student-name')).toHaveText('学生3');
    await expect(page.locator('.record-item')).toHaveCount(0);
    await expect(page.locator('.report-card')).toContainText('该学生尚未开始作答');
    await expect(page.locator('.student-detail-panel')).not.toContainText('学生2的');
    await expect(page.locator('body')).not.toContainText('旧请求失败');
  });
}
