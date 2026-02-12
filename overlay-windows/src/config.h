#pragma once

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
#include <shared/calendar_shared.h>

namespace CalendarOverlay
{
    class Config
    {
    private:
        CRITICAL_SECTION cs;
        std::string dataPath;
        std::string configPath;
        OverlayConfig config;
        void createDefaultConfig();
        void setDefaults();

    public:
        static Config &getInstance();
        Config();
        ~Config();
        bool load();
        bool save();
        OverlayConfig getConfig() const
        {
            return config;
        }
        std::string getDataPath() const
        {
            return dataPath;
        }
        void setClickThrough(bool enabled);
        void setPosition(int x, int y);
        void setSize(int width, int height);
        void setOpacity(float opacity);
        void save(const OverlayConfig &newConfig);
    };
}