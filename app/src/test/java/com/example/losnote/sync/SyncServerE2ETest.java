package com.example.losnote.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.example.losnote.data.DBHelper;
import com.example.losnote.data.Note;
import com.example.losnote.data.Srs;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

/**
 * 跨语言端到端：Robolectric 里的**真** SyncClient ↔ **真** Node 服务器。
 *
 * 默认跳过（没配 {@code losnote.e2e.server} 时），由 {@code scripts/e2e-sync.sh} 起的临时服务触发：
 * 手机 A 建题并复习 → 同步 → 清空本地库假装换设备 → 同步 → 从服务器把题和 SRS 进度拉回来。
 *
 * 这条链路之前只有「Java 客户端 ↔ 桩服务」和「Node 服务器 ↔ node 测试」两段各自验证过，
 * 客户端和服务器之间的字段契约没有真跑过。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SyncServerE2ETest {

    private static final String DB_NAME = "losnote.db";

    @Test
    public void pushFromOneDevice_thenPullOnFreshDevice() throws Exception {
        String server = System.getProperty("losnote.e2e.server", "");
        Assume.assumeTrue("没有 losnote.e2e.server，跳过跨语言 E2E", !server.isEmpty());
        String token = System.getProperty("losnote.e2e.token", "");

        Context ctx = RuntimeEnvironment.getApplication();
        reset(ctx);

        SyncConfig cfg = new SyncConfig();
        cfg.serverUrl = server;
        cfg.token = token;
        cfg.save(ctx);

        // ---- 手机 A：建题 + 复习一次（EASY） ----
        DBHelper db = DBHelper.get(ctx);
        db.insert(new Note("跨端同步验证", "从手机 A 推到服务器", "验证"));
        Note saved = db.queryAll().get(0);
        saved.stars += Srs.EASY + 1;
        Srs.apply(saved, Srs.EASY);
        db.saveReview(saved, Srs.EASY);

        SyncResult push = SyncClient.sync(ctx);
        assertTrue("上传应当成功：" + push.error, push.ok);
        assertEquals("上传题数", 1, push.pushedNotes);
        assertEquals("上传复习记录", 1, push.pushedReviews);
        assertEquals("推完本地要标干净", 0, db.pendingNotes(10).size());

        // ---- 换一台设备：本地库清空，同步配置还在 ----
        reset(ctx);
        DBHelper fresh = DBHelper.get(ctx);
        assertEquals("新设备本地是空的", 0, fresh.queryAll().size());

        SyncResult pull = SyncClient.sync(ctx);
        assertTrue("下载应当成功：" + pull.error, pull.ok);
        assertEquals("下载题数", 1, pull.pulledNotes);
        assertEquals("下载复习记录", 1, pull.pulledReviews);

        Note got = fresh.queryAll().get(0);
        assertEquals("跨端同步验证", got.name);
        assertEquals("从手机 A 推到服务器", got.definition);
        assertEquals("验证", got.category);
        assertEquals("星星要跟着走", 3, got.stars);
        assertEquals("复习次数要跟着走", 1, got.reviewCount);
        assertEquals("间隔天数要跟着走", 4, got.intervalDays);
        assertEquals("难度因子要跟着走", 265, got.easeFactor);
        assertTrue("下次复习时间要跟着走", got.nextReview > got.lastReview);
        assertEquals("远端来的行是干净的", 0, fresh.pendingNotes(10).size());
        assertEquals("复习记录不该再推回去", 0, fresh.pendingReviews(10).size());

        // ---- 第三轮：两边都没有新东西 ----
        SyncResult idle = SyncClient.sync(ctx);
        assertTrue(idle.ok);
        assertTrue("第三轮应当「已是最新」：" + idle.summary(), idle.summary().contains("已是最新"));
    }

    private static void reset(Context ctx) throws Exception {
        Field f = DBHelper.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
        ctx.deleteDatabase(DB_NAME);
    }
}
