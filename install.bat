@echo off
rem Install the client into the desktop (Windows).
rem
rem Usage: install.bat              install for the current user
rem        install.bat --uninstall  undo everything this script did
rem
rem What installing means here: this folder stays where it is and keeps
rem working exactly as before; what gets installed is the desktop wiring
rem around it, all per-user (no admin prompt):
rem
rem   - a Start Menu entry ("RSCD Community Client")
rem   - the rscd:// link type, handled by this client -- so the community
rem     website's Join Now buttons launch the game pointed at the right world
rem
rem The rscd:// part is the reason this script exists. A join link is just
rem     rscd://host:port/?name=World%20Name
rem and the client takes it as a command-line argument; registering the
rem scheme in HKEY_CURRENT_USER\Software\Classes is what makes the browser
rem hand it over. Everything lands under the current user, so no
rem administrator rights are needed and uninstalling cannot affect anyone
rem else on the machine.
rem
rem If you move this folder or change your Java installation, run this again
rem -- the registry keys hold absolute paths from install time.

setlocal
cd /d "%~dp0"
set "HERE=%CD%"
set "STARTMENU=%APPDATA%\Microsoft\Windows\Start Menu\Programs"
set "SHORTCUT=%STARTMENU%\RSCD Community Client.lnk"

if "%~1"=="--uninstall" (
   reg delete "HKCU\Software\Classes\rscd" /f >nul 2>&1
   if exist "%SHORTCUT%" del "%SHORTCUT%"
   echo Removed the Start Menu entry and the rscd:// registration.
   echo This folder itself is untouched -- delete it if you want it gone too.
   exit /b 0
)

rem javaw.exe is the windowless launcher -- the right thing behind a clicked
rem link, where a console window flashing up would just be confusing. Found
rem through JAVA_HOME first, then PATH.
set "JAVAW="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVAW=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVAW (
   for /f "delims=" %%J in ('where javaw 2^>nul') do if not defined JAVAW set "JAVAW=%%J"
)
if not defined JAVAW (
   echo.
   echo Java was not found, and the desktop wiring needs its real location. 1>&2
   echo.
   echo   Download Java from https://adoptium.net, 1>&2
   echo   or in a terminal:  winget install EclipseAdoptium.Temurin.21.JRE 1>&2
   echo.
   echo then run install.bat again. 1>&2
   exit /b 1
)

if not exist rscd-client.jar (
   echo Note: rscd-client.jar is not built yet. Run run.bat once first ^(it
   echo builds the jar if a JDK is installed^), or unzip a release download
   echo over this folder, then run install.bat again.
   exit /b 1
)

rem The rscd:// scheme, per-user. The command is what the browser runs with
rem the clicked link in place of %%1.
reg add "HKCU\Software\Classes\rscd" /ve /t REG_SZ /d "URL:RSCD Community" /f >nul || goto :regfail
reg add "HKCU\Software\Classes\rscd" /v "URL Protocol" /t REG_SZ /d "" /f >nul || goto :regfail
reg add "HKCU\Software\Classes\rscd\shell\open\command" /ve /t REG_SZ /d "\"%JAVAW%\" -jar \"%HERE%\rscd-client.jar\" \"%%1\"" /f >nul || goto :regfail

rem Start Menu shortcut, made through WScript.Shell -- the one shortcut API
rem that is always present, no PowerShell execution policy involved.
set "VBS=%TEMP%\rscd-shortcut.vbs"
(
   echo Set s = CreateObject^("WScript.Shell"^).CreateShortcut^("%SHORTCUT%"^)
   echo s.TargetPath = "%JAVAW%"
   echo s.Arguments = "-jar ""%HERE%\rscd-client.jar"""
   echo s.WorkingDirectory = "%HERE%"
   echo s.Description = "RuneScape Classic community client"
   echo s.Save
) > "%VBS%"
cscript //nologo "%VBS%" >nul
del "%VBS%"

echo Installed for %USERNAME%:
echo   - Start Menu: RSCD Community Client
echo   - rscd:// links now open this client.
echo.
echo Try it: click a Join Now link on a community website, or run
echo   start rscd://127.0.0.1:43594/?name=Local%%20Test
echo.
echo To undo: install.bat --uninstall
exit /b 0

:regfail
echo Could not write to HKEY_CURRENT_USER\Software\Classes -- something on 1>&2
echo this machine is blocking per-user registry writes. 1>&2
exit /b 1
