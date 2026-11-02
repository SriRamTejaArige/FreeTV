@echo off
rem Shared settings for the FreeTV scripts.
rem Put your own values in settings.local.bat (same folder, not uploaded to GitHub), for example:
rem     set TV_IP=192.168.1.20
rem     set ADB=C:\platform-tools\adb.exe
if exist "%~dp0settings.local.bat" call "%~dp0settings.local.bat"

rem Your TV's IP address (TV: Settings > Network & Internet > your Wi-Fi).
if not defined TV_IP set TV_IP=192.168.1.20
rem adb from Android platform-tools: https://developer.android.com/tools/releases/platform-tools
if not defined ADB set ADB=adb
rem Only needed for building: the Android SDK and a JDK 17.
if not defined ANDROID_HOME set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not defined JAVA_HOME for /d %%J in ("C:\Program Files\Eclipse Adoptium\jdk-17*" "C:\Program Files\Java\jdk-17*" "C:\Program Files\Android\Android Studio\jbr") do if exist "%%~fJ\bin\java.exe" set "JAVA_HOME=%%~fJ"

set T=-s %TV_IP%:5555
set "ROOT=%~dp0.."
