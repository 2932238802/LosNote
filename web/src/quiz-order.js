// 复习抽题的排序逻辑（纯函数，便于单测）。
//
// 服务端 T-001 已改为 ORDER BY RANDOM()，但：
// 1. 旧版服务端（线上可能未升级）仍按固定顺序返回，首题会恒定；
// 2. 即使服务端随机，小样本下仍有概率连续两次抽到同一题。
// 因此前端在本地再洗牌一次，并在到期题多于一道时避开上一次的首题。

/** Fisher-Yates 洗牌，返回新数组，不修改入参。 */
export function shuffle(items, random = Math.random) {
  const list = Array.isArray(items) ? items.slice() : [];
  for (let i = list.length - 1; i > 0; i -= 1) {
    const j = Math.min(i, Math.floor(random() * (i + 1)));
    [list[i], list[j]] = [list[j], list[i]];
  }
  return list;
}

/**
 * 生成本轮抽题队列。
 * - 到期题 0 道：返回空数组；
 * - 到期题 1 道：允许重复（无法避重）；
 * - 到期题 > 1 道：洗牌后若首题与上一次相同，从其余题中随机换一道到首位。
 *
 * @param {Array<{uuid: string}>} items 服务端返回的到期题
 * @param {string} previousFirstUuid 上一次成功抽题时显示的首题 uuid
 * @param {() => number} random 注入随机源，仅测试使用
 */
export function orderForStart(items, previousFirstUuid = '', random = Math.random) {
  const list = shuffle(items, random);
  if (list.length < 2) return list;
  if (!previousFirstUuid || list[0]?.uuid !== previousFirstUuid) return list;

  // 收集所有与上一次首题不同的下标，随机挑一道换到首位
  const candidates = [];
  for (let i = 1; i < list.length; i += 1) {
    if (list[i]?.uuid !== previousFirstUuid) candidates.push(i);
  }
  if (!candidates.length) return list;

  const pick = candidates[Math.min(candidates.length - 1, Math.floor(random() * candidates.length))];
  [list[0], list[pick]] = [list[pick], list[0]];
  return list;
}
