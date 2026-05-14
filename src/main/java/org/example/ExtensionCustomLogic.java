package org.example;

import com.nandbox.bots.api.Nandbox;
import com.nandbox.bots.api.NandboxClient;
import com.nandbox.bots.api.data.*;
import com.nandbox.bots.api.inmessages.*;
import com.nandbox.bots.api.outmessages.*;
import com.nandbox.bots.api.util.*;
import com.nandbox.bots.api.test.*;

import net.minidev.json.*;
import net.minidev.json.parser.JSONParser;

import org.example.CallbackAdapter;
import java.io.FileInputStream;
import java.io.IOException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.Properties;

public class ExtensionCustomLogic extends CallbackAdapter {
    private Nandbox.Api api;

    private static final String API_BASE_URL = "https://apiweather.org";
    private static final String API_KEY = "123456789";

    public static void main(String[] args) throws Exception {
        String TOKEN = "";
        Properties properties = new Properties();
        try {
            FileInputStream input = new FileInputStream("token.properties");
            try {
                properties.load(input);
            } finally {
                try { input.close(); } catch (Exception e) { }
            }
            TOKEN = properties.getProperty("Token");
            System.out.println("Token: " + TOKEN);
        } catch (IOException e) {
            e.printStackTrace();
        }
        NandboxClient client = NandboxClient.get();
        client.connect(TOKEN, new ExtensionCustomLogic());
    }

    @Override
    public void onConnect(Nandbox.Api api) {
        this.api = api;
    }

    @Override
    public void onReceive(IncomingMessage incomingMsg) {
        if (incomingMsg == null || incomingMsg.getChat() == null || incomingMsg.getFrom() == null) {
            return;
        }

        String chatId = incomingMsg.getChat().getId();
        String text = incomingMsg.getText();
        String reference = Utils.getUniqueId();
        String userId = incomingMsg.getFrom().getId();
        String appId = incomingMsg.getAppId();
        Integer chatSettings = incomingMsg.getChatSettings();

        if (text == null) {
            return;
        }
        text = text.trim();
        if (text.length() == 0) {
            return;
        }

        String lower = text.toLowerCase();

        try {
            if (lower.startsWith("/help")) {
                sendHelp(chatId, reference, userId, chatSettings, appId);
                return;
            }

            if (lower.startsWith("/weather")) {
                String location = extractArgument(text);
                if (location == null || location.length() == 0) {
                    api.sendText(chatId, "Usage: /weather <city>\nExample: /weather London111\n\nTip: Provide a city name.", reference, null, userId, 0, false, chatSettings, null, null, null, appId);
                    return;
                }
                String reply = fetchCurrentWeather(location);
                api.sendText(chatId, reply, reference, null, userId, 0, false, chatSettings, null, null, null, appId);
                return;
            }

            if (lower.startsWith("/forecast")) {
                String location = extractArgument(text);
                if (location == null || location.length() == 0) {
                    api.sendText(chatId, "Usage: /forecast <city>\nExample: /forecast Paris", reference, null, userId, 0, false, chatSettings, null, null, null, appId);
                    return;
                }
                String reply = fetchForecast(location);
                api.sendText(chatId, reply, reference, null, userId, 0, false, chatSettings, null, null, null, appId);
                return;
            }

            if (looksLikeCommand(lower)) {
                api.sendText(chatId, "Unknown command. Type /help to see available commands.", reference, null, userId, 0, false, chatSettings, null, null, null, appId);
            }
        } catch (Exception ex) {
            try {
                api.sendText(chatId, "Sorry, something went wrong while processing your request.", reference, null, userId, 0, false, chatSettings, null, null, null, appId);
            } catch (Exception ignore) {
            }
        }
    }

    private static boolean looksLikeCommand(String textLower) {
        return textLower != null && textLower.startsWith("/");
    }

    private void sendHelp(String chatId, String reference, String userId, Integer chatSettings, String appId) throws Exception {
        String msg = "Weather Bot Commands:\n" +
                "/weather <city> - Current weather\n" +
                "/forecast <city> - Forecast\n" +
                "/help - Show this help\n\n" +
                "Examples:\n" +
                "/weather Berlin\n" +
                "/forecast New York";
        api.sendText(chatId, msg, reference, null, userId, 0, false, chatSettings, null, null, null, appId);
    }

    private static String extractArgument(String text) {
        if (text == null) return null;
        int space = text.indexOf(' ');
        if (space < 0) return "";
        return text.substring(space + 1).trim();
    }

    private static String fetchCurrentWeather(String location) {
        try {
            String endpoint = API_BASE_URL + "/current";
            String q = "q=" + urlEncode(location) + "&apikey=" + urlEncode(API_KEY);
            String body = httpGet(endpoint + "?" + q, 8000, 8000);
            return formatCurrentWeather(location, body);
        } catch (Exception e) {
            return "Could not fetch current weather for '" + safeText(location) + "'. Please try again later.";
        }
    }

    private static String fetchForecast(String location) {
        try {
            String endpoint = API_BASE_URL + "/forecast";
            String q = "q=" + urlEncode(location) + "&apikey=" + urlEncode(API_KEY);
            String body = httpGet(endpoint + "?" + q, 8000, 8000);
            return formatForecast(location, body);
        } catch (Exception e) {
            return "Could not fetch forecast for '" + safeText(location) + "'. Please try again later.";
        }
    }

    private static String formatCurrentWeather(String location, String responseBody) {
        if (responseBody == null) {
            return "No data returned for '" + safeText(location) + "'.";
        }
        JSONObject json = parseJsonObject(responseBody);
        if (json == null) {
            return "Weather for '" + safeText(location) + "':\n" + abbreviate(responseBody, 800);
        }

        String place = coalesce(asString(json.get("location")), asString(json.get("name")), location, "Unknown location");
        String condition = coalesce(asString(json.get("condition")), asString(json.get("weather")), asString(json.get("description")), "N/A");
        String temp = coalesce(asString(json.get("temp")), asString(json.get("temperature")), asString(json.get("temp_c")), asString(json.get("tempC")), asString(json.get("temperature_c")), "N/A");
        String humidity = coalesce(asString(json.get("humidity")), "N/A");
        String wind = coalesce(asString(json.get("wind")), asString(json.get("wind_kph")), asString(json.get("windSpeed")), "N/A");

        StringBuffer sb = new StringBuffer();
        sb.append("Current weather for ").append(place).append(":\n");
        sb.append("Condition: ").append(condition).append("\n");
        sb.append("Temperature: ").append(temp).append("\n");
        sb.append("Humidity: ").append(humidity).append("\n");
        sb.append("Wind: ").append(wind);
        return sb.toString();
    }

    private static String formatForecast(String location, String responseBody) {
        if (responseBody == null) {
            return "No data returned for '" + safeText(location) + "'.";
        }
        JSONObject json = parseJsonObject(responseBody);
        if (json == null) {
            return "Forecast for '" + safeText(location) + "':\n" + abbreviate(responseBody, 1000);
        }

        String place = coalesce(asString(json.get("location")), asString(json.get("name")), location, "Unknown location");
        Object daysObj = json.get("days");
        if (daysObj == null) {
            daysObj = json.get("forecast");
        }

        StringBuffer sb = new StringBuffer();
        sb.append("Forecast for ").append(place).append(":\n");

        if (daysObj instanceof JSONArray) {
            JSONArray days = (JSONArray) daysObj;
            int count = Math.min(days.size(), 5);
            for (int i = 0; i < count; i++) {
                Object item = days.get(i);
                if (item instanceof JSONObject) {
                    JSONObject d = (JSONObject) item;
                    String date = coalesce(asString(d.get("date")), asString(d.get("day")), asString(d.get("datetime")), "Day " + (i + 1));
                    String cond = coalesce(asString(d.get("condition")), asString(d.get("weather")), asString(d.get("description")), "N/A");
                    String min = coalesce(asString(d.get("min")), asString(d.get("minTemp")), asString(d.get("min_c")), "N/A");
                    String max = coalesce(asString(d.get("max")), asString(d.get("maxTemp")), asString(d.get("max_c")), "N/A");
                    sb.append(date).append(": ").append(cond);
                    if (!"N/A".equals(min) || !"N/A".equals(max)) {
                        sb.append(" (").append(min).append(" - ").append(max).append(")");
                    }
                    sb.append("\n");
                } else {
                    sb.append("Day ").append(i + 1).append(": ").append(String.valueOf(item)).append("\n");
                }
            }
            if (days.size() == 0) {
                sb.append("No forecast days available.");
            }
        } else {
            sb.append(abbreviate(responseBody, 1000));
        }

        return trimTrailingNewlines(sb.toString());
    }

    private static String httpGet(String urlStr, int connectTimeoutMs, int readTimeoutMs) throws Exception {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "nandbox-weather-bot/1.0");

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                is = conn.getInputStream();
            } else {
                is = conn.getErrorStream();
                String err = readAll(is);
                String msg = "HTTP " + code;
                if (err != null && err.trim().length() > 0) {
                    msg += ": " + abbreviate(err, 500);
                }
                throw new IOException(msg);
            }
            return readAll(is);
        } finally {
            try { if (is != null) is.close(); } catch (Exception e) { }
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception e) { }
            }
        }
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return null;
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuffer sb = new StringBuffer();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private static JSONObject parseJsonObject(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.length() == 0) return null;
        if (!(trimmed.startsWith("{") && trimmed.endsWith("}"))) {
            return null;
        }
        try {
            JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);
            Object obj = parser.parse(trimmed);
            if (obj instanceof JSONObject) {
                return (JSONObject) obj;
            }
        } catch (Exception e) {
        }
        return null;
    }

    private static String urlEncode(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String asString(Object o) {
        if (o == null) return null;
        if (o instanceof String) return (String) o;
        return String.valueOf(o);
    }

    private static String coalesce(String a, String b) {
        if (a != null && a.trim().length() > 0) return a.trim();
        if (b != null && b.trim().length() > 0) return b.trim();
        return null;
    }

    private static String coalesce(String a, String b, String c) {
        String r = coalesce(a, b);
        if (r != null && r.trim().length() > 0) return r.trim();
        if (c != null && c.trim().length() > 0) return c.trim();
        return null;
    }

    private static String coalesce(String a, String b, String c, String d) {
        String r = coalesce(a, b, c);
        if (r != null && r.trim().length() > 0) return r.trim();
        if (d != null && d.trim().length() > 0) return d.trim();
        return null;
    }

    private static String coalesce(String a, String b, String c, String d, String e) {
        String r = coalesce(a, b, c, d);
        if (r != null && r.trim().length() > 0) return r.trim();
        if (e != null && e.trim().length() > 0) return e.trim();
        return null;
    }

    private static String coalesce(String a, String b, String c, String d, String e, String f) {
        String r = coalesce(a, b, c, d, e);
        if (r != null && r.trim().length() > 0) return r.trim();
        if (f != null && f.trim().length() > 0) return f.trim();
        return null;
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, Math.max(0, maxLen - 3)) + "...";
    }

    private static String trimTrailingNewlines(String s) {
        if (s == null) return null;
        int end = s.length();
        while (end > 0) {
            char ch = s.charAt(end - 1);
            if (ch == '\n' || ch == '\r') {
                end--;
            } else {
                break;
            }
        }
        return s.substring(0, end);
    }

    private static String safeText(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() == 0) return "";
        if (t.length() > 200) t = t.substring(0, 200);
        return t;
    }

    @Override
    public void onReceive(JSONObject obj) {
        if (obj == null) return;
        try {
            if (obj.get("text") != null || obj.get("message") != null || obj.get("chat") != null || obj.get("from") != null) {
                return;
            }
        } catch (Exception e) {
            return;
        }
    }

    @Override
    public void onClose() {}

    @Override
    public void onError() {}

    @Override
    public void onChatMenuCallBack(ChatMenuCallback chatMenuCallback) {}

    @Override
    public void onInlineMessageCallback(InlineMessageCallback inlineMsgCallback) {}

    @Override
    public void onMessagAckCallback(MessageAck msgAck) {}

    @Override
    public void onUserJoinedBot(User user) {}

    @Override
    public void onChatMember(ChatMember chatMember) {}

    @Override
    public void onChatAdministrators(ChatAdministrators chatAdministrators) {}

    @Override
    public void userStartedBot(User user) {}

    @Override
    public void onMyProfile(User user) {}

    @Override
    public void onProductDetail(ProductItemResponse productItem) {}

    @Override
    public void onCollectionProduct(GetProductCollectionResponse collectionProduct) {}

    @Override
    public void listCollectionItemResponse(ListCollectionItemResponse collections) {}

    @Override
    public void onUserDetails(User user, String appId) {}

    @Override
    public void userStoppedBot(User user) {}

    @Override
    public void userLeftBot(User user) {}

    @Override
    public void permanentUrl(PermanentUrl permenantUrl) {}

    @Override
    public void onChatDetails(Chat chat, String appId) {}

    @Override
    public void onInlineSearh(InlineSearch inlineSearch) {}

    @Override
    public void onBlackListPattern(Pattern pattern) {}

    @Override
    public void onWhiteListPattern(Pattern pattern) {}

    @Override
    public void onBlackList(BlackList blackList) {}

    @Override
    public void onDeleteBlackList(List_ak blackList) {}

    @Override
    public void onWhiteList(WhiteList whiteList) {}

    @Override
    public void onDeleteWhiteList(List_ak whiteList) {}

    @Override
    public void onScheduleMessage(IncomingMessage incomingScheduleMsg) {}

    @Override
    public void onWorkflowDetails(WorkflowDetails workflowDetails) {}

    @Override
    public void onCreateChat(Chat chat) {}

    @Override
    public void onMenuCallBack(MenuCallback menuCallback) {}
}
