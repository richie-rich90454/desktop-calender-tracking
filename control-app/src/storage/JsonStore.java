package storage;

/**
 * JSON-based persistence layer.
 *
 * Responsibilities:
 *-Save calendar events to disk
 *-Load calendar events from disk
 *
 * Java data types used:
 *-Path
 *-List<Event>
 *-String
 *
 * Java technologies involved:
 *-Java NIO
 *-JSON serialization
 *
 * Design intent:
 * Storage format can change without affecting the model.
 */
import model.CalendarModel;
import model.Event;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class JsonStore {
    private static String DEFAULT_FILE_NAME="calendar_events.json";
    private static String BACKUP_FILE_NAME="calendar_events_backup.json";
    private static DateTimeFormatter DATE_FORMATTER=DateTimeFormatter.ISO_LOCAL_DATE;
    private static DateTimeFormatter TIME_FORMATTER=DateTimeFormatter.ISO_LOCAL_TIME;
    private Path storagePath;
    private Path backupPath;
    public JsonStore(){
        String userHome=System.getProperty("user.home");
        this.storagePath=Paths.get(userHome, ".calendarapp", DEFAULT_FILE_NAME);
        this.backupPath=Paths.get(userHome, ".calendarapp", BACKUP_FILE_NAME);
        ensureStorageDirectory();
    }
    public JsonStore(String filePath){
        this.storagePath=Paths.get(filePath);
        this.backupPath=Paths.get(filePath+".backup");
        ensureStorageDirectory();
    }
    public JsonStore(Path storagePath, Path backupPath){
        this.storagePath=storagePath;
        this.backupPath=backupPath;
        ensureStorageDirectory();
    }
    private void ensureStorageDirectory(){
        try{
            Path parentDir=storagePath.getParent();
            if (parentDir!=null&&!Files.exists(parentDir)){
                Files.createDirectories(parentDir);
            }
        }
        catch (IOException e){
            System.err.println("Failed to create storage directory: "+e.getMessage());
        }
    }
    public boolean saveCalendar(CalendarModel model){
        return saveCalendar(model, this.storagePath);
    }
    public boolean saveCalendar(CalendarModel model, Path filePath){
        try{
            if (Files.exists(filePath)){
                createBackup(filePath);
            }
            String json=convertToJson(model);
            Files.writeString(filePath, json);
            return true;
        }
        catch (IOException e){
            System.err.println("Failed to save calendar: "+e.getMessage());
            return false;
        }
    }
    public CalendarModel loadCalendar(){
        return loadCalendar(this.storagePath);
    }
    public CalendarModel loadCalendar(Path filePath){
        try{
            if (!Files.exists(filePath)){
                System.out.println("File not found: "+filePath);
                return new CalendarModel();
            }
            String json=Files.readString(filePath);
            return parseFromJson(json);
        }
        catch (IOException e){
            System.err.println("Failed to load calendar: "+e.getMessage());
            return tryLoadFromBackup();
        }
    }
    private CalendarModel tryLoadFromBackup(){
        try{
            if (Files.exists(backupPath)){
                System.out.println("Attempting to load from backup...");
                String json=Files.readString(backupPath);
                return parseFromJson(json);
            }
        }
        catch (IOException e){
            System.err.println("Failed to load from backup: "+e.getMessage());
        }
        return new CalendarModel();
    }
    private void createBackup(Path originalPath){
        try{
            Files.copy(originalPath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e){
            System.err.println("Failed to create backup: "+e.getMessage());
        }
    }
    private String convertToJson(CalendarModel model){
        StringBuilder json=new StringBuilder();
        json.append("{\n");
        json.append("  \"version\": \"1.0\",\n");
        json.append("  \"savedAt\": \"").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
        json.append("  \"events\": [\n");
        List<Event> events=model.getSortedEvents();
        for (int i=0;i<events.size();i++){
            Event event=events.get(i);
            json.append(convertEventToJson(event));
            if (i<events.size()-1){
                json.append(",\n");
            }
            else{
                json.append("\n");
            }
        }
        json.append("  ]\n");
        json.append("}");
        return json.toString();
    }
    private String convertEventToJson(Event event){
        StringBuilder eventJson=new StringBuilder();
        eventJson.append("    {\n");
        eventJson.append("      \"title\": \"").append(escapeJsonString(event.getTitle())).append("\",\n");
        eventJson.append("      \"date\": \"").append(event.getDate().format(DATE_FORMATTER)).append("\",\n");
        eventJson.append("      \"startTime\": \"").append(event.getStartTime().toLocalTime().format(TIME_FORMATTER)).append("\",\n");
        eventJson.append("      \"endTime\": \"").append(event.getEndTime().toLocalTime().format(TIME_FORMATTER)).append("\",\n");
        eventJson.append("      \"startDateTime\": \"").append(event.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
        eventJson.append("      \"endDateTime\": \"").append(event.getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\"\n");
        eventJson.append("    }");
        return eventJson.toString();
    }
    private CalendarModel parseFromJson(String json){
        CalendarModel model=new CalendarModel();
        try{
            Pattern pattern=Pattern.compile("\"events\"\\s*:\\s*\\[");
            Matcher matcher=pattern.matcher(json);
            if (!matcher.find()){
                System.err.println("No events array found in JSON");
                return model;
            }
            int eventsStart=matcher.end()-1;
            int eventsEnd=findMatchingBracket(json, eventsStart);
            if (eventsEnd==-1){
                System.err.println("Could not find matching bracket for events array");
                return model;
            }
            String eventsArray=json.substring(eventsStart+1, eventsEnd);
            List<Event> events=parseEventsArray(eventsArray);
            System.out.println("Parsed "+events.size()+" events from JSON");
            for (Event event:events){
                model.addEvent(event);
            }
        }
        catch (Exception e){
            System.err.println("Error parsing JSON: "+e.getMessage());
            e.printStackTrace();
        }
        return model;
    }
    private List<Event> parseEventsArray(String eventsArrayJson){
        List<Event> events=new ArrayList<>();
        try{
            String[] eventStrings=splitJsonObjects(eventsArrayJson);
            for (String eventStr:eventStrings){
                if (eventStr.trim().isEmpty()){
                    continue;
                }
                Event event=parseEventObject(eventStr.trim());
                if (event!=null){
                    events.add(event);
                }
            }
        }
        catch (Exception e){
            System.err.println("Error parsing events array: "+e.getMessage());
        }
        return events;
    }
    private Event parseEventObject(String eventJson){
        try{
            String title=extractJsonField(eventJson, "title");
            String dateStr=extractJsonField(eventJson, "date");
            String startTimeStr=extractJsonField(eventJson, "startTime");
            String endTimeStr=extractJsonField(eventJson, "endTime");
            if (startTimeStr==null){
                String startDateTimeStr=extractJsonField(eventJson, "startDateTime");
                if (startDateTimeStr!=null){
                    LocalDateTime startDateTime=LocalDateTime.parse(startDateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    dateStr=startDateTime.toLocalDate().format(DATE_FORMATTER);
                    startTimeStr=startDateTime.toLocalTime().format(TIME_FORMATTER);
                }
            }
            if (endTimeStr==null){
                String endDateTimeStr=extractJsonField(eventJson, "endDateTime");
                if (endDateTimeStr!=null){
                    endTimeStr=LocalDateTime.parse(endDateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalTime().format(TIME_FORMATTER);
                }
            }
            if (title==null||dateStr==null||startTimeStr==null||endTimeStr==null){
                return null;
            }
            LocalDate date=LocalDate.parse(dateStr, DATE_FORMATTER);
            LocalTime startTime=LocalTime.parse(startTimeStr, TIME_FORMATTER);
            LocalTime endTime=LocalTime.parse(endTimeStr, TIME_FORMATTER);
            return new Event(unescapeJsonString(title), date, startTime, endTime);
            
        }
        catch (Exception e){
            System.err.println("Error parsing event: "+e.getMessage());
            return null;
        }
    }
    private String extractJsonField(String json, String fieldName){
        String pattern="\""+fieldName+"\"\\s*:\\s*\"";
        int start=json.indexOf(pattern);
        if (start==-1){
            return null;
        }
        start+=pattern.length();
        int end=json.indexOf("\"", start);
        if (end==-1){
            return null;
        }
        return json.substring(start, end);
    }
    private String[] splitJsonObjects(String jsonArray){
        List<String> objects=new ArrayList<>();
        int braceCount=0;
        StringBuilder currentObject=new StringBuilder();
        boolean inString=false;
        for (int i=0;i<jsonArray.length();i++){
            char c=jsonArray.charAt(i);
            if (c=='"'&&(i==0||jsonArray.charAt(i-1)!='\\')){
                inString=!inString;
            }
            if (!inString){
                if (c=='{'){
                    braceCount++;
                }
                else if (c=='}'){
                    braceCount--;
                }
            }
            currentObject.append(c);
            if (!inString&&c=='}'&&braceCount==0){
                objects.add(currentObject.toString());
                currentObject=new StringBuilder();
                i++;
                while (i<jsonArray.length()&&Character.isWhitespace(jsonArray.charAt(i))){
                    i++;
                }
            }
        }
        return objects.toArray(new String[0]);
    }
    private int findMatchingBracket(String json, int start){
        int bracketCount=0;
        boolean inString=false;
        for (int i=start;i<json.length();i++){
            char c=json.charAt(i);
            if (c=='"'&&(i==0||json.charAt(i-1)!='\\')){
                inString=!inString;
            }
            if (!inString){
                if (c=='['||c=='{'){
                    bracketCount++;
                }
                else if (c==']'||c=='}'){
                    bracketCount--;
                    if (bracketCount==0){
                        return i;
                    }
                }
            }
        }
        return -1;
    }
    private String escapeJsonString(String input){
        if (input==null){
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    private String unescapeJsonString(String input){
        if (input==null){
            return "";
        }
        return input.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\\", "\\");
    }
    public Path getStoragePath(){
        return storagePath;
    }
    public Path getBackupPath(){
        return backupPath;
    }
    public void setStoragePath(Path newPath){
        this.storagePath=newPath;
        ensureStorageDirectory();
    }
    public void setBackupPath(Path newPath){
        this.backupPath=newPath;
    }
    public boolean saveFileExists(){
        return Files.exists(storagePath);
    }
    public boolean backupFileExists(){
        return Files.exists(backupPath);
    }
    public boolean createManualBackup(CalendarModel model){
        Path manualBackup=Paths.get(backupPath.toString()+".manual."+System.currentTimeMillis()+".json");
        return saveCalendar(model, manualBackup);
    }
    public boolean exportCalendar(CalendarModel model, Path exportPath){
        return saveCalendar(model, exportPath);
    }
    public CalendarModel importCalendar(Path importPath){
        return loadCalendar(importPath);
    }
    public long getFileSize(){
        try{
            return Files.size(storagePath);
        }
        catch (IOException e){
            return 0;
        }
    }
    public String getStorageInfo(){
        try{
            StringBuilder info=new StringBuilder();
            info.append("Storage Path: ").append(storagePath).append("\n");
            info.append("File Exists: ").append(saveFileExists()).append("\n");
            if (saveFileExists()){
                info.append("File Size: ").append(getFileSize()).append(" bytes\n");
                info.append("Last Modified: ").append(Files.getLastModifiedTime(storagePath)).append("\n");
            }
            info.append("Backup Exists: ").append(backupFileExists()).append("\n");
            return info.toString();
        }
        catch (IOException e){
            return "Error getting storage info: "+e.getMessage();
        }
    }
}