@echo off
setlocal enabledelayedexpansion

set ROOT=%~dp0..
if not "%WORKSPACE_FOLDER%"=="" set ROOT=%WORKSPACE_FOLDER%
set WORKSPACE=%ROOT%\tools\rmcp\minecraft
set OUTPUTDIR=%WORKSPACE%\build
if "%MOD_NAME%"=="" set MOD_NAME=BetaMoon
set OUTPUTZIP=%OUTPUTDIR%\%MOD_NAME%.jar
set LUAJ_JAR=%ROOT%\lib\luaj-jse-3.0.1.jar

if "%MULTIMC_MODS_PATH%"=="" (
  set MULTIMC_MODS_PATH=E:\Program Files\MultiMC\instances\b1.7.3_modding\.minecraft\mods
)

if not exist "%WORKSPACE%" (
  echo RetroMCP workspace not found at "%WORKSPACE%". Run build first.
  exit /b 1
)

set SOURCE_DIR=
for %%D in ("%WORKSPACE%\reobf\client" "%WORKSPACE%\reobf\minecraft" "%WORKSPACE%\reobf" "%WORKSPACE%\bin") do (
  if exist "%%~D" (
    set SOURCE_DIR=%%~D
    goto :foundsrc
  )
)
:foundsrc
if "%SOURCE_DIR%"=="" (
  echo No reobfuscated output found. Run the build task first.
  exit /b 1
)

if not exist "%OUTPUTDIR%" mkdir "%OUTPUTDIR%"
set TMPZIP=%TEMP%\industrio_zip_tmp
if exist "%TMPZIP%" rmdir /s /q "%TMPZIP%"
mkdir "%TMPZIP%"
xcopy "%SOURCE_DIR%\*" "%TMPZIP%\" /E /I /Y >NUL
if exist "%LUAJ_JAR%" powershell -NoLogo -NoProfile -Command "Expand-Archive -Path '%LUAJ_JAR%' -DestinationPath '%TMPZIP%' -Force" >NUL
if exist "%ROOT%\resources" xcopy "%ROOT%\resources\*" "%TMPZIP%\resources\" /E /I /Y >NUL
if exist "%OUTPUTZIP%" del "%OUTPUTZIP%"
powershell -NoLogo -NoProfile -Command "Compress-Archive -Path '%TMPZIP%\*' -DestinationPath '%OUTPUTZIP%'"
rmdir /s /q "%TMPZIP%"

if not exist "%MULTIMC_MODS_PATH%" mkdir "%MULTIMC_MODS_PATH%"
copy /Y "%OUTPUTZIP%" "%MULTIMC_MODS_PATH%\%MOD_NAME%.jar" >NUL
echo Mod package created at "%OUTPUTZIP%"
echo Copied to MultiMC mods folder: "%MULTIMC_MODS_PATH%\%MOD_NAME%.jar"
