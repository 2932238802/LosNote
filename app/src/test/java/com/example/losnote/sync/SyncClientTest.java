package com.example.losnote.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.example.losnote.data.DBHelper;
import com.example.losnote.data.Note;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 同步客户端端到端测试：真起一个本地 HTTP 桩服务（JDK 自带 HttpServer），
 * 走真实的 HttpURLConnection，验证请求体形状、响应落库、游标推进与错误映射。
 *
 * 之前只有一个「协议契约」的 node 脚本验证过字段名，但客户端的 Java 代码从没真正跑过。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SyncClientTest {

    private static final String DB_NAME = "losnote.db";

    private Context ctx;
    private DBHelper db;
    private Stub stub;

    @Before
    public void setUp() throws Exception {
        ctx = RuntimeEnvironment.getApplication();
        ctx.deleteDatabase(DB_NAME);
        resetSingleton();
        db = DBHelper.get(ctx);

        stub = new Stub();
        stub.start();

        SyncConfig cfg = new SyncConfig();
        cfg.serverUrl = stub.base();
        cfg.token = "test-token";
        cfg.save(ctx);
    }

    @After
    public void tearDown() throws Exception {
        stub.stop();
        ctx.deleteDatabase(DB_NAME);
        resetSingleton();
    }

    private static void resetSingleton() throws Exception {
        Field f = DBHelper.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    @Test
    public void pushLocalNote_clearsDirtyAndSendsExpectedFields() throws Exception {
        db.insert(new Note("闭包是什么", "函数 + 词法环境", "JS"));

        stub.postReply = "{\"seq\":5,\"applied\":{\"notes\":1,\"reviews\":0,\"dailyLogs\":0},"
                + "\"conflicts\":{\"notes\":[],\"dailyLogs\":[]}}";
        stub.getReply = "{\"seq\":5,\"hasMore\":false,\"notes\":[],\"reviews\":[],\"dailyLogs\":[]}";

        SyncResult r = SyncClient.sync(ctx);

        assertTrue("同步应当成功：" + r.error, r.ok);
        assertNull(r.error);
        assertEquals("上传的题数", 1, r.pushedNotes);
        assertEquals("推完要标干净", 0, db.pendingNotes(10).size());
        assertEquals("游标要推进到服务器序号", 5, db.getLastSyncSeq());

        JSONObject body = new JSONObject(stub.lastPost);
        JSONArray notes = body.getJSONArray("notes");
        assertEquals(1, notes.length());
        JSONObject n = notes.getJSONObject(0);
        assertEquals("闭包是什么", n.getString("name"));
        assertEquals("函数 + 词法环境", n.getString("definition"));
        assertEquals("JS", n.getString("category"));
        assertEquals("默认难度因子", 250, n.getInt("easeFactor"));
        assertFalse("普通题不是删除", n.getBoolean("deleted"));
        assertTrue("uuid 必须带上，否则服务器会另造一条", n.getString("uuid").length() > 10);
        assertTrue("请求要带令牌", stub.lastAuth.startsWith("Bearer "));
        assertEquals("令牌值", "Bearer test-token", stub.lastAuth);
    }

    @Test
    public void pushDeletedNote_sendsTombstone() throws Exception {
        db.insert(new Note("要删的题", "x", "临时"));
        Note n = db.queryAll().get(0);
        db.delete(n.id);

        stub.postReply = "{\"seq\":8,\"applied\":{\"notes\":1,\"reviews\":0,\"dailyLogs\":0},"
                + "\"conflicts\":{\"notes\":[],\"dailyLogs\":[]}}";
        stub.getReply = "{\"seq\":8,\"hasMore\":false,\"notes\":[],\"reviews\":[],\"dailyLogs\":[]}";

        SyncResult r = SyncClient.sync(ctx);
        assertTrue(r.ok);
        assertEquals("墓碑不算普通题", 0, r.pushedNotes);

        JSONObject body = new JSONObject(stub.lastPost);
        JSONArray notes = body.getJSONArray("notes");
        assertEquals("删除的题也占一条", 1, notes.length());
        JSONObject t = notes.getJSONObject(0);
        assertEquals(n.uuid, t.getString("uuid"));
        assertTrue("墓碑要标 deleted", t.getBoolean("deleted"));
        assertTrue("墓碑要带时间戳", t.getLong("contentTs") > 0);
    }

    @Test
    public void pullRemoteNote_landsCleanAndAdvancesCursor() throws Exception {
        long ts = System.currentTimeMillis() - 1000;
        stub.getReply = "{\"seq\":9,\"hasMore\":false,\"notes\":[{\"uuid\":\"remote-uuid-1\","
                + "\"name\":\"网页上建的题\",\"definition\":\"来自服务端\",\"category\":\"云\","
                + "\"createdAt\":" + ts + ",\"mastery\":1,\"intervalDays\":3,\"easeFactor\":265,"
                + "\"reviewCount\":1,\"nextReview\":" + (ts + 86400000L) + ",\"lastReview\":" + ts + ","
                + "\"stars\":3,\"contentTs\":" + ts + ",\"srsTs\":" + ts + ",\"deleted\":false}],"
                + "\"reviews\":[],\"dailyLogs\":[]}";

        SyncResult r = SyncClient.sync(ctx);
        assertTrue("同步应当成功：" + r.error, r.ok);

        Note n = db.queryAll().get(0);
        assertEquals("网页上建的题", n.name);
        assertEquals("remote-uuid-1", n.uuid);
        assertEquals("星星要落库", 3, n.stars);
        assertEquals("复习次数要落库", 1, n.reviewCount);
        assertEquals("拉到的题数", 1, r.pulledNotes);
        assertEquals("游标推进", 9L, db.getLastSyncSeq());
        assertEquals("远端来的行是干净的，不该再推回去", 0, db.pendingNotes(10).size());

        // 再来一次：服务器没有新数据，应该「已是最新」
        stub.getReply = "{\"seq\":9,\"hasMore\":false,\"notes\":[],\"reviews\":[],\"dailyLogs\":[]}";
        SyncResult again = SyncClient.sync(ctx);
        assertTrue(again.ok);
        assertFalse("第二次不该再有改动", again.changedAnything());
        assertTrue(again.summary().contains("已是最新"));
    }

    @Test
    public void pullRemoteTombstone_deletesLocalNote() throws Exception {
        db.insert(new Note("手机上删掉", "x", "云"));
        Note local = db.queryAll().get(0);

        // 先把本地这条标干净，模拟「之前已同步过」
        stub.postReply = "{\"seq\":1,\"applied\":{\"notes\":1,\"reviews\":0,\"dailyLogs\":0},"
                + "\"conflicts\":{\"notes\":[],\"dailyLogs\":[]}}";
        stub.getReply = "{\"seq\":1,\"hasMore\":false,\"notes\":[],\"reviews\":[],\"dailyLogs\":[]}";
        assertTrue(SyncClient.sync(ctx).ok);

        // 网页端删掉了它：服务器下发墓碑
        stub.getReply = "{\"seq\":2,\"hasMore\":false,\"notes\":[{\"uuid\":\"" + local.uuid + "\","
                + "\"deleted\":true,\"contentTs\":" + (System.currentTimeMillis() + 1000) + ","
                + "\"srsTs\":0}],\"reviews\":[],\"dailyLogs\":[]}";

        SyncResult r = SyncClient.sync(ctx);
        assertTrue(r.ok);
        assertEquals("本地题应被删除", 0, db.queryAll().size());
        assertEquals("删除计数", 1, r.deleted);
        // 墓碑是服务器下发的，本地不需要再回推一条
        assertEquals(0, db.pendingTombstones(10).size());
    }

    @Test
    public void unauthorized_setsFriendlyError() throws Exception {
        db.insert(new Note("随便一题", "x", "云"));
        stub.status = 401;
        stub.failBody = "{\"error\":\"无效的访问令牌\"}";

        SyncResult r = SyncClient.sync(ctx);

        assertFalse("401 不能算成功", r.ok);
        assertNotNull(r.error);
        assertTrue("错误里要有状态码，方便排查：" + r.error, r.error.contains("401"));
        assertEquals("失败的推送不能把本地标干净", 1, db.pendingNotes(10).size());
        assertEquals("游标不动", 0, db.getLastSyncSeq());
    }

    /* ---------------- 桩服务 ---------------- */

    /**
     * 只实现 /api/sync 的最小 HTTP 桩服务。
     * 用裸 Socket 手写响应，因为 android.jar 的编译类路径里没有 com.sun.net.httpserver。
     */
    static final class Stub {
        ServerSocket server;
        Thread thread;
        volatile boolean running;
        volatile String postReply = "{\"seq\":0,\"applied\":{},\"conflicts\":{}}";
        volatile String getReply = "{\"seq\":0,\"hasMore\":false,\"notes\":[],\"reviews\":[],\"dailyLogs\":[]}";
        volatile int status = 200;
        volatile String failBody = "{}";
        volatile String lastPost;
        volatile String lastAuth = "";

        void start() throws IOException {
            server = new ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"));
            running = true;
            thread = new Thread(() -> {
                while (running) {
                    try (Socket sock = server.accept()) {
                        handle(sock);
                    } catch (IOException e) {
                        if (!running) {
                            return;
                        }
                    }
                }
            }, "stub-http");
            thread.setDaemon(true);
            thread.start();
        }

        private void handle(Socket sock) throws IOException {
            InputStream in = sock.getInputStream();
            String head = readHead(in);
            String method = head.isEmpty() ? "GET" : head.split(" ")[0];
            String auth = headerValue(head, "authorization");
            lastAuth = auth == null ? "" : auth;
            int len = 0;
            String cl = headerValue(head, "content-length");
            if (cl != null && !cl.isEmpty()) {
                len = Integer.parseInt(cl.trim());
            }
            String body = len > 0 ? readExactly(in, len) : "";
            if ("POST".equals(method)) {
                lastPost = body;
            }
            int code = status;
            String reply = code == 200 ? ("POST".equals(method) ? postReply : getReply) : failBody;
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            String respHead = "HTTP/1.1 " + code + (code == 200 ? " OK" : " ERR") + "\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + out.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream os = sock.getOutputStream();
            os.write(respHead.getBytes(StandardCharsets.US_ASCII));
            os.write(out);
            os.flush();
        }

        private static String readHead(InputStream in) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int prev3 = -1, prev2 = -1, prev1 = -1, cur;
            while ((cur = in.read()) != -1) {
                bos.write(cur);
                if (prev3 == '\r' && prev2 == '\n' && prev1 == '\r' && cur == '\n') {
                    break;
                }
                prev3 = prev2;
                prev2 = prev1;
                prev1 = cur;
            }
            return bos.toString(StandardCharsets.ISO_8859_1.name());
        }

        private static String headerValue(String head, String name) {
            for (String line : head.split("\r\n")) {
                int idx = line.indexOf(':');
                if (idx > 0 && line.substring(0, idx).trim().equalsIgnoreCase(name)) {
                    return line.substring(idx + 1).trim();
                }
            }
            return null;
        }

        private static String readExactly(InputStream in, int len) throws IOException {
            byte[] buf = new byte[len];
            int off = 0;
            while (off < len) {
                int n = in.read(buf, off, len - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
            return new String(buf, 0, off, StandardCharsets.UTF_8);
        }

        void stop() {
            running = false;
            if (server != null) {
                try {
                    server.close();
                } catch (IOException ignored) {
                }
            }
        }

        String base() {
            return "http://127.0.0.1:" + server.getLocalPort();
        }
    }
}
