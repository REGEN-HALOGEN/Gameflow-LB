@echo off
title GameFlow LB - Frontend
echo.
echo  Starting frontend dev server at http://localhost:5173
echo  (Requires backend running on http://localhost:8080)
echo.
cd /d "%~dp0frontend"
npm run dev
pause
