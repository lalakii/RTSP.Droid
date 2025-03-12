package cn.lalaki.rtsp_android_example;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.Toast;

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

public class MainActivity extends AppCompatActivity implements DialogInterface.OnClickListener, ActivityResultCallback<ActivityResult>, CompoundButton.OnCheckedChangeListener, ServiceConnection {

    private Handler mHandler;
    private OnRecordingEvent event;
    private SwitchCompat mSwitchCompat;
    private ActivityResultLauncher<Intent> startActivityForResult;
    public MediaProjectionManager mMediaProjectionManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        String[] permissions = new String[]{Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION, Manifest.permission.RECORD_AUDIO};
        ActivityCompat.requestPermissions(this, Arrays.stream(permissions).filter(Objects::nonNull)
                .toArray(String[]::new), 0);
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog dialog = new AlertDialog.Builder(this).setPositiveButton(R.string.confirm, this)
                    .setNegativeButton(R.string.cancel, this).create();
            dialog.setTitle(R.string.float_title);
            dialog.setMessage(getString(R.string.float_permission));
            dialog.show();
        }
        startActivityForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this);
        mHandler = new Handler(this.getMainLooper());
        mSwitchCompat = findViewById(R.id.checkbox);
        mSwitchCompat.setOnCheckedChangeListener(this);
    }

    @Override
    public void onActivityResult(ActivityResult result) {
        int resultCode = result.getResultCode();
        if (resultCode == Activity.RESULT_OK) {
            bindService(new Intent(this, SLService.class), this, Context.BIND_AUTO_CREATE);
            mHandler.postDelayed(() -> event.StartRec(mMediaProjectionManager, mHandler, resultCode, result.getData(), findViewById(R.id.rtsp_url)), 1000);
        } else {
            mSwitchCompat.setChecked(false);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        mSwitchCompat.setText(b ? R.string.switch_text_stop : R.string.switch_text);
        if (b) {
            startActivityForResult.launch(mMediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()));
        } else {
            if (event != null) {
                event.Dispose();
            } else {
                Toast.makeText(this, R.string.granting, Toast.LENGTH_SHORT).show();
            }
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
        getMenuInflater().inflate(R.menu.menu1, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {

    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (AlertDialog.BUTTON_POSITIVE == which) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        }
    }

    public interface OnRecordingEvent {
        void StartRec(MediaProjectionManager mMediaProjectionManager, Handler mHandler, int resultCode, Intent data, TextView tv);

        void Dispose();
    }
}