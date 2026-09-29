@echo off
setlocal
title GameFlow LB - Starting...

:: Run the PowerShell launcher (works on any Windows with PS 5+)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
endlocal
