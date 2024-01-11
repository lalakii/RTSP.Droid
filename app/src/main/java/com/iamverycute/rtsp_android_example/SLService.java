package com.iamverycute.rtsp_android_example;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.pedro.common.ConnectChecker;
import com.pedro.rtspserver.RtspServer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author iamverycute
 */

public class SLService extends Service implements Runnable, ConnectChecker, MainActivity.OnRecordingEvent {

    private RtspServer svr;
    private View floatView;
    private String rtsp_url;
    private Notification notify;
    private RemoteViews notifyView;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams layoutParams;

    @Override
    public void onCreate() {
        super.onCreate();
        svr = new RtspServer(this, 12345);
        rtsp_url = String.format("rtsp://%s:%s", svr.getServerIp(), svr.getPort());
        svr.setLogs(false);
        svr.setAudioInfo(32000, true);
        svr.setAuth("", "");
        svr.startServer();

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutParams = new WindowManager.LayoutParams() {
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
        NotificationManagerCompat.from(this).createNotificationChannel(new NotificationChannelCompat.Builder(notify.getChannelId(), IMPORTANCE_HIGH).setName(getString(R.string.app_name)).setDescription(getString(R.string.app_name)).setSound(null, null).setLightsEnabled(false).setShowBadge(false).setVibrationEnabled(false).build());
        startForeground(1, notify, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }

    public void StartRec(MediaProjectionManager mpm, Handler mHandler, int resultCode, Intent data, TextView tv) {
        this.pm = mpm.getMediaProjection(resultCode, data);
        this.pm.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
            }

            @Override
            public void onCapturedContentResize(int width, int height) {
                super.onCapturedContentResize(width, height);
            }

            @Override
            public void onCapturedContentVisibilityChanged(boolean isVisible) {
                super.onCapturedContentVisibilityChanged(isVisible);
            }
        }, mHandler);
        tv.setText(rtsp_url);
        ShowNotify(rtsp_url);
        REC_YOUR_SCREEN();
    }

    @Override
    public void Dispose() {
        if (hevcAACEncoderThread != null) {
            hevcAACEncoderThread.interrupt();
        }
        ShowNotify(getString(R.string.stopped));
    }

    private void ShowNotify(String text) {
        notifyView.setTextViewText(R.id.tv1, text);
        if (checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION) == PackageManager.PERMISSION_GRANTED)
            try {
                startForeground(1, notify, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } catch (SecurityException ignored) {
            }
    }

    private MediaProjection pm;
    private MediaCodec hevcEncoder;
    private MediaCodec aacEncoder;
    private VirtualDisplay display;
    private Surface surface;
    private Thread hevcAACEncoderThread;

    private void REC_YOUR_SCREEN() {
        if (Settings.canDrawOverlays(this)) {
            mWindowManager.addView(floatView, layoutParams);
        }
        int width = 720;
        int height = 1280;
        MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height);
        videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
        try {
            hevcEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
        } catch (IOException ignored) {
        }
        hevcEncoder.configure(videoFormat, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
        surface = hevcEncoder.createInputSurface();
        hevcEncoder.start();
        display = pm.createVirtualDisplay("vd-9", width, height, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION, surface, null, null);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            int sampleRate = 32000;
            int ENCODING = ENCODING_PCM_16BIT;
            int channelConfig = CHANNEL_IN_MONO;

            AudioPlaybackCaptureConfiguration audioConfig = new AudioPlaybackCaptureConfiguration.Builder(this.pm).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).addMatchingUsage(AudioAttributes.USAGE_ALARM).addMatchingUsage(AudioAttributes.USAGE_ASSISTANT).addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION).addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build();

            minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, ENCODING);
            //if u want record mic sound. replace setAudioPlaybackCaptureConfig  to  setAudioSource(MIC)
            audioRecord = new AudioRecord.Builder().setAudioPlaybackCaptureConfig(audioConfig).setAudioFormat(new AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(ENCODING).setChannelMask(channelConfig).build()).setBufferSizeInBytes(minBufferSize).build();
            MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelConfig);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 48000);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            try {
                aacEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            } catch (IOException ignored) {
            }
            aacEncoder.configure(audioFormat, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
            aacEncoder.start();
            audioRecord.startRecording();
        }

        hevcAACEncoderThread = new Thread(this);
        hevcAACEncoderThread.start();
    }

    private AudioRecord audioRecord;
    private int minBufferSize;

    private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    private final MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

    @Override
    public void run() {
        while (!hevcAACEncoderThread.isInterrupted()) {
            addAACEncoder();
            int hevcOutputBufferIndex = hevcEncoder.dequeueOutputBuffer(bufferInfo, 0);
            if (hevcOutputBufferIndex < 0) {
                floatView.postInvalidate();
                continue;
            }
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                ByteBuffer formatBuffer = hevcEncoder.getOutputFormat(hevcOutputBufferIndex).getByteBuffer("csd-0");
                if (formatBuffer != null) {
                    csd0Handler(formatBuffer.array());
                }
            }
            ByteBuffer sendBuffer = hevcEncoder.getOutputBuffer(hevcOutputBufferIndex);
            if (sendBuffer != null) {
                svr.sendVideo(sendBuffer, bufferInfo);
            }
            hevcEncoder.releaseOutputBuffer(hevcOutputBufferIndex, false);
        }
        releaseAll();
    }

    private void releaseAll() {
        hevcEncoder.stop();
        hevcEncoder.reset();
        hevcEncoder.release();
        if (aacEncoder != null) {
            aacEncoder.stop();
            aacEncoder.reset();
            aacEncoder.release();
        }
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }
        if (surface != null) {
            surface.release();
        }
        if (display != null) {
            display.release();
        }
        if (pm != null) {
            pm.stop();
        }
        if (floatView.isAttachedToWindow()) mWindowManager.removeView(floatView);
    }

    private void csd0Handler(byte[] csdBuf) {
        int segment = 0;
        int ppsPosition = -1;
        int spsPosition = -1;
        for (int i = csdBuf.length - 1; i > -1; i--) {
            if (segment == 3 && csdBuf[i + 4] == 1) {
                if (ppsPosition == -1) {
                    ppsPosition = i + 1;
                } else {
                    spsPosition = i + 1;
                    break;
                }
            }
            segment = csdBuf[i] == 0 ? segment + 1 : 0;
        }
        ByteBuffer vps = ByteBuffer.allocate(spsPosition).put(csdBuf, 0, spsPosition);
        ByteBuffer sps = ByteBuffer.allocate(ppsPosition - spsPosition).put(csdBuf, spsPosition, ppsPosition - spsPosition);
        ByteBuffer pps = ByteBuffer.allocate(csdBuf.length - ppsPosition).put(csdBuf, ppsPosition, csdBuf.length - ppsPosition);
        svr.setVideoInfo(sps, pps, vps);
    }

    private void addAACEncoder() {
        if (audioRecord != null) {
            ByteBuffer audioRawByteBuffer = ByteBuffer.allocateDirect(minBufferSize);
            int len = audioRecord.read(audioRawByteBuffer, minBufferSize, AudioRecord.READ_NON_BLOCKING);
            int audioInputBufferIndex = aacEncoder.dequeueInputBuffer(0);
            if (audioInputBufferIndex > -1) {
                ByteBuffer aacEncoderBuffer = aacEncoder.getInputBuffer(audioInputBufferIndex);
                if (aacEncoderBuffer != null) {
                    ((ByteBuffer) aacEncoderBuffer.clear()).put(audioRawByteBuffer);
                }
                aacEncoder.queueInputBuffer(audioInputBufferIndex, 0, len, System.nanoTime() / 1000L, 0);
                int audioOutputBufferIndex;
                while ((audioOutputBufferIndex = aacEncoder.dequeueOutputBuffer(audioBufferInfo, 0)) > -1) {
                    ByteBuffer audioBuffer = aacEncoder.getOutputBuffer(audioOutputBufferIndex);
                    if (audioBuffer != null) {
                        this.svr.sendAudio(audioBuffer, audioBufferInfo);
                    }
                    aacEncoder.releaseOutputBuffer(audioOutputBufferIndex, false);
                }
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new SLBinder(this);
    }

    @Override
    public void onAuthError() {

    }

    @Override
    public void onAuthSuccess() {

    }

    @Override
    public void onConnectionFailed(@NonNull String s) {

    }

    @Override
    public void onConnectionStarted(@NonNull String s) {

    }

    @Override
    public void onConnectionSuccess() {

    }

    @Override
    public void onDisconnect() {

    }

    @Override
    public void onNewBitrate(long l) {

    }
}