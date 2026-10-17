import {test} from 'node:test';
import assert from 'node:assert/strict';
import {updateLoginInfo,listAccounts} from '../src/db/accountDb.ts';
test('account history never retains bearer credentials',()=>{
 const storage=new Map();globalThis.localStorage={getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)};
 updateLoginInfo('test_user','sensitive-bearer-token',1,'STUDENT','test');
 assert.equal(JSON.stringify(listAccounts()).includes('sensitive-bearer-token'),false);
});
