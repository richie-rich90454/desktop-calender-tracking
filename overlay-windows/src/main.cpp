#include "desktop_window.h"
#include <windows.h>
#include <iostream>
#include <thread>
#include <atomic>
#include <shellapi.h>

namespace CalendarOverlay{
    std::atomic<bool> running(true);
    std::unique_ptr<DesktopWindow> mainWindow;
    BOOL WINAPI consoleHandler(DWORD signal){
        if (signal==CTRL_C_EVENT||signal==CTRL_CLOSE_EVENT){
            running=false;
            return TRUE;
        }
        return FALSE;
    }
    void hideConsole(){
        HWND console=GetConsoleWindow();
        ShowWindow(console, SW_HIDE);
    }
    void showConsole(){
        HWND console=GetConsoleWindow();
        ShowWindow(console, SW_SHOW);
    }
    bool isAlreadyRunning(){
        HANDLE mutex=CreateMutexW(NULL, TRUE, L"CalendarOverlayInstance");
        if (GetLastError()==ERROR_ALREADY_EXISTS){
            MessageBoxW(NULL,L"Calendar Overlay is already running!\nCheck your system tray for the icon.", L"Calendar Overlay", MB_ICONINFORMATION|MB_OK);
            return true;
        }
        return false;
    }
    void runAsService(){
        AllocConsole();
        freopen("CONOUT$", "w", stdout);
        freopen("CONOUT$", "w", stderr);
        std::cout<<"Calendar Overlay Service Started"<<std::endl;
        std::cout<<"Press Ctrl+C to exit"<<std::endl;
        SetConsoleCtrlHandler(consoleHandler, TRUE);
        mainWindow=std::make_unique<DesktopWindow>();
        if (mainWindow->create()){
            mainWindow->show();
            MSG msg={};
            while (running&&GetMessage(&msg, NULL, 0, 0)){
                TranslateMessage(&msg);
                DispatchMessage(&msg);
            }
        }
        std::cout<<"Calendar Overlay Service Stopped"<<std::endl;
    }
    void runWithGUI(bool showConsoleWindow){
        if (!showConsoleWindow){
            hideConsole();
        }
        if (isAlreadyRunning()){
            return;
        }
        mainWindow=std::make_unique<DesktopWindow>();
        if (!mainWindow->create()){
            MessageBoxW(NULL, L"Failed to create calendar overlay window!",
                L"Error", MB_ICONERROR|MB_OK);
            return;
        }
        mainWindow->show();
        MSG msg={};
        while (GetMessage(&msg, NULL, 0, 0)){
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        }
    }
    struct CommandLineArgs{
        bool silent=false;
        bool console=false;
        bool help=false;
        int x=-1, y=-1;
        int width=-1, height=-1;
        float opacity=-1.0f;
    };
    CommandLineArgs parseCommandLine(int argc, char* argv[]){
        CommandLineArgs args;
        for (int i=1;i<argc;i++){
            std::string arg=argv[i];
            if (arg=="--silent"||arg=="-s"){
                args.silent=true;
            }
            else if (arg=="--console"||arg=="-c"){
                args.console=true;
            }
            else if (arg=="--help"||arg=="-h"){
                args.help=true;
            }
            else if (arg=="--x"&&i+1<argc){
                args.x=std::stoi(argv[++i]);
            }
            else if (arg=="--y"&&i+1<argc){
                args.y=std::stoi(argv[++i]);
            }
            else if (arg=="--width"&&i+1<argc){
                args.width=std::stoi(argv[++i]);
            }
            else if (arg=="--height"&&i+1<argc){
                args.height=std::stoi(argv[++i]);
            }
            else if (arg=="--opacity"&&i+1<argc){
                args.opacity=std::stof(argv[++i]);
            }
        }
        return args;
    }
    void printHelp(){
        std::cout<<R"(
Calendar Desktop Overlay - Display calendar events on your wallpaper
Usage: CalendarOverlay.exe [options]
Options:
    -s, --silent     Run silently (no console, auto-start)
    -c, --console    Show console window for debugging
    -h, --help       Show this help message
    --x POS          Window X position (default: 100)
    --y POS          Window Y position (default: 100)
    --width SIZE     Window width (default: 400)
    --height SIZE    Window height (default: 600)
    --opacity VALUE  Window opacity 0.0-1.0 (default: 0.85)

Examples:
    CalendarOverlay.exe                 # Normal mode
    CalendarOverlay.exe --silent        # Background service mode
    CalendarOverlay.exe --console --x 50 --y 50 # Debug mode with position

Features:
    - Displays today's calendar events
    - Click-through transparent window
    - System tray icon for control
    - Auto-updates from Java calendar app
    - Draggable window
    - Customizable appearance

The overlay reads events from: %APPDATA%\DesktopCalendar\calendar_events.json

Controls:
    - Drag window to reposition
    - Right-click tray icon for menu
    - ESC to hide window
    - F5 to refresh events
)"<<std::endl;
    }
}
int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpCmdLine, int nCmdShow){
    int argc;
    LPWSTR* argv=CommandLineToArgvW(GetCommandLineW(), &argc);
    std::vector<std::string> argsStr;
    std::vector<char*> argsChar;
    for (int i=0;i<argc;i++){
        int size=WideCharToMultiByte(CP_UTF8, 0, argv[i], -1, NULL, 0, NULL, NULL);
        std::string arg(size, 0);
        WideCharToMultiByte(CP_UTF8, 0, argv[i], -1, &arg[0], size, NULL, NULL);
        argsStr.push_back(arg);
    }
    for (auto& arg : argsStr){
        argsChar.push_back(&arg[0]);
    }
    auto cmdArgs=CalendarOverlay::parseCommandLine(argsChar.size(), argsChar.data());
    if (cmdArgs.help){
        CalendarOverlay::printHelp();
        return 0;
    }
    if (cmdArgs.silent){
        CalendarOverlay::runAsService();
    }
    else{
        CalendarOverlay::runWithGUI(cmdArgs.console);
    }
    LocalFree(argv);
    return 0;
}
int main(int argc, char* argv[]){
    auto cmdArgs=CalendarOverlay::parseCommandLine(argc, argv);
    if (cmdArgs.help){
        CalendarOverlay::printHelp();
        return 0;
    }
    if (cmdArgs.silent){
        CalendarOverlay::runAsService();
    }
    else{
        CalendarOverlay::runWithGUI(true);
    }
    return 0;
}