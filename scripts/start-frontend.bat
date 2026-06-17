@echo off
REM Interface React — Microservice CIN
set PATH=C:\Program Files\nodejs;%PATH%
cd /d "%~dp0..\frontend"
if not exist node_modules (
  echo Installation des dependances...
  call npm install
)
echo.
echo Frontend : http://localhost:5173
echo Backend  : http://localhost:8081  (doit deja tourner)
echo.
call npm run dev
