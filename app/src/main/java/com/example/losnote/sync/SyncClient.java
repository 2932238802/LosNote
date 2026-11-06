package com.example.losnote.sync;

import android.content.Context;

import com.example.losnote.data.DBHelper;
import com.example.losnote.data.DailyLog;
import com.example.losnote.data.Note;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 与自建服务器同步：先推本地改动，再按服务器序号拉增量，循环到没有新数据为止。
 *
 * 协议与 {@code docs/sync-design.md} 一致：
 *   POST /api/sync {notes, reviews, dailyLogs}  -> {seq, applied, conflicts}
 *   GET  /api/sync?since=&limit=                -> {seq, hasMore, notes, reviews, dailyLogs}
 * 鉴权：Authorization: Bearer <token>
 */
public class SyncClient {

    private static final int BATCH = 200;
    private static final int MAX_ROUNDS = 40;
    private static final int TIMEOUT_MS = 15000;

    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public static SyncResult sync(Context context) {
        SyncConfig cfg = SyncConfig.load(context);
        if (!cfg.isConfigured()) {
            return SyncResult.fail("还没有填写服务器地址或访问令牌");
        }
        if (!RUNNING.compareAndSet(false, true)) {
            SyncResult busy = new SyncResult();
            busy.ok = true;
            busy.error = null;
            return busy;
        }
        DBHelper db = DBHelper.get(context);
        SyncResult result = new SyncResult();
        try {
            for (int round = 0; round < MAX_ROUNDS; round++) {
                boolean pushed = push(db, cfg, result);
                boolean more = pull(db, cfg, result);
                if (!pushed && !more) {
                    break;
                }
            }
            cfg.touchLastSync(context);
            result.ok = true;
        } catch (Exception e) {
            result.ok = false;
            result.error = e.getMessage() == null ? e.toString() : e.getMessage();
        } finally {
            RUNNING.set(false);
        }
        return result;
    }

    /* ---------------- 推 ---------------- */

    private static boolean push(DBHelper db, SyncConfig cfg, SyncResult r) throws Exception {
        List<Note> notes = db.pendingNotes(BATCH);
        List<DBHelper.ReviewRow> reviews = db.pendingReviews(BATCH);
        List<DailyLog> dailies = db.pendingDailyLogs(BATCH);
        List<DBHelper.Tombstone> tombs = db.pendingTombstones(BATCH);
        if (notes.isEmpty() && reviews.isEmpty() && dailies.isEmpty() && tombs.isEmpty()) {
            return false;
        }

        JSONArray noteArr = new JSONArray();
        for (Note n : notes) {
            noteArr.put(noteJson(n));
        }
        for (DBHelper.Tombstone t : tombs) {
            if (!DBHelper.KIND_NOTE.equals(t.kind)) {
                continue;
            }
            JSONObject o = new JSONObject();
            o.put("uuid", t.uuid);
            o.put("deleted", true);
            o.put("contentTs", t.ts);
            o.put("srsTs", t.ts);
            noteArr.put(o);
        }

        JSONArray reviewArr = new JSONArray();
        for (DBHelper.ReviewRow rv : reviews) {
            JSONObject o = new JSONObject();
            o.put("uuid", rv.uuid);
            o.put("noteUuid", rv.noteUuid == null ? JSONObject.NULL : rv.noteUuid);
            o.put("grade", rv.grade);
            o.put("time", rv.time);
            reviewArr.put(o);
        }

        JSONArray dailyArr = new JSONArray();
        for (DailyLog d : dailies) {
            JSONObject o = new JSONObject();
            o.put("uuid", d.uuid);
            o.put("content", d.content == null ? "" : d.content);
            o.put("createdAt", d.createdAt);
            o.put("updatedTs", d.updatedTs);
            o.put("deleted", false);
            dailyArr.put(o);
        }
        for (DBHelper.Tombstone t : tombs) {
            if (!DBHelper.KIND_DAILY.equals(t.kind)) {
                continue;
            }
            JSONObject o = new JSONObject();
            o.put("uuid", t.uuid);
            o.put("deleted", true);
            o.put("updatedTs", t.ts);
            dailyArr.put(o);
        }

        JSONObject body = new JSONObject();
        body.put("notes", noteArr);
        body.put("reviews", reviewArr);
        body.put("dailyLogs", dailyArr);

        JSONObject resp = request(cfg, "/api/sync", "POST", body.toString());
        long seq = resp.optLong("seq", 0);

        // 服务器顶回来的更新版本，直接落到本地，避免两边反复推
        JSONObject conflicts = resp.optJSONObject("conflicts");
        if (conflicts != null) {
            JSONArray cn = conflicts.optJSONArray("notes");
            for (int i = 0; cn != null && i < cn.length(); i++) {
                JSONObject o = cn.optJSONObject(i);
                if (o != null) {
                    db.upsertNote(parseNote(o));
                }
            }
            JSONArray cd = conflicts.optJSONArray("dailyLogs");
            for (int i = 0; cd != null && i < cd.length(); i++) {
                JSONObject o = cd.optJSONObject(i);
                if (o != null) {
                    db.upsertDailyLog(parseDailyLog(o));
                }
            }
        }

        db.markAllSynced(notes, reviews, dailies, tombs, seq);

        r.pushedNotes += notes.size();
        r.pushedReviews += reviews.size();
        r.pushedDailyLogs += dailies.size();

        // 满批说明后面还有，继续
        return notes.size() == BATCH || reviews.size() == BATCH
                || dailies.size() == BATCH || tombs.size() == BATCH;
    }

    /* ---------------- 拉 ---------------- */

    private static boolean pull(DBHelper db, SyncConfig cfg, SyncResult r) throws Exception {
        long since = db.getLastSyncSeq();
        JSONObject resp = request(cfg, "/api/sync?since=" + since + "&limit=500", "GET", null);

        JSONArray notes = resp.optJSONArray("notes");
        for (int i = 0; notes != null && i < notes.length(); i++) {
            JSONObject o = notes.optJSONObject(i);
            if (o == null) {
                continue;
            }
            Note n = parseNote(o);
            if (n.deleted) {
                if (db.applyRemoteTombstone(DBHelper.KIND_NOTE, n.uuid,
                        Math.max(n.contentTs, n.srsTs))) {
                    r.deleted++;
                }
            } else if (db.upsertNote(n)) {
                r.pulledNotes++;
            }
        }

        JSONArray reviews = resp.optJSONArray("reviews");
        for (int i = 0; reviews != null && i < reviews.length(); i++) {
            JSONObject o = reviews.optJSONObject(i);
            if (o == null) {
                continue;
            }
            DBHelper.ReviewRow rv = new DBHelper.ReviewRow();
            rv.uuid = o.optString("uuid", null);
            rv.noteUuid = o.optString("noteUuid", null);
            rv.grade = o.optInt("grade", 0);
            rv.time = o.optLong("time", 0);
            rv.seq = o.optLong("seq", 0);
            if (db.upsertReview(rv)) {
                r.pulledReviews++;
            }
        }

        JSONArray dailies = resp.optJSONArray("dailyLogs");
        for (int i = 0; dailies != null && i < dailies.length(); i++) {
            JSONObject o = dailies.optJSONObject(i);
            if (o == null) {
                continue;
            }
            DailyLog d = parseDailyLog(o);
            if (d.deleted) {
                if (db.applyRemoteTombstone(DBHelper.KIND_DAILY, d.uuid, d.updatedTs)) {
                    r.deleted++;
                }
            } else if (db.upsertDailyLog(d)) {
                r.pulledDailyLogs++;
            }
        }

        long seq = resp.optLong("seq", since);
        if (seq > since) {
            db.setLastSyncSeq(seq);
        }
        return resp.optBoolean("hasMore", false);
    }

    /* ---------------- JSON ---------------- */

    private static JSONObject noteJson(Note n) throws Exception {
        JSONObject o = new JSONObject();
        o.put("uuid", n.uuid);
        o.put("name", n.name == null ? "" : n.name);
        o.put("definition", n.definition == null ? "" : n.definition);
        o.put("category", n.category == null ? "" : n.category);
        o.put("createdAt", n.createdAt);
        o.put("mastery", n.mastery);
        o.put("intervalDays", n.intervalDays);
        o.put("easeFactor", n.easeFactor);
        o.put("reviewCount", n.reviewCount);
        o.put("nextReview", n.nextReview);
        o.put("lastReview", n.lastReview);
        o.put("stars", n.stars);
        o.put("contentTs", n.contentTs);
        o.put("srsTs", n.srsTs);
        o.put("deleted", false);
        o.put("seq", n.seq);
        return o;
    }

    private static Note parseNote(JSONObject o) {
        Note n = new Note();
        n.uuid = o.optString("uuid", null);
        n.name = o.optString("name", "");
        n.definition = o.optString("definition", "");
        n.category = o.optString("category", "");
        n.createdAt = o.optLong("createdAt", 0);
        n.mastery = o.optInt("mastery", 0);
        n.intervalDays = o.optInt("intervalDays", 0);
        n.easeFactor = o.optInt("easeFactor", 250);
        n.reviewCount = o.optInt("reviewCount", 0);
        n.nextReview = o.optLong("nextReview", 0);
        n.lastReview = o.optLong("lastReview", 0);
        n.stars = o.optInt("stars", 0);
        n.contentTs = o.optLong("contentTs", 0);
        n.srsTs = o.optLong("srsTs", 0);
        n.deleted = o.optBoolean("deleted", false);
        n.seq = o.optLong("seq", 0);
        return n;
    }

    private static DailyLog parseDailyLog(JSONObject o) {
        DailyLog d = new DailyLog();
        d.uuid = o.optString("uuid", null);
        d.content = o.optString("content", "");
        d.createdAt = o.optLong("createdAt", 0);
        d.updatedTs = o.optLong("updatedTs", 0);
        d.deleted = o.optBoolean("deleted", false);
        d.seq = o.optLong("seq", 0);
        return d;
    }

    /* ---------------- HTTP ---------------- */

    private static JSONObject request(SyncConfig cfg, String path, String method, String body)
            throws Exception {
        URL url = new URL(cfg.apiBase() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Authorization", "Bearer " + cfg.token);
            conn.setRequestProperty("Accept", "application/json");
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = conn.getResponseCode();
            String text;
            if (code >= 200 && code < 300) {
                text = readAll(conn.getInputStream());
            } else {
                String err = readAll(conn.getErrorStream());
                throw new Exception("服务器返回 " + code
                        + (err == null || err.isEmpty() ? "" : "：" + brief(err)));
            }
            if (text == null || text.isEmpty()) {
                return new JSONObject();
            }
            return new JSONObject(text);
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static String brief(String text) {
        String s = text.trim();
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    /* ---------------- 后台线程入口 ---------------- */

    private static final java.util.concurrent.ExecutorService EXEC =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    /** 在后台线程跑一次同步，回调在主线程执行（callback 可为 null） */
    public static void syncAsync(final Context context, final Callback callback) {
        final Context app = context.getApplicationContext();
        EXEC.execute(() -> {
            final SyncResult result = sync(app);
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    try {
                        callback.onDone(result);
                    } catch (Throwable ignored) {
                    }
                });
            }
        });
    }

    public interface Callback {
        void onDone(SyncResult result);
    }
}
