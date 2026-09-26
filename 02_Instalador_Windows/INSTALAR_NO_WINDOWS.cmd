@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
echo ===============================================
echo        INSTALADOR KARAOKE APP - WINDOWS
echo ===============================================
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Instalar-Karaoke.ps1"
set "RESULTADO=%ERRORLEVEL%"
echo.
if not "%RESULTADO%"=="0" (
  echo A instalacao nao foi concluida. Veja as mensagens acima.
) else (
  echo Instalacao concluida. Abra KaraokeApp pelo atalho na Area de Trabalho.
)
echo.
pause
exit /b %RESULTADO%
