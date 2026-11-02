@echo off
rem Puts the TV back to its own home screen and screensaver. Glass Home stays installed unless you choose to remove it.
setlocal
call "%~dp0settings.bat"
"%ADB%" connect %TV_IP%:5555 >nul
"%ADB%" %T% shell "pm enable com.google.android.tvlauncher; pm enable com.google.android.apps.tv.launcherx; pm enable com.mitv.tvhome.atv; true" 2>nul
"%ADB%" %T% shell "settings delete secure screensaver_components"
choice /m "Also uninstall Glass Home"
if errorlevel 2 goto home
"%ADB%" %T% uninstall com.sriramteja.glasshome
:home
"%ADB%" %T% shell "input keyevent KEYCODE_HOME"
echo Done. If the TV asks which home screen to use, pick the TV's own one and choose Always.
pause
