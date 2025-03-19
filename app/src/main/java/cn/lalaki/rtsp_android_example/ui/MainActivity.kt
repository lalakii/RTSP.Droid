package cn.lalaki.rtsp_android_example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import cn.lalaki.rtsp_android_example.IRecordingEvent
import cn.lalaki.rtsp_android_example.MainApp
import cn.lalaki.rtsp_android_example.R
import cn.lalaki.rtsp_android_example.SLService
import cn.lalaki.rtsp_android_example.databinding.MainBinding
import com.suke.widget.SwitchButton
import java.net.URISyntaxException
import kotlin.system.exitProcess

open class MainActivity : AppCompatActivity(), View.OnClickListener,
    SwitchButton.OnCheckedChangeListener, DialogInterface.OnClickListener,
    ActivityResultCallback<ActivityResult> {
    lateinit var mBinding: MainBinding
    private lateinit var mMainApp: MainApp
    var mServiceIsBound = false
    lateinit var startActivityForResult: ActivityResultLauncher<Intent>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContext = applicationContext
        if (appContext is MainApp) {
            mMainApp = appContext
            mMainApp.mActivity = this
        }
        mBinding = MainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        startActivityForResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult(), this)
        mBinding.clearAll.setOnClickListener(this)
        mBinding.forceExit.setOnClickListener(this)
        mBinding.copyBtn.setOnClickListener(this)
        mBinding.report.setOnClickListener(this)
        val event = mMainApp.mEvent
        if (event?.running() == true) {
            event.onRestore()
            mBinding.audioMic.setChecked(event.audioMic())
            mBinding.switchBtn.setChecked(true)
        }
        mBinding.switchBtn.setOnCheckedChangeListener(this)
        ActivityCompat.requestPermissions(this, mMainApp.mRequestPermissions, 0x233)
        if (!Settings.canDrawOverlays(this)) {
            val floatWindowTips =
                AlertDialog.Builder(this).setPositiveButton(R.string.confirm, this)
                    .setNegativeButton(R.string.cancel, this).create()
            floatWindowTips.setTitle(R.string.float_title)
            floatWindowTips.setMessage(getString(R.string.float_permission))
            floatWindowTips.show()
        }
        mBinding.forceExit.buttonColor =
            getColor(info.hoang8f.fbutton.R.color.fbutton_color_alizarin)
        mBinding.report.buttonColor = getColor(info.hoang8f.fbutton.R.color.fbutton_color_green_sea)
        mBinding.pixel.adapter = ArrayAdapter<String>(
            this,
            R.layout.item,
            resources.getStringArray(R.array.pixels)
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu1, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onClick(v: View?) {
        val id = v?.id
        if (id != null) {
            when (id) {
                R.id.report -> {
                    startUrl(R.string.issues_url)
                }

                R.id.copy_btn -> {
                    if (mBinding.rtspUrl.text.contains("rtsp")) {
                        val service = getSystemService(CLIPBOARD_SERVICE)
                        if (service is ClipboardManager) {
                            service.setPrimaryClip(
                                ClipData.newPlainText(
                                    getText(R.string.app_name),
                                    mBinding.rtspUrl.text
                                )
                            )
                            Toast.makeText(this, R.string.copy_ok, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                R.id.clear_all -> {
                    mBinding.logView.text = ""
                }

                R.id.force_exit -> {
                    exitProcess(0)
                }
            }
        }
    }

    private fun startUrl(urlId: Int) {
        try {
            startActivity(Intent.getIntentOld(getString(urlId)))
        } catch (_: URISyntaxException) {
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.opensource) {
            startUrl(R.string.project_url)
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onClick(dialog: DialogInterface?, which: Int) {
        if (AlertDialog.BUTTON_POSITIVE == which) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.fromParts("package", packageName, null)
                )
            )
        }
    }

    override fun onCheckedChanged(view: SwitchButton?, isChecked: Boolean) {
        synchronized(this) {
            mBinding.switchLabel.text =
                getText(if (isChecked) R.string.switch_text_stop else R.string.switch_text)
            if (isChecked) {
                val captureIntent = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
                    mMainApp.mMediaProjectionManager
                        .createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                } else {
                    mMainApp.mMediaProjectionManager.createScreenCaptureIntent()
                }
                startActivityForResult.launch(captureIntent)
            } else {
                val event = mMainApp.mEvent
                if (event != null) {
                    event.onRelease()
                } else {
                    Toast.makeText(this, R.string.granting, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startRecord(event: IRecordingEvent?) {
        if (event != null) {
            mMainApp.mIsMic = mBinding.audioMic.isChecked
            val pixelArray = mBinding.pixel.selectedItem.toString().split("x")
            mMainApp.mHeight = pixelArray[0].toInt()
            mMainApp.mWidth = pixelArray[1].toInt()
            event.onRecord(mMainApp)
            mBinding.logView.append("The service has been bound, recording screen...\n")
        }
    }

    override fun onActivityResult(result: ActivityResult) {
        val resultCode = result.resultCode
        if (resultCode == RESULT_OK) {
            mMainApp.mResultCode = resultCode
            mMainApp.mResult = result
            bindService(
                Intent(mMainApp, SLService::class.java),
                mMainApp,
                BIND_AUTO_CREATE
            )
            if (mServiceIsBound) {
                startRecord(mMainApp.mEvent)
            }
        } else {
            mBinding.switchBtn.setChecked(false)
        }
    }
}