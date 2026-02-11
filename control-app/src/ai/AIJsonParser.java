package ai;

import model.Event;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AIJsonParser{
    private static final DateTimeFormatter DATE_FORMATTER=DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER=DateTimeFormatter.ofPattern("HH:mm");
    public static List<Event> parseAIResponse(String jsonResponse) throws AIException{
        if (jsonResponse==null||jsonResponse.trim().isEmpty()){
            throw new AIException("Empty or null response from AI", AIException.ErrorType.INVALID_RESPONSE);
        }
        try{
            System.out.println("=== PARSING AI RESPONSE ===");
            String cleanedResponse=cleanAndUnescapeJson(jsonResponse);
            System.out.println("Cleaned response preview: "+cleanedResponse.substring(0, Math.min(500, cleanedResponse.length())));
            try{
                return parseCompleteJson(cleanedResponse);
            }
            catch (AIException e){
                System.out.println("Complete JSON parse failed: "+e.getMessage());
                return parseEventsDirectly(cleanedResponse);
            }
        }
        catch (AIException e){
            throw e;
        }
        catch (Exception e){
            throw new AIException("Failed to parse AI response: "+e.getMessage(),AIException.ErrorType.INVALID_RESPONSE, e);
        }
    }
    private static String cleanAndUnescapeJson(String json){
        json=json.trim();
        if (json.startsWith("```json")){
            json=json.substring(7);
        }
        else if (json.startsWith("```")){
            json=json.substring(3);
        }
        if (json.endsWith("```")){
            json=json.substring(0, json.length() -3);
        }
        json=json.trim();
        json=unescapeJsonString(json);
        return json;
    }
    private static List<Event> parseCompleteJson(String json) throws AIException{
        System.out.println("Attempting complete JSON parse...");
        int eventsStart=json.indexOf("\"events\"");
        if (eventsStart==-1){
            eventsStart=json.indexOf("'events'");
        }
        if (eventsStart==-1){
            throw new AIException("No events key found in JSON", AIException.ErrorType.INVALID_RESPONSE);
        }
        int arrayStart=json.indexOf('[', eventsStart);
        if (arrayStart==-1){
            throw new AIException("No array found after events key", AIException.ErrorType.INVALID_RESPONSE);
        }
        int arrayEnd=findMatchingBracket(json, arrayStart);
        if (arrayEnd==-1){
            throw new AIException("No matching array end found", AIException.ErrorType.INVALID_RESPONSE);
        }
        String arrayContent=json.substring(arrayStart+1, arrayEnd).trim();
        System.out.println("Extracted array content length: "+arrayContent.length());
        return parseEventsArray(arrayContent);
    }
    private static List<Event> parseEventsDirectly(String json) throws AIException{
        System.out.println("Falling back to direct event parsing...");
        List<Event> events=new ArrayList<>();
        int start=0;
        while (true){
            int eventStart=json.indexOf('{', start);
            if (eventStart==-1) break;
            int eventEnd=findMatchingBracket(json, eventStart);
            if (eventEnd==-1) break;
            String eventJson=json.substring(eventStart, eventEnd+1);
            try{
                Event event=parseSingleEvent(eventJson);
                if (event!=null){
                    events.add(event);
                }
            }
            catch (Exception e){
                System.out.println("Skipping invalid event: "+e.getMessage());
            }
            start=eventEnd+1;
        }
        if (events.isEmpty()){
            throw new AIException("No valid events found in response", AIException.ErrorType.INVALID_RESPONSE);
        }
        return events;
    }
    private static Event parseSingleEvent(String eventJson) throws AIException{
        try{
            String title=extractJsonValueFlexible(eventJson, "title", "name", "summary");
            String dateStr=extractJsonValueFlexible(eventJson, "date", "day", "event_date");
            String startTimeStr=extractJsonValueFlexible(eventJson, "start_time", "startTime", "start", "from");
            String endTimeStr=extractJsonValueFlexible(eventJson, "end_time", "endTime", "end", "to");
            if (title==null||dateStr==null||startTimeStr==null||endTimeStr==null){
                throw new AIException("Missing required fields", AIException.ErrorType.INVALID_RESPONSE);
            }
            System.out.println("Parsing event: "+title+" on "+dateStr+" from "+startTimeStr+" to "+endTimeStr);
            startTimeStr=normalizeTimeFormat(startTimeStr);
            endTimeStr=normalizeTimeFormat(endTimeStr);
            LocalDate date=LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
            LocalTime startTime=LocalTime.parse(startTimeStr, TIME_FORMATTER);
            LocalTime endTime=LocalTime.parse(endTimeStr, TIME_FORMATTER);
            if (!endTime.isAfter(startTime)){
                throw new AIException("End time must be after start time", AIException.ErrorType.INVALID_RESPONSE);
            }
            return new Event(title.trim(), date, startTime, endTime);
        }
        catch (Exception e){
            throw new AIException("Failed to parse event: "+e.getMessage(),AIException.ErrorType.INVALID_RESPONSE, e);
        }
    }
    private static String extractJsonValueFlexible(String json, String... possibleKeys){
        for (String key:possibleKeys){
            String value=extractJsonValue(json, key);
            if (value!=null&&!value.isEmpty()){
                return value;
            }
        }
        return null;
    }
    private static String extractJsonValue(String json, String key){
        String pattern="\""+key+"\"";
        int pos=json.indexOf(pattern);
        if (pos==-1){
            pattern=key+"\"";
            pos=json.indexOf(pattern);
            if (pos!=-1) pos--;
        }
        if (pos==-1){
            return null;
        }
        int colonPos=json.indexOf(':', pos+pattern.length());
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
        if (firstChar == '"'||firstChar == '\''){
            int quoteEnd=valueStart+1;
            int backslashCount=0;
            while (quoteEnd < json.length()){
                char c=json.charAt(quoteEnd);
                if (c == '\\'){
                    backslashCount++;
                }
                else if ((c == '"'||c == '\'')&&(backslashCount%2==0)&&c == firstChar){
                    break;
                }
                else{
                    backslashCount=0;
                }
                quoteEnd++;
            }
            if (quoteEnd >= json.length()){
                return null;
            }
            String value=json.substring(valueStart+1, quoteEnd);
            return unescapeJsonString(value);
        }
        else{
            int valueEnd=valueStart;
            while (valueEnd < json.length()&&json.charAt(valueEnd) != ','&&json.charAt(valueEnd)!='}'&&json.charAt(valueEnd)!=']'&&!Character.isWhitespace(json.charAt(valueEnd))){
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd).trim();
        }
    }
    private static List<Event> parseEventsArray(String arrayContent) throws AIException{
        List<Event> events=new ArrayList<>();
        if (arrayContent.trim().isEmpty()){
            return events;
        }
        String[] eventStrings=splitJsonObjects(arrayContent);
        System.out.println("Found "+eventStrings.length+" event objects in array");
        for (int i=0; i < eventStrings.length; i++){
            String eventStr=eventStrings[i].trim();
            if (eventStr.isEmpty()){
                continue;
            }
            try{
                Event event=parseSingleEvent(eventStr);
                if (event!=null){
                    events.add(event);
                    System.out.println("Successfully parsed event "+(i+1)+": "+event.getTitle());
                }
            }
            catch (Exception e){
                System.out.println("Failed to parse event "+(i+1)+": "+e.getMessage());
            }
        }
        return events;
    }
    private static String[] splitJsonObjects(String jsonArray){
        List<String> objects=new ArrayList<>();
        int start=0;
        int braceCount=0;
        boolean inString=false;
        char stringChar=0;
        for (int i=0; i < jsonArray.length(); i++){
            char c=jsonArray.charAt(i);
            if ((c == '"'||c == '\'')&&(i ==0||jsonArray.charAt(i - 1)!='\\')){
                if (!inString){
                    inString=true;
                    stringChar=c;
                }
                else if (c ==stringChar){
                    inString=false;
                }
            }
            if (!inString){
                if (c == '{'){
                    if (braceCount ==0){
                        start=i;
                    }
                    braceCount++;
                }
                else if (c == '}'){
                    braceCount--;
                    if (braceCount ==0){
                        String obj=jsonArray.substring(start, i+1);
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
        char stringChar=0;
        for (int i=start; i < json.length(); i++){
            char c=json.charAt(i);
            if ((c == '"'||c == '\'')&&(i ==0||json.charAt(i - 1)!='\\')){
                if (!inString){
                    inString=true;
                    stringChar=c;
                }
                else if (c ==stringChar){
                    inString=false;
                }
            }
            if (!inString){
                if (c == '['||c == '{'){
                    bracketCount++;
                }
                else if (c == ']'||c == '}'){
                    bracketCount--;
                    if (bracketCount ==0){
                        return i;
                    }
                }
            }
        }
        return -1;
    }
    private static String normalizeTimeFormat(String time) throws AIException{
        try{
            if (time==null){
                throw new IllegalArgumentException("Time cannot be null");
            }
            time=time.trim().toUpperCase();
            if ((time.startsWith("\"")&&time.endsWith("\""))||(time.startsWith("'")&&time.endsWith("'"))){
                time=time.substring(1, time.length() -1).trim();
            }
            boolean isPM=time.contains("PM");
            boolean isAM=time.contains("AM");
            if (isPM||isAM){
                String timeWithoutAmPm=time.replace("AM", "").replace("PM", "").trim();
                String[] parts=timeWithoutAmPm.split(":");
                if (parts.length >=1){
                    int hour=Integer.parseInt(parts[0]);
                    int minute=parts.length >1?Integer.parseInt(parts[1]):0;
                    if (isPM&&hour!=12){
                        hour +=12;
                    }
                    else if (isAM&&hour ==12){
                        hour=0;
                    }
                    return String.format("%02d:%02d", hour, minute);
                }
            }
            if (time.matches("\\d{1,2}:\\d{2}")){
                String[] parts=time.split(":");
                int hour=Integer.parseInt(parts[0]);
                int minute=Integer.parseInt(parts[1]);
                if (hour >=0&&hour <=23&&minute >=0&&minute <=59){
                    return String.format("%02d:%02d", hour, minute);
                }
            }
            if (time.matches("\\d{1,2}:\\d{2}:\\d{2}")){
                String[] parts=time.split(":");
                int hour=Integer.parseInt(parts[0]);
                int minute=Integer.parseInt(parts[1]);
                if (hour >=0&&hour <=23&&minute >=0&&minute <=59){
                    return String.format("%02d:%02d", hour, minute);
                }
            }
            if (time.matches("\\d{3,4}")){
                if (time.length() ==3){
                    time="0"+time;
                }
                String hourStr=time.substring(0, 2);
                String minuteStr=time.substring(2, 4);
                int hour=Integer.parseInt(hourStr);
                int minute=Integer.parseInt(minuteStr);
                if (hour >=0&&hour <=23&&minute >=0&&minute <=59){
                    return String.format("%02d:%02d", hour, minute);
                }
            }
            try{
                LocalTime parsed=LocalTime.parse(time, TIME_FORMATTER);
                return parsed.format(TIME_FORMATTER);
            }
            catch (Exception e){
                try{
                    LocalTime parsed=LocalTime.parse(time);
                    return parsed.format(TIME_FORMATTER);
                }
                catch (Exception e2){
                    throw new IllegalArgumentException("Unrecognized time format: "+time);
                }
            }
        }
        catch (Exception e){
            throw new AIException("Invalid time format: '"+time+"' - "+e.getMessage(),AIException.ErrorType.INVALID_RESPONSE, e);
        }
    }
    private static String unescapeJsonString(String input){
        if (input==null){
            return "";
        }
        StringBuilder result=new StringBuilder();
        for (int i=0; i < input.length(); i++){
            char c=input.charAt(i);
            if (c == '\\'&&i+1 < input.length()){
                char next=input.charAt(i+1);
                switch (next){
                    case '"': result.append('"'); i++; break;
                    case '\\': result.append('\\'); i++; break;
                    case '/': result.append('/'); i++; break;
                    case 'b': result.append('\b'); i++; break;
                    case 'f': result.append('\f'); i++; break;
                    case 'n': result.append('\n'); i++; break;
                    case 'r': result.append('\r'); i++; break;
                    case 't': result.append('\t'); i++; break;
                    case 'u':
                        if (i+5 < input.length()){
                            String hex=input.substring(i+2, i+6);
                            try{
                                int codePoint=Integer.parseInt(hex, 16);
                                result.append((char) codePoint);
                                i+=5;
                            }
                            catch (NumberFormatException e){
                                result.append(c);
                            }
                        }
                        else{
                            result.append(c);
                        }
                        break;
                    default:
                        result.append(c);
                }
            }
            else{
                result.append(c);
            }
        }
        return result.toString();
    }
    public static String escapeJsonString(String input){
        if (input==null){
            return "";
        }
        StringBuilder result=new StringBuilder();
        for (char c:input.toCharArray()){
            switch (c){
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '/': result.append("\\/"); break;
                case '\b': result.append("\\b"); break;
                case '\f': result.append("\\f"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    result.append(c);
            }
        }
        return result.toString();
    }
}