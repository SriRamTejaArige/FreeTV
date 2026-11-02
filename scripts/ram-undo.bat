@echo off
rem Undoes ram-boost.bat.
setlocal
call "%~dp0settings.bat"
"%ADB%" connect %TV_IP%:5555 >nul
for %%P in (com.netflix.ninja com.amazon.amazonvideo.livingroom com.google.android.youtube.tv in.startv.hotstar com.android.vending) do (
  "%ADB%" %T% shell "cmd appops set %%P RUN_ANY_IN_BACKGROUND allow" 2>nul
)
"%ADB%" %T% shell "pm enable com.google.android.onetimeinitializer; pm enable com.google.android.partnersetup" 2>nul
echo Undone.
pause
