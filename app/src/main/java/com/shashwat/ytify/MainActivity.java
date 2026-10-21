package com.shashwat.ytify;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.net.*;
import android.util.*;
import android.webkit.*;
import java.io.*;
import org.json.*;
import android.content.pm.*;
import android.provider.Settings;
import java.net.URLEncoder;
import android.content.SharedPreferences;
import android.webkit.CookieManager;

public class MainActivity extends Activity {

  private boolean portrait = false;
  BroadcastReceiver broadcastReceiver;

  private String icon = "";
  private String title = "";
  private String subtitle = "";
  private long duration;
  private boolean isPlaying = false;
  private boolean dL = false;

  private VeltrixWebview web;

  // Start HTTP server
  private void startHttpServer() {
    try {
      HttpServer server = new HttpServer();
      if (!server.isRunning()) {
        server.start();
        Log.i("MainActivity", "HTTP Server started on port " + server.getPort());
      }
    } catch (Exception e) {
      Log.e("MainActivity", "Error starting HTTP server: " + e.getMessage(), e);
    }
  }

  private static final String JS_MEDIA_SCRIPT =
      "(function() {\n" +
      "  if (window.__ytify_native_injected) return;\n" +
      "  window.__ytify_native_injected = true;\n" +
      "  window.__mediaSessionHandlers = window.__mediaSessionHandlers || {};\n" +
      "  window.__currentAudio = window.__currentAudio || null;\n" +
      "  window.__currentMediaData = window.__currentMediaData || {\n" +
      "    title: '', artist: '', album: '', artwork: '', duration: 0, position: 0, state: 'none', hasValidMetadata: false\n" +
      "  };\n" +
      "  function cleanText(str) {\n" +
      "    if (!str) return '';\n" +
      "    var s = ('' + str).replace(/\\s*-\\s*ytify\\s*$/i, '').trim();\n" +
      "    if (s.toLowerCase() === 'ytify' || s.toLowerCase() === 'free music streaming') return '';\n" +
      "    return s;\n" +
      "  }\n" +
      "  function sendToAndroid() {\n" +
      "    try {\n" +
      "      if (typeof Android === 'undefined' || !Android.updateMedia) return;\n" +
      "      var d = window.__currentMediaData;\n" +
      "      if (!d.hasValidMetadata || !d.title) return;\n" +
      "      var dur = d.duration || 0;\n" +
      "      var pos = d.position || 0;\n" +
      "      if (window.__currentAudio) {\n" +
      "        if ((!dur || isNaN(dur)) && window.__currentAudio.duration && !isNaN(window.__currentAudio.duration)) {\n" +
      "          dur = window.__currentAudio.duration * 1000;\n" +
      "        }\n" +
      "        if (window.__currentAudio.currentTime && !isNaN(window.__currentAudio.currentTime)) {\n" +
      "          pos = window.__currentAudio.currentTime * 1000;\n" +
      "        }\n" +
      "      }\n" +
      "      Android.updateMedia(\n" +
      "        d.title,\n" +
      "        d.artist || '',\n" +
      "        d.album || '',\n" +
      "        d.artwork || '',\n" +
      "        Math.round(dur || 0),\n" +
      "        Math.round(pos || 0),\n" +
      "        d.state || 'none'\n" +
      "      );\n" +
      "    } catch (e) { console.error('Error sending media to Android:', e); }\n" +
      "  }\n" +
      "  function handleMetadataUpdate(meta) {\n" +
      "    if (!meta) return;\n" +
      "    var title = cleanText(meta.title);\n" +
      "    if (!title) return;\n" +
      "    var artist = cleanText(meta.artist);\n" +
      "    var album = cleanText(meta.album);\n" +
      "    var artwork = '';\n" +
      "    if (meta.artwork && meta.artwork.length > 0) {\n" +
      "      artwork = meta.artwork[meta.artwork.length - 1].src || '';\n" +
      "    }\n" +
      "    if (!artwork) {\n" +
      "      try {\n" +
      "        var mini = document.querySelector('.miniplayer img') || document.querySelector('img[alt^=\"Media Artwork for\"]');\n" +
      "        if (mini && mini.src) artwork = mini.src;\n" +
      "      } catch (e) {}\n" +
      "    }\n" +
      "    window.__currentMediaData.title = title;\n" +
      "    window.__currentMediaData.artist = artist;\n" +
      "    window.__currentMediaData.album = album;\n" +
      "    window.__currentMediaData.hasValidMetadata = true;\n" +
      "    if (artwork && artwork.startsWith('blob:')) {\n" +
      "      fetch(artwork)\n" +
      "        .then(function(res) { return res.blob(); })\n" +
      "        .then(function(blob) {\n" +
      "          var reader = new FileReader();\n" +
      "          reader.onloadend = function() {\n" +
      "            window.__currentMediaData.artwork = reader.result || '';\n" +
      "            sendToAndroid();\n" +
      "          };\n" +
      "          reader.onerror = function() {\n" +
      "            window.__currentMediaData.artwork = '';\n" +
      "            sendToAndroid();\n" +
      "          };\n" +
      "          reader.readAsDataURL(blob);\n" +
      "        })\n" +
      "        .catch(function(e) {\n" +
      "          window.__currentMediaData.artwork = '';\n" +
      "          sendToAndroid();\n" +
      "        });\n" +
      "    } else {\n" +
      "      window.__currentMediaData.artwork = artwork;\n" +
      "      sendToAndroid();\n" +
      "    }\n" +
      "  }\n" +
      "  var OrigMediaMetadata = window.MediaMetadata;\n" +
      "  if (OrigMediaMetadata) {\n" +
      "    window.MediaMetadata = function(init) {\n" +
      "      var inst = new OrigMediaMetadata(init);\n" +
      "      try { if (init) handleMetadataUpdate(init); } catch (e) {}\n" +
      "      return inst;\n" +
      "    };\n" +
      "    window.MediaMetadata.prototype = OrigMediaMetadata.prototype;\n" +
      "  }\n" +
      "  if (window.MediaSession && window.MediaSession.prototype) {\n" +
      "    try {\n" +
      "      var protoDesc = Object.getOwnPropertyDescriptor(window.MediaSession.prototype, 'metadata');\n" +
      "      var origProtoSet = protoDesc ? protoDesc.set : null;\n" +
      "      var origProtoGet = protoDesc ? protoDesc.get : null;\n" +
      "      Object.defineProperty(window.MediaSession.prototype, 'metadata', {\n" +
      "        configurable: true,\n" +
      "        enumerable: true,\n" +
      "        get: function() { return origProtoGet ? origProtoGet.call(this) : this.__meta; },\n" +
      "        set: function(val) {\n" +
      "          this.__meta = val;\n" +
      "          if (origProtoSet) { try { origProtoSet.call(this, val); } catch (e) {} }\n" +
      "          handleMetadataUpdate(val);\n" +
      "        }\n" +
      "      });\n" +
      "    } catch (e) {}\n" +
      "  }\n" +
      "  function hookAudio(audio) {\n" +
      "    if (!audio || audio.__hooked) return;\n" +
      "    audio.__hooked = true;\n" +
      "    window.__currentAudio = audio;\n" +
      "    audio.addEventListener('play', function() {\n" +
      "      window.__currentAudio = audio;\n" +
      "      window.__currentMediaData.state = 'playing';\n" +
      "      window.__currentMediaData.position = (audio.currentTime || 0) * 1000;\n" +
      "      if (audio.duration && !isNaN(audio.duration)) {\n" +
      "        window.__currentMediaData.duration = audio.duration * 1000;\n" +
      "      }\n" +
      "      if (navigator.mediaSession && navigator.mediaSession.metadata) {\n" +
      "        handleMetadataUpdate(navigator.mediaSession.metadata);\n" +
      "      } else {\n" +
      "        sendToAndroid();\n" +
      "      }\n" +
      "    });\n" +
      "    audio.addEventListener('pause', function() {\n" +
      "      window.__currentMediaData.state = 'paused';\n" +
      "      window.__currentMediaData.position = (audio.currentTime || 0) * 1000;\n" +
      "      sendToAndroid();\n" +
      "    });\n" +
      "    audio.addEventListener('ended', function() {\n" +
      "      window.__currentMediaData.state = 'paused';\n" +
      "      sendToAndroid();\n" +
      "    });\n" +
      "    audio.addEventListener('timeupdate', function() {\n" +
      "      window.__currentMediaData.position = (audio.currentTime || 0) * 1000;\n" +
      "      if (audio.duration && !isNaN(audio.duration)) {\n" +
      "        window.__currentMediaData.duration = audio.duration * 1000;\n" +
      "      }\n" +
      "    });\n" +
      "    audio.addEventListener('loadedmetadata', function() {\n" +
      "      if (audio.duration && !isNaN(audio.duration)) {\n" +
      "        window.__currentMediaData.duration = audio.duration * 1000;\n" +
      "      }\n" +
      "      if (navigator.mediaSession && navigator.mediaSession.metadata) {\n" +
      "        handleMetadataUpdate(navigator.mediaSession.metadata);\n" +
      "      }\n" +
      "    });\n" +
      "  }\n" +
      "  var OrigAudio = window.Audio;\n" +
      "  if (OrigAudio) {\n" +
      "    window.Audio = function(...args) {\n" +
      "      var a = new OrigAudio(...args);\n" +
      "      hookAudio(a);\n" +
      "      return a;\n" +
      "    };\n" +
      "    window.Audio.prototype = OrigAudio.prototype;\n" +
      "  }\n" +
      "  if ('mediaSession' in navigator) {\n" +
      "    var ms = navigator.mediaSession;\n" +
      "    var origSetActionHandler = ms.setActionHandler ? ms.setActionHandler.bind(ms) : null;\n" +
      "    ms.setActionHandler = function(action, handler) {\n" +
      "      window.__mediaSessionHandlers[action] = handler;\n" +
      "      if (origSetActionHandler) {\n" +
      "        try { origSetActionHandler(action, handler); } catch (e) {}\n" +
      "      }\n" +
      "    };\n" +
      "    var origSetPosition = ms.setPositionState ? ms.setPositionState.bind(ms) : null;\n" +
      "    ms.setPositionState = function(state) {\n" +
      "      if (origSetPosition) {\n" +
      "        try { origSetPosition(state); } catch (e) {}\n" +
      "      }\n" +
      "      if (state) {\n" +
      "        if (state.duration !== undefined) window.__currentMediaData.duration = state.duration * 1000;\n" +
      "        if (state.position !== undefined) window.__currentMediaData.position = state.position * 1000;\n" +
      "        sendToAndroid();\n" +
      "      }\n" +
      "    };\n" +
      "    var _metadata = ms.metadata;\n" +
      "    try {\n" +
      "      Object.defineProperty(ms, 'metadata', {\n" +
      "        configurable: true,\n" +
      "        enumerable: true,\n" +
      "        get() { return _metadata; },\n" +
      "        set(meta) {\n" +
      "          _metadata = meta;\n" +
      "          handleMetadataUpdate(meta);\n" +
      "        }\n" +
      "      });\n" +
      "    } catch (e) {}\n" +
      "    var _playbackState = ms.playbackState;\n" +
      "    try {\n" +
      "      Object.defineProperty(ms, 'playbackState', {\n" +
      "        configurable: true,\n" +
      "        enumerable: true,\n" +
      "        get() { return _playbackState; },\n" +
      "        set(val) {\n" +
      "          _playbackState = val;\n" +
      "          window.__currentMediaData.state = val === 'playing' ? 'playing' : (val === 'paused' ? 'paused' : 'none');\n" +
      "          sendToAndroid();\n" +
      "        }\n" +
      "      });\n" +
      "    } catch (e) {}\n" +
      "  }\n" +
      "  window.__nativePlay = function() {\n" +
      "    if (window.__mediaSessionHandlers['play']) {\n" +
      "      window.__mediaSessionHandlers['play']();\n" +
      "    } else if (window.__currentAudio) {\n" +
      "      window.__currentAudio.play();\n" +
      "    }\n" +
      "  };\n" +
      "  window.__nativePause = function() {\n" +
      "    if (window.__mediaSessionHandlers['pause']) {\n" +
      "      window.__mediaSessionHandlers['pause']();\n" +
      "    } else if (window.__currentAudio) {\n" +
      "      window.__currentAudio.pause();\n" +
      "    }\n" +
      "  };\n" +
      "  window.__nativeNext = function() {\n" +
      "    if (window.__mediaSessionHandlers['nexttrack']) {\n" +
      "      window.__mediaSessionHandlers['nexttrack']();\n" +
      "    }\n" +
      "  };\n" +
      "  window.__nativePrev = function() {\n" +
      "    if (window.__mediaSessionHandlers['previoustrack']) {\n" +
      "      window.__mediaSessionHandlers['previoustrack']();\n" +
      "    }\n" +
      "  };\n" +
      "  window.__nativeSeekTo = function(seconds) {\n" +
      "    if (window.__mediaSessionHandlers['seekto']) {\n" +
      "      window.__mediaSessionHandlers['seekto']({ seekTime: seconds });\n" +
      "    } else if (window.__currentAudio) {\n" +
      "      window.__currentAudio.currentTime = seconds;\n" +
      "    }\n" +
      "  };\n" +
      "})();";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    // Request notification permission on Android 13+
    if (Build.VERSION.SDK_INT >= 33) {
      if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(new String[]{ Manifest.permission.POST_NOTIFICATIONS }, 102);
      }
    }

    // Start HTTP server as soon as app opens
    startHttpServer();

    SharedPreferences prefs = getSharedPreferences("ytify", MODE_PRIVATE);

    if (!prefs.contains("bgplay")) {
      prefs.edit().putBoolean("bgplay", true).apply();
    }

    // Register broadcast receiver for audio controls
    IntentFilter filter = new IntentFilter("AUDIO_CONTROL");
    registerReceiver(new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String action = intent.getStringExtra("action");
        if (action != null && web != null) {
          switch (action) {
            case "play":
              web.evaluateJavascript("if (window.__nativePlay) { window.__nativePlay(); } else if (window.__currentAudio) { window.__currentAudio.play(); }", null);
              break;
            case "pause":
              web.evaluateJavascript("if (window.__nativePause) { window.__nativePause(); } else if (window.__currentAudio) { window.__currentAudio.pause(); }", null);
              break;
            case "next":
              web.evaluateJavascript("if (window.__nativeNext) { window.__nativeNext(); }", null);
              break;
            case "previous":
              web.evaluateJavascript("if (window.__nativePrev) { window.__nativePrev(); }", null);
              break;
            case "seekTo":
              long position = intent.getLongExtra("position", 0);
              double sec = position / 1000.0;
              web.evaluateJavascript("if (window.__nativeSeekTo) { window.__nativeSeekTo(" + sec + "); } else if (window.__currentAudio) { window.__currentAudio.currentTime = " + sec + "; }", null);
              break;
          }
        }
      }
    }, filter);

    load(false);

    MainActivity.this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  public void load(boolean dl) {

    web = findViewById(R.id.web);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setSupportZoom(true);
    web.getSettings().setBuiltInZoomControls(true);
    web.getSettings().setDisplayZoomControls(false);

    Intent intent = getIntent();
    String action = intent.getAction();
    Uri data = intent.getData();
    String url = "https://ytify.pp.ua/";
    if (Intent.ACTION_VIEW.equals(action) && data != null) {
      url = data.toString();
    } else if (Intent.ACTION_SEND.equals(action)) {
      String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
      if (sharedText != null && (sharedText.contains("ytify.pp.ua") || sharedText.contains("ytify-native.netlify.app") || sharedText.contains("youtu.be"))) {
        url = sharedText;
      }
    }
    web.loadUrl(url);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setDatabaseEnabled(true);
    web.addJavascriptInterface(new WebAppInterface(this), "Android");
    web.setWebChromeClient(new CustomWebClient());
    web.getSettings().setMediaPlaybackRequiresUserGesture(false); // Allow autoplay
    web.setLayerType(View.LAYER_TYPE_HARDWARE, null);

    web.setWebViewClient(new WebViewClient() {
      @Override
      public void onPageStarted(WebView p1, String p2, Bitmap p3) {
        p1.evaluateJavascript(JS_MEDIA_SCRIPT, null);
        super.onPageStarted(p1, p2, p3);
      }

      @Override
      public void onPageFinished(WebView p1, String url) {
        p1.evaluateJavascript(JS_MEDIA_SCRIPT, null);
        p1.evaluateJavascript("if (window.trustedTypes && window.trustedTypes.createPolicy && !window.trustedTypes.defaultPolicy) {window.trustedTypes.createPolicy('default', {createHTML: (string) => string,createScriptURL: string => string, createScript: string => string, });}", null);
        p1.evaluateJavascript("if (typeof Android !== 'undefined') { Android.setBgPlay(true); }", null);

        if (!url.contains("ytify.pp.ua") && !url.contains("ytify-native.netlify.app") && isPlaying) {
          isPlaying = false;
          stopService(new Intent(getApplicationContext(), ForegroundService.class));
        }

        super.onPageFinished(p1, url);
      }
    });

    setReceiver();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == 101) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        web.loadUrl("https://ytify.pp.ua");
      } else {
        Toast.makeText(getApplicationContext(), getString(R.string.grant_mic), Toast.LENGTH_SHORT).show();
      }
    } else if (requestCode == 1) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
        Toast.makeText(getApplicationContext(), getString(R.string.grant_storage), Toast.LENGTH_SHORT).show();
      }
    }
  }
  @Override
  public void onBackPressed() {
    if (web.canGoBack()) {
      web.goBack();
    } else {
      finish();
    }
  }

  @Override
  public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
    web.loadUrl(isInPictureInPictureMode ?
      "javascript:document.getElementsByClassName('video-stream')[0].play();" :
      "javascript:removePIP();");

  }

  @Override
  protected void onUserLeaveHint() {
    super.onUserLeaveHint();
    web.loadUrl("javascript:PIPlayer();");
  }

  public class CustomWebClient extends WebChromeClient {
    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    protected FrameLayout frame;
    private int mOriginalOrientation;
    private int mOriginalSystemUiVisibility;
    public CustomWebClient() {}

    public Bitmap getDefaultVideoPoster() {

      if (MainActivity.this == null) {
        return null;
      }
      return BitmapFactory.decodeResource(MainActivity.this.getApplicationContext().getResources(), 2130837573);
    }

    public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback viewCallback) {

      this.mOriginalOrientation = portrait ?
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT :
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        WindowManager.LayoutParams params = MainActivity.this.getWindow().getAttributes();
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        MainActivity.this.getWindow().setAttributes(params);
      }

      if (this.mCustomView != null) {
        onHideCustomView();
        return;
      }
      this.mCustomView = paramView;
      this.mOriginalSystemUiVisibility = MainActivity.this.getWindow().getDecorView().getSystemUiVisibility();
      MainActivity.this.setRequestedOrientation(this.mOriginalOrientation);
      this.mOriginalOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
      this.mCustomViewCallback = viewCallback;
      ((FrameLayout) MainActivity.this.getWindow().getDecorView()).addView(this.mCustomView, new FrameLayout.LayoutParams(-1, -1));
      MainActivity.this.getWindow().getDecorView().setSystemUiVisibility(3846);
    }
    public void onHideCustomView() {

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        MainActivity.this.getWindow().setAttributes(params);

      }

      ((FrameLayout) MainActivity.this.getWindow().getDecorView()).removeView(this.mCustomView);
      this.mCustomView = null;
      MainActivity.this.getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
      MainActivity.this.setRequestedOrientation(this.mOriginalOrientation);
      this.mOriginalOrientation = portrait ?
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT :
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;

      this.mCustomViewCallback = null;
      web.clearFocus();
    }

    @Override
    public void onPermissionRequest(final PermissionRequest request) {
      if (Build.VERSION.SDK_INT > 22 && (request.getOrigin().toString().contains("ytify.pp.ua") || request.getOrigin().toString().contains("ytify-native.netlify.app"))) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
          requestPermissions(new String[] {
            Manifest.permission.RECORD_AUDIO
          }, 101);
        } else {
          request.grant(request.getResources());
        }
      }
    }
  }

  private void downloadFile(String filename, String url, String mtype) {

    if (Build.VERSION.SDK_INT > 22 && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
      runOnUiThread(() -> Toast.makeText(getApplicationContext(), R.string.grant_storage, Toast.LENGTH_SHORT).show());
      requestPermissions(new String[] {
        Manifest.permission.WRITE_EXTERNAL_STORAGE
      }, 1);
    }
    try {
      try {
        String encodedFileName = URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");

        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(filename)
          .setDescription(filename)
          .setMimeType(mtype)
          .setAllowedOverMetered(true)
          .setAllowedOverRoaming(true)
          .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, encodedFileName)
          .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE |
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        downloadManager.enqueue(request);
        Toast.makeText(this, getString(R.string.dl_started), Toast.LENGTH_SHORT).show();
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      }
    } catch (Exception ignored) {
      Toast.makeText(this, ignored.toString(), Toast.LENGTH_SHORT).show();
    }
  }

  public class WebAppInterface {
    Context mContext;
    WebAppInterface(Context c) {
      mContext = c;
    }

    @JavascriptInterface
    public void showToast(String txt) {

      Toast.makeText(getApplicationContext(), txt + "", Toast.LENGTH_SHORT).show();
    }
    @JavascriptInterface
    public void gohome(String x) {
      Intent startMain = new Intent(Intent.ACTION_MAIN);
      startMain.addCategory(Intent.CATEGORY_HOME);
      startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(startMain);
    }

    @JavascriptInterface
    public void downvid(String name, String url, String m) {
      downloadFile(name, url, m);
    }
    @JavascriptInterface
    public void fullScreen(boolean value) {
      portrait = value;
    }
    @JavascriptInterface
    public void oplink(String url) {
      Intent i = new Intent();
      i.setAction(Intent.ACTION_VIEW);
      i.setData(Uri.parse(url));
      startActivity(i);
    }
    @JavascriptInterface
    public String getInfo() {
      PackageManager manager = getApplicationContext().getPackageManager();
      try {
        PackageInfo info = manager.getPackageInfo(getApplicationContext().getPackageName(), PackageManager.GET_ACTIVITIES);
        return info.versionName + "";
      } catch (PackageManager.NameNotFoundException e) {
        return "1.0";
      }

    }
    @JavascriptInterface
    public void setBgPlay(boolean bgplay) {

      SharedPreferences prefs = getSharedPreferences("ytify", MODE_PRIVATE);
      prefs.edit().putBoolean("bgplay", bgplay).apply();

    }

    @JavascriptInterface
    public void updateMedia(String title, String artist, String album, String artworkUrl, long duration, long position, String state) {
      if (title == null || "none".equals(state)) {
        if ("none".equals(state)) {
          bgStop();
        }
        return;
      }
      String cleanTitle = title.replaceAll("(?i)\\s*-\\s*ytify\\s*$", "").trim();
      if (cleanTitle.isEmpty() || cleanTitle.equalsIgnoreCase("ytify") || cleanTitle.equalsIgnoreCase("free music streaming")) {
        return;
      }
      isPlaying = "playing".equals(state);
      Intent intent = new Intent(getApplicationContext(), ForegroundService.class);
      intent.putExtra("icon", artworkUrl != null ? artworkUrl : "");
      intent.putExtra("title", cleanTitle);
      intent.putExtra("subtitle", artist != null ? artist.replaceAll("(?i)\\s*-\\s*ytify\\s*$", "").trim() : "");
      String cleanAlbum = album != null ? album.replaceAll("(?i)\\s*-\\s*ytify\\s*$", "").trim() : "";
      if (cleanAlbum.equalsIgnoreCase("ytify") || cleanAlbum.equalsIgnoreCase("free music streaming")) {
        cleanAlbum = "";
      }
      intent.putExtra("album", cleanAlbum);
      intent.putExtra("duration", duration);
      intent.putExtra("currentPosition", position);
      intent.putExtra("action", isPlaying ? "play" : "pause");
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent);
      } else {
        startService(intent);
      }
    }

    @JavascriptInterface
    public void bgStart(String iconn, String titlen, String subtitlen, long dura) {
      icon = iconn;
      title = titlen;
      subtitle = subtitlen;
      duration = dura;
      isPlaying = true;

      Intent intent = new Intent(getApplicationContext(), ForegroundService.class);

      // Add extras to the Intent
      intent.putExtra("icon", icon);
      intent.putExtra("title", title);
      intent.putExtra("subtitle", subtitle);
      intent.putExtra("duration", duration);
      intent.putExtra("currentPosition", 0);
      intent.putExtra("action", "play");

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent);
      } else {
        startService(intent);
      }

    }

    @JavascriptInterface
    public void bgUpdate(String iconn, String titlen, String subtitlen, long dura) {

      icon = iconn;
      title = titlen;
      subtitle = subtitlen;
      duration = (long)(dura);

      getApplicationContext().sendBroadcast(new Intent("UPDATE_NOTIFICATION")
        .putExtra("icon", icon)
        .putExtra("title", title)
        .putExtra("subtitle", subtitle)
        .putExtra("duration", duration)
        .putExtra("currentPosition", 0)
        .putExtra("action", "pause")
      );

    }
    @JavascriptInterface
    public void bgStop() {
      isPlaying = false;

      stopService(new Intent(getApplicationContext(), ForegroundService.class));

    }
    @JavascriptInterface
    public void bgPause(long ct) {

      getApplicationContext().sendBroadcast(new Intent("UPDATE_NOTIFICATION")
        .putExtra("icon", icon)
        .putExtra("title", title)
        .putExtra("subtitle", subtitle)
        .putExtra("duration", duration)
        .putExtra("currentPosition", ct)
        .putExtra("action", "pause")
      );

    }
    @JavascriptInterface
    public void bgPlay(long ct) {

      getApplicationContext().sendBroadcast(new Intent("UPDATE_NOTIFICATION")
        .putExtra("icon", icon)
        .putExtra("title", title)
        .putExtra("subtitle", subtitle)
        .putExtra("duration", duration)
        .putExtra("currentPosition", ct)
        .putExtra("action", "play")
      );

    }
    @JavascriptInterface
    public void bgBuffer(long ct) {

      getApplicationContext().sendBroadcast(new Intent("UPDATE_NOTIFICATION")
        .putExtra("icon", icon)
        .putExtra("title", title)
        .putExtra("subtitle", subtitle)
        .putExtra("duration", duration)
        .putExtra("currentPosition", ct)
        .putExtra("action", "buffer")
      );

    }
    @JavascriptInterface
    public String getAllCookies(String url) {
      String cookies = CookieManager.getInstance().getCookie(url);
      return cookies;
    }
    @JavascriptInterface
    public void pipvid(String x) {
      if (android.os.Build.VERSION.SDK_INT >= 26) {
        try {
          PictureInPictureParams params;
          if (x.equals("portrait")) {
            params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(9, 16)).build();
          } else {
            params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build();
          }
          enterPictureInPictureMode(params);
        } catch (IllegalStateException e) {
          e.printStackTrace();
        }

      } else {
        //   Toast.makeText(getApplicationContext(), getString(R.string.no_pip), Toast.LENGTH_SHORT).show();
      }
    }
  }

  public void setReceiver() {
    broadcastReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String action = intent.getExtras().getString("actionname");

        Log.e("Action MainActivity", action);

        switch (action) {
        case "PLAY_ACTION":
          web.evaluateJavascript("if (window.__nativePlay) { window.__nativePlay(); } else if (window.__mediaSessionHandlers && window.__mediaSessionHandlers.play) { window.__mediaSessionHandlers.play(); } else if (window.__currentAudio) { window.__currentAudio.play(); }", null);
          break;
        case "PAUSE_ACTION":
          web.evaluateJavascript("if (window.__nativePause) { window.__nativePause(); } else if (window.__mediaSessionHandlers && window.__mediaSessionHandlers.pause) { window.__mediaSessionHandlers.pause(); } else if (window.__currentAudio) { window.__currentAudio.pause(); }", null);
          break;
        case "NEXT_ACTION":
          web.evaluateJavascript("if (window.__nativeNext) { window.__nativeNext(); } else if (window.__mediaSessionHandlers && window.__mediaSessionHandlers.nexttrack) { window.__mediaSessionHandlers.nexttrack(); }", null);
          break;
        case "PREV_ACTION":
          web.evaluateJavascript("if (window.__nativePrev) { window.__nativePrev(); } else if (window.__mediaSessionHandlers && window.__mediaSessionHandlers.previoustrack) { window.__mediaSessionHandlers.previoustrack(); }", null);
          break;
        case "SEEKTO":
          try {
            long posMs = Long.parseLong(intent.getExtras().getString("pos"));
            double posSec = posMs / 1000.0;
            web.evaluateJavascript("if (window.__nativeSeekTo) { window.__nativeSeekTo(" + posSec + "); } else if (window.__mediaSessionHandlers && window.__mediaSessionHandlers.seekto) { window.__mediaSessionHandlers.seekto({ seekTime: " + posSec + " }); } else if (window.__currentAudio) { window.__currentAudio.currentTime = " + posSec + "; }", null);
          } catch (Exception e) {
            Log.e("MainActivity", "Error seeking: " + e.getMessage());
          }
          break;
        }

      }
    };

    if (Build.VERSION.SDK_INT >= 34 && getApplicationInfo().targetSdkVersion >= 34) {
      registerReceiver(broadcastReceiver, new IntentFilter("TRACKS_TRACKS"), RECEIVER_EXPORTED);
    } else {
      registerReceiver(broadcastReceiver, new IntentFilter("TRACKS_TRACKS"));
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

      Intent intent = new Intent(getApplicationContext(), ForegroundService.class);

      stopService(intent);

    unregisterReceiver(broadcastReceiver);
  }

}
