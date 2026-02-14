// ==================== config.cpp ====================
// Implementation of the Config singleton.
// Uses a simple JSON‑like file format (key‑value pairs) – no external JSON library.
// The file is stored in the user's AppData folder.

#include "config.h"
#include <shlobj.h>        // For SHGetFolderPathA
#include <iostream>
#include <algorithm>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <filesystem>

namespace CalendarOverlay
{
    // Constructor: determines the storage path and initialises critical section.
    Config::Config()
    {
        InitializeCriticalSection(&cs);
        setDefaults();   // Start with default values

        char appDataPath[MAX_PATH];
        if (SUCCEEDED(SHGetFolderPathA(NULL, CSIDL_APPDATA, NULL, 0, appDataPath)))
        {
            dataPath = std::string(appDataPath) + "\\DesktopCalendar\\";
            configPath = dataPath + "overlay_config.json";
            CreateDirectoryA(dataPath.c_str(), NULL);   // Ensure folder exists
        }
        else
        {
            // Fallback to current directory
            dataPath = ".\\data\\";
            configPath = ".\\overlay_config.json";
            CreateDirectoryA("data", NULL);
        }
    }

    Config::~Config()
    {
        DeleteCriticalSection(&cs);
    }

    // Singleton instance – thread‑safe in C++11 (static initialisation).
    Config &Config::getInstance()
    {
        static Config instance;
        return instance;
    }

    // Loads the configuration file.
    // The file is a simple JSON‑like object, parsed line by line.
    // Returns true if the file existed and was successfully read.
    bool Config::load()
    {
        EnterCriticalSection(&cs);
        std::ifstream file(configPath);
        if (!file.is_open())
        {
            createDefaultConfig();   // No file – create one with defaults
            LeaveCriticalSection(&cs);
            return false;
        }

        std::string line;
        bool inConfig = false;
        int braceCount = 0;

        while (std::getline(file, line))
        {
            // Remove whitespace for easier detection of braces
            std::string trimmed = line;
            trimmed.erase(std::remove_if(trimmed.begin(), trimmed.end(), isspace), trimmed.end());

            if (trimmed.find("{") != std::string::npos)
            {
                braceCount++;
                inConfig = true;
            }
            if (trimmed.find("}") != std::string::npos)
            {
                braceCount--;
                if (braceCount == 0)
                {
                    break;   // End of object
                }
            }

            if (inConfig)
            {
                // Look for a quoted key
                std::string key;
                std::string valueStr;
                size_t quote1 = line.find('"');
                if (quote1 != std::string::npos)
                {
                    size_t quote2 = line.find('"', quote1 + 1);
                    if (quote2 != std::string::npos)
                    {
                        key = line.substr(quote1 + 1, quote2 - quote1 - 1);
                        size_t colon = line.find(':', quote2);
                        if (colon != std::string::npos)
                        {
                            size_t valueStart = line.find_first_not_of(" \t", colon + 1);
                            if (valueStart != std::string::npos)
                            {
                                size_t valueEnd = line.find_last_of(",}");
                                if (valueEnd == std::string::npos)
                                {
                                    valueEnd = line.length();
                                }
                                valueStr = line.substr(valueStart, valueEnd - valueStart);
                                // Remove quotes and trailing commas
                                valueStr.erase(std::remove(valueStr.begin(), valueStr.end(), '\"'), valueStr.end());
                                valueStr.erase(std::remove(valueStr.begin(), valueStr.end(), ','), valueStr.end());

                                // Assign to the appropriate field
                                if (key == "enabled")
                                {
                                    config.enabled = (valueStr == "true");
                                }
                                else if (key == "positionX")
                                {
                                    config.positionX = std::stoi(valueStr);
                                }
                                else if (key == "positionY")
                                {
                                    config.positionY = std::stoi(valueStr);
                                }
                                else if (key == "width")
                                {
                                    config.width = std::stoi(valueStr);
                                }
                                else if (key == "height")
                                {
                                    config.height = std::stoi(valueStr);
                                }
                                else if (key == "opacity")
                                {
                                    config.opacity = std::stof(valueStr);
                                }
                                else if (key == "showPastEvents")
                                {
                                    config.showPastEvents = (valueStr == "true");
                                }
                                else if (key == "showAllDay")
                                {
                                    config.showAllDay = (valueStr == "true");
                                }
                                else if (key == "refreshInterval")
                                {
                                    config.refreshInterval = std::stoi(valueStr);
                                }
                                else if (key == "fontSize")
                                {
                                    config.fontSize = std::stoi(valueStr);
                                }
                                else if (key == "backgroundColor")
                                {
                                    config.backgroundColor = std::stoul(valueStr, nullptr, 16);
                                }
                                else if (key == "textColor")
                                {
                                    config.textColor = std::stoul(valueStr, nullptr, 16);
                                }
                                else if (key == "clickThrough")
                                {
                                    config.clickThrough = (valueStr == "true");
                                }
                            }
                        }
                    }
                }
            }
        }
        file.close();
        LeaveCriticalSection(&cs);
        return true;
    }

    // Saves the current configuration to the JSON file.
    bool Config::save()
    {
        EnterCriticalSection(&cs);
        std::ofstream file(configPath);
        if (!file.is_open())
        {
            LeaveCriticalSection(&cs);
            return false;
        }

        file << "{\n";
        file << "  \"enabled\": " << (config.enabled ? "true" : "false") << ",\n";
        file << "  \"positionX\": " << config.positionX << ",\n";
        file << "  \"positionY\": " << config.positionY << ",\n";
        file << "  \"width\": " << config.width << ",\n";
        file << "  \"height\": " << config.height << ",\n";
        file << "  \"opacity\": " << std::fixed << std::setprecision(2) << config.opacity << ",\n";
        file << "  \"showPastEvents\": " << (config.showPastEvents ? "true" : "false") << ",\n";
        file << "  \"showAllDay\": " << (config.showAllDay ? "true" : "false") << ",\n";
        file << "  \"refreshInterval\": " << config.refreshInterval << ",\n";
        file << "  \"fontSize\": " << config.fontSize << ",\n";
        file << "  \"backgroundColor\": \"" << std::hex << std::setw(8) << std::setfill('0') << config.backgroundColor << "\",\n";
        file << "  \"textColor\": \"" << std::hex << std::setw(8) << std::setfill('0') << config.textColor << "\",\n";
        file << "  \"clickThrough\": " << (config.clickThrough ? "true" : "false") << "\n";
        file << "}\n";

        file.close();
        LeaveCriticalSection(&cs);
        return true;
    }

    // Overload of save() that updates the in‑memory config first.
    void Config::save(const OverlayConfig &newConfig)
    {
        EnterCriticalSection(&cs);
        config = newConfig;
        std::ofstream file(configPath);
        if (file.is_open())
        {
            file << "{\n";
            file << "  \"enabled\": " << (config.enabled ? "true" : "false") << ",\n";
            file << "  \"positionX\": " << config.positionX << ",\n";
            file << "  \"positionY\": " << config.positionY << ",\n";
            file << "  \"width\": " << config.width << ",\n";
            file << "  \"height\": " << config.height << ",\n";
            file << "  \"opacity\": " << std::fixed << std::setprecision(2) << config.opacity << ",\n";
            file << "  \"showPastEvents\": " << (config.showPastEvents ? "true" : "false") << ",\n";
            file << "  \"showAllDay\": " << (config.showAllDay ? "true" : "false") << ",\n";
            file << "  \"refreshInterval\": " << config.refreshInterval << ",\n";
            file << "  \"fontSize\": " << config.fontSize << ",\n";
            file << "  \"backgroundColor\": \"" << std::hex << std::setw(8) << std::setfill('0') << config.backgroundColor << "\",\n";
            file << "  \"textColor\": \"" << std::hex << std::setw(8) << std::setfill('0') << config.textColor << "\",\n";
            file << "  \"clickThrough\": " << (config.clickThrough ? "true" : "false") << "\n";
            file << "}\n";
            file.close();
        }
        LeaveCriticalSection(&cs);
    }

    // Creates a default configuration file by resetting to defaults and saving.
    void Config::createDefaultConfig()
    {
        setDefaults();
        save();
    }

    // Resets the in‑memory config to the default constructor values.
    void Config::setDefaults()
    {
        config = OverlayConfig();   // uses the struct's default constructor
    }

    // Setter for click‑through mode – updates memory and saves immediately.
    void Config::setClickThrough(bool enabled)
    {
        EnterCriticalSection(&cs);
        config.clickThrough = enabled;
        LeaveCriticalSection(&cs);
        save();   // Save to disk after change
    }

    // Setter for window position.
    void Config::setPosition(int x, int y)
    {
        EnterCriticalSection(&cs);
        config.positionX = x;
        config.positionY = y;
        LeaveCriticalSection(&cs);
        save();
    }

    // Setter for window size.
    void Config::setSize(int width, int height)
    {
        EnterCriticalSection(&cs);
        config.width = width;
        config.height = height;
        LeaveCriticalSection(&cs);
        save();
    }

    // Setter for opacity.
    void Config::setOpacity(float opacity)
    {
        EnterCriticalSection(&cs);
        config.opacity = opacity;
        LeaveCriticalSection(&cs);
        save();
    }
}