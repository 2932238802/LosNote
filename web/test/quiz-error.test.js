import { test } from 'node:test';
import assert from 'node:assert/strict';
import { quizErrorText } from '../src/quiz-error.js';

function apiError(message, status) {
  const error = new Error(message);
  error.status = status;
  return error;
}

test('401 返回重新登录提示', () => {
  assert.equal(
    quizErrorText(apiError('令牌无效或缺失', 401)),
    '令牌无效或已失效，请到「设置」重新登录',
  );
});

test('浏览器网络失败返回检查网络提示', () => {
  assert.equal(quizErrorText(new TypeError('Failed to fetch')), '连不上服务器，请检查网络后重试');
  assert.equal(quizErrorText(new TypeError('Load failed')), '连不上服务器，请检查网络后重试');
  assert.equal(quizErrorText(new Error('NetworkError when attempting to fetch resource.')), '连不上服务器，请检查网络后重试');
});

test('其他接口错误透传可读信息', () => {
  assert.equal(quizErrorText(apiError('请求失败（500）', 500)), '请求失败（500）');
});

test('缺少错误信息时给出兜底文案', () => {
  assert.equal(quizErrorText(null), '取题失败，请重试');
  assert.equal(quizErrorText({}), '取题失败，请重试');
});
