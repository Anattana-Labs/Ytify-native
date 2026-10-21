package com.shashwat.ytify;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a video id into playable audio streams by asking the Worker API,
 * Casquad fallback API, and unciphered YouTube InnerTube clients (VisionOS, Android VR, TV HTML5) in sequence.
 * Replaces NewPipeExtractor with direct, lightweight, zero-bloat stream extraction using pure Android networking.
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

        public JSONObject context() {
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
                return new JSONObject().put("client", client);
            } catch (Exception e) {
                return new JSONObject();
            }
        }
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

    public static final YouTubeClient TvHtml5 = new YouTubeClient(
            "TVHTML5", "7.20230405.08.01", "7",
            "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
            null, null, null, null, null, "TV HTML5"
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

    public static boolean needsRedirectResolution(String url) {
        if (url == null) return false;
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase();
            return host.contains("huggingface.co") ||
                    host.contains("workers.dev") ||
                    host.contains("onrender.com") ||
                    host.contains("hf.space") ||
                    host.contains("casquad");
        } catch (Exception e) {
            return false;
        }
    }

    public static String resolveDirectStreamUrl(String url, int maxHops) {
        if (url == null) return null;
        if (!url.startsWith("http://") && !url.startsWith("https://")) return url;
        if (!needsRedirectResolution(url)) return url;

        String currentUrl = url;
        for (int hop = 0; hop < maxHops; hop++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(currentUrl).openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)");
                conn.setRequestProperty("Range", "bytes=0-0");

                int code = conn.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = conn.getHeaderField("Location");
                    if (loc != null && !loc.trim().isEmpty()) {
                        URI currentUri = URI.create(currentUrl);
                        URI resolved = currentUri.resolve(loc.trim());
                        currentUrl = resolved.toString();
                        if (!needsRedirectResolution(currentUrl)) {
                            return currentUrl;
                        }
                    } else {
                        break;
                    }
                } else if (code >= 200 && code < 300) {
                    return currentUrl;
                } else {
                    Log.d(TAG, "Redirect probe returned HTTP " + code + " for " + currentUrl);
                    break;
                }
            } catch (Exception e) {
                Log.d(TAG, "Redirect probe error: " + e.getMessage());
                break;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return currentUrl;
    }

    public static boolean isPlayable(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)");
            conn.setRequestProperty("Range", "bytes=0-0");
            int code = conn.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static class WorkerStreamResult {
        public String title;
        public String artist;
        public String thumbnail;
        public long durationSec;
        public String saavnStreamUrl;
        public String muzoAacStreamUrl;
        public String muzoLosslessUrl;
    }

    public static WorkerStreamResult fetchWorkerStream(String videoId) {
        String apiUrl = "https://bitter-meadow-ad01.shashwat-coding.workers.dev/v1?videoId=" + videoId + "&debug=true";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)");

            String body = readResponse(conn);
            if (!body.startsWith("{")) return null;
            JSONObject json = new JSONObject(body);

            WorkerStreamResult res = new WorkerStreamResult();
            JSONObject meta = json.optJSONObject("metadata");
            if (meta != null) {
                res.title = meta.optString("title", null);
                JSONArray artistsArr = meta.optJSONArray("artists");
                if (artistsArr != null && artistsArr.length() > 0) {
                    List<String> names = new ArrayList<>();
                    for (int i = 0; i < artistsArr.length(); i++) {
                        JSONObject a = artistsArr.optJSONObject(i);
                        if (a != null) {
                            String n = a.optString("name", "");
                            if (!n.trim().isEmpty()) names.add(n.trim());
                        }
                    }
                    if (!names.isEmpty()) {
                        res.artist = TextUtils.join(", ", names);
                    }
                }
                JSONArray thumbs = meta.optJSONArray("thumbnails");
                if (thumbs != null && thumbs.length() > 0) {
                    JSONObject lastThumb = thumbs.optJSONObject(thumbs.length() - 1);
                    if (lastThumb != null) {
                        res.thumbnail = lastThumb.optString("url", null);
                    }
                }
                res.durationSec = parseDuration(meta.optString("duration", ""));
            }

            JSONObject saavnObj = json.optJSONObject("saavn");
            if (saavnObj != null) {
                res.saavnStreamUrl = saavnObj.optString("streamUrl", null);
            }

            JSONObject muzoObj = json.optJSONObject("muzo");
            if (muzoObj != null) {
                res.muzoLosslessUrl = muzoObj.optString("lossless", null);
                res.muzoAacStreamUrl = muzoObj.optString("aacStream", null);
            }

            return res;
        } catch (Exception e) {
            Log.d(TAG, videoId + " worker API error: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static class CasquadResult {
        public String name;
        public String artist;
        public String url;
        public String losslessUrl;
    }

    public static CasquadResult fetchCasquadFallbackStream(String videoId) {
        String apiUrl = "https://muzo-bot-1.onrender.com/api/stream?id=" + videoId;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)");

            String body = readResponse(conn);
            if (!body.startsWith("{")) return null;
            JSONObject json = new JSONObject(body);

            CasquadResult res = new CasquadResult();
            res.name = json.optString("name", null);
            res.artist = json.optString("artist", null);
            res.url = json.optString("url", null);
            res.losslessUrl = json.optString("lossless", null);
            return res;
        } catch (Exception e) {
            Log.d(TAG, videoId + " casquad API error: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static long parseDuration(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0L;
        String[] parts = raw.trim().split(":");
        try {
            if (parts.length == 1) return Long.parseLong(parts[0]);
            if (parts.length == 2) return Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1]);
            if (parts.length == 3) return Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2]);
        } catch (Exception ignored) {}
        return 0L;
    }

    public static JSONObject queryInnerTube(String videoId, YouTubeClient client) {
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject()
                    .put("context", client.context())
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
        } catch (Exception e) {
            Log.d(TAG, "InnerTube error for " + client.friendlyName + ": " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    /**
     * Resolves all available audio and video streams for videoId and returns the unified JSON response.
     */
    public static JSONObject resolveStreams(String videoId) throws Exception {
        String title = null;
        String author = null;
        long lengthSeconds = 0;
        JSONArray adaptiveFormats = new JSONArray();

        // 1. Check Worker Stream (320kbps m4a)
        WorkerStreamResult worker = fetchWorkerStream(videoId);
        if (worker != null) {
            if (worker.title != null && !worker.title.trim().isEmpty()) title = worker.title.trim();
            if (worker.artist != null && !worker.artist.trim().isEmpty()) author = worker.artist.trim();
            if (worker.durationSec > 0) lengthSeconds = worker.durationSec;

            if (worker.saavnStreamUrl != null && !worker.saavnStreamUrl.trim().isEmpty()) {
                String directSaavn = resolveDirectStreamUrl(worker.saavnStreamUrl, 3);
                if (directSaavn != null) {
                    JSONObject format = new JSONObject();
                    format.put("url", directSaavn);
                    format.put("type", "audio/mp4; codecs=\"mp4a.40.2\"");
                    format.put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"");
                    format.put("itag", 140);
                    format.put("bitrate", 320000);
                    format.put("quality", "AUDIO_QUALITY_HIGH");
                    format.put("audioQuality", "AUDIO_QUALITY_HIGH");
                    adaptiveFormats.put(format);
                    Log.i(TAG, "Added Worker Saavn 320kbps m4a stream for " + videoId);
                }
            }
        }

        // 2. Check Casquad Fallback
        if (adaptiveFormats.length() == 0) {
            CasquadResult casquad = fetchCasquadFallbackStream(videoId);
            if (casquad != null) {
                if (title == null && casquad.name != null) title = casquad.name.trim();
                if (author == null && casquad.artist != null) author = casquad.artist.trim();
                if (casquad.url != null && !casquad.url.trim().isEmpty()) {
                    String directCasquad = resolveDirectStreamUrl(casquad.url, 3);
                    if (directCasquad != null) {
                        JSONObject format = new JSONObject();
                        format.put("url", directCasquad);
                        format.put("type", "audio/mp4; codecs=\"mp4a.40.2\"");
                        format.put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"");
                        format.put("itag", 140);
                        format.put("bitrate", 320000);
                        format.put("quality", "AUDIO_QUALITY_HIGH");
                        format.put("audioQuality", "AUDIO_QUALITY_HIGH");
                        adaptiveFormats.put(format);
                        Log.i(TAG, "Added Casquad 320kbps m4a stream for " + videoId);
                    }
                }
            }
        }

        // 3. Try Native Unciphered InnerTube Clients (VisionOs, AndroidVR, TVHTML5)
        YouTubeClient[] clients = new YouTubeClient[]{ VisionOs, AndroidVr16510, TvHtml5 };
        for (YouTubeClient client : clients) {
            JSONObject itResponse = queryInnerTube(videoId, client);
            if (itResponse != null) {
                JSONObject videoDetails = itResponse.optJSONObject("videoDetails");
                if (videoDetails != null) {
                    if (title == null) title = videoDetails.optString("title", "");
                    if (author == null) author = videoDetails.optString("author", "");
                    if (lengthSeconds == 0) lengthSeconds = videoDetails.optLong("lengthSeconds", 0);
                }

                JSONObject streamingData = itResponse.optJSONObject("streamingData");
                if (streamingData != null) {
                    JSONArray itFormats = streamingData.optJSONArray("adaptiveFormats");
                    if (itFormats != null) {
                        int addedCount = 0;
                        for (int i = 0; i < itFormats.length(); i++) {
                            JSONObject f = itFormats.optJSONObject(i);
                            if (f != null && f.has("url")) {
                                String u = f.optString("url");
                                if (u != null && !u.trim().isEmpty()) {
                                    adaptiveFormats.put(f);
                                    addedCount++;
                                }
                            }
                        }
                        if (addedCount > 0) {
                            Log.i(TAG, "Acquired " + addedCount + " unciphered streams from " + client.friendlyName);
                            break;
                        }
                    }
                }
            }
        }

        if (adaptiveFormats.length() == 0) {
            throw new Exception("No playable audio stream could be extracted for video " + videoId);
        }

        JSONObject result = new JSONObject();
        result.put("id", videoId);
        result.put("title", title != null ? title : "Unknown Title");
        result.put("author", author != null ? author : "Unknown Artist");
        result.put("authorId", "");
        result.put("lengthSeconds", lengthSeconds);
        result.put("adaptiveFormats", adaptiveFormats);

        JSONObject streamingData = new JSONObject();
        streamingData.put("adaptiveFormats", adaptiveFormats);
        result.put("streamingData", streamingData);

        return result;
    }
}
