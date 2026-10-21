package com.shashwat.ytify;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HttpServer extends NanoHTTPD {
    private static final String TAG = "YtifyHttpServer";
    public static final int PORT = 9999;
    private final InnerTube innerTube;
    private boolean isRunning = false;
    private static boolean isNewPipeInitialized = false;

    public HttpServer() {
        super("0.0.0.0", PORT);
        initNewPipe();
        this.innerTube = new InnerTube();
    }

    private static synchronized void initNewPipe() {
        if (!isNewPipeInitialized) {
            try {
                NewPipe.init(NewPipeDownloader.getInstance(), new Localization("US", "en"));
                isNewPipeInitialized = true;
                Log.i(TAG, "NewPipeExtractor initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize NewPipeExtractor: " + e.getMessage(), e);
            }
        }
    }

    public void start() {
        if (isRunning) {
            Log.i(TAG, "Server is already running");
            return;
        }

        try {
            super.start(SOCKET_READ_TIMEOUT, false);
            isRunning = true;
            Log.i(TAG, "Server started on port " + PORT + " and bound to all interfaces");
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
        corsHeaders.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        corsHeaders.put("Access-Control-Allow-Headers", "Content-Type, X-Requested-With");

        if (session.getMethod() == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "");
        }

        String uri = session.getUri();
        Log.i(TAG, "Request received: " + uri);

        if ("/".equals(uri)) {
            JSONObject statusJson = new JSONObject();
            try {
                statusJson.put("status", "ok");
                statusJson.put("extractor", "NewPipeExtractor");
                statusJson.put("version", "v0.26.5");
            } catch (JSONException ignored) {}

            Response response = newFixedLengthResponse(Response.Status.OK, "application/json", statusJson.toString());
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
            JSONObject extracted = extractWithNewPipe(videoId);
            Response response = newFixedLengthResponse(Response.Status.OK, "application/json", extracted.toString());
            addCorsHeaders(response, corsHeaders);
            return response;
        } catch (Exception e) {
            Log.w(TAG, "NewPipeExtractor failed for " + videoId + ": " + e.getMessage() + ", falling back to InnerTube");
            try {
                JSONObject playerInfo = innerTube.player(videoId);
                Response response = newFixedLengthResponse(Response.Status.OK, "application/json", playerInfo.toString());
                addCorsHeaders(response, corsHeaders);
                return response;
            } catch (Exception innerException) {
                Log.e(TAG, "InnerTube fallback also failed for " + videoId + ": " + innerException.getMessage(), innerException);
                return createErrorResponse(Response.Status.INTERNAL_ERROR, "Extraction error: " + e.getMessage(), corsHeaders);
            }
        }
    }

    private JSONObject extractWithNewPipe(String videoId) throws Exception {
        String youtubeUrl = "https://www.youtube.com/watch?v=" + videoId;
        StreamInfo streamInfo = StreamInfo.getInfo(ServiceList.YouTube, youtubeUrl);

        JSONObject result = new JSONObject();
        result.put("id", videoId);
        result.put("title", streamInfo.getName());
        result.put("author", streamInfo.getUploaderName());
        result.put("authorId", streamInfo.getUploaderUrl());
        result.put("lengthSeconds", streamInfo.getDuration());

        JSONArray adaptiveFormats = new JSONArray();

        if (streamInfo.getAudioStreams() != null) {
            for (AudioStream stream : streamInfo.getAudioStreams()) {
                JSONObject format = new JSONObject();
                format.put("url", stream.getUrl());
                String mime = stream.getFormat() != null ? stream.getFormat().getMimeType() : "audio/webm";
                String codec = stream.getCodec() != null ? stream.getCodec() : "opus";
                String type = mime + "; codecs=\"" + codec + "\"";
                format.put("type", type);
                format.put("mimeType", type);
                format.put("itag", stream.getItag());
                format.put("bitrate", stream.getAverageBitrate() > 0 ? stream.getAverageBitrate() : 128000);
                format.put("quality", stream.getQuality());
                format.put("audioQuality", stream.getQuality());
                adaptiveFormats.put(format);
            }
        }

        if (streamInfo.getVideoOnlyStreams() != null) {
            for (VideoStream stream : streamInfo.getVideoOnlyStreams()) {
                JSONObject format = new JSONObject();
                format.put("url", stream.getUrl());
                String mime = stream.getFormat() != null ? stream.getFormat().getMimeType() : "video/mp4";
                String codec = stream.getCodec() != null ? stream.getCodec() : "avc1";
                String type = mime + "; codecs=\"" + codec + "\"";
                format.put("type", type);
                format.put("mimeType", type);
                format.put("itag", stream.getItag());
                format.put("bitrate", stream.getBitrate());
                format.put("quality", stream.getResolution());
                adaptiveFormats.put(format);
            }
        }

        result.put("adaptiveFormats", adaptiveFormats);

        JSONObject streamingData = new JSONObject();
        streamingData.put("adaptiveFormats", adaptiveFormats);
        result.put("streamingData", streamingData);

        return result;
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