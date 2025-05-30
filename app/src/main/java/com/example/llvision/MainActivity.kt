package com.example.llvision

import android.content.Intent
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.util.Log
import com.example.llvision.databinding.ActivityMainBinding
import com.llvision.glass3.library.usb.USBMonitor
import com.llvision.glass3.platform.*
import com.llvision.glxss.common.service.GlassServiceManager
import com.llvision.glxss.common.utils.ToastUtils
import com.llvision.glass3.platform.base.BasePermissionActivity
import androidx.core.net.toUri

class MainActivity : BasePermissionActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mGlass3Device: IGlass3Device? = null
    private val mUIHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1234
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        } else {
            checkStorageManagerPermission()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                checkStorageManagerPermission()
            } else {
                Log.d("OnPermissionAccepted", getString(R.string.overlay_permission_denied))            }
        }
    }

    override fun onPermissionAccepted(isAccepted: Boolean) {
        if (isAccepted) {
            LLVisionGlass3SDK.getInstance().init(this, glassStatus)
        } else {
            Log.d("OnPermissionAccepted", getString(R.string.sdk_init_failed))
        }
    }

    private fun checkStorageManagerPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private val glassStatus = object : ConnectionStatusListener {
        override fun onServiceConnected(devices: List<IGlass3Device>) {}

        override fun onServiceDisconnected() {
            mUIHandler.removeCallbacks(connectRunnable)
        }

        override fun onDeviceConnect(device: IGlass3Device) {
            mGlass3Device = device
            updateConnectionStatus(connected = true)
            readDeviceInfo(device)
            mUIHandler.postDelayed(connectRunnable, 3000)
        }

        override fun onDeviceDisconnect(device: IGlass3Device) {
            updateConnectionStatus(connected = false)
            GlassServiceManager.stopAllServices()
        }

        override fun onError(code: Int, msg: String) {
            ToastUtils.showShort(applicationContext, getString(R.string.sdk_init_failed, msg))
        }
    }

    private val connectRunnable = Runnable {
        try {
            mGlass3Device?.let { device ->
                if (USBMonitor.isThirdPartyDevice(device.usbDevice)) {
                    GlassServiceManager.startGlassAudioService(this)
                } else {
                    GlassServiceManager.initAllServices(applicationContext, device)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val deviceList = LLVisionGlass3SDK.getInstance().glass3DeviceList
            if (deviceList.isNotEmpty()) {
                mGlass3Device = deviceList[0]
                updateConnectionStatus(true)
                readDeviceInfo(mGlass3Device!!)
                mUIHandler.postDelayed(connectRunnable, 3000)
            } else {
                updateConnectionStatus(false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LLVisionGlass3SDK.getInstance().takeIf { it.isServiceConnected }?.destroy()
        GlassServiceManager.stopAllServices()
    }

    private fun updateConnectionStatus(connected: Boolean) {
        binding.textConnected.apply {
            setTextColor(if (connected) Color.GREEN else Color.RED)
            setText(if (connected) R.string.device_connected else R.string.device_disconnected)
        }
    }

    private fun readDeviceInfo(device: IGlass3Device) {
        Log.d("DeviceInfo", "Serial: ${device.deviceId}")
    }
}
