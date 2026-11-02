@echo off
rem Takes a screenshot of the TV and saves a short health report in the check folder.
setlocal
call "%~dp0settings.bat"
set "OUT=%ROOT%\check"
if not exist "%OUT%" mkdir "%OUT%"
"%ADB%" connect %TV_IP%:5555 >nul
"%ADB%" %T% exec-out screencap -p > "%OUT%\tv-screen.png"
> "%OUT%\tv-report.txt" (
  echo === Home screen
  "%ADB%" %T% shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1"
  echo === Glass Home version
  "%ADB%" %T% shell "dumpsys package com.sriramteja.glasshome | grep versionName"
  echo === Memory
  "%ADB%" %T% shell "grep -E 'MemTotal|MemAvailable|SwapFree' /proc/meminfo"
  echo === Temperature
  "%ADB%" %T% shell "cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null"
  echo === Biggest apps in memory
  "%ADB%" %T% shell "dumpsys meminfo | sed -n '/Total PSS by process/,/Total PSS by OOM/p' | head -15"
  echo === Recent crashes
  "%ADB%" %T% shell "logcat -d -b crash | tail -n 40"
)
echo Saved check\tv-screen.png and check\tv-report.txt
pause
