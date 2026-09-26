# Executar pelo INSTALAR_NO_WINDOWS.cmd no Windows 10/11 64 bits.
# O SDK .NET 8 so e necessario durante a instalacao; a versao publicada inclui runtime.
[CmdletBinding()]
param(
    [string]$MediaRoot = '',
    [string]$FfplayPath = '',
    [switch]$SkipLaunch
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
function Info([string]$message) { Write-Host "[KaraokeApp] $message" -ForegroundColor Cyan }
function Fail([string]$message) { Write-Host "[ERRO] $message" -ForegroundColor Red; exit 1 }
function Get-Dotnet8 {
    $command = Get-Command dotnet -ErrorAction SilentlyContinue
    if (-not $command) { return $null }
    $versions = (& dotnet --list-sdks 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not ($versions | Where-Object { $_ -match '^8\.' })) { return $null }
    return $command.Source
}
try {
    if (-not [Environment]::Is64BitOperatingSystem) { Fail 'E necessario Windows 64 bits.' }
    $scriptFolder = Split-Path -Parent $MyInvocation.MyCommand.Path
    $projectFile = Join-Path $scriptFolder 'KaraokeApp.csproj'
    if (-not (Test-Path -LiteralPath $projectFile)) { Fail 'KaraokeApp.csproj ausente.' }

    $dotnet = Get-Dotnet8
    if (-not $dotnet) {
        Info 'SDK .NET 8 nao encontrado. Vou abrir a pagina oficial de download.'
        Start-Process 'https://dotnet.microsoft.com/download/dotnet/8.0'
        Write-Host 'Instale o .NET 8 SDK para Windows x64 e execute INSTALAR_NO_WINDOWS.cmd novamente.' -ForegroundColor Yellow
        exit 2
    }

    $ffplay = $null
    if ($FfplayPath) {
        if (-not (Test-Path -LiteralPath $FfplayPath -PathType Leaf)) { Fail "ffplay nao encontrado: $FfplayPath" }
        $ffplay = (Resolve-Path -LiteralPath $FfplayPath).Path
    } else {
        $ffCmd = Get-Command ffplay.exe -ErrorAction SilentlyContinue
        if ($ffCmd) { $ffplay = $ffCmd.Source }
        foreach ($candidate in @('C:\ffmpeg\bin\ffplay.exe', 'C:\Program Files\ffmpeg\bin\ffplay.exe')) {
            if (-not $ffplay -and (Test-Path -LiteralPath $candidate)) { $ffplay = $candidate }
        }
    }
    if (-not $ffplay) {
        Info 'FFplay nao encontrado. Ele e necessario para reproduzir musicas e videos.'
        Write-Host 'Instale FFmpeg (com ffplay.exe): https://ffmpeg.org/download.html' -ForegroundColor Yellow
        $answer = Read-Host 'Se ja tem ffplay.exe, informe o caminho completo (ou pressione Enter para instalar somente o KaraokeApp)'
        if ($answer.Trim(' ', '"')) {
            $answer = $answer.Trim(' ', '"')
            if (-not (Test-Path -LiteralPath $answer -PathType Leaf)) { Fail "ffplay nao encontrado: $answer" }
            $ffplay = (Resolve-Path -LiteralPath $answer).Path
        }
    }

    $publish = Join-Path $env:TEMP ('KaraokeApp-Publish-' + [Guid]::NewGuid().ToString('N'))
    New-Item -Path $publish -ItemType Directory -Force | Out-Null
    Info 'Compilando para Windows x64 e incluindo runtime .NET...'
    & $dotnet publish $projectFile -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:DebugType=none --output $publish
    if ($LASTEXITCODE -ne 0) { Fail 'Falha na compilacao. Verifique internet (NuGet) e mensagens acima.' }
    $builtExe = Join-Path $publish 'KaraokeApp.exe'
    if (-not (Test-Path -LiteralPath $builtExe -PathType Leaf)) { Fail 'Compilacao terminou sem gerar KaraokeApp.exe.' }

    $install = Join-Path $env:LOCALAPPDATA 'Programs\KaraokeApp'
    New-Item -Path $install -ItemType Directory -Force | Out-Null
    Get-ChildItem -LiteralPath $publish -File | Copy-Item -Destination $install -Force
    $installExe = Join-Path $install 'KaraokeApp.exe'
    if ($ffplay) {
        $ffplayDestination = Join-Path $install 'ffplay.exe'
        # Copiar apenas o binario nao garante funcionamento se o distribuidor exigir DLLs;
        # por isso usamos sempre o caminho original da instalacao FFmpeg.
        Set-Content -LiteralPath (Join-Path $install 'ffplay-path.txt') -Value $ffplay -Encoding UTF8
    }
    $samples = Join-Path $install 'Samples'
    New-Item -Path $samples -ItemType Directory -Force | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $scriptFolder 'Samples') -File | Copy-Item -Destination $samples -Force

    $launcher = Join-Path $install 'Iniciar-Karaoke.cmd'
    $ffArg = if ($ffplay) { ' --ffplay "' + $ffplay + '"' } else { '' }
    $rootArg = if ($MediaRoot) { ' --root "' + $MediaRoot + '"' } else { '' }
    $launcherLines = @('@echo off', 'chcp 65001 >nul', 'cd /d "%~dp0"', '"%~dp0KaraokeApp.exe"' + $ffArg + $rootArg, 'if errorlevel 1 pause')
    Set-Content -LiteralPath $launcher -Value $launcherLines -Encoding ASCII

    $shell = New-Object -ComObject WScript.Shell
    $desktop = [Environment]::GetFolderPath('Desktop')
    $startMenu = Join-Path ([Environment]::GetFolderPath('Programs')) 'KaraokeApp'
    New-Item -ItemType Directory -Path $startMenu -Force | Out-Null
    foreach ($link in @((Join-Path $desktop 'KaraokeApp.lnk'), (Join-Path $startMenu 'KaraokeApp.lnk'))) {
        $shortcut = $shell.CreateShortcut($link)
        $shortcut.TargetPath = $launcher
        $shortcut.WorkingDirectory = $install
        $shortcut.IconLocation = "$installExe,0"
        $shortcut.Description = 'KaraokeApp - catálogo, reprodução e pontuação de afinação'
        $shortcut.Save()
    }
    $uninstaller = Join-Path $install 'DESINSTALAR.cmd'
    @'
@echo off
setlocal
set "APP=%LOCALAPPDATA%\Programs\KaraokeApp"
del /q "%USERPROFILE%\Desktop\KaraokeApp.lnk" 2>nul
rmdir /s /q "%APPDATA%\Microsoft\Windows\Start Menu\Programs\KaraokeApp" 2>nul
start "" cmd /c "timeout /t 2 /nobreak >nul & rmdir /s /q ""%APP%"""
echo Desinstalacao agendada. Feche esta janela.
'@ | Set-Content -LiteralPath $uninstaller -Encoding ASCII
    Remove-Item -LiteralPath $publish -Force -Recurse -ErrorAction SilentlyContinue
    Info "Instalado em $install"
    Info 'Atalhos criados na Area de Trabalho e no menu Iniciar.'
    if (-not $ffplay) { Write-Host 'ATENCAO: Instale FFmpeg/FFplay e coloque ffplay.exe no PATH para reproduzir as faixas.' -ForegroundColor Yellow }
    if (-not $SkipLaunch) {
        $start = Read-Host 'Abrir KaraokeApp agora? [S/n]'
        if ($start -notmatch '^[Nn]') { Start-Process -FilePath $launcher -WorkingDirectory $install }
    }
    exit 0
} catch {
    Write-Host ("[ERRO] " + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
