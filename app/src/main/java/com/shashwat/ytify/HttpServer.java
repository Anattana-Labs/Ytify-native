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
    private static final int PORT = 9999;
    private InnerTube innerTube;
    private boolean isRunning = false;

    public HttpServer() {
        super("0.0.0.0", PORT);
        innerTube = new InnerTube();
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

    @Override
    public Response serve(IHTTPSession session) {
        // Add CORS headers
        Map<String, String> corsHeaders = new HashMap<>();
        corsHeaders.put("Access-Control-Allow-Origin", "*");
        corsHeaders.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        corsHeaders.put("Access-Control-Allow-Headers", "Content-Type, X-Requested-With");
        
        // Handle OPTIONS preflight request
        if (session.getMethod() == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "");
        }
        
        String uri = session.getUri();
        Log.i(TAG, "Request received: " + uri);
        
        // Root endpoint
        if ("/".equals(uri)) {
            JSONObject statusJson = new JSONObject();
            try {
                statusJson.put("status", "ok");
            } catch (JSONException e) {
                Log.e(TAG, "Error creating JSON: " + e.getMessage());
            }
            
            Response response = newFixedLengthResponse(Response.Status.OK, "application/json", statusJson.toString());
            addCorsHeaders(response, corsHeaders);
            return response;
        }
        
        // Handle streams endpoint
        if (uri.startsWith("/streams/")) {
            String[] pathParts = uri.split("/");
            
            if (pathParts.length < 3) {
                return createErrorResponse(Response.Status.BAD_REQUEST, "Video ID is required", corsHeaders);
            }
            
            String videoId = pathParts[2];
            if (videoId.isEmpty()) {
                return createErrorResponse(Response.Status.BAD_REQUEST, "Video ID is required", corsHeaders);
            }
            
            try {
                JSONObject playerInfo = innerTube.player(videoId);
                Response response = newFixedLengthResponse(Response.Status.OK, "application/json", playerInfo.toString());
                addCorsHeaders(response, corsHeaders);
                return response;
            } catch (Exception e) {
                Log.e(TAG, "Error processing request: " + e.getMessage(), e);
                return createErrorResponse(Response.Status.INTERNAL_ERROR, "Error: " + e.getMessage(), corsHeaders);
            }
        }
        
        // Default 404 response for unknown routes
        return createErrorResponse(Response.Status.NOT_FOUND, "Not Found", corsHeaders);
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
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON: " + e.getMessage());
        }
        
        Response response = newFixedLengthResponse(status, "application/json", errorJson.toString());
        addCorsHeaders(response, headers);
        return response;
    }
}