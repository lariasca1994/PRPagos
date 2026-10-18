; Instalador de PRPagos Escritorio (Inno Setup 6, gratuito). Lo compila
; empaquetar.ps1 -Instalador (y el workflow escritorio.yml) a partir de la
; carpeta build\dist\PRPagos que arma jpackage.
;
; - Automático: no hace preguntas. Instala para el usuario actual (sin permisos
;   de administrador) en %LOCALAPPDATA%\Programs\PRPagos, crea los accesos
;   directos y abre la aplicación al terminar.
; - Con desinstalador: queda en Configuración > Aplicaciones instaladas y en el
;   menú Inicio ("Desinstalar PRPagos"). Desinstalar borra la aplicación, sus
;   registros de uso y sus preferencias; la cuenta y los datos siguen en la web.
; - Versiones futuras: el mismo AppId actualiza la instalación en su lugar; la
;   actualización automática (instalar.ps1) conserva el desinstalador y deja la
;   versión al día en Aplicaciones instaladas.
; - Si encuentra una instalación anterior hecha con el instalador MSI de
;   jpackage, la desinstala primero para que no queden dos.

#ifndef Version
  #define Version "1.0.0"
#endif

[Setup]
AppId={{B6F0B0C2-5C1E-4D4B-9E1F-7A2C3D4E5F60}
AppName=PRPagos
AppVersion={#Version}
AppVerName=PRPagos {#Version}
AppPublisher=PRPagos
AppComments=Control de ingresos y gastos con presupuesto y plan 50/30/20
VersionInfoVersion={#Version}
DefaultDirName={localappdata}\Programs\PRPagos
DefaultGroupName=PRPagos
PrivilegesRequired=lowest
DisableWelcomePage=yes
DisableDirPage=yes
DisableProgramGroupPage=yes
DisableReadyPage=yes
DisableFinishedPage=yes
DisableStartupPrompt=yes
CloseApplications=no
RestartApplications=no
SetupIconFile=resources\icono.ico
UninstallDisplayIcon={app}\PRPagos.exe
UninstallDisplayName=PRPagos
OutputDir=build\dist
OutputBaseFilename=PRPagos-{#Version}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UsedUserAreasWarning=no

[Languages]
Name: "es"; MessagesFile: "compiler:Languages\Spanish.isl"

[InstallDelete]
; Al actualizar, sin restos de la versión anterior (librerías o Java viejos).
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "build\dist\PRPagos\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{userprograms}\PRPagos\PRPagos"; Filename: "{app}\PRPagos.exe"
Name: "{userprograms}\PRPagos\Desinstalar PRPagos"; Filename: "{uninstallexe}"
Name: "{userdesktop}\PRPagos"; Filename: "{app}\PRPagos.exe"

[Run]
Filename: "{app}\PRPagos.exe"; Flags: nowait skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}"
Type: filesandordirs; Name: "{userappdata}\PRPagos"

[Code]
const
  RAIZ_DESINSTALAR = 'Software\Microsoft\Windows\CurrentVersion\Uninstall';

procedure CerrarPRPagos();
var
  Codigo: Integer;
begin
  { PRPagos.exe abierto bloquearía los archivos: se cierra sin preguntar. }
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /T /IM PRPagos.exe', '', SW_HIDE,
    ewWaitUntilTerminated, Codigo);
  Sleep(800);
end;

procedure QuitarInstalacionMsiAnterior();
var
  Claves: TArrayOfString;
  I, Codigo: Integer;
  Nombre, Desinstalar: String;
begin
  { El instalador anterior (jpackage, MSI por usuario) registraba "PRPagos" con su
    propio código de producto: se desinstala en silencio para que no queden dos. }
  if not RegGetSubkeyNames(HKCU, RAIZ_DESINSTALAR, Claves) then
    Exit;
  for I := 0 to GetArrayLength(Claves) - 1 do
  begin
    if RegQueryStringValue(HKCU, RAIZ_DESINSTALAR + '\' + Claves[I], 'DisplayName', Nombre)
      and (Nombre = 'PRPagos')
      and RegQueryStringValue(HKCU, RAIZ_DESINSTALAR + '\' + Claves[I], 'UninstallString', Desinstalar)
      and (Pos('msiexec', Lowercase(Desinstalar)) > 0) then
    begin
      Exec(ExpandConstant('{sys}\msiexec.exe'), '/x ' + Claves[I] + ' /qn /norestart', '', SW_HIDE,
        ewWaitUntilTerminated, Codigo);
    end;
  end;
end;

function PrepareToInstall(var NecesitaReiniciar: Boolean): String;
begin
  CerrarPRPagos();
  QuitarInstalacionMsiAnterior();
  Result := '';
end;

function InitializeUninstall(): Boolean;
begin
  CerrarPRPagos();
  Result := True;
end;

procedure CurUninstallStepChanged(Paso: TUninstallStep);
begin
  if Paso = usPostUninstall then
    { Preferencias de la aplicación (tema claro/oscuro). }
    RegDeleteKeyIncludingSubkeys(HKCU, 'Software\JavaSoft\Prefs\com\prpagos');
end;
