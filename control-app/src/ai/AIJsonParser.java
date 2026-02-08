package ai;

import model.Event;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON parsing utilities for AI responses, adapted from JsonStore.java
 * Handles parsing of AI-generated event JSON with multiple field name variations
 */

public class AIJsonParser{
    private static final DateTimeFormatter DATE_FORMATTER=DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER=DateTimeFormatter.ofPattern("HH:mm");
    public static List<Event> parseAIResponse(String jsonResponse) throws AIException{
        try{
            String eventsArrayJson=extractEventsArray(jsonResponse);
            if (eventsArrayJson==null){
                throw new AIException("No 'events' array found in AI response",AIException.ErrorType.INVALID_RESPONSE);
            }
            List<Event> events=parseEventsArray(eventsArrayJson);
            if (events.isEmpty()){
                throw new AIException("No valid events found in AI response",AIException.ErrorType.INVALID_RESPONSE);
            }
            return events;
        }
        catch (AIException e){
            throw e;
        }
        catch (Exception e){
            throw new AIException("Failed to parse AI response: "+e.getMessage(),AIException.ErrorType.INVALID_RESPONSE, e);
        }
    }
    private static String extractEventsArray(String json){
        int eventsKeyPos=json.indexOf("\"events\"");
        if (eventsKeyPos==-1){
            eventsKeyPos=json.indexOf("events:");
            if (eventsKeyPos==-1){
                return null;
            }
            eventsKeyPos += "events:".length() - 1;
        }
        else{
            eventsKeyPos += "\"events\"".length();
        }
        int bracketStart=json.indexOf('[', eventsKeyPos);
        if (bracketStart==-1){
            return null;
        }
        int bracketEnd=findMatchingBracket(json, bracketStart);
        if (bracketEnd==-1){
            return null;
        }
        return json.substring(bracketStart+1, bracketEnd).trim();
    }
    private static List<Event> parseEventsArray(String eventsArrayJson) throws AIException{
        List<Event> events=new ArrayList<>();
        if (eventsArrayJson.isEmpty()){
            return events;
        }
        String[] eventStrings=splitJsonObjects(eventsArrayJson);
        for (String eventStr:eventStrings){
            eventStr=eventStr.trim();
            if (eventStr.isEmpty()){
                continue;
            }
            Event event=parseEventObject(eventStr);
            if (event!=null){
                events.add(event);
            }
        }
        return events;
    }
    private static Event parseEventObject(String eventJson) throws AIException{
        try{
            String title=extractJsonField(eventJson, "title", "name", "summary");
            String dateStr=extractJsonField(eventJson, "date", "day", "event_date");
            String startTimeStr=extractJsonField(eventJson, "start_time", "startTime", "start", "from");
            String endTimeStr=extractJsonField(eventJson, "end_time", "endTime", "end", "to");
            if (title==null||dateStr==null||startTimeStr==null||endTimeStr==null){
                throw new AIException("Missing required fields in event JSON: "+eventJson,AIException.ErrorType.INVALID_RESPONSE);
            }
            startTimeStr=normalizeTimeFormat(startTimeStr);
            endTimeStr=normalizeTimeFormat(endTimeStr);
            LocalDate date=LocalDate.parse(dateStr, DATE_FORMATTER);
            LocalTime startTime=LocalTime.parse(startTimeStr, TIME_FORMATTER);
            LocalTime endTime=LocalTime.parse(endTimeStr, TIME_FORMATTER);
            if (!endTime.isAfter(startTime)){
                throw new AIException("End time must be after start time: "+startTimeStr+" - "+endTimeStr,AIException.ErrorType.INVALID_RESPONSE);
            }
            return new Event(unescapeJsonString(title), date, startTime, endTime);
        }
        catch (Exception e){
            throw new AIException("Failed to parse event: "+e.getMessage(),AIException.ErrorType.INVALID_RESPONSE, e);
        }
    }
    private static String extractJsonField(String json, String... possibleFieldNames){
        for (String fieldName:possibleFieldNames){
            String value=extractSingleJsonField(json, fieldName);
            if (value!=null){
                return value;
            }
        }
        return null;
    }
    private static String extractSingleJsonField(String json, String fieldName){
        try{
            String fieldPattern="\""+fieldName+"\"";
            int fieldPos=json.indexOf(fieldPattern);
            if (fieldPos==-1){
                return null;
            }
            int colonPos=json.indexOf(":", fieldPos+fieldPattern.length());
            if (colonPos==-1){
                return null;
            }
            int valueStart=colonPos+1;
            while (valueStart < json.length()&&Character.isWhitespace(json.charAt(valueStart))){
                valueStart++;
            }
            if (valueStart >= json.length()){
                return null;
            }
            char firstChar=json.charAt(valueStart);
            if (firstChar=='"'){
                int valueEnd=valueStart+1;
                while (valueEnd < json.length()){
                    char c=json.charAt(valueEnd);
                    if (c=='"'&&json.charAt(valueEnd - 1)!='\\'){
                        break;
                    }
                    valueEnd++;
                }
                if (valueEnd >= json.length()){
                    return null;
                }
                String value=json.substring(valueStart+1, valueEnd);
                return unescapeJsonString(value);
            }
            else{
                int valueEnd=valueStart;
                while (valueEnd < json.length()&&json.charAt(valueEnd)!=','&&json.charAt(valueEnd)!='}'&&!Character.isWhitespace(json.charAt(valueEnd))){
                    valueEnd++;
                }
                return json.substring(valueStart, valueEnd).trim();
            }
        }
        catch (Exception e){
            return null;
        }
    }
    private static String normalizeTimeFormat(String time) throws AIException{
        try{
            if (time==null){
                throw new IllegalArgumentException("Time cannot be null");
            }
            time=time.trim().toUpperCase();
            if (time.isEmpty()){
                throw new IllegalArgumentException("Time cannot be empty");
            }
            time=time.replaceAll("\\s+", " ");
            if (time.matches("^\\d{1,2}:\\d{2}$")){
                String[] parts=time.split(":");
                int hour=Integer.parseInt(parts[0]);
                int minute=Integer.parseInt(parts[1]);
                if (hour >=0&&hour <=23&&minute >=0&&minute <=59){
                    return String.format("%02d:%02d", hour, minute);
                }
            }
            if (time.matches("^\\d{1,2}:\\d{2}:\\d{2}$")){
                String[] parts=time.split(":");
                int hour=Integer.parseInt(parts[0]);
                int minute=Integer.parseInt(parts[1]);
                if (hour >=0&&hour <=23&&minute >=0&&minute <=59){
                    return String.format("%02d:%02d", hour, minute);
                }
            }
            if (time.matches("^\\d{3,4}$")&&!time.contains(":")){
                int len=time.length();
                int hour, minute;
                if (len ==3){
                    hour=Integer.parseInt(time.substring(0, 1));
                    minute=Integer.parseInt(time.substring(1, 3));
                }
                else{
                    hour=Integer.parseInt(time.substring(0, 2));
                    minute=Integer.parseInt(time.substring(2, 4));
                }
                if (hour >=0&&hour <=23&&minute >=0&&minute <=59){
                    return String.format("%02d:%02d", hour, minute);
                }
            }
            if (time.contains("AM")||time.contains("PM")){
                String ampm=time.contains("AM")?"AM":"PM";
                String timePart=time.replace("AM", "").replace("PM", "").trim();
                if (timePart.matches("^\\d{1,2}(:\\d{2}){0,2}$")){
                    String[] parts=timePart.split(":");
                    int hour=Integer.parseInt(parts[0]);
                    int minute=parts.length >1?Integer.parseInt(parts[1]):0;
                    if (ampm.equals("PM")&&hour!=12){
                        hour +=12;
                    }
                    else if (ampm.equals("AM")&&hour ==12){
                        hour=0;
                    }
                    if (hour >=0&&hour <=23&&minute >=0&&minute <=59){
                        return String.format("%02d:%02d", hour, minute);
                    }
                }
            }
            try{
                LocalTime parsed=LocalTime.parse(time);
                return parsed.format(TIME_FORMATTER);
            }
            catch (Exception e){
            }
            throw new IllegalArgumentException("Unrecognized time format: "+time);
        }
        catch (Exception e){
            throw new AIException("Invalid time format: "+time+" - "+e.getMessage(),AIException.ErrorType.INVALID_RESPONSE, e);
        }
    }
    private static String[] splitJsonObjects(String jsonArray){
        List<String> objects=new ArrayList<>();
        int start=0;
        int braceCount=0;
        boolean inString=false;
        String cleaned=jsonArray.replace("\n", "").replace("\r", "").trim();
        for (int i=0; i < cleaned.length(); i++){
            char c=cleaned.charAt(i);
            if (c=='"'&&(i ==0||cleaned.charAt(i - 1)!='\\')){
                inString=!inString;
            }
            if (!inString){
                if (c=='{'){
                    if (braceCount ==0){
                        start=i;
                    }
                    braceCount++;
                }
                else if (c=='}'){
                    braceCount--;
                    if (braceCount ==0){
                        String obj=cleaned.substring(start, i+1);
                        objects.add(obj);
                    }
                }
            }
        }
        return objects.toArray(new String[0]);
    }
    private static int findMatchingBracket(String json, int start){
        int bracketCount=0;
        boolean inString=false;
        for (int i=start; i < json.length(); i++){
            char c=json.charAt(i);
            if (c=='"'&&(i ==0||json.charAt(i - 1)!='\\')){
                inString=!inString;
            }
            if (!inString){
                if (c=='['||c=='{'){
                    bracketCount++;
                }
                else if (c==']'||c=='}'){
                    bracketCount--;
                    if (bracketCount ==0){
                        return i;
                    }
                }
            }
        }
        return -1;
    }
    private static String unescapeJsonString(String input){
        if (input==null){
            return "";
        }
        return input.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\\", "\\");
    }
    public static String escapeJsonString(String input){
        if (input==null){
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}