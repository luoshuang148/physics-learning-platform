import { test, expect } from '@playwright/test';

test('student generates, saves, activates and opens a learning plan using mocked APIs', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('zhixue_token', 'isolated-learning-plan-fixture'));
  let planStatus = 'DRAFT';
  let planVersion = 1;
  const planView = () => ({ id: 91, eventId: 42, status: planStatus, version: planVersion, days: 3,
    items: [{ id: 701, day: 1, knowledgePointCode: 'mechanics.newton_2', questionId: 31, reasonSourceIds: ['W1', 'S1'] }],
    sourceAnswerRevision: 'revision-42', practiceAttempts: 0, practiceCorrect: 0, progressStatus: 'NOT_STARTED',
    executionMode: 'WORKFLOW', evidenceSummary: ['W1', 'S1'] });
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace('/api/v1', '');
    if (path === '/user/me') return route.fulfill({ json: { code: 200, data: { id: 7, username: 'student', realName: '李同学', userType: 'STUDENT' } } });
    if (path === '/ai/student/assistant/resolve') return route.fulfill({ json: { code: 200, data: { intent: 'PLAN', query: request.postDataJSON().question, days: 3, excludeAttempted: false } } });
    if (path === '/ai/capabilities') return route.fulfill({ json: { code: 200, data: { reports: false, tutoring: false, workflowReady: true, agentReady: false } } });
    if (path === '/teacher/events/available') return route.fulfill({ json: { code: 200, data: [{ id: 42, eventTitle: '牛顿定律作业', questionIds: [31], questionCount: 1 }] } });
    if (path === '/ai/student/learning-plan/preview' && request.method() === 'POST') {
      const body = request.postDataJSON();
      const planPreview = { days: body.days, sourceEventId: 42, sourceAnswerRevision: 'revision-42', executionMode: 'WORKFLOW', items: [{ id: 701, day: 1, knowledgePointCode: 'mechanics.newton_2', questionId: 31, reasonSourceIds: ['W1', 'S1'] }], evidenceSummary: ['W1', 'S1'], partial: false };
      const events = [
        ['progress', { runId: 'run-42', requestId: body.requestId, mode: 'WORKFLOW' }],
        ['tool-step', { runId: 'run-42', seq: 1, toolName: 'get_wrong_questions', status: 'SUCCEEDED', summary: '已读取本人错题和历史快照' }],
        ['plan', { runId: 'run-42', seq: 2, planPreview, evidenceSummary: planPreview.evidenceSummary }],
        ['complete', { runId: 'run-42', seq: 3, status: 'success', executionMode: 'WORKFLOW' }],
      ];
      return route.fulfill({ status: 200, contentType: 'text/event-stream', body: events.map(([name, data]) => `event: ${name}\ndata: ${JSON.stringify(data)}\n\n`).join('') });
    }
    if (path === '/learning-plans' && request.method() === 'GET') return route.fulfill({ json: { code: 200, data: planStatus === 'ACTIVE' ? [planView()] : [] } });
    if (path === '/learning-plans' && request.method() === 'POST') return route.fulfill({ json: { code: 201, message: 'created', data: planView() } });
    if (path === '/learning-plans/91/activate' && request.method() === 'POST') {
      planStatus = 'ACTIVE'; planVersion = 2;
      return route.fulfill({ json: { code: 200, data: planView() } });
    }
    return route.fulfill({ json: { code: 200, data: [] } });
  });

  await page.goto('student/ai');
  await page.getByRole('textbox', { name: '输入你的物理问题' }).fill('帮我针对这次作业的错题安排三天复习');
  await page.getByRole('button', { name: '发送消息', exact: true }).click();
  // With a single available assignment the plan card auto-generates after mount.
  await expect(page.getByRole('heading', { name: '3 天复习计划' })).toBeVisible();
  await page.getByText('查看生成依据', { exact: true }).click();
  await expect(page.getByText('已读取本人错题和历史快照')).toBeVisible();
  await page.getByText('查看计划依据', { exact: true }).click();
  await expect(page.getByText('W1、S1', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '保存为我的计划' }).click();
  await expect(page.locator('.plan-state').getByText('草稿', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '启用计划', exact: true }).click();
  await expect(page.locator('.plan-state').getByText('进行中', { exact: true })).toBeVisible();
  await page.reload();
  await page.getByRole('button', { name: '我的复习计划', exact: true }).click();
  await expect(page.getByRole('heading', { name: '3 天复习计划' })).toBeVisible();
  await page.getByRole('button', { name: '继续复习' }).click();
  await expect(page.locator('.plan-state').getByText('进行中', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: /开始补练/ }).click();
  await expect(page.getByRole('dialog', { name: '计划补练' })).toBeVisible();
});
