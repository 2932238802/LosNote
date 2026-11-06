package com.example.losnote.ai;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * AI 配置统一入口。
 * 当前支持：DeepSeek、Qwen(OpenAI兼容)、Custom。
 * 未来 GitHub Copilot 或其它服务可以继续扩展 provider preset。
 */
public class AiConfig {

    public static final String SP = "ai_settings";

    public static final String PROVIDER_DEEPSEEK = "DeepSeek";
    public static final String PROVIDER_QWEN = "Qwen/通义千问";
    public static final String PROVIDER_CUSTOM = "Custom/自定义";

    public String provider;
    public String apiKey;
    public String baseUrl;
    public String model;
    public boolean webSearch;

    public static AiConfig load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(SP, 0);
        AiConfig c = new AiConfig();
        c.provider = sp.getString("provider", PROVIDER_DEEPSEEK);
        c.apiKey = sp.getString("api_key", "");
        c.baseUrl = sp.getString("base_url", defaultBaseUrl(c.provider));
        c.model = sp.getString("model", defaultModel(c.provider));
        c.webSearch = sp.getBoolean("web_search", false);

        // 兼容旧版本 deepseek SharedPreferences
        if (c.apiKey.isEmpty()) {
            String old = ctx.getSharedPreferences("deepseek", 0).getString("api_key", "");
            if (!old.isEmpty()) c.apiKey = old;
        }
        if (c.baseUrl == null || c.baseUrl.trim().isEmpty()) c.baseUrl = defaultBaseUrl(c.provider);
        if (c.model == null || c.model.trim().isEmpty()) c.model = defaultModel(c.provider);
        return c;
    }

    public void save(Context ctx) {
        ctx.getSharedPreferences(SP, 0).edit()
                .putString("provider", provider)
                .putString("api_key", apiKey == null ? "" : apiKey)
                .putString("base_url", baseUrl == null ? "" : baseUrl)
                .putString("model", model == null ? "" : model)
                .putBoolean("web_search", webSearch)
                .apply();
        // 同步旧 deepseek key，兼容旧逻辑
        ctx.getSharedPreferences("deepseek", 0).edit()
                .putString("api_key", apiKey == null ? "" : apiKey)
                .apply();
    }

    public static String defaultBaseUrl(String provider) {
        if (PROVIDER_QWEN.equals(provider)) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
        }
        if (PROVIDER_CUSTOM.equals(provider)) {
            return "";
        }
        return "https://api.deepseek.com/chat/completions";
    }

    public static String defaultModel(String provider) {
        if (PROVIDER_QWEN.equals(provider)) {
            return "qwen-plus";
        }
        if (PROVIDER_CUSTOM.equals(provider)) {
            return "";
        }
        return "deepseek-chat";
    }
}
