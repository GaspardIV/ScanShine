1. W android studio - w idei pewnie podobnie:

Download the NDK and build tools
To compile and debug native code for your app, you need the following components:
The Android Native Development Kit (NDK): a toolset that allows you to use C and C++ code with Android, and provides platform libraries that allow you to manage native activities and access physical device components, such as sensors and touch input.

CMake: an external build tool that works alongside Gradle to build your native library. You do not need this component if you only plan to use ndk-build.

LLDB: the debugger Android Studio uses to debug native code.

You can install these components using the SDK Manager:
From an open project, select Tools > Android > SDK Manager from the menu bar.
Click the SDK Tools tab.
Check the boxes next to LLDB, CMake, and NDK,
