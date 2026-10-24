import { test, expect, type Page } from '@playwright/test';

test.setTimeout(25000);
const task = (id: number, status: string, classId = 1, availability = status) => ({
  id,
  classId,
  teacherId: 99,
  eventTitle: `任务${id}`,
  status,
  availability,
  questionIds: [101],
  questionCount: 1,
  startAt: null,
  dueAt: null,
  eventTimestamp: '2026-10-24T00:00:00Z',
  createdAt: '2026-10-24T00:00:00Z',
  updatedAt: '2026-10-24T00:00:00Z',
});
async function setup(page: Page) {
  const requests: string[] = [],
    writes: string[] = [],
    payloads: Array<Record<string, unknown>> = [];
  let tasks = [
    task(1, 'PUBLISHED', 1, 'OPEN'),
    task(2, 'DRAFT'),
    task(3, 'CLOSED'),
    task(4, 'PUBLISHED', 2, 'OPEN'),
  ];
  await page.addInitScript(() => {
    localStorage.setItem('zhixue_token', 'teacher-workflow-fixture');
    localStorage.setItem('zxwl.activeClassId', '1');
  });
  await page.route('**/api/v1/**', async (route) => {
    if (!['fetch', 'xhr'].includes(route.request().resourceType())) return route.continue();
    const url = new URL(route.request().url()),
      path = url.pathname.replace('/api/v1', '');
    requests.push(path + url.search);
    if (route.request().method() !== 'GET') writes.push(`${route.request().method()} ${path}`);
    let data: unknown = [];
    if (path === '/user/me') data = { id: 99, realName: '教师', userType: 'TEACHER' };
    else if (path === '/teacher/classes')
      data = [1, 2].map((id) => ({ id, name: `${id}班`, role: 'OWNER', status: 'ACTIVE' }));
    else if (path === '/teacher/events/all') data = tasks;
    else if (path === '/teacher/events/overview')
      data = tasks.map((t) => ({
        taskId: t.id,
        classId: t.classId,
        completedStudents: 12,
        totalStudents: 30,
        completionRate: 40,
      }));
    else if (path === '/teacher/events/history')
      data = { records: [task(8, 'ARCHIVED')], total: 1, pages: 1, currentPage: 1 };
    else if (path === '/teacher/events' && route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      payloads.push(body);
      const created = { ...task(tasks.length + 10, body.status), ...body };
      tasks.push(created);
      data = created;
    } else if (path === '/teacher/events/1' && route.request().method() === 'DELETE') {
      tasks = tasks.filter((t) => t.id !== 1);
      data = null;
    } else if (path === '/questions/teacher/page') {
      const n = Number(url.searchParams.get('page') ?? 1);
      data = {
        records: [
          {
            id: n === 1 ? 101 : 121,
            content: `题目 ${n === 1 ? 101 : 121}`,
            difficulty: 2,
            hasImage: false,
          },
        ],
        total: 21,
        pages: 2,
      };
    } else if (path === '/questions/teacher/get/101')
      data = {
        id: 101,
        content: '完整力学题目',
        options: { A: '选项一', B: '选项二' },
        correctOption: 'B',
        explanation: '根据牛顿第二定律',
        difficulty: 2,
      };
    return route.fulfill({ json: { code: 200, data } });
  });
  return { requests, writes, payloads };
}

test('tasks start with a scoped list, status filters and an explicit editor entry', async ({
  page,
}) => {
  await setup(page);
  await page.goto('teacher/event-manage');
  await expect(page.getByTestId('task-total')).toHaveText('3');
  await expect(page.locator('.task-row')).toHaveCount(3);
  await expect(page.getByLabel('任务名称', { exact: true })).toHaveCount(0);
  await expect(page.locator('.bank-item')).toHaveCount(0);
  await page.getByRole('tab', { name: /草稿/ }).click();
  await expect(page.locator('.task-row')).toHaveCount(1);
  await expect(page.locator('.task-row')).toContainText('任务2');
  await page.getByRole('tab', { name: /全部/ }).click();
  await page.getByPlaceholder('搜索任务名称…').fill('任务3');
  await expect(page.locator('.task-row')).toHaveCount(1);
  await expect(page.locator('.task-row')).toContainText('任务3');
});

test('archive confirmation names the task and cancel never writes', async ({ page }) => {
  const { writes } = await setup(page);
  await page.goto('teacher/event-manage');
  const row = page.locator('.task-row').filter({ hasText: '任务1' });
  await row.getByRole('button', { name: '更多操作' }).click();
  await row.getByRole('button', { name: '归档任务', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '归档任务' });
  await expect(dialog).toContainText('任务1');
  await dialog.getByRole('button', { name: '取消', exact: true }).click();
  expect(writes).toEqual([]);
  await row.getByRole('button', { name: '更多操作' }).click();
  await row.getByRole('button', { name: '归档任务', exact: true }).click();
  await dialog.getByRole('button', { name: '确认归档' }).click();
  await expect(page.locator('.task-row')).toHaveCount(2);
  expect(writes).toEqual(['DELETE /teacher/events/1']);
});

test('question selection persists across pages, previews full content and filters by difficulty', async ({
  page,
}) => {
  const { requests } = await setup(page);
  await page.goto('teacher/event-manage');
  await page.getByRole('button', { name: '新建任务', exact: true }).click();
  await page.getByLabel('任务名称', { exact: true }).fill('新作业');
  await page.getByRole('button', { name: '从题库选题', exact: true }).click();
  await page.locator('.bank-item').getByRole('button', { name: '预览', exact: true }).click();
  const preview = page.locator('.picker-preview');
  await expect(preview).toContainText('完整力学题目');
  await expect(preview).toContainText('选项二');
  await page.locator('.bank-item').getByRole('checkbox').check();
  await page.getByRole('button', { name: '下一页', exact: true }).click();
  await page.locator('.bank-item').getByRole('checkbox').check();
  await page.getByLabel('题目难度').selectOption('2');
  await expect
    .poll(() =>
      requests.some((r) => r.includes('/questions/teacher/page') && r.includes('difficulty=2')),
    )
    .toBe(true);
  await expect(page.locator('.picker-footer')).toContainText('已选 2 / 200 题');
  await page.getByRole('button', { name: '完成选题', exact: true }).click();
  await expect(page.locator('.selected-content')).toHaveText(['题目 101', '题目 121']);
});

test('archived requests use the active class and changing class resets the list', async ({
  page,
}) => {
  const { requests } = await setup(page);
  await page.goto('teacher/event-manage');
  await page.getByRole('tab', { name: '已归档', exact: true }).click();
  await expect
    .poll(() =>
      requests.some((r) => r.includes('/teacher/events/history') && r.includes('classId=1')),
    )
    .toBe(true);
  await page.getByRole('combobox', { name: '班级', exact: true }).selectOption('2');
  await expect
    .poll(() =>
      requests.some((r) => r.includes('/teacher/events/history') && r.includes('classId=2')),
    )
    .toBe(true);
});

test('mobile question picker restores focus and long task actions remain reachable', async ({
  page,
}) => {
  await setup(page);
  await page.route('**/api/v1/questions/teacher/page*', (route) =>
    route.fulfill({
      json: {
        code: 200,
        data: {
          records: Array.from({ length: 20 }, (_, i) => ({
            id: 101 + i,
            content: `题目 ${101 + i}`,
            difficulty: 2,
            hasImage: false,
          })),
          total: 20,
          pages: 1,
        },
      },
    }),
  );
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('teacher/event-manage');
  await page.getByRole('button', { name: '新建任务', exact: true }).click();
  const trigger = page.getByRole('button', { name: '从题库选题', exact: true });
  await trigger.click();
  await expect(page.getByRole('dialog', { name: '从题库选题' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(trigger).toBeFocused();
  await trigger.click();
  await page.getByRole('checkbox', { name: '选择本页题目', exact: true }).check();
  await page.getByRole('button', { name: '完成选题', exact: true }).click();
  await expect(page.locator('.selected-content')).toHaveCount(20);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button', { name: '保存草稿', exact: true }).scrollIntoViewIfNeeded();
  const actions = await page.getByRole('button', { name: '保存草稿', exact: true }).boundingBox();
  const navigation = await page
    .getByRole('navigation', { name: '教师底部导航', exact: true })
    .boundingBox();
  expect(actions!.y + actions!.height).toBeLessThanOrEqual(navigation!.y);
});

test('draft and publish preserve the class, selected order and list workflow', async ({ page }) => {
  const { payloads } = await setup(page);
  await page.goto('teacher/event-manage');
  await page.getByRole('button', { name: '新建任务', exact: true }).click();
  await page.getByLabel('任务名称', { exact: true }).fill('新的草稿');
  await page.getByRole('button', { name: '从题库选题', exact: true }).click();
  await page.locator('.bank-item').getByRole('checkbox').check();
  await page.getByRole('button', { name: '下一页', exact: true }).click();
  await page.locator('.bank-item').getByRole('checkbox').check();
  await page.getByRole('button', { name: '完成选题', exact: true }).click();
  await page.getByRole('button', { name: '上移题目 121' }).click();
  await page.getByRole('button', { name: '保存草稿', exact: true }).click();
  await expect(page.getByRole('tab', { name: /草稿/ })).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('.task-row').filter({ hasText: '新的草稿' })).toBeVisible();
  expect(payloads[0]).toMatchObject({
    eventTitle: '新的草稿',
    classId: 1,
    status: 'DRAFT',
    questionIds: [121, 101],
  });
  await page.getByRole('button', { name: '新建任务', exact: true }).click();
  await page.getByLabel('任务名称', { exact: true }).fill('新的作业');
  await page.getByRole('button', { name: '从题库选题', exact: true }).click();
  await page.locator('.bank-item').getByRole('checkbox').check();
  await page.getByRole('button', { name: '完成选题', exact: true }).click();
  await page.getByRole('button', { name: '发布任务', exact: true }).click();
  await expect(page.locator('.task-row').filter({ hasText: '新的作业' })).toBeVisible();
  expect(payloads[1]).toMatchObject({ classId: 1, status: 'PUBLISHED', questionIds: [101] });
});

test('unsaved task can cancel both leaving and switching class', async ({ page }) => {
  await setup(page);
  await page.goto('teacher/event-manage');
  await page.getByRole('button', { name: '新建任务', exact: true }).click();
  await page.getByLabel('任务名称', { exact: true }).fill('未保存的任务');
  page.once('dialog', (dialog) => dialog.dismiss());
  await page.getByRole('button', { name: '返回任务列表', exact: true }).click();
  await expect(page.getByLabel('任务名称', { exact: true })).toHaveValue('未保存的任务');
  page.once('dialog', (dialog) => dialog.dismiss());
  await page.getByRole('combobox', { name: '班级', exact: true }).selectOption('2');
  await expect(page.getByRole('combobox', { name: '班级', exact: true })).toHaveValue('1');
  await expect(page.getByLabel('任务名称', { exact: true })).toHaveValue('未保存的任务');
});
