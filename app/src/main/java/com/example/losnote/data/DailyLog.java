package com.example.losnote.data;

/**
 * 一条“今日足迹”记录：今天做过的一件让自己变得更好的事。
 * content   —— 记录内容
 * createdAt —— 记录时间戳（毫秒）
 */
public class DailyLog {
    public long id;
    public String content;
    public long createdAt;

    public DailyLog() {
    }

    public DailyLog(long id, String content, long createdAt) {
        this.id = id;
        this.content = content;
        this.createdAt = createdAt;
    }
}
