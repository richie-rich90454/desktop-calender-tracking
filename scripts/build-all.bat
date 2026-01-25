@echo off
echo ============================================
echo Desktop Calendar Tracking - Build Script
echo ============================================
echo.

echo Step 1: Checking for existing components
echo.

REM Check for Java JAR
if exist CalendarApp.jar (
    echo Found: CalendarApp.jar (Java application)
    set JAR_EXISTS=1
) else (
    if exist ..\scripts\CalendarApp.jar (
        echo Found: ..\scripts\CalendarApp.jar
        set JAR_EXISTS=1
    ) else (
        echo Warning: CalendarApp.jar not found
        set JAR_EXISTS=0
    )
)

REM Check for C++ overlay
if exist "..\overlay-windows\build\Release\CalendarOverlay.exe" (
    echo Found: CalendarOverlay.exe (C++ overlay)
    set EXE_EXISTS=1
) else (
    echo CalendarOverlay.exe not found
    set EXE_EXISTS=0
)

echo.
echo Step 2: Creating distribution package
echo.

REM Create dist directory
if not exist ..\dist mkdir ..\dist

REM Copy Java JAR if it exists
if %JAR_EXISTS%==1 (
    if exist CalendarApp.jar (
        copy CalendarApp.jar ..\dist\CalendarApp.jar >nul
        echo Copied CalendarApp.jar to dist
    ) else (
        if exist ..\scripts\CalendarApp.jar (
            copy ..\scripts\CalendarApp.jar ..\dist\CalendarApp.jar >nul
            echo Copied CalendarApp.jar to dist
        )
    )
)

REM Copy C++ executable if it exists
if %EXE_EXISTS%==1 (
    copy "..\overlay-windows\build\Release\CalendarOverlay.exe" ..\dist\CalendarOverlay.exe >nul
    echo Copied CalendarOverlay.exe to dist
)

echo.
echo Step 3: Creating launcher
echo.

REM Create launcher script
echo @echo off > ..\dist\launch.bat
echo echo Desktop Calendar Tracking >> ..\dist\launch.bat
echo echo ========================= >> ..\dist\launch.bat
echo echo. >> ..\dist\launch.bat

if %JAR_EXISTS%==1 (
    echo echo Starting Java Calendar Application... >> ..\dist\launch.bat
    echo start javaw -jar CalendarApp.jar >> ..\dist\launch.bat
    echo echo. >> ..\dist\launch.bat
)

if %EXE_EXISTS%==1 (
    echo echo Starting C++ Desktop Overlay... >> ..\dist\launch.bat
    echo start CalendarOverlay.exe --silent >> ..\dist\launch.bat
    echo echo. >> ..\dist\launch.bat
)

echo echo Applications started >> ..\dist\launch.bat
echo echo. >> ..\dist\launch.bat
echo echo Note: >> ..\dist\launch.bat
echo echo - Java app runs in background >> ..\dist\launch.bat
echo echo - C++ overlay appears as transparent window >> ..\dist\launch.bat
echo echo - Check system tray for icons >> ..\dist\launch.bat
echo echo. >> ..\dist\launch.bat
echo pause >> ..\dist\launch.bat

echo Created launcher: ..\dist\launch.bat

echo.
echo Step 4: Build summary
echo =====================
echo.

if %JAR_EXISTS%==1 (
    echo [X] Java Calendar Application: READY
) else (
    echo [ ] Java Calendar Application: MISSING
    echo     To build: Run scripts\build.bat (requires Java JDK)
)

if %EXE_EXISTS%==1 (
    echo [X] C++ Desktop Overlay: READY
) else (
    echo [ ] C++ Desktop Overlay: MISSING
    echo     To build:
    echo     1. Install Visual Studio 2022 Community Edition
    echo     2. Install "Desktop development with C++" workload
    echo     3. Open "Developer Command Prompt for VS 2022"
    echo     4. cd /d "%~dp0..\overlay-windows"
    echo     5. mkdir build
    echo     6. cd build
    echo     7. cmake .. -G "Visual Studio 17 2022" -A x64
    echo     8. cmake --build . --config Release
    echo     9. copy Release\CalendarOverlay.exe ..\..\dist\
)

echo.
echo [X] Launcher: READY (dist\launch.bat)
echo.
echo Distribution package created in: ..\dist
echo.
echo To run: cd ..\dist && launch.bat
echo.
echo ============================================
echo Build completed
echo ============================================
echo.
pause