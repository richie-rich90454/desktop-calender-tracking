package ai;

import model.Event;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

/*
 * OpenAI SDK-compatible AI client for multiple providers.
 *
 * Responsibilities:
 * - Communicate with OpenAI API and compatible services (DeepSeek, OpenRouter, etc.)
 * - Dynamically fetch available models from provider APIs
 * - Format requests according to OpenAI specification
 * - Parse OpenAI-style JSON responses
 * - Handle provider-specific configurations
 *
 * Java data types used:
 * - HttpRequest with Bearer authentication
 * - URI for API endpoints
 * - List<Event> for generated events
 * - String for JSON manipulation
 * - Map for provider configurations
 *
 * Java technologies involved:
 * - java.net.http for HTTP communication
 * - String manipulation for JSON parsing
 * - Java 17 text blocks for request templates
 * - Dynamic model fetching with caching
 *
 * Design intent:
 * Supports all OpenAI SDK-compatible APIs with dynamic model discovery.
 * Models are fetched from provider endpoints, not hardcoded.
 * Provider-specific endpoints and configurations are supported.
 */
public class OpenAICompatibleClient extends BaseAIClient{
    private static final String DEFAULT_ENDPOINT="https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL="gpt-5-mini-2025-08-07";
    private static final double DEFAULT_COST_PER_1K=0.002;
    private List<String> supportedModels=new ArrayList<>();
    private boolean modelsFetched=false;
    public OpenAICompatibleClient(){
        super();
        this.endpoint=DEFAULT_ENDPOINT;
        this.model=DEFAULT_MODEL;
    }
    public OpenAICompatibleClient(String apiKey){
        this();
        this.apiKey=apiKey;
    }
    public OpenAICompatibleClient(String apiKey, String endpoint){
        this(apiKey);
        if (endpoint!=null&&!endpoint.isEmpty()){
            this.endpoint=endpoint;
        }
    }
    @Override
    public String getProviderName(){
        return "OpenAI (Compatible)";
    }
    @Override
    public boolean isOfflineCapable(){
        return false;
    }
    @Override
    public double getCostPerThousandTokens(){
        return DEFAULT_COST_PER_1K;
    }
    @Override
    public int getMaxTokensPerRequest(){
        return 8192;
    }
    @Override
    public List<String> getSupportedModels(){
        if (!modelsFetched&&apiKey!=null&&!apiKey.isEmpty()){
            try{
                supportedModels=fetchModelsViaAPI();
                modelsFetched=true;
            }
            catch (Exception e){
                throw new RuntimeException("Failed to fetch models from provider API: "+e.getMessage(), e);
            }
        }
        return new ArrayList<>(supportedModels);
    }
    private List<String> fetchModelsViaAPI() throws AIException{
        try{
            String modelsEndpoint=constructModelsEndpoint();
            HttpRequest request=HttpRequest.newBuilder().uri(URI.create(modelsEndpoint)).header("Authorization", "Bearer "+apiKey).GET().build();
            String response=httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
            return parseModelsFromResponse(response);
            
        }
        catch (Exception e){
            throw new AIException("Failed to fetch models from API: "+e.getMessage(), AIException.ErrorType.NETWORK_ERROR, e);
        }
    }
    private String constructModelsEndpoint(){
        if (endpoint.contains("/chat/completions")){
            return endpoint.replace("/chat/completions", "/models");
        }
        else if (endpoint.contains("/v1/chat/completions")){
            return endpoint.replace("/v1/chat/completions", "/v1/models");
        }
        if (endpoint.endsWith("/")){
            return endpoint+"models";
        }
        return endpoint+"/models";
    }
    private List<String> parseModelsFromResponse(String response){
        List<String> models=new ArrayList<>();
        try{
            String data=extractJsonArray(response, "data").toString();
            if (!data.isEmpty()){
                List<String> modelItems=extractJsonArray(data, "");
                for (String item:modelItems){
                    String modelId=extractJsonValue(item, "id");
                    if (modelId!=null){
                        models.add(modelId);
                    }
                }
            }
            if (models.isEmpty()){
                List<String> directModels=extractJsonArray(response, "");
                for (String modelItem:directModels){
                    String modelId=extractJsonValue(modelItem, "id")!=null?extractJsonValue(modelItem, "id"):modelItem;
                    if (modelId!=null&&!modelId.isEmpty()){
                        models.add(modelId);
                    }
                }
            }
        }
        catch (Exception e){
            throw new RuntimeException("Failed to parse models response: "+e.getMessage(), e);
        }
        if (models.isEmpty()){
            throw new RuntimeException("No models found in API response");
        }
        return models;
    }
    public void refreshModels() throws AIException{
        supportedModels=fetchModelsViaAPI();
        modelsFetched=true;
    }
    @Override
    public List<Event> generateEvents(String goalDescription, LocalDate startDate, int days, List<Event> existingEvents) throws AIException{
        try{
            String requestBody=buildEventGenerationRequest(goalDescription, startDate, days, existingEvents);
            String response=sendRequest(requestBody);
            int promptTokens=estimateTokens(requestBody);
            int completionTokens=estimateTokens(response);
            updateUsageStats(promptTokens, completionTokens);
            return parseResponse(response, startDate, days, existingEvents);
        }
        catch (AIException e){
            throw e;
        }
        catch (Exception e){
            throw new AIException("Failed to generate events: "+e.getMessage(), AIException.ErrorType.OTHER, e);
        }
    }
    @Override
    protected HttpRequest buildHttpRequest(String requestBody){
        return HttpRequest.newBuilder().uri(URI.create(endpoint)).header("Content-Type", "application/json").header("Authorization", "Bearer "+apiKey).POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
    }
    @Override
    protected String buildTestRequest(String prompt){
        return String.format("""
        {
                "model": "%s",
                "messages": [{"role": "user", "content": "%s"}],
                "max_tokens": 10,
                "temperature": 0.1
            }
            """, model, escapeJson(prompt));
    }
    protected String buildEventGenerationRequest(String goalDescription, LocalDate startDate, int days, List<Event> existingEvents){
        String existingEventsStr=formatExistingEvents(existingEvents);
        return String.format("""
        {
                "model": "%s",
                "messages": [
                {
                        "role": "system",
                        "content": "You are a calendar planning assistant. Generate realistic calendar events based on the user's goal. Return events in JSON format: [{\\"title\\": \\"string\\", \\"date\\": \\"YYYY-MM-DD\\", \\"start_time\\": \\"HH:MM\\", \\"end_time\\": \\"HH:MM\\"}]. Consider time blocks of 30-120 minutes. Avoid overlapping events. Include breaks between events."
                    },
                {
                        "role": "user",
                        "content": "Goal: %s\\nStart Date: %s\\nDays to plan: %d\\nExisting events to avoid: %s\\n\\nGenerate a realistic schedule for the next %d days. Return only the JSON array, no other text."
                    }
                ],
                "max_tokens": 2000,
                "temperature": 0.7,
                "response_format":{"type": "json_object"}
            }
            """, model, escapeJson(goalDescription), startDate, days, existingEventsStr, days);
    }
    @Override
    protected List<Event> parseResponse(String response, LocalDate startDate, int days, List<Event> existingEvents) throws AIException{
        try{
            String content=extractContentFromResponse(response);
            if (content==null||content.isEmpty()){
                throw new AIException("Empty response from AI", AIException.ErrorType.INVALID_RESPONSE);
            }
            int arrayStart=content.indexOf('[');
            int arrayEnd=content.lastIndexOf(']');
            if (arrayStart==-1||arrayEnd==-1||arrayEnd <= arrayStart){
                throw new AIException("No valid JSON array found in response: "+content, AIException.ErrorType.INVALID_RESPONSE);
            }
            String jsonArray=content.substring(arrayStart, arrayEnd+1);
            List<String> eventItems=extractJsonArray(jsonArray, "");
            if (eventItems.isEmpty()){
                eventItems=parseDirectJsonArray(jsonArray);
            }
            List<Event> events=new ArrayList<>();
            for (String item:eventItems){
                Event event=parseEventItem(item);
                if (event!=null){
                    events.add(event);
                }
            }
            if (events.isEmpty()){
                throw new AIException("No valid events found in response", AIException.ErrorType.INVALID_RESPONSE);
            }
            return events;
        }
        catch (AIException e){
            throw e;
        }
        catch (Exception e){
            throw new AIException("Failed to parse AI response: "+e.getMessage(), AIException.ErrorType.INVALID_RESPONSE, e);
        }
    }
    private String extractContentFromResponse(String response){
        try{
            String content=extractJsonValue(response, "content");
            if (content!=null){
                return content;
            }
            String messageContent=extractJsonValue(response, "message");
            if (messageContent!=null){
                content=extractJsonValue(messageContent, "content");
                if (content!=null){
                    return content;
                }
            }
            List<String> choices=extractJsonArray(response, "choices");
            if (!choices.isEmpty()){
                for (String choice:choices){
                    String message=extractJsonValue(choice, "message");
                    if (message!=null){
                        content=extractJsonValue(message, "content");
                        if (content!=null){
                            return content;
                        }
                    }
                }
            }
            return response;
        }
        catch (Exception e){
            return response;
        }
    }
    private Event parseEventItem(String jsonItem) throws AIException{
        try{
            String title=extractJsonValue(jsonItem, "title");
            String date=extractJsonValue(jsonItem, "date");
            String startTime=extractJsonValue(jsonItem, "start_time");
            String endTime=extractJsonValue(jsonItem, "end_time");
            if (title==null||date==null||startTime==null||endTime==null){
                title=extractJsonValue(jsonItem, "name")!=null?extractJsonValue(jsonItem, "name"):title;
                date=extractJsonValue(jsonItem, "date");
                startTime=extractJsonValue(jsonItem, "startTime")!=null?extractJsonValue(jsonItem, "startTime"):startTime;
                endTime=extractJsonValue(jsonItem, "endTime")!=null?extractJsonValue(jsonItem, "endTime"):endTime;
                if (title==null||date==null||startTime==null||endTime==null){
                    throw new AIException("Missing required fields in event item: "+jsonItem, AIException.ErrorType.INVALID_RESPONSE);
                }
            }
            startTime=normalizeTimeFormat(startTime);
            endTime=normalizeTimeFormat(endTime);
            LocalTime start=LocalTime.parse(startTime, TIME_FORMATTER);
            LocalTime end=LocalTime.parse(endTime, TIME_FORMATTER);
            if (!end.isAfter(start)){
                throw new AIException("End time must be after start time: "+startTime+" - "+endTime, AIException.ErrorType.INVALID_RESPONSE);
            }
            return createEvent(title, date, startTime, endTime);
        }
        catch (Exception e){
            throw new AIException("Failed to parse event item: "+e.getMessage(), AIException.ErrorType.INVALID_RESPONSE);
        }
    }
    private String normalizeTimeFormat(String time) throws AIException{
        try{
            time=time.trim().toUpperCase();
            time=time.replace("AM", "").replace("PM", "").trim();
            LocalTime parsedTime;
            if (time.split(":").length==2){
                parsedTime=LocalTime.parse(time+":00");
                return parsedTime.format(TIME_FORMATTER);
            }
            else{
                parsedTime=LocalTime.parse(time);
                return parsedTime.format(TIME_FORMATTER);
            }
        }
        catch (Exception e){
            throw new AIException("Invalid time format: "+time, AIException.ErrorType.INVALID_RESPONSE);
        }
    }
    private List<String> parseDirectJsonArray(String jsonArray){
        List<String> items=new ArrayList<>();
        StringBuilder current=new StringBuilder();
        int depth=0;
        boolean inString=false;
        char lastChar=0;
        for (int i=0;i<jsonArray.length();i++){
            char c=jsonArray.charAt(i);
            if (c=='"'&&lastChar!='\\'){
                inString=!inString;
            }
            else if (!inString){
                if (c=='{'){
                    depth++;
                }
                else if (c=='}'){
                    depth--;
                }
                else if (c==','&&depth==0){
                    String item=current.toString().trim();
                    if (!item.isEmpty()&&item.startsWith("{")&&item.endsWith("}")){
                        items.add(item);
                    }
                    current=new StringBuilder();
                    continue;
                }
            }
            current.append(c);
            lastChar=c;
        }
        String lastItem=current.toString().trim();
        if (!lastItem.isEmpty()&&lastItem.startsWith("{")&&lastItem.endsWith("}")){
            items.add(lastItem);
        }
        return items;
    }
    private String formatExistingEvents(List<Event> existingEvents){
        if (existingEvents==null||existingEvents.isEmpty()){
            return "None";
        }
        StringBuilder sb=new StringBuilder();
        for (Event event:existingEvents){
            sb.append(String.format("%s: %s %s-%s;", 
                event.getDate(),
                event.getTitle(),
                event.getStartTime(),
                event.getEndTime()
            ));
        }
        return sb.toString();
    }
    private String escapeJson(String text){
        if (text==null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}