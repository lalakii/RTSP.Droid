package cn.lalaki.rtsp_android_example.binder

import android.os.Binder
import cn.lalaki.rtsp_android_example.SLService

class SLBinder(context: SLService) : Binder() {
    var mContext: SLService = context
}