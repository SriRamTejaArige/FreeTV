@echo off
rem Installs Glass Home on the TV from this computer and makes it the home screen and screensaver.
rem (FreeTV's own Setup does the same from the phone.) Undo: restore-tv.bat
setlocal
call "%~dp0settings.bat"
set "APK=%ROOT%\dist\GlassHome.apk"
if not exist "%APK%" (echo dist\GlassHome.apk not found - run build.bat first, or download it from Releases into dist\. & pause & exit /b 1)
set G=com.sriramteja.glasshome

"%ADB%" connect %TV_IP%:5555
echo If the TV asks "Allow USB debugging?", choose Always allow, then press a key here.
pause >nul
echo Installing Glass Home...
"%ADB%" %T% install -r -g "%APK%" || (echo Install failed. & pause & exit /b 1)
echo Giving it its permissions...
"%ADB%" %T% shell "appops set %G% MANAGE_EXTERNAL_STORAGE allow; appops set %G% GET_USAGE_STATS allow; pm grant %G% android.permission.ACCESS_COARSE_LOCATION; mkdir -p /sdcard/Pictures/GlassHome; true"
echo Making it the home screen and the screensaver...
"%ADB%" %T% shell "cmd package set-home-activity --user 0 %G%/.HomeActivity; settings put secure screensaver_components %G%/.GlassDream; settings put secure screensaver_enabled 1; settings put secure screensaver_activate_on_sleep 1; true"

choice /m "Also turn off the TV's own home screen (recommended, so it does not come back)"
if errorlevel 2 goto home
"%ADB%" %T% shell "pm disable-user --user 0 com.google.android.tvlauncher; pm disable-user --user 0 com.google.android.apps.tv.launcherx; pm disable-user --user 0 com.mitv.tvhome.atv; true" 2>nul
:home
"%ADB%" %T% shell "input keyevent KEYCODE_WAKEUP; input keyevent KEYCODE_HOME"
echo.
echo Done. Wallpapers: copy pictures to the TV's Pictures\GlassHome folder, or use FreeTV's Walls tab.
pause
