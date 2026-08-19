@echo off
rem Compile the client and repack the jar run.bat launches.
rem
rem The Windows twin of build.sh -- same steps, same checks, same output. This
rem exists because "javac -d build" alone changes nothing a player sees:
rem run.bat runs rscd-client.jar, so a build that stops at build\ leaves the
rem old jar in place and the new code apparently does not work.
rem
rem Any JDK 8 or later will do; set JDK or JAVA_HOME to choose one. The class
rem files target Java 8 either way (--release 8 on a modern JDK,
rem -source/-target on JDK 8 itself, which predates the --release flag), so the
rem packed jar runs on whatever JRE a player already has. Set RELEASE to
rem change the target.
rem
rem Everything is relative to this script, so a fresh git clone builds with no
rem editing.

setlocal enabledelayedexpansion
cd /d "%~dp0"

if defined JDK (
   set "JBIN=%JDK%\bin\"
) else if defined JAVA_HOME (
   set "JBIN=%JAVA_HOME%\bin\"
) else (
   set "JBIN="
)

set "JAVAC=%JBIN%javac"
set "JAVA=%JBIN%java"
set "JAR=%JBIN%jar"
if not defined RELEASE set "RELEASE=8"

"%JAVAC%" -version >nul 2>&1
if errorlevel 1 (
   echo.
   echo Building needs a JDK ^(the Java compiler^), and javac was not found. 1>&2
   echo.
   echo   Download one from https://adoptium.net ^(pick the JDK, not the JRE^), 1>&2
   echo   or in a terminal:  winget install EclipseAdoptium.Temurin.21.JDK 1>&2
   echo.
   echo If a JDK is already installed, set JAVA_HOME to its folder, e.g. 1>&2
   echo   set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21" 1>&2
   echo.
   echo Only playing, not changing the code? You do not need to build at all: 1>&2
   echo run.bat runs the rscd-client.jar that ships with a release. 1>&2
   exit /b 1
)

rem JDK 8's javac has no --release flag (it arrived in JDK 9), so pick the
rem spelling this javac understands. javac -version prints "javac 1.8.0_431"
rem on 8 and "javac 17.0.2" on later ones.
set "JAVAC_VER="
for /f "tokens=2" %%V in ('"%JAVAC%" -version 2^>^&1') do if not defined JAVAC_VER set "JAVAC_VER=%%V"
if "%JAVAC_VER:~0,2%"=="1." (
   set "TARGET_FLAGS=-source %RELEASE% -target %RELEASE%"
) else (
   set "TARGET_FLAGS=--release %RELEASE%"
)

if exist build rmdir /s /q build
mkdir build

rem javac takes an argument file; dir /s /b is the batch equivalent of find.
rem
rem Two things bite here, and both only show up in a checkout whose path has a
rem space in it:
rem
rem   1. javac splits argument files on whitespace, so each path must be quoted;
rem   2. inside those quotes javac treats '\' as an escape character, so a
rem      Windows path comes back out with its separators eaten.
rem
rem Quoting plus forward slashes satisfies both. Windows javac accepts '/'
rem happily. Without this the failure is a baffling "invalid flag" or "invalid
rem filename" naming half a directory.
if exist "%TEMP%\rscd-client-sources.txt" del "%TEMP%\rscd-client-sources.txt"
for /f "delims=" %%F in ('dir /s /b src\*.java') do (
   set "SRC=%%F"
   echo "!SRC:\=/!">> "%TEMP%\rscd-client-sources.txt"
)
"%JAVAC%" -nowarn %TARGET_FLAGS% -d build "@%TEMP%\rscd-client-sources.txt"
if errorlevel 1 (
   echo.
   echo The compile failed -- the errors above name the file and line. 1>&2
   exit /b 1
)
del "%TEMP%\rscd-client-sources.txt"

if exist rscd-client.jar del rscd-client.jar
"%JAR%" cfm rscd-client.jar manifest.txt -C build .
if errorlevel 1 exit /b 1

rem The client has no dependencies any more. If a Class-Path ever comes back
rem the jar has stopped standing alone, and run.bat's bare "java -jar" would
rem fail in the field instead of here. build.sh reads this back out of the
rem packed jar; checking the source manifest is the same guarantee and does not
rem need unzip on PATH.
findstr /b /c:"Class-Path:" manifest.txt >nul
if not errorlevel 1 (
   echo FAIL: manifest declares a Class-Path; this jar is meant to stand alone 1>&2
   exit /b 1
)

rem A real load, because javap normalises separators and would pass anyway.
rem The one-class checker is generated here rather than kept as a stray .java
rem in the repository root; build-check\ keeps it off the jar's own classpath,
rem so the classes below genuinely resolve from the jar, not from build\.
if exist build-check rmdir /s /q build-check
mkdir build-check
(
   echo public class JarLoads {
   echo    public static void main^(String[] args^) throws Exception {
   echo       Class.forName^(args[0], false, JarLoads.class.getClassLoader^(^)^);
   echo    }
   echo }
) > build-check\JarLoads.java
"%JAVAC%" -nowarn -d build-check build-check\JarLoads.java
if errorlevel 1 exit /b 1

for %%C in (
   org.rscdaemon.client.mudclient
   org.rscdaemon.client.ScriptPanel
   org.rscdaemon.client.ScriptRunner
   org.rscdaemon.client.WorldsPanel
   org.rscdaemon.client.util.XmlObjects
) do (
   "%JAVA%" -cp "build-check;rscd-client.jar" JarLoads %%C
   if errorlevel 1 (
      echo FAIL: %%C did not load from the packed jar 1>&2
      exit /b 1
   )
)
rmdir /s /q build-check

rem Counted through a temp file rather than a pipe inside for /f: every form of
rem for /f ('...') mangles the quotes around a %JAR% that lives in
rem "C:\Program Files", and the failure is silent -- it just reports 0 classes.
rem find is a system command with no spaces in its path, so it is safe to pipe.
"%JAR%" tf rscd-client.jar > "%TEMP%\rscd-client-jarlist.txt"
for /f %%N in ('find /c ".class" ^< "%TEMP%\rscd-client-jarlist.txt"') do set "COUNT=%%N"
del "%TEMP%\rscd-client-jarlist.txt"
echo rscd-client.jar repacked: %COUNT% classes, no dependencies
echo Run run.bat to play.
