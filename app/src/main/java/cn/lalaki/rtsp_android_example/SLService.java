package cn.lalaki.rtsp_android_example;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH;

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

    private RtspServer mRtspServer;
    private View mFloatView;
    private String mRtspUrl;
    private Notification mNotify;
    private RemoteViews mNotifyView;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayoutParams;
    private MediaProjection mMediaProjection;
    private MediaCodec mHevcEncoder;
    private MediaCodec mAacEncoder;
    private VirtualDisplay mVirtualDisplay;
    private Surface mSurface;
    private Thread hevcAACEncoderThread;
    private AudioRecord mAudioRecord;
    private int minBufferSize;
    private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private final MediaCodec.BufferInfo mAudioBufferInfo = new MediaCodec.BufferInfo();
    private TextView mRtspUrlView;

    @Override
    public void onCreate() {
        super.onCreate();
        mRtspServer = new RtspServer(this, 12345);
        mRtspServer.setLogs(false);
        mRtspUrl = String.format("rtsp://%s:%s", mRtspServer.getServerIp(), mRtspServer.getPort());
        mRtspServer.setAudioInfo(32000, false);
        mRtspServer.setAuth("", "");
        mRtspServer.startServer();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mLayoutParams = new WindowManager.LayoutParams() {
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
        mFloatView = new View(this);
        mFloatView.setBackgroundColor(Color.RED);
        mNotifyView = new RemoteViews(getPackageName(), R.layout.notify);
        mNotifyView.setOnClickPendingIntent(R.id.tv1, PendingIntent.getActivity(getApplicationContext(), 1, new Intent(getApplicationContext(), MainActivity.class), FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE));
        mNotify = new NotificationCompat.Builder(this, getString(R.string.channel_id)).setContentText(mRtspUrl).setCustomContentView(mNotifyView).setWhen(System.currentTimeMillis()).setSmallIcon(android.R.drawable.presence_video_online).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true).build();
        NotificationManagerCompat.from(this).createNotificationChannel(new NotificationChannelCompat.Builder(mNotify.getChannelId(), IMPORTANCE_HIGH).setName(getString(R.string.app_name)).setDescription(getString(R.string.app_name)).setSound(null, null).setLightsEnabled(false).setShowBadge(false).setVibrationEnabled(false).build());
        startForeground(1, mNotify, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }

    public void StartRec(MediaProjectionManager mmMediaProjection, Handler mHandler, int resultCode, Intent data, TextView tv) {
        this.mMediaProjection = mmMediaProjection.getMediaProjection(resultCode, data);
        this.mMediaProjection.registerCallback(new MediaProjection.Callback() {
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
        this.mRtspUrlView = tv;
        tv.setText(mRtspUrl);
        ShowNotify(mRtspUrl, true);
        REC_YOUR_SCREEN();
    }

    @Override
    public void Dispose() {
        mRtspUrlView.setText("");
        if (hevcAACEncoderThread != null) {
            hevcAACEncoderThread.interrupt();
        }
        ShowNotify(getString(R.string.stopped), false);
    }

    private void ShowNotify(String text, boolean state) {
        mNotifyView.setTextViewText(R.id.tv1, text);
        mNotifyView.setTextColor(R.id.tv1, getColor(state ? android.R.color.holo_green_dark : android.R.color.darker_gray));
        if (checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION) == PackageManager.PERMISSION_GRANTED) {
            try {
                startForeground(1, mNotify, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } catch (SecurityException ignored) {
            }
        }
    }

    private void REC_YOUR_SCREEN() {
        if (Settings.canDrawOverlays(this) && !mFloatView.isAttachedToWindow()) {
            mWindowManager.addView(mFloatView, mLayoutParams);
        }
        int width = 720;
        int height = 1280;
        MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height);
        videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
        try {
            mHevcEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
        } catch (IOException ignored) {
        }
        mHevcEncoder.configure(videoFormat, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
        mSurface = mHevcEncoder.createInputSurface();
        mHevcEncoder.start();
        try {
            mVirtualDisplay = mMediaProjection.createVirtualDisplay("vd-9", width, height, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION, mSurface, null, null);
        } catch (SecurityException ignored) {
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            int sampleRate = 32000;
            int ENCODING = ENCODING_PCM_16BIT;
            int channelConfig = CHANNEL_IN_MONO;
            AudioPlaybackCaptureConfiguration audioConfig = new AudioPlaybackCaptureConfiguration.Builder(this.mMediaProjection).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).addMatchingUsage(AudioAttributes.USAGE_ALARM).addMatchingUsage(AudioAttributes.USAGE_ASSISTANT).addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION).addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build();
            minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, ENCODING);
            //if u want record mic sound. replace setAudioPlaybackCaptureConfig  to  setAudioSource(MIC)
            mAudioRecord = new AudioRecord.Builder().setAudioPlaybackCaptureConfig(audioConfig).setAudioFormat(new AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(ENCODING).setChannelMask(channelConfig).build()).setBufferSizeInBytes(minBufferSize).build();
            MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelConfig);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 48000);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            try {
                mAacEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            } catch (IOException ignored) {
            }
            mAacEncoder.configure(audioFormat, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
            mAacEncoder.start();
            try {
                mAudioRecord.startRecording();
            } catch (IllegalStateException ignored) {
            }
        }
        hevcAACEncoderThread = new Thread(this);
        hevcAACEncoderThread.start();
    }

    @Override
    public void run() {
        long beginTime;
        beginTime = System.nanoTime();
        while (!hevcAACEncoderThread.isInterrupted()) {
            long timestamp = (System.nanoTime() - beginTime) / 1000L;
            addAACEncoder(timestamp);
            int hevcOutputBufferIndex = mHevcEncoder.dequeueOutputBuffer(mBufferInfo, 0);
            if (hevcOutputBufferIndex < 0) {
                mFloatView.postInvalidate();
                continue;
            }
            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                ByteBuffer formatBuffer = mHevcEncoder.getOutputFormat(hevcOutputBufferIndex).getByteBuffer("csd-0");
                if (formatBuffer != null) {
                    csd0Handler(formatBuffer.array());
                }
            }
            ByteBuffer sendBuffer = mHevcEncoder.getOutputBuffer(hevcOutputBufferIndex);
            if (sendBuffer != null) {
                mBufferInfo.presentationTimeUs = timestamp;
                mRtspServer.sendVideo(sendBuffer, mBufferInfo);
            }
            mHevcEncoder.releaseOutputBuffer(hevcOutputBufferIndex, false);
        }
        releaseAll();
    }

    private void releaseAll() {
        mHevcEncoder.stop();
        mHevcEncoder.reset();
        mHevcEncoder.release();
        if (mAacEncoder != null) {
            mAacEncoder.stop();
            mAacEncoder.reset();
            mAacEncoder.release();
        }
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
        }
        if (mSurface != null) {
            mSurface.release();
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }
        if (mFloatView.isAttachedToWindow()) {
            mWindowManager.removeView(mFloatView);
        }
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
        mRtspServer.setVideoInfo(sps, pps, vps);
    }

    private void addAACEncoder(long timestamp) {
        if (mAudioRecord != null) {
            ByteBuffer audioRawByteBuffer = ByteBuffer.allocateDirect(minBufferSize);
            int len = mAudioRecord.read(audioRawByteBuffer, minBufferSize, AudioRecord.READ_NON_BLOCKING);
            if (len > 0) {
                int audioInputBufferIndex = mAacEncoder.dequeueInputBuffer(0);
                if (audioInputBufferIndex > -1) {
                    ByteBuffer mAacEncoderBuffer = mAacEncoder.getInputBuffer(audioInputBufferIndex);
                    if (mAacEncoderBuffer != null) {
                        ((ByteBuffer) mAacEncoderBuffer.clear()).put(audioRawByteBuffer);
                    }
                    mAacEncoder.queueInputBuffer(audioInputBufferIndex, 0, len, timestamp, 0);
                    int audioOutputBufferIndex;
                    while ((audioOutputBufferIndex = mAacEncoder.dequeueOutputBuffer(mAudioBufferInfo, 0)) > -1) {
                        ByteBuffer audioBuffer = mAacEncoder.getOutputBuffer(audioOutputBufferIndex);
                        if (audioBuffer != null) {
                            this.mRtspServer.sendAudio(audioBuffer, mAudioBufferInfo);
                        }
                        mAacEncoder.releaseOutputBuffer(audioOutputBufferIndex, false);
                    }
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