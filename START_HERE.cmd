@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title DSHCraft - HMCL Desktop
set "launcher=%~dp0hmcl-ui\HMCL\build\libs\DSHCraft-1.3.0-SNAPSHOT.exe"
if not exist "%launcher%" (
  echo Build DSHCraft first: cd hmcl-ui ^&^& gradlew.bat :HMCL:build
  pause
  exit /b 1
)
start "" "%launcher%"
