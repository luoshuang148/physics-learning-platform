import { test, expect, type Page } from '@playwright/test';

const tasks = [42, 43].map(id => ({ id, eventTitle: id === 42 ? '力学作业' : '运动学作业', teacherName: '王老师',
  questionIds: [1, 2], questionCount: 2, status: id === 42 ? 'PUBLISHED' : 'ARCHIVED',
  canAnswer: id === 42, availability: id === 42 ? 'OPEN' : 'ARCHIVED', startAt: null, dueAt: null }));
async function fixture(page: Page, reports = true, answered = 1) {
  const streams: number[] = [];
  await page.addInitScript(() => localStorage.setItem('zhixue_token', 'review-browser-fixture'));
  await page.route('**/api/v1/**', route => {
    if (!['fetch', 'xhr'].includes(route.request().resourceType())) return route.continue();
    const url = new URL(route.request().url()), path = url.pathname.replace('/api/v1', '');
    if (path === '/ai/student/report') {
      const id = Number(url.searchParams.get('eventId')); streams.push(id);
      return route.fulfill({ contentType: 'text/event-stream', body: `event: content\ndata: 作业 ${id} 的练习建议\n\nevent: complete\ndata: {}\n\n` });
    }
    const result = (id: number) => ({ task: tasks.find(task => task.id === id), answeredQuestions: answered, correctQuestions: 0, answerRevision: String(id) });
    const data = path === '/user/me' ? { id: 7, username: 'review_fixture', realName: '李同学', userType: 'STUDENT' }
      : path === '/teacher/events/student-tasks' ? tasks.map(task => result(task.id))
      : path.endsWith('/my-result') ? result(Number(path.split('/')[3]))
      : path === '/teacher/events/available' ? [tasks[0]]
      : path === '/ai/capabilities' ? { reports, tutoring: false }
      : path === '/answer-records/student/summary' ? { totalAttempts: 1, correctAttempts: 0, wrongQuestions: 1 }
      : path.endsWith('/page') ? { records: [], total: 0, pages: 0 }
      : path.endsWith('/questions') ? [{ id: 1, content: '力学题', options: { A: 'a', B: 'b' } }, { id: 2, content: '第二题', options: { A: 'a', B: 'b' } }]
      : path.startsWith('/answer-records/student/task/') ? { records: [1, 2].map(questionId => ({ recordId: String(questionId), eventId: 42, questionId, correct: false, userAnswer: 'B', correctAnswer: 'A', practice: false })) }
      : [];
    return route.fulfill({ json: { code: 200, data } });
  });
  return streams;
}

test('report lives inside assignments, including previously answered archived work', async ({ page }) => {
  const streams = await fixture(page);
  await page.goto('student/task');
  const nav = page.getByRole('navigation', { name: '学生主导航', exact: true });
  await expect(nav.getByRole('link')).toHaveText(['首页', '作业', '错题本', '学习助手']);
  await page.locator('.assignment-row').filter({ hasText: '运动学作业' }).getByRole('link', { name: '查看结果 / 复盘' }).click();
  await expect(page).toHaveURL(/student\/report\?eventId=43$/);
  await expect(page.getByRole('heading', { name: '运动学作业', exact: true })).toBeVisible();
  await expect(nav.getByRole('link', { name: '作业', exact: true })).toHaveAttribute('aria-current', 'page');
  await expect(page.getByTestId('assignment-answered')).toContainText('1 / 2');
  await expect(page.getByTestId('assignment-correct')).toHaveText('0');
  expect(streams).toEqual([]);
  await page.getByRole('button', { name: '生成 AI 复盘', exact: true }).click();
  await expect(page.locator('.review-output')).toContainText('作业 43 的练习建议');
  expect(streams).toEqual([43]);
});

test('answer completion links to the exact task review', async ({ page }) => {
  await fixture(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('student/task');
  await page.getByRole('button', { name: '继续作答', exact: true }).click();
  await expect(page.locator('.finish-bar')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.getByRole('button', { name: '查看本次结果与复盘', exact: true }).click();
  await expect(page).toHaveURL(/student\/report\?eventId=42$/);
  await expect(page.getByRole('heading', { name: '力学作业', exact: true })).toBeVisible();
});

test('unscoped report links return to assignments and never guess a task', async ({ page }) => {
  const streams = await fixture(page);
  await page.goto('student/report');
  await expect(page).toHaveURL(/student\/task$/);
  expect(streams).toEqual([]);
});

test('assignment facts remain readable when AI is unavailable, with mobile navigation intact', async ({ page }) => {
  await fixture(page, false);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('student/report?eventId=42');
  await expect(page.getByTestId('assignment-answered')).toContainText('1 / 2');
  await expect(page.getByRole('button', { name: '生成 AI 复盘', exact: true })).toBeDisabled();
  await expect(page.getByRole('navigation', { name: '学生底部导航', exact: true }).getByRole('link')).toHaveCount(4);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});

test('unanswered task cannot generate an AI review', async ({ page }) => {
  const streams = await fixture(page, true, 0);
  await page.goto('student/report?eventId=42');
  await expect(page.getByText('这份作业还没有作答记录')).toBeVisible();
  await expect(page.getByRole('button', { name: '生成 AI 复盘', exact: true })).toBeDisabled();
  expect(streams).toEqual([]);
});

test('slow AI capabilities never delay the saved assignment facts', async ({ page }) => {
  await fixture(page);
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/ai/capabilities', async route => {
    await pending;
    await route.fulfill({ json: { code: 200, data: { reports: false, tutoring: false } } }).catch(() => {});
  });
  await page.goto('student/report?eventId=42');
  try {
    await expect(page.getByTestId('assignment-answered')).toContainText('1 / 2', { timeout: 3000 });
    await expect(page.getByRole('button', { name: '生成 AI 复盘', exact: true })).toBeDisabled();
  } finally { release(); }
});

test('cached AI review stays scoped to its task and expires after new answers', async ({ page }) => {
  await fixture(page);
  await page.goto('student/report?eventId=42');
  await page.getByRole('button', { name: '生成 AI 复盘', exact: true }).click();
  await expect(page.locator('.review-output')).toContainText('作业 42 的练习建议');
  await page.getByRole('navigation', { name: '学生主导航', exact: true }).getByRole('link', { name: '作业', exact: true }).click();
  await page.locator('.assignment-row').filter({ hasText: '运动学作业' }).getByRole('link', { name: '查看结果 / 复盘' }).click();
  await expect(page.getByRole('button', { name: '生成 AI 复盘', exact: true })).toBeVisible();
  await expect(page.locator('.assignment-review')).not.toContainText('作业 42 的练习建议');
  await page.getByRole('button', { name: '生成 AI 复盘', exact: true }).click();
  await expect(page.locator('.review-output')).toContainText('作业 43 的练习建议');
  await page.reload();
  await expect(page.locator('.review-output')).toContainText('作业 43 的练习建议');
  await page.route('**/teacher/events/43/my-result', route => route.fulfill({ json: { code: 200, data: {
    task: tasks[1], answeredQuestions: 1, correctQuestions: 0, answerRevision: '44',
  } } }));
  await page.reload();
  await expect(page.getByRole('button', { name: '生成 AI 复盘', exact: true })).toBeVisible();
  await expect(page.locator('.review-output')).toHaveCount(0);
});

test('stopping generation never caches a partial review', async ({ page }) => {
  await fixture(page);
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/ai/student/report?*', async route => {
    await pending;
    await route.fulfill({ contentType: 'text/event-stream', body: 'event: content\ndata: stopped content\n\nevent: complete\ndata: {}\n\n' }).catch(() => {});
  });
  await page.goto('student/report?eventId=42');
  await page.getByRole('button', { name: '生成 AI 复盘', exact: true }).click();
  await page.getByRole('button', { name: '停止生成', exact: true }).click();
  release();
  await expect(page.getByText('已停止生成，当前内容不完整，可以重新生成。')).toBeVisible();
  await page.reload();
  await expect(page.getByRole('button', { name: '生成 AI 复盘', exact: true })).toBeVisible();
  await expect(page.locator('.review-output')).toHaveCount(0);
});


test('saved server report restores after browser cache is cleared', async ({ page }) => {
  const streams = await fixture(page);
  await page.route('**/teacher/events/42/my-result', route => route.fulfill({ json: { code: 200, data: {
    task: tasks[0], answeredQuestions: 1, correctQuestions: 0, answerRevision: '42',
    savedReport: { content: '从服务器恢复的复盘', answerRevision: '42', generatedAt: '2026-10-19T00:00:00Z' },
  } } }));
  await page.goto('student/report?eventId=42');
  await expect(page.locator('.review-output')).toContainText('从服务器恢复的复盘');
  expect(streams).toEqual([]);
});
