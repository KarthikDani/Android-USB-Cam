# USB Camera App

An Android application for streaming video from external UVC (USB Video Class) cameras via OTG (On-The-Go).

## Features

- Detects connected UVC cameras via USB OTG
- Requests necessary permissions from the user
- Displays live video feed from the camera
- Simple and intuitive user interface

## Prerequisites

- Android device with USB OTG support
- USB UVC camera
- USB OTG adapter (if your device doesn't have a USB-A port)

## Building the Application

1. Open the project in Android Studio
2. Sync the project with Gradle files
3. Build the project using `Build > Make Project`

## Installing the Application

1. Connect your Android device to your computer
2. Enable USB debugging on your Android device
3. In Android Studio, select `Run > Run 'app'`
4. Select your connected device from the list
5. The app will be installed and launched on your device

## Testing the Application

1. Connect your UVC camera to your Android device using a USB OTG adapter
2. Launch the USB Camera App
3. The app should automatically detect the connected camera
4. Tap the "Connect USB Camera" button
5. Grant USB permission when prompted
6. The camera preview should appear on the screen
7. To disconnect, tap the "Disconnect" button

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/usbcamapp/
│   │   │   ├── MainActivity.kt          # Main activity handling UI and USB communication
│   │   │   └── UvcCameraHandler.kt      # Handles UVC protocol and video streaming
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml    # Main layout file
│   │   │   ├── values/
│   │   │   │   ├── colors.xml           # Color definitions
│   │   │   │   ├── strings.xml          # String resources
│   │   │   │   └── themes.xml           # Theme definitions
│   │   │   └── xml/
│   │   │       └── device_filter.xml    # USB device filter for UVC cameras
│   │   └── AndroidManifest.xml          # Application manifest
│   └── build.gradle                     # App module build configuration
├── build.gradle                         # Project level build configuration
├── gradle.properties                    # Gradle properties
└── settings.gradle                      # Project settings
```

## Implementation Details

### USB Host Implementation

The app uses Android's USB Host API to communicate with the UVC camera:

1. Detects connected USB devices
2. Requests permission to access the USB device
3. Claims the UVC video streaming interface
4. Reads video data from the camera's video endpoint

### UVC Protocol Handling

The `UvcCameraHandler` class handles the UVC protocol:

1. Parses UVC headers to identify frame boundaries
2. Extracts complete video frames from the data stream
3. Simulates frame decoding and rendering (in a production app, this would use a library like FFmpeg)

### Video Streaming

The app displays the video stream with minimal latency:

1. Uses a `SurfaceView` for efficient video rendering
2. Implements a frame queue to buffer video frames
3. Renders frames on the main thread for smooth playback

### User Interface

The UI provides clear guidance through the connection process:

1. Status text indicating connection state
2. Connect/Disconnect buttons
3. Full-screen camera preview

## Limitations

This implementation is a demonstration of the core concepts. A production app would need:

1. Actual video decoding using a library like FFmpeg or MediaCodec
2. Support for different UVC formats (MJPEG, H.264, etc.)
3. Better error handling and recovery
4. Configuration options for resolution, frame rate, etc.
5. Support for multiple cameras

## Dependencies

- AndroidX libraries
- Mobile FFmpeg (for video decoding in a production app)
  
---
