package cn.lalaki.rtsp_android_example;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH;

import static cn.lalaki.rtsp_android_example.util.AACAudioRecorder.SAMPLE_RATE;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Paint;
import android.media.MediaCodec;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.pedro.rtspserver.RtspServer;

import cn.lalaki.rtsp_android_example.util.AACAudioRecorder;
import cn.lalaki.rtsp_android_example.util.HEVCVideoRecorder;

/**
 * @author lalakii    -     i@lalaki.cn
 */

public class SLService extends Service implements MainActivity.OnRecordingEvent {
    private static RtspServer mRtspServer;
    private View mFloatView;
    private String mRtspUrl;
    private Notification mNotify;
    private RemoteViews mNotifyView;
    private WindowManager.LayoutParams mLayoutParams;
    private MediaProjection mMediaProjection;
    private WindowManager mWindowManager;
    private TextView mRtspUrlView;
    private AACAudioRecorder mAACAudioRecorder;
    private HEVCVideoRecorder mHEVCVideoRecorder;
    public static final int USE_PORT = 12345;
    private static final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private MainApp mMainApp;
    private NotificationManager mNotificationManager;
    private static final int NOTIFY_ID = 1;
    public boolean mIsMic = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Context appContext = getApplicationContext();
        if (appContext instanceof MainApp) {
            mMainApp = (MainApp) appContext;
        }
        if (mRtspServer == null) {
            mRtspServer = new RtspServer(mMainApp, USE_PORT);
            mRtspServer.setLogs(false);
            mRtspServer.setAudioInfo(SAMPLE_RATE, false);
            mRtspServer.setAuth("", "");
            mRtspServer.startServer();
        }
        mRtspUrl = String.format("rtsp://%s:%s", mRtspServer.getServerIp(), mRtspServer.getPort());
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotifyView = new RemoteViews(getPackageName(), R.layout.notify);
        mNotifyView.setOnClickPendingIntent(R.id.tv1, PendingIntent.getActivity(getApplicationContext(), 1, getPackageManager().getLaunchIntentForPackage(getPackageName()), FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE));
        mNotify = new NotificationCompat.Builder(mMainApp, getString(R.string.channel_id)).setContentText(mRtspUrl).setCustomContentView(mNotifyView).setWhen(System.currentTimeMillis()).setSmallIcon(android.R.drawable.presence_video_online).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true).build();
        NotificationManagerCompat.from(mMainApp).createNotificationChannel(new NotificationChannelCompat.Builder(mNotify.getChannelId(), IMPORTANCE_HIGH).setName(getString(R.string.app_name)).setDescription(getString(R.string.app_name)).setSound(null, null).setLightsEnabled(false).setShowBadge(false).setVibrationEnabled(false).build());
        startMediaProjectionForeground();
    }

    public boolean isRunning() {
        return mHEVCVideoRecorder.getMRunning();
    }

    public void onRestore(TextView rtspUrlView) {
        showNotify(mRtspUrl, rtspUrlView, true);
    }

    public void onRecord(MediaProjectionManager mediaProjection, Handler mHandler, int resultCode, Intent data, WindowManager windowManager, View floatView, WindowManager.LayoutParams layoutParams, boolean isMic, TextView rtspUrlView, TextView logView) {
        this.mWindowManager = windowManager;
        this.mLayoutParams = layoutParams;
        this.mFloatView = floatView;
        this.mRtspUrlView = rtspUrlView;
        this.mMediaProjection = mediaProjection.getMediaProjection(resultCode, data);
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
        showNotify(mRtspUrl, rtspUrlView, true);
        beginRecorder(isMic, logView);
    }

    private void startMediaProjectionForeground() {
        try {
            startForeground(NOTIFY_ID, mNotify, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } catch (SecurityException ignored) {
        }
    }

    private void showNotify(String text, TextView tv, boolean state) {
        if (tv != null) {
            tv.setText(state ? mRtspUrl : getText(R.string.stopped));
            tv.setTextColor(getColor(state ? R.color.blue : android.R.color.darker_gray));
            if (state) {
                tv.setPaintFlags(tv.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            } else {
                tv.setPaintFlags(tv.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
            }
        }
        mNotifyView.setTextViewText(R.id.tv1, text);
        mNotifyView.setTextColor(R.id.tv1, getColor(state ? android.R.color.holo_green_dark : android.R.color.darker_gray));
        if (ActivityCompat.checkSelfPermission(mMainApp, Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION) == PackageManager.PERMISSION_GRANTED) {
            startMediaProjectionForeground();
        }
        if (!state) {
            mNotificationManager.notify(NOTIFY_ID, mNotify);
        }
    }

    private void beginRecorder(boolean isMic, TextView logView) {
        if (Settings.canDrawOverlays(mMainApp) && !mFloatView.isAttachedToWindow()) {
            mWindowManager.addView(mFloatView, mLayoutParams);
        }
        mIsMic = isMic;
        HEVCVideoRecorder hevcVideoRecorder = new HEVCVideoRecorder(mMediaProjection, logView);
        mAACAudioRecorder = new AACAudioRecorder(mMainApp, mMediaProjection, mBufferInfo, isMic);
        hevcVideoRecorder.start(mRtspServer, mAACAudioRecorder, mBufferInfo, mFloatView);
        mHEVCVideoRecorder = hevcVideoRecorder;
    }

    @Override
    public void onRelease() {
        HEVCVideoRecorder hevcVideoRecorder = mHEVCVideoRecorder;
        if (hevcVideoRecorder != null) {
            hevcVideoRecorder.release();
        }
        AACAudioRecorder aacAudioRecorder = mAACAudioRecorder;
        if (aacAudioRecorder != null) {
            aacAudioRecorder.release();
        }
        MediaProjection projection = mMediaProjection;
        if (projection != null) {
            projection.stop();
        }
        View floatView = mFloatView;
        WindowManager windowManager = mWindowManager;
        if (windowManager != null && floatView != null && floatView.isAttachedToWindow()) {
            windowManager.removeView(floatView);
        }
        showNotify(getString(R.string.stopped), mRtspUrlView, false);
    }

    @Override
    public boolean isMic() {
        return mIsMic;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new SLBinder(this);
    }
}