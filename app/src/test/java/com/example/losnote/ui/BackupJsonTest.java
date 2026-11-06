package com.example.losnote.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.example.losnote.data.DBHelper;
import com.example.losnote.data.Note;
import com.example.losnote.data.Srs;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 备份互通测试：网页端（服务端 /api/export）导出的 v2 JSON 必须能被手机导入，
 * 并且 SRS 进度不能丢；手机导出的文件也要能被自己再导回去。
 *
 * 固定样例 app/src/test/resources/server-backup-v2.json 由服务端真跑一遍 exportBackup 生成，
 * 不是手写的，所以两边字段对不上时会直接失败。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BackupJsonTest {

    private static final String DB_NAME = "losnote.db";

    private Context ctx;
    private DBHelper db;

    @Before
    public void setUp() throws Exception {
        ctx = RuntimeEnvironment.getApplication();
        ctx.deleteDatabase(DB_NAME);
        resetSingleton();
        db = DBHelper.get(ctx);
    }

    @After
    public void tearDown() throws Exception {
        ctx.deleteDatabase(DB_NAME);
        resetSingleton();
    }

    private static void resetSingleton() throws Exception {
        Field f = DBHelper.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    private static String readResource(String name) throws Exception {
        try (InputStream in = BackupJsonTest.class.getResourceAsStream("/" + name)) {
            assertNotNull("测试资源不存在: " + name, in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private Note byName(String name) {
        for (Note n : db.queryAll()) {
            if (name.equals(n.name)) {
                return n;
            }
        }
        return null;
    }

    @Test
    public void serverExport_importsWithFullSrsProgress() throws Exception {
        String json = readResource("server-backup-v2.json");

        int[] counts = BankFragment.importBackupJson(db, json);
        assertEquals("题目条数", 2, counts[0]);
        assertEquals("足迹条数", 1, counts[1]);

        Note closure = byName("闭包是什么");
        assertNotNull("网页端导出的题应能导入", closure);
        assertEquals("UUID 必须保留（否则同步会造出重复题）", 36, closure.uuid.length());
        assertEquals("EASY 评 3 星", 3, closure.stars);
        assertEquals("复习次数", 1, closure.reviewCount);
        assertEquals("间隔天数", 4, closure.intervalDays);
        assertEquals("难度因子", 265, closure.easeFactor);
        assertTrue("已复习的题应掌握", closure.mastery == 1);
        assertTrue("下次复习应在未来", closure.nextReview > System.currentTimeMillis());

        Note tcp = byName("TCP 三次握手");
        assertNotNull(tcp);
        assertEquals("没复习过的题", 0, tcp.reviewCount);
        assertEquals("默认难度因子", 250, tcp.easeFactor);

        assertEquals("足迹也要导入", 1, db.queryAllDailyLogs(100).size());
        assertTrue("导入的题应标脏，联网后自动上传", db.pendingNotes(50).size() >= 2);
    }

    @Test
    public void androidExport_roundTripsThroughImport() throws Exception {
        db.insert(new Note("什么是 WAL", "预写日志", "数据库"));
        Note saved = byName("什么是 WAL");
        assertNotNull(saved);
        // 与 QuizFragment.grade() 一致：先加星星，再算 SRS，最后落库（saveReview 只写入当前值）
        saved.stars += Srs.EASY + 1;
        Srs.apply(saved, Srs.EASY);
        db.saveReview(saved, Srs.EASY);

        String json = BankFragment.buildBackupJson(db);
        JSONObject root = new JSONObject(json);
        assertEquals(2, root.optInt("version"));
        assertFalse("复习明细不进备份", root.optBoolean("reviewHistoryIncluded", true));

        ctx.deleteDatabase(DB_NAME);
        resetSingleton();
        db = DBHelper.get(ctx);

        int[] counts = BankFragment.importBackupJson(db, json);
        assertEquals(1, counts[0]);
        Note back = byName("什么是 WAL");
        assertNotNull(back);
        assertEquals("导出再导入要保住星星", 3, back.stars);
        assertEquals("复习次数", 1, back.reviewCount);
        assertEquals("间隔", 4, back.intervalDays);
        assertEquals("UUID 不换", saved.uuid, back.uuid);
    }

    @Test
    public void v1Backup_stillImports() throws Exception {
        String v1 = "{\"version\":1,\"notes\":[{\"name\":\"老题\",\"definition\":\"老的答案\","
                + "\"category\":\"旧栏目\",\"mastery\":1}]}";

        int[] counts = BankFragment.importBackupJson(db, v1);
        assertEquals(1, counts[0]);
        assertEquals(0, counts[1]);

        Note old = byName("老题");
        assertNotNull(old);
        assertEquals("v1 也保留掌握度", 1, old.mastery);
        assertEquals("缺字段时用默认难度因子", 250, old.easeFactor);
        assertNotNull("v1 没 uuid，导入时自动补", old.uuid);
    }

    @Test
    public void duplicateUuid_isNotImportedTwice() throws Exception {
        String json = readResource("server-backup-v2.json");
        int[] first = BankFragment.importBackupJson(db, json);
        int[] second = BankFragment.importBackupJson(db, json);

        assertEquals(2, first[0]);
        assertEquals("同样的 uuid 再来一次应覆盖而不是新增", 2, db.queryAll().size());
        assertEquals("足迹重复应跳过", 0, second[1]);
        assertTrue(second[0] >= 1);
        assertEquals(1, db.queryAllDailyLogs(100).size());
    }
}
