@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Karaoke TV - Android APK
where gradle >nul 2>nul
if errorlevel 1 (
 echo Gradle nao encontrado. Abra esta pasta pelo Android Studio.
 echo Selecione Build - Build APK(s) ou instale o Gradle 8.9 e Android SDK 35.
 pause
 exit /b 1
)
call gradle :app:assembleDebug
if errorlevel 1 (
 echo Erro durante a compilacao. Confirme Android SDK 35 e Java 17.
 pause
 exit /b 1
)
echo APK: app\build\outputs\apk\debug\app-debug.apk
pause
