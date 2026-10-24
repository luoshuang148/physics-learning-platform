import { test, expect, type Page } from '@playwright/test';

const students = [1, 2, 3].map((id) => ({
  id,
  username: `student_${id}`,
  realName: `学生${id}`,
  className: '一班',
  userType: 'STUDENT',
  active: true,
  createTime: '2026-10-18T00:00:00Z',
  lastLogin: null,
}));
const records = (id: number) =>
  [true, false, null].map((correct, index) => ({
    recordId: `${id}-${index}`,
    userId: id,
    questionId: index + 1,
    correct,
    answeredAt: '2026-10-18T00:00:00Z',
    questionPreview: `学生${id}的题目${index}`,
    userAnswer: 'A',
    errorReason: null,
    eventId: null,
    practice: false,
    attemptOrder: index + 1,
    sessionId: `session-${id}`,
  }));
const detail = (id: number, index: number) => ({
  ...records(id)[index],
  questionContent: `学生${id}的题目${index}`,
  correctAnswer: 'B',
  questionOptions: { A: 'a', B: 'b' },
  questionAnalysis: '解析',
  difficulty: '简单',
  imageBase64: null,
  sessionId: `session-${id}`,
});

async function setup(
  page: Page,
  delayedStudent?: { gate: Promise<void>; fail: boolean; enabled: boolean },
) {
  const requests: string[] = [];
  await page.addInitScript(() => {
    localStorage.setItem('zhixue_token', 'offline-teacher-fixture');
  });
  await page.route('**/api/v1/**', async (route) => {
    const resourceType = route.request().resourceType();
    if (resourceType !== 'fetch' && resourceType !== 'xhr') return route.continue();
    const parsed = new URL(route.request().url());
    const path = parsed.pathname.replace('/api/v1', '');
    requests.push(path);
    let data: unknown = null;
    let status = 200;
    const history = path.match(/^\/answer-records\/user\/(\d+)\/history$/);
    const answerDetail = path.match(/^\/answer-records\/(\d+-\d+)$/);
    if (path === '/user/me')
      data = { ...students[0], id: 99, userType: 'TEACHER', realName: '教师' };
    else if (path === '/teacher/classes')
      data = [
        {
          id: 1,
          name: '一班',
          grade: null,
          status: 'ACTIVE',
          studentCount: 3,
          teacherCount: 1,
          role: 'OWNER',
        },
      ];
    else if (path === '/teacher/classes/1/students')
      data = students.map((student) => ({
        userId: student.id,
        username: student.username,
        realName: student.realName,
        className: student.className,
        active: true,
      }));
    else if (path === '/user/teacher/all') data = students;
    else if (path === '/answer-records/teacher/student-stats')
      data = [
        { userId: 1, totalCount: 3, wrongCount: 1 },
        { userId: 2, totalCount: 10, wrongCount: 6 },
      ];
    else if (history) {
      const id = Number(history[1]);
      if (id === 2 && delayedStudent?.enabled) await delayedStudent.gate;
      if (id === 2 && delayedStudent?.enabled && delayedStudent.fail) {
        status = 500;
        data = null;
      } else {
        const wrongOnly = parsed.searchParams.get('wrongOnly') === 'true';
        const rows =
          id === 3 ? [] : records(id).filter((row) => !wrongOnly || row.correct === false);
        data = { records: rows, total: rows.length, pages: 1, currentPage: 1, pageSize: 20 };
      }
    } else if (answerDetail) {
      const [id, index] = answerDetail[1].split('-').map(Number);
      data = detail(id, index);
    } else if (path.startsWith('/ai/teacher/report/')) {
      const id = Number(path.split('/').at(-1));
      if (id === 2 && delayedStudent?.enabled) await delayedStudent.gate;
      if (id === 2 && delayedStudent?.enabled && delayedStudent.fail) {
        status = 500;
        data = null;
      } else {
        data =
          id === 3
            ? null
            : { latestReportContent: `学生${id}的报告`, reportCreateTime: '2026-10-18T00:00:00Z' };
      }
    }
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify({
        code: status,
        message: status === 200 ? '操作成功' : '旧请求失败',
        data,
      }),
    });
  });
  return requests;
}

test('class table keeps aggregate statistics stable when searching and never preloads student details', async ({
  page,
}) => {
  const requests = await setup(page);
  await page.goto('teacher/student-analysis');
  await expect(page.locator('.student-row')).toHaveCount(3);
  await expect(page.getByTestId('class-student-count')).toHaveText('3');
  await expect(page.getByTestId('class-error-rate')).toHaveText('53.8%');
  await expect(page.locator('.student-row').filter({ hasText: '学生3' })).toContainText('暂无作答');
  expect(requests.some((path) => path.startsWith('/answer-records/user/'))).toBe(false);
  expect(requests.some((path) => path.startsWith('/ai/teacher/report/'))).toBe(false);
  await page.getByPlaceholder('搜索姓名或用户名…').fill('学生1');
  await expect(page.locator('.student-row')).toHaveCount(1);
  await expect(page.getByTestId('class-student-count')).toHaveText('3');
  await expect(page.getByTestId('class-error-rate')).toHaveText('53.8%');
});

test('sort, student drawer, inline record detail and report tab preserve the class table', async ({
  page,
}) => {
  const requests = await setup(page);
  await page.goto('teacher/student-analysis');
  await page.getByLabel('学生排序').selectOption('error-desc');
  await expect(page.locator('.student-row').first()).toContainText('学生2');
  await page.getByRole('button', { name: '查看学生1学情', exact: true }).click();
  const drawer = page.getByRole('dialog', { name: '学生学情详情' });
  await expect(drawer.locator('.record-item')).toHaveCount(3);
  expect(requests.some((path) => path.startsWith('/ai/teacher/report/'))).toBe(false);
  await drawer.getByRole('button', { name: /学生1的题目0/ }).click();
  await expect(drawer.locator('.record-expanded')).toContainText('正确答案');
  await drawer.getByRole('tab', { name: '错题记录', exact: true }).click();
  await expect(drawer.locator('.record-item')).toHaveCount(1);
  await drawer.getByRole('tab', { name: 'AI 学习报告', exact: true }).click();
  await expect(drawer.locator('.report-card')).toContainText('学生1的报告');
  expect(requests.filter((path) => path === '/ai/teacher/report/1')).toHaveLength(1);
  await drawer.getByRole('button', { name: '关闭', exact: true }).click();
  await expect(drawer).not.toBeVisible();
  await expect(page.locator('.student-row').first()).toContainText('学生2');
});

for (const fail of [false, true]) {
  test(`late ${fail ? 'failed' : 'successful'} student response cannot replace a newly selected student`, async ({
    page,
  }) => {
    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const delayed = { gate, fail, enabled: true };
    const requests = await setup(page, delayed);
    await page.goto('teacher/student-analysis');
    await page.getByRole('button', { name: '查看学生2学情', exact: true }).click();
    await expect.poll(() => requests.includes('/answer-records/user/2/history')).toBe(true);
    await page.getByRole('dialog').getByRole('button', { name: '关闭', exact: true }).click();
    await page.getByRole('button', { name: '查看学生3学情', exact: true }).click();
    await expect(page.locator('.records-card')).toContainText('该学生当前没有答题记录');
    const oldResponse = page.waitForResponse((r) =>
      r.url().includes('/answer-records/user/2/history'),
    );
    release();
    await oldResponse;
    await page.evaluate(
      () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))),
    );
    await expect(page.locator('.detail-student-name')).toHaveText('学生3');
    await expect(page.locator('.record-item')).toHaveCount(0);
    await expect(page.getByRole('dialog')).not.toContainText('学生2的');
    await expect(page.locator('body')).not.toContainText('旧请求失败');
  });
}

test('mobile drawer has a readable record, closes with Escape and restores focus', async ({
  page,
}) => {
  await setup(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('teacher/student-analysis');
  const trigger = page.getByRole('button', { name: '查看学生1学情', exact: true });
  await trigger.click();
  const drawer = page.getByRole('dialog', { name: '学生学情详情' });
  await expect(drawer.locator('.record-item')).toHaveCount(3);
  expect(await drawer.evaluate((el) => el.scrollWidth <= innerWidth)).toBe(true);
  await page.keyboard.press('Escape');
  await expect(drawer).not.toBeVisible();
  await expect(trigger).toBeFocused();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('missing report is a normal empty state', async ({ page }) => {
  await setup(page);
  await page.route('**/api/v1/ai/teacher/report/1', (route) =>
    route.fulfill({
      status: 400,
      json: { code: 4402, message: '答题记录不存在: 该学生暂无学习报告记录', data: null },
    }),
  );
  await page.goto('teacher/student-analysis');
  await page.getByRole('button', { name: '查看学生1学情', exact: true }).click();
  await page.getByRole('tab', { name: 'AI 学习报告', exact: true }).click();
  await expect(page.locator('.report-card')).toContainText('暂未生成学习报告');
  await expect(page.locator('.report-card')).not.toContainText('重新加载');
});

test('report service failure offers a retry instead of claiming no report exists', async ({
  page,
}) => {
  await setup(page);
  let fail = true;
  await page.route('**/api/v1/ai/teacher/report/1', (route) =>
    route.fulfill(
      fail
        ? { status: 500, json: { code: 500, message: '服务故障', data: null } }
        : {
            json: {
              code: 200,
              data: {
                latestReportContent: '重新加载后的报告',
                reportCreateTime: '2026-10-24T00:00:00Z',
              },
            },
          },
    ),
  );
  await page.goto('teacher/student-analysis');
  await page.getByRole('button', { name: '查看学生1学情', exact: true }).click();
  await page.getByRole('tab', { name: 'AI 学习报告', exact: true }).click();
  await expect(page.locator('.report-card')).toContainText('暂时无法加载');
  await expect(page.locator('.report-card')).not.toContainText('暂未生成');
  fail = false;
  await page.locator('.report-card').getByRole('button', { name: '重新加载' }).click();
  await expect(page.locator('.report-card')).toContainText('重新加载后的报告');
});
