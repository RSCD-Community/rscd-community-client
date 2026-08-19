@echo off
rem Launch the desktop client.
rem
rem Usage: run.bat [settings.ini]
rem
rem Everything is relative to this script, so a fresh git clone runs without
rem editing anything. Java is found through JDK, then JAVA_HOME, then PATH.
rem If rscd-client.jar is missing and a JDK is installed, this builds it
rem first, so "clone and run.bat" is the whole setup.
rem
rem No --add-opens, no lib\ directory, no JDK pin. The client used to need all
rem three because XStream reflected into java.base to read the definition
rem files; it reads them itself now (client\util\XmlObjects), so any Java 8 or
rem later runs it as-is.

setlocal
cd /d "%~dp0"

if defined JDK (
   set "JAVA=%JDK%\bin\java.exe"
   set "JAVAC=%JDK%\bin\javac.exe"
) else if defined JAVA_HOME (
   set "JAVA=%JAVA_HOME%\bin\java.exe"
   set "JAVAC=%JAVA_HOME%\bin\javac.exe"
) else (
   set "JAVA=java"
   set "JAVAC=javac"
)

"%JAVA%" -version >nul 2>&1
if errorlevel 1 (
   echo.
   echo The client needs Java, and java was not found on this machine. 1>&2
   echo.
   echo   Download it from https://adoptium.net, 1>&2
   echo   or in a terminal:  winget install EclipseAdoptium.Temurin.21.JRE 1>&2
   echo.
   echo If Java is already installed, set JAVA_HOME to its folder, e.g. 1>&2
   echo   set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jre-21" 1>&2
   exit /b 1
)

rem A release download ships rscd-client.jar; a bare git clone does not. If it
rem is missing but a compiler is around, build it now rather than telling the
rem user to go run another script first.
if not exist rscd-client.jar (
   "%JAVAC%" -version >nul 2>&1
   if errorlevel 1 (
      echo.
      echo rscd-client.jar is missing, and there is no Java compiler ^(JDK^) 1>&2
      echo here to build it with. Two ways forward, either is fine: 1>&2
      echo.
      echo   1. Download a release zip -- it includes rscd-client.jar prebuilt. 1>&2
      echo   2. Install a JDK ^(https://adoptium.net^) and run this script 1>&2
      echo      again; it will build the jar itself. 1>&2
      exit /b 1
   )
   echo rscd-client.jar is not built yet -- building it now ^(first run only^)...
   call build.bat
   if errorlevel 1 exit /b 1
)

rem Arguments after -jar belong to the client (the settings file). System
rem properties have to go before it, so they come through JAVA_OPTS:
rem   set JAVA_OPTS=-Drscd.cacheurl=http://localhost:8137/cache_data
"%JAVA%" %JAVA_OPTS% -jar rscd-client.jar %*
if errorlevel 1 (
   echo.
   echo The client exited with an error -- the messages above say why. If it
   echo mentions a missing or unsupported Java, install Java 8 or later from
   echo https://adoptium.net
   exit /b 1
)
