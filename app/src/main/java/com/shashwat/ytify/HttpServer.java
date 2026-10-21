package com.shashwat.ytify;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class HttpServer extends NanoHTTPD {
    private static final String TAG = "YtifyHttpServer";
    public static final int PORT = 9999;
    private boolean isRunning = false;

    public HttpServer() {
        super("0.0.0.0", PORT);
    }

    public void start() {
        if (isRunning) {
            Log.i(TAG, "Server is already running");
            return;
        }

        try {
            super.start(SOCKET_READ_TIMEOUT, false);
            isRunning = true;
            Log.i(TAG, "Server started on port " + PORT + " using PlaybackResolver");
        } catch (IOException e) {
            Log.e(TAG, "Error starting server: " + e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        super.stop();
        isRunning = false;
        Log.i(TAG, "Server stopped");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getPort() {
        return PORT;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Map<String, String> corsHeaders = new HashMap<>();
        corsHeaders.put("Access-Control-Allow-Origin", "*");
        corsHeaders.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS, HEAD");
        corsHeaders.put("Access-Control-Allow-Headers", "*");
        corsHeaders.put("Access-Control-Max-Age", "86400");

        if (session.getMethod() == Method.OPTIONS) {
            Response response = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "");
            addCorsHeaders(response, corsHeaders);
            return response;
        }

        String uri = session.getUri();
        if (uri != null && uri.contains("?")) {
            uri = uri.substring(0, uri.indexOf("?"));
        }
        Log.i(TAG, "Request received: " + uri);

        if ("/".equals(uri)) {
            JSONObject statusJson = new JSONObject();
            try {
                statusJson.put("status", "ok");
                statusJson.put("extractor", "PlaybackResolver");
                statusJson.put("version", "v1.0");
            } catch (JSONException ignored) {}

            Response response = newFixedLengthResponse(Response.Status.OK, "application/json", statusJson.toString());
            addCorsHeaders(response, corsHeaders);
            return response;
        }

        if ("/s/".equals(uri) || "/s".equals(uri)) {
            JSONObject sessionJson = new JSONObject();
            try {
                sessionJson.put("sessionId", "ytify_local_session");
            } catch (JSONException ignored) {}
            Response response = newFixedLengthResponse(Response.Status.OK, "application/json", sessionJson.toString());
            addCorsHeaders(response, corsHeaders);
            return response;
        }

        if (uri != null && uri.startsWith("/proxy")) {
            return handleProxyRequest(session, corsHeaders);
        }

        String videoId = extractVideoId(uri);
        if (videoId != null && !videoId.isEmpty()) {
            return handleStreamsRequest(videoId, corsHeaders);
        }

        return createErrorResponse(Response.Status.NOT_FOUND, "Not Found", corsHeaders);
    }

    private Response handleProxyRequest(IHTTPSession session, Map<String, String> corsHeaders) {
        try {
            Map<String, String> parms = session.getParms();
            String targetUrl = parms.get("url");
            if (targetUrl == null || targetUrl.trim().isEmpty()) {
                String qs = session.getQueryParameterString();
                if (qs != null && qs.contains("url=")) {
                    int idx = qs.indexOf("url=");
                    targetUrl = URLDecoder.decode(qs.substring(idx + 4), "UTF-8");
                }
            }
            if (targetUrl == null || targetUrl.trim().isEmpty()) {
                return createErrorResponse(Response.Status.BAD_REQUEST, "Missing url parameter", corsHeaders);
            }

            HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
            conn.setRequestMethod(session.getMethod().name());
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15";
            Map<String, String> reqHeaders = session.getHeaders();
            for (Map.Entry<String, String> e : reqHeaders.entrySet()) {
                if ("user-agent".equalsIgnoreCase(e.getKey()) && e.getValue() != null && !e.getValue().trim().isEmpty()) {
                    userAgent = e.getValue();
                    break;
                }
            }
            conn.setRequestProperty("User-Agent", userAgent);

            String range = null;
            for (Map.Entry<String, String> e : reqHeaders.entrySet()) {
                if ("range".equalsIgnoreCase(e.getKey())) {
                    range = e.getValue();
                    break;
                }
            }
            if (range != null && !range.trim().isEmpty()) {
                conn.setRequestProperty("Range", range.trim());
            }

            conn.connect();

            int code = conn.getResponseCode();
            Response.IStatus status;
            if (code == 206) {
                status = Response.Status.PARTIAL_CONTENT;
            } else if (code >= 200 && code < 300) {
                status = Response.Status.OK;
            } else {
                status = Response.Status.lookup(code);
                if (status == null) status = Response.Status.INTERNAL_ERROR;
            }

            String contentType = conn.getContentType();
            if (contentType == null || contentType.trim().isEmpty()) {
                contentType = targetUrl.contains("mime=video") ? "video/mp4" : "audio/mp4";
            }
            long contentLength = conn.getContentLengthLong();
            InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();

            Response response = newFixedLengthResponse(status, contentType, is, contentLength);
            addCorsHeaders(response, corsHeaders);
            response.addHeader("Accept-Ranges", "bytes");

            String contentRange = conn.getHeaderField("Content-Range");
            if (contentRange != null && !contentRange.trim().isEmpty()) {
                response.addHeader("Content-Range", contentRange.trim());
            }

            return response;
        } catch (Exception e) {
            Log.e(TAG, "Proxy error: " + e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_ERROR, "Proxy error: " + e.getMessage(), corsHeaders);
        }
    }

    private String extractVideoId(String uri) {
        if (uri == null) return null;
        if (uri.contains("?")) {
            uri = uri.substring(0, uri.indexOf("?"));
        }
        if (uri.startsWith("/proxy")) {
            return null;
        }
        if (uri.startsWith("/streams/")) {
            String[] parts = uri.split("/");
            return parts.length >= 3 ? parts[2] : null;
        } else if (uri.startsWith("/api/v1/videos/")) {
            String[] parts = uri.split("/");
            return parts.length >= 5 ? parts[4] : null;
        } else if (uri.startsWith("/s/")) {
            String[] parts = uri.split("/");
            return parts.length >= 3 ? parts[2] : null;
        }
        return null;
    }

    private Response handleStreamsRequest(String videoId, Map<String, String> corsHeaders) {
        try {
            JSONObject extracted = PlaybackResolver.resolveStreams(videoId);
            Response response = newFixedLengthResponse(Response.Status.OK, "application/json", extracted.toString());
            addCorsHeaders(response, corsHeaders);
            return response;
        } catch (Exception e) {
            Log.e(TAG, "PlaybackResolver extraction error for " + videoId + ": " + e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_ERROR, "Extraction error: " + e.getMessage(), corsHeaders);
        }
    }

    private void addCorsHeaders(Response response, Map<String, String> headers) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            response.addHeader(entry.getKey(), entry.getValue());
        }
    }

    private Response createErrorResponse(Response.Status status, String errorMessage, Map<String, String> headers) {
        JSONObject errorJson = new JSONObject();
        try {
            errorJson.put("error", errorMessage);
        } catch (JSONException ignored) {}

        Response response = newFixedLengthResponse(status, "application/json", errorJson.toString());
        addCorsHeaders(response, headers);
        return response;
    }
}