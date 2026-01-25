#pragma once
#include <windows.h>
#include <string>
#include <shared/calendar_shared.h>

namespace CalendarOverlay{
    class Config{
    private:
        CRITICAL_SECTION cs;
        std::string dataPath;
        std::string configPath;
        OverlayConfig config;
        void createDefaultConfig();
        void setDefaults();
    public:
        static Config& getInstance();
        Config();
        ~Config();
        bool load();
        bool save();
        OverlayConfig getConfig() const{
            return config;
        }
        void setClickThrough(bool enabled);
        void setPosition(int x, int y);
        void setSize(int width, int height);
        void setOpacity(float opacity);
        void save(const OverlayConfig& newConfig);
    };
}