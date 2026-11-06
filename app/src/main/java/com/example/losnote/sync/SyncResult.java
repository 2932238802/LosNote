package com.example.losnote.sync;

/** 一次同步的结果，给界面显示用 */
public class SyncResult {
    public int pushedNotes;
    public int pushedReviews;
    public int pushedDailyLogs;
    public int pulledNotes;
    public int pulledReviews;
    public int pulledDailyLogs;
    public int deleted;
    public boolean ok;
    public String error;

    public static SyncResult fail(String message) {
        SyncResult r = new SyncResult();
        r.ok = false;
        r.error = message;
        return r;
    }

    public boolean changedAnything() {
        return pushedNotes + pushedReviews + pushedDailyLogs
                + pulledNotes + pulledReviews + pulledDailyLogs + deleted > 0;
    }

    public String summary() {
        if (!ok) {
            return "同步失败：" + (error == null ? "未知错误" : error);
        }
        if (!changedAnything()) {
            return "已是最新，没有需要同步的改动";
        }
        return "同步完成：上传 " + (pushedNotes + pushedReviews + pushedDailyLogs)
                + " 条，下载 " + (pulledNotes + pulledReviews + pulledDailyLogs)
                + " 条" + (deleted > 0 ? "，删除 " + deleted + " 条" : "");
    }
}
