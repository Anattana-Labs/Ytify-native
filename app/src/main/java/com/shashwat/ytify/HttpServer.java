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
            if (e.getMessage() != null && e.getMessage().contains("EADDRINUSE")) {
                Log.w(TAG, "Server port " + PORT + " is already in use; assuming existing server instance is active.");
                isRunning = true;
            } else {
                Log.e(TAG, "Error starting server: " + e.getMessage(), e);
            }
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
        corsHeaders.put("Access-Control-Allow-Private-Network", "true");
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
            String targetUrl = null;
            String qs = session.getQueryParameterString();
            if (qs != null && qs.contains("url=")) {
                int idx = qs.indexOf("url=");
                targetUrl = URLDecoder.decode(qs.substring(idx + 4), "UTF-8");
            }
            if (targetUrl == null || targetUrl.trim().isEmpty()) {
                targetUrl = session.getParms().get("url");
            }
            if (targetUrl == null || targetUrl.trim().isEmpty()) {
                return createErrorResponse(Response.Status.BAD_REQUEST, "Missing url parameter", corsHeaders);
            }

            long totalLen = -1;
            if (targetUrl.contains("clen=")) {
                try {
                    int cIdx = targetUrl.indexOf("clen=") + 5;
                    int endIdx = targetUrl.indexOf("&", cIdx);
                    String clenStr = endIdx > 0 ? targetUrl.substring(cIdx, endIdx) : targetUrl.substring(cIdx);
                    totalLen = Long.parseLong(clenStr);
                } catch (Exception ignored) {}
            }

            String userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)";
            Map<String, String> reqHeaders = session.getHeaders();
            for (Map.Entry<String, String> e : reqHeaders.entrySet()) {
                if ("user-agent".equalsIgnoreCase(e.getKey()) && e.getValue() != null && !e.getValue().trim().isEmpty()) {
                    userAgent = e.getValue();
                    break;
                }
            }

            if (totalLen <= 0) {
                try {
                    HttpURLConnection probe = (HttpURLConnection) new URL(targetUrl).openConnection();
                    probe.setRequestMethod("GET");
                    probe.setRequestProperty("User-Agent", userAgent);
                    probe.setRequestProperty("Range", "bytes=0-0");
                    probe.setConnectTimeout(6000);
                    probe.setReadTimeout(6000);
                    probe.connect();
                    String cr = probe.getHeaderField("Content-Range");
                    if (cr != null && cr.contains("/")) {
                        String totalStr = cr.substring(cr.lastIndexOf("/") + 1).trim();
                        if (!"*".equals(totalStr)) {
                            totalLen = Long.parseLong(totalStr);
                        }
                    }
                    probe.disconnect();
                } catch (Exception ignored) {}
            }

            String rangeHeader = null;
            for (Map.Entry<String, String> e : reqHeaders.entrySet()) {
                if ("range".equalsIgnoreCase(e.getKey()) && e.getValue() != null && !e.getValue().trim().isEmpty()) {
                    rangeHeader = e.getValue().trim();
                    break;
                }
            }

            long start = 0;
            long end = (totalLen > 0) ? (totalLen - 1) : -1;
            boolean isRangeRequest = false;

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                isRangeRequest = true;
                String r = rangeHeader.substring(6).trim();
                int dash = r.indexOf("-");
                if (dash != -1) {
                    String sStr = r.substring(0, dash).trim();
                    String eStr = r.substring(dash + 1).trim();
                    if (!sStr.isEmpty()) {
                        try { start = Long.parseLong(sStr); } catch (Exception ignored) {}
                    }
                    if (!eStr.isEmpty()) {
                        try { end = Long.parseLong(eStr); } catch (Exception ignored) {}
                    }
                }
            }

            if (totalLen > 0) {
                if (end < 0 || end >= totalLen) {
                    end = totalLen - 1;
                }
                if (start > end || start >= totalLen) {
                    return createErrorResponse(Response.Status.RANGE_NOT_SATISFIABLE, "Requested Range Not Satisfiable", corsHeaders);
                }
            } else if (end < 0) {
                end = start + (256 * 1024) - 1;
            }

            long contentLength = (end - start + 1);
            String contentType = targetUrl.contains("mime=video") ? "video/mp4" : "audio/webm";

            InputStream is = new MultiChunkInputStream(targetUrl, userAgent, start, end);
            Response.IStatus status = isRangeRequest ? Response.Status.PARTIAL_CONTENT : Response.Status.OK;
            Response response = newFixedLengthResponse(status, contentType, is, contentLength);
            addCorsHeaders(response, corsHeaders);
            response.addHeader("Accept-Ranges", "bytes");
            if (isRangeRequest) {
                String totalStr = (totalLen > 0) ? String.valueOf(totalLen) : "*";
                response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + totalStr);
            }

            return response;
        } catch (Exception e) {
            Log.e(TAG, "Proxy error: " + e.getMessage(), e);
            return createErrorResponse(Response.Status.INTERNAL_ERROR, "Proxy error: " + e.getMessage(), corsHeaders);
        }
    }

    private static class MultiChunkInputStream extends InputStream {
        private final String targetUrl;
        private final String userAgent;
        private final long endOffset;
        private long currentOffset;
        private InputStream currentStream;
        private HttpURLConnection currentConn;
        private static final int CHUNK_SIZE = 262144; // 256 KB safe chunk size for Google Video CDN

        public MultiChunkInputStream(String targetUrl, String userAgent, long startOffset, long endOffset) {
            this.targetUrl = targetUrl;
            this.userAgent = userAgent;
            this.currentOffset = startOffset;
            this.endOffset = endOffset;
        }

        private boolean openNextChunk() throws IOException {
            if (currentOffset > endOffset) {
                return false;
            }
            closeCurrent();
            long chunkEnd = Math.min(currentOffset + CHUNK_SIZE - 1, endOffset);
            URL url = new URL(targetUrl);
            currentConn = (HttpURLConnection) url.openConnection();
            currentConn.setConnectTimeout(8000);
            currentConn.setReadTimeout(12000);
            currentConn.setInstanceFollowRedirects(true);
            currentConn.setRequestProperty("User-Agent", userAgent);
            currentConn.setRequestProperty("Range", "bytes=" + currentOffset + "-" + chunkEnd);
            currentConn.connect();

            int code = currentConn.getResponseCode();
            if (code != 200 && code != 206) {
                closeCurrent();
                throw new IOException("Google Video CDN returned HTTP " + code + " for chunk " + currentOffset + "-" + chunkEnd);
            }
            currentStream = currentConn.getInputStream();
            return true;
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int r = read(b, 0, 1);
            return r == -1 ? -1 : (b[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (currentOffset > endOffset) {
                return -1;
            }
            if (currentStream == null) {
                if (!openNextChunk()) {
                    return -1;
                }
            }
            int r = currentStream.read(b, off, len);
            if (r == -1) {
                closeCurrent();
                if (!openNextChunk()) {
                    return -1;
                }
                r = currentStream.read(b, off, len);
            }
            if (r > 0) {
                currentOffset += r;
            }
            return r;
        }

        private void closeCurrent() {
            if (currentStream != null) {
                try { currentStream.close(); } catch (Exception ignored) {}
                currentStream = null;
            }
            if (currentConn != null) {
                try { currentConn.disconnect(); } catch (Exception ignored) {}
                currentConn = null;
            }
        }

        @Override
        public void close() {
            closeCurrent();
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
            MainActivity.notifyLocalServerUsed();
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