import {test} from 'node:test';
import assert from 'node:assert/strict';
import {requiredAICapability} from '../src/utils/aiCapabilities.ts';
import {scopedPracticeRows} from '../src/utils/practiceReview.ts';
test('all AI operations use the capability of their actual provider',()=>{
 for(const url of ['/ai/student/report?eventId=1','/ai/teacher/report']) assert.equal(requiredAICapability(url),'reports',url);
 for(const url of ['/ai/student/realtimeQA','/ai/student/wrong-question-analysis']) assert.equal(requiredAICapability(url),'tutoring',url);
});
test('same wording in different tasks preserves each mastery state',()=>{
 const rows=[{recordId:'new',eventId:2,questionId:9,correct:true,questionContent:'same'},{recordId:'old',eventId:1,questionId:9,correct:false,questionContent:'same'}];
 assert.deepEqual(scopedPracticeRows(rows),rows);
});
