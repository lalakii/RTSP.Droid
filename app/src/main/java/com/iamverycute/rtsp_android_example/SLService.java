package com.iamverycute.rtsp_android_example;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static androidx.core.app.NotificationManagerCompat.IMPORTANCE_MIN;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
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
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import com.pedro.rtspserver.RtspServer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author iamverycute
 */

public class SLService extends Service implements Runnable, ConnectCheckerRtsp, MainActivity.OnRecordingEvent {

    private RtspServer svr;
    private View floatView;
    private String rtsp_url;
    private Notification notify;
    private RemoteViews notifyView;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams windowLayoutParams;

    @Override
    public void onCreate() {
        super.onCreate();
        svr = new RtspServer(this, 12345);
        rtsp_url = String.format("rtsp://%s:%s", svr.getServerIp(), svr.getPort());
        svr.setLogs(false);
        svr.setStereo(true);
        svr.setAuth("", "");
        svr.startServer();

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
        if (hevcAACEncoderThread != null) {
            hevcAACEncoderThread.interrupt();
        }
        ShowNotify("已停止运行");
    }

    private void ShowNotify(String text) {
        notifyView.setTextViewText(R.id.tv1, text);
        startForeground(1, notify, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }

    private MediaProjection pm;
    private MediaCodec hevcEncoder;
    private MediaCodec aacEncoder;
    private VirtualDisplay display;
    private Surface surface;
    private Thread hevcAACEncoderThread;

    private void REC_YOUR_SCREEN() {
        if (Settings.canDrawOverlays(this)) {
            mWindowManager.addView(floatView, windowLayoutParams);
        }
        int width = 720;
        int height = 1280;
        MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height);
        videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
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
                csd0Handler(hevcEncoder.getOutputFormat(hevcOutputBufferIndex).getByteBuffer("csd-0").array());
            }
            svr.sendVideo(hevcEncoder.getOutputBuffer(hevcOutputBufferIndex), bufferInfo);
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
                ((ByteBuffer) aacEncoder.getInputBuffer(audioInputBufferIndex).clear()).put(audioRawByteBuffer);
                aacEncoder.queueInputBuffer(audioInputBufferIndex, 0, len, System.nanoTime() / 1000L, 0);
                int audioOutputBufferIndex;
                while ((audioOutputBufferIndex = aacEncoder.dequeueOutputBuffer(audioBufferInfo, 0)) > -1) {
                    this.svr.sendAudio(aacEncoder.getOutputBuffer(audioOutputBufferIndex), audioBufferInfo);
                    aacEncoder.releaseOutputBuffer(audioOutputBufferIndex, false);
                }
            }
        }
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
        return new SLBinder(this);
    }
}