package cn.lalaki.rtsp_android_example;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.suke.widget.SwitchButton;

import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, DialogInterface.OnClickListener, ActivityResultCallback<ActivityResult>, SwitchButton.OnCheckedChangeListener, ServiceConnection {

    private Handler mHandler;
    private OnRecordingEvent mEvent;
    private TextView mSwitchLabel;
    private SwitchButton mSwitchBtn;
    private ActivityResultLauncher<Intent> startActivityForResult;
    public MediaProjectionManager mMediaProjectionManager;
    private TextView mLogView;
    private View mFloatView;
    private WindowManager mWindowManager;
    public int mResultCode;
    public ActivityResult mResult;
    private TextView mRtspUrlView;
    private final String[] mPermissions = new String[]{Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO};
    private ClipboardManager mClipboardManager;
    private RadioButton mRadioButton;
    private final WindowManager.LayoutParams mLayoutParams = new WindowManager.LayoutParams() {
        {
            width = MATCH_PARENT;
            height = MATCH_PARENT;
            x = 0;
            y = 0;
            alpha = 0;
            gravity = Gravity.LEFT | Gravity.BOTTOM;
            type = TYPE_APPLICATION_OVERLAY;
            flags = FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_NO_LIMITS;
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mRadioButton = findViewById(R.id.audio_mic);
        mRtspUrlView = findViewById(R.id.rtsp_url);
        mFloatView = new View(this);
        mFloatView.setBackgroundColor(Color.RED);
        mLogView = findViewById(R.id.log_view);
        mClipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        ActivityCompat.requestPermissions(this, mPermissions, 0x233);
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog floatWindowTips = new AlertDialog.Builder(this).setPositiveButton(R.string.confirm, this).setNegativeButton(R.string.cancel, this).create();
            floatWindowTips.setTitle(R.string.float_title);
            floatWindowTips.setMessage(getString(R.string.float_permission));
            floatWindowTips.show();
        }
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mHandler = new Handler(this.getMainLooper());
        startActivityForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this);
        mSwitchLabel = findViewById(R.id.switch_label);
        mSwitchBtn = findViewById(R.id.switch_btn);
        mSwitchBtn.setOnCheckedChangeListener(this);
        findViewById(R.id.copy_btn).setOnClickListener(this);
    }

    private void startRecord(OnRecordingEvent event) {
        if (event != null) {
            boolean isMic = mRadioButton.isChecked();
            event.onRecord(mMediaProjectionManager, mHandler, mResultCode, mResult.getData(), mWindowManager, mFloatView, mLayoutParams, isMic, mRtspUrlView, mLogView);
            mLogView.append("The service has been bound, recording screen...\n");
        }
    }

    @Override
    public void onActivityResult(ActivityResult result) {
        int resultCode = result.getResultCode();
        if (resultCode == Activity.RESULT_OK) {
            mResultCode = resultCode;
            mResult = result;
            bindService(new Intent(this, SLService.class), this, Context.BIND_AUTO_CREATE);
            startRecord(mEvent);
        } else {
            mSwitchBtn.setChecked(false);
        }
    }

    @Override
    public void onCheckedChanged(SwitchButton view, boolean b) {
        synchronized (this) {
            mSwitchLabel.setText(b ? R.string.switch_text_stop : R.string.switch_text);
            if (b) {
                Intent captureIntent;
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
                    captureIntent = mMediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
                } else {
                    captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
                }
                startActivityForResult.launch(captureIntent);
            } else {
                OnRecordingEvent event = mEvent;
                if (event != null) {
                    event.onRelease();
                } else {
                    Toast.makeText(this, R.string.granting, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        mEvent = ((SLBinder) iBinder).getMContext();
        startRecord(mEvent);
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
    public void onClick(DialogInterface dialog, int which) {
        if (AlertDialog.BUTTON_POSITIVE == which) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {

    }

    @Override
    public void onClick(View v) {
        String text = mRtspUrlView.getText().toString().trim();
        if (!text.isEmpty()) {
            mClipboardManager.setPrimaryClip(ClipData.newPlainText(getText(R.string.app_name), text));
            Toast.makeText(this, R.string.copy_ok, Toast.LENGTH_SHORT).show();
        }
    }


    public interface OnRecordingEvent {
        void onRecord(MediaProjectionManager mMediaProjectionManager, Handler mHandler, int resultCode, Intent data, WindowManager windowManager, View floatView, WindowManager.LayoutParams layoutParams, boolean isMic, TextView tv, TextView log);

        void onRelease();
    }
}