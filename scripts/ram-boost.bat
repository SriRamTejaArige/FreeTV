@echo off
rem Frees memory on 1 GB TVs: stops streaming apps and the Play Store from running in the background,
rem and turns off two leftover setup helpers. Nothing is uninstalled. Undo: ram-undo.bat
setlocal
call "%~dp0settings.bat"
choice /m "Stop Netflix, Prime Video, YouTube, Hotstar and Play Store from running in the background"
if errorlevel 2 exit /b 0
"%ADB%" connect %TV_IP%:5555 >nul
"%ADB%" %T% shell "grep MemAvailable /proc/meminfo"
for %%P in (com.netflix.ninja com.amazon.amazonvideo.livingroom com.google.android.youtube.tv in.startv.hotstar com.android.vending) do (
  "%ADB%" %T% shell "cmd appops set %%P RUN_ANY_IN_BACKGROUND ignore; am force-stop %%P" 2>nul
)
"%ADB%" %T% shell "pm disable-user --user 0 com.google.android.onetimeinitializer; pm disable-user --user 0 com.google.android.partnersetup" 2>nul
timeout /t 5 /nobreak >nul
"%ADB%" %T% shell "grep MemAvailable /proc/meminfo"
echo Done. The apps still open normally when you choose them.
pause
