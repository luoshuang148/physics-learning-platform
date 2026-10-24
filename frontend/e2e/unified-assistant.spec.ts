import { test, expect, type Page } from '@playwright/test';

const source = { sourceId: 'Q:31:1:QUESTION:0', kind: 'PLATFORM_QUESTION', questionId: 31, revision: 1, part: 'QUESTION', text: '质量为 2 kg 的物体受到 4 N 的合力，求加速度。' };

async function fixture(page: Page, options: { fail?: boolean; noTasks?: boolean } = {}) {
  await page.addInitScript(() => localStorage.setItem('zhixue_token', 'assistant-fixture'));
  const requests: { path: string; body: any; auth: string | undefined }[] = [];
  await page.route('**/api/v1/**', async route => {
    const req = route.request();
    const path = new URL(req.url()).pathname.replace('/api/v1', '');
    const body = req.postData() ? req.postDataJSON() : null;
    requests.push({ path, body, auth: req.headers().authorization });
    if (path === '/user/me') return route.fulfill({ json: { code: 200, data: { id: 7, username: 'student', realName: '李同学', userType: 'STUDENT' } } });
    if (path === '/ai/capabilities') return route.fulfill({ json: { code: 200, data: { reports: false, tutoring: true, workflowReady: true, agentReady: false } } });
    if (path === '/teacher/events/available') return route.fulfill({ json: { code: 200, data: options.noTasks ? [] : [{ id: 42, eventTitle: '牛顿定律作业', questionIds: [31], questionCount: 1 }] } });
    if (path === '/answer-records/student/history') return route.fulfill({ json: { code: 200, data: { records: [{ recordId: 'record-31', questionId: 31, questionPreview: '物体的加速度如何计算？', userAnswer: 'B', correct: false, eventId: 42, answeredAt: '2026-10-24T10:00:00' }], total: 1 } } });
    if (path === '/ai/student/assistant/resolve') {
      const q = body.question as string;
      const intent = /复习/.test(q) ? 'PLAN' : /这道题|错题/.test(q) ? 'WRONG' : /找|平台|再来/.test(q) ? 'PLATFORM' : 'GENERAL';
      return route.fulfill({ json: { code: 200, data: { intent, query: q.startsWith('再来') ? `${body.previousQuestion}；${q}` : q, days: 3, excludeAttempted: q.includes('做过') } } });
    }
    if (path === '/ai/student/assistant/messages') {
      if (options.fail) return route.fulfill({ status: 503, json: { message: '服务暂不可用' } });
      const events: [string, unknown][] = [['progress', { stage: body.mode === 'PLATFORM' ? 'retrieving' : 'generating' }]];
      if (body.mode === 'PLATFORM') events.push(['sources', { sources: [source] }]);
      events.push(['content', { text: body.mode === 'PLATFORM' ? '找到一道相关题目，可以用牛顿第二定律解答。' : '动量守恒指系统总动量保持不变。公式为 $p=mv$。' }], ['complete', { status: 'COMPLETED' }]);
      return route.fulfill({ contentType: 'text/event-stream', body: events.map(([name, data]) => `event: ${name}\r\ndata: ${JSON.stringify(data)}\r\n\r\n`).join('') });
    }
    if (path.startsWith('/ai/student/platform-sources/')) return route.fulfill({ status: 403, json: { message: '来源已失效' } });
    return route.fulfill({ json: { code: 200, data: [] } });
  });
  return requests;
}

async function send(page: Page, text: string) {
  await page.getByRole('textbox', { name: '输入你的物理问题' }).fill(text);
  await page.getByRole('button', { name: '发送消息', exact: true }).click();
}

test('one composer automatically searches, preserves follow-up subject, and switches to explanations', async ({ page }) => {
  const requests = await fixture(page);
  await page.goto('student/ai');
  await expect(page.getByRole('textbox')).toHaveCount(1);
  await expect(page.getByRole('button', { name: '平台找题', exact: true })).toHaveCount(0);
  await send(page, '帮我找牛顿第二定律练习题');
  await expect(page.getByText('找到一道相关题目，可以用牛顿第二定律解答。')).toHaveCount(1);
  await expect(page.getByRole('button', { name: /查看原题/ })).toBeVisible();
  const first = requests.find(r => r.path === '/ai/student/assistant/messages')!;
  expect(first.body.mode).toBe('PLATFORM');
  expect(first.auth).toBe('Bearer assistant-fixture');
  await send(page, '再来两道简单的，排除做过的');
  await expect.poll(() => requests.filter(r => r.path === '/ai/student/assistant/messages').length).toBe(2);
  const second = requests.filter(r => r.path === '/ai/student/assistant/messages')[1]!;
  expect(second.body.retrievalQuery).toContain('牛顿第二定律');
  expect(second.body.excludeAttempted).toBe(true);
  expect(second.body.history).toEqual(expect.arrayContaining([expect.objectContaining({ role: 'ASSISTANT', content: '找到一道相关题目，可以用牛顿第二定律解答。', sourceIds: ['Q:31:1:QUESTION:0'] })]));
  await expect(page.getByText('找到一道相关题目，可以用牛顿第二定律解答。')).toHaveCount(2);
  await send(page, '动量守恒怎么理解？');
  await expect(page.getByText(/动量守恒指系统总动量保持不变/)).toHaveCount(1);
  expect(requests.filter(r => r.path === '/ai/student/assistant/messages')[2]!.body.mode).toBe('GENERAL');
  await expect(page.locator('.katex')).toHaveCount(1);
});

test('wrong-answer clarification shows actual questions instead of asking for an ID', async ({ page }) => {
  await fixture(page);
  await page.goto('student/ai');
  await send(page, '这道题为什么错了？');
  await expect(page.getByRole('button', { name: /物体的加速度如何计算/ })).toBeVisible();
  await expect(page.getByPlaceholder('例如 1001')).toHaveCount(0);
});

test('HTTP failure offers a retry and does not duplicate the user message', async ({ page }) => {
  await fixture(page, { fail: true });
  await page.goto('student/ai');
  await send(page, '动量守恒怎么理解？');
  await expect(page.getByRole('alert')).toContainText('暂不可用');
  await page.getByRole('button', { name: '重试' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByText('动量守恒怎么理解？', { exact: true })).toHaveCount(1);
});

test('a revoked source shows an error without displaying its cached text', async ({ page }) => {
  await fixture(page);
  await page.goto('student/ai');
  await send(page, '平台有没有牛顿第二定律的题？');
  await page.getByRole('button', { name: /查看原题/ }).click();
  await expect(page.getByText('来源已失效或暂时无法访问，请重新提问检索。')).toBeVisible();
  await expect(page.getByText(source.text, { exact: true })).toHaveCount(0);
});

test('mobile composer fits the viewport and Enter sends one message', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await fixture(page);
  await page.goto('student/ai');
  const input = page.getByRole('textbox', { name: '输入你的物理问题' });
  await input.fill('动量守恒怎么理解？');
  await input.press('Shift+Enter');
  await expect(input).toHaveValue('动量守恒怎么理解？\n');
  await input.press('Enter');
  await expect(page.getByText(/动量守恒指系统总动量保持不变/)).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: '/tmp/physics-assistant-mobile.png', fullPage: true });
});


test('stopping a pending answer leaves one recoverable stopped response', async ({ page }) => {
  await fixture(page);
  let release: (() => void) | undefined;
  await page.route('**/ai/student/assistant/messages', async route => {
    await new Promise<void>(resolve => { release = resolve; });
    await route.fulfill({ contentType: 'text/event-stream', body: 'event: complete\ndata: {"status":"CANCELLED"}\n\n' }).catch(() => {});
  });
  await page.goto('student/ai');
  await send(page, '动量守恒怎么理解？');
  await expect.poll(() => Boolean(release)).toBe(true);
  await page.getByRole('button', { name: '停止生成', exact: true }).click();
  release?.();
  await expect(page.getByText('已停止生成', { exact: true })).toHaveCount(1);
  await expect(page.getByRole('button', { name: '停止生成', exact: true })).toHaveCount(0);
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('a plan with no available assignments explains the missing context inline', async ({ page }) => {
  await fixture(page, { noTasks: true });
  await page.goto('student/ai');
  await send(page, '帮我安排三天复习');
  await expect(page.getByText(/暂时没有可用作业/)).toBeVisible();
  await expect(page.getByRole('textbox')).toHaveCount(1);
});

test('desktop welcome keeps the entire entry in one viewport', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await fixture(page);
  await page.goto('student/ai');
  await expect(page.getByRole('heading', { name: '今天，想弄懂什么？' })).toBeVisible();
  await expect(page.getByRole('button', { name: /安排一次复习/ })).toBeInViewport();
  await page.screenshot({ path: '/tmp/physics-assistant-desktop.png', fullPage: true });
});

test('leaving while assignments load cannot start a background learning plan', async ({ page }) => {
  const requests = await fixture(page);
  let release: (() => void) | undefined;
  await page.route('**/teacher/events/available', async route => {
    await new Promise<void>(resolve => { release = resolve; });
    await route.fulfill({ json: { code: 200, data: [{ id: 42, eventTitle: '牛顿定律作业', questionIds: [31], questionCount: 1 }] } });
  });
  await page.goto('student/ai');
  await send(page, '帮我安排三天复习');
  await expect.poll(() => Boolean(release)).toBe(true);
  await page.getByRole('link', { name: '首页', exact: true }).click();
  await expect(page).toHaveURL(/student\/home/);
  await expect(page.getByRole('textbox', { name: '输入你的物理问题' })).toHaveCount(0);
  release?.();
  await expect(page.getByRole('region', { name: '学习概览' })).toBeVisible();
  expect(requests.filter(r => r.path === '/ai/student/learning-plan/preview')).toHaveLength(0);
});

test('new conversation starts a fresh wrong-answer tutoring session', async ({ page }) => {
  await fixture(page);
  const sessionIds: string[] = [];
  await page.route('**/ai/student/wrong-question-analysis', async route => {
    sessionIds.push(route.request().headers()['x-session-id']!);
    await route.fulfill({ contentType: 'text/event-stream', body: 'event: message\ndata: {"content":"需要先画受力图。"}\n\nevent: complete\ndata: {}\n\n' });
  });
  await page.goto('student/ai');
  for (let i = 0; i < 2; i++) {
    await send(page, '这道题为什么错了？');
    await page.getByRole('button', { name: /物体的加速度如何计算/ }).click();
    await expect(page.getByText('需要先画受力图。', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: /新对话/ }).click();
  }
  expect(sessionIds).toHaveLength(2);
  expect(sessionIds[0]).not.toBe(sessionIds[1]);
});
