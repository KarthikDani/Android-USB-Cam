package com.example.usbcamapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

class UvcCameraHandler(
    private val usbManager: UsbManager,
    private val usbDevice: UsbDevice,
    private val surfaceHolder: SurfaceHolder
) {
    private var deviceConnection: UsbDeviceConnection? = null
    private var cameraInterface: UsbInterface? = null
    private var videoEndpoint: UsbEndpoint? = null
    
    private var isStreaming = false
    private var streamingThread: Thread? = null
    private val frameQueue = LinkedBlockingQueue<ByteArray>(5)
    private val renderHandler = Handler(Looper.getMainLooper())
    
    // UVC header constants
    private val UVC_HEADER_SIZE = 12
    private val UVC_PAYLOAD_HEADER_SIZE = 2
    
    // Frame buffer for accumulating data
    private var frameBuffer = ByteArray(1024 * 1024 * 2) // 2MB buffer
    private var frameBufferPosition = 0
    
    companion object {
        private const val TAG = "UvcCameraHandler"
        private const val USB_INTERFACE_CLASS_VIDEO = 0x0E
        private const val USB_INTERFACE_SUBCLASS_VIDEO_CONTROL = 0x01
        private const val USB_INTERFACE_SUBCLASS_VIDEO_STREAMING = 0x02
    }
    
    fun initializeCamera(): Boolean {
        try {
            // Open USB device connection
            deviceConnection = usbManager.openDevice(usbDevice)
            if (deviceConnection == null) {
                Log.e(TAG, "Failed to open USB device")
                return false
            }
            
            // Find the video streaming interface
            for (i in 0 until usbDevice.interfaceCount) {
                val usbInterface = usbDevice.getInterface(i)
                if (usbInterface.interfaceClass == USB_INTERFACE_CLASS_VIDEO &&
                    usbInterface.interfaceSubclass == USB_INTERFACE_SUBCLASS_VIDEO_STREAMING) {
                    cameraInterface = usbInterface
                    break
                }
            }
            
            if (cameraInterface == null) {
                Log.e(TAG, "No video streaming interface found")
                return false
            }
            
            // Claim the interface
            if (!deviceConnection!!.claimInterface(cameraInterface, true)) {
                Log.e(TAG, "Failed to claim interface")
                return false
            }
            
            // Find the video endpoint
            for (i in 0 until cameraInterface!!.endpointCount) {
                val endpoint = cameraInterface!!.getEndpoint(i)
                if (endpoint.type == UsbEndpoint.TYPE_BULK || endpoint.type == UsbEndpoint.TYPE_ISOCHRONOUS) {
                    videoEndpoint = endpoint
                    break
                }
            }
            
            if (videoEndpoint == null) {
                Log.e(TAG, "No video endpoint found")
                return false
            }
            
            Log.d(TAG, "Camera initialized successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing camera", e)
            return false
        }
    }
    
    fun startStreaming() {
        if (isStreaming) return
        
        isStreaming = true
        frameBufferPosition = 0
        
        // Clear the frame queue
        frameQueue.clear()
        
        streamingThread = Thread(StreamingRunnable())
        streamingThread?.start()
    }
    
    fun stopStreaming() {
        isStreaming = false
        streamingThread?.interrupt()
        streamingThread = null
    }
    
    fun release() {
        stopStreaming()
        
        try {
            deviceConnection?.releaseInterface(cameraInterface)
            deviceConnection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera", e)
        }
        
        deviceConnection = null
        cameraInterface = null
        videoEndpoint = null
    }
    
    private inner class StreamingRunnable : Runnable {
        override fun run() {
            val buffer = ByteBuffer.allocate(1024 * 1024) // 1MB buffer
            
            while (isStreaming && !Thread.currentThread().isInterrupted) {
                try {
                    // Read data from the USB endpoint
                    val bytesRead = deviceConnection?.bulkTransfer(
                        videoEndpoint,
                        buffer.array(),
                        buffer.capacity(),
                        1000
                    ) ?: -1
                    
                    if (bytesRead > 0) {
                        // Process the received data
                        processVideoData(buffer.array(), bytesRead)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in streaming loop", e)
                    break
                }
            }
        }
    }
    
    private fun processVideoData(data: ByteArray, length: Int) {
        // Append new data to frame buffer
        if (frameBufferPosition + length > frameBuffer.size) {
            // Resize frame buffer if needed
            val newBuffer = ByteArray(frameBuffer.size * 2)
            System.arraycopy(frameBuffer, 0, newBuffer, 0, frameBufferPosition)
            frameBuffer = newBuffer
        }
        
        System.arraycopy(data, 0, frameBuffer, frameBufferPosition, length)
        frameBufferPosition += length
        
        // Try to parse frames from the buffer
        parseFrames()
    }
    
    private fun parseFrames() {
        var offset = 0
        
        while (offset + UVC_HEADER_SIZE < frameBufferPosition) {
            // Check if we have a valid UVC header
            if (frameBuffer[offset] >= UVC_HEADER_SIZE) {
                val headerLength = frameBuffer[offset].toInt()
                val payloadLength = ((frameBuffer[offset + 3].toInt() and 0xFF) shl 8) or
                                   (frameBuffer[offset + 2].toInt() and 0xFF)
                
                // Check if we have a complete frame
                if (offset + headerLength + payloadLength <= frameBufferPosition) {
                    // Extract the frame data
                    val frameData = ByteArray(headerLength + payloadLength)
                    System.arraycopy(frameBuffer, offset, frameData, 0, headerLength + payloadLength)
                    
                    // Add to queue for rendering
                    if (!frameQueue.offer(frameData)) {
                        frameQueue.poll() // Remove oldest frame
                        frameQueue.offer(frameData) // Add new frame
                    }
                    
                    // Move offset past this frame
                    offset += headerLength + payloadLength
                    
                    // Render the frame
                    renderFrame(frameData)
                } else {
                    // Not enough data for a complete frame, break and wait for more data
                    break
                }
            } else {
                // Invalid header, skip byte
                offset++
            }
        }
        
        // Move remaining data to the beginning of the buffer
        if (offset > 0 && offset < frameBufferPosition) {
            System.arraycopy(frameBuffer, offset, frameBuffer, 0, frameBufferPosition - offset)
            frameBufferPosition -= offset
        } else if (offset >= frameBufferPosition) {
            frameBufferPosition = 0
        }
    }
    
    private fun renderFrame(frameData: ByteArray) {
        // In a real implementation, you would decode the video frame here
        // For this example, we'll just create a dummy bitmap and render it
        
        renderHandler.post {
            try {
                val canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    // Create a dummy bitmap for demonstration
                    val bitmap = createDummyFrame(frameData.size)
                    
                    // Draw the bitmap to the canvas
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    
                    // Unlock and post the canvas
                    surfaceHolder.unlockCanvasAndPost(canvas)
                    
                    // Recycle the bitmap
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rendering frame", e)
            }
        }
    }
    
    private fun createDummyFrame(dataSize: Int): Bitmap {
        val width = 640
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        
        // Fill background
        paint.color = Color.BLACK
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        // Draw some dummy content
        paint.color = Color.GREEN
        paint.textSize = 48f
        canvas.drawText("USB Camera App", 50f, 100f, paint)
        
        paint.textSize = 36f
        canvas.drawText("Frame Size: ${dataSize} bytes", 50f, 180f, paint)
        
        // Draw a rectangle that changes color based on frame size
        paint.color = when {
            dataSize < 1000 -> Color.RED
            dataSize < 5000 -> Color.YELLOW
            else -> Color.GREEN
        }
        canvas.drawRect(50f, 250f, 590f, 400f, paint)
        
        return bitmap
    }
}