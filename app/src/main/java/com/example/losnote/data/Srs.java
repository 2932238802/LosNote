package com.example.losnote.data;

/**
 * 间隔重复算法（简化版 SM-2）。
 *
 * 用户复习后评三档：
 *   AGAIN（忘了）   —— 重置，很快再复习
 *   GOOD （记得）   —— 间隔按难度系数增长
 *   EASY （太简单） —— 间隔增长更多，难度系数提升
 *
 * 间隔（intervalDays）、难度系数（easeFactor，×100）随评分演变。
 * nextReview = now + intervalDays * 一天。
 */
public class Srs {

    public static final int AGAIN = 0;
    public static final int GOOD = 1;
    public static final int EASY = 2;

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    /** 根据评分原地更新 note 的 SRS 字段 */
    public static void apply(Note n, int grade) {
        long now = System.currentTimeMillis();
        int ef = n.easeFactor <= 0 ? 250 : n.easeFactor;

        switch (grade) {
            case AGAIN:
                n.intervalDays = 0;            // 当天再来
                ef = Math.max(130, ef - 20);   // 难度系数下降，最低 1.3
                n.mastery = 0;
                break;
            case EASY:
                if (n.reviewCount == 0) {
                    n.intervalDays = 4;
                } else {
                    n.intervalDays = (int) Math.ceil(Math.max(1, n.intervalDays) * (ef / 100.0) * 1.3);
                }
                ef = Math.min(350, ef + 15);
                n.mastery = 1;
                break;
            case GOOD:
            default:
                if (n.reviewCount == 0) {
                    n.intervalDays = 1;
                } else if (n.reviewCount == 1) {
                    n.intervalDays = 3;
                } else {
                    n.intervalDays = (int) Math.ceil(Math.max(1, n.intervalDays) * (ef / 100.0));
                }
                n.mastery = 1;
                break;
        }

        n.easeFactor = ef;
        n.reviewCount++;
        n.lastReview = now;
        // intervalDays=0 时设为 10 分钟后；否则 N 天后
        n.nextReview = (n.intervalDays == 0) ? (now + 10 * 60 * 1000)
                : (now + (long) n.intervalDays * DAY_MS);
    }

    /** 人类可读的"下次复习"描述 */
    public static String nextReviewText(Note n) {
        if (n.reviewCount == 0 || n.nextReview == 0) {
            return "新题";
        }
        long diff = n.nextReview - System.currentTimeMillis();
        if (diff <= 0) {
            return "待复习";
        }
        long days = diff / DAY_MS;
        if (days >= 1) {
            return days + " 天后";
        }
        long hours = diff / (60 * 60 * 1000);
        if (hours >= 1) {
            return hours + " 小时后";
        }
        return "稍后";
    }
}
