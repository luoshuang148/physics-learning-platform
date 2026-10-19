import { test, expect } from './support/isolatedTest';
const base=process.env.E2E_BASE_URL??'http://127.0.0.1:18080/api/v1/';
const url=(p:string)=>new URL(p,base).toString();

test('lost submission response and same-task practice preserve original results',async({browser,request})=>{
 const login=await request.post(url('auth/login'),{data:{username:process.env.E2E_TEACHER_USERNAME??'e2e_teacher',password:process.env.E2E_TEACHER_PASSWORD??'E2e-teacher-fixture-2026'}});
 const teacherToken=(await login.json()).data.token;const th={Authorization:`Bearer ${teacherToken}`};
 const tag=Date.now();const qs=[];
 for(let n=1;n<=2;n++){
  const response=await request.post(url('questions/teacher/create'),{headers:th,data:{content:`recovery ${tag} question ${n}`,options:'{"A":"correct","B":"wrong","C":"other","D":"none"}',correctOption:'A',difficulty:1}});
  expect(response.ok(),await response.text()).toBeTruthy();qs.push((await response.json()).data);
 }
 const task=(await(await request.post(url('teacher/events'),{headers:th,data:{eventTitle:'recovery task',questionIds:qs.map(q=>q.id),eventTimestamp:'2026-10-18T00:00:00'}})).json()).data;
 const name=`recovery_${tag}`;const password='Recovery-fixture-2026';
 expect((await request.post(url('auth/register'),{data:{username:name,realName:name,password,userType:'STUDENT'}})).ok()).toBeTruthy();
 const context=await browser.newContext();const page=await context.newPage();
 await page.goto(url('auth/login'));await page.getByPlaceholder('请输入用户名').fill(name);await page.getByPlaceholder('请输入密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await expect(page).toHaveURL(/student\/home$/);
 const token=await page.evaluate(()=>localStorage.getItem('zhixue_token'));const sh={Authorization:`Bearer ${token}`};
 // An already completed second question must survive restoration of the first draft.
 expect((await request.post(url('answer-records/student/submit'),{headers:sh,data:{eventId:task.id,requestId:`q2-${tag}`,intent:'ASSIGNMENT',questionId:qs[1].id,userAnswer:'A'}})).ok()).toBeTruthy();
 await page.goto(url('student/task'));await page.locator('.task-item').filter({hasText:'recovery task'}).getByRole('button',{name:'继续作答'}).click();await expect(page).toHaveURL(/student\/question$/);
 let committedPayload:any;
 await page.route('**/answer-records/student/submit',async route=>{
  committedPayload=route.request().postDataJSON();const response=await route.fetch();expect(response.ok()).toBeTruthy();await route.abort('failed');
 },{times:1});
 await page.locator('label.option-row').filter({hasText:'wrong'}).click();await page.getByRole('button',{name:'提交答案',exact:true}).click();
 await expect(page.locator('.q-submit-row-pending')).toBeVisible();
 await expect.poll(()=>committedPayload?.requestId).toBeTruthy();
 const savedRequestId=committedPayload.requestId;
 const drafts=await page.evaluate(()=>Object.keys(sessionStorage).filter(k=>k.startsWith('student_question_view_state')).map(k=>JSON.parse(sessionStorage.getItem(k)??'{}')));
 expect(drafts.some(d=>d.pendingAnswer?.submission?.requestId===savedRequestId)).toBeTruthy();
 await page.getByRole('button',{name:'重试保存作答',exact:true}).click();
 await expect(page.getByText('✗ 回答错误')).toBeVisible();
 await expect(page.locator('.q-save-feedback')).toContainText('本题答案已提交成功');
 await page.reload();await expect(page.locator('.q-dot.wrong')).toHaveCount(1);await expect(page.locator('.q-dot.correct')).toHaveCount(1);
 const before=(await(await request.get(url(`answer-records/student/task/${task.id}`),{headers:sh})).json()).data.records;
 expect(before.filter((r:any)=>!r.practice)).toHaveLength(2);
 await page.goto(url(`student/practice?eventId=${task.id}&questionId=${qs[0].id}`));
 await page.locator('label.option-row').filter({hasText:'correct'}).click();await page.getByRole('button',{name:'提交答案',exact:true}).click();await expect(page.locator('.q-save-feedback')).toBeVisible();
 await page.goto(url('student/question'));await expect(page.locator('.q-dot.wrong')).toHaveCount(1);await expect(page.locator('.q-dot.correct')).toHaveCount(1);
 await expect(page.locator('.result-box')).toContainText('回答错误');
 const after=(await(await request.get(url(`answer-records/student/task/${task.id}`),{headers:sh})).json()).data.records;
 expect(after.filter((r:any)=>!r.practice)).toHaveLength(2);expect(after.filter((r:any)=>r.practice)).toHaveLength(1);
 await context.close();
});

test('practice drafts cannot overwrite the original result map for the same task',async({browser,request})=>{
 const login=(await(await request.post(url('auth/login'),{data:{username:process.env.E2E_TEACHER_USERNAME??'e2e_teacher',password:process.env.E2E_TEACHER_PASSWORD??'E2e-teacher-fixture-2026'}})).json()).data;
 const th={Authorization:`Bearer ${login.token}`};
 const tag=Date.now(),qs:any[]=[];
 for(let n=1;n<=2;n++)qs.push((await(await request.post(url('questions/teacher/create'),{headers:th,data:{content:`practice scope ${tag} question ${n}`,options:'{"A":"correct","B":"wrong","C":"other","D":"none"}',correctOption:'A',difficulty:1}})).json()).data);
 const task=(await(await request.post(url('teacher/events'),{headers:th,data:{eventTitle:'practice scope',questionIds:qs.map(q=>q.id),eventTimestamp:'2026-10-18T00:00:00'}})).json()).data;
 const name=`practice_${tag}`,password='Practice-fixture-2026';await request.post(url('auth/register'),{data:{username:name,realName:name,password,userType:'STUDENT'}});
 const context=await browser.newContext(),page=await context.newPage();await page.goto(url('auth/login'));await page.getByPlaceholder('请输入用户名').fill(name);await page.getByPlaceholder('请输入密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录',exact:true}).click();await expect(page).toHaveURL(/student\/home$/)
 const token=await page.evaluate(()=>localStorage.getItem('zhixue_token')),sh={Authorization:`Bearer ${token}`};
 for(let n=0;n<2;n++)expect((await request.post(url('answer-records/student/submit'),{headers:sh,data:{eventId:task.id,requestId:`scope-${tag}-${n}`,intent:'ASSIGNMENT',questionId:qs[n].id,userAnswer:n===0?'B':'A'}})).ok()).toBeTruthy();
 const me=(await(await request.get(url('user/me'),{headers:sh})).json()).data;
 await page.evaluate(({userId,eventId})=>sessionStorage.setItem('student_task_entry_event_id_'+userId,String(eventId)),{userId:me.id,eventId:task.id});
 await page.goto(url('student/question'));await expect(page).toHaveURL(/student\/question$/);
 await expect(page.locator('.q-dot.wrong')).toHaveCount(1);await expect(page.locator('.q-dot.correct')).toHaveCount(1);
 await page.goto(url(`student/practice?eventId=${task.id}&questionId=${qs[0].id}`));await page.locator('label.option-row').filter({hasText:'correct'}).click();await page.getByRole('button',{name:'提交答案',exact:true}).click();await expect(page.locator('.q-save-feedback')).toBeVisible();
 await page.goto(url('student/question'));await expect(page.locator('.q-dot.wrong')).toHaveCount(1);await expect(page.locator('.q-dot.correct')).toHaveCount(1);await expect(page.locator('.result-box')).toContainText('回答错误');await context.close();
});