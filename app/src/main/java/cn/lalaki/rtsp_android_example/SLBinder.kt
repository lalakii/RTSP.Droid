package cn.lalaki.rtsp_android_example

import android.os.Binder

class SLBinder(context: SLService) : Binder() {
    var mContext: SLService = context
}
