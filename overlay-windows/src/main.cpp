// ==================== main.cpp ====================
// Entry point for the Calendar Overlay application.
// Handles command line arguments, single instance check,
// and runs either as a service (silent mode) or as a normal windowed app.

// Include necessary headers and define resource IDs.
#include <windows.h>
#undef __in
#undef __out
#include "desktop_window.h"
#include <iostream>
#include <thread>
#include <atomic>
#include <shellapi.h>
#include <vector>
#include <string>

// Resource identifiers (must match resource.h)
#define IDI_APP_ICON 101
#define IDR_TRAY_MENU 102
#define IDM_EXIT 1001
#define IDM_SHOW 1002
#define IDM_HIDE 1003
#define IDM_REFRESH 1004
#define IDM_SETTINGS 1005

namespace CalendarOverlay
{
    std::atomic<bool> running(true);
    std::unique_ptr<DesktopWindow> mainWindow;

    // Console control handler for Ctrl+C / close events.
    BOOL WINAPI consoleHandler(DWORD signal)
    {
        if (signal == CTRL_C_EVENT || signal == CTRL_CLOSE_EVENT)
        {
            running = false;
            return TRUE;
        }
        return FALSE;
    }

    // Hides the console window.
    void hideConsole()
    {
        HWND console = GetConsoleWindow();
        ShowWindow(console, SW_HIDE);
    }

    // Shows the console window.
    void showConsole()
    {
        HWND console = GetConsoleWindow();
        ShowWindow(console, SW_SHOW);
    }

    // Checks if another instance is already running using a named mutex.
    bool isAlreadyRunning()
    {
        HANDLE mutex = CreateMutexW(NULL, TRUE, L"CalendarOverlayInstance");
        if (GetLastError() == ERROR_ALREADY_EXISTS)
        {
            MessageBoxW(NULL, L"Calendar Overlay is already running!\nCheck your system tray for the icon.", L"Calendar Overlay", MB_ICONINFORMATION | MB_OK);
            return true;
        }
        return false;
    }

    // Runs as a background service (with console for logging).
    void runAsService()
    {
        AllocConsole();   // Allocate a console for output
        freopen("CONOUT$", "w", stdout);
        freopen("CONOUT$", "w", stderr);
        std::cout << "Calendar Overlay Service Started" << std::endl;
        std::cout << "Press Ctrl+C to exit" << std::endl;

        SetConsoleCtrlHandler(consoleHandler, TRUE);

        mainWindow = std::make_unique<DesktopWindow>();
        if (mainWindow->create())
        {
            mainWindow->show();
            MSG msg = {};
            while (running && GetMessage(&msg, NULL, 0, 0))
            {
                TranslateMessage(&msg);
                DispatchMessage(&msg);
            }
        }
        std::cout << "Calendar Overlay Service Stopped" << std::endl;
    }

    // Runs with a visible window (and optionally a console).
    void runWithGUI(bool showConsoleWindow)
    {
        if (!showConsoleWindow)
        {
            hideConsole();   // Hide the console if not requested
        }

        if (isAlreadyRunning())
        {
            return;
        }

        mainWindow = std::make_unique<DesktopWindow>();
        if (!mainWindow->create())
        {
            MessageBoxW(NULL, L"Failed to create calendar overlay window!",
                        L"Error", MB_ICONERROR | MB_OK);
            return;
        }

        mainWindow->show();

        // Standard message loop.
        MSG msg = {};
        while (GetMessage(&msg, NULL, 0, 0))
        {
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        }
    }

    // Structure to hold parsed command line arguments.
    struct CommandLineArgs
    {
        bool silent = false;
        bool console = false;
        bool help = false;
        int x = -1, y = -1;
        int width = -1, height = -1;
        float opacity = -1.0f;
    };

    // Parses command line arguments (ANSI version, but we convert from wide later).
    CommandLineArgs parseCommandLine(int argc, char *argv[])
    {
        CommandLineArgs args;
        for (int i = 1; i < argc; i++)
        {
            std::string arg = argv[i];
            if (arg == "--silent" || arg == "-s")
            {
                args.silent = true;
            }
            else if (arg == "--console" || arg == "-c")
            {
                args.console = true;
            }
            else if (arg == "--help" || arg == "-h")
            {
                args.help = true;
            }
            else if (arg == "--x" && i + 1 < argc)
            {
                args.x = std::stoi(argv[++i]);
            }
            else if (arg == "--y" && i + 1 < argc)
            {
                args.y = std::stoi(argv[++i]);
            }
            else if (arg == "--width" && i + 1 < argc)
            {
                args.width = std::stoi(argv[++i]);
            }
            else if (arg == "--height" && i + 1 < argc)
            {
                args.height = std::stoi(argv[++i]);
            }
            else if (arg == "--opacity" && i + 1 < argc)
            {
                args.opacity = std::stof(argv[++i]);
            }
        }
        return args;
    }

    // Prints help text.
    void printHelp()
    {
        std::cout << R"(
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
)" << std::endl;
    }
}

// ------------------------------------------------------------------------
// WinMain – entry point for Windows GUI applications.
// Converts command line to UTF-8 and dispatches to the appropriate mode.
// ------------------------------------------------------------------------
int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpCmdLine, int nCmdShow)
{
    int argc;
    LPWSTR *argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    std::vector<std::string> argsStr;
    std::vector<char *> argsChar;

    // Convert wide arguments to UTF-8 strings.
    for (int i = 0; i < argc; i++)
    {
        int size = WideCharToMultiByte(CP_UTF8, 0, argv[i], -1, NULL, 0, NULL, NULL);
        std::string arg(size, 0);
        WideCharToMultiByte(CP_UTF8, 0, argv[i], -1, &arg[0], size, NULL, NULL);
        argsStr.push_back(arg);
    }
    for (auto &arg : argsStr)
    {
        argsChar.push_back(&arg[0]);
    }

    auto cmdArgs = CalendarOverlay::parseCommandLine(argsChar.size(), argsChar.data());

    if (cmdArgs.help)
    {
        CalendarOverlay::printHelp();
        LocalFree(argv);
        return 0;
    }

    if (cmdArgs.silent)
    {
        CalendarOverlay::runAsService();
    }
    else
    {
        CalendarOverlay::runWithGUI(cmdArgs.console);
    }

    LocalFree(argv);
    return 0;
}