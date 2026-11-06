import test from 'node:test';
import assert from 'node:assert/strict';

import { parseBatchText } from '../src/importer.js';

test('@@@ 多行：块首行是名称，其余是定义', () => {
  const text = `闭包是什么
一个函数与其词法环境的组合。

@@@
SRS 间隔重复
按记忆曲线安排复习间隔。
`;
  const notes = parseBatchText(text, '基础');
  assert.equal(notes.length, 2);
  assert.equal(notes[0].name, '闭包是什么');
  assert.equal(notes[0].definition, '一个函数与其词法环境的组合。');
  assert.equal(notes[0].category, '基础');
  assert.equal(notes[1].name, 'SRS 间隔重复');
  assert.equal(notes[1].definition, '按记忆曲线安排复习间隔。');
});

test('多行模式：单行块（无定义）也能识别', () => {
  const notes = parseBatchText('只有标题\n@@@\n第二条', '默认');
  assert.equal(notes.length, 2);
  assert.equal(notes[0].name, '只有标题');
  assert.equal(notes[0].definition, '');
  assert.equal(notes[1].name, '第二条');
});

test('竖线单行：两端是名称和栏目', () => {
  const notes = parseBatchText('TCP 三次握手 | 建立连接 | 网络', '默认');
  assert.deepEqual(notes[0], {
    name: 'TCP 三次握手',
    definition: '建立连接',
    category: '网络',
  });
});

test('竖线两段：第二段是定义，栏目取默认', () => {
  const notes = parseBatchText('HashMap | 数组+链表+红黑树', 'Java');
  assert.deepEqual(notes[0], {
    name: 'HashMap',
    definition: '数组+链表+红黑树',
    category: 'Java',
  });
});

test('竖线四段：中间各段用 " | " 拼回定义，末段是栏目', () => {
  const notes = parseBatchText('Markdown 表格 | a | b | 工具', '默认');
  assert.deepEqual(notes[0], {
    name: 'Markdown 表格',
    definition: 'a | b',
    category: '工具',
  });
});

test('全角竖线也认', () => {
  const notes = parseBatchText('全角｜定义｜栏目', '默认');
  assert.deepEqual(notes[0], { name: '全角', definition: '定义', category: '栏目' });
});

test('空名称与空行被跳过', () => {
  const notes = parseBatchText(' | 没名字\n\n有名字 | 定义', '默认');
  assert.equal(notes.length, 1);
  assert.equal(notes[0].name, '有名字');
});

test('空文本返回空数组', () => {
  assert.deepEqual(parseBatchText('', '默认'), []);
  assert.deepEqual(parseBatchText('   \n\n', '默认'), []);
});
