package com.shashwat.ytify;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Pure YouTube InnerTube stream extractor for ytify.
 * Completely local, zero external worker/bucket APIs, zero external extractor library.
 * Direct unciphered InnerTube multi-client pipeline for both audio and video playback.
 */
public class PlaybackResolver {

    private static final String TAG = "PlaybackResolver";
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 12000;

    public static class YouTubeClient {
        public final String clientName;
        public final String clientVersion;
        public final String clientId;
        public final String userAgent;
        public final String osName;
        public final String osVersion;
        public final String deviceMake;
        public final String deviceModel;
        public final String androidSdkVersion;
        public final String friendlyName;

        public YouTubeClient(String clientName, String clientVersion, String clientId, String userAgent,
                             String osName, String osVersion, String deviceMake, String deviceModel,
                             String androidSdkVersion, String friendlyName) {
            this.clientName = clientName;
            this.clientVersion = clientVersion;
            this.clientId = clientId;
            this.userAgent = userAgent;
            this.osName = osName;
            this.osVersion = osVersion;
            this.deviceMake = deviceMake;
            this.deviceModel = deviceModel;
            this.androidSdkVersion = androidSdkVersion;
            this.friendlyName = friendlyName;
        }

        public JSONObject context(String visitorData) {
            try {
                JSONObject client = new JSONObject()
                        .put("clientName", clientName)
                        .put("clientVersion", clientVersion)
                        .put("gl", "US")
                        .put("hl", "en");
                if (osName != null) client.put("osName", osName);
                if (osVersion != null) client.put("osVersion", osVersion);
                if (deviceMake != null) client.put("deviceMake", deviceMake);
                if (deviceModel != null) client.put("deviceModel", deviceModel);
                if (androidSdkVersion != null) client.put("androidSdkVersion", androidSdkVersion);
                if (visitorData != null && !visitorData.trim().isEmpty()) {
                    client.put("visitorData", visitorData.trim());
                }
                return new JSONObject().put("client", client);
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    private static volatile String cachedVisitorData = null;

    public static synchronized String getVisitorData(boolean forceRefresh) {
        if (!forceRefresh && cachedVisitorData != null && !cachedVisitorData.trim().isEmpty()) {
            return cachedVisitorData;
        }
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject().put(
                    "context", new JSONObject().put(
                            "client", new JSONObject()
                                    .put("clientName", "WEB")
                                    .put("clientVersion", "2.20240901.01.00")
                                    .put("gl", "US")
                                    .put("hl", "en")
                    )
            );
            conn = (HttpURLConnection) new URL("https://www.youtube.com/youtubei/v1/visitor_id").openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            String resStr = readResponse(conn);
            if (resStr.startsWith("{")) {
                JSONObject json = new JSONObject(resStr);
                JSONObject respCtx = json.optJSONObject("responseContext");
                if (respCtx != null) {
                    String vd = respCtx.optString("visitorData", null);
                    if (vd != null && !vd.trim().isEmpty()) {
                        cachedVisitorData = vd.trim();
                        Log.i(TAG, "Acquired YouTube visitorData: " + cachedVisitorData);
                        return cachedVisitorData;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch visitorData: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return cachedVisitorData;
    }

    public static final YouTubeClient VisionOs = new YouTubeClient(
            "VISIONOS", "0.1", "101",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
            "visionOS", "1.3.21O771", "Apple", "RealityDevice14,1", null, "visionOS"
    );

    public static final YouTubeClient AndroidVr16510 = new YouTubeClient(
            "ANDROID_VR", "1.65.10", "28",
            "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            "Android", "12", "Oculus", "Quest 3", "32", "Android VR 1.65"
    );

    public static final YouTubeClient AndroidVr16148 = new YouTubeClient(
            "ANDROID_VR", "1.61.48", "28",
            "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            "Android", "12", "Oculus", "Quest 3", "32", "Android VR 1.61"
    );

    public static final YouTubeClient AndroidVr14332 = new YouTubeClient(
            "ANDROID_VR", "1.43.32", "28",
            "com.google.android.apps.youtube.vr.oculus/1.43.32 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)",
            "Android", "12", "Oculus", "Quest 3", "32", "Android VR 1.43"
    );

    public static final YouTubeClient AndroidVrNoAuth = new YouTubeClient(
            "ANDROID_VR", "1.65.10", "28",
            "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12; en_US; Oculus Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            null, null, null, null, null, "Android VR (no device)"
    );

    public static final YouTubeClient TvHtml5 = new YouTubeClient(
            "TVHTML5", "7.20230405.08.01", "7",
            "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
            null, null, null, null, null, "TV HTML5"
    );

    public static final YouTubeClient AndroidTestSuite = new YouTubeClient(
            "ANDROID_TESTSUITE", "1.9", "3",
            "Google-HTTP-Java-Client/1.43.3 (gzip)",
            null, null, null, null, "30", "Android TestSuite"
    );

    private static String readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        }
    }

    public static JSONObject queryInnerTube(String videoId, YouTubeClient client, String visitorData) {
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject()
                    .put("context", client.context(visitorData))
                    .put("videoId", videoId)
                    .put("playbackContext", new JSONObject().put(
                            "contentPlaybackContext", new JSONObject().put("signatureTimestamp", 20630)
                    ));

            conn = (HttpURLConnection) new URL("https://www.youtube.com/youtubei/v1/player").openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", client.userAgent);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Youtube-Client-Name", client.clientId);
            conn.setRequestProperty("X-Youtube-Client-Version", client.clientVersion);
            if (visitorData != null && !visitorData.trim().isEmpty()) {
                conn.setRequestProperty("X-Goog-Visitor-Id", visitorData.trim());
            }

            boolean isWeb = client.clientName.startsWith("WEB") || client.clientName.startsWith("TVHTML5");
            if (isWeb) {
                conn.setRequestProperty("Origin", "https://www.youtube.com");
                conn.setRequestProperty("Referer", "https://www.youtube.com/");
            }

            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                os.write(bytes);
            }

            String resStr = readResponse(conn);
            if (!resStr.startsWith("{")) return null;
            JSONObject json = new JSONObject(resStr);
            JSONObject playability = json.optJSONObject("playabilityStatus");
            if (playability != null && "OK".equalsIgnoreCase(playability.optString("status"))) {
                return json;
            }
            String status = playability != null ? playability.optString("status") : "null";
            String reason = playability != null ? playability.optString("reason") : "";
            Log.d(TAG, client.friendlyName + " status: " + status + ", reason: " + reason);
        } catch (Exception e) {
            Log.d(TAG, "InnerTube error for " + client.friendlyName + ": " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    /**
     * Resolves all available audio and video streams for videoId using pure YouTube InnerTube.
     * No external scraping library, no external bucket/worker APIs.
     */
    public static JSONObject resolveStreams(String videoId) throws Exception {
        String visitorData = getVisitorData(false);

        YouTubeClient[] clients = new YouTubeClient[]{
                AndroidVr16510,
                VisionOs,
                AndroidVr16148,
                AndroidVr14332,
                AndroidVrNoAuth,
                TvHtml5,
                AndroidTestSuite
        };

        JSONObject successfulResponse = null;
        YouTubeClient successfulClient = null;

        for (int attempt = 0; attempt < 2 && successfulResponse == null; attempt++) {
            if (attempt > 0) {
                Log.i(TAG, "Refreshing visitorData after initial stream attempt failed for: " + videoId);
                visitorData = getVisitorData(true);
            }
            for (YouTubeClient client : clients) {
                JSONObject resp = queryInnerTube(videoId, client, visitorData);
                if (resp != null) {
                    JSONObject streamingData = resp.optJSONObject("streamingData");
                    if (streamingData != null) {
                        JSONArray adaptive = streamingData.optJSONArray("adaptiveFormats");
                        JSONArray muxed = streamingData.optJSONArray("formats");
                        boolean hasPlayableUrl = false;
                        if (adaptive != null) {
                            for (int i = 0; i < adaptive.length(); i++) {
                                JSONObject f = adaptive.optJSONObject(i);
                                if (f != null && f.has("url") && !f.optString("url").trim().isEmpty()) {
                                    hasPlayableUrl = true;
                                    break;
                                }
                            }
                        }
                        if (!hasPlayableUrl && muxed != null) {
                            for (int i = 0; i < muxed.length(); i++) {
                                JSONObject f = muxed.optJSONObject(i);
                                if (f != null && f.has("url") && !f.optString("url").trim().isEmpty()) {
                                    hasPlayableUrl = true;
                                    break;
                                }
                            }
                        }
                        if (hasPlayableUrl) {
                            successfulResponse = resp;
                            successfulClient = client;
                            Log.i(TAG, "Successfully extracted unciphered streams from InnerTube client: " + client.friendlyName);
                            break;
                        }
                    }
                }
            }
        }

        if (successfulResponse == null) {
            throw new Exception("YouTube InnerTube extraction failed for videoId " + videoId + ": all unciphered stream clients failed");
        }

        String title = "Unknown Title";
        String author = "Unknown Artist";
        long lengthSeconds = 0;

        JSONObject videoDetails = successfulResponse.optJSONObject("videoDetails");
        if (videoDetails != null) {
            title = videoDetails.optString("title", "Unknown Title");
            author = videoDetails.optString("author", "Unknown Artist");
            lengthSeconds = videoDetails.optLong("lengthSeconds", 0);
        }

        JSONObject streamingData = successfulResponse.optJSONObject("streamingData");
        JSONArray rawAdaptive = streamingData != null ? streamingData.optJSONArray("adaptiveFormats") : null;
        JSONArray rawMuxed = streamingData != null ? streamingData.optJSONArray("formats") : null;

        JSONArray outAdaptiveFormats = new JSONArray();
        JSONArray outFormatStreams = new JSONArray();

        // 1. Process adaptiveFormats (separate audio-only & video-only streams)
        if (rawAdaptive != null) {
            for (int i = 0; i < rawAdaptive.length(); i++) {
                JSONObject f = rawAdaptive.optJSONObject(i);
                if (f == null) continue;
                String rawUrl = f.optString("url", null);
                if (rawUrl == null || rawUrl.trim().isEmpty()) continue; // skip ciphered formats

                JSONObject cleanFormat = new JSONObject(f.toString());
                int itagVal = cleanFormat.optInt("itag", 0);
                String mime = cleanFormat.optString("mimeType", "");
                boolean isVideo = mime.startsWith("video/");
                boolean isAudio = mime.startsWith("audio/");

                cleanFormat.put("type", mime);
                cleanFormat.put("mimeType", mime);
                cleanFormat.put("itag", itagVal);

                long br = cleanFormat.optLong("bitrate", 0);
                cleanFormat.put("bitrate", String.valueOf(br));
                cleanFormat.put("clen", cleanFormat.optString("contentLength", ""));

                if (isVideo) {
                    int height = cleanFormat.optInt("height", 0);
                    int width = cleanFormat.optInt("width", 0);
                    int fps = cleanFormat.optInt("fps", 30);
                    String rawQLabel = cleanFormat.optString("qualityLabel", "");
                    String baseRes = "";
                    if (!rawQLabel.isEmpty()) {
                        baseRes = rawQLabel.replaceAll("(?i)[^0-9p]", "");
                    }
                    if (baseRes.isEmpty() && height > 0) {
                        if (height >= 1800) baseRes = "2160p";
                        else if (height >= 1200) baseRes = "1440p";
                        else if (height >= 900) baseRes = "1080p";
                        else if (height >= 600) baseRes = "720p";
                        else if (height >= 400) baseRes = "480p";
                        else if (height >= 300) baseRes = "360p";
                        else if (height >= 200) baseRes = "240p";
                        else baseRes = "144p";
                    }
                    if (baseRes.isEmpty()) {
                        baseRes = cleanFormat.optString("quality", "360p");
                    }

                    cleanFormat.put("resolution", baseRes);
                    cleanFormat.put("quality", baseRes);
                    cleanFormat.put("qualityLabel", baseRes);
                    cleanFormat.put("height", height);
                    cleanFormat.put("width", width);
                    cleanFormat.put("fps", fps);
                } else if (isAudio) {
                    cleanFormat.put("resolution", "");
                    cleanFormat.put("quality", cleanFormat.optString("audioQuality", "medium"));
                    cleanFormat.put("encoding", mime.contains("opus") ? "opus" : "m4a");
                }

                cleanFormat.put("url", rawUrl);
                cleanFormat.put("originalUrl", rawUrl);

                outAdaptiveFormats.put(cleanFormat);
            }
        }

        // 2. Process formatStreams (muxed audio+video formats: itag 18, 22, etc.)
        if (rawMuxed != null) {
            for (int i = 0; i < rawMuxed.length(); i++) {
                JSONObject f = rawMuxed.optJSONObject(i);
                if (f == null) continue;
                String rawUrl = f.optString("url", null);
                if (rawUrl == null || rawUrl.trim().isEmpty()) continue;

                JSONObject cleanFormat = new JSONObject(f.toString());
                int itagVal = cleanFormat.optInt("itag", 0);
                String mime = cleanFormat.optString("mimeType", "video/mp4");

                cleanFormat.put("type", mime);
                cleanFormat.put("mimeType", mime);
                cleanFormat.put("itag", itagVal);

                long br = cleanFormat.optLong("bitrate", 0);
                cleanFormat.put("bitrate", String.valueOf(br));
                cleanFormat.put("clen", cleanFormat.optString("contentLength", ""));

                String rawQLabel = cleanFormat.optString("qualityLabel", "");
                String baseRes = "";
                if (!rawQLabel.isEmpty()) {
                    baseRes = rawQLabel.replaceAll("(?i)[^0-9p]", "");
                }
                if (baseRes.isEmpty() && cleanFormat.has("height")) {
                    int h = cleanFormat.optInt("height", 0);
                    if (h >= 1800) baseRes = "2160p";
                    else if (h >= 1200) baseRes = "1440p";
                    else if (h >= 900) baseRes = "1080p";
                    else if (h >= 600) baseRes = "720p";
                    else if (h >= 400) baseRes = "480p";
                    else if (h >= 300) baseRes = "360p";
                    else if (h >= 200) baseRes = "240p";
                    else baseRes = "144p";
                }
                if (baseRes.isEmpty()) {
                    baseRes = cleanFormat.optString("quality", "360p");
                }

                cleanFormat.put("resolution", baseRes);
                cleanFormat.put("quality", baseRes);
                cleanFormat.put("qualityLabel", baseRes);

                cleanFormat.put("url", rawUrl);
                cleanFormat.put("originalUrl", rawUrl);

                outFormatStreams.put(cleanFormat);
            }
        }

        // 3. Captions if available
        JSONArray outCaptions = new JSONArray();
        JSONObject captionsObj = successfulResponse.optJSONObject("captions");
        if (captionsObj != null) {
            JSONObject renderer = captionsObj.optJSONObject("playerCaptionsTracklistRenderer");
            if (renderer != null) {
                JSONArray tracks = renderer.optJSONArray("captionTracks");
                if (tracks != null) {
                    for (int i = 0; i < tracks.length(); i++) {
                        JSONObject track = tracks.optJSONObject(i);
                        if (track != null) {
                            String baseUrl = track.optString("baseUrl", "");
                            if (!baseUrl.isEmpty()) {
                                JSONObject c = new JSONObject();
                                String label = "";
                                JSONObject nameObj = track.optJSONObject("name");
                                if (nameObj != null) {
                                    label = nameObj.optString("simpleText", "");
                                }
                                c.put("label", label);
                                c.put("language_code", track.optString("languageCode", "en"));
                                c.put("url", baseUrl);
                                outCaptions.put(c);
                            }
                        }
                    }
                }
            }
        }

        // 4. Construct unified Invidious response for ytify
        JSONObject result = new JSONObject();
        result.put("id", videoId);
        result.put("videoId", videoId);
        result.put("title", title);
        result.put("author", author);
        result.put("authorId", "");
        result.put("lengthSeconds", lengthSeconds);
        result.put("adaptiveFormats", outAdaptiveFormats);
        result.put("formatStreams", outFormatStreams);
        result.put("recommendedVideos", new JSONArray());
        result.put("captions", outCaptions);
        result.put("liveNow", false);
        result.put("proxy", "");

        JSONObject outStreamingData = new JSONObject();
        outStreamingData.put("adaptiveFormats", outAdaptiveFormats);
        outStreamingData.put("formats", outFormatStreams);
        result.put("streamingData", outStreamingData);

        return result;
    }
}
