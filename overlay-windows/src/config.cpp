#include "config.h"
#include <shlobj.h>
#include <iostream>
#include <algorithm>

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
                if (braceCount==0)
                    break;
            }
            if (inConfig){
                parseJsonLine(line, "enabled", config.enabled);
                parseJsonLine(line, "positionX", config.positionX);
                parseJsonLine(line, "positionY", config.positionY);
                parseJsonLine(line, "width", config.width);
                parseJsonLine(line, "height", config.height);
                parseJsonLine(line, "opacity", config.opacity);
                parseJsonLine(line, "showPastEvents", config.showPastEvents);
                parseJsonLine(line, "showAllDay", config.showAllDay);
                parseJsonLine(line, "refreshInterval", config.refreshInterval);
                parseJsonLine(line, "fontSize", config.fontSize);
                if (line.find("backgroundColor")!=std::string::npos){
                    size_t start=line.find("\"", line.find("backgroundColor")+1);
                    if (start!=std::string::npos){
                        size_t end=line.find("\"", start+1);
                        if (end!=std::string::npos){
                            std::string hex=line.substr(start+1, end-start-1);
                            config.backgroundColor=std::stoul(hex, nullptr, 16);
                        }
                    }
                }
                if (line.find("textColor")!=std::string::npos){
                    size_t start=line.find("\"", line.find("textColor")+1);
                    if (start!=std::string::npos){
                        size_t end=line.find("\"", start+1);
                        if (end!=std::string::npos){
                            std::string hex=line.substr(start+1, end-start-1);
                            config.textColor=std::stoul(hex, nullptr, 16);
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
        file<<"  \"enabled\": "<<(config.enabled ? "true" : "false")<<",\n";
        file<<"  \"positionX\": "<<config.positionX<<",\n";
        file<<"  \"positionY\": "<<config.positionY<<",\n";
        file<<"  \"width\": "<<config.width<<",\n";
        file<<"  \"height\": "<<config.height<<",\n";
        file<<"  \"opacity\": "<<std::fixed<<std::setprecision(2)<<config.opacity<<",\n";
        file<<"  \"showPastEvents\": "<<(config.showPastEvents ? "true" : "false")<<",\n";
        file<<"  \"showAllDay\": "<<(config.showAllDay ? "true" : "false")<<",\n";
        file<<"  \"refreshInterval\": "<<config.refreshInterval<<",\n";
        file<<"  \"fontSize\": "<<config.fontSize<<",\n";
        file<<"  \"backgroundColor\": \""<<std::hex<<std::setw(8)<<std::setfill('0')<<config.backgroundColor<<"\",\n";
        file<<"  \"textColor\": \""<<std::hex<<std::setw(8)<<std::setfill('0')<<config.textColor<<"\"\n";
        file<<"}\n";
        file.close();
        LeaveCriticalSection(&cs);
        return true;
    }
    void Config::createDefaultConfig(){
        setDefaults();
        save();
    }
    void Config::setDefaults(){
        config=OverlayConfig();
    }
    bool Config::parseJsonLine(const std::string &line, const std::string &key, std::string &value){
        if (line.find("\""+key+"\"")!=std::string::npos){
            size_t start=line.find("\"", line.find(key)+key.length()+2);
            if (start!=std::string::npos){
                size_t end=line.find("\"", start+1);
                if (end!=std::string::npos){
                    value=line.substr(start+1, end-start-1);
                    return true;
                }
            }
        }
        return false;
    }
    bool Config::parseJsonLine(const std::string &line, const std::string &key, bool &value){
        if (line.find("\""+key+"\"")!=std::string::npos){
            size_t start=line.find(":", line.find(key))+1;
            if (start!=std::string::npos){
                std::string val=line.substr(start);
                val.erase(std::remove_if(val.begin(), val.end(), isspace), val.end());
                if (val.find("true")!=std::string::npos){
                    value=true;
                    return true;
                }
                if (val.find("false")!=std::string::npos){
                    value=false;
                    return true;
                }
            }
        }
        return false;
    }
    bool Config::parseJsonLine(const std::string &line, const std::string &key, int &value)
    {
        if (line.find("\""+key+"\"")!=std::string::npos){
            size_t start=line.find(":", line.find(key))+1;
            if (start!=std::string::npos){
                size_t end=line.find_first_of(",}", start);
                if (end!=std::string::npos){
                    std::string val=line.substr(start, end-start);
                    val.erase(std::remove_if(val.begin(), val.end(), isspace), val.end());
                    try{
                        value=std::stoi(val);
                        return true;
                    }
                    catch (...){

                    }
                }
            }
        }
        return false;
    }
    bool Config::parseJsonLine(const std::string &line, const std::string &key, float &value){
        if (line.find("\""+key+"\"")!=std::string::npos){
            size_t start=line.find(":", line.find(key))+1;
            if (start!=std::string::npos){
                size_t end=line.find_first_of(",}", start);
                if (end!=std::string::npos){
                    std::string val=line.substr(start, end-start);
                    val.erase(std::remove_if(val.begin(), val.end(), isspace), val.end());
                    try{
                        value=std::stof(val);
                        return true;
                    }
                    catch (...){
                        
                    }
                }
            }
        }
        return false;
    }
}