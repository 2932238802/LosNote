package com.example.losnote.sync;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 云同步连接配置：服务器地址 + 访问令牌。
 * 单用户场景，一条令牌就够；存在 SharedPreferences 里。
 */
public class SyncConfig {

    private static final String SP = "sync_settings";
    private static final String K_URL = "server_url";
    private static final String K_TOKEN = "token";
    private static final String K_LAST_AT = "last_sync_at";

    public String serverUrl = "";
    public String token = "";
    public long lastSyncAt;

    public static SyncConfig load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE);
        SyncConfig c = new SyncConfig();
        c.serverUrl = sp.getString(K_URL, "");
        c.token = sp.getString(K_TOKEN, "");
        c.lastSyncAt = sp.getLong(K_LAST_AT, 0);
        return c;
    }

    public void save(Context ctx) {
        serverUrl = normalize(serverUrl);
        token = token == null ? "" : token.trim();
        SharedPreferences sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE);
        sp.edit()
                .putString(K_URL, serverUrl)
                .putString(K_TOKEN, token)
                .putLong(K_LAST_AT, lastSyncAt)
                .apply();
    }

    public void touchLastSync(Context ctx) {
        lastSyncAt = System.currentTimeMillis();
        ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
                .edit().putLong(K_LAST_AT, lastSyncAt).apply();
    }

    public boolean isConfigured() {
        return serverUrl != null && !serverUrl.isEmpty()
                && token != null && !token.isEmpty();
    }

    /** 例如 http://1.2.3.4:8787 */
    public String apiBase() {
        return normalize(serverUrl);
    }

    private static String normalize(String url) {
        String s = url == null ? "" : url.trim();
        if (s.isEmpty()) {
            return "";
        }
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "http://" + s;
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
