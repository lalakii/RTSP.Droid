package cn.lalaki.rtsp_android_example.util

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.view.Surface
import android.view.View
import android.widget.TextView
import com.pedro.rtspserver.RtspServer
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.concurrent.thread

open class HEVCVideoRecorder(
    mMediaProjection: MediaProjection,
    var logView: TextView,
    var width: Int,
    var height: Int
) {
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mSurface: Surface? = null
    private var mHevcEncoder: MediaCodec? = null
    var mRunning = false

    init {
        // 自行测试哪个分辨率可用
        logView.append(String.format("Pixel: w:%s, h:%s\n", width, height))
        val videoFormat =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)
        videoFormat.setInteger(
            MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
        )
        videoFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25)
        try {
            val hevcEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
            mHevcEncoder = hevcEncoder
            hevcEncoder.configure(videoFormat, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null)
            val surface = hevcEncoder.createInputSurface()
            mSurface = surface
            hevcEncoder.start()
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "vd-9",
                width,
                height,
                1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                surface,
                null,
                null
            )
        } catch (e: IllegalArgumentException) {
            logView.append("出现此提示请尝试不同的分辨率！！！${e.localizedMessage}")
        } catch (e: IllegalStateException) {
            logView.append(e.localizedMessage)
        } catch (e: IOException) {
            logView.append(e.localizedMessage)
        } catch (e: IOException) {
            logView.append(e.localizedMessage)
        } catch (e: SecurityException) {
            logView.append(e.localizedMessage)
        }
    }

    private fun csd0Handler(data: ByteArray, rtspServer: RtspServer) {
        var segment = 0
        var ppsPosition = -1
        var spsPosition = -1
        for (i in data.size - 1 downTo 0) {
            if (segment == 3 && data[i + 4] == 1.toByte()) {
                if (ppsPosition == -1) {
                    ppsPosition = i + 1
                } else {
                    spsPosition = i + 1
                    break
                }
            }
            segment = if (data[i] == 0.toByte()) {
                segment + 1
            } else {
                0
            }
        }
        val vps = ByteBuffer.allocate(spsPosition).put(data, 0, spsPosition)
        val spsSize = ppsPosition - spsPosition
        val sps = ByteBuffer.allocate(spsSize).put(data, spsPosition, spsSize)
        val ppsSize = data.size - ppsPosition
        val pps = ByteBuffer.allocate(ppsSize).put(data, ppsPosition, ppsSize)
        rtspServer.setVideoInfo(sps, pps, vps)
    }

    open fun start(
        rtspServer: RtspServer,
        aacAudioRecorder: AACAudioRecorder?,
        bufferInfo: MediaCodec.BufferInfo,
        floatView: View?
    ) {
        thread {
            mRunning = true
            try {
                val hevcEncoder = mHevcEncoder
                if (hevcEncoder != null) {
                    val beginTime = System.nanoTime()
                    while (mRunning) {
                        val timestamp = (System.nanoTime() - beginTime) / 1000L
                        aacAudioRecorder?.sendAacBuffer(rtspServer, timestamp)
                        val hevcOutputBufferIndex = hevcEncoder.dequeueOutputBuffer(bufferInfo, 0)
                        if (hevcOutputBufferIndex < 0) {
                            floatView?.postInvalidate()
                            continue
                        }
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                            val formatBuffer =
                                hevcEncoder.getOutputFormat(hevcOutputBufferIndex).getByteBuffer(
                                    "csd-0"
                                )
                            if (formatBuffer != null) {
                                csd0Handler(formatBuffer.array(), rtspServer)
                            }
                        }
                        val outputBuffer = hevcEncoder.getOutputBuffer(hevcOutputBufferIndex)
                        if (outputBuffer != null) {
                            bufferInfo.presentationTimeUs = timestamp
                            rtspServer.sendVideo(outputBuffer, bufferInfo)
                        }
                        hevcEncoder.releaseOutputBuffer(hevcOutputBufferIndex, false)
                    }
                }
            } catch (e: IllegalStateException) {
                logView.post {
                    logView.append(e.localizedMessage)
                }
            }
            stop()
        }
    }

    private fun stop() {
        val hevcEncoder = mHevcEncoder
        if (hevcEncoder != null) {
            hevcEncoder.stop()
            hevcEncoder.reset()
            hevcEncoder.release()
        }
        mSurface?.release()
        mVirtualDisplay?.release()
        logView.post {
            logView.append("All objects are released.\n")
        }
    }

    open fun release() {
        mRunning = false
    }
}