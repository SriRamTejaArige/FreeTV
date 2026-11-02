@echo off
rem Builds both apps: Glass Home (TV) and FreeTV (phone, with Glass Home inside).
rem Results: dist\FreeTV.apk and dist\GlassHome.apk
setlocal
call "%~dp0settings.bat"
cd /d "%ROOT%"
echo Building Glass Home and FreeTV (the first build downloads Gradle and can take a few minutes)...
call gradlew.bat assembleRelease --console=plain
if errorlevel 1 goto failed
if not exist dist mkdir dist
copy /y glasshome\app\build\outputs\apk\release\glasshome-release.apk dist\GlassHome.apk >nul || goto failed
copy /y freetv\app\build\outputs\apk\release\freetv-release.apk dist\FreeTV.apk >nul || goto failed
echo.
echo Done: dist\FreeTV.apk (phone) and dist\GlassHome.apk (TV)
if not "%~1"=="nopause" pause
exit /b 0
:failed
echo.
echo Build failed. Check that JAVA_HOME points to JDK 17 and ANDROID_HOME to the Android SDK (scripts\settings.bat).
if not "%~1"=="nopause" pause
exit /b 1
