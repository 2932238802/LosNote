package com.example.losnote.data;

/**
 * 一条知识点记录。
 * name        —— 名称 / 题目
 * definition  —— 完整定义 / 内容
 * category    —— 所属栏目（分类）
 * mastery     —— 掌握度：0=未掌握，1=已掌握
 * --- SRS 间隔重复字段 ---
 * intervalDays —— 当前复习间隔（天）
 * easeFactor   —— 难度系数（×100 存整数，初始 250 表示 2.5）
 * reviewCount  —— 已复习次数
 * nextReview   —— 下次应复习的时间戳（毫秒）；0 表示从未复习/立即可复习
 * lastReview   —— 上次复习时间戳（毫秒）
 * stars        —— 累计获得星数（每次评分累加 1-3）
 * --- 云同步字段（v6）---
 * uuid         —— 跨设备唯一标识，首次入库时生成
 * contentTs    —— 内容组（名称/定义/栏目）最后修改时间，用于 LWW 合并
 * srsTs        —— SRS 组（掌握度/间隔/星数…）最后修改时间
 * dirty        —— true 表示本地有改动还没推给服务器
 * seq          —— 最近一次从服务器收到的序号
 */
public class Note {
    public long id;
    public String name;
    public String definition;
    public String category;
    public long createdAt;
    public int mastery;

    public int intervalDays;
    public int easeFactor = 250;
    public int reviewCount;
    public long nextReview;
    public long lastReview;
    public int stars;

    public String uuid;
    public long contentTs;
    public long srsTs;
    public boolean dirty;
    public long seq;
    /** 只在解析服务器下发的行时使用：true 表示这条是删除墓碑 */
    public boolean deleted;

    public Note() {
    }

    public Note(String name, String definition, String category) {
        this.name = name;
        this.definition = definition;
        this.category = category;
        this.createdAt = System.currentTimeMillis();
        this.mastery = 0;
        this.easeFactor = 250;
    }

    public Note(long id, String name, String definition, String category, long createdAt, int mastery) {
        this.id = id;
        this.name = name;
        this.definition = definition;
        this.category = category;
        this.createdAt = createdAt;
        this.mastery = mastery;
        this.easeFactor = 250;
    }
}
