@echo off
rem Double-clickable dev-client launcher. Builds the current code, then opens the
rem Minecraft client with the mod loaded. Leave this window open while playing --
rem closing it closes Minecraft.
title Formicary dev client
set "JAVA_HOME=C:\Users\Family\.jdks\jdk-21.0.11+10"
cd /d "%~dp0"
call "%~dp0gradlew.bat" runClient
if errorlevel 1 (
    echo.
    echo Launch failed -- read the error above.
    pause
)
