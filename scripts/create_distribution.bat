@echo off
REM ========================================================
REM Create distribution package for Calendar Wallpaper Overlay
REM ========================================================

SET ROOT_DIR=%~dp0\..
SET DIST_DIR=%ROOT_DIR%\dist
SET OVERLAY_DIR=%ROOT_DIR%\overlay-windows
SET BUILD_DIR=%OVERLAY_DIR%\build_nmake
SET CONTROL_APP_DIR=%ROOT_DIR%\control-app

REM Step 1: Create distribution directory
echo Creating distribution directory...
rmdir /s /q "%DIST_DIR%" 2>nul
mkdir "%DIST_DIR%"
mkdir "%DIST_DIR%\bin"
mkdir "%DIST_DIR%\config"
mkdir "%DIST_DIR%\docs"

REM Step 2: Copy C++ executable
echo Copying C++ overlay executable...
copy "%BUILD_DIR%\bin\Debug\CalendarOverlay.exe" "%DIST_DIR%\bin\CalendarWallpaper.exe" >nul
if errorlevel 1 (
    echo Warning: Could not find CalendarOverlay.exe, trying Release build...
    copy "%BUILD_DIR%\Release\CalendarOverlay.exe" "%DIST_DIR%\bin\CalendarWallpaper.exe" >nul
    if errorlevel 1 (
        echo Error: Could not find C++ executable
        exit /b 1
    )
)

REM Step 3: Copy Java application (if built)
echo Copying Java application...
if exist "%ROOT_DIR%\dist\CalendarApp.jar" (
    copy "%ROOT_DIR%\dist\CalendarApp.jar" "%DIST_DIR%\bin\" >nul
    echo ✓ Java JAR copied
) else (
    echo ℹ Java JAR not found (run build-java.bat first)
)

REM Step 4: Copy configuration files
echo Copying configuration files...
copy "%OVERLAY_DIR%\config.json" "%DIST_DIR%\config\overlay_config.json" >nul 2>&1
copy "%ROOT_DIR%\shared\calendar_schema.json" "%DIST_DIR%\config\" >nul 2>&1

REM Step 5: Create launcher scripts
echo Creating launcher scripts...

REM Create wallpaper mode launcher
echo @echo off > "%DIST_DIR%\launch_wallpaper.bat"
echo REM Launcher for Calendar Wallpaper Mode >> "%DIST_DIR%\launch_wallpaper.bat"
echo echo Starting Calendar Wallpaper Overlay... >> "%DIST_DIR%\launch_wallpaper.bat"
echo echo. >> "%DIST_DIR%\launch_wallpaper.bat"
echo echo This will create a dynamic wallpaper overlay showing: >> "%DIST_DIR%\launch_wallpaper.bat"
echo echo - Current date and time >> "%DIST_DIR%\launch_wallpaper.bat"
echo echo - Upcoming calendar events >> "%DIST_DIR%\launch_wallpaper.bat"
echo echo - Click on the overlay to open the Java calendar editor >> "%DIST_DIR%\launch_wallpaper.bat"
echo echo. >> "%DIST_DIR%\launch_wallpaper.bat"
echo bin\CalendarWallpaper.exe --wallpaper --position top-right >> "%DIST_DIR%\launch_wallpaper.bat"
echo pause >> "%DIST_DIR%\launch_wallpaper.bat"

REM Create standard overlay launcher
echo @echo off > "%DIST_DIR%\launch_overlay.bat"
echo REM Launcher for Standard Overlay Mode >> "%DIST_DIR%\launch_overlay.bat"
echo echo Starting Calendar Overlay... >> "%DIST_DIR%\launch_overlay.bat"
echo bin\CalendarWallpaper.exe >> "%DIST_DIR%\launch_overlay.bat"
echo pause >> "%DIST_DIR%\launch_overlay.bat"

REM Create README
echo # Calendar Wallpaper Overlay > "%DIST_DIR%\README.txt"
echo. >> "%DIST_DIR%\README.txt"
echo ## Features >> "%DIST_DIR%\README.txt"
echo. >> "%DIST_DIR%\README.txt"
echo 1. **Dynamic Wallpaper Mode**: Runs as a desktop wallpaper overlay >> "%DIST_DIR%\README.txt"
echo 2. **Top-Right Positioning**: Shows calendar info in top-right corner >> "%DIST_DIR%\README.txt"
echo 3. **Click to Edit**: Click on overlay to launch Java calendar editor >> "%DIST_DIR%\README.txt"
echo 4. **Real-time Updates**: Shows current time and upcoming events >> "%DIST_DIR%\README.txt"
echo. >> "%DIST_DIR%\README.txt"
echo ## Usage >> "%DIST_DIR%\README.txt"
echo. >> "%DIST_DIR%\README.txt"
echo - Run `launch_wallpaper.bat` for wallpaper mode >> "%DIST_DIR%\README.txt"
echo - Run `launch_overlay.bat` for standard overlay mode >> "%DIST_DIR%\README.txt"
echo - Click on the overlay to open calendar editor >> "%DIST_DIR%\README.txt"
echo. >> "%DIST_DIR%\README.txt"
echo ## Command Line Options >> "%DIST_DIR%\README.txt"
echo. >> "%DIST_DIR%\README.txt"
echo - `--wallpaper` or `-w`: Enable wallpaper mode >> "%DIST_DIR%\README.txt"
echo - `--position [pos]`: Set position (top-right, top-left, bottom-right, bottom-left) >> "%DIST_DIR%\README.txt"
echo - `--fullscreen` or `-f`: Full screen wallpaper mode >> "%DIST_DIR%\README.txt"
echo. >> "%DIST_DIR%\README.txt"
echo ## Requirements >> "%DIST_DIR%\README.txt"
echo. >> "%DIST_DIR%\README.txt"
echo - Windows 10/11 >> "%DIST_DIR%\README.txt"
echo - Visual C++ Redistributable >> "%DIST_DIR%\README.txt"
echo - Java Runtime (for calendar editor) >> "%DIST_DIR%\README.txt"

REM Step 6: Create zip package
echo Creating ZIP package...
cd "%DIST_DIR%"
powershell -Command "Compress-Archive -Path * -DestinationPath '..\CalendarWallpaperOverlay.zip' -Force"
cd "%ROOT_DIR%"

echo.
echo ========================================================
echo Distribution package created successfully!
echo.
echo Files located in: %DIST_DIR%
echo ZIP package: %ROOT_DIR%\CalendarWallpaperOverlay.zip
echo.
echo To run in wallpaper mode: launch_wallpaper.bat
echo To run in overlay mode: launch_overlay.bat
echo ========================================================
pause