import { type Page } from '@playwright/test';
import { test, expect } from './support/isolatedTest';

const base = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:18080/api/v1/';
const teacherName = process.env.E2E_TEACHER_USERNAME ?? 'e2e_teacher';
const teacherPassword = process.env.E2E_TEACHER_PASSWORD ?? 'E2e-teacher-fixture-2026';
const url = (path: string) => new URL(path, base).toString();
async function login(page: Page, username: string, password: string) {
  await page.goto(url('auth/login'));
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码', { exact: true }).fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(/\/(teacher\/dashboard|student\/home)$/);
}
async function headers(page: Page) {
  const token = await page.evaluate(() => localStorage.getItem('zhixue_token'));
  return { Authorization: `Bearer ${token}`, 'X-Session-Id': '20261018120000_e2e0000000000001' };
}

test('publish → answer → archive → reuse → archived practice, and safe AI rendering', async ({ browser, request }) => {
  const teacher = await browser.newContext(); const student = await browser.newContext();
  const tp = await teacher.newPage(); const sp = await student.newPage();
  const tag = Date.now(); const studentName = `e2e_student_${tag}`; const password = 'Student-fixture-2026';
  const registration = await request.post(url('auth/register'), { data: { username: studentName, password, realName: studentName, userType: 'STUDENT' } });
  expect(registration.ok()).toBeTruthy();
  await login(tp, teacherName, teacherPassword);
  const teacherHeaders = await headers(tp);
  const content = `E2E immutable force fixture ${tag}`;
  const created = await request.post(url('questions/teacher/create'), { headers: teacherHeaders, data: {
    content, options: JSON.stringify({ A: '3m/s²', B: '12m/s²', C: '6m/s²', D: '0m/s²' }), correctOption: 'A', explanation: 'a=F/m', difficulty: 1,
  } });
  expect(created.ok()).toBeTruthy(); const question = (await created.json()).data;
  // Select and publish from the actual teacher page.
  await tp.goto(url('teacher/event-manage'));
  await tp.locator('#task-title').fill('E2E task');
  await tp.getByPlaceholder('搜索题目 ID 或内容…').fill(content);
  await tp.locator('.bank-item').filter({ hasText: content }).getByRole('button', { name: '加入', exact: true }).click();
  await tp.locator('.publish-btn').click();
  await expect(tp.locator('.event-status-card')).toContainText('已发布');
  const originalTask = (await (await request.get(url('teacher/events/all'), { headers: teacherHeaders })).json()).data.find((item: any) => item.questionIds.includes(question.id) && item.eventTitle === 'E2E task');
  expect(originalTask.questionIds).toContain(question.id);

  await login(sp, studentName, password);
  await sp.goto(url('student/task')); await sp.locator('.task-item').filter({ hasText: 'E2E task' }).getByRole('button', { name: '开始作答' }).click();
  await expect(sp).toHaveURL(/student\/question$/);
  await expect(sp.locator('.question-text')).toContainText(content);
  await sp.locator('label.option-row').filter({ hasText: '12m/s²' }).click();
  await sp.getByRole('button', { name: '提交答案', exact: true }).click();
  await expect(sp.getByText('✗ 回答错误')).toBeVisible();
  await expect(sp.locator('.q-save-feedback')).toContainText('本题答案已提交成功');
  await sp.reload(); await expect(sp.locator('.q-dot.wrong')).toHaveCount(1);
  const studentHeaders = await headers(sp);
  let summary = await request.get(url(`answer-records/teacher/summary?eventId=${originalTask.id}`), { headers: teacherHeaders });
  expect((await summary.json()).data).toMatchObject({ assignmentAnswers: 1, assignmentCorrect: 0 });

  await tp.locator('.task-row').filter({ hasText: 'E2E task' }).getByRole('button', { name: '归档', exact: true }).click();
  await expect(tp.locator('.task-row').filter({ hasText: 'E2E task' })).toHaveCount(0);
  // Edit the bank after archiving; the old task must keep the original text and answer.
  const changed = await request.put(url('questions/teacher/update'), { headers: teacherHeaders, data: {
    id: question.id, content: `${content} EDITED`, options: JSON.stringify({ A: '3m/s²', B: '12m/s²', C: '6m/s²', D: '0m/s²' }), correctOption: 'B', difficulty: 1,
  } });
  expect(changed.ok()).toBeTruthy();
  await tp.locator('#task-title').fill('E2E reused task');
  await tp.getByPlaceholder('搜索题目 ID 或内容…').fill(content);
  await tp.locator('.bank-item').filter({ hasText: content }).getByRole('button', { name: '加入', exact: true }).click();
  await tp.locator('.publish-btn').click();
  await expect(tp.locator('.event-status-card')).toContainText('已发布');
  const reusedTask = (await (await request.get(url('teacher/events/all'), { headers: teacherHeaders })).json()).data.find((item: any) => item.questionIds.includes(question.id) && item.eventTitle === 'E2E reused task');
  expect(reusedTask.id).not.toEqual(originalTask.id);
  await sp.goto(url('student/task')); await sp.locator('.task-item').filter({ hasText: 'E2E reused task' }).getByRole('button', { name: '开始作答' }).click();
  await expect(sp).toHaveURL(/student\/question$/);
  await expect(sp.locator('label.option-row')).toHaveCount(4);
  await expect(sp.locator('.q-dot.wrong')).toHaveCount(0);
  await expect(sp.locator('.q-dot.correct')).toHaveCount(0);
  // Reusing a question does not reuse completion or report data.
  const emptyReport = await request.get(url(`ai/student/report?eventId=${reusedTask.id}`), { headers: studentHeaders });
  expect(await emptyReport.text()).toContain('无答题记录');

  // Finish the reused assignment before practicing its historical version.
  await sp.locator('label.option-row').filter({ hasText: '12m/s²' }).click();
  await sp.getByRole('button', { name: '提交答案', exact: true }).click();
  await expect(sp.locator('.q-save-feedback')).toBeVisible();
  await sp.goto(url(`student/practice?eventId=${originalTask.id}&questionId=${question.id}`));
  await expect(sp.locator('.question-text')).toContainText(content);
  await expect(sp.locator('.question-text')).not.toContainText('EDITED');
  await sp.locator('label.option-row').filter({ hasText: '3m/s²' }).click();
  await sp.getByRole('button', { name: '提交答案', exact: true }).click();
  await expect(sp.locator('.q-save-feedback')).toContainText('提交成功');
  summary = await request.get(url(`answer-records/teacher/summary?eventId=${originalTask.id}`), { headers: teacherHeaders });
  expect((await summary.json()).data).toMatchObject({ assignmentAnswers: 1, assignmentCorrect: 0, practiceAttempts: 1, masteredQuestions: 1 });
  await sp.goto(url('student/wrong-book')); await sp.locator('.wb-card-head').first().click();
  await expect(sp.getByText('题目内容 · 已通过补练')).toBeVisible();

  // Stub the upstream stream in the browser only: this smoke test makes no model request.
  await sp.route('**/ai/capabilities', route => route.fulfill({ json: { code: 200, data: { reports: true, tutoring: true, notice: 'Synthetic browser fixture' } } }));
  let requestedRecord: string | undefined;
  await sp.route('**/ai/student/wrong-question-analysis', async route => {
    requestedRecord = route.request().postDataJSON().recordId;
    await route.fulfill({ contentType: 'text/event-stream', body: 'event: content\ndata: <img src=x onerror="window.__xss=1"><script>window.__xss=2</script>SAFE_RENDER $F=ma$\n\nevent: complete\ndata: {}\n\n' });
  });
  await sp.getByRole('button', { name: 'AI分析', exact: true }).click();
  await expect(sp.locator('.wb-card-detail')).toContainText('SAFE_RENDER');
  expect(requestedRecord).toBeTruthy();
  expect(await sp.evaluate(() => (window as any).__xss)).toBeUndefined();
  await expect(sp.locator('.wb-card-detail [onerror], .wb-card-detail script')).toHaveCount(0);
  await sp.screenshot({ path: 'test-results/learning-journey.png', fullPage: true });
  await teacher.close(); await student.close();
});