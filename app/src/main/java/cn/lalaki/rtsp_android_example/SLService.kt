package cn.lalaki.rtsp_android_example

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Paint
import android.media.MediaCodec
import android.media.projection.MediaProjection
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.RemoteViews
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH
import com.pedro.rtspserver.RtspServer
import cn.lalaki.rtsp_android_example.binder.SLBinder
import cn.lalaki.rtsp_android_example.ui.MainActivity
import cn.lalaki.rtsp_android_example.util.AACAudioRecorder
import cn.lalaki.rtsp_android_example.util.HEVCVideoRecorder

/**
 * @author lalakii    -     i@lalaki.cn
 */
class SLService : Service(), IRecordingEvent {
    private var mFloatView: View? = null
    private var mRtspUrl: String? = null
    private var mNotify: Notification? = null
    private var mNotifyView: RemoteViews? = null
    private var mLayoutParams: WindowManager.LayoutParams? = null
    private var mMediaProjection: MediaProjection? = null
    private var mWindowManager: WindowManager? = null
    private var mRtspUrlView: TextView? = null
    private var mAACAudioRecorder: AACAudioRecorder? = null
    private var mHEVCVideoRecorder: HEVCVideoRecorder? = null
    private var mMainApp: MainApp? = null
    private var mNotificationManager: NotificationManager? = null
    var mIsMic = false

    override fun onCreate() {
        super.onCreate()
        val appContext: Context = applicationContext
        if (appContext is MainApp) {
            mMainApp = appContext
        }
        if (mRtspServer == null) {
            mMainApp?.let {
                mRtspServer = RtspServer(it, USE_PORT)
                mRtspServer?.setLogs(false)
                mRtspServer?.setAudioInfo(AACAudioRecorder.SAMPLE_RATE, false)
                mRtspServer?.setAuth("", "")
                mRtspServer?.startServer()
            }
        }
        mRtspUrl = String.format("rtsp://%s:%s", mRtspServer?.serverIp, mRtspServer?.port)
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mNotifyView = RemoteViews(packageName, R.layout.notify)
        mNotifyView?.setOnClickPendingIntent(
            R.id.tv1,
            PendingIntent.getActivity(
                applicationContext,
                1,
                packageManager.getLaunchIntentForPackage(packageName),
                FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
            )
        )
        mMainApp?.let {
            mNotify = NotificationCompat.Builder(it, getString(R.string.channel_id))
                .setContentText(mRtspUrl)
                .setCustomContentView(mNotifyView)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .build()
            NotificationManagerCompat.from(it).createNotificationChannel(
                NotificationChannelCompat.Builder(mNotify!!.channelId, IMPORTANCE_HIGH)
                    .setName(getString(R.string.app_name))
                    .setDescription(getString(R.string.app_name))
                    .setSound(null, null)
                    .setLightsEnabled(false)
                    .setShowBadge(false)
                    .setVibrationEnabled(false)
                    .build()
            )
        }
        startMediaProjectionForeground()
    }

    override fun running(): Boolean {
        return mHEVCVideoRecorder?.mRunning == true
    }

    override fun onRestore() {
        val activity: MainActivity? = mMainApp?.mActivity
        if (activity != null) {
            showNotify("$mRtspUrl", activity.mBinding.rtspUrl, true)
        }
    }

    override fun onRecord(appContext: MainApp) {
        mWindowManager = appContext.mWindowManager
        mLayoutParams = appContext.mLayoutParams
        mFloatView = appContext.mFloatView
        val activity: MainActivity? = appContext.mActivity
        val result: ActivityResult? = mMainApp?.mResult
        if (result != null && activity != null) {
            mRtspUrlView = activity.mBinding.rtspUrl
            val data: Intent? = result.data
            if (data != null) {
                mMediaProjection = mMainApp?.mMediaProjectionManager?.getMediaProjection(
                    mMainApp!!.mResultCode,
                    data
                )
                mMediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        super.onStop()
                    }

                    override fun onCapturedContentResize(width: Int, height: Int) {
                        super.onCapturedContentResize(width, height)
                    }

                    override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                        super.onCapturedContentVisibilityChanged(isVisible)
                    }
                }, mMainApp!!.mHandler)
                showNotify("$mRtspUrl", mRtspUrlView!!, true)
                beginRecorder(mMainApp!!.mIsMic, activity.mBinding.logView)
            }
        }
    }

    private fun startMediaProjectionForeground() {
        val notify = mNotify
        if (notify != null) {
            try {
                startForeground(
                    NOTIFY_ID,
                    notify,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } catch (_: SecurityException) {
            }
        }
    }

    private fun showNotify(text: String, tv: TextView?, state: Boolean) {
        if (tv != null) {
            tv.text = if (state) mRtspUrl else getText(R.string.stopped)
            tv.setTextColor(getColor(if (state) R.color.blue else android.R.color.darker_gray))
            if (state) {
                tv.paintFlags = tv.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            } else {
                tv.paintFlags = tv.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            }
        }
        mNotifyView?.setTextViewText(R.id.tv1, text)
        mNotifyView?.setTextColor(
            R.id.tv1,
            getColor(if (state) android.R.color.holo_green_dark else android.R.color.darker_gray)
        )
        if (ActivityCompat.checkSelfPermission(
                mMainApp!!,
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startMediaProjectionForeground()
        }
        if (!state) {
            mNotificationManager?.notify(NOTIFY_ID, mNotify)
        }
    }

    private fun beginRecorder(isMic: Boolean, logView: TextView) {
        if (Settings.canDrawOverlays(mMainApp) && mFloatView?.isAttachedToWindow == false) {
            mWindowManager?.addView(mFloatView, mLayoutParams)
        }
        mIsMic = isMic
        val mediaProjection = mMediaProjection
        val mainApp = mMainApp
        val rtspServer = mRtspServer
        if (mediaProjection != null && mainApp != null && rtspServer != null) {
            val hevcVideoRecorder = HEVCVideoRecorder(mediaProjection, logView)
            mAACAudioRecorder = AACAudioRecorder(mainApp, mediaProjection, mBufferInfo, isMic)
            hevcVideoRecorder.start(rtspServer, mAACAudioRecorder, mBufferInfo, mFloatView)
            mHEVCVideoRecorder = hevcVideoRecorder
        }
    }

    override fun onRelease() {
        mHEVCVideoRecorder?.release()
        mAACAudioRecorder?.release()
        mMediaProjection?.stop()
        val floatView = mFloatView
        val windowManager = mWindowManager
        if (windowManager != null && floatView != null && floatView.isAttachedToWindow) {
            windowManager.removeView(floatView)
        }
        showNotify(getString(R.string.stopped), mRtspUrlView!!, false)
    }

    override fun audioMic(): Boolean {
        return mIsMic
    }

    override fun onBind(intent: Intent): IBinder {
        return SLBinder(this)
    }

    companion object {
        var mRtspServer: RtspServer? = null
        val mBufferInfo = MediaCodec.BufferInfo()
        const val USE_PORT = 12345
        const val NOTIFY_ID = 1
    }
}
