package com.shashwat.ytify;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class InnerTube {
    private static final String TAG = "InnerTube";
    private static final String BASE_URL = "https://youtubei.googleapis.com/youtubei/v1/";
    private static final String REFERER_YOUTUBE = "https://www.youtube.com/";
    private static final String USER_AGENT_ANDROID = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Mobile Safari/537.36";
    
    private final String clientName;
    private final String clientVersion;
    private final int clientId;
    private final String userAgent;
    private final String referer;
    
    public InnerTube() {
        this.clientName = "ANDROID";
        this.clientVersion = "19.17.34";
        this.clientId = 3;
        this.userAgent = USER_AGENT_ANDROID;
        this.referer = REFERER_YOUTUBE;
    }
    
    public JSONObject player(String videoId) throws IOException, JSONException {
        JSONObject payload = getContextPayload();
        try {
            payload.put("videoId", videoId);
        } catch (JSONException e) {
            Log.e(TAG, "Error adding videoId to payload: " + e.getMessage());
            throw e;
        }
        
        return makeRequest("player", payload);
    }
    
    private JSONObject getContextPayload() {
        JSONObject payload = new JSONObject();
        try {
            JSONObject context = new JSONObject();
            JSONObject client = new JSONObject();
            
            client.put("clientName", clientName);
            client.put("clientVersion", clientVersion);
            
            context.put("client", client);
            payload.put("context", context);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating context payload: " + e.getMessage());
        }
        
        return payload;
    }
    
    private JSONObject makeRequest(String endpoint, JSONObject payload) throws IOException, JSONException {
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("X-Goog-Api-Format-Version", "1");
            connection.setRequestProperty("X-YouTube-Client-Name", String.valueOf(clientId));
            connection.setRequestProperty("X-YouTube-Client-Version", clientVersion);
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setRequestProperty("Referer", referer);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            
            // Write payload to request body
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Check response code
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error! status: " + responseCode);
            }
            
            // Read response
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }
            
            return new JSONObject(response.toString());
        } finally {
            connection.disconnect();
        }
    }
    
    private Error handleError(Exception error) {
        return new Error("YouTube API Error: " + error.getMessage());
    }
}