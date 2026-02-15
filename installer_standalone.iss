[Setup]
AppName=Desktop Calendar
AppVersion=1.0
DefaultDirName={autopf}\DesktopCalendar
DefaultGroupName=Desktop Calendar
UninstallDisplayIcon={app}\DesktopCalendar.exe
Compression=lzma2
SolidCompression=yes
OutputDir=installer_output
OutputBaseFilename=DesktopCalendar_Setup
; SetupIconFile=scripts\calendar.ico

[Files]
Source: "dist\DesktopCalendar.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\Desktop Calendar"; Filename: "{app}\DesktopCalendar.exe"
Name: "{group}\Uninstall Desktop Calendar"; Filename: "{uninstallexe}"
Name: "{autodesktop}\Desktop Calendar"; Filename: "{app}\DesktopCalendar.exe"; Tasks: desktopicon

[Tasks]
Name: desktopicon; Description: "Create a &desktop icon"; GroupDescription: "Additional icons:"

[Run]
Filename: "{app}\DesktopCalendar.exe"; Description: "Launch Desktop Calendar"; Flags: postinstall nowait skipifsilent