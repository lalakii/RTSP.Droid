package com.iamverycute.rtsp_android_example;

import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioEncoder extends Thread {
    private final AudioDataCallback callback;
    private MediaCodec aacEncoder;
    private AudioRecord audioRecord;
    private int minBufferSize;

    public AudioEncoder(Context context, AudioPlaybackCaptureConfiguration audioConfig) {
        this.callback = (AudioDataCallback) context;
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        int sampleRate = 32000;
        int ENCODING = ENCODING_PCM_16BIT;
        int channelConfig = CHANNEL_IN_MONO;
        minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, ENCODING);
        //if u want record mic sound. replace setAudioPlaybackCaptureConfig  to  setAudioSource(MIC)
        audioRecord = new AudioRecord.Builder().setAudioPlaybackCaptureConfig(audioConfig).setAudioFormat(new AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(ENCODING).setChannelMask(channelConfig).build()).setBufferSizeInBytes(minBufferSize).build();
        MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelConfig);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 48000);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        try {
            aacEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        } catch (IOException ignored) {
        }
        aacEncoder.configure(audioFormat, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
        aacEncoder.start();
    }

    private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    @Override
    public void run() {
        super.run();
        audioRecord.startRecording();
        while (!Thread.currentThread().isInterrupted()) {
            ByteBuffer rawBuffer = ByteBuffer.allocateDirect(minBufferSize);
            int len = audioRecord.read(rawBuffer, minBufferSize, AudioRecord.READ_NON_BLOCKING);
            if (len == 0) continue;
            AAC_ENCODE(rawBuffer, len);
        }
        audioRecord.stop();
        audioRecord.release();
        aacEncoder.stop();
        aacEncoder.reset();
        aacEncoder.release();
    }

    private void AAC_ENCODE(ByteBuffer rawData, int len) {
        int inputIndex = aacEncoder.dequeueInputBuffer(0);
        if (inputIndex > -1) {
            ((ByteBuffer) aacEncoder.getInputBuffer(inputIndex).clear()).put(rawData);
            aacEncoder.queueInputBuffer(inputIndex, 0, len, System.nanoTime() / 1000L, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }
        int outputIndex;
        while ((outputIndex = aacEncoder.dequeueOutputBuffer(bufferInfo, 0)) > -1) {
            callback.OnAudio(aacEncoder.getOutputBuffer(outputIndex), bufferInfo);
            aacEncoder.releaseOutputBuffer(outputIndex, false);
        }
    }

    interface AudioDataCallback {
        void OnAudio(ByteBuffer data, MediaCodec.BufferInfo info);
    }
}