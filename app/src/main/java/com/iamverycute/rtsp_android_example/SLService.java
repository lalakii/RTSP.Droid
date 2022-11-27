package com.iamverycute.rtsp_android_example;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static androidx.core.app.NotificationManagerCompat.IMPORTANCE_MIN;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import com.pedro.rtspserver.RtspServer;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SLService extends Service implements Runnable, ConnectCheckerRtsp, MainActivity.OnScreenRecording, AudioEncoder.AudioDataCallback {

    private View floatView;
    private String rtsp_url;
    private Notification notify;
    private RemoteViews notifyView;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams windowLayoutParams;

    @Override
    public void onCreate() {
        super.onCreate();
        MainActivity.PutSlObj(this);
        svr = new RtspServer(this, 12345);
        rtsp_url = String.format("rtsp://%s:%s", svr.getServerIp(), svr.getPort());
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowLayoutParams = new WindowManager.LayoutParams() {
            {
                width = MATCH_PARENT;
                height = MATCH_PARENT;
                x = 0;
                y = 0;
                alpha = 0;
                gravity = Gravity.LEFT | Gravity.BOTTOM;
                type = TYPE_APPLICATION_OVERLAY;
                flags = FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_NO_LIMITS;
            }
        };
        floatView = new View(this);
        floatView.setBackgroundColor(Color.RED);
        notifyView = new RemoteViews(getPackageName(), R.layout.notify);
        notifyView.setOnClickPendingIntent(R.id.tv1, PendingIntent.getActivity(getApplicationContext(), 1, new Intent(getApplicationContext(), MainActivity.class), FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE));
        notify = new NotificationCompat.Builder(this, getString(R.string.channel_id)).setContentText(rtsp_url).setCustomContentView(notifyView).setWhen(System.currentTimeMillis()).setSmallIcon(android.R.drawable.presence_video_online).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true).build();
        NotificationManagerCompat.from(this).createNotificationChannel(new NotificationChannelCompat.Builder(notify.getChannelId(), IMPORTANCE_MIN).setName(getString(R.string.app_name)).setDescription(getString(R.string.app_name)).setSound(null, null).setLightsEnabled(false).setShowBadge(false).setVibrationEnabled(false).build());
        startForeground(1, notify, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }

    public void Success(MediaProjection pm, TextView tv) {
        this.pm = pm;
        tv.setText(rtsp_url);
        ShowNotify(rtsp_url);
        REC_YOUR_SCREEN();
    }

    @Override
    public void Granting() {
        ShowNotify("请授予屏幕录制权限");
    }

    @Override
    public void Dispose() {
        if (codecThread != null) {
            codecThread.interrupt();
        }
        if (audioEncoderThread != null) {
            audioEncoderThread.interrupt();
        }
        ShowNotify("已停止运行");
    }

    private void ShowNotify(String text) {
        notifyView.setTextViewText(R.id.tv1, text);
        startForeground(1, notify, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }

    private MediaProjection pm;
    private MediaCodec codec;
    private VirtualDisplay display;
    private Surface surface;
    private RtspServer svr;
    private Thread codecThread;
    private Thread audioEncoderThread;

    private void REC_YOUR_SCREEN() {
        if (Settings.canDrawOverlays(this)) {
            mWindowManager.addView(floatView, windowLayoutParams);
        }
        int width = 720;
        int height = 1280;
        MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height);
        videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 27);
        try {
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
        } catch (IOException ignored) {
        }
        codec.configure(videoFormat, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
        surface = codec.createInputSurface();
        display = pm.createVirtualDisplay("vd-9", width, height, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION, surface, null, null);
        svr.setLogs(false);
        svr.setStereo(false);
        svr.setAuth("", "");
        svr.startServer();

        codecThread = new Thread(this);
        codecThread.start();

        AudioPlaybackCaptureConfiguration.Builder audioConfig = new AudioPlaybackCaptureConfiguration.Builder(this.pm);
        audioConfig.addMatchingUsage(AudioAttributes.USAGE_MEDIA);
        audioConfig.addMatchingUsage(AudioAttributes.USAGE_GAME);
        audioConfig.addMatchingUsage(AudioAttributes.USAGE_UNKNOWN);
        audioConfig.addMatchingUsage(AudioAttributes.USAGE_ALARM);
        audioConfig.addMatchingUsage(AudioAttributes.USAGE_ASSISTANT);
        audioConfig.addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION);
        audioConfig.addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE);

        audioEncoderThread = new AudioEncoder(this, audioConfig.build());
        audioEncoderThread.start();
    }

    @Override
    public void OnAudio(ByteBuffer data, MediaCodec.BufferInfo info) {
        svr.sendAudio(data, info);
    }

    private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    @Override
    public void run() {
        codec.start();
        while (!Thread.currentThread().isInterrupted()) {
            int index = codec.dequeueOutputBuffer(bufferInfo, 0);
            if (index < 0) {
                floatView.postInvalidate();
                continue;
            }
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                csd0Handler(codec.getOutputFormat(index).getByteBuffer("csd-0").array());
            }
            svr.sendVideo(codec.getOutputBuffer(index), bufferInfo);
            codec.releaseOutputBuffer(index, false);
        }
        codec.stop();
        codec.reset();
        codec.release();
        if (surface != null) {
            surface.release();
        }
        if (display != null) {
            display.release();
        }
        if (pm != null) {
            pm.stop();
        }
        if (floatView.isAttachedToWindow())
            mWindowManager.removeView(floatView);
    }

    private void csd0Handler(byte[] csdArray) {
        int segment = 0;
        int vpsPosition = -1;
        int spsPosition = -1;
        int ppsPosition = -1;
        for (int i = 0; i < csdArray.length; i++) {
            if (segment == 3 && csdArray[i] == 1) {
                if (vpsPosition == -1) {
                    vpsPosition = i - segment;
                } else if (spsPosition == -1) {
                    spsPosition = i - segment;
                } else {
                    ppsPosition = i - segment;
                    break;
                }
            }
            segment = csdArray[i] == 0 ? segment + 1 : 0;
        }
        ByteBuffer vps = ByteBuffer.allocate(spsPosition).put(csdArray, 0, spsPosition);
        ByteBuffer sps = ByteBuffer.allocate(ppsPosition - spsPosition).put(csdArray, spsPosition, ppsPosition - spsPosition);
        ByteBuffer pps = ByteBuffer.allocate(csdArray.length - ppsPosition).put(csdArray, ppsPosition, csdArray.length - ppsPosition);
        svr.setVideoInfo(sps, pps, vps);
    }

    @Override
    public void onAuthErrorRtsp() {

    }

    @Override
    public void onAuthSuccessRtsp() {

    }

    @Override
    public void onConnectionFailedRtsp(@NonNull String s) {

    }

    @Override
    public void onConnectionStartedRtsp(@NonNull String s) {

    }

    @Override
    public void onConnectionSuccessRtsp() {

    }

    @Override
    public void onDisconnectRtsp() {

    }

    @Override
    public void onNewBitrateRtsp(long l) {

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}