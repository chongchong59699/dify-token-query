package com.company.difytokenquery.dify;

import com.company.difytokenquery.model.DifyModelOption;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HTTP client for Dify Console API (/console/api/...). */
public class DifyConsoleClient {
    public static class ModelParameterSupport {
        private final boolean loaded;
        private final boolean supportsEnableThinking;
        private final boolean supportsReasoningEffort;
        private final List<String> reasoningEffortOptions;

        public ModelParameterSupport(boolean loaded, boolean supportsEnableThinking,
                                     boolean supportsReasoningEffort, List<String> reasoningEffortOptions) {
            this.loaded = loaded;
            this.supportsEnableThinking = supportsEnableThinking;
            this.supportsReasoningEffort = supportsReasoningEffort;
            this.reasoningEffortOptions = reasoningEffortOptions == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(reasoningEffortOptions));
        }

        public static ModelParameterSupport unknown() {
            return new ModelParameterSupport(false, false, false, Collections.<String>emptyList());
        }

        public boolean isLoaded() { return loaded; }
        public boolean isSupportsEnableThinking() { return supportsEnableThinking; }
        public boolean isSupportsReasoningEffort() { return supportsReasoningEffort; }
        public List<String> getReasoningEffortOptions() { return reasoningEffortOptions; }
    }

    public interface HttpTransport {
        HttpResponse execute(String method, String url, Map<String, String> headers, String body) throws IOException;
    }

    public static class HttpResponse {
        private final int statusCode;
        private final String body;

        public HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }

        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }
    }

    public static class DifyApiException extends IOException {
        private final int statusCode;
        private final String errorCode;

        public DifyApiException(int statusCode, String errorCode, String message) {
            super(message);
            this.statusCode = statusCode;
            this.errorCode = errorCode == null ? "" : errorCode;
        }

        public int getStatusCode() { return statusCode; }
        public String getErrorCode() { return errorCode; }
    }

    private final String baseUrl;
    private final HttpTransport transport;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken = "";
    private String refreshToken = "";
    private String loggedInEmail = "";
    private String cachedPassword = "";

    public DifyConsoleClient(String difyFrontendUrl) {
        this(difyFrontendUrl, new UrlConnectionTransport());
    }

    public DifyConsoleClient(String difyFrontendUrl, HttpTransport transport) {
        this.baseUrl = trimTrailingSlashes(difyFrontendUrl == null ? "" : difyFrontendUrl.trim());
        this.transport = transport == null ? new UrlConnectionTransport() : transport;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isLoggedIn() {
        return !isBlank(accessToken);
    }

    public String getLoggedInEmail() {
        return loggedInEmail;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setTokens(String email, String accessToken, String refreshToken) {
        this.loggedInEmail = email == null ? "" : email.trim();
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.refreshToken = refreshToken == null ? "" : refreshToken.trim();
    }

    public void login(String email, String password) throws IOException {
        if (isBlank(baseUrl)) {
            throw new IOException("请先在Dify配置中填写 Dify 前端地址");
        }
        if (isBlank(email) || isBlank(password)) {
            throw new IOException("请填写 Dify 登录邮箱和密码");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("email", email.trim());
        body.put("password", password);
        body.put("language", "zh-Hans");
        body.put("remember_me", true);

        HttpResponse response = executeRaw("POST", "/console/api/login", false, body.toString());
        JsonNode root = parseJson(response.getBody());
        if (response.getStatusCode() >= 400) {
            throw buildApiException(response.getStatusCode(), root, response.getBody());
        }

        String result = textOf(root, "result");
        if (!"success".equalsIgnoreCase(result) && !root.has("data")) {
            throw buildApiException(response.getStatusCode(), root, response.getBody());
        }
        JsonNode data = root.has("data") ? root.get("data") : root;
        String token = textOf(data, "access_token");
        if (isBlank(token)) {
            throw new IOException("登录成功但未返回 access_token：" + response.getBody());
        }
        this.accessToken = token;
        this.refreshToken = textOf(data, "refresh_token");
        this.loggedInEmail = email.trim();
        this.cachedPassword = password;
    }

    public List<DifyModelOption> listLlmModels() throws IOException {
        JsonNode root = getJson("/console/api/workspaces/current/models/model-types/llm");
        JsonNode data = root.has("data") ? root.get("data") : root;
        if (data == null || !data.isArray()) {
            return Collections.emptyList();
        }

        List<DifyModelOption> result = new ArrayList<DifyModelOption>();
        for (JsonNode providerNode : data) {
            String provider = textOf(providerNode, "provider");
            String providerLabel = pickLocalizedText(providerNode.get("label"), provider);
            JsonNode modelsNode = providerNode.get("models");
            if (modelsNode == null || !modelsNode.isArray()) {
                continue;
            }
            for (JsonNode modelNode : modelsNode) {
                String modelName = textOf(modelNode, "model");
                if (isBlank(provider) || isBlank(modelName)) {
                    continue;
                }
                String status = textOf(modelNode, "status");
                if (!isUsableModelStatus(status)) {
                    continue;
                }
                String modelLabel = pickLocalizedText(modelNode.get("label"), modelName);
                String modelType = textOf(modelNode, "model_type");
                String modelMode = textOf(modelNode.get("model_properties"), "mode");
                String fetchFrom = textOf(modelNode, "fetch_from");
                result.add(new DifyModelOption(
                        provider, providerLabel, modelName, modelLabel, modelMode, modelType, status, fetchFrom
                ));
            }
        }

        Collections.sort(result, new Comparator<DifyModelOption>() {
            @Override
            public int compare(DifyModelOption a, DifyModelOption b) {
                int p = a.getProviderLabel().compareToIgnoreCase(b.getProviderLabel());
                if (p != 0) return p;
                return a.getModelName().compareToIgnoreCase(b.getModelName());
            }
        });
        return result;
    }

    public ModelParameterSupport getModelParameterSupport(String provider, String modelName) throws IOException {
        if (isBlank(provider) || isBlank(modelName)) {
            return ModelParameterSupport.unknown();
        }
        String cleanProvider = trimSlashes(provider.trim());
        String path = "/console/api/workspaces/current/model-providers/" + cleanProvider
                + "/models/parameter-rules?model=" + URLEncoder.encode(modelName.trim(), "UTF-8");
        JsonNode root = getJson(path);
        JsonNode data = root.has("data") ? root.get("data") : root;
        if (data == null || !data.isArray()) {
            return ModelParameterSupport.unknown();
        }
        boolean supportsEnableThinking = false;
        boolean supportsReasoningEffort = false;
        List<String> effortOptions = new ArrayList<String>();
        for (JsonNode rule : data) {
            String name = textOf(rule, "name");
            if ("enable_thinking".equals(name)) {
                supportsEnableThinking = true;
            } else if ("reasoning_effort".equals(name)) {
                supportsReasoningEffort = true;
                JsonNode opts = rule.get("options");
                if (opts != null && opts.isArray()) {
                    for (JsonNode opt : opts) {
                        String val = opt.asText("");
                        if (!isBlank(val)) {
                            effortOptions.add(val);
                        }
                    }
                }
            }
        }
        return new ModelParameterSupport(true, supportsEnableThinking, supportsReasoningEffort, effortOptions);
    }

    public JsonNode getAppDetail(String appId) throws IOException {
        return getJson("/console/api/apps/" + encodePath(appId));
    }

    public JsonNode updateAppModelConfig(String appId, JsonNode modelConfigBody) throws IOException {
        return postJson("/console/api/apps/" + encodePath(appId) + "/model-config", modelConfigBody);
    }

    public JsonNode getWorkflowDraft(String appId) throws IOException {
        return getJson("/console/api/apps/" + encodePath(appId) + "/workflows/draft");
    }

    public JsonNode updateWorkflowDraft(String appId, JsonNode draftPayload) throws IOException {
        return postJson("/console/api/apps/" + encodePath(appId) + "/workflows/draft", draftPayload);
    }

    public JsonNode publishWorkflow(String appId, String markedName, String markedComment) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("marked_name", markedName == null ? "" : markedName);
        body.put("marked_comment", markedComment == null ? "" : markedComment);
        return postJson("/console/api/apps/" + encodePath(appId) + "/workflows/publish", body);
    }

    public String exportAppDsl(String appId, String workflowId) throws IOException {
        StringBuilder path = new StringBuilder();
        path.append("/console/api/apps/").append(encodePath(appId)).append("/export?include_secret=false");
        if (!isBlank(workflowId)) {
            path.append("&workflow_id=").append(URLEncoder.encode(workflowId.trim(), "UTF-8"));
        }
        HttpResponse response = executeWithAutoRefresh("GET", path.toString(), null);
        if (response.getStatusCode() >= 400) {
            JsonNode err = tryParseJson(response.getBody());
            throw buildApiException(response.getStatusCode(), err, response.getBody());
        }
        JsonNode root = tryParseJson(response.getBody());
        if (root != null && root.has("data") && !root.get("data").isNull()) {
            return root.get("data").asText("");
        }
        return response.getBody();
    }

    private JsonNode getJson(String path) throws IOException {
        HttpResponse response = executeWithAutoRefresh("GET", path, null);
        JsonNode root = tryParseJson(response.getBody());
        if (response.getStatusCode() >= 400) {
            throw buildApiException(response.getStatusCode(), root, response.getBody());
        }
        if (root == null) {
            throw new IOException("接口未返回有效 JSON：" + path);
        }
        return root;
    }

    private JsonNode postJson(String path, JsonNode body) throws IOException {
        String payload = body == null ? "{}" : objectMapper.writeValueAsString(body);
        HttpResponse response = executeWithAutoRefresh("POST", path, payload);
        JsonNode root = tryParseJson(response.getBody());
        if (response.getStatusCode() >= 400) {
            throw buildApiException(response.getStatusCode(), root, response.getBody());
        }
        return root == null ? objectMapper.createObjectNode() : root;
    }

    private HttpResponse executeWithAutoRefresh(String method, String path, String body) throws IOException {
        if (isBlank(baseUrl)) {
            throw new IOException("请先配置 Dify 前端地址");
        }
        if (isBlank(accessToken)) {
            throw new DifyApiException(401, "unauthorized", "尚未登录 Dify 控制台");
        }

        HttpResponse response = executeRaw(method, path, true, body);
        if (response.getStatusCode() == 401 && tryRefreshOrRelogin()) {
            response = executeRaw(method, path, true, body);
        }
        return response;
    }

    private boolean tryRefreshOrRelogin() {
        if (!isBlank(refreshToken)) {
            try {
                ObjectNode body = objectMapper.createObjectNode();
                body.put("refresh_token", refreshToken);
                HttpResponse resp = executeRaw("POST", "/console/api/refresh-token", false, body.toString());
                if (resp.getStatusCode() < 400) {
                    JsonNode root = tryParseJson(resp.getBody());
                    JsonNode data = root != null && root.has("data") ? root.get("data") : root;
                    String nextToken = textOf(data, "access_token");
                    if (!isBlank(nextToken)) {
                        this.accessToken = nextToken;
                        String nextRefresh = textOf(data, "refresh_token");
                        if (!isBlank(nextRefresh)) {
                            this.refreshToken = nextRefresh;
                        }
                        return true;
                    }
                }
            } catch (IOException ignored) {
                // Fall back to password re-login below.
            }
        }
        if (!isBlank(loggedInEmail) && !isBlank(cachedPassword)) {
            try {
                login(loggedInEmail, cachedPassword);
                return true;
            } catch (IOException ignored) {
                return false;
            }
        }
        return false;
    }

    private HttpResponse executeRaw(String method, String path, boolean withAuth, String body) throws IOException {
        String url = baseUrl + (path.startsWith("/") ? path : "/" + path);
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Accept", "application/json");
        if (body != null) {
            headers.put("Content-Type", "application/json; charset=UTF-8");
        }
        if (withAuth && !isBlank(accessToken)) {
            headers.put("Authorization", "Bearer " + accessToken);
        }
        return transport.execute(method, url, headers, body);
    }

    private static boolean isUsableModelStatus(String status) {
        if (isBlank(status)) {
            return true;
        }
        String normalized = status.trim().toLowerCase();
        return "active".equals(normalized) || "available".equals(normalized);
    }

    private static String pickLocalizedText(JsonNode node, String fallback) {
        if (node == null || node.isNull()) {
            return fallback == null ? "" : fallback;
        }
        if (node.isTextual()) {
            String text = node.asText("");
            return text.isEmpty() ? (fallback == null ? "" : fallback) : text;
        }
        if (node.isObject()) {
            String zh = textOf(node, "zh_Hans");
            if (!isBlank(zh)) return zh;
            String en = textOf(node, "en_US");
            if (!isBlank(en)) return en;
            if (node.fields().hasNext()) {
                String first = node.fields().next().getValue().asText("");
                if (!isBlank(first)) return first;
            }
        }
        return fallback == null ? "" : fallback;
    }

    private JsonNode parseJson(String text) throws IOException {
        JsonNode node = tryParseJson(text);
        if (node == null) {
            throw new IOException("响应不是有效 JSON：" + abbreviate(text, 200));
        }
        return node;
    }

    private JsonNode tryParseJson(String text) {
        if (isBlank(text)) {
            return null;
        }
        try {
            return objectMapper.readTree(text);
        } catch (IOException e) {
            return null;
        }
    }

    private DifyApiException buildApiException(int statusCode, JsonNode root, String rawBody) {
        String code = "";
        String msg = "";
        if (root != null && root.isObject()) {
            code = textOf(root, "code");
            msg = textOf(root, "message");
            if (isBlank(msg)) {
                msg = textOf(root, "error");
            }
        }
        if (isBlank(msg)) {
            msg = isBlank(rawBody) ? ("HTTP " + statusCode) : abbreviate(rawBody, 240);
        }
        return new DifyApiException(statusCode, code, "HTTP " + statusCode + (isBlank(code) ? "" : " [" + code + "]") + ": " + msg);
    }

    private static String encodePath(String segment) throws IOException {
        return URLEncoder.encode(segment == null ? "" : segment.trim(), "UTF-8").replace("+", "%20");
    }

    private static String textOf(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }

    private static String trimSlashes(String value) {
        String result = trimTrailingSlashes(value);
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private static String trimTrailingSlashes(String value) {
        String result = value == null ? "" : value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String abbreviate(String value, int maxLen) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen) + "...";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class UrlConnectionTransport implements HttpTransport {
        @Override
        public HttpResponse execute(String method, String urlString, Map<String, String> headers, String body) throws IOException {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(false);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            if (body != null) {
                conn.setDoOutput(true);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }

            int status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String responseBody = readStream(stream);
            return new HttpResponse(status, responseBody);
        }

        private String readStream(InputStream stream) throws IOException {
            if (stream == null) {
                return "";
            }
            try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }
}