package com.example.losnote.data;

/**
 * 一条“今日足迹”记录：今天做过的一件让自己变得更好的事。
 * content   —— 记录内容
 * createdAt —— 记录时间戳（毫秒）
 * uuid      —— 跨设备唯一标识（v6 云同步）
 * updatedTs —— 最后修改时间戳，用于 LWW 合并
 * dirty     —— true 表示还没推给服务器
 * seq       —— 最近一次从服务器收到的序号
 */
public class DailyLog {
    public long id;
    public String content;
    public long createdAt;

    public String uuid;
    public long updatedTs;
    public boolean dirty;
    public long seq;
    /** 只在解析服务器下发的行时使用：true 表示这条是删除墓碑 */
    public boolean deleted;

    public DailyLog() {
    }

    public DailyLog(long id, String content, long createdAt) {
        this.id = id;
        this.content = content;
        this.createdAt = createdAt;
    }
}
