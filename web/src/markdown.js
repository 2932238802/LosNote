import { marked } from 'marked';
import hljs from 'highlight.js/lib/common';
import DOMPurify from 'dompurify';

marked.setOptions({ gfm: true, breaks: true });

/** Markdown -> 安全 HTML 字符串 */
export function renderMarkdown(source) {
  const raw = marked.parse(source || '');
  return DOMPurify.sanitize(raw, { ADD_ATTR: ['target'] });
}

/** 渲染进某个元素，并顺带做代码高亮（避免依赖 marked 渲染器的版本差异） */
export function renderInto(el, source) {
  if (!el) return;
  el.innerHTML = renderMarkdown(source);
  el.querySelectorAll('pre code').forEach((block) => {
    try {
      hljs.highlightElement(block);
    } catch {
      /* 高亮失败不影响正文 */
    }
  });
  el.querySelectorAll('a').forEach((a) => {
    if (a.host && a.host !== location.host) {
      a.target = '_blank';
      a.rel = 'noopener noreferrer';
    }
  });
}

/** 纯文本摘要，用于列表里的一行预览 */
export function plainSummary(source, max = 120) {
  const text = (source || '')
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[#>*_~|-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

/** 相对时间：3 天后 / 2 小时前 */
export function humanDue(note, now = Date.now()) {
  if (!note.reviewCount || !note.nextReview) return '新题';
  const diff = note.nextReview - now;
  if (diff <= 0) return '待复习';
  const day = 24 * 60 * 60 * 1000;
  const days = Math.floor(diff / day);
  if (days >= 1) return `${days} 天后`;
  const hours = Math.floor(diff / (60 * 60 * 1000));
  if (hours >= 1) return `${hours} 小时后`;
  const mins = Math.max(1, Math.floor(diff / 60000));
  return `${mins} 分钟后`;
}
