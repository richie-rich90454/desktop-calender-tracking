@echo off
echo Building Desktop Calendar Tracking...

REM Build Java application
cd control-app
if exist build (
    rmdir /s /q build
)
mkdir build

REM Compile Java
javac -d build -cp "src;lib/*" src/app/*.java src/ui/*.java src/model/*.java src/state/*.java src/storage/*.java src/service/*.java src/calendar/*.java

REM Create JAR
echo Main-Class: app.Main > manifest.txt
jar cfm CalendarApp.jar manifest.txt -C build .

REM Copy to distribution
cd ..
if not exist dist mkdir dist
copy control-app\CalendarApp.jar dist\

echo Build complete!
pause