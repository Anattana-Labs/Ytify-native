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
  private HttpServer httpServer;
  private static java.lang.ref.WeakReference<MainActivity> sInstance = null;
  private static long sLastNoticeTime = 0;

  public static void notifyLocalServerUsed() {
    MainActivity act = sInstance != null ? sInstance.get() : null;
    if (act != null) {
      act.showNativeLocalServerUsed();
    }
  }

  public void showNativeLocalServerUsed() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        try {
          long now = System.currentTimeMillis();
          if (now - sLastNoticeTime < 2500) {
            return;
          }
          sLastNoticeTime = now;

          ViewGroup root = findViewById(android.R.id.content);
          if (root == null) {
            Toast.makeText(MainActivity.this, "Local server used", Toast.LENGTH_SHORT).show();
            return;
          }

          View oldNotice = root.findViewWithTag("native_local_notice");
          if (oldNotice != null) root.removeView(oldNotice);

          LinearLayout pill = new LinearLayout(MainActivity.this);
          pill.setTag("native_local_notice");
          pill.setOrientation(LinearLayout.HORIZONTAL);
          pill.setGravity(Gravity.CENTER_VERTICAL);

          final float density = getResources().getDisplayMetrics().density;
          int padH = (int) (14 * density);
          int padV = (int) (8 * density);
          pill.setPadding(padH, padV, padH, padV);

          android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
          bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
          bg.setColor(Color.parseColor("#181825"));
          bg.setCornerRadius(999 * density);
          bg.setStroke((int) (1.2f * density), Color.parseColor("#a6e3a1"));
          pill.setBackground(bg);
          pill.setElevation(8 * density);

          View tickIcon = new View(MainActivity.this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path path = new Path();

            {
              paint.setColor(Color.parseColor("#a6e3a1"));
              paint.setStyle(Paint.Style.STROKE);
              paint.setStrokeWidth(2.2f * density);
              paint.setStrokeCap(Paint.Cap.ROUND);
              paint.setStrokeJoin(Paint.Join.ROUND);
            }

            @Override
            protected void onDraw(Canvas canvas) {
              super.onDraw(canvas);
              float w = getWidth();
              float h = getHeight();
              path.reset();
              path.moveTo(w * 0.18f, h * 0.54f);
              path.lineTo(w * 0.42f, h * 0.78f);
              path.lineTo(w * 0.82f, h * 0.24f);
              canvas.drawPath(path, paint);
            }
          };
          int iconSize = (int) (16 * density);
          LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
          iconParams.setMarginEnd((int) (8 * density));
          tickIcon.setLayoutParams(iconParams);
          pill.addView(tickIcon);

          TextView tv = new TextView(MainActivity.this);
          tv.setText("Local server used");
          tv.setTextColor(Color.parseColor("#cdd6f4"));
          tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
          tv.setTypeface(Typeface.DEFAULT_BOLD);
          pill.addView(tv);

          FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
              ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
          params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
          params.bottomMargin = (int) (86 * density);
          pill.setLayoutParams(params);

          pill.setAlpha(0f);
          pill.setTranslationY(16 * density);
          root.addView(pill);

          pill.animate()
              .alpha(1f)
              .translationY(0f)
              .setDuration(220)
              .withEndAction(new Runnable() {
                @Override
                public void run() {
                  pill.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                      pill.animate()
                          .alpha(0f)
                          .translationY(16 * density)
                          .setDuration(220)
                          .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                              root.removeView(pill);
                            }
                          })
                          .start();
                    }
                  }, 2400);
                }
              })
              .start();

        } catch (Exception e) {
          Toast.makeText(MainActivity.this, "Local server used", Toast.LENGTH_SHORT).show();
        }
      }
    });
  }

  public void showOnboardingOverlay() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        try {
          final ViewGroup root = findViewById(android.R.id.content);
          if (root == null) return;
          View existing = root.findViewWithTag("onboarding_overlay");
          if (existing != null) return;

          final View onboardingView = getLayoutInflater().inflate(R.layout.onboarding_view, root, false);
          onboardingView.setTag("onboarding_overlay");
          Button btnContinue = onboardingView.findViewById(R.id.btn_continue);
          btnContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              SharedPreferences prefs = getSharedPreferences("ytify", MODE_PRIVATE);
              prefs.edit().putBoolean("onboarding_completed", true).apply();
              onboardingView.animate()
                  .alpha(0f)
                  .translationY(30f)
                  .setDuration(240)
                  .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                      root.removeView(onboardingView);
                    }
                  })
                  .start();
            }
          });
          root.addView(onboardingView);
        } catch (Exception e) {
          Log.e("MainActivity", "Error showing onboarding: " + e.getMessage(), e);
        }
      }
    });
  }

  // Start HTTP server
  private void startHttpServer() {
    try {
      if (httpServer == null) {
        httpServer = new HttpServer();
      }
      if (!httpServer.isRunning()) {
        httpServer.start();
        Log.i("MainActivity", "HTTP Server started on port " + httpServer.getPort());
      }
    } catch (Exception e) {
      Log.e("MainActivity", "Error starting HTTP server: " + e.getMessage(), e);
    }
  }

  private static final String JS_STREAM_INTERCEPTOR =
      "(function() {\n" +
      "  if (window.__ytify_fetch_interceptor_installed) return;\n" +
      "  window.__ytify_fetch_interceptor_installed = true;\n" +
      "  var LOCAL_PORT = 9999;\n" +
      "  var LOCAL_URL = 'http://127.0.0.1:' + LOCAL_PORT;\n" +
      "  var origFetch = window.fetch;\n" +
      "  window.fetch = async function(resource, init) {\n" +
      "    try {\n" +
      "      var urlStr = '';\n" +
      "      if (typeof resource === 'string') {\n" +
      "        urlStr = resource;\n" +
      "      } else if (resource && resource.url) {\n" +
      "        urlStr = resource.url;\n" +
      "      }\n" +
      "      var match = urlStr.match(/(?:\\/api\\/v1\\/videos\\/|\\/s\\/)([a-zA-Z0-9_-]{11})(?:[/?#]|$)/);\n" +
      "      if (match && match[1] && !urlStr.includes(':' + LOCAL_PORT)) {\n" +
      "        var videoId = match[1];\n" +
      "        window.__currentVideoId = videoId;\n" +
      "        console.log('[YtifyNative] Intercepted stream request for videoId:', videoId);\n" +
      "        try {\n" +
      "          var localReqUrl = LOCAL_URL + '/api/v1/videos/' + videoId;\n" +
      "          var localRes = await origFetch(localReqUrl, {\n" +
      "            method: 'GET',\n" +
      "            headers: { 'Accept': 'application/json' },\n" +
      "            signal: init && init.signal ? init.signal : undefined\n" +
      "          });\n" +
      "          if (localRes && localRes.ok) {\n" +
      "            console.log('[YtifyNative] Successfully resolved stream from localhost:9999 for:', videoId);\n" +
      "            if (typeof Android !== 'undefined' && Android.onLocalServerUsed) {\n" +
      "              Android.onLocalServerUsed();\n" +
      "            }\n" +
      "            return localRes;\n" +
      "          }\n" +
      "          console.warn('[YtifyNative] Localhost:9999 returned status ' + (localRes ? localRes.status : 'null') + ', falling back to remote...');\n" +
      "        } catch (localErr) {\n" +
      "          console.warn('[YtifyNative] Localhost:9999 failed:', localErr, ', falling back to remote...');\n" +
      "        }\n" +
      "      }\n" +
      "    } catch (e) {\n" +
      "      console.error('[YtifyNative] Error in fetch interceptor:', e);\n" +
      "    }\n" +
      "    return origFetch.apply(this, arguments);\n" +
      "  };\n" +
      "  try {\n" +
      "    var origVideoSrcDesc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');\n" +
      "    if (origVideoSrcDesc && origVideoSrcDesc.set) {\n" +
      "      var origSetSrc = origVideoSrcDesc.set;\n" +
      "      origVideoSrcDesc.set = function(val) {\n" +
      "        if (this instanceof HTMLVideoElement && typeof val === 'string' && (val.endsWith('/Resolution') || val === 'Resolution')) {\n" +
      "          var sel = document.querySelector('.watcher select');\n" +
      "          if (sel && sel.options) {\n" +
      "            for (var i = 0; i < sel.options.length; i++) {\n" +
      "              var optVal = sel.options[i].value;\n" +
      "              if (optVal && optVal.startsWith('http')) {\n" +
      "                val = optVal;\n" +
      "                sel.selectedIndex = i;\n" +
      "                break;\n" +
      "              }\n" +
      "            }\n" +
      "          }\n" +
      "        }\n" +
      "        return origSetSrc.call(this, val);\n" +
      "      };\n" +
      "      Object.defineProperty(HTMLMediaElement.prototype, 'src', origVideoSrcDesc);\n" +
      "    }\n" +
      "  } catch (e) {}\n" +
      "  console.log('[YtifyNative] Stream interceptor installed: primary=localhost:9999');\n" +
      "})();\n";

  private static final String JS_MEDIA_SCRIPT =
      "(function() {\n" +
      "  if (window.__ytify_native_injected) return;\n" +
      "  window.__ytify_native_injected = true;\n" +
      "  window.__mediaSessionHandlers = window.__mediaSessionHandlers || {};\n" +
      "  window.__currentAudio = window.__currentAudio || null;\n" +
      "  window.__currentMediaData = window.__currentMediaData || {\n" +
      "    title: '', artist: '', album: '', artwork: '', duration: 0, position: 0, state: 'playing'\n" +
      "  };\n" +
      "  function cleanText(str) {\n" +
      "    if (!str) return '';\n" +
      "    var s = ('' + str).replace(/\\s*-\\s*ytify\\s*$/i, '').trim();\n" +
      "    if (s.toLowerCase() === 'ytify' || s.toLowerCase() === 'free music streaming') return '';\n" +
      "    return s;\n" +
      "  }\n" +
      "  function getVideoId() {\n" +
      "    try {\n" +
      "      var titleEl = document.querySelector('.mediaDetails #title, .miniplayer .mediaDetails #title, #title, a[href*=\"watch?v=\"]');\n" +
      "      if (titleEl && titleEl.href) {\n" +
      "        var match = titleEl.href.match(/[?&]v=([a-zA-Z0-9_-]{11})/);\n" +
      "        if (match && match[1]) {\n" +
      "          window.__currentVideoId = match[1];\n" +
      "          return match[1];\n" +
      "        }\n" +
      "      }\n" +
      "      var m = window.location.href.match(/[?&]v=([a-zA-Z0-9_-]{11})/);\n" +
      "      if (m && m[1]) {\n" +
      "        window.__currentVideoId = m[1];\n" +
      "        return m[1];\n" +
      "      }\n" +
      "      if (window.__currentVideoId) return window.__currentVideoId;\n" +
      "    } catch (e) {}\n" +
      "    return '';\n" +
      "  }\n" +
      "  function getFallbackArtwork() {\n" +
      "    var v = getVideoId();\n" +
      "    if (v) return 'https://i.ytimg.com/vi/' + v + '/hqdefault.jpg';\n" +
      "    return '';\n" +
      "  }\n" +
      "  function sendToAndroid() {\n" +
      "    try {\n" +
      "      if (typeof Android === 'undefined' || !Android.updateMedia) return;\n" +
      "      var d = window.__currentMediaData;\n" +
      "      if (!d.title) return;\n" +
      "      var titleClean = cleanText(d.title);\n" +
      "      if (!titleClean || titleClean.toLowerCase() === 'ytify' || titleClean.toLowerCase() === 'free music streaming') return;\n" +
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
      "      var state = d.state === 'paused' ? 'paused' : 'playing';\n" +
      "      var artwork = d.artwork || '';\n" +
      "      if (!artwork || artwork.startsWith('blob:') || artwork.startsWith('data:')) {\n" +
      "        artwork = getFallbackArtwork();\n" +
      "      }\n" +
      "      var artistClean = d.artist || getDomArtist() || '';\n" +
      "      Android.updateMedia(\n" +
      "        titleClean,\n" +
      "        artistClean,\n" +
      "        d.album || '',\n" +
      "        artwork,\n" +
      "        Math.round(dur || 0),\n" +
      "        Math.round(pos || 0),\n" +
      "        state\n" +
      "      );\n" +
      "    } catch (e) { console.error('Error sending media to Android:', e); }\n" +
      "  }\n" +
      "  function getDomArtist() {\n" +
      "    try {\n" +
      "      var el = document.querySelector('.mediaDetails #author, #author, .author, .miniplayer #author');\n" +
      "      if (el) {\n" +
      "        var text = cleanText(el.innerText || el.textContent || '');\n" +
      "        if (text) return text;\n" +
      "      }\n" +
      "    } catch (e) {}\n" +
      "    return '';\n" +
      "  }\n" +
      "  function applyArtwork(src) {\n" +
      "    var fb = getFallbackArtwork();\n" +
      "    if (!src || src.startsWith('blob:') || src.startsWith('data:')) {\n" +
      "      window.__currentMediaData.artwork = fb;\n" +
      "    } else {\n" +
      "      window.__currentMediaData.artwork = src;\n" +
      "    }\n" +
      "    sendToAndroid();\n" +
      "  }\n" +
      "  function handleMetadataUpdate(meta) {\n" +
      "    if (!meta) return;\n" +
      "    var title = cleanText(meta.title);\n" +
      "    if (!title) return;\n" +
      "    var artist = cleanText(meta.artist);\n" +
      "    if (!artist) {\n" +
      "      artist = getDomArtist();\n" +
      "    }\n" +
      "    var album = cleanText(meta.album);\n" +
      "    var artwork = '';\n" +
      "    if (meta.artwork && meta.artwork.length > 0) {\n" +
      "      artwork = meta.artwork[meta.artwork.length - 1].src || '';\n" +
      "    }\n" +
      "    if (!artwork) {\n" +
      "      try {\n" +
      "        var mini = document.querySelector('.miniplayer img, img[alt^=\"Media Artwork for\"]');\n" +
      "        if (mini && mini.src) artwork = mini.src;\n" +
      "      } catch (e) {}\n" +
      "    }\n" +
      "    window.__currentMediaData.title = title;\n" +
      "    window.__currentMediaData.artist = artist || getDomArtist();\n" +
      "    window.__currentMediaData.album = album;\n" +
      "    window.__currentMediaData.artwork = '';\n" +
      "    if (window.__currentMediaData.state !== 'paused') {\n" +
      "      window.__currentMediaData.state = 'playing';\n" +
      "    }\n" +
      "    applyArtwork(artwork);\n" +
      "  }\n" +
      "  function checkFallbackMetadata() {\n" +
      "    var domArtist = getDomArtist();\n" +
      "    if (domArtist && !window.__currentMediaData.artist) {\n" +
      "      window.__currentMediaData.artist = domArtist;\n" +
      "    }\n" +
      "    if (!window.__currentMediaData.title && document.title) {\n" +
      "      var t = cleanText(document.title);\n" +
      "      if (t && t.toLowerCase() !== 'ytify' && t.toLowerCase() !== 'free music streaming') {\n" +
      "        window.__currentMediaData.title = t;\n" +
      "        var mini = document.querySelector('.miniplayer img, img[alt^=\"Media Artwork for\"]');\n" +
      "        if (mini && mini.src) {\n" +
      "          applyArtwork(mini.src);\n" +
      "        } else {\n" +
      "          var fb = getFallbackArtwork();\n" +
      "          if (fb) window.__currentMediaData.artwork = fb;\n" +
      "          sendToAndroid();\n" +
      "        }\n" +
      "      }\n" +
      "    } else if (domArtist && window.__currentMediaData.title) {\n" +
      "      sendToAndroid();\n" +
      "    }\n" +
      "  }\n" +
      "  function hookAudio(audio) {\n" +
      "    if (!audio || audio.__hooked) return;\n" +
      "    audio.__hooked = true;\n" +
      "    window.__currentAudio = audio;\n" +
      "    audio.addEventListener('play', function() {\n" +
      "      window.__currentAudio = audio;\n" +
      "      window.__currentMediaData.state = 'playing';\n" +
      "      if (audio.duration && !isNaN(audio.duration)) {\n" +
      "        window.__currentMediaData.duration = audio.duration * 1000;\n" +
      "      }\n" +
      "      if (navigator.mediaSession && navigator.mediaSession.metadata) {\n" +
      "        handleMetadataUpdate(navigator.mediaSession.metadata);\n" +
      "      } else {\n" +
      "        checkFallbackMetadata();\n" +
      "        sendToAndroid();\n" +
      "      }\n" +
      "    });\n" +
      "    audio.addEventListener('pause', function() {\n" +
      "      window.__currentAudio = audio;\n" +
      "      window.__currentMediaData.state = 'paused';\n" +
      "      window.__currentMediaData.position = (audio.currentTime || 0) * 1000;\n" +
      "      sendToAndroid();\n" +
      "    });\n" +
      "    audio.addEventListener('ended', function() {\n" +
      "      window.__currentAudio = audio;\n" +
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
      "  try {\n" +
      "    var origPlay = HTMLMediaElement.prototype.play;\n" +
      "    HTMLMediaElement.prototype.play = function() {\n" +
      "      hookAudio(this);\n" +
      "      window.__currentAudio = this;\n" +
      "      window.__currentMediaData.state = 'playing';\n" +
      "      if (navigator.mediaSession && navigator.mediaSession.metadata) {\n" +
      "        handleMetadataUpdate(navigator.mediaSession.metadata);\n" +
      "      } else {\n" +
      "        checkFallbackMetadata();\n" +
      "        sendToAndroid();\n" +
      "      }\n" +
      "      return origPlay.apply(this, arguments);\n" +
      "    };\n" +
      "    var origPause = HTMLMediaElement.prototype.pause;\n" +
      "    HTMLMediaElement.prototype.pause = function() {\n" +
      "      hookAudio(this);\n" +
      "      window.__currentAudio = this;\n" +
      "      window.__currentMediaData.state = 'paused';\n" +
      "      sendToAndroid();\n" +
      "      return origPause.apply(this, arguments);\n" +
      "    };\n" +
      "  } catch (e) {}\n" +
      "  try {\n" +
      "    var OrigAudio = window.Audio;\n" +
      "    if (OrigAudio) {\n" +
      "      window.Audio = function(...args) {\n" +
      "        var a = new OrigAudio(...args);\n" +
      "        hookAudio(a);\n" +
      "        return a;\n" +
      "      };\n" +
      "      window.Audio.prototype = OrigAudio.prototype;\n" +
      "    }\n" +
      "  } catch (e) {}\n" +
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
      "    try {\n" +
      "      var msProto = (window.MediaSession && window.MediaSession.prototype) || Object.getPrototypeOf(ms);\n" +
      "      if (msProto) {\n" +
      "        var desc = Object.getOwnPropertyDescriptor(msProto, 'metadata');\n" +
      "        if (desc && desc.set) {\n" +
      "          var origSet = desc.set;\n" +
      "          Object.defineProperty(msProto, 'metadata', {\n" +
      "            configurable: true,\n" +
      "            enumerable: true,\n" +
      "            get: desc.get,\n" +
      "            set: function(val) {\n" +
      "              origSet.call(this, val);\n" +
      "              try { handleMetadataUpdate(val); } catch (e) {}\n" +
      "            }\n" +
      "          });\n" +
      "        }\n" +
      "        var pbDesc = Object.getOwnPropertyDescriptor(msProto, 'playbackState');\n" +
      "        if (pbDesc && pbDesc.set) {\n" +
      "          var origPbSet = pbDesc.set;\n" +
      "          Object.defineProperty(msProto, 'playbackState', {\n" +
      "            configurable: true,\n" +
      "            enumerable: true,\n" +
      "            get: pbDesc.get,\n" +
      "            set: function(val) {\n" +
      "              origPbSet.call(this, val);\n" +
      "              if (val === 'playing') {\n" +
      "                window.__currentMediaData.state = 'playing';\n" +
      "                sendToAndroid();\n" +
      "              } else if (val === 'paused') {\n" +
      "                window.__currentMediaData.state = 'paused';\n" +
      "                sendToAndroid();\n" +
      "              }\n" +
      "            }\n" +
      "          });\n" +
      "        }\n" +
      "      }\n" +
      "    } catch (e) {}\n" +
      "  }\n" +
      "  setInterval(function() {\n" +
      "    try {\n" +
      "      var domTitleEl = document.querySelector('.mediaDetails #title, .miniplayer .mediaDetails #title');\n" +
      "      var domTitle = domTitleEl ? cleanText(domTitleEl.innerText || domTitleEl.textContent || '') : '';\n" +
      "      var domArtist = getDomArtist();\n" +
      "      if (domTitle && (domTitle !== window.__lastSeenTitle || (domArtist && domArtist !== window.__lastSeenArtist))) {\n" +
      "        window.__lastSeenTitle = domTitle;\n" +
      "        window.__lastSeenArtist = domArtist;\n" +
      "        getVideoId();\n" +
      "        if (navigator.mediaSession && navigator.mediaSession.metadata && cleanText(navigator.mediaSession.metadata.title) === domTitle) {\n" +
      "          handleMetadataUpdate(navigator.mediaSession.metadata);\n" +
      "        } else {\n" +
      "          window.__currentMediaData.title = domTitle;\n" +
      "          window.__currentMediaData.artist = domArtist;\n" +
      "          applyArtwork('');\n" +
      "        }\n" +
      "      } else if (navigator.mediaSession && navigator.mediaSession.metadata) {\n" +
      "        var m = navigator.mediaSession.metadata;\n" +
      "        var art = (m.artwork && m.artwork.length > 0) ? m.artwork[m.artwork.length - 1].src : '';\n" +
      "        if (m.title !== window.__lastSeenTitle || m.artist !== window.__lastSeenArtist) {\n" +
      "          window.__lastSeenTitle = m.title;\n" +
      "          window.__lastSeenArtist = m.artist;\n" +
      "          handleMetadataUpdate(m);\n" +
      "        }\n" +
      "      } else {\n" +
      "        checkFallbackMetadata();\n" +
      "      }\n" +
      "      if (window.__currentAudio) {\n" +
      "        if (!window.__currentAudio.paused && window.__currentMediaData.state !== 'playing') {\n" +
      "          window.__currentMediaData.state = 'playing';\n" +
      "          sendToAndroid();\n" +
      "        }\n" +
      "      }\n" +
      "    } catch (e) {}\n" +
      "  }, 800);\n" +
      "  window.__nativePlay = function() {\n" +
      "    if (window.__mediaSessionHandlers['play']) {\n" +
      "      try { window.__mediaSessionHandlers['play'](); return; } catch (e) {}\n" +
      "    }\n" +
      "    if (window.__currentAudio) {\n" +
      "      window.__currentAudio.play();\n" +
      "    }\n" +
      "  };\n" +
      "  window.__nativePause = function() {\n" +
      "    if (window.__mediaSessionHandlers['pause']) {\n" +
      "      try { window.__mediaSessionHandlers['pause'](); return; } catch (e) {}\n" +
      "    }\n" +
      "    if (window.__currentAudio) {\n" +
      "      window.__currentAudio.pause();\n" +
      "    }\n" +
      "  };\n" +
      "  window.__nativeNext = function() {\n" +
      "    if (window.__mediaSessionHandlers['nexttrack']) {\n" +
      "      try { window.__mediaSessionHandlers['nexttrack'](); return; } catch (e) {}\n" +
      "    }\n" +
      "    var btn = document.getElementById('playNextButton');\n" +
      "    if (btn) btn.click();\n" +
      "  };\n" +
      "  window.__nativePrev = function() {\n" +
      "    if (window.__mediaSessionHandlers['previoustrack']) {\n" +
      "      try { window.__mediaSessionHandlers['previoustrack'](); return; } catch (e) {}\n" +
      "    }\n" +
      "    var btn = document.getElementById('playPrevButton');\n" +
      "    if (btn) btn.click();\n" +
      "  };\n" +
      "  window.__nativeSeekTo = function(seconds) {\n" +
      "    if (window.__mediaSessionHandlers['seekto']) {\n" +
      "      try { window.__mediaSessionHandlers['seekto']({ seekTime: seconds }); return; } catch (e) {}\n" +
      "    }\n" +
      "    if (window.__currentAudio) {\n" +
      "      window.__currentAudio.currentTime = seconds;\n" +
      "    }\n" +
      "  };\n" +
      "})();";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    sInstance = new java.lang.ref.WeakReference<>(this);
    setContentView(R.layout.main);
    WebView.setWebContentsDebuggingEnabled(true);

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

    if (!prefs.getBoolean("onboarding_completed", false)) {
      showOnboardingOverlay();
    }

    MainActivity.this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  public void load(boolean dl) {

    web = findViewById(R.id.web);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setSupportZoom(true);
    web.getSettings().setBuiltInZoomControls(true);
    web.getSettings().setDisplayZoomControls(false);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setDatabaseEnabled(true);
    web.getSettings().setMediaPlaybackRequiresUserGesture(false); // Allow autoplay
    web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    web.addJavascriptInterface(new WebAppInterface(this), "Android");
    web.setWebChromeClient(new CustomWebClient());

    web.setWebViewClient(new WebViewClient() {
      @Override
      public void onPageStarted(WebView p1, String p2, Bitmap p3) {
        p1.evaluateJavascript(JS_STREAM_INTERCEPTOR, null);
        p1.evaluateJavascript(JS_MEDIA_SCRIPT, null);
        super.onPageStarted(p1, p2, p3);
      }

      @Override
      public void onPageFinished(WebView p1, String url) {
        p1.evaluateJavascript(JS_STREAM_INTERCEPTOR, null);
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
    public void onLocalServerUsed() {
      showNativeLocalServerUsed();
    }

    @JavascriptInterface
    public void showOnboarding() {
      showOnboardingOverlay();
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
      if (title == null) return;
      String cleanTitle = title.replaceAll("(?i)\\s*-\\s*ytify\\s*$", "").trim();
      if (cleanTitle.isEmpty() || cleanTitle.equalsIgnoreCase("ytify") || cleanTitle.equalsIgnoreCase("free music streaming")) {
        return;
      }
      isPlaying = !"paused".equalsIgnoreCase(state);
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

    if (sInstance != null && sInstance.get() == this) {
      sInstance = null;
    }

    if (httpServer != null && httpServer.isRunning()) {
      httpServer.stop();
    }

    Intent intent = new Intent(getApplicationContext(), ForegroundService.class);
    stopService(intent);

    unregisterReceiver(broadcastReceiver);
  }

}
