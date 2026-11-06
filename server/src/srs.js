/**
 * 间隔重复算法（简化版 SM-2）—— 与安卓 Srs.java 逐行对齐。
 *
 *   AGAIN（忘了）   —— 重置，10 分钟后再来
 *   GOOD （记得）   —— 间隔按难度系数增长
 *   EASY （太简单） —— 间隔增长更多，难度系数提升
 *
 * 注意：星数累加不在这里做。安卓是在评分前 stars += grade + 1，再调 apply，
 * 服务端 applyReview 也按同样顺序。
 */
export const AGAIN = 0;
export const GOOD = 1;
export const EASY = 2;

export const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * 原地更新一条 note 的 SRS 字段（camelCase 视图对象）。
 * @param {object} n 含 intervalDays / easeFactor / reviewCount / mastery 等
 * @param {number} grade 0/1/2
 * @param {number} now 当前时间戳（毫秒），便于测试注入
 */
export function apply(n, grade, now = Date.now()) {
  let ef = n.easeFactor <= 0 ? 250 : n.easeFactor;
  let interval = n.intervalDays;

  switch (grade) {
    case AGAIN:
      interval = 0; // 当天再来
      ef = Math.max(130, ef - 20); // 难度系数下降，最低 1.3
      n.mastery = 0;
      break;
    case EASY:
      if (n.reviewCount === 0) {
        interval = 4;
      } else {
        interval = Math.ceil(Math.max(1, interval) * (ef / 100) * 1.3);
      }
      ef = Math.min(350, ef + 15);
      n.mastery = 1;
      break;
    case GOOD:
    default:
      if (n.reviewCount === 0) {
        interval = 1;
      } else if (n.reviewCount === 1) {
        interval = 3;
      } else {
        interval = Math.ceil(Math.max(1, interval) * (ef / 100));
      }
      n.mastery = 1;
      break;
  }

  n.intervalDays = interval;
  n.easeFactor = ef;
  n.reviewCount += 1;
  n.lastReview = now;
  // intervalDays=0 时设为 10 分钟后；否则 N 天后
  n.nextReview =
    interval === 0 ? now + 10 * 60 * 1000 : now + interval * DAY_MS;
  return n;
}

/** 人类可读的「下次复习」描述，与安卓 Srs.nextReviewText 一致 */
export function nextReviewText(n, now = Date.now()) {
  if (n.reviewCount === 0 || n.nextReview === 0) return '新题';
  const diff = n.nextReview - now;
  if (diff <= 0) return '待复习';
  const days = Math.floor(diff / DAY_MS);
  if (days >= 1) return days + ' 天后';
  const hours = Math.floor(diff / (60 * 60 * 1000));
  if (hours >= 1) return hours + ' 小时后';
  return '稍后';
}
