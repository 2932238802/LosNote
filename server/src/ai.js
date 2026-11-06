/**
 * AI 相关接口：配置读写 + 「AI 整理导入」。
 *
 * 与安卓端保持一致：OpenAI 兼容 Chat Completions；提示词、温度、去围栏逻辑都照搬
 * AddFragment.callDeepSeekOrganize + AiClient。
 *
 * Key 存在服务器上，网页端只看到「是否已配置」，不回传明文。
 */
import { getSetting, setSetting } from './db.js';

const K_BASE = 'ai_base_url';
const K_MODEL = 'ai_model';
const K_KEY = 'ai_api_key';
const K_SEARCH = 'ai_web_search';

const SYSTEM_PROMPT = '你只输出合法 JSON。';

function stripFence(content) {
  if (!content) return '';
  return content
    .trim()
    .replace(/^```json\s*/, '')
    .replace(/^```\s*/, '')
    .replace(/\s*```$/, '')
    .trim();
}

function readConfig(db) {
  return {
    baseUrl: getSetting(db, K_BASE, '') || '',
    model: getSetting(db, K_MODEL, '') || '',
    apiKey: getSetting(db, K_KEY, '') || '',
    webSearch: getSetting(db, K_SEARCH, '0') === '1',
  };
}

/** 调用 OpenAI 兼容接口，返回文本 */
async function chat(config, system, user, temperature) {
  if (!config.apiKey) throw Object.assign(new Error('服务端还没有配置 AI API Key'), { status: 400 });
  if (!config.baseUrl) throw Object.assign(new Error('服务端还没有配置 Base URL'), { status: 400 });
  if (!config.model) throw Object.assign(new Error('服务端还没有配置模型名'), { status: 400 });

  const finalUser = config.webSearch
    ? '如果你的模型/服务支持联网搜索，请优先结合最新网络信息；如果不支持，请基于已有知识回答。\n\n' + user
    : user;

  let resp;
  try {
    resp = await fetch(config.baseUrl, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        authorization: `Bearer ${config.apiKey}`,
      },
      body: JSON.stringify({
        model: config.model,
        messages: [
          { role: 'system', content: system },
          { role: 'user', content: finalUser },
        ],
        temperature,
      }),
      signal: AbortSignal.timeout(120000),
    });
  } catch (err) {
    throw Object.assign(new Error(`AI 请求失败：${err.message}`), { status: 502 });
  }

  const text = await resp.text();
  if (!resp.ok) {
    throw Object.assign(new Error(`AI 返回 ${resp.status}：${text.slice(0, 500)}`), { status: 502 });
  }
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    throw Object.assign(new Error('AI 返回的不是 JSON'), { status: 502 });
  }
  const content = json?.choices?.[0]?.message?.content;
  if (typeof content !== 'string') {
    throw Object.assign(new Error('AI 响应里没有 choices[0].message.content'), { status: 502 });
  }
  return content.trim();
}

export function aiRoutes(route, db, readJson) {
  route('GET', /^\/api\/settings\/ai$/, () => {
    const c = readConfig(db);
    return {
      baseUrl: c.baseUrl,
      model: c.model,
      webSearch: c.webSearch,
      hasKey: Boolean(c.apiKey),
    };
  });

  route('PUT', /^\/api\/settings\/ai$/, async ({ req }) => {
    const body = await readJson(req);
    if (body.baseUrl != null) setSetting(db, K_BASE, String(body.baseUrl).trim());
    if (body.model != null) setSetting(db, K_MODEL, String(body.model).trim());
    if (body.apiKey != null && String(body.apiKey) !== '') {
      setSetting(db, K_KEY, String(body.apiKey).trim());
    }
    if (body.clearKey === true) setSetting(db, K_KEY, '');
    if (body.webSearch != null) setSetting(db, K_SEARCH, body.webSearch ? '1' : '0');
    const c = readConfig(db);
    return { baseUrl: c.baseUrl, model: c.model, webSearch: c.webSearch, hasKey: Boolean(c.apiKey) };
  });

  /** 把一段杂乱笔记整理成题目（只解析，不落库，前端预览后再 commit） */
  route('POST', /^\/api\/ai\/format$/, async ({ req }) => {
    const body = await readJson(req);
    const raw = String(body.text || '');
    if (!raw.trim()) throw Object.assign(new Error('内容不能为空'), { status: 400 });
    const category = String(body.category || '未分类').trim() || '未分类';
    const sample = raw.length > 45000 ? raw.slice(0, 45000) : raw;

    const prompt =
      '你是题库整理助手。请把用户提供的笔记整理为 JSON 数组。' +
      '每个元素包含 name、definition、category 三个字段。' +
      'name 是题目，definition 是答案/定义，尽量保留 Markdown、代码块、列表和表格。' +
      'category 统一填：' +
      category +
      '。只输出 JSON 数组，不要解释，不要 Markdown 代码围栏。\n\n笔记内容：\n' +
      sample;

    const content = stripFence(await chat(readConfig(db), SYSTEM_PROMPT, prompt, 0.1));
    let arr;
    try {
      arr = JSON.parse(content);
    } catch {
      throw Object.assign(new Error(`AI 输出的不是合法 JSON：${content.slice(0, 200)}`), {
        status: 502,
      });
    }
    if (!Array.isArray(arr)) {
      throw Object.assign(new Error('AI 输出的不是数组'), { status: 502 });
    }
    const notes = [];
    for (const o of arr) {
      const name = String(o?.name || '').trim();
      if (!name) continue;
      let cat = String(o?.category || category).trim();
      if (!cat) cat = category;
      notes.push({ name, definition: String(o?.definition || '').trim(), category: cat });
    }
    return { count: notes.length, notes };
  });
}
