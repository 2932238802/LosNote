const TOKEN_KEY = 'losnote.token';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || '';
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

async function request(method, path, body) {
  const headers = {};
  const token = getToken();
  if (token) headers.authorization = `Bearer ${token}`;
  if (body !== undefined) headers['content-type'] = 'application/json';

  const res = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!res.ok) {
    throw new ApiError(data?.error || `请求失败（${res.status}）`, res.status);
  }
  return data;
}

export const api = {
  health: () => request('GET', '/api/health'),

  notes: ({ q = '', category = '', limit = 300, offset = 0 } = {}) => {
    const p = new URLSearchParams();
    if (q) p.set('q', q);
    if (category) p.set('category', category);
    p.set('limit', String(limit));
    p.set('offset', String(offset));
    return request('GET', `/api/notes?${p.toString()}`);
  },

  note: (uuid) => request('GET', `/api/notes/${encodeURIComponent(uuid)}`),
  createNote: (note) => request('POST', '/api/notes', note),
  updateNote: (uuid, patch) => request('PATCH', `/api/notes/${encodeURIComponent(uuid)}`, patch),
  deleteNote: (uuid) => request('DELETE', `/api/notes/${encodeURIComponent(uuid)}`),

  categories: () => request('GET', '/api/categories'),
  due: ({ category = '', limit = 100 } = {}) => {
    const p = new URLSearchParams();
    if (category) p.set('category', category);
    p.set('limit', String(limit));
    return request('GET', `/api/quiz/due?${p.toString()}`);
  },
  review: (uuid, grade) =>
    request('POST', `/api/notes/${encodeURIComponent(uuid)}/review`, { grade }),

  parse: (text, category) => request('POST', '/api/import/parse', { text, category }),
  commit: (notes, category) => request('POST', '/api/import/commit', { notes, category }),

  pull: (since, limit = 500) =>
    request('GET', `/api/sync?since=${encodeURIComponent(since)}&limit=${limit}`),
  push: (body) => request('POST', '/api/sync', body),

  aiSettings: () => request('GET', '/api/settings/ai'),
  saveAiSettings: (patch) => request('PUT', '/api/settings/ai', patch),
  aiFormat: (text, category) => request('POST', '/api/ai/format', { text, category }),
};
