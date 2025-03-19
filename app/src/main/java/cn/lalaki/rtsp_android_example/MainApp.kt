package cn.lalaki.rtsp_android_example

import android.app.Application
import android.content.ComponentName
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import androidx.activity.result.ActivityResult
import cn.lalaki.rtsp_android_example.binder.SLBinder
import cn.lalaki.rtsp_android_example.ui.MainActivity
import com.pedro.common.ConnectChecker

class MainApp : Application(), ServiceConnection, ConnectChecker {
    var mEvent: IRecordingEvent? = null
    var mActivity: MainActivity? = null
    var mIsMic = false
    var mResult: ActivityResult? = null
    var mResultCode = -1
    val mRequestPermissions = arrayOf<String>(
        android.Manifest.permission.POST_NOTIFICATIONS, android.Manifest.permission.RECORD_AUDIO
    )
    var mWidth = 0
    var mHeight = 0
    val mHandler by lazy {
        Handler(mainLooper)
    }
    val mFloatView by lazy {
        View(this)
    }
    val mLayoutParams: WindowManager.LayoutParams by lazy {
        WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            x = 0
            y = 0
            alpha = 0f
            gravity = Gravity.LEFT or Gravity.BOTTOM
            type = TYPE_APPLICATION_OVERLAY
            flags =
                FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_NOT_TOUCHABLE or FLAG_LAYOUT_NO_LIMITS
        }
    }
    val mMediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    val mWindowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onServiceConnected(
        name: ComponentName?, service: IBinder?
    ) {
        mEvent = (service as (SLBinder)).mContext
        mActivity?.mServiceIsBound = true
        mActivity?.startRecord(mEvent)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        mActivity?.mServiceIsBound = false
        mEvent = null
    }

    override fun onAuthError() {
    }

    override fun onAuthSuccess() {
    }

    override fun onConnectionFailed(reason: String) {
    }

    override fun onConnectionStarted(url: String) {
    }

    override fun onConnectionSuccess() {
    }

    override fun onDisconnect() {
    }

    override fun onNewBitrate(bitrate: Long) {
    }
}