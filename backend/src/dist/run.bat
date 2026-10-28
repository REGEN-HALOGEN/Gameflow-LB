@echo off
setlocal enabledelayedexpansion

:: ============================================================
::  GameFlow LB — Run from distributable ZIP
::  Requires: Java 21+ on PATH, JAVA_HOME, or common JDK dirs
:: ============================================================

title GameFlow LB

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

set "JAR_PATH=%ROOT%\gameflow-lb.jar"

:: 1) Verify that the JAR file exists
if not exist "%JAR_PATH%" (
    echo.
    echo ============================================================
    echo [ERROR] gameflow-lb.jar not found!
    echo Looked for: "%JAR_PATH%"
    echo.
    echo If you opened this file directly from inside the ZIP file:
    echo 1. Right-click the ZIP and choose "Extract All..."
    echo 2. Open the extracted folder
    echo 3. Double-click run.bat from inside the extracted folder
    echo ============================================================
    echo.
    pause
    exit /b 1
)

:: 2) Locate Java 21+
set "JAVA_CMD="
set "JAVA_OK="
set "CURRENT_JAVA_VER="

:: Check 1: 'java' on PATH
where java >nul 2>&1
if not errorlevel 1 (
    for /f "tokens=3" %%v in ('^"java -version 2^>^&1 ^| findstr /i "version"^"') do (
        set "CURRENT_JAVA_VER=%%~v"
        for /f "delims=." %%a in ("!CURRENT_JAVA_VER!") do (
            if %%a GEQ 21 (
                set "JAVA_CMD=java"
                set "JAVA_OK=1"
            )
        )
    )
)

:: Check 2: JAVA_HOME if set
if not defined JAVA_OK (
    if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
        for /f "tokens=3" %%v in ('^""%JAVA_HOME%\bin\java.exe" -version 2^>^&1 ^| findstr /i "version"^"') do (
            set "JVER=%%~v"
            for /f "delims=." %%a in ("!JVER!") do (
                if %%a GEQ 21 (
                    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
                    set "JAVA_OK=1"
                )
            )
        )
    )
)

:: Check 3: Common Java 21+ installation locations on Windows
if not defined JAVA_OK (
    for /d %%D in (
        "C:\Program Files\Eclipse Adoptium\jdk-2*"
        "C:\Program Files\Eclipse Adoptium\jre-2*"
        "C:\Program Files\Java\jdk-2*"
        "C:\Program Files\Java\jre-2*"
        "C:\Program Files\Microsoft\jdk-2*"
        "C:\Program Files\BellSoft\*-2*"
        "C:\Program Files\Amazon Corretto\jdk2*"
        "C:\Program Files\Zulu\zulu-2*"
        "C:\Program Files\Semeru\*-2*"
        "%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-2*"
        "%LOCALAPPDATA%\Programs\Eclipse Adoptium\jre-2*"
        "%LOCALAPPDATA%\Programs\Java\jdk-2*"
    ) do (
        if not defined JAVA_OK (
            if exist "%%~fD\bin\java.exe" (
                for /f "tokens=3" %%v in ('^""%%~fD\bin\java.exe" -version 2^>^&1 ^| findstr /i "version"^"') do (
                    set "JVER=%%~v"
                    for /f "delims=." %%a in ("!JVER!") do (
                        if %%a GEQ 21 (
                            set "JAVA_CMD=%%~fD\bin\java.exe"
                            set "JAVA_OK=1"
                        )
                    )
                )
            )
        )
    )
)

:: If still not found, show user-friendly error and pause
if not defined JAVA_OK (
    echo.
    echo ============================================================
    echo [ERROR] Java 21 or higher is required to run GameFlow LB.
    if defined CURRENT_JAVA_VER (
        echo Currently active Java version: !CURRENT_JAVA_VER!
    ) else (
        echo No Java installation was detected on PATH or JAVA_HOME.
    )
    echo.
    echo Please install Java 21+ or set JAVA_HOME:
    echo Eclipse Temurin 21: https://adoptium.net/temurin/releases/?version=21
    echo ============================================================
    echo.
    pause
    exit /b 1
)

echo.
echo  ===========================================
echo     GameFlow LB - Load Balancer Simulator   
echo     http://localhost:8080                   
echo  ===========================================
echo.
echo [INFO] Using Java runtime: "!JAVA_CMD!"
echo [INFO] Starting backend server...
echo.

:: Automatically open browser after 4 seconds
start /b cmd /c "timeout /t 4 /nobreak >nul & start http://localhost:8080" >nul 2>&1

"!JAVA_CMD!" -Djava.rmi.server.hostname=127.0.0.1 -jar "!JAR_PATH!"

set "EXIT_CODE=%errorlevel%"
if not "%EXIT_CODE%"=="0" (
    echo.
    echo [ERROR] GameFlow LB exited with error code: %EXIT_CODE%
    echo If this was unexpected, please check the log messages above.
    pause
) else (
    echo.
    echo GameFlow LB has stopped.
    pause
)

endlocal
