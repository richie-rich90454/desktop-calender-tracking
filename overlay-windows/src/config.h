// ==================== config.h ====================
// Configuration management for the Calendar Overlay.
// Handles loading/saving settings to a JSON file in %APPDATA%\DesktopCalendar\.
// All settings are stored in an OverlayConfig struct and are accessible
// via the Config singleton.

#pragma once

// Ensure Windows headers don't pull in unnecessary macros.
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif

#include <windows.h>
#undef __in
#undef __out
#include <string>
#include <shared/calendar_shared.h>   // Contains OverlayConfig definition

namespace CalendarOverlay
{
    // Singleton class that manages overlay settings.
    class Config
    {
    private:
        CRITICAL_SECTION cs;            // Protects concurrent access (though mostly single‑threaded)
        std::string dataPath;            // Folder where config file resides
        std::string configPath;          // Full path to overlay_config.json
        OverlayConfig config;            // Current configuration values

        void createDefaultConfig();      // Writes default config to disk
        void setDefaults();              // Sets config to default values (in memory)

    public:
        static Config &getInstance();    // Singleton accessor
        Config();
        ~Config();

        bool load();                     // Load config from file (returns true on success)
        bool save();                     // Save current config to file

        OverlayConfig getConfig() const { return config; }
        std::string getDataPath() const { return dataPath; }

        // Individual setters – modify a single setting and save automatically
        void setClickThrough(bool enabled);
        void setPosition(int x, int y);
        void setSize(int width, int height);
        void setOpacity(float opacity);

        // Overwrite entire config and save
        void save(const OverlayConfig &newConfig);
    };
}