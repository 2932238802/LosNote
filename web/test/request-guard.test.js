import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createLatestRequestGuard } from '../src/request-guard.js';

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

test('后发请求使先发请求失效', () => {
  const guard = createLatestRequestGuard();
  const first = guard.begin();
  assert.equal(guard.isLatest(first), true);
  const second = guard.begin();
  assert.equal(guard.isLatest(first), false);
  assert.equal(guard.isLatest(second), true);
});

test('乱序响应：旧响应后到也不会覆盖新队列', async () => {
  const guard = createLatestRequestGuard();
  const applied = [];
  const oldResponse = deferred();
  const newResponse = deferred();

  async function run(token, pending) {
    const value = await pending.promise;
    if (!guard.isLatest(token)) return;
    applied.push(value);
  }

  const oldToken = guard.begin();
  const oldRun = run(oldToken, oldResponse);
  const newToken = guard.begin();
  const newRun = run(newToken, newResponse);

  newResponse.resolve('新栏目');
  await newRun;
  oldResponse.resolve('旧栏目');
  await oldRun;

  assert.deepEqual(applied, ['新栏目']);
});

test('旧请求先结束时不关闭最新请求的 loading', async () => {
  const guard = createLatestRequestGuard();
  let loading = false;
  const oldResponse = deferred();
  const newResponse = deferred();

  async function run(token, pending) {
    loading = true;
    try {
      await pending.promise;
      if (!guard.isLatest(token)) return;
    } finally {
      if (guard.isLatest(token)) loading = false;
    }
  }

  const oldToken = guard.begin();
  const oldRun = run(oldToken, oldResponse);
  const newToken = guard.begin();
  const newRun = run(newToken, newResponse);

  oldResponse.resolve();
  await oldRun;
  assert.equal(loading, true, '旧请求结束不应关闭新请求的 loading');

  newResponse.resolve();
  await newRun;
  assert.equal(loading, false);
});

test('旧请求失败不覆盖新请求的错误状态', async () => {
  const guard = createLatestRequestGuard();
  let error = '';
  const oldResponse = deferred();
  const newResponse = deferred();

  async function run(token, pending) {
    try {
      await pending.promise;
    } catch (e) {
      if (guard.isLatest(token)) error = e.message;
    }
  }

  const oldToken = guard.begin();
  const oldRun = run(oldToken, oldResponse);
  const newToken = guard.begin();
  const newRun = run(newToken, newResponse);

  oldResponse.reject(new Error('旧错误'));
  await oldRun;
  assert.equal(error, '');

  newResponse.reject(new Error('新错误'));
  await newRun;
  assert.equal(error, '新错误');
});
