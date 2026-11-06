import test from 'node:test';
import assert from 'node:assert/strict';

import { apply, AGAIN, GOOD, EASY, DAY_MS } from '../src/srs.js';

const base = () => ({
  mastery: 0,
  intervalDays: 0,
  easeFactor: 250,
  reviewCount: 0,
  nextReview: 0,
  lastReview: 0,
});

test('新题评 GOOD：间隔 1 天，系数不变', () => {
  const n = base();
  const now = 1_700_000_000_000;
  apply(n, GOOD, now);
  assert.equal(n.intervalDays, 1);
  assert.equal(n.easeFactor, 250);
  assert.equal(n.reviewCount, 1);
  assert.equal(n.mastery, 1);
  assert.equal(n.nextReview, now + 1 * DAY_MS);
});

test('新题评 EASY：间隔 4 天，系数 +15', () => {
  const n = base();
  apply(n, EASY, 0);
  assert.equal(n.intervalDays, 4);
  assert.equal(n.easeFactor, 265);
  assert.equal(n.reviewCount, 1);
  assert.equal(n.mastery, 1);
});

test('新题评 AGAIN：间隔 0，10 分钟后再来，系数 -20', () => {
  const n = base();
  const now = 1000;
  apply(n, AGAIN, now);
  assert.equal(n.intervalDays, 0);
  assert.equal(n.easeFactor, 230);
  assert.equal(n.mastery, 0);
  assert.equal(n.nextReview, now + 10 * 60 * 1000);
});

test('第二题序位评 GOOD：间隔 3 天', () => {
  const n = { ...base(), reviewCount: 1, intervalDays: 1, mastery: 1 };
  apply(n, GOOD, 0);
  assert.equal(n.intervalDays, 3);
  assert.equal(n.reviewCount, 2);
});

test('EASY 间隔按系数增长，系数封顶 350', () => {
  const n = { ...base(), reviewCount: 3, intervalDays: 10, easeFactor: 280, mastery: 1 };
  apply(n, EASY, 0);
  assert.equal(n.intervalDays, Math.ceil(10 * 2.8 * 1.3)); // 37
  assert.equal(n.easeFactor, 295);
});

test('系数下限 130', () => {
  const n = { ...base(), easeFactor: 135, reviewCount: 2 };
  apply(n, AGAIN, 0);
  assert.equal(n.easeFactor, 130);
});

test('GOOD 增长使用变动前的系数', () => {
  const n = { ...base(), reviewCount: 5, intervalDays: 7, easeFactor: 200, mastery: 1 };
  apply(n, GOOD, 0);
  assert.equal(n.intervalDays, 14); // 7 * 2.0
  assert.equal(n.easeFactor, 200);
});
