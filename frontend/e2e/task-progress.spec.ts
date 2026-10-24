import { test, expect, type Page } from '@playwright/test';

const ids = [101, 102, 103];
const result = (id: number, title: string, answered: number, availability = 'OPEN') => ({
  task: { id, eventTitle: title, teacherName: '王老师', questionIds: ids, questionCount: 3,
    availability, status: availability === 'CLOSED' ? 'CLOSED' : availability === 'ARCHIVED' ? 'ARCHIVED' : 'PUBLISHED',
    canAnswer: availability === 'OPEN', startAt: null, dueAt: null },
  answeredQuestions: answered, correctQuestions: 0, answerRevision: String(answered),
});
const fixtureResults = () => [result(1, '尚未作答', 0), result(2, '继续力学作业', 2), result(3, '全部答完', 3),
  result(4, '截止未交', 0, 'ENDED'), result(5, '截止未完成', 1, 'CLOSED'), result(6, '等待开放', 0, 'SCHEDULED')];

async function studentFixture(page: Page, pending = false) {
  const results = fixtureResults();
  let savedLastAnswer = false;
  await page.addInitScript(({ pending }) => {
    localStorage.setItem('zhixue_token', 'progress-browser-fixture');
    if (pending) sessionStorage.setItem('student_question_view_state_v2_7_2_assignment', JSON.stringify({
      currentQuestionId: 102, selectedOption: 'B',
      pendingAnswer: { requestId: 'pending-fixture', questionId: 102, userAnswer: 'B' },
    }));
  }, { pending });
  await page.route('**/api/v1/**', route => {
    if (!['fetch', 'xhr'].includes(route.request().resourceType())) return route.continue();
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    const answer = (questionId: number) => ({ recordId: String(questionId), eventId: 2, questionId,
      correct: false, correctAnswer: 'A', userAnswer: 'B', practice: false });
    if (path === '/answer-records/student/submit') {
      savedLastAnswer = true; results[1].answeredQuestions = 3;
      return route.fulfill({ json: { code: 200, data: answer(102) } });
    }
    const data = path === '/user/me' ? { id: 7, username: 'progress_fixture', realName: '李同学', userType: 'STUDENT' }
      : path === '/teacher/events/student-tasks' ? results
      : path === '/teacher/events/available' ? results.filter(r => r.task.status === 'PUBLISHED').map(r => r.task)
      : path.endsWith('/questions') ? ids.map((id, index) => ({ id, content: ['第一题', '第二题', '第三题'][index], options: { A: '选项A', B: '选项B' } }))
      : path === '/answer-records/student/task/2' ? { records: [101, 103, ...(savedLastAnswer ? [102] : [])].map(answer) }
      : path === '/answer-records/student/summary' ? { totalAttempts: 2, correctAttempts: 0, wrongQuestions: 2 }
      : path === '/ai/capabilities' ? { reports: false, tutoring: false }
      : path.endsWith('/page') ? { records: [], total: 0 }
      : [];
    return route.fulfill({ json: { code: 200, data } });
  });
}

test('each assignment shows saved progress and distinguishes personal state from opening time', async ({ page }) => {
  await studentFixture(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('student/task');
  const card = (title: string) => page.locator('.assignment-row').filter({ hasText: title });
  await expect(card('尚未作答')).toContainText('已完成 0 / 3 题');
  await expect(card('尚未作答')).toContainText('未开始');
  await expect(card('继续力学作业')).toContainText('已完成 2 / 3 题');
  await expect(card('继续力学作业')).toContainText('进行中');
  await expect(card('继续力学作业').getByRole('progressbar')).toHaveAttribute('value', '67');
  await expect(card('全部答完')).toContainText('已完成');
  await expect(card('全部答完').getByRole('button', { name: /作答/ })).toHaveCount(0);
  await expect(card('截止未交')).toContainText('未交');
  await expect(card('截止未完成')).toContainText('未完成');
  await expect(card('等待开放')).toContainText('未开放');
  await expect(page.locator('.assignment-row').first()).toContainText('继续力学作业');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.screenshot({ path: 'artifacts/verification/task-progress-mobile.png', fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.screenshot({ path: 'artifacts/verification/task-progress-desktop.png', fullPage: true });
});

test('continue opens the first unanswered question and saved completion refreshes the assignment card', async ({ page }) => {
  await studentFixture(page);
  await page.goto('student/task');
  await page.locator('.assignment-row').filter({ hasText: '继续力学作业' }).getByRole('button', { name: '继续作答', exact: true }).click();
  await expect(page.locator('.question-text')).toHaveText('第二题');
  await expect(page).toHaveURL(/student\/question$/);
  await page.reload();
  await expect(page.locator('.question-text')).toHaveText('第二题');
  await page.locator('label.option-row').filter({ hasText: '选项B' }).click();
  await page.getByRole('button', { name: '提交答案', exact: true }).click();
  await expect(page.locator('.finish-bar')).toBeVisible();
  await page.getByRole('navigation', { name: '学生主导航', exact: true }).getByRole('link', { name: '作业', exact: true }).click();
  const card = page.locator('.assignment-row').filter({ hasText: '继续力学作业' });
  await expect(card).toContainText('已完成 3 / 3 题');
  await expect(card.getByRole('button', { name: '继续作答', exact: true })).toHaveCount(0);
  await expect(card.getByRole('link', { name: '查看结果 / 复盘' })).toBeVisible();
});

test('home uses the same progress and prioritizes unfinished work', async ({ page }) => {
  await studentFixture(page);
  await page.goto('student/home');
  await expect(page.getByRole('heading', { name: '继续力学作业', exact: true })).toBeVisible();
  await expect(page.locator('.student-current-task')).toContainText('已完成 2 / 3 题');
  await page.getByRole('button', { name: '继续作答', exact: true }).click();
  await expect(page.locator('.question-text')).toHaveText('第二题');
});

test('continuing preserves an answer that still needs saving', async ({ page }) => {
  await studentFixture(page, true);
  await page.goto('student/task');
  await page.locator('.assignment-row').filter({ hasText: '继续力学作业' }).getByRole('button', { name: '继续作答', exact: true }).click();
  await expect(page.locator('.question-text')).toHaveText('第二题');
  await expect(page.getByRole('button', { name: '重试保存作答', exact: true })).toBeVisible();
});

for (const availability of ['OPEN', 'CLOSED']) test(`teacher progress and exports use the same definitions: ${availability}`, async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('zhixue_token', 'progress-teacher-fixture'));
  await page.route('**/api/v1/**', route => {
    if (!['fetch', 'xhr'].includes(route.request().resourceType())) return route.continue();
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    const data = path === '/user/me' ? { id: 1, realName: '王老师', userType: 'TEACHER' }
      : path.endsWith('/stats') ? { taskId: 9, title: '状态一致性', status: availability === 'CLOSED' ? 'CLOSED' : 'PUBLISHED',
        availability, questionCount: 3, totalStudents: 3, participatedStudents: 2, completedStudents: 1,
        notStartedStudents: 1, partialStudents: 1, completionRate: 33, participationRate: 67, accuracy: 0,
        answeredQuestions: 4, correctQuestions: 0, questions: [], students: ['NOT_STARTED', 'PARTIAL', 'COMPLETED'].map((status, index) => ({
          userId: index + 7, username: `student${index}`, realName: `学生${index}`, status, answeredQuestions: index === 2 ? 3 : index,
          correctQuestions: 0, missingQuestions: index === 2 ? 0 : 3 - index,
        })) } : [];
    return route.fulfill({ json: { code: 200, data } });
  });
  await page.goto('teacher/assignments/9');
  const labels = availability === 'OPEN' ? ['未开始', '进行中', '已完成'] : ['未交', '未完成', '已完成'];
  await expect(page.locator('.row-status')).toHaveText(labels);
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: '导出 CSV', exact: true }).click();
  const file = await (await download).path();
  const { readFile } = await import('node:fs/promises');
  const csv = await readFile(file!, 'utf8');
  for (const label of labels) expect(csv).toContain(label);
});
