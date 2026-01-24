#pragma once
#include <string>
#include <windows.h>
#include "shared/calendar_shared.h"

namespace CalendarOverlay{
    class Config{
    public:
        static Config& getInstance(){
            static Config instance;
            return instance;
        }
        bool load();
        bool save();
        void setDefaults();
        OverlayConfig& getConfig(){
            return config;
        }
        std::string getDataPath() const{
            return dataPath;
        }
        std::string getConfigPath() const{
            return configPath;
        }
        void setPosition(int x, int y){
            config.positionX=x;
            config.positionY=y;
        }
        void setSize(int w, int h){
            config.width=w;
            config.height=h;
        }
        void setOpacity(float opacity){
            config.opacity=opacity;
        }
    private:
        Config();
        ~Config()=default;
        Config(const Config&)=delete;
        Config& operator=(const Config&)=delete;
        void createDefaultConfig();
        std::string getAppDataPath();
        OverlayConfig config;
        std::string dataPath;
        std::string configPath;
        CRITICAL_SECTION cs;
    };
}