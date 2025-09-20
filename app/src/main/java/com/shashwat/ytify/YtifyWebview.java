package com.shashwat.ytify;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.View;
import android.util.Log;
import android.webkit.WebView;

public class YtifyWebview extends WebView {
    private static final String TAG = "YtifyWebview";
    private HttpServer httpServer;
    private boolean serverRunning = false;

    public YtifyWebview(Context context) {
        super(context);
        initializeServer();
    }

    public YtifyWebview(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeServer();
    }

    public YtifyWebview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initializeServer();
    }

    private void initializeServer() {
        try {
            if (httpServer == null) {
                httpServer = new HttpServer();
            }
            
            if (!httpServer.isRunning()) {
                httpServer.start();
                serverRunning = true;
                Log.i(TAG, "HTTP Server initialized and started");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing server: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        initializeServer();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (httpServer != null && serverRunning) {
            httpServer.stop();
            serverRunning = false;
            Log.i(TAG, "HTTP Server stopped");
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        SharedPreferences prefs = getContext().getSharedPreferences("ytify", Context.MODE_PRIVATE);
        boolean bgPlay = prefs.getBoolean("bgplay", false); 
        
        if (visibility != View.GONE && visibility != View.INVISIBLE || !bgPlay){
            super.onWindowVisibilityChanged(visibility);
        }
    }
}
