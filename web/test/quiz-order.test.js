import { test } from 'node:test';
import assert from 'node:assert/strict';
import { shuffle, orderForStart } from '../src/quiz-order.js';

// 线性同余随机源：固定种子保证测试可复现
function rng(seed) {
  let state = seed >>> 0;
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    return state / 4294967296;
  };
}

function makeItems(n) {
  return Array.from({ length: n }, (_, i) => ({ uuid: `n${i + 1}`, name: `题 ${i + 1}` }));
}

function uuids(items) {
  return items.map((item) => item.uuid);
}

function assertSameSet(actual, expected) {
  assert.deepEqual([...uuids(actual)].sort(), [...uuids(expected)].sort());
}

test('洗牌返回完整排列，且不修改入参', () => {
  const input = makeItems(6);
  const before = uuids(input);
  const out = shuffle(input, rng(7));
  assert.deepEqual(uuids(input), before);
  assertSameSet(out, input);
});

test('到期题 0 道返回空数组', () => {
  assert.deepEqual(orderForStart([], 'n1', rng(1)), []);
  assert.deepEqual(orderForStart(null, 'n1', rng(1)), []);
});

test('到期题 1 道允许重复，不报错', () => {
  const only = makeItems(1);
  const out = orderForStart(only, 'n1', rng(1));
  assert.deepEqual(uuids(out), ['n1']);
  const firstStart = orderForStart(only, '', rng(2));
  assert.deepEqual(uuids(firstStart), ['n1']);
});

test('到期题 > 1：老服务端固定顺序下，连续 20 次首题都不重复且至少 3 种', () => {
  // 固定同一批题目顺序 = 模拟线上尚未升级的确定性排序服务端
  const pool = makeItems(6);
  const random = rng(20261118);
  const firsts = [];
  let previous = '';

  for (let i = 0; i < 20; i += 1) {
    const queue = orderForStart(pool, previous, random);
    assert.equal(queue.length, pool.length);
    assertSameSet(queue, pool);
    if (previous) assert.notEqual(queue[0].uuid, previous, `第 ${i + 1} 次首题与上一次相同`);
    previous = queue[0].uuid;
    firsts.push(previous);
  }

  assert.ok(new Set(firsts).size >= 3, `20 次只出现了 ${new Set(firsts).size} 种首题`);
});

test('到期题 > 1：50 组随机种子下每次首题都避开上一次', () => {
  for (let seed = 1; seed <= 50; seed += 1) {
    const random = rng(seed * 2654435761);
    const pool = makeItems(5);
    let previous = '';
    for (let i = 0; i < 20; i += 1) {
      const queue = orderForStart(pool, previous, random);
      assertSameSet(queue, pool);
      if (previous) assert.notEqual(queue[0].uuid, previous);
      previous = queue[0].uuid;
    }
  }
});

test('到期题 2 道时首题仍在两题间交替，不会卡死', () => {
  const pool = makeItems(2);
  const random = rng(42);
  let previous = '';
  const firsts = [];
  for (let i = 0; i < 10; i += 1) {
    const queue = orderForStart(pool, previous, random);
    if (previous) assert.notEqual(queue[0].uuid, previous);
    previous = queue[0].uuid;
    firsts.push(previous);
  }
  assert.equal(new Set(firsts).size, 2);
});

test('上一次首题不在本次到期集合时，正常洗牌且不丢题', () => {
  const pool = makeItems(5);
  const out = orderForStart(pool, 'n99', rng(3));
  assertSameSet(out, pool);
  assert.equal(out.length, 5);
});

test('数据异常（全部 uuid 相同）时不死循环、不丢题', () => {
  const same = [{ uuid: 'x' }, { uuid: 'x' }, { uuid: 'x' }];
  const out = orderForStart(same, 'x', rng(9));
  assert.equal(out.length, 3);
  assert.deepEqual(uuids(out), ['x', 'x', 'x']);
});
