package com.example.losnote.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 用 Robolectric 在 JVM 上跑真实的 SQLite：验证 v5→v6 迁移与字段组 LWW 同步逻辑。
 * 这两块是最容易把「手机老数据」搞坏的地方，必须自动化兜住。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DBHelperSyncTest {

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

    /** 直接开一个 v5 结构的库，模拟线上的老版本 */
    private SQLiteDatabase createV5Database(int noteCount, int dailyCount) {
        File file = ctx.getDatabasePath(DB_NAME);
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            assertTrue(dir.mkdirs());
        }
        SQLiteDatabase v5 = SQLiteDatabase.openOrCreateDatabase(file, null);
        v5.execSQL("CREATE TABLE notes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, definition TEXT, " +
                "category TEXT, created_at INTEGER, mastery INTEGER DEFAULT 0, " +
                "interval_days INTEGER DEFAULT 0, ease_factor INTEGER DEFAULT 250, " +
                "review_count INTEGER DEFAULT 0, next_review INTEGER DEFAULT 0, " +
                "last_review INTEGER DEFAULT 0, stars INTEGER DEFAULT 0)");
        v5.execSQL("CREATE TABLE reviews (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "note_id INTEGER, grade INTEGER, time INTEGER)");
        v5.execSQL("CREATE TABLE daily_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "content TEXT, created_at INTEGER)");

        long base = 1_700_000_000_000L;
        for (int i = 0; i < noteCount; i++) {
            ContentValues cv = new ContentValues();
            cv.put("name", "题目" + i);
            cv.put("definition", "答案" + i);
            cv.put("category", "数学");
            cv.put("created_at", base + i * 1000L);
            cv.put("mastery", i == 0 ? 1 : 0);
            cv.put("last_review", base + 5000L + i);
            cv.put("stars", i);
            v5.insertOrThrow("notes", null, cv);
        }
        for (int i = 0; i < dailyCount; i++) {
            ContentValues cv = new ContentValues();
            cv.put("content", "足迹" + i);
            cv.put("created_at", base + i * 2000L);
            v5.insertOrThrow("daily_logs", null, cv);
        }
        ContentValues rv = new ContentValues();
        rv.put("note_id", 1);
        rv.put("grade", 1);
        rv.put("time", base + 9000L);
        v5.insertOrThrow("reviews", null, rv);

        v5.setVersion(5);
        v5.close();
        return v5;
    }

    /* ---------------- 迁移 ---------------- */

    @Test
    public void migration_backfillsUuidTimestampsAndDirty() throws Exception {
        createV5Database(3, 2);
        resetSingleton();
        DBHelper helper = DBHelper.get(ctx);

        List<Note> notes = helper.queryAll();
        assertEquals("迁移后题目不能丢", 3, notes.size());
        for (Note n : notes) {
            assertNotNull("uuid 必须补齐", n.uuid);
            assertFalse(n.uuid.isEmpty());
            assertTrue("内容时间戳应回填为创建时间", n.contentTs > 0);
            assertTrue("SRS 时间戳不早于内容时间戳", n.srsTs >= n.contentTs);
            assertTrue("迁移后必须待推送", n.dirty);
        }

        List<DailyLog> dailies = helper.queryAllDailyLogs(100);
        assertEquals(2, dailies.size());
        for (DailyLog d : dailies) {
            assertNotNull(d.uuid);
            assertEquals("足迹 updated_ts 应回填为 created_at", d.createdAt, d.updatedTs);
            assertTrue("足迹必须待推送", d.dirty);
        }

        // 复习记录补 uuid 并关联到题目 uuid
        List<DBHelper.ReviewRow> rows = helper.pendingReviews(100);
        assertEquals(1, rows.size());
        assertNotNull(rows.get(0).uuid);
        assertNotNull("复习记录应关联到题目的 uuid", rows.get(0).noteUuid);

        // 新增的两张同步表存在
        SQLiteDatabase raw = helper.getWritableDatabase();
        assertTrue(tableExists(raw, "tombstones"));
        assertTrue(tableExists(raw, "sync_state"));
        assertEquals(0L, helper.getLastSyncSeq());
    }

    @Test
    public void migration_isIdempotentWhenReopened() throws Exception {
        createV5Database(2, 1);
        resetSingleton();
        List<Note> first = DBHelper.get(ctx).queryAll();
        String uuid0 = first.get(0).uuid;

        // 再开一次（真实场景：杀进程重开）
        resetSingleton();
        List<Note> second = DBHelper.get(ctx).queryAll();
        assertEquals(2, second.size());
        List<String> uuids = new ArrayList<>();
        for (Note n : second) {
            uuids.add(n.uuid);
        }
        assertTrue("重开不能换 uuid", uuids.contains(uuid0));
    }

    /* ---------------- 字段组 LWW ---------------- */

    @Test
    public void upsertNote_remoteContentNewerOnly_keepsLocalSrsDirty() {
        Note local = new Note("旧标题", "旧答案", "数学");
        long id = db.insert(local);
        assertTrue(id > 0);
        Note stored = db.queryAll().get(0);

        Note remote = copy(stored);
        remote.name = "新标题";
        remote.definition = "新答案";
        remote.contentTs = stored.contentTs + 10_000L;
        remote.srsTs = stored.srsTs - 10_000L;   // SRS 比本地旧
        remote.mastery = 1;

        assertTrue(db.upsertNote(remote));

        Note after = db.queryAll().get(0);
        assertEquals("内容组应被远端覆盖", "新标题", after.name);
        assertEquals("SRS 组应保留本地值", stored.mastery, after.mastery);
        assertTrue("本地 SRS 还有没推出去的改动，必须保持 dirty", after.dirty);
    }

    @Test
    public void upsertNote_bothGroupsNewer_marksClean() {
        long id = db.insert(new Note("a", "b", "c"));
        assertTrue(id > 0);
        Note stored = db.queryAll().get(0);

        Note remote = copy(stored);
        remote.name = "a2";
        remote.mastery = 1;
        remote.contentTs = stored.contentTs + 5_000L;
        remote.srsTs = stored.srsTs + 5_000L;
        remote.seq = 42L;

        assertTrue(db.upsertNote(remote));
        Note after = db.queryAll().get(0);
        assertEquals("a2", after.name);
        assertEquals(1, after.mastery);
        assertFalse("两组都被远端覆盖后应干净", after.dirty);
        assertEquals(42L, after.seq);
    }

    @Test
    public void upsertNote_olderThanLocal_isNoop() {
        db.insert(new Note("a", "b", "c"));
        Note stored = db.queryAll().get(0);
        Note remote = copy(stored);
        remote.name = "should-not-apply";
        remote.contentTs = stored.contentTs - 1;
        remote.srsTs = stored.srsTs - 1;

        assertFalse(db.upsertNote(remote));
        assertEquals("a", db.queryAll().get(0).name);
    }

    @Test
    public void upsertNote_afterLocalDelete_respectsTomestone() {
        long id = db.insert(new Note("待删", "x", "c"));
        Note stored = db.queryAll().get(0);
        db.delete(id);
        assertEquals(0, db.queryAll().size());
        assertEquals(1, db.pendingTombstones(10).size());

        Note remote = copy(stored);
        remote.contentTs = stored.contentTs - 1;  // 远端更旧：不能复活
        assertFalse(db.upsertNote(remote));
        assertEquals(0, db.queryAll().size());

        // 远端更新：允许复活，并清掉本地墓碑
        remote.contentTs = stored.contentTs + 100_000L;
        remote.srsTs = stored.srsTs + 100_000L;
        assertTrue(db.upsertNote(remote));
        assertEquals(1, db.queryAll().size());
        assertEquals(0, db.pendingTombstones(10).size());
    }

    @Test
    public void upsertReview_isIdempotent() {
        long id = db.insert(new Note("a", "b", "c"));
        Note stored = db.queryAll().get(0);

        DBHelper.ReviewRow r = new DBHelper.ReviewRow();
        r.uuid = "rev-1";
        r.noteUuid = stored.uuid;
        r.grade = 1;
        r.time = System.currentTimeMillis();
        r.seq = 7;

        assertTrue(db.upsertReview(r));
        assertFalse("同一条复习记录重复下发应跳过", db.upsertReview(r));
        // 远端下发的记录本来就是干净的，不该被再推回去
        assertEquals(0, db.pendingReviews(10).size());
    }

    @Test
    public void upsertDailyLog_lwwAndTombstone() {
        long id = db.insertDailyLog("第一条");
        assertTrue(id > 0);
        DailyLog local = db.queryAllDailyLogs(10).get(0);

        DailyLog remote = new DailyLog(0L, "远端改过", local.createdAt);
        remote.uuid = local.uuid;
        remote.updatedTs = local.updatedTs + 1000L;
        assertTrue(db.upsertDailyLog(remote));
        DailyLog after = db.queryAllDailyLogs(10).get(0);
        assertEquals("远端改过", after.content);
        assertFalse("远端更新后应干净", after.dirty);

        DailyLog older = new DailyLog(0L, "更旧", local.createdAt);
        older.uuid = local.uuid;
        older.updatedTs = local.updatedTs - 1;
        assertFalse(db.upsertDailyLog(older));
        assertEquals("远端改过", db.queryAllDailyLogs(10).get(0).content);

        long delId = db.queryAllDailyLogs(10).get(0).id;
        db.deleteDailyLog(delId);
        assertEquals(0, db.queryAllDailyLogs(10).size());

        DailyLog resurrect = new DailyLog(0L, "不该复活", local.createdAt);
        resurrect.uuid = local.uuid;
        resurrect.updatedTs = local.updatedTs + 1;
        assertFalse(db.upsertDailyLog(resurrect));
        assertEquals(0, db.queryAllDailyLogs(10).size());
    }

    @Test
    public void markAllSynced_doesNotClearNewerLocalEdits() {
        db.insert(new Note("a", "b", "c"));
        Note snapshot = db.queryAll().get(0);

        // 推送期间本地又改了内容
        Note edit = copy(snapshot);
        edit.name = "改过了";
        db.update(edit);

        db.markAllSynced(java.util.Collections.singletonList(snapshot),
                new ArrayList<DBHelper.ReviewRow>(),
                new ArrayList<DailyLog>(),
                new ArrayList<DBHelper.Tombstone>(), 9L);

        Note after = db.queryAll().get(0);
        assertTrue("推送后本地又改过，不能标干净", after.dirty);
    }

    @Test
    public void deleteAll_leavesTombstoneForEveryNote() {
        db.insert(new Note("a", "b", "c"));
        db.insert(new Note("d", "e", "f"));
        db.deleteAll();
        assertEquals(0, db.queryAll().size());
        assertEquals(2, db.pendingTombstones(10).size());
    }

    /* ---------------- 工具 ---------------- */

    private static Note copy(Note s) {
        Note n = new Note(s.name, s.definition, s.category);
        n.id = s.id;
        n.createdAt = s.createdAt;
        n.mastery = s.mastery;
        n.intervalDays = s.intervalDays;
        n.easeFactor = s.easeFactor;
        n.reviewCount = s.reviewCount;
        n.nextReview = s.nextReview;
        n.lastReview = s.lastReview;
        n.stars = s.stars;
        n.uuid = s.uuid;
        n.contentTs = s.contentTs;
        n.srsTs = s.srsTs;
        n.dirty = s.dirty;
        n.seq = s.seq;
        return n;
    }

    private static boolean tableExists(SQLiteDatabase db, String table) {
        Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{table});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    private static void resetSingleton() throws Exception {
        Field f = DBHelper.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }
}
