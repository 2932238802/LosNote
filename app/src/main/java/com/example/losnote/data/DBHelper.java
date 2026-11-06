package com.example.losnote.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SQLite 数据库帮助类。
 * v1: 基础字段
 * v2: + mastery
 * v3: + SRS 字段（interval/ease/review_count/next_review/last_review）+ reviews 复习记录表
 * v4: + stars（累计星数）
 * v5: + daily_logs 今日足迹表
 * v6: + 云同步字段（uuid / content_ts / srs_ts / deleted / dirty / seq），删除改为软删墓碑
 */
public class DBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "losnote.db";
    private static final int DB_VERSION = 6;

    public static final String TABLE = "notes";
    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_DEFINITION = "definition";
    public static final String COL_CATEGORY = "category";
    public static final String COL_CREATED = "created_at";
    public static final String COL_MASTERY = "mastery";
    public static final String COL_INTERVAL = "interval_days";
    public static final String COL_EASE = "ease_factor";
    public static final String COL_REVIEW_COUNT = "review_count";
    public static final String COL_NEXT_REVIEW = "next_review";
    public static final String COL_LAST_REVIEW = "last_review";
    public static final String COL_STARS = "stars";

    // v6：云同步字段。
    // uuid 是跨设备身份；content_ts / srs_ts 是两组字段各自的 LWW 时间戳；
    // dirty=1 表示本地有改动还没推给服务器；seq 记录最近一次从服务器收到的序号。
    public static final String COL_UUID = "uuid";
    public static final String COL_CONTENT_TS = "content_ts";
    public static final String COL_SRS_TS = "srs_ts";
    public static final String COL_DIRTY = "dirty";
    public static final String COL_SEQ = "seq";

    /** 墓碑：本地删掉的行的 uuid，推给服务器后才清掉 */
    public static final String TABLE_TOMB = "tombstones";
    public static final String T_KIND = "kind";
    public static final String T_UUID = "uuid";
    public static final String T_TS = "ts";

    /** 同步游标 */
    public static final String TABLE_SYNC_STATE = "sync_state";
    public static final String SS_KEY = "k";
    public static final String SS_VAL = "v";

    public static final String KIND_NOTE = "note";
    public static final String KIND_DAILY = "daily";

    public static final String TABLE_REVIEWS = "reviews";
    public static final String RV_ID = "id";
    public static final String RV_NOTE_ID = "note_id";
    public static final String RV_GRADE = "grade";
    public static final String RV_TIME = "time";
    public static final String RV_UUID = "uuid";
    public static final String RV_NOTE_UUID = "note_uuid";
    public static final String RV_DIRTY = "dirty";
    public static final String RV_SEQ = "seq";

    public static final String TABLE_DAILY = "daily_logs";
    public static final String DL_ID = "id";
    public static final String DL_CONTENT = "content";
    public static final String DL_CREATED = "created_at";
    public static final String DL_UUID = "uuid";
    public static final String DL_UPDATED = "updated_ts";
    public static final String DL_DIRTY = "dirty";
    public static final String DL_SEQ = "seq";

    private static DBHelper instance;

    public static synchronized DBHelper get(Context context) {
        if (instance == null) {
            instance = new DBHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DBHelper(@Nullable Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NAME + " TEXT NOT NULL, " +
                COL_DEFINITION + " TEXT, " +
                COL_CATEGORY + " TEXT, " +
                COL_CREATED + " INTEGER, " +
                COL_MASTERY + " INTEGER DEFAULT 0, " +
                COL_INTERVAL + " INTEGER DEFAULT 0, " +
                COL_EASE + " INTEGER DEFAULT 250, " +
                COL_REVIEW_COUNT + " INTEGER DEFAULT 0, " +
                COL_NEXT_REVIEW + " INTEGER DEFAULT 0, " +
                COL_LAST_REVIEW + " INTEGER DEFAULT 0, " +
                COL_STARS + " INTEGER DEFAULT 0, " +
                COL_UUID + " TEXT, " +
                COL_CONTENT_TS + " INTEGER DEFAULT 0, " +
                COL_SRS_TS + " INTEGER DEFAULT 0, " +
                COL_DIRTY + " INTEGER DEFAULT 0, " +
                COL_SEQ + " INTEGER DEFAULT 0" +
                ")");
        createReviewsTable(db);
        createDailyLogTable(db);
        createTombstoneTable(db);
        createSyncStateTable(db);
        createSyncIndexes(db);
    }

    private void createReviewsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_REVIEWS + " (" +
                RV_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                RV_NOTE_ID + " INTEGER, " +
                RV_GRADE + " INTEGER, " +
                RV_TIME + " INTEGER, " +
                RV_UUID + " TEXT, " +
                RV_NOTE_UUID + " TEXT, " +
                RV_DIRTY + " INTEGER DEFAULT 0, " +
                RV_SEQ + " INTEGER DEFAULT 0" +
                ")");
    }

    private void createDailyLogTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_DAILY + " (" +
                DL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                DL_CONTENT + " TEXT NOT NULL, " +
                DL_CREATED + " INTEGER, " +
                DL_UUID + " TEXT, " +
                DL_UPDATED + " INTEGER DEFAULT 0, " +
                DL_DIRTY + " INTEGER DEFAULT 0, " +
                DL_SEQ + " INTEGER DEFAULT 0" +
                ")");
    }

    private void createTombstoneTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TOMB + " (" +
                T_KIND + " TEXT NOT NULL, " +
                T_UUID + " TEXT NOT NULL, " +
                T_TS + " INTEGER DEFAULT 0, " +
                "PRIMARY KEY (" + T_KIND + ", " + T_UUID + "))");
    }

    private void createSyncStateTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SYNC_STATE + " (" +
                SS_KEY + " TEXT PRIMARY KEY, " +
                SS_VAL + " INTEGER DEFAULT 0)");
    }

    private void createSyncIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_notes_uuid ON " + TABLE + "(" + COL_UUID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_dirty ON " + TABLE + "(" + COL_DIRTY + ")");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_reviews_uuid ON " + TABLE_REVIEWS + "(" + RV_UUID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reviews_dirty ON " + TABLE_REVIEWS + "(" + RV_DIRTY + ")");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_daily_uuid ON " + TABLE_DAILY + "(" + DL_UUID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_dirty ON " + TABLE_DAILY + "(" + DL_DIRTY + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_MASTERY + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_INTERVAL + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_EASE + " INTEGER DEFAULT 250");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_REVIEW_COUNT + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_NEXT_REVIEW + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_LAST_REVIEW + " INTEGER DEFAULT 0");
            createReviewsTable(db);
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_STARS + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 5) {
            createDailyLogTable(db);
        }
        if (oldVersion < 6) {
            migrateV6(db);
        }
    }

    /**
     * v5 → v6：加同步字段，回填 uuid 与时间戳，并把已有数据全部标成待推送。
     * 首次升级后联一次网，旧数据就都上云了。
     */
    private void migrateV6(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_UUID + " TEXT");
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_CONTENT_TS + " INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_SRS_TS + " INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_DIRTY + " INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_SEQ + " INTEGER DEFAULT 0");

        db.execSQL("ALTER TABLE " + TABLE_REVIEWS + " ADD COLUMN " + RV_UUID + " TEXT");
        db.execSQL("ALTER TABLE " + TABLE_REVIEWS + " ADD COLUMN " + RV_NOTE_UUID + " TEXT");
        db.execSQL("ALTER TABLE " + TABLE_REVIEWS + " ADD COLUMN " + RV_DIRTY + " INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE_REVIEWS + " ADD COLUMN " + RV_SEQ + " INTEGER DEFAULT 0");

        db.execSQL("ALTER TABLE " + TABLE_DAILY + " ADD COLUMN " + DL_UUID + " TEXT");
        db.execSQL("ALTER TABLE " + TABLE_DAILY + " ADD COLUMN " + DL_UPDATED + " INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE_DAILY + " ADD COLUMN " + DL_DIRTY + " INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE_DAILY + " ADD COLUMN " + DL_SEQ + " INTEGER DEFAULT 0");

        createTombstoneTable(db);
        createSyncStateTable(db);
        backfillSyncColumns(db);
        createSyncIndexes(db);
    }

    /** 给升级前的老数据补 uuid / 时间戳 / dirty 标记 */
    private void backfillSyncColumns(SQLiteDatabase db) {
        long now = System.currentTimeMillis();

        // 1) 题目：内容时间戳取创建时间，SRS 时间戳取最后复习时间
        List<long[]> noteRows = new ArrayList<>();
        Cursor nc = db.rawQuery("SELECT " + COL_ID + ", " + COL_CREATED + ", " + COL_LAST_REVIEW
                + " FROM " + TABLE + " WHERE " + COL_UUID + " IS NULL", null);
        try {
            while (nc.moveToNext()) {
                noteRows.add(new long[]{nc.getLong(0), nc.getLong(1), nc.getLong(2)});
            }
        } finally {
            nc.close();
        }
        for (long[] row : noteRows) {
            long created = row[1] > 0 ? row[1] : now;
            long lastReview = row[2];
            ContentValues cv = new ContentValues();
            cv.put(COL_UUID, newUuid());
            cv.put(COL_CONTENT_TS, created);
            cv.put(COL_SRS_TS, Math.max(created, lastReview));
            cv.put(COL_DIRTY, 1);
            cv.put(COL_SEQ, 0);
            db.update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(row[0])});
        }

        // 2) 复习记录：补 uuid，并关联到题目的 uuid
        List<Object[]> reviewRows = new ArrayList<>();
        Cursor rc = db.rawQuery("SELECT r." + RV_ID + ", n." + COL_UUID
                + " FROM " + TABLE_REVIEWS + " r LEFT JOIN " + TABLE + " n ON n." + COL_ID
                + " = r." + RV_NOTE_ID + " WHERE r." + RV_UUID + " IS NULL", null);
        try {
            while (rc.moveToNext()) {
                reviewRows.add(new Object[]{rc.getLong(0), rc.isNull(1) ? null : rc.getString(1)});
            }
        } finally {
            rc.close();
        }
        for (Object[] row : reviewRows) {
            ContentValues cv = new ContentValues();
            cv.put(RV_UUID, newUuid());
            cv.put(RV_NOTE_UUID, (String) row[1]);
            cv.put(RV_DIRTY, 1);
            cv.put(RV_SEQ, 0);
            db.update(TABLE_REVIEWS, cv, RV_ID + "=?", new String[]{String.valueOf(row[0])});
        }

        // 3) 今日足迹：updated_ts 用记录时间，全部标成待推送
        db.execSQL("UPDATE " + TABLE_DAILY + " SET " + DL_UPDATED + " = " + DL_CREATED
                + " WHERE " + DL_UPDATED + " IS NULL OR " + DL_UPDATED + " = 0");
        db.execSQL("UPDATE " + TABLE_DAILY + " SET " + DL_DIRTY + " = 1, " + DL_SEQ + " = 0");
        List<Long> dailyIds = new ArrayList<>();
        Cursor dc = db.rawQuery("SELECT " + DL_ID + " FROM " + TABLE_DAILY
                + " WHERE " + DL_UUID + " IS NULL", null);
        try {
            while (dc.moveToNext()) {
                dailyIds.add(dc.getLong(0));
            }
        } finally {
            dc.close();
        }
        for (Long id : dailyIds) {
            ContentValues cv = new ContentValues();
            cv.put(DL_UUID, newUuid());
            db.update(TABLE_DAILY, cv, DL_ID + "=?", new String[]{String.valueOf(id)});
        }
    }

    /* ---------------- 增 ---------------- */

    public long insert(@NonNull Note note) {
        return getWritableDatabase().insert(TABLE, null, toValues(note));
    }

    public int insertBatch(@NonNull List<Note> notes) {
        SQLiteDatabase db = getWritableDatabase();
        int count = 0;
        db.beginTransaction();
        try {
            for (Note note : notes) {
                if (db.insert(TABLE, null, toValues(note)) != -1) {
                    count++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return count;
    }

    private ContentValues toValues(Note n) {
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put(COL_NAME, n.name);
        cv.put(COL_DEFINITION, n.definition);
        cv.put(COL_CATEGORY, n.category);
        cv.put(COL_CREATED, n.createdAt > 0 ? n.createdAt : now);
        cv.put(COL_MASTERY, n.mastery);
        cv.put(COL_INTERVAL, n.intervalDays);
        cv.put(COL_EASE, n.easeFactor <= 0 ? 250 : n.easeFactor);
        cv.put(COL_REVIEW_COUNT, n.reviewCount);
        cv.put(COL_NEXT_REVIEW, n.nextReview);
        cv.put(COL_LAST_REVIEW, n.lastReview);
        cv.put(COL_STARS, n.stars);
        cv.put(COL_UUID, n.uuid != null && !n.uuid.isEmpty() ? n.uuid : newUuid());
        cv.put(COL_CONTENT_TS, now);
        cv.put(COL_SRS_TS, now);
        cv.put(COL_DIRTY, 1);
        cv.put(COL_SEQ, 0);
        return cv;
    }

    /* ---------------- 改 ---------------- */

    /** 只改内容三字段，并让内容组的时间戳往前走，标记待推送 */
    public int update(@NonNull Note note) {
        ContentValues cv = new ContentValues();
        cv.put(COL_NAME, note.name);
        cv.put(COL_DEFINITION, note.definition);
        cv.put(COL_CATEGORY, note.category);
        cv.put(COL_CONTENT_TS, System.currentTimeMillis());
        cv.put(COL_DIRTY, 1);
        return getWritableDatabase().update(TABLE, cv, COL_ID + "=?",
                new String[]{String.valueOf(note.id)});
    }

    public void setMastery(long id, int mastery) {
        ContentValues cv = new ContentValues();
        cv.put(COL_MASTERY, mastery);
        cv.put(COL_SRS_TS, System.currentTimeMillis());
        cv.put(COL_DIRTY, 1);
        getWritableDatabase().update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    /** 复习后保存 SRS 状态并记录一条复习日志 */
    public void saveReview(@NonNull Note n, int grade) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            ContentValues cv = new ContentValues();
            cv.put(COL_MASTERY, n.mastery);
            cv.put(COL_INTERVAL, n.intervalDays);
            cv.put(COL_EASE, n.easeFactor);
            cv.put(COL_REVIEW_COUNT, n.reviewCount);
            cv.put(COL_NEXT_REVIEW, n.nextReview);
            cv.put(COL_LAST_REVIEW, n.lastReview);
            cv.put(COL_STARS, n.stars);
            cv.put(COL_SRS_TS, now);
            cv.put(COL_DIRTY, 1);
            db.update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(n.id)});

            String noteUuid = n.uuid != null && !n.uuid.isEmpty() ? n.uuid : uuidOfNote(db, n.id);
            ContentValues rv = new ContentValues();
            rv.put(RV_NOTE_ID, n.id);
            rv.put(RV_GRADE, grade);
            rv.put(RV_TIME, now);
            rv.put(RV_UUID, newUuid());
            rv.put(RV_NOTE_UUID, noteUuid);
            rv.put(RV_DIRTY, 1);
            rv.put(RV_SEQ, 0);
            db.insertOrThrow(TABLE_REVIEWS, null, rv);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /* ---------------- 删（软删改为墓碑 + 物理删，墓碑推给服务器后才清） ---------------- */

    public void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String uuid = uuidOfNote(db, id);
            if (uuid != null) {
                addTombstone(db, KIND_NOTE, uuid, System.currentTimeMillis());
            }
            db.delete(TABLE_REVIEWS, RV_NOTE_ID + "=?", new String[]{String.valueOf(id)});
            db.delete(TABLE, COL_ID + "=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public int deleteByIds(@NonNull List<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        SQLiteDatabase db = getWritableDatabase();
        int count = 0;
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            for (Long id : ids) {
                String uuid = uuidOfNote(db, id);
                if (uuid != null) {
                    addTombstone(db, KIND_NOTE, uuid, now);
                }
                db.delete(TABLE_REVIEWS, RV_NOTE_ID + "=?", new String[]{String.valueOf(id)});
                count += db.delete(TABLE, COL_ID + "=?", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return count;
    }

    public int deleteByCategory(@NonNull String category) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            List<String> uuids = new ArrayList<>();
            Cursor c = db.query(TABLE, new String[]{COL_UUID}, COL_CATEGORY + "=?",
                    new String[]{category}, null, null, null);
            try {
                while (c.moveToNext()) {
                    uuids.add(c.getString(0));
                }
            } finally {
                c.close();
            }
            for (String uuid : uuids) {
                if (uuid != null) {
                    addTombstone(db, KIND_NOTE, uuid, now);
                }
            }
            db.delete(TABLE_REVIEWS, RV_NOTE_ID + " IN (SELECT " + COL_ID + " FROM " + TABLE
                    + " WHERE " + COL_CATEGORY + "=?)", new String[]{category});
            int count = db.delete(TABLE, COL_CATEGORY + "=?", new String[]{category});
            db.setTransactionSuccessful();
            return count;
        } finally {
            db.endTransaction();
        }
    }

    /** 重命名/合并栏目：把 oldCategory 下的所有题移动到 newCategory */
    public int updateCategory(@NonNull String oldCategory, @NonNull String newCategory) {
        ContentValues cv = new ContentValues();
        cv.put(COL_CATEGORY, newCategory);
        cv.put(COL_CONTENT_TS, System.currentTimeMillis());
        cv.put(COL_DIRTY, 1);
        return getWritableDatabase().update(TABLE, cv, COL_CATEGORY + "=?", new String[]{oldCategory});
    }

    /** 清空题库：每条都留墓碑，其他设备同步后也会一起清掉 */
    public void deleteAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            List<String> uuids = new ArrayList<>();
            Cursor c = db.query(TABLE, new String[]{COL_UUID}, null, null, null, null, null);
            try {
                while (c.moveToNext()) {
                    uuids.add(c.getString(0));
                }
            } finally {
                c.close();
            }
            for (String uuid : uuids) {
                if (uuid != null) {
                    addTombstone(db, KIND_NOTE, uuid, now);
                }
            }
            db.delete(TABLE_REVIEWS, null, null);
            db.delete(TABLE, null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /* ---------------- 查 ---------------- */

    public List<Note> queryAll() {
        return queryByCategory((String) null);
    }

    public List<Note> queryByCategory(@Nullable Collection<String> categories) {
        SQLiteDatabase db = getReadableDatabase();
        List<String> args = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        buildCategoryWhere(categories, where, args);
        Cursor c = db.query(TABLE, null,
                where.length() > 0 ? where.toString() : null,
                args.isEmpty() ? null : args.toArray(new String[0]),
                null, null, COL_CREATED + " DESC");
        return collect(c);
    }

    public List<Note> queryByCategory(@Nullable String category) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c;
        if (category == null || category.isEmpty()) {
            c = db.query(TABLE, null, null, null, null, null, COL_CREATED + " DESC");
        } else {
            c = db.query(TABLE, null, COL_CATEGORY + "=?", new String[]{category},
                    null, null, COL_CREATED + " DESC");
        }
        return collect(c);
    }

    public List<Note> search(@NonNull String keyword, @Nullable Collection<String> categories) {
        SQLiteDatabase db = getReadableDatabase();
        String kw = "%" + keyword + "%";
        List<String> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("(" + COL_NAME + " LIKE ? OR " + COL_DEFINITION + " LIKE ?)");
        args.add(kw);
        args.add(kw);
        buildCategoryWhere(categories, where, args);
        Cursor c = db.query(TABLE, null,
                where.toString(), args.toArray(new String[0]),
                null, null, COL_CREATED + " DESC");
        return collect(c);
    }

    public List<Note> search(@NonNull String keyword, @Nullable String category) {
        SQLiteDatabase db = getReadableDatabase();
        String kw = "%" + keyword + "%";
        Cursor c;
        if (category == null || category.isEmpty()) {
            c = db.query(TABLE, null,
                    "(" + COL_NAME + " LIKE ? OR " + COL_DEFINITION + " LIKE ?)",
                    new String[]{kw, kw}, null, null, COL_CREATED + " DESC");
        } else {
            c = db.query(TABLE, null,
                    COL_CATEGORY + "=? AND (" + COL_NAME + " LIKE ? OR " + COL_DEFINITION + " LIKE ?)",
                    new String[]{category, kw, kw}, null, null, COL_CREATED + " DESC");
        }
        return collect(c);
    }

    /** 抽题：可按多个栏目筛选，empty=全部 */
    public List<Note> queryForQuiz(@Nullable Collection<String> categories, boolean onlyUnknown) {
        SQLiteDatabase db = getReadableDatabase();
        List<String> args = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        buildCategoryWhere(categories, where, args);
        if (onlyUnknown) {
            if (where.length() > 0) where.append(" AND ");
            where.append(COL_MASTERY).append("=0");
        }
        Cursor c = db.query(TABLE, null,
                where.length() > 0 ? where.toString() : null,
                args.isEmpty() ? null : args.toArray(new String[0]),
                null, null, COL_CREATED + " DESC");
        return collect(c);
    }

    public List<Note> queryForQuiz(@Nullable String category, boolean onlyUnknown) {
        SQLiteDatabase db = getReadableDatabase();
        StringBuilder where = new StringBuilder();
        List<String> args = new ArrayList<>();
        if (category != null && !category.isEmpty()) {
            where.append(COL_CATEGORY).append("=?");
            args.add(category);
        }
        if (onlyUnknown) {
            if (where.length() > 0) {
                where.append(" AND ");
            }
            where.append(COL_MASTERY).append("=0");
        }
        Cursor c = db.query(TABLE, null,
                where.length() > 0 ? where.toString() : null,
                args.isEmpty() ? null : args.toArray(new String[0]),
                null, null, COL_CREATED + " DESC");
        return collect(c);
    }

    /** 复习模式：到期需复习的题（next_review<=now 或 从未复习），可限定多个栏目 */
    public List<Note> queryDue(@Nullable Collection<String> categories) {
        SQLiteDatabase db = getReadableDatabase();
        long now = System.currentTimeMillis();
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(now));
        StringBuilder where = new StringBuilder("(" + COL_NEXT_REVIEW + "<=? OR " + COL_REVIEW_COUNT + "=0)");
        buildCategoryWhere(categories, where, args);
        Cursor c = db.query(TABLE, null, where.toString(),
                args.toArray(new String[0]), null, null, COL_NEXT_REVIEW + " ASC");
        return collect(c);
    }

    /** 复习模式：到期需复习的题（next_review<=now 或 从未复习），可限定单个栏目 */
    public List<Note> queryDue(@Nullable String category) {
        SQLiteDatabase db = getReadableDatabase();
        long now = System.currentTimeMillis();
        StringBuilder where = new StringBuilder("(" + COL_NEXT_REVIEW + "<=? OR " + COL_REVIEW_COUNT + "=0)");
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(now));
        if (category != null && !category.isEmpty()) {
            where.append(" AND ").append(COL_CATEGORY).append("=?");
            args.add(category);
        }
        Cursor c = db.query(TABLE, null, where.toString(),
                args.toArray(new String[0]), null, null, COL_NEXT_REVIEW + " ASC");
        return collect(c);
    }

    public List<String> queryCategories() {
        List<String> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(true, TABLE, new String[]{COL_CATEGORY},
                COL_CATEGORY + " IS NOT NULL AND " + COL_CATEGORY + " <> ''",
                null, COL_CATEGORY, null, COL_CATEGORY + " ASC", null);
        try {
            while (c.moveToNext()) {
                result.add(c.getString(0));
            }
        } finally {
            c.close();
        }
        return result;
    }

    public int count(@Nullable String category) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c;
        if (category == null || category.isEmpty()) {
            c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        } else {
            c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE + " WHERE " + COL_CATEGORY + "=?",
                    new String[]{category});
        }
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /* ---------------- 多栏目辅助 ---------------- */

    /** 构建 WHERE 子句的 IN 条件；categories 为 null 或 empty 则跳过 */
    private void buildCategoryWhere(@Nullable Collection<String> categories,
                                    @NonNull StringBuilder where,
                                    @NonNull List<String> args) {
        if (categories == null || categories.isEmpty()) return;
        if (where.length() > 0) where.append(" AND ");
        where.append(COL_CATEGORY).append(" IN (");
        String[] arr = categories.toArray(new String[0]);
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) where.append(",");
            where.append("?");
            args.add(arr[i]);
        }
        where.append(")");
    }

    /* ---------------- 今日足迹 ---------------- */

    public long insertDailyLog(@NonNull String content) {
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put(DL_CONTENT, content);
        cv.put(DL_CREATED, now);
        cv.put(DL_UUID, newUuid());
        cv.put(DL_UPDATED, now);
        cv.put(DL_DIRTY, 1);
        cv.put(DL_SEQ, 0);
        return getWritableDatabase().insert(TABLE_DAILY, null, cv);
    }

    public int deleteDailyLog(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String uuid = null;
            Cursor c = db.query(TABLE_DAILY, new String[]{DL_UUID}, DL_ID + "=?",
                    new String[]{String.valueOf(id)}, null, null, null);
            try {
                if (c.moveToFirst()) {
                    uuid = c.getString(0);
                }
            } finally {
                c.close();
            }
            if (uuid != null) {
                addTombstone(db, KIND_DAILY, uuid, System.currentTimeMillis());
            }
            int n = db.delete(TABLE_DAILY, DL_ID + "=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
            return n;
        } finally {
            db.endTransaction();
        }
    }

    /** 今天的足迹，按时间倒序 */
    public List<DailyLog> queryDailyLogsToday() {
        long start = todayStart();
        return queryDailyLogs(start, start + 24L * 60 * 60 * 1000);
    }

    /** 指定时间范围内的足迹，按时间倒序 */
    public List<DailyLog> queryDailyLogs(long startMs, long endMs) {
        Cursor c = getReadableDatabase().query(TABLE_DAILY, null,
                DL_CREATED + ">=? AND " + DL_CREATED + "<?",
                new String[]{String.valueOf(startMs), String.valueOf(endMs)},
                null, null, DL_CREATED + " DESC");
        return collectDailyLogs(c);
    }

    /** 全部足迹，按时间倒序，最多 limit 条 */
    public List<DailyLog> queryAllDailyLogs(int limit) {
        Cursor c = getReadableDatabase().query(TABLE_DAILY, null,
                null, null, null, null, DL_CREATED + " DESC",
                String.valueOf(limit));
        return collectDailyLogs(c);
    }

    public int countDailyLogsToday() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_DAILY + " WHERE " + DL_CREATED + ">=? AND "
                        + DL_CREATED + "<?",
                new String[]{String.valueOf(todayStart()),
                        String.valueOf(todayStart() + 24L * 60 * 60 * 1000)});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    public int countDailyLogsTotal() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_DAILY, null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    /** 连续记录天数：从今天往回数，连续有足迹记录的天数 */
    public int dailyStreakDays() {
        Set<Long> days = new HashSet<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + DL_CREATED + " FROM " + TABLE_DAILY, null);
        try {
            while (c.moveToNext()) {
                days.add(dayIndex(c.getLong(0)));
            }
        } finally {
            c.close();
        }
        if (days.isEmpty()) {
            return 0;
        }
        long today = dayIndex(System.currentTimeMillis());
        int streak = 0;
        // 今天还没记录时，从昨天开始算，避免早上打开就显示中断
        long cursor = days.contains(today) ? today : today - 1;
        while (days.contains(cursor)) {
            streak++;
            cursor--;
        }
        return streak;
    }

    /* ---------------- 统计 ---------------- */

    /** 已掌握题数（mastery=1） */
    public int countMastered() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE " + COL_MASTERY + "=1", null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    /** 今日到期需复习的题数 */
    public int countDueToday() {
        long now = System.currentTimeMillis();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE " + COL_NEXT_REVIEW + "<=? OR " + COL_REVIEW_COUNT + "=0",
                new String[]{String.valueOf(now)});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    /** 今日已复习次数（从今天 0 点起 reviews 表的记录数） */
    public int countReviewedToday() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_REVIEWS + " WHERE " + RV_TIME + ">=?",
                new String[]{String.valueOf(todayStart())});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    /** 连续打卡天数：从今天往回数，连续有复习记录的天数 */
    public int streakDays() {
        Set<Long> days = new HashSet<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + RV_TIME + " FROM " + TABLE_REVIEWS, null);
        try {
            while (c.moveToNext()) {
                days.add(dayIndex(c.getLong(0)));
            }
        } finally {
            c.close();
        }
        if (days.isEmpty()) {
            return 0;
        }
        long today = dayIndex(System.currentTimeMillis());
        int streak = 0;
        // 如果今天还没复习，从昨天开始算
        long cursor = days.contains(today) ? today : today - 1;
        while (days.contains(cursor)) {
            streak++;
            cursor--;
        }
        return streak;
    }

    /** 各栏目掌握进度：返回 [栏目, 已掌握数, 总数] */
    public List<int[]> categoryProgressCounts(List<String> categories) {
        List<int[]> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        for (String cat : categories) {
            int total = count(cat);
            Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE + " WHERE " + COL_CATEGORY
                    + "=? AND " + COL_MASTERY + "=1", new String[]{cat});
            int mastered = 0;
            try { if (c.moveToFirst()) mastered = c.getInt(0); } finally { c.close(); }
            result.add(new int[]{mastered, total});
        }
        return result;
    }

    private long todayStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private long dayIndex(long timeMs) {
        // 转成本地"第几天"，用于判断同一天
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis() / (24L * 60 * 60 * 1000);
    }

    /* ---------------- 云同步 ---------------- */

    /** 待推送的题目（dirty=1），按内容时间戳先来后到 */
    public List<Note> pendingNotes(int limit) {
        List<Note> result = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE, null, COL_DIRTY + "=1",
                null, null, null, COL_CONTENT_TS + " ASC", String.valueOf(limit));
        return collect(c);
    }

    public void markNoteSynced(@NonNull String uuid, long seq) {
        ContentValues cv = new ContentValues();
        cv.put(COL_DIRTY, 0);
        cv.put(COL_SEQ, seq);
        getWritableDatabase().update(TABLE, cv, COL_UUID + "=?", new String[]{uuid});
    }

    /** 待推送的复习记录 */
    public List<ReviewRow> pendingReviews(int limit) {
        List<ReviewRow> result = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_REVIEWS,
                new String[]{RV_UUID, RV_NOTE_UUID, RV_GRADE, RV_TIME, RV_SEQ},
                RV_DIRTY + "=1", null, null, null, RV_TIME + " ASC", String.valueOf(limit));
        try {
            while (c.moveToNext()) {
                ReviewRow r = new ReviewRow();
                r.uuid = c.getString(0);
                r.noteUuid = c.getString(1);
                r.grade = c.getInt(2);
                r.time = c.getLong(3);
                r.seq = c.getLong(4);
                result.add(r);
            }
        } finally {
            c.close();
        }
        return result;
    }

    public void markReviewSynced(@NonNull String uuid, long seq) {
        ContentValues cv = new ContentValues();
        cv.put(RV_DIRTY, 0);
        cv.put(RV_SEQ, seq);
        getWritableDatabase().update(TABLE_REVIEWS, cv, RV_UUID + "=?", new String[]{uuid});
    }

    /** 待推送的今日足迹 */
    public List<DailyLog> pendingDailyLogs(int limit) {
        Cursor c = getReadableDatabase().query(TABLE_DAILY, null, DL_DIRTY + "=1",
                null, null, null, DL_UPDATED + " ASC", String.valueOf(limit));
        return collectDailyLogs(c);
    }

    public void markDailyLogSynced(@NonNull String uuid, long seq) {
        ContentValues cv = new ContentValues();
        cv.put(DL_DIRTY, 0);
        cv.put(DL_SEQ, seq);
        getWritableDatabase().update(TABLE_DAILY, cv, DL_UUID + "=?", new String[]{uuid});
    }

    /** 待推送的删除墓碑 */
    public List<Tombstone> pendingTombstones(int limit) {
        List<Tombstone> result = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE_TOMB, null, null, null, null, null,
                T_TS + " ASC", String.valueOf(limit));
        try {
            while (c.moveToNext()) {
                result.add(new Tombstone(c.getString(0), c.getString(1), c.getLong(2)));
            }
        } finally {
            c.close();
        }
        return result;
    }

    public void clearTombstone(@NonNull String kind, @NonNull String uuid) {
        getWritableDatabase().delete(TABLE_TOMB, T_KIND + "=? AND " + T_UUID + "=?",
                new String[]{kind, uuid});
    }

    /**
     * 应用服务器下发的题目（字段组 LWW）。
     * 本地更新则保留本地版本等推送；远端更新则覆盖对应字段组。
     *
     * @return true 表示本地数据被改动
     */
    public boolean upsertNote(@NonNull Note n) {
        if (n.uuid == null || n.uuid.isEmpty()) {
            return false;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Note local = null;
            Cursor c = db.query(TABLE, null, COL_UUID + "=?", new String[]{n.uuid},
                    null, null, null);
            try {
                if (c.moveToFirst()) {
                    local = cursorToNote(c);
                }
            } finally {
                c.close();
            }

            if (local == null) {
                long tombTs = tombstoneTs(db, KIND_NOTE, n.uuid);
                if (tombTs > 0 && tombTs >= n.contentTs) {
                    // 本机删过且更新，保留墓碑，等推送后服务器自会收敛
                    db.setTransactionSuccessful();
                    return false;
                }
                if (tombTs > 0) {
                    db.delete(TABLE_TOMB, T_KIND + "=? AND " + T_UUID + "=?",
                            new String[]{KIND_NOTE, n.uuid});
                }
                db.insert(TABLE, null, remoteNoteValues(n));
                db.setTransactionSuccessful();
                return true;
            }

            boolean contentNewer = n.contentTs > local.contentTs;
            boolean srsNewer = n.srsTs > local.srsTs;
            if (!contentNewer && !srsNewer) {
                db.setTransactionSuccessful();
                return false;
            }
            ContentValues cv = new ContentValues();
            if (contentNewer) {
                cv.put(COL_NAME, n.name);
                cv.put(COL_DEFINITION, n.definition);
                cv.put(COL_CATEGORY, n.category);
                cv.put(COL_CREATED, n.createdAt > 0 ? n.createdAt : local.createdAt);
                cv.put(COL_CONTENT_TS, n.contentTs);
            }
            if (srsNewer) {
                cv.put(COL_MASTERY, n.mastery);
                cv.put(COL_INTERVAL, n.intervalDays);
                cv.put(COL_EASE, n.easeFactor <= 0 ? 250 : n.easeFactor);
                cv.put(COL_REVIEW_COUNT, n.reviewCount);
                cv.put(COL_NEXT_REVIEW, n.nextReview);
                cv.put(COL_LAST_REVIEW, n.lastReview);
                cv.put(COL_STARS, n.stars);
                cv.put(COL_SRS_TS, n.srsTs);
            }
            // 有一组没被覆盖，说明本地那组还有没推出去的改动
            boolean keepDirty = local.dirty && (!contentNewer || !srsNewer);
            cv.put(COL_DIRTY, keepDirty ? 1 : 0);
            cv.put(COL_SEQ, n.seq);
            db.update(TABLE, cv, COL_UUID + "=?", new String[]{n.uuid});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /** 应用服务器下发的复习记录（uuid 幂等），返回是否新插入 */
    public boolean upsertReview(@NonNull ReviewRow r) {
        if (r.uuid == null || r.uuid.isEmpty()) {
            return false;
        }
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.query(TABLE_REVIEWS, new String[]{RV_ID}, RV_UUID + "=?",
                new String[]{r.uuid}, null, null, null);
        boolean exists;
        try {
            exists = c.moveToFirst();
        } finally {
            c.close();
        }
        if (exists) {
            return false;
        }
        ContentValues cv = new ContentValues();
        cv.put(RV_NOTE_ID, noteIdOfUuid(db, r.noteUuid));
        cv.put(RV_GRADE, r.grade);
        cv.put(RV_TIME, r.time);
        cv.put(RV_UUID, r.uuid);
        cv.put(RV_NOTE_UUID, r.noteUuid);
        cv.put(RV_DIRTY, 0);
        cv.put(RV_SEQ, r.seq);
        db.insert(TABLE_REVIEWS, null, cv);
        return true;
    }

    /** 应用服务器下发的足迹（按 updatedTs 后写胜出） */
    public boolean upsertDailyLog(@NonNull DailyLog d) {
        if (d.uuid == null || d.uuid.isEmpty()) {
            return false;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            DailyLog local = null;
            Cursor c = db.query(TABLE_DAILY, null, DL_UUID + "=?", new String[]{d.uuid},
                    null, null, null);
            try {
                if (c.moveToFirst()) {
                    DailyLog tmp = new DailyLog(
                            c.getLong(c.getColumnIndexOrThrow(DL_ID)),
                            c.getString(c.getColumnIndexOrThrow(DL_CONTENT)),
                            c.getLong(c.getColumnIndexOrThrow(DL_CREATED)));
                    tmp.uuid = c.getString(c.getColumnIndex(DL_UUID));
                    tmp.updatedTs = getLongSafe(c, DL_UPDATED, 0);
                    tmp.dirty = getIntSafe(c, DL_DIRTY, 0) == 1;
                    local = tmp;
                }
            } finally {
                c.close();
            }
            boolean applied = false;
            if (local == null) {
                long tombTs = tombstoneTs(db, KIND_DAILY, d.uuid);
                if (tombTs > 0 && tombTs >= d.updatedTs) {
                    db.setTransactionSuccessful();
                    return false;
                }
                if (tombTs > 0) {
                    db.delete(TABLE_TOMB, T_KIND + "=? AND " + T_UUID + "=?",
                            new String[]{KIND_DAILY, d.uuid});
                }
                ContentValues cv = new ContentValues();
                cv.put(DL_CONTENT, d.content);
                cv.put(DL_CREATED, d.createdAt);
                cv.put(DL_UUID, d.uuid);
                cv.put(DL_UPDATED, d.updatedTs);
                cv.put(DL_DIRTY, 0);
                cv.put(DL_SEQ, d.seq);
                db.insert(TABLE_DAILY, null, cv);
                applied = true;
            } else if (d.updatedTs > local.updatedTs) {
                ContentValues cv = new ContentValues();
                cv.put(DL_CONTENT, d.content);
                cv.put(DL_UPDATED, d.updatedTs);
                cv.put(DL_DIRTY, 0);
                cv.put(DL_SEQ, d.seq);
                db.update(TABLE_DAILY, cv, DL_UUID + "=?", new String[]{d.uuid});
                applied = true;
            }
            db.setTransactionSuccessful();
            return applied;
        } finally {
            db.endTransaction();
        }
    }

    /** 服务器说某行被删了：按时间戳决定是否删本地 */
    public boolean applyRemoteTombstone(@NonNull String kind, @NonNull String uuid, long ts) {
        SQLiteDatabase db = getWritableDatabase();
        long localTs;
        if (KIND_NOTE.equals(kind)) {
            Note n = noteByUuid(db, uuid);
            if (n == null) {
                return false;
            }
            localTs = Math.max(n.contentTs, n.srsTs);
            if (localTs >= ts) {
                return false;
            }
            db.delete(TABLE_REVIEWS, RV_NOTE_UUID + "=?", new String[]{uuid});
            db.delete(TABLE, COL_UUID + "=?", new String[]{uuid});
        } else if (KIND_DAILY.equals(kind)) {
            long[] found = new long[]{-1, 0};
            Cursor c = db.query(TABLE_DAILY, new String[]{DL_ID, DL_UPDATED}, DL_UUID + "=?",
                    new String[]{uuid}, null, null, null);
            try {
                if (c.moveToFirst()) {
                    found[0] = c.getLong(0);
                    found[1] = c.getLong(1);
                }
            } finally {
                c.close();
            }
            if (found[0] < 0 || found[1] >= ts) {
                return false;
            }
            db.delete(TABLE_DAILY, DL_UUID + "=?", new String[]{uuid});
        } else {
            return false;
        }
        return true;
    }

    public long getLastSyncSeq() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + SS_VAL + " FROM " + TABLE_SYNC_STATE + " WHERE " + SS_KEY + "='last_seq'",
                null);
        try {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        } finally {
            c.close();
        }
    }

    public void setLastSyncSeq(long seq) {
        ContentValues cv = new ContentValues();
        cv.put(SS_KEY, "last_seq");
        cv.put(SS_VAL, seq);
        getWritableDatabase().insertWithOnConflict(TABLE_SYNC_STATE, null, cv,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * 推送成功后把整行标记为干净。
     * 只清理「推上去那一刻的样子」——用时间戳做条件，防止推送期间本地又改了还被误标干净。
     */
    public void markAllSynced(List<Note> notes, List<ReviewRow> reviews,
                              List<DailyLog> dailies, List<Tombstone> tombs, long seq) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put(COL_DIRTY, 0);
            cv.put(COL_SEQ, seq);
            for (Note n : notes) {
                if (n.uuid == null) continue;
                db.update(TABLE, cv,
                        COL_UUID + "=? AND " + COL_CONTENT_TS + "<=? AND " + COL_SRS_TS + "<=?",
                        new String[]{n.uuid, String.valueOf(n.contentTs), String.valueOf(n.srsTs)});
            }
            ContentValues rv = new ContentValues();
            rv.put(RV_DIRTY, 0);
            rv.put(RV_SEQ, seq);
            for (ReviewRow r : reviews) {
                if (r.uuid == null) continue;
                db.update(TABLE_REVIEWS, rv, RV_UUID + "=?", new String[]{r.uuid});
            }
            ContentValues dv = new ContentValues();
            dv.put(DL_DIRTY, 0);
            dv.put(DL_SEQ, seq);
            for (DailyLog d : dailies) {
                if (d.uuid == null) continue;
                db.update(TABLE_DAILY, dv,
                        DL_UUID + "=? AND " + DL_UPDATED + "<=?",
                        new String[]{d.uuid, String.valueOf(d.updatedTs)});
            }
            for (Tombstone t : tombs) {
                db.delete(TABLE_TOMB, T_KIND + "=? AND " + T_UUID + "=? AND " + T_TS + "<=?",
                        new String[]{t.kind, t.uuid, String.valueOf(t.ts)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** 同步推送时表示一条待同步的复习记录 */
    public static class ReviewRow {
        public String uuid;
        public String noteUuid;
        public int grade;
        public long time;
        public long seq;
    }

    /** 一条待同步的删除墓碑 */
    public static class Tombstone {
        public final String kind;
        public final String uuid;
        public final long ts;

        public Tombstone(String kind, String uuid, long ts) {
            this.kind = kind;
            this.uuid = uuid;
            this.ts = ts;
        }
    }

    /* ---------------- 备份导入 ---------------- */

    /**
     * 从备份文件写入一条题：文件内容为准，不走 LWW。
     * uuid 已存在则覆盖那一条（重复导入不会产生副本），时间戳刷新为当前，并标记待推送。
     */
    public long importNote(@NonNull Note n) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = toValues(n);
        long existing = n.uuid == null || n.uuid.isEmpty() ? 0 : noteIdOfUuid(db, n.uuid);
        if (existing > 0) {
            db.update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(existing)});
            return existing;
        }
        return db.insert(TABLE, null, cv);
    }

    /** 从备份文件写入一条足迹，uuid 已存在则跳过 */
    public long importDailyLog(@NonNull DailyLog d) {
        SQLiteDatabase db = getWritableDatabase();
        if (d.uuid != null && !d.uuid.isEmpty()) {
            Cursor c = db.query(TABLE_DAILY, new String[]{DL_ID}, DL_UUID + "=?",
                    new String[]{d.uuid}, null, null, null);
            boolean exists;
            try {
                exists = c.moveToFirst();
            } finally {
                c.close();
            }
            if (exists) {
                return 0;
            }
        }
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put(DL_CONTENT, d.content);
        cv.put(DL_CREATED, d.createdAt > 0 ? d.createdAt : now);
        cv.put(DL_UUID, d.uuid != null && !d.uuid.isEmpty() ? d.uuid : newUuid());
        cv.put(DL_UPDATED, now);
        cv.put(DL_DIRTY, 1);
        cv.put(DL_SEQ, 0);
        return db.insert(TABLE_DAILY, null, cv);
    }

    /** 按 uuid 找本地 id，找不到返回 0 */
    public long idOfUuid(@Nullable String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return 0L;
        }
        return noteIdOfUuid(getReadableDatabase(), uuid);
    }

    /* ---------------- 同步内部工具 ---------------- */

    private ContentValues remoteNoteValues(Note n) {
        ContentValues cv = toValues(n);
        cv.put(COL_UUID, n.uuid);
        cv.put(COL_CONTENT_TS, n.contentTs);
        cv.put(COL_SRS_TS, n.srsTs);
        cv.put(COL_DIRTY, 0);
        cv.put(COL_SEQ, n.seq);
        return cv;
    }

    private void addTombstone(SQLiteDatabase db, String kind, String uuid, long ts) {
        long old = tombstoneTs(db, kind, uuid);
        ContentValues cv = new ContentValues();
        cv.put(T_KIND, kind);
        cv.put(T_UUID, uuid);
        cv.put(T_TS, Math.max(old, ts));
        db.insertWithOnConflict(TABLE_TOMB, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private long tombstoneTs(SQLiteDatabase db, String kind, String uuid) {
        Cursor c = db.query(TABLE_TOMB, new String[]{T_TS},
                T_KIND + "=? AND " + T_UUID + "=?", new String[]{kind, uuid}, null, null, null);
        try {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        } finally {
            c.close();
        }
    }

    @Nullable
    private String uuidOfNote(SQLiteDatabase db, long id) {
        Cursor c = db.query(TABLE, new String[]{COL_UUID}, COL_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    @Nullable
    private Note noteByUuid(SQLiteDatabase db, String uuid) {
        Cursor c = db.query(TABLE, null, COL_UUID + "=?", new String[]{uuid}, null, null, null);
        try {
            return c.moveToFirst() ? cursorToNote(c) : null;
        } finally {
            c.close();
        }
    }

    /** 本地还没有这条远端复习记录对应的题时返回 0，避免悬空外键 */
    private long noteIdOfUuid(SQLiteDatabase db, String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return 0L;
        }
        Cursor c = db.query(TABLE, new String[]{COL_ID}, COL_UUID + "=?", new String[]{uuid},
                null, null, null);
        try {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        } finally {
            c.close();
        }
    }

    private static String newUuid() {
        return UUID.randomUUID().toString();
    }

    /* ---------------- 工具 ---------------- */

    private List<Note> collect(Cursor c) {
        List<Note> result = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                result.add(cursorToNote(c));
            }
        } finally {
            c.close();
        }
        return result;
    }

    private List<DailyLog> collectDailyLogs(Cursor c) {
        List<DailyLog> result = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                DailyLog d = new DailyLog(
                        c.getLong(c.getColumnIndexOrThrow(DL_ID)),
                        c.getString(c.getColumnIndexOrThrow(DL_CONTENT)),
                        c.getLong(c.getColumnIndexOrThrow(DL_CREATED)));
                d.uuid = c.getString(c.getColumnIndex(DL_UUID));
                d.updatedTs = getLongSafe(c, DL_UPDATED, 0);
                d.dirty = getIntSafe(c, DL_DIRTY, 0) == 1;
                d.seq = getLongSafe(c, DL_SEQ, 0);
                result.add(d);
            }
        } finally {
            c.close();
        }
        return result;
    }

    private Note cursorToNote(Cursor c) {
        Note n = new Note(
                c.getLong(c.getColumnIndexOrThrow(COL_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_NAME)),
                c.getString(c.getColumnIndexOrThrow(COL_DEFINITION)),
                c.getString(c.getColumnIndexOrThrow(COL_CATEGORY)),
                c.getLong(c.getColumnIndexOrThrow(COL_CREATED)),
                getIntSafe(c, COL_MASTERY, 0)
        );
        n.intervalDays = getIntSafe(c, COL_INTERVAL, 0);
        n.easeFactor = getIntSafe(c, COL_EASE, 250);
        n.reviewCount = getIntSafe(c, COL_REVIEW_COUNT, 0);
        n.nextReview = getLongSafe(c, COL_NEXT_REVIEW, 0);
        n.lastReview = getLongSafe(c, COL_LAST_REVIEW, 0);
        n.stars = getIntSafe(c, COL_STARS, 0);
        n.uuid = c.getString(c.getColumnIndex(COL_UUID));
        n.contentTs = getLongSafe(c, COL_CONTENT_TS, 0);
        n.srsTs = getLongSafe(c, COL_SRS_TS, 0);
        n.dirty = getIntSafe(c, COL_DIRTY, 0) == 1;
        n.seq = getLongSafe(c, COL_SEQ, 0);
        return n;
    }

    private int getIntSafe(Cursor c, String col, int def) {
        int idx = c.getColumnIndex(col);
        return idx >= 0 ? c.getInt(idx) : def;
    }

    private long getLongSafe(Cursor c, String col, long def) {
        int idx = c.getColumnIndex(col);
        return idx >= 0 ? c.getLong(idx) : def;
    }
}
