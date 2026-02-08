package ai;

import model.Event;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

public abstract class BaseAIClient implements AIClient {
    protected String apiKey;
    protected String endpoint;
    protected String model;
    protected final HttpClient httpClient;
    protected final UsageStats lastUsageStats;
    protected final UsageStats totalUsageStats;
    protected static final DateTimeFormatter DATE_FORMATTER=
        DateTimeFormatter.ISO_LOCAL_DATE;
    protected static final DateTimeFormatter TIME_FORMATTER=
        DateTimeFormatter.ISO_LOCAL_TIME;
    
    protected BaseAIClient(){
        this.httpClient=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).version(HttpClient.Version.HTTP_2).build();
        this.lastUsageStats=new UsageStats();
        this.totalUsageStats=new UsageStats();
    }
    @Override
    public abstract List<Event> generateEvents(String goalDescription, LocalDate startDate, int days, List<Event> existingEvents) throws AIException;
    @Override
    public boolean testConnection() throws AIException{
        try{
            String testPrompt="Say \"OK\" if you can hear me.";
            String response=sendRequest(buildTestRequest(testPrompt));
            return response != null && !response.isEmpty();
        } catch (Exception e){
            throw new AIException("Connection test failed: " + e.getMessage(), 
                AIException.ErrorType.NETWORK_ERROR, e);
        }
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
    
    protected String sendRequest(String requestBody) throws AIException{
        int maxRetries=3;
        int retryDelay=1000;
        
        for (int attempt=1; attempt <= maxRetries; attempt++){
            try{
                HttpRequest request=buildHttpRequest(requestBody);
                HttpResponse<String> response=httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString()
                );
                
                if (response.statusCode() == 200){
                    return response.body();
                } else if (response.statusCode() == 429){
                    if (attempt < maxRetries){
                        Thread.sleep(retryDelay * attempt);
                        continue;
                    }
                    throw new AIException("Rate limit exceeded", 
                        AIException.ErrorType.RATE_LIMIT_ERROR);
                } else if (response.statusCode() == 401){
                    throw new AIException("Invalid API key", 
                        AIException.ErrorType.AUTHENTICATION_ERROR);
                } else if (response.statusCode() >= 500){
                    if (attempt < maxRetries){
                        Thread.sleep(retryDelay * attempt);
                        continue;
                    }
                    throw new AIException("Server error: " + response.statusCode(), 
                        AIException.ErrorType.SERVER_ERROR);
                } else{
                    throw new AIException("API error: " + response.statusCode() + 
                        " - " + response.body(), AIException.ErrorType.OTHER);
                }
            } catch (InterruptedException e){
                Thread.currentThread().interrupt();
                throw new AIException("Request interrupted", 
                    AIException.ErrorType.OTHER, e);
            } catch (Exception e){
                if (attempt == maxRetries){
                    throw new AIException("Request failed after " + maxRetries + 
                        " attempts: " + e.getMessage(), 
                        AIException.ErrorType.NETWORK_ERROR, e);
                }
                try{
                    Thread.sleep(retryDelay * attempt);
                } catch (InterruptedException ie){
                    Thread.currentThread().interrupt();
                    throw new AIException("Retry interrupted", 
                        AIException.ErrorType.OTHER, ie);
                }
            }
        }
        throw new AIException("Request failed after " + maxRetries + " attempts", 
            AIException.ErrorType.NETWORK_ERROR);
    }
    
    protected abstract HttpRequest buildHttpRequest(String requestBody);
    protected abstract String buildTestRequest(String prompt);
    protected abstract List<Event> parseResponse(String response, 
        LocalDate startDate, int days, List<Event> existingEvents) 
        throws AIException;
    protected abstract double getCostPerThousandTokens();
    
    protected String extractJsonValue(String json, String key){
        String searchKey="\"" + key + "\":";
        int startIndex=json.indexOf(searchKey);
        if (startIndex == -1) return null;
        
        startIndex += searchKey.length();
        int endIndex=json.indexOf(",", startIndex);
        if (endIndex == -1) endIndex=json.indexOf("}", startIndex);
        if (endIndex == -1) return null;
        
        String value=json.substring(startIndex, endIndex).trim();
        if (value.startsWith("\"") && value.endsWith("\"")){
            value=value.substring(1, value.length() - 1);
        }
        return value;
    }
    
    protected List<String> extractJsonArray(String json, String key){
        List<String> result=new ArrayList<>();
        String searchKey="\"" + key + "\":";
        int startIndex=json.indexOf(searchKey);
        if (startIndex == -1) return result;
        
        startIndex += searchKey.length();
        startIndex=json.indexOf("[", startIndex);
        if (startIndex == -1) return result;
        
        int bracketCount=1;
        int currentIndex=startIndex + 1;
        
        while (currentIndex < json.length() && bracketCount > 0){
            char c=json.charAt(currentIndex);
            if (c == '[') bracketCount++;
            else if (c == ']') bracketCount--;
            currentIndex++;
        }
        
        if (bracketCount != 0) return result;
        
        String arrayContent=json.substring(startIndex + 1, currentIndex - 1).trim();
        if (arrayContent.isEmpty()) return result;
        
        StringBuilder currentItem=new StringBuilder();
        int nestedBraces=0;
        int nestedBrackets=0;
        
        for (int i=0; i < arrayContent.length(); i++){
            char c=arrayContent.charAt(i);
            if (c == '{') nestedBraces++;
            else if (c == '}') nestedBraces--;
            else if (c == '[') nestedBrackets++;
            else if (c == ']') nestedBrackets--;
            
            if (c == ',' && nestedBraces == 0 && nestedBrackets == 0){
                String item=currentItem.toString().trim();
                if (!item.isEmpty()) result.add(item);
                currentItem=new StringBuilder();
            } else{
                currentItem.append(c);
            }
        }
        
        String lastItem=currentItem.toString().trim();
        if (!lastItem.isEmpty()) result.add(lastItem);
        return result;
    }
    
    protected Event createEvent(String title, String dateStr, 
                               String startTimeStr, String endTimeStr) 
                               throws AIException{
        try{
            LocalDate date=LocalDate.parse(dateStr, DATE_FORMATTER);
            LocalTime startTime=LocalTime.parse(startTimeStr, TIME_FORMATTER);
            LocalTime endTime=LocalTime.parse(endTimeStr, TIME_FORMATTER);
            return new Event(title, date, startTime, endTime);
        } catch (Exception e){
            throw new AIException("Failed to create event from: " + title + 
                ", " + dateStr + ", " + startTimeStr + "-" + endTimeStr, 
                AIException.ErrorType.INVALID_RESPONSE, e);
        }
    }
    
    protected void updateUsageStats(int promptTokens, int completionTokens){
        double costPerThousandTokens=getCostPerThousandTokens();
        lastUsageStats.addRequest(promptTokens, completionTokens, 
            costPerThousandTokens);
        totalUsageStats.addRequest(promptTokens, completionTokens, 
            costPerThousandTokens);
    }
    
    protected int estimateTokens(String text){
        return (int) Math.ceil(text.length() / 4.0);
    }
}