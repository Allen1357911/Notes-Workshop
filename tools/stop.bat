@echo off
title Note-Workshop : Stop
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop.ps1"
