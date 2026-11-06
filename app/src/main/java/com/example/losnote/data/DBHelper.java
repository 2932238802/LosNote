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

/**
 * SQLite 数据库帮助类。
 * v1: 基础字段
 * v2: + mastery
 * v3: + SRS 字段（interval/ease/review_count/next_review/last_review）+ reviews 复习记录表
 * v4: + stars（累计星数）
 * v5: + daily_logs 今日足迹表
 */
public class DBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "losnote.db";
    private static final int DB_VERSION = 5;

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

    public static final String TABLE_REVIEWS = "reviews";
    public static final String RV_ID = "id";
    public static final String RV_NOTE_ID = "note_id";
    public static final String RV_GRADE = "grade";
    public static final String RV_TIME = "time";

    public static final String TABLE_DAILY = "daily_logs";
    public static final String DL_ID = "id";
    public static final String DL_CONTENT = "content";
    public static final String DL_CREATED = "created_at";

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
                COL_STARS + " INTEGER DEFAULT 0" +
                ")");
        createReviewsTable(db);
        createDailyLogTable(db);
    }

    private void createReviewsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_REVIEWS + " (" +
                RV_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                RV_NOTE_ID + " INTEGER, " +
                RV_GRADE + " INTEGER, " +
                RV_TIME + " INTEGER" +
                ")");
    }

    private void createDailyLogTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_DAILY + " (" +
                DL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                DL_CONTENT + " TEXT NOT NULL, " +
                DL_CREATED + " INTEGER" +
                ")");
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
        ContentValues cv = new ContentValues();
        cv.put(COL_NAME, n.name);
        cv.put(COL_DEFINITION, n.definition);
        cv.put(COL_CATEGORY, n.category);
        cv.put(COL_CREATED, n.createdAt > 0 ? n.createdAt : System.currentTimeMillis());
        cv.put(COL_MASTERY, n.mastery);
        cv.put(COL_INTERVAL, n.intervalDays);
        cv.put(COL_EASE, n.easeFactor <= 0 ? 250 : n.easeFactor);
        cv.put(COL_REVIEW_COUNT, n.reviewCount);
        cv.put(COL_NEXT_REVIEW, n.nextReview);
        cv.put(COL_LAST_REVIEW, n.lastReview);
        cv.put(COL_STARS, n.stars);
        return cv;
    }

    /* ---------------- 改 ---------------- */

    public int update(@NonNull Note note) {
        ContentValues cv = new ContentValues();
        cv.put(COL_NAME, note.name);
        cv.put(COL_DEFINITION, note.definition);
        cv.put(COL_CATEGORY, note.category);
        return getWritableDatabase().update(TABLE, cv, COL_ID + "=?",
                new String[]{String.valueOf(note.id)});
    }

    public void setMastery(long id, int mastery) {
        ContentValues cv = new ContentValues();
        cv.put(COL_MASTERY, mastery);
        getWritableDatabase().update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    /** 复习后保存 SRS 状态并记录一条复习日志 */
    public void saveReview(@NonNull Note n, int grade) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put(COL_MASTERY, n.mastery);
            cv.put(COL_INTERVAL, n.intervalDays);
            cv.put(COL_EASE, n.easeFactor);
            cv.put(COL_REVIEW_COUNT, n.reviewCount);
            cv.put(COL_NEXT_REVIEW, n.nextReview);
            cv.put(COL_LAST_REVIEW, n.lastReview);
            cv.put(COL_STARS, n.stars);
            db.update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(n.id)});

            ContentValues rv = new ContentValues();
            rv.put(RV_NOTE_ID, n.id);
            rv.put(RV_GRADE, grade);
            rv.put(RV_TIME, System.currentTimeMillis());
            db.insertOrThrow(TABLE_REVIEWS, null, rv);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /* ---------------- 删 ---------------- */

    public void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
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
            for (Long id : ids) {
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
        return getWritableDatabase().update(TABLE, cv, COL_CATEGORY + "=?", new String[]{oldCategory});
    }

    public void deleteAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
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
        ContentValues cv = new ContentValues();
        cv.put(DL_CONTENT, content);
        cv.put(DL_CREATED, System.currentTimeMillis());
        return getWritableDatabase().insert(TABLE_DAILY, null, cv);
    }

    public int deleteDailyLog(long id) {
        return getWritableDatabase().delete(TABLE_DAILY, DL_ID + "=?",
                new String[]{String.valueOf(id)});
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
                result.add(new DailyLog(
                        c.getLong(c.getColumnIndexOrThrow(DL_ID)),
                        c.getString(c.getColumnIndexOrThrow(DL_CONTENT)),
                        c.getLong(c.getColumnIndexOrThrow(DL_CREATED))));
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
