package com.shashwat.ytify;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
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

        String videoId = extractVideoId(uri);
        if (videoId != null && !videoId.isEmpty()) {
            return handleStreamsRequest(videoId, corsHeaders);
        }

        return createErrorResponse(Response.Status.NOT_FOUND, "Not Found", corsHeaders);
    }

    private String extractVideoId(String uri) {
        if (uri == null) return null;
        if (uri.contains("?")) {
            uri = uri.substring(0, uri.indexOf("?"));
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