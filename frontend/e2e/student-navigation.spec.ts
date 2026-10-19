import { test, expect, type Page } from '@playwright/test';

// Browser-only fixtures: these checks never create accounts or change backend data.
const task = { id: 42, eventTitle: '牛顿运动定律 · 课后练习', teacherName: '王老师',
  questionIds: [1], canAnswer: true, availability: 'OPEN', status: 'PUBLISHED',
  startAt: null, dueAt: '2026-10-22T20:00:00', eventDescription: null };
const question = { id: 1, content: '质量为 2 kg 的物体受到 6 N 的合力，加速度是多少？',
  options: { A: '3 m/s²', B: '6 m/s²' }, correctOption: 'A', difficulty: 1 };
const labels = ['首页', '作业', '错题本', '3D 实验'];
const teacherLabels = ['总览', '学生名册', '题库管理', '学生学情', '任务管理'];

async function fixture(page: Page, role = 'STUDENT', tasks: object[] = [task]) {
  await page.addInitScript(() => localStorage.setItem('zhixue_token', 'browser-layout-fixture'));
  await page.route('**/api/v1/**', route => {
    if (!['fetch', 'xhr'].includes(route.request().resourceType())) return route.continue();
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    const data = path === '/user/me'
      ? { id: 7, username: 'layout_fixture', realName: role === 'STUDENT' ? '李同学' : '王老师', userType: role, className: '高一（2）班' }
      : path === '/teacher/events/available' ? tasks
      : path === '/teacher/events/student-tasks' ? tasks.map(task => ({ task, answeredQuestions: 0, correctQuestions: 0, answerRevision: '0' }))
      : path === '/teacher/events/42' ? task
      : path === '/teacher/events/42/questions' ? [question]
      : path === '/teacher/events/42/stats' ? { taskId: 42, title: task.eventTitle, status: 'PUBLISHED', availability: 'OPEN', questionCount: 1, totalStudents: 0, students: [], questions: [] }
      : path === '/answer-records/student/task/42' ? { records: [], totalCount: 0 }
      : path === '/answer-records/student/summary' ? { totalAttempts: 48, correctAttempts: 38, wrongQuestions: 6 }
      : path.endsWith('/page') || path.endsWith('/history') ? { records: [], totalCount: 0, totalPages: 0, currentPage: 1, pageSize: 20 }
      : path === '/ai/capabilities' ? { reports: false, tutoring: false }
      : [];
    return route.fulfill({ json: { code: 200, data } });
  });
}

async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
}

test('student desktop has shared top navigation and preserves assignment entry and profile controls', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await fixture(page);
  await page.goto('student/home');
  await expect(page.locator('.sidebar')).toHaveCount(0);
  const nav = page.getByRole('navigation', { name: '学生主导航', exact: true });
  await expect(nav.getByRole('link')).toHaveText(labels);
  await expect(page.getByRole('heading', { name: '牛顿运动定律 · 课后练习' })).toBeVisible();
  await page.getByRole('button', { name: '个人菜单', exact: true }).click();
  await expect(page.locator('.user-menu')).toBeVisible();
  const menu = await page.locator('.user-menu').boundingBox();
  const avatar = await page.getByRole('button', { name: '个人菜单', exact: true }).boundingBox();
  expect(menu!.y).toBeGreaterThanOrEqual(avatar!.y + avatar!.height);
  await page.getByRole('button', { name: '设置', exact: true }).click();
  await expect(page.getByRole('slider')).toBeVisible();
  await page.getByRole('button', { name: '修改个人信息', exact: true }).click();
  await expect(page.locator('.profile-modal')).toBeVisible();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.getByRole('button', { name: '开始作答', exact: true }).click();
  await expect(page).toHaveURL(/student\/question$/);
  await expect(page.locator('.question-text')).toContainText(question.content);
  await expect(nav.getByRole('link', { name: '作业', exact: true })).toHaveAttribute('aria-current', 'page');
  expect(await page.evaluate(() => sessionStorage.getItem('student_task_entry_event_id_7'))).toBe('42');
  await page.reload();
  await expect(nav.getByRole('link', { name: '作业', exact: true })).toHaveAttribute('aria-current', 'page');
  for (const label of ['错题本', '3D 实验', '首页']) {
    await nav.getByRole('link', { name: label, exact: true }).click();
    await expect(nav.getByRole('link', { name: label, exact: true })).toHaveAttribute('aria-current', 'page');
  }
  await page.goto('student/practice?eventId=42&questionId=1');
  await expect(nav.getByRole('link', { name: '错题本', exact: true })).toHaveAttribute('aria-current', 'page');
  await noOverflow(page);
});

test('student mobile navigation stays accessible and the AI button clears it after dragging', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await fixture(page);
  await page.goto('student/home');
  const nav = page.getByRole('navigation', { name: '学生底部导航', exact: true });
  await expect(nav).toBeVisible();
  await expect(nav.getByRole('link')).toHaveText(labels);
  await expect(page.getByRole('navigation', { name: '学生主导航', exact: true })).toBeHidden();
  await nav.getByRole('link', { name: '作业', exact: true }).click();
  await expect(page).toHaveURL(/student\/task$/);
  await page.getByRole('button', { name: '开始作答' }).click();
  await expect(nav.getByRole('link', { name: '作业', exact: true })).toHaveAttribute('aria-current', 'page');
  const fab = page.locator('.ai-fab');
  let bounds = await fab.boundingBox();
  await page.mouse.move(bounds!.x + 20, bounds!.y + 20);
  await page.mouse.down();
  await page.mouse.move(bounds!.x + 20, 830, { steps: 6 });
  await page.mouse.up();
  bounds = await fab.boundingBox();
  const navBounds = await nav.boundingBox();
  expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(navBounds!.y);
  await page.waitForTimeout(100); // Drag release briefly suppresses clicks.
  await page.getByRole('button', { name: '打开 AI 学习助手', exact: true }).click();
  await expect(page.locator('.ai-drawer')).toBeVisible();
  const drawer = await page.locator('.ai-drawer').boundingBox();
  expect(drawer!.y).toBeGreaterThanOrEqual(0);
  expect(drawer!.x).toBeGreaterThanOrEqual(0);
  expect(drawer!.y + drawer!.height).toBeLessThanOrEqual(navBounds!.y);
  await page.getByRole('button', { name: '关闭 AI 学习助手', exact: true }).click();
  await noOverflow(page);
  await page.getByRole('button', { name: '个人菜单', exact: true }).click();
  await page.getByRole('button', { name: '设置', exact: true }).click();
  await page.getByRole('slider').fill('18');
  await noOverflow(page);
  await page.getByRole('button', { name: '个人菜单', exact: true }).click();
  await page.setViewportSize({ width: 768, height: 1000 });
  await noOverflow(page);
  await page.setViewportSize({ width: 320, height: 720 });
  await expect(nav.getByRole('link', { name: '3D 实验', exact: true })).toBeVisible();
  await noOverflow(page);
});

test('empty student home keeps all navigation entries and does not offer assignment answering', async ({ page }) => {
  await fixture(page, 'STUDENT', []);
  await page.goto('student/home');
  await expect(page.getByRole('heading', { name: '当前暂无教师任务' })).toBeVisible();
  await expect(page.getByRole('button', { name: '开始作答', exact: true })).toHaveCount(0);
  await expect(page.getByRole('navigation', { name: '学生主导航', exact: true }).getByRole('link')).toHaveCount(4);
});

test('teacher uses the shared desktop navigation and retains account controls', async ({ page }) => {
  await fixture(page, 'TEACHER');
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('teacher/users');
  const nav = page.getByRole('navigation', { name: '教师主导航', exact: true });
  await expect(nav.getByRole('link')).toHaveText(teacherLabels);
  await expect(page.locator('.sidebar')).toHaveCount(0);
  await page.getByRole('button', { name: '个人菜单', exact: true }).click();
  await expect(page.locator('.user-menu')).toBeVisible();
  const menu = await page.locator('.user-menu').boundingBox();
  const avatar = await page.getByRole('button', { name: '个人菜单', exact: true }).boundingBox();
  expect(menu!.y).toBeGreaterThanOrEqual(avatar!.y + avatar!.height);
  await page.getByRole('button', { name: '修改个人信息', exact: true }).click();
  await expect(page.locator('.profile-modal')).toBeVisible();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  for (const label of teacherLabels) {
    await nav.getByRole('link', { name: label, exact: true }).click();
    await expect(nav.getByRole('link', { name: label, exact: true })).toHaveAttribute('aria-current', 'page');
  }
  await page.goto('teacher/assignments/42');
  await expect(nav.getByRole('link', { name: '任务管理', exact: true })).toHaveAttribute('aria-current', 'page');
  await noOverflow(page);
});

test('teacher mobile bottom navigation clears content and AI controls at maximum font size', async ({ page }) => {
  await fixture(page, 'TEACHER');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('teacher/dashboard');
  const nav = page.getByRole('navigation', { name: '教师底部导航', exact: true });
  await expect(nav.getByRole('link')).toHaveText(teacherLabels);
  await expect(page.getByRole('navigation', { name: '教师主导航', exact: true })).toBeHidden();
  await page.getByRole('button', { name: '个人菜单', exact: true }).click();
  await page.getByRole('button', { name: '设置', exact: true }).click();
  await page.getByRole('slider').fill('18');
  await page.getByRole('button', { name: '个人菜单', exact: true }).click();
  for (const label of teacherLabels) {
    await nav.getByRole('link', { name: label, exact: true }).click();
    await expect(nav.getByRole('link', { name: label, exact: true })).toHaveAttribute('aria-current', 'page');
    await noOverflow(page);
  }
  const fab = page.locator('.ai-fab');
  const bounds = await fab.boundingBox();
  await page.mouse.move(bounds!.x + 20, bounds!.y + 20);
  await page.mouse.down();
  await page.mouse.move(bounds!.x + 20, 830, { steps: 6 });
  await page.mouse.up();
  const navBounds = await nav.boundingBox();
  const dragged = await fab.boundingBox();
  expect(dragged!.y + dragged!.height).toBeLessThanOrEqual(navBounds!.y);
  await page.waitForTimeout(100);
  await page.getByRole('button', { name: '打开 AI 学习助手', exact: true }).click();
  await expect(page.locator('.ai-drawer')).toBeVisible();
  const drawer = await page.locator('.ai-drawer').boundingBox();
  expect(drawer!.y).toBeGreaterThanOrEqual(0);
  expect(drawer!.y + drawer!.height).toBeLessThanOrEqual(navBounds!.y);
  await page.getByRole('button', { name: '关闭 AI 学习助手', exact: true }).click();
  await page.setViewportSize({ width: 320, height: 720 });
  await expect(nav.getByRole('link', { name: '学生学情', exact: true })).toBeVisible();
  await noOverflow(page);
});
