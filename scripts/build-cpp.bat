@echo off
REM ========================================================
REM Build CalendarOverlay from scripts folder - UPDATED FOR VS 2026
REM ========================================================

REM Step 0: Determine paths
SET ROOT_DIR=%~dp0\..
SET OVERLAY_DIR=%ROOT_DIR%\overlay-windows
SET BUILD_DIR=%OVERLAY_DIR%\build_nmake

REM Step 1: Set up VS environment (x64) - UPDATED FOR VS 2026
call "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvarsall.bat" x64
if errorlevel 1 (
    echo Failed to initialize Visual Studio environment.
    exit /b 1
)

REM Step 2: Remove old build folder
rmdir /s /q "%BUILD_DIR%"

REM Step 3: Configure CMake with NMake, force MSVC compiler - UPDATED FOR VS 2026
"C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe" ^
    -S "%OVERLAY_DIR%" ^
    -B "%BUILD_DIR%" ^
    -G "NMake Makefiles" ^
    -DCMAKE_CXX_COMPILER="C:/Program Files/Microsoft Visual Studio/18/Community/VC/Tools/MSVC/14.50.35717/bin/Hostx64/x64/cl.exe" ^
    -DCMAKE_CXX_STANDARD=17

if errorlevel 1 (
    echo CMake configuration failed.
    exit /b 1
)

REM Step 4: Build the project
cmake --build "%BUILD_DIR%" --config Release
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

echo ========================================================
echo Build completed successfully!
echo Executable located in build_nmake\Release\CalendarOverlay.exe
echo ========================================================
pause