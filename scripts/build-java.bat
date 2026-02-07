@echo off
setlocal enabledelayedexpansion

echo Java Build Script for Calendar Application
echo ==========================================

set "PROJECT_ROOT=%~dp0.."
set "SRC_DIR=%PROJECT_ROOT%\control-app\src"
set "BUILD_DIR=%PROJECT_ROOT%\build"
set "DIST_DIR=%PROJECT_ROOT%\dist"
set "JAR_NAME=CalendarApp.jar"

if "%1%"=="clean" (
    echo Cleaning build directories...
    if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
    if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
    echo Clean complete
    goto :end
)

if "%1%"=="compile" (
    echo Compiling Java source files...
    goto :compile
)

echo Building Java application...

:clean_build
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"

:compile
echo Compiling...
mkdir "%BUILD_DIR%" 2>nul

for /r "%SRC_DIR%" %%f in (*.java) do (
    set "JAVA_FILES=!JAVA_FILES! "%%f""
)

if "!JAVA_FILES!"=="" (
    echo Error: No Java source files found!
    exit /b 1
)

javac -d "%BUILD_DIR%" -cp "%SRC_DIR%" --release 17 !JAVA_FILES!
if errorlevel 1 (
    echo Compilation failed!
    exit /b 1
)
echo Compilation successful

:copy_resources
echo Copying resources...
for /r "%SRC_DIR%" %%f in (*.properties *.txt *.png *.jpg *.ico) do (
    set "REL_PATH=%%~f"
    set "REL_PATH=!REL_PATH:%SRC_DIR%\=!"
    mkdir "%BUILD_DIR%\!REL_PATH:\%%~nf.%%~xf=!" 2>nul
    copy "%%f" "%BUILD_DIR%\!REL_PATH!" >nul
)

:create_jar
echo Creating JAR file...
mkdir "%DIST_DIR%" 2>nul

(
echo Manifest-Version: 1.0
echo Main-Class: app.Main
echo Class-Path: .
) > "%BUILD_DIR%\MANIFEST.MF"

cd "%BUILD_DIR%"
jar cfm "%DIST_DIR%\%JAR_NAME%" MANIFEST.MF .
cd "%PROJECT_ROOT%"

if exist "%DIST_DIR%\%JAR_NAME%" (
    echo Build successful!
    echo JAR created: %DIST_DIR%\%JAR_NAME%
    for %%F in ("%DIST_DIR%\%JAR_NAME%") do (
        set /a "SIZE=%%~zF / 1024"
        echo JAR size: !SIZE! KB
    )
) else (
    echo Error: JAR creation failed
    exit /b 1
)

:end
echo.
pause