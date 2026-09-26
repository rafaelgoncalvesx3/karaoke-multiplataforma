@echo off
setlocal
cd /d "%~dp0"
echo Para gerar um Setup.exe tradicional, instale Inno Setup 6.
echo https://jrsoftware.org/isdl.php
echo.
echo Primeiro gere uma publicacao executando o comando:
echo dotnet publish KaraokeApp.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -o publish
echo.
echo Depois abra KaraokeApp.iss no Inno Setup e clique em Compile.
start "" "https://jrsoftware.org/isdl.php"
pause
