@echo off
setlocal enabledelayedexpansion

set ROOT=%~dp0..
if not "%WORKSPACE_FOLDER%"=="" set ROOT=%WORKSPACE_FOLDER%
set SRC=%ROOT%\src
set RMCP=%ROOT%\tools\rmcp\minecraft\src
if "%MOD_ENTRY%"=="" set MOD_ENTRY=mod_BetaMoon.java
set LUAJ_JAR=%ROOT%\lib\luaj-jse-3.0.1.jar
set LUAJ_LIB_DIR=%ROOT%\tools\rmcp\libraries\org\luaj\luaj-jse\3.0.1
set LUAJ_MINECRAFT_LIB_DIR=%ROOT%\tools\rmcp\minecraft\libraries\org\luaj\luaj-jse\3.0.1

if not exist "%RMCP%" (
  echo RetroMCP workspace not found at "%RMCP%". Run scripts\setup_rmcp.sh first.
  exit /b 1
)

if exist "%RMCP%\%MOD_ENTRY%" del "%RMCP%\%MOD_ENTRY%"
if exist "%RMCP%\piggo" rmdir /s /q "%RMCP%\piggo"
if exist "%RMCP%\org\luaj" rmdir /s /q "%RMCP%\org\luaj"
xcopy "%SRC%\*" "%RMCP%\" /E /I /Y >NUL
if exist "%LUAJ_JAR%" (
  if not exist "%LUAJ_LIB_DIR%" mkdir "%LUAJ_LIB_DIR%"
  copy /Y "%LUAJ_JAR%" "%LUAJ_LIB_DIR%\luaj-jse-3.0.1.jar" >NUL
  if not exist "%LUAJ_MINECRAFT_LIB_DIR%" mkdir "%LUAJ_MINECRAFT_LIB_DIR%"
  copy /Y "%LUAJ_JAR%" "%LUAJ_MINECRAFT_LIB_DIR%\luaj-jse-3.0.1.jar" >NUL
)
echo Synced mod sources into "%RMCP%"
