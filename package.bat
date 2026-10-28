@echo off
setlocal enabledelayedexpansion

:: ============================================================
::  GameFlow LB — Package into distributable ZIP
::  Produces:  backend\target\gameflow-lb-1.0.0.zip
::  Contents:  gameflow-lb.jar + run.bat + run.sh + README.txt
:: ============================================================

title GameFlow LB - Packaging

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

:: ── Locate Java 21+ JDK ────────────────────────────────────
set "JAVA_OK="

:: Try JAVA_HOME first
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" (
    for /f "tokens=3" %%v in ('"%JAVA_HOME%\bin\java.exe" -version 2^>^&1 ^| findstr /i "version"') do (
        set "JVER=%%~v"
        for /f "delims=." %%a in ("!JVER!") do (
            if %%a GEQ 21 set "JAVA_OK=1"
        )
    )
)

:: Scan common JDK install paths
if not defined JAVA_OK (
    for /d %%D in (
        "C:\Program Files\Eclipse Adoptium\jdk-2*"
        "C:\Program Files\Java\jdk-2*"
        "C:\Program Files\Microsoft\jdk-2*"
        "C:\Program Files\BellSoft\*-2*"
        "C:\Program Files\Amazon Corretto\jdk2*"
    ) do (
        if not defined JAVA_OK (
            if exist "%%~fD\bin\javac.exe" (
                set "JAVA_HOME=%%~fD"
                set "PATH=%%~fD\bin;!PATH!"
                set "JAVA_OK=1"
            )
        )
    )
)

if not defined JAVA_OK (
    echo [ERROR] Java 21+ JDK is required ^(must have javac^). >&2
    echo         Set JAVA_HOME or install a JDK. >&2
    exit /b 1
)

echo [INFO] Using JAVA_HOME=!JAVA_HOME!

:: ── Check Maven ─────────────────────────────────────────────
where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven ^(mvn^) is required on PATH to build the project. >&2
    exit /b 1
)

echo.
echo  ╔═══════════════════════════════════════════════╗
echo  ║  Building GameFlow LB distributable ZIP...    ║
echo  ╚═══════════════════════════════════════════════╝
echo.

cd /d "%ROOT%\backend"
call mvn clean package -Pbundle -DskipTests

if errorlevel 1 (
    echo.
    echo [ERROR] Build failed. See output above. >&2
    exit /b 1
)

echo.
echo ═══════════════════════════════════════════════════
echo  BUILD SUCCESSFUL
echo.
echo  ZIP:  %ROOT%\backend\target\gameflow-lb-1.0.0.zip
echo.
echo  To run:
echo    1. Unzip gameflow-lb-1.0.0.zip
echo    2. Run:   gameflow-lb-1.0.0\run.bat
echo    3. Open:  http://localhost:8080
echo ═══════════════════════════════════════════════════
echo.

endlocal
