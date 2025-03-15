package cn.lalaki.rtsp_android_example

interface IRecordingEvent {
    fun onRecord(appContext: MainApp)

    fun onRelease()

    fun running(): Boolean

    fun audioMic(): Boolean

    fun onRestore()
}