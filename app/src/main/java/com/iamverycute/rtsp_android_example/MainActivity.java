package com.iamverycute.rtsp_android_example;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements ActivityResultCallback<ActivityResult>, CompoundButton.OnCheckedChangeListener, ServiceConnection {

    private Handler mHandler;
    private OnRecordingEvent event;
    private SwitchCompat switchButton;
    private ActivityResultLauncher<Intent> startActivityForResult;
    public MediaProjectionManager mpm;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        String[] permissions = new String[]{Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION, Manifest.permission.RECORD_AUDIO};
        ActivityCompat.requestPermissions(this, Arrays.stream(permissions).filter(Objects::nonNull)
                .toArray(String[]::new), 0);
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        }
        startActivityForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this);
        mHandler = new Handler(this.getMainLooper());
        switchButton = findViewById(R.id.checkbox);
        switchButton.setOnCheckedChangeListener(this);
    }

    @Override
    public void onActivityResult(ActivityResult result) {
        int resultCode = result.getResultCode();
        if (resultCode == Activity.RESULT_OK) {
            bindService(new Intent(this, SLService.class), this, Context.BIND_AUTO_CREATE);
            mHandler.postDelayed(() -> event.StartRec(mpm,mHandler, resultCode, result.getData(), findViewById(R.id.rtsp_url)), 1000);
        } else {
            switchButton.setChecked(false);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (b) {
            startActivityForResult.launch(mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()));
        } else {
            event.Dispose();
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        event = ((SLBinder) iBinder).getContext();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.opensource) {
            try {
                startActivity(Intent.getIntentOld(getString(R.string.project_url)));
            } catch (URISyntaxException ignored) {
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_top, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {

    }

    public interface OnRecordingEvent {
        void StartRec(MediaProjectionManager mpm,Handler mHandler, int resultCode, Intent data, TextView tv);

        void Dispose();
    }
}