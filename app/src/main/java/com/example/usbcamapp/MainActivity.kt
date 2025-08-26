package com.example.usbcamapp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var cameraPreview: SurfaceView
    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    private lateinit var pendingIntent: PendingIntent
    private var uvcCameraHandler: UvcCameraHandler? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize UI components
        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        cameraPreview = findViewById(R.id.cameraPreview)
        
        // Initialize USB manager
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        
        // Create pending intent for USB permission
        val permissionIntent = Intent(ACTION_USB_PERMISSION)
        pendingIntent = PendingIntent.getBroadcast(this, 0, permissionIntent, PendingIntent.FLAG_IMMUTABLE)
        
        // Register BroadcastReceiver for USB permissions
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        // Set up button click listeners
        connectButton.setOnClickListener {
            connectToCamera()
        }
        
        disconnectButton.setOnClickListener {
            disconnectCamera()
        }
        
        // Set up SurfaceView callback
        cameraPreview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                // Surface is ready, start streaming if camera is initialized
                if (uvcCameraHandler != null) {
                    uvcCameraHandler?.startStreaming()
                    statusText.text = "Streaming started"
                }
            }
            
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                // Handle surface changes if needed
            }
            
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                // Surface is destroyed, stop streaming
                uvcCameraHandler?.stopStreaming()
                statusText.text = "Streaming stopped"
            }
        })
        
        // Check for connected USB devices
        checkForConnectedDevices()
    }
    
    private fun checkForConnectedDevices() {
        val deviceList = usbManager?.deviceList
        if (deviceList != null && deviceList.isNotEmpty()) {
            statusText.text = getString(R.string.camera_connected)
            // Get the first available device for now
            usbDevice = deviceList.values.firstOrNull()
        } else {
            statusText.text = getString(R.string.no_camera_detected)
        }
    }
    
    private fun connectToCamera() {
        if (usbDevice != null) {
            // Check if permission is already granted
            if (usbManager?.hasPermission(usbDevice) == true) {
                statusText.text = getString(R.string.permission_granted)
                initializeCameraStreaming()
            } else {
                // Request permission to access the USB device
                usbManager?.requestPermission(usbDevice, pendingIntent)
            }
        } else {
            statusText.text = getString(R.string.no_camera_detected)
        }
    }
    
    private fun disconnectCamera() {
        // Stop streaming and release camera
        uvcCameraHandler?.stopStreaming()
        uvcCameraHandler?.release()
        uvcCameraHandler = null
        
        // Hide preview and show connect button
        cameraPreview.visibility = View.GONE
        connectButton.visibility = View.VISIBLE
        disconnectButton.visibility = View.GONE
        
        // Update status
        statusText.text = "Camera disconnected"
    }
    
    private fun initializeCameraStreaming() {
        if (usbDevice != null && usbManager != null) {
            uvcCameraHandler = UvcCameraHandler(usbManager!!, usbDevice!!, cameraPreview.holder)
            
            if (uvcCameraHandler?.initializeCamera() == true) {
                statusText.text = "Camera initialized, starting stream..."
                // Show the camera preview
                cameraPreview.visibility = View.VISIBLE
                connectButton.visibility = View.GONE
                disconnectButton.visibility = View.VISIBLE
                
                // Start streaming if surface is already created
                if (cameraPreview.holder.surface != null && cameraPreview.holder.surface.isValid) {
                    uvcCameraHandler?.startStreaming()
                    statusText.text = "Streaming started"
                }
            } else {
                statusText.text = "Failed to initialize camera"
                Toast.makeText(this, "Failed to initialize camera", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // BroadcastReceiver to handle USB permission responses
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.apply {
                            statusText.text = getString(R.string.permission_granted)
                            initializeCameraStreaming()
                        }
                    } else {
                        statusText.text = getString(R.string.permission_denied)
                        Toast.makeText(context, "USB permission denied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        uvcCameraHandler?.release()
    }
    
    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.usbcamapp.USB_PERMISSION"
    }
}