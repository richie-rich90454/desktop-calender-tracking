package ai;

import model.Event;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;

/**
 * BaseAIClient provides shared networking, retry handling, usage tracking,
 * and lightweight JSON parsing utilities for AI provider clients.
 *
 * Subclasses implement provider-specific request building and response parsing.
 */
public abstract class BaseAIClient implements AIClient {

    protected String apiKey;
    protected String endpoint;
    protected String model;
    protected final HttpClient httpClient;
    protected final UsageStats lastUsageStats;
    protected final UsageStats totalUsageStats;
    protected static final DateTimeFormatter DATE_FORMATTER=DateTimeFormatter.ISO_LOCAL_DATE;
    protected static final DateTimeFormatter TIME_FORMATTER=new DateTimeFormatterBuilder().appendPattern("HH:mm").optionalStart().appendPattern(":ss").optionalEnd().toFormatter();

    protected BaseAIClient(){
        this.httpClient=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).version(HttpClient.Version.HTTP_2).build();
        this.lastUsageStats=new UsageStats();
        this.totalUsageStats=new UsageStats();
    }

    @Override
    public abstract List<Event> generateEvents(String goalDescription, LocalDate startDate, int days, List<Event> existingEvents) throws AIException;
    @Override
    public List<Event> generateEvents(String goalDescription, LocalDate startDate, int days, List<Event> existingEvents, ProgressCallback callback) throws AIException{
        return generateEvents(goalDescription, startDate, days, existingEvents);
    }
    @Override
    public boolean testConnection() throws AIException{
        ensureConfigured();
        try{
            String response=sendRequest(buildTestRequest("Say \"OK\" if you can hear me."));
            return response!=null&&response.contains("OK");
        }
        catch (Exception e){
            throw new AIException("Connection test failed: "+e.getMessage(), AIException.ErrorType.NETWORK_ERROR, e);
        }
    }
    @Override
    public boolean isOfflineCapable(){
        return false;
    }
    @Override
    public void setApiKey(String apiKey){ 
        this.apiKey=apiKey; 
    }
    @Override
    public void setEndpoint(String endpoint){ 
        this.endpoint=endpoint; 
    }
    @Override
    public void setModel(String model){ 
        this.model=model; 
    }
    @Override
    public String getCurrentModel(){ 
        return model; 
    }
    @Override
    public UsageStats getLastUsageStats(){ 
        return lastUsageStats; 
    }
    @Override
    public UsageStats getTotalUsageStats(){ 
        return totalUsageStats; 
    }
    @Override
    public void resetUsageStats(){ 
        lastUsageStats.reset(); 
    }
    @Override
    public void shutdown(){
    }

    protected void ensureConfigured() throws AIException{
        if (apiKey==null||apiKey.isBlank()) throw new AIException("API key not set", AIException.ErrorType.AUTHENTICATION_ERROR);
        if (endpoint==null||endpoint.isBlank()) throw new AIException("Endpoint not set", AIException.ErrorType.OTHER);
        if (model==null||model.isBlank()) throw new AIException("Model not set", AIException.ErrorType.OTHER);
    }

    protected String sendRequest(String requestBody) throws AIException{
        try{
            HttpRequest request=buildHttpRequest(requestBody);
            HttpResponse<String> response=httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200){
                throw new AIException("API request failed with status "+response.statusCode()+": "+response.body(), AIException.ErrorType.OTHER);
            }
            return response.body();
        }
        catch (Exception e){
            throw new AIException("Failed to send request: "+e.getMessage(), AIException.ErrorType.NETWORK_ERROR, e);
        }
    }

    protected abstract HttpRequest buildHttpRequest(String requestBody);
    protected abstract String buildTestRequest(String prompt);
    protected abstract List<Event> parseResponse(String response, LocalDate startDate, int days, List<Event> existingEvents) throws AIException;

    protected Event createEvent(String title, String dateStr, String startTimeStr, String endTimeStr) throws AIException{
        try{
            LocalDate date=LocalDate.parse(dateStr, DATE_FORMATTER);
            LocalTime start=LocalTime.parse(startTimeStr, TIME_FORMATTER);
            LocalTime end=LocalTime.parse(endTimeStr, TIME_FORMATTER);
            return new Event(title, date, start, end);
        }
        catch (Exception e){
            throw new AIException("Invalid event: "+title+" "+dateStr+" "+startTimeStr+"-"+endTimeStr, AIException.ErrorType.INVALID_RESPONSE, e);
        }
    }

    protected void updateUsageStats(int promptTokens, int completionTokens){
        lastUsageStats.addRequest(promptTokens, completionTokens);
        totalUsageStats.addRequest(promptTokens, completionTokens);
    }

    protected int estimateTokens(String text){
        return (int)Math.ceil(text.length()/4.0);
    }

    protected String extractJsonValue(String json, String key){
        if (json==null||key==null) return null;
        String search="\""+key+"\":";
        int index=json.indexOf(search);
        if (index==-1) return null;
        index+=search.length();
        while (index<json.length()&&Character.isWhitespace(json.charAt(index))) index++;
        if (index>=json.length()) return null;
        char first=json.charAt(index);
        if (first=='"'){
            int end=index+1;
            while (end<json.length()){
                if (json.charAt(end)=='"'&&json.charAt(end-1)!='\\') break;
                end++;
            }
            if (end>=json.length()) return null;
            return json.substring(index+1, end);
        }
        int end=index;
        while (end<json.length()&&json.charAt(end)!=','&&json.charAt(end)!='}') end++;
        return json.substring(index, end).trim();
    }

    protected List<String> extractJsonArray(String json, String key){
        List<String> result=new ArrayList<>();
        if (json==null) return result;
        int index=key==null||key.isEmpty()?json.indexOf("["):json.indexOf("\""+key+"\":");
        if (index==-1) return result;
        index=json.indexOf("[", index);
        if (index==-1) return result;
        int depth=1;
        int i=index+1;
        while (i<json.length()&&depth>0){
            if (json.charAt(i)=='[') depth++;
            else if (json.charAt(i)==']') depth--;
            i++;
        }
        if (depth!=0) return result;
        String body=json.substring(index+1, i-1);
        StringBuilder current=new StringBuilder();
        int braceDepth=0;
        boolean inString=false;
        char last=0;
        for (char c:body.toCharArray()){
            if (c=='"'&&last!='\\') inString=!inString;
            if (!inString){
                if (c=='{') braceDepth++;
                if (c=='}') braceDepth--;
                if (c==','&&braceDepth==0){
                    String item=current.toString().trim();
                    if (!item.isEmpty()) result.add(item);
                    current.setLength(0);
                    last=c;
                    continue;
                }
            }
            current.append(c);
            last=c;
        }
        String lastItem=current.toString().trim();
        if (!lastItem.isEmpty()) result.add(lastItem);
        return result;
    }
}