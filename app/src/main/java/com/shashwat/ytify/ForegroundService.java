package com.shashwat.ytify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ForegroundService extends Service {

    public static final String CHANNEL_ID = "Media";
    public static final String ACTION_UPDATE_NOTIFICATION = "UPDATE_NOTIFICATION";
    private static final String TAG = "ForegroundService";
    private static final int NOTIFICATION_ID = 1;

    private NotificationManager notificationManager;
    private BroadcastReceiver updateReceiver;
    private MediaSession mediaSession;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentTitle = "";
    private String currentArtist = "";
    private String currentAlbum = "";
    private String currentArtworkUrl = "";
    private Bitmap cachedArtwork = null;
    private long currentDuration = 0;
    private long currentPosition = 0;
    private int currentPlaybackState = PlaybackState.STATE_NONE;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        initMediaSession();
        registerUpdateReceiver();
    }

    private void initMediaSession() {
        mediaSession = new MediaSession(getApplicationContext(), "MediaSession");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                broadcastTrackAction("PLAY_ACTION");
            }

            @Override
            public void onPause() {
                super.onPause();
                broadcastTrackAction("PAUSE_ACTION");
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                broadcastTrackAction("NEXT_ACTION");
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                broadcastTrackAction("PREV_ACTION");
            }

            @Override
            public void onSeekTo(long pos) {
                super.onSeekTo(pos);
                getApplicationContext().sendBroadcast(new Intent("TRACKS_TRACKS")
                        .putExtra("actionname", "SEEKTO")
                        .putExtra("pos", String.valueOf(pos)));
                getApplicationContext().sendBroadcast(new Intent("AUDIO_CONTROL")
                        .putExtra("action", "seekTo")
                        .putExtra("position", pos));
            }

            @Override
            public void onStop() {
                super.onStop();
                broadcastTrackAction("PAUSE_ACTION");
                stopForeground(true);
                stopSelf();
            }
        });

        mediaSession.setActive(true);
    }

    private void broadcastTrackAction(String action) {
        getApplicationContext().sendBroadcast(new Intent("TRACKS_TRACKS")
                .putExtra("actionname", action));
        getApplicationContext().sendBroadcast(new Intent("AUDIO_CONTROL")
                .putExtra("action", action.replace("_ACTION", "").toLowerCase()));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Media Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Media playback controls");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            handleIntent(intent);
        }
        return START_NOT_STICKY;
    }

    private String cleanText(String text) {
        if (text == null) return "";
        String res = text.replaceAll("(?i)\\s*-\\s*ytify\\s*$", "").trim();
        if (res.equalsIgnoreCase("ytify") || res.equalsIgnoreCase("free music streaming")) {
            return "";
        }
        return res;
    }

    private void handleIntent(Intent intent) {
        String title = intent.getStringExtra("title");
        String subtitle = intent.getStringExtra("subtitle");
        String album = intent.getStringExtra("album");
        String icon = intent.getStringExtra("icon");
        String action = intent.getStringExtra("action");
        long duration = intent.getLongExtra("duration", 0);
        long currentPosition = intent.getLongExtra("currentPosition", 0);

        String cleanTitle = title != null ? cleanText(title) : "";
        String cleanSubtitle = subtitle != null ? cleanText(subtitle) : "";
        String cleanAlbum = album != null ? cleanText(album) : "";

        boolean isNewSong = !cleanTitle.isEmpty() && !cleanTitle.equalsIgnoreCase(currentTitle);
        if (isNewSong) {
            currentTitle = cleanTitle;
            currentArtist = cleanSubtitle;
            currentAlbum = cleanAlbum;
            currentArtworkUrl = "";
            cachedArtwork = null;
        } else {
            if (!cleanTitle.isEmpty()) currentTitle = cleanTitle;
            if (!cleanSubtitle.isEmpty()) currentArtist = cleanSubtitle;
            if (!cleanAlbum.isEmpty()) currentAlbum = cleanAlbum;
        }

        if (duration > 0) {
            currentDuration = duration;
        }
        if (currentPosition >= 0) {
            this.currentPosition = currentPosition;
        }

        if ("pause".equalsIgnoreCase(action)) {
            currentPlaybackState = PlaybackState.STATE_PAUSED;
        } else if ("play".equalsIgnoreCase(action)) {
            currentPlaybackState = PlaybackState.STATE_PLAYING;
        } else {
            currentPlaybackState = PlaybackState.STATE_BUFFERING;
        }

        processNotification(icon);
    }

    private void registerUpdateReceiver() {
        updateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_UPDATE_NOTIFICATION.equals(intent.getAction())) {
                    handleIntent(intent);
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_UPDATE_NOTIFICATION);
        if (Build.VERSION.SDK_INT >= 34 && getApplicationInfo().targetSdkVersion >= 34) {
            registerReceiver(updateReceiver, filter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(updateReceiver, filter);
        }
    }

    private void processNotification(String iconUrl) {
        if (iconUrl != null && !iconUrl.isEmpty() && !iconUrl.startsWith("blob:") && !iconUrl.equals(currentArtworkUrl)) {
            currentArtworkUrl = iconUrl;
            renderNotificationAndSession();
            final String targetUrl = iconUrl;
            executor.execute(() -> {
                Bitmap loaded = loadBitmap(targetUrl);
                if (loaded != null && targetUrl.equals(currentArtworkUrl)) {
                    cachedArtwork = loaded;
                    mainHandler.post(this::renderNotificationAndSession);
                }
            });
        } else {
            renderNotificationAndSession();
        }
    }

    private Bitmap loadBitmap(String source) {
        if (source == null || source.trim().isEmpty() || source.startsWith("blob:")) return null;
        source = source.trim();
        try {
            if (source.startsWith("data:image") || source.startsWith("data:")) {
                int comma = source.indexOf(",");
                if (comma > 0) {
                    byte[] bytes = Base64.decode(source.substring(comma + 1), Base64.DEFAULT);
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                }
            } else if (!source.startsWith("http://") && !source.startsWith("https://")) {
                try {
                    byte[] bytes = Base64.decode(source, Base64.DEFAULT);
                    if (bytes != null && bytes.length > 0) {
                        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    }
                } catch (Exception ignored) {}
            }

            URL url = new URL(source);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoInput(true);
            conn.connect();

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null) {
                    return loadBitmap(newUrl);
                }
            }

            if (code >= 200 && code < 300) {
                try (InputStream is = conn.getInputStream();
                     java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, n);
                    }
                    byte[] imageBytes = baos.toByteArray();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    if (bitmap != null) {
                        Log.d(TAG, "Loaded artwork bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                    }
                    return bitmap;
                }
            } else {
                Log.e(TAG, "HTTP error " + code + " loading artwork: " + source);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load artwork: " + e.getMessage(), e);
        }
        return null;
    }

    private void renderNotificationAndSession() {
        Context cont = getApplicationContext();

        updateMediaSessionMetadata(currentTitle, currentArtist, currentAlbum, cachedArtwork, currentDuration);
        updatePlaybackState(currentPosition, currentPlaybackState);

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        Intent openAppIntent = new Intent(cont, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(cont, 0, openAppIntent, pendingIntentFlags);

        Intent prevIntent = new Intent(cont, NotificationActionService.class);
        prevIntent.setAction("PREV_ACTION");
        PendingIntent prevPendingIntent = PendingIntent.getBroadcast(cont, 1, prevIntent, pendingIntentFlags);

        Intent playIntent = new Intent(cont, NotificationActionService.class);
        playIntent.setAction("PLAY_ACTION");
        PendingIntent playPendingIntent = PendingIntent.getBroadcast(cont, 2, playIntent, pendingIntentFlags);

        Intent pauseIntent = new Intent(cont, NotificationActionService.class);
        pauseIntent.setAction("PAUSE_ACTION");
        PendingIntent pausePendingIntent = PendingIntent.getBroadcast(cont, 3, pauseIntent, pendingIntentFlags);

        Intent nextIntent = new Intent(cont, NotificationActionService.class);
        nextIntent.setAction("NEXT_ACTION");
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(cont, 4, nextIntent, pendingIntentFlags);

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        Notification.MediaStyle mediaStyle = new Notification.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2);

        builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(currentTitle.isEmpty() ? "Playing Audio" : currentTitle)
                .setContentText(currentArtist)
                .setContentIntent(openAppPendingIntent)
                .setStyle(mediaStyle)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(currentPlaybackState == PlaybackState.STATE_PLAYING);

        if (currentAlbum != null && !currentAlbum.isEmpty()) {
            builder.setSubText(currentAlbum);
        }

        if (cachedArtwork != null) {
            builder.setLargeIcon(cachedArtwork);
        }

        builder.addAction(R.drawable.ic_skip_previous_white, "Previous", prevPendingIntent);

        if (currentPlaybackState == PlaybackState.STATE_PLAYING) {
            builder.addAction(R.drawable.ic_pause_white, "Pause", pausePendingIntent);
        } else {
            builder.addAction(R.drawable.ic_play_arrow_white, "Play", playPendingIntent);
        }

        builder.addAction(R.drawable.ic_skip_next_white, "Next", nextPendingIntent);

        Notification notification = builder.build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void updateMediaSessionMetadata(String title, String artist, String album, Bitmap albumArt, long duration) {
        MediaMetadata.Builder metaBuilder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration);

        if (albumArt != null) {
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt);
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, albumArt);
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, albumArt);
        } else {
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, null);
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, null);
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, null);
        }

        mediaSession.setMetadata(metaBuilder.build());
    }

    private void updatePlaybackState(long currentPosition, int state) {
        long updateTime = SystemClock.elapsedRealtime();
        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SEEK_TO
                        | PlaybackState.ACTION_STOP)
                .setState(state, currentPosition, 1.0f, updateTime);

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (updateReceiver != null) {
            try {
                unregisterReceiver(updateReceiver);
            } catch (Exception ignored) {}
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        executor.shutdownNow();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
