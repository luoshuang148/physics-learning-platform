import {test} from 'node:test';
import assert from 'node:assert/strict';
import {dispatchAIStreamEvent} from '../src/utils/aiEventDispatch.ts';

test('sources_event_is_not_rendered_as_answer_text',()=>{
 const calls=[];
 dispatchAIStreamEvent('sources','[{"sourceId":"S1"}]',{onMessage:value=>calls.push(['message',value]),onSources:value=>calls.push(['sources',value])});
 assert.deepEqual(calls,[['sources',[{sourceId:'S1'}]]]);
});
test('legacy_message_event_still_works',()=>{
 const calls=[];
 dispatchAIStreamEvent('message','普通旧格式回答',{onMessage:value=>calls.push(value)});
 assert.deepEqual(calls,[{content:'普通旧格式回答'}]);
});
test('old_run_events_are_ignored',()=>{
 const calls=[];
 dispatchAIStreamEvent('run-step','{"step":"old"}',{onMessage:value=>calls.push(value),onToolStep:value=>calls.push(value)});
 assert.deepEqual(calls,[]);
});
