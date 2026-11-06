/** 单用户令牌鉴权：Authorization: Bearer <LOSNOTE_TOKEN> */
import { timingSafeEqual } from 'node:crypto';

export function sameToken(a, b) {
  const ab = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  if (ab.length !== bb.length) return false;
  return timingSafeEqual(ab, bb);
}

/**
 * 从请求里取出令牌。也允许 `?token=` 与 `X-Token`，方便手机端和浏览器直连。
 * @returns {string}
 */
export function tokenFromRequest(req, url) {
  const auth = req.headers['authorization'] || '';
  if (auth.toLowerCase().startsWith('bearer ')) return auth.slice(7).trim();
  const header = req.headers['x-token'];
  if (header) return String(header);
  return url.searchParams.get('token') || '';
}

export function isAuthorized(req, url, expected) {
  if (!expected) return false;
  return sameToken(tokenFromRequest(req, url), expected);
}
