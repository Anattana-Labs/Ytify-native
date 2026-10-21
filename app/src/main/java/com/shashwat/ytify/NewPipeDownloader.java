package com.shashwat.ytify;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NewPipeDownloader extends Downloader {

    private static NewPipeDownloader instance;

    private NewPipeDownloader() {}

    public static synchronized NewPipeDownloader getInstance() {
        if (instance == null) {
            instance = new NewPipeDownloader();
        }
        return instance;
    }

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        String urlString = request.url();
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod(request.httpMethod());

        Map<String, List<String>> headers = request.headers();
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                String name = entry.getKey();
                for (String val : entry.getValue()) {
                    conn.addRequestProperty(name, val);
                }
            }
        }

        byte[] dataToSend = request.dataToSend();
        if (dataToSend != null && dataToSend.length > 0) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(dataToSend);
            }
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == 429) {
            throw new ReCaptchaException("reCaptcha Challenge requested", urlString);
        }

        String responseMessage = conn.getResponseMessage();
        Map<String, List<String>> responseHeaders = new HashMap<>(conn.getHeaderFields());
        String latestUrl = conn.getURL().toString();

        InputStream is = (responseCode >= 200 && responseCode < 400) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder bodyBuilder = new StringBuilder();
        if (is != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    bodyBuilder.append(line).append("\n");
                }
            }
        }

        return new Response(
                responseCode,
                responseMessage != null ? responseMessage : "",
                responseHeaders,
                bodyBuilder.toString(),
                latestUrl
        );
    }
}
