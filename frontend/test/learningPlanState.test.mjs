import {test} from 'node:test';
import assert from 'node:assert/strict';
import {acceptLearningPlanEvent, startLearningPlanRun} from '../src/composables/learningPlanState.ts';

test('late events from an older run cannot replace the current preview',()=>{
 const current=startLearningPlanRun('new-run');
 const result=acceptLearningPlanEvent(current,{runId:'old-run',seq:2},state=>({...state,error:'stale'}));
 assert.equal(result,current);
});

test('events must arrive in increasing sequence for the active run',()=>{
 const started=startLearningPlanRun('active-run');
 const first=acceptLearningPlanEvent(started,{runId:'active-run',seq:1},state=>({...state,error:'first'}));
 const duplicate=acceptLearningPlanEvent(first,{runId:'active-run',seq:1},state=>({...state,error:'duplicate'}));
 const second=acceptLearningPlanEvent(duplicate,{runId:'active-run',seq:2},state=>({...state,error:'second'}));
 assert.equal(duplicate.error,'first');
 assert.equal(second.error,'second');
 assert.equal(second.lastSeq,2);
});
