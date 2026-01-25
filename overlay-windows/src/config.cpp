#include "config.h"
#include <shlobj.h>
#include <iostream>
#include <algorithm>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <filesystem>

namespace CalendarOverlay{
    Config::Config(){
        InitializeCriticalSection(&cs);
        setDefaults();
        char appDataPath[MAX_PATH];
        if (SUCCEEDED(SHGetFolderPathA(NULL, CSIDL_APPDATA, NULL, 0, appDataPath))){
            dataPath=std::string(appDataPath)+"\\DesktopCalendar\\";
            configPath=dataPath+"overlay_config.json";
            CreateDirectoryA(dataPath.c_str(), NULL);
        }
        else{
            dataPath=".\\data\\";
            configPath=".\\overlay_config.json";
            CreateDirectoryA("data", NULL);
        }
    }
    Config::~Config(){
        DeleteCriticalSection(&cs);
    }
    Config& Config::getInstance(){
        static Config instance;
        return instance;
    }
    bool Config::load(){
        EnterCriticalSection(&cs);
        std::ifstream file(configPath);
        if (!file.is_open()){
            createDefaultConfig();
            LeaveCriticalSection(&cs);
            return false;
        }
        std::string line;
        bool inConfig=false;
        int braceCount=0;
        while (std::getline(file, line)){
            std::string trimmed=line;
            trimmed.erase(std::remove_if(trimmed.begin(), trimmed.end(), isspace), trimmed.end());
            if (trimmed.find("{")!=std::string::npos){
                braceCount++;
                inConfig=true;
            }
            if (trimmed.find("}")!=std::string::npos){
                braceCount--;
                if (braceCount==0){
                    break;
                }
            }
            if (inConfig){
                std::string key;
                std::string valueStr;
                size_t quote1=line.find('"');
                if (quote1!=std::string::npos){
                    size_t quote2=line.find('"', quote1+1);
                    if (quote2!=std::string::npos){
                        key=line.substr(quote1+1, quote2-quote1-1);
                        size_t colon=line.find(':', quote2);
                        if (colon!=std::string::npos){
                            size_t valueStart=line.find_first_not_of(" \t", colon+1);
                            if (valueStart!=std::string::npos){
                                size_t valueEnd=line.find_last_of(",}");
                                if (valueEnd==std::string::npos){
                                    valueEnd=line.length();
                                }
                                valueStr=line.substr(valueStart, valueEnd-valueStart);
                                valueStr.erase(std::remove(valueStr.begin(), valueStr.end(), '\"'), valueStr.end());
                                valueStr.erase(std::remove(valueStr.begin(), valueStr.end(), ','), valueStr.end());
                                if (key=="enabled"){
                                    config.enabled=(valueStr=="true");
                                }
                                else if (key=="positionX"){
                                    config.positionX=std::stoi(valueStr);
                                }
                                else if (key=="positionY"){
                                    config.positionY=std::stoi(valueStr);
                                }
                                else if (key=="width"){
                                    config.width=std::stoi(valueStr);
                                }
                                else if (key=="height"){
                                    config.height=std::stoi(valueStr);
                                }
                                else if (key=="opacity"){
                                    config.opacity=std::stof(valueStr);
                                }
                                else if (key=="showPastEvents"){
                                    config.showPastEvents=(valueStr=="true");
                                }
                                else if (key=="showAllDay"){
                                    config.showAllDay=(valueStr=="true");
                                }
                                else if (key=="refreshInterval"){
                                    config.refreshInterval=std::stoi(valueStr);
                                }
                                else if (key=="fontSize"){
                                    config.fontSize=std::stoi(valueStr);
                                }
                                else if (key=="backgroundColor"){
                                    config.backgroundColor=std::stoul(valueStr, nullptr, 16);
                                }
                                else if (key=="textColor"){
                                    config.textColor=std::stoul(valueStr, nullptr, 16);
                                }
                                else if (key=="clickThrough"){
                                    config.clickThrough=(valueStr=="true");
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
    bool Config::save(){
        EnterCriticalSection(&cs);
        std::ofstream file(configPath);
        if (!file.is_open()){
            LeaveCriticalSection(&cs);
            return false;
        }
        file<<"{\n";
        file<<"  \"enabled\": "<<(config.enabled?"true":"false")<<",\n";
        file<<"  \"positionX\": "<<config.positionX<<",\n";
        file<<"  \"positionY\": "<<config.positionY<<",\n";
        file<<"  \"width\": "<<config.width<<",\n";
        file<<"  \"height\": "<<config.height<<",\n";
        file<<"  \"opacity\": "<<std::fixed<<std::setprecision(2)<<config.opacity<<",\n";
        file<<"  \"showPastEvents\": "<<(config.showPastEvents?"true":"false")<<",\n";
        file<<"  \"showAllDay\": "<<(config.showAllDay?"true":"false")<<",\n";
        file<<"  \"refreshInterval\": "<<config.refreshInterval<<",\n";
        file<<"  \"fontSize\": "<<config.fontSize<<",\n";
        file<<"  \"backgroundColor\": \""<<std::hex<<std::setw(8)<<std::setfill('0')<<config.backgroundColor<<"\",\n";
        file<<"  \"textColor\": \""<<std::hex<<std::setw(8)<<std::setfill('0')<<config.textColor<<"\",\n";
        file<<"  \"clickThrough\": "<<(config.clickThrough?"true":"false")<<"\n";
        file<<"}\n";
        file.close();
        LeaveCriticalSection(&cs);
        return true;
    }
    void Config::save(const OverlayConfig& newConfig){
        EnterCriticalSection(&cs);
        config = newConfig;
        std::ofstream file(configPath);
        if (file.is_open()){
            file<<"{\n";
            file<<"  \"enabled\": "<<(config.enabled?"true":"false")<<",\n";
            file<<"  \"positionX\": "<<config.positionX<<",\n";
            file<<"  \"positionY\": "<<config.positionY<<",\n";
            file<<"  \"width\": "<<config.width<<",\n";
            file<<"  \"height\": "<<config.height<<",\n";
            file<<"  \"opacity\": "<<std::fixed<<std::setprecision(2)<<config.opacity<<",\n";
            file<<"  \"showPastEvents\": "<<(config.showPastEvents?"true":"false")<<",\n";
            file<<"  \"showAllDay\": "<<(config.showAllDay?"true":"false")<<",\n";
            file<<"  \"refreshInterval\": "<<config.refreshInterval<<",\n";
            file<<"  \"fontSize\": "<<config.fontSize<<",\n";
            file<<"  \"backgroundColor\": \""<<std::hex<<std::setw(8)<<std::setfill('0')<<config.backgroundColor<<"\",\n";
            file<<"  \"textColor\": \""<<std::hex<<std::setw(8)<<std::setfill('0')<<config.textColor<<"\",\n";
            file<<"  \"clickThrough\": "<<(config.clickThrough?"true":"false")<<"\n";
            file<<"}\n";
            file.close();
        }
        LeaveCriticalSection(&cs);
    }
    void Config::createDefaultConfig(){
        setDefaults();
        save();
    }
    void Config::setDefaults(){
        config=OverlayConfig();
    }
    void Config::setClickThrough(bool enabled){
        EnterCriticalSection(&cs);
        config.clickThrough=enabled;
        LeaveCriticalSection(&cs);
    }
    void Config::setPosition(int x, int y){
        EnterCriticalSection(&cs);
        config.positionX=x;
        config.positionY=y;
        LeaveCriticalSection(&cs);
    }
    void Config::setSize(int width, int height){
        EnterCriticalSection(&cs);
        config.width=width;
        config.height=height;
        LeaveCriticalSection(&cs);
    }
    void Config::setOpacity(float opacity){
        EnterCriticalSection(&cs);
        config.opacity=opacity;
        LeaveCriticalSection(&cs);
    }
}