@echo off
setlocal enabledelayedexpansion

:: GameFlow LB - local startup (no containers, no cloud).
:: Starts the Spring Boot backend (REST + WS on :8080, RMI registry on :1099)
:: and the Vite frontend (http://localhost:5173).

title GameFlow LB - Startup
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

:: 1) Locate Java 21+
set "JAVA_OK="
where java >nul 2>&1
if not errorlevel 1 (
    for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        set "JVER=%%~v"
        for /f "delims=." %%a in ("!JVER!") do (
            if %%a GEQ 21 set "JAVA_OK=1"
        )
    )
)

:: If java on PATH is not Java 21+, check JAVA_HOME or common JDK 21+ install paths
if not defined JAVA_OK (
    if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
        for /f "tokens=3" %%v in ('"%JAVA_HOME%\bin\java.exe" -version 2^>^&1 ^| findstr /i "version"') do (
            set "JVER=%%~v"
            for /f "delims=." %%a in ("!JVER!") do (
                if %%a GEQ 21 (
                    set "PATH=%JAVA_HOME%\bin;!PATH!"
                    set "JAVA_OK=1"
                )
            )
        )
    )
)

if not defined JAVA_OK (
    for /d %%D in (
        "C:\Program Files\Eclipse Adoptium\jdk-2*"
        "C:\Program Files\Eclipse Adoptium\jre-2*"
        "C:\Program Files\Java\jdk-2*"
        "C:\Program Files\Microsoft\jdk-2*"
        "C:\Program Files\BellSoft\*-2*"
        "C:\Program Files\Amazon Corretto\jdk2*"
    ) do (
        if not defined JAVA_OK (
            if exist "%%~fD\bin\java.exe" (
                set "JAVA_HOME=%%~fD"
                set "PATH=%%~fD\bin;!PATH!"
                set "JAVA_OK=1"
            )
        )
    )
)

if not defined JAVA_OK (
    echo [ERROR] Java 21+ is required on PATH. >&2
    exit /b 1
)

:: 2) Check Node.js + npm
where npm >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Node.js + npm are required on PATH. >&2
    exit /b 1
)

:: 3) Determine backend command
set "BACKEND_CMD="
where mvn >nul 2>&1
if not errorlevel 1 (
    set "BACKEND_CMD=mvn -q spring-boot:run"
) else if exist "%ROOT%\backend\target\gameflow-lb-1.0.0.jar" (
    set BACKEND_CMD=java -Djava.rmi.server.hostname=127.0.0.1 -jar "%ROOT%\backend\target\gameflow-lb-1.0.0.jar"
) else (
    echo [ERROR] Neither 'mvn' was found on PATH nor was the JAR found at: >&2
    echo         %ROOT%\backend\target\gameflow-lb-1.0.0.jar >&2
    echo         Please install Maven or build the JAR first. >&2
    exit /b 1
)

:: 4) Start Backend if not already responding
curl -sf -o nul http://localhost:8080/api/system
if not errorlevel 1 (
    echo [INFO] Backend is already running on http://localhost:8080
) else (
    echo -^> backend:  !BACKEND_CMD!  ^(http://localhost:8080^)
    start "GameFlow LB - Backend" /D "%ROOT%\backend" cmd /k !BACKEND_CMD!
)

:: 5) Start Frontend if not already responding
curl -sf -o nul http://localhost:5173
if not errorlevel 1 (
    echo [INFO] Frontend is already running on http://localhost:5173
) else (
    echo -^> frontend: npm run dev         ^(http://localhost:5173^)
    start "GameFlow LB - Frontend" /D "%ROOT%\frontend" cmd /k npm run dev
)

:: 6) Wait for backend to be ready
echo Waiting for backend...
set "UP="
for /l %%i in (1,1,30) do (
    if not defined UP (
        curl -sf -o nul http://localhost:8080/api/system
        if not errorlevel 1 (
            set "UP=1"
        ) else (
            timeout /t 2 /nobreak >nul
        )
    )
)

if defined UP (
    echo [OK] backend up
    echo [OK] open http://localhost:5173 and press Start Simulation
    start http://localhost:5173
) else (
    echo [ERROR] backend failed to start - see the GameFlow LB - Backend console window. >&2
    exit /b 1
)

endlocal
