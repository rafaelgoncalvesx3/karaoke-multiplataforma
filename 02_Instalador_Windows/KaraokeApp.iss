; Opcional: gere o instalador tradicional depois de publicar para ./publish
#define MyAppName "KaraokeApp"
#define MyAppVersion "1.0.0"
[Setup]
AppId={{91B27F81-75D7-4AA3-9076-A83A5BCFCE19}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
DefaultDirName={localappdata}\Programs\KaraokeApp
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir=InstaladorGerado
OutputBaseFilename=KaraokeApp_Setup_Windows_x64
Compression=lzma2
SolidCompression=yes
ArchitecturesAllowed=x64compatible
WizardStyle=modern
UninstallDisplayIcon={app}\KaraokeApp.exe
[Files]
Source: "publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "Samples\*"; DestDir: "{app}\Samples"; Flags: ignoreversion recursesubdirs createallsubdirs
[Icons]
Name: "{autoprograms}\KaraokeApp"; Filename: "{app}\KaraokeApp.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\KaraokeApp"; Filename: "{app}\KaraokeApp.exe"; WorkingDir: "{app}"; Tasks: desktopicon
[Tasks]
Name: "desktopicon"; Description: "Criar atalho na area de trabalho"; GroupDescription: "Atalhos:"; Flags: checkedonce
[Run]
Filename: "{app}\KaraokeApp.exe"; Description: "Abrir KaraokeApp"; Flags: nowait postinstall skipifsilent
