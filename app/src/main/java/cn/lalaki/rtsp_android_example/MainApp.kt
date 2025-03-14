package cn.lalaki.rtsp_android_example

import android.app.Application
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.pedro.common.ConnectChecker

class MainApp : Application(), ServiceConnection, ConnectChecker {
    var mEvent: MainActivity.OnRecordingEvent? = null
    var mActivity: MainActivity? = null
    override fun onServiceConnected(
        name: ComponentName?,
        service: IBinder?
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