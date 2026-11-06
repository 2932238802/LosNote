/**
 * 批量文本解析 —— 与安卓 AddFragment 的 parseMultiline / parseSingleLine 对齐。
 *
 * 多行模式（文本里出现 `@@@` 时）：单独一行的 `@@@` 分隔；每块第一行是名称，
 * 其余各行（含换行）是定义。
 *
 * 单行模式：每行 `名称 | 定义 | 栏目`；只有两段时第二段是定义、栏目取默认；
 * 三段以上时中间各段用 " | " 拼回定义，末段是栏目。
 * 半角 `|` 和全角 `｜` 都认。
 */

/**
 * @param {string} raw 原始文本
 * @param {string} fallbackCategory 默认栏目
 * @returns {{name: string, definition: string, category: string}[]}
 */
export function parseBatchText(raw, fallbackCategory = '未分类') {
  if (typeof raw !== 'string' || !raw.trim()) return [];
  return raw.includes('@@@')
    ? parseMultiline(raw, fallbackCategory)
    : parseSingleLine(raw, fallbackCategory);
}

function parseMultiline(raw, fallbackCategory) {
  const notes = [];
  // Java: raw.split("(?m)^\\s*@@@\\s*$")
  const blocks = raw.split(/^[ \t]*@@@[ \t]*\r?$/m);
  for (const block of blocks) {
    const b = block.trim();
    if (!b) continue;
    const nl = b.indexOf('\n');
    let name;
    let def;
    if (nl < 0) {
      name = b.trim();
      def = '';
    } else {
      name = b.slice(0, nl).trim();
      def = b.slice(nl + 1).trim();
    }
    if (!name) continue;
    notes.push({ name, definition: def, category: fallbackCategory });
  }
  return notes;
}

function parseSingleLine(raw, fallbackCategory) {
  const notes = [];
  for (const line of raw.split(/\r?\n/)) {
    if (!line.trim()) continue;
    const parts = line.split(/[|｜]/);
    const name = (parts[0] || '').trim();
    let def = '';
    let category = '';
    if (parts.length === 2) {
      def = parts[1].trim();
    } else if (parts.length > 2) {
      def = parts
        .slice(1, -1)
        .map((p) => p.trim())
        .join(' | ');
      category = parts[parts.length - 1].trim();
    }
    if (!name) continue;
    notes.push({
      name,
      definition: def,
      category: category || fallbackCategory,
    });
  }
  return notes;
}
