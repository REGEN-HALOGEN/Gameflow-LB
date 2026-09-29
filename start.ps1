#Requires -Version 5
<#
  GameFlow LB — One-click launcher
  Finds any Java 17+ on the machine and starts the backend JAR.
  Double-click start.bat (or run this script directly) to launch.
#>

$ErrorActionPreference = "Stop"
$Host.UI.RawUI.WindowTitle = "GameFlow LB"

Write-Host ""
Write-Host "  ============================================================" -ForegroundColor Cyan
Write-Host "   GameFlow LB  |  Cloud Gaming Load Balancer Simulator" -ForegroundColor Cyan
Write-Host "  ============================================================" -ForegroundColor Cyan
Write-Host ""

# ── Locate JAR ────────────────────────────────────────────────────────────────
$root   = Split-Path $MyInvocation.MyCommand.Path
$jarPath = Join-Path $root "backend\target\gameflow-lb-1.0.0.jar"
if (-not (Test-Path $jarPath)) {
    Write-Host "  [ERROR] Backend JAR not found:" -ForegroundColor Red
    Write-Host "          $jarPath" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Build it first:" -ForegroundColor Yellow
    Write-Host "    cd backend && mvn package -DskipTests" -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

# ── Find Java 17+ ─────────────────────────────────────────────────────────────
function Get-JavaVersion([string]$exe) {
    try {
        $out = & $exe -version 2>&1 | Select-String "version"
        if ($out -match '"(\d+)[\._](\d+)') {
            $major = [int]$Matches[1]
            # Old "1.x" scheme
            if ($major -eq 1) { $major = [int]$Matches[2] }
            return $major
        }
    } catch {}
    return 0
}

$javaExe = $null

# 1) java already on PATH
$onPath = Get-Command java -ErrorAction SilentlyContinue
if ($onPath) {
    $v = Get-JavaVersion $onPath.Source
    if ($v -ge 17) { $javaExe = $onPath.Source }
}

# 2) Scan Eclipse Adoptium / Temurin
if (-not $javaExe) {
    $adoptDir = "C:\Program Files\Eclipse Adoptium"
    if (Test-Path $adoptDir) {
        Get-ChildItem $adoptDir -Directory | Sort-Object Name -Descending | ForEach-Object {
            if (-not $javaExe) {
                $candidate = Join-Path $_.FullName "bin\java.exe"
                if (Test-Path $candidate) {
                    $v = Get-JavaVersion $candidate
                    if ($v -ge 17) { $javaExe = $candidate }
                }
            }
        }
    }
}

# 3) JAVA_HOME
if (-not $javaExe -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path $candidate) {
        $v = Get-JavaVersion $candidate
        if ($v -ge 17) { $javaExe = $candidate }
    }
}

# 4) Common JDK install locations
if (-not $javaExe) {
    $searchRoots = @(
        "C:\Program Files\Java",
        "C:\Program Files\Microsoft",
        "C:\Program Files\BellSoft",
        "C:\Program Files\Amazon Corretto",
        "C:\Program Files\Zulu",
        "C:\Softwares"
    )
    foreach ($searchRoot in $searchRoots) {
        if ($javaExe) { break }
        if (-not (Test-Path $searchRoot)) { continue }
        Get-ChildItem $searchRoot -Directory | Sort-Object Name -Descending | ForEach-Object {
            if (-not $javaExe) {
                $candidate = Join-Path $_.FullName "bin\java.exe"
                if (Test-Path $candidate) {
                    $v = Get-JavaVersion $candidate
                    if ($v -ge 17) { $javaExe = $candidate }
                }
            }
        }
    }
}

if (-not $javaExe) {
    Write-Host "  [ERROR] No Java 17+ found on this machine." -ForegroundColor Red
    Write-Host ""
    Write-Host "  Install Temurin 21 from: https://adoptium.net" -ForegroundColor Yellow
    Write-Host "  Then run this launcher again." -ForegroundColor Yellow
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

$jver = Get-JavaVersion $javaExe
Write-Host "  Java  : Java $jver — $javaExe" -ForegroundColor Green
Write-Host "  JAR   : $jarPath" -ForegroundColor Green
Write-Host "  URL   : http://localhost:8080" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Backend starting... (Ctrl+C to stop)" -ForegroundColor Yellow
Write-Host ""

# ── Launch ─────────────────────────────────────────────────────────────────────
& $javaExe "-Djava.rmi.server.hostname=127.0.0.1" -jar $jarPath

Write-Host ""
Write-Host "  Backend stopped." -ForegroundColor Yellow
Read-Host "Press Enter to exit"
