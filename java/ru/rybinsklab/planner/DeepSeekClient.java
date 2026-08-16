package ru.rybinsklab.planner;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DeepSeekClient {

    static String ask(Context ctx, String systemPrompt, String userMessage) throws Exception {
        SharedPreferences p = ctx.getSharedPreferences("planner", 0);
        String key = p.getString("ai_key", "").trim();
        if (key.isEmpty()) throw new Exception("Введите API-ключ DeepSeek в настройках");

        URL url = new URL("https://api.deepseek.com/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + key);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);

        org.json.JSONObject body = new org.json.JSONObject();
        body.put("model", "deepseek-chat");
        org.json.JSONArray msgs = new org.json.JSONArray();
        msgs.put(new org.json.JSONObject().put("role", "system").put("content", systemPrompt));
        msgs.put(new org.json.JSONObject().put("role", "user").put("content", userMessage));
        body.put("messages", msgs);
        body.put("temperature", 0.7);

        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        String resp = new String(bos.toByteArray(), "UTF-8");

        if (code >= 200 && code < 300) {
            org.json.JSONObject o = new org.json.JSONObject(resp);
            return o.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
        } else {
            String msg = resp;
            try { msg = new org.json.JSONObject(resp).optJSONObject("error").optString("message", resp); } catch (Exception ignored) { }
            throw new Exception("Ошибка API (" + code + "): " + msg);
        }
    }
}
