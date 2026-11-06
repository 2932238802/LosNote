package com.example.losnote.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** OpenAI-compatible Chat Completions 调用工具。 */
public class AiClient {

    public static String chat(AiConfig config, String system, String user, double temperature) throws Exception {
        if (config == null || config.apiKey == null || config.apiKey.trim().isEmpty()) {
            throw new RuntimeException("请先在设置中配置 AI API Key");
        }
        if (config.baseUrl == null || config.baseUrl.trim().isEmpty()) {
            throw new RuntimeException("请先在设置中配置 Base URL");
        }
        if (config.model == null || config.model.trim().isEmpty()) {
            throw new RuntimeException("请先在设置中配置模型名");
        }

        JSONObject body = new JSONObject();
        body.put("model", config.model);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));
        String finalUser = user;
        if (config.webSearch) {
            finalUser = "如果你的模型/服务支持联网搜索，请优先结合最新网络信息；如果不支持，请基于已有知识回答。\n\n" + user;
        }
        messages.put(new JSONObject().put("role", "user").put("content", finalUser));
        body.put("messages", messages);
        body.put("temperature", temperature);

        URL url = new URL(config.baseUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + config.apiKey);
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.close();

        InputStream is = conn.getResponseCode() >= 300 ? conn.getErrorStream() : conn.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder resp = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) resp.append(line);
        br.close();
        if (conn.getResponseCode() >= 300) throw new RuntimeException(resp.toString());

        JSONObject json = new JSONObject(resp.toString());
        return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim();
    }

    public static String stripFence(String content) {
        if (content == null) return "";
        return content.trim()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();
    }
}
