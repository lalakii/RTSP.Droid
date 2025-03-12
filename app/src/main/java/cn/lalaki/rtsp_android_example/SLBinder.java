package cn.lalaki.rtsp_android_example;

import android.os.Binder;

public class SLBinder extends Binder {
    private final SLService context;

    public SLBinder(SLService context) {
        this.context = context;
    }
    public SLService getContext(){
        return this.context;
    }
}
