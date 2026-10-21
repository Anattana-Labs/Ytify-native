package com.shashwat.ytify;

/**
 * Replaced NewPipeDownloader with direct OkHttp streaming via PlaybackResolver.
 */
public class NewPipeDownloader {
    private static NewPipeDownloader instance;

    private NewPipeDownloader() {}

    public static synchronized NewPipeDownloader getInstance() {
        if (instance == null) {
            instance = new NewPipeDownloader();
        }
        return instance;
    }
}
