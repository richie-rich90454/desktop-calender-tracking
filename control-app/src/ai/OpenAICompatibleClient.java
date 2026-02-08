package ai;

import model.Event;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.ArrayList;
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
 * Design intent:
 * Supports all OpenAI SDK-compatible APIs with dynamic model discovery.
 * Models are fetched from provider endpoints, not hardcoded.
 */

public class OpenAICompatibleClient extends BaseAIClient{
    private static final String DEFAULT_ENDPOINT="https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL="gpt-5-mini-2025-08-07";
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
        if (endpoint!=null&&!endpoint.isEmpty()) this.endpoint=endpoint;
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
        return 0.0; // Cost estimation disabled as per user request
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
        if (endpoint.contains("/chat/completions")) return endpoint.replace("/chat/completions", "/models");
        if (endpoint.contains("/v1/chat/completions")) return endpoint.replace("/v1/chat/completions", "/v1/models");
        if (endpoint.endsWith("/")) return endpoint+"models";
        return endpoint+"/models";
    }
    private List<String> parseModelsFromResponse(String response){
        List<String> models=new ArrayList<>();
        List<String> items=extractJsonArray(response, "data");
        for (String item:items){
            String id=extractJsonValue(item, "id");
            if (id!=null&&!id.isEmpty()) models.add(id);
        }
        if (models.isEmpty()) throw new RuntimeException("No models found in API response");
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
            updateUsageStats(estimateTokens(requestBody), estimateTokens(response));
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
            "messages": [{"role":"user","content":"%s"}],
            "max_tokens": 10,
            "temperature": 0.1
        }
        """, model, escapeJson(prompt));
    }
    protected String buildEventGenerationRequest(String goalDescription, LocalDate startDate, int days, List<Event> existingEvents){
        return String.format("""
        {
            "model": "%s",
            "messages": [
                {
                    "role": "system",
                    "content": "You are a calendar planning assistant. Generate calendar events based on the user's goal. Return ONLY a valid JSON array of event objects. Each event object must have these exact fields: title (string), date (YYYY-MM-DD), start_time (HH:MM in 24-hour format), end_time (HH:MM in 24-hour format). Example: [{\\"title\\":\\"Study session\\",\\"date\\":\\"2025-02-10\\",\\"start_time\\":\\"14:00\\",\\"end_time\\":\\"16:00\\"}, {\\"title\\":\\"Meeting\\",\\"date\\":\\"2025-02-11\\",\\"start_time\\":\\"10:00\\",\\"end_time\\":\\"11:30\\"}]"
                },
                {
                    "role": "user",
                    "content": "Goal: %s\\nStart Date: %s\\nNumber of days to plan: %d\\nExisting events (avoid conflicts): %s\\n\\nGenerate a schedule of events for the next %d days starting from %s. Make sure events don't overlap with existing events. Return ONLY the JSON array, no other text."
                }
            ],
            "max_tokens": 2000,
            "temperature": 0.7,
            "response_format": { "type": "json_object" }
        }
        """, model, escapeJson(goalDescription), startDate, days, escapeJson(formatExistingEvents(existingEvents)), days, startDate);
    }
    @Override
    protected List<Event> parseResponse(String response, LocalDate startDate, int days, List<Event> existingEvents) throws AIException{
        try{
            String content=extractContentFromResponse(response);
            int start=content.indexOf('[');
            int end=content.lastIndexOf(']');
            if (start==-1||end==-1||end <= start) throw new AIException("No JSON array found", AIException.ErrorType.INVALID_RESPONSE);
            List<String> items=extractJsonArray(content.substring(start, end+1), "");
            List<Event> events=new ArrayList<>();
            for (String item:items) events.add(parseEventItem(item));
            if (events.isEmpty()) throw new AIException("No valid events parsed", AIException.ErrorType.INVALID_RESPONSE);
            return events;
        }
        catch (AIException e){
            throw e;
        }
        catch (Exception e){
            throw new AIException("Failed to parse response: "+e.getMessage(), AIException.ErrorType.INVALID_RESPONSE, e);
        }
    }
    private String extractContentFromResponse(String response){
        String content=extractJsonValue(response, "content");
        if (content!=null) return content;
        List<String> choices=extractJsonArray(response, "choices");
        for (String choice:choices){
            String message=extractJsonValue(choice, "message");
            if (message!=null){
                content=extractJsonValue(message, "content");
                if (content!=null) return content;
            }
            String delta=extractJsonValue(choice, "delta");
            if (delta!=null){
                content=extractJsonValue(delta, "content");
                if (content!=null) return content;
            }
            content=extractJsonValue(choice, "text");
            if (content!=null) return content;
        }
        String cleaned=response.trim();
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            int contentStart=cleaned.indexOf("\"content\":");
            if (contentStart != -1) {
                contentStart += 10; 
                int quoteStart=cleaned.indexOf('"', contentStart);
                if (quoteStart != -1 && quoteStart < cleaned.length()) {
                    int quoteEnd=cleaned.indexOf('"', quoteStart + 1);
                    while (quoteEnd != -1 && quoteEnd > 0 && cleaned.charAt(quoteEnd - 1)=='\\') {
                        quoteEnd=cleaned.indexOf('"', quoteEnd + 1);
                    }
                    if (quoteEnd != -1) {
                        return cleaned.substring(quoteStart + 1, quoteEnd);
                    }
                }
            }
        }
        return cleaned;
    }
    private Event parseEventItem(String json) throws AIException{
        String title=extractJsonValue(json, "title");
        if (title==null) title=extractJsonValue(json, "name");
        if (title==null) title=extractJsonValue(json, "summary");
        
        String date=extractJsonValue(json, "date");
        if (date==null) date=extractJsonValue(json, "day");
        if (date==null) date=extractJsonValue(json, "event_date");
        String start=null;
        String end=null;
        start=extractJsonValue(json, "start_time");
        end=extractJsonValue(json, "end_time");
        if (start==null) start=extractJsonValue(json, "start");
        if (end==null) end=extractJsonValue(json, "end");
        if (start==null) start=extractJsonValue(json, "startTime");
        if (end==null) end=extractJsonValue(json, "endTime");
        if (start==null) start=extractJsonValue(json, "from");
        if (end==null) end=extractJsonValue(json, "to");
        if (start != null) start=normalizeTimeFormat(start);
        if (end != null) end=normalizeTimeFormat(end);
        if (title==null||date==null||start==null||end==null) {
            throw new AIException("Invalid event item: " + json + 
                "\nMissing fields - title: " + title + 
                ", date: " + date + 
                ", start: " + start + 
                ", end: " + end, 
                AIException.ErrorType.INVALID_RESPONSE);
        }
        LocalTime st=LocalTime.parse(start, TIME_FORMATTER);
        LocalTime et=LocalTime.parse(end, TIME_FORMATTER);
        if (!et.isAfter(st)) throw new AIException("End time before start time", AIException.ErrorType.INVALID_RESPONSE);
        return createEvent(title, date, start, end);
    }
    private String normalizeTimeFormat(String time) throws AIException{
        try{
            if (time==null) throw new IllegalArgumentException("Time cannot be null");
            time=time.trim().toUpperCase();
            if (time.isEmpty()) throw new IllegalArgumentException("Time cannot be empty");
            time=time.replaceAll("\\s+", " ");
            try {
                if (time.matches("^\\d{1,2}:\\d{2}$")) {
                    String[] parts=time.split(":");
                    int hour=Integer.parseInt(parts[0]);
                    int minute=Integer.parseInt(parts[1]);
                    if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                        return String.format("%02d:%02d", hour, minute);
                    }
                }
                if (time.matches("^\\d{1,2}:\\d{2}:\\d{2}$")) {
                    String[] parts=time.split(":");
                    int hour=Integer.parseInt(parts[0]);
                    int minute=Integer.parseInt(parts[1]);
                    int second=Integer.parseInt(parts[2]);
                    if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59 && second >= 0 && second <= 59) {
                        return String.format("%02d:%02d", hour, minute);
                    }
                }
                if (time.matches("^\\d{3,4}$") && !time.contains(":")) {
                    int len=time.length();
                    int hour, minute;
                    if (len==3) {
                        hour=Integer.parseInt(time.substring(0, 1));
                        minute=Integer.parseInt(time.substring(1, 3));
                    } else {
                        hour=Integer.parseInt(time.substring(0, 2));
                        minute=Integer.parseInt(time.substring(2, 4));
                    }
                    if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                        return String.format("%02d:%02d", hour, minute);
                    }
                }
                if (time.contains("AM") || time.contains("PM")) {
                    String ampm=time.contains("AM") ? "AM" : "PM";
                    String timePart=time.replace("AM", "").replace("PM", "").trim();
                    if (timePart.matches("^\\d{1,2}(:\\d{2}){0,2}$")) {
                        String[] parts=timePart.split(":");
                        int hour=Integer.parseInt(parts[0]);
                        int minute=parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                        if (ampm.equals("PM") && hour != 12) {
                            hour += 12;
                        } else if (ampm.equals("AM") && hour==12) {
                            hour=0;
                        }
                        
                        if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                            return String.format("%02d:%02d", hour, minute);
                        }
                    }
                }
                try {
                    LocalTime parsed=LocalTime.parse(time);
                    return parsed.format(TIME_FORMATTER);
                } catch (Exception e) {

                }
                
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number in time: " + time);
            }
            throw new IllegalArgumentException("Unrecognized time format: " + time);
            
        } catch (Exception e) {
            throw new AIException("Invalid time format: " + time + " - " + e.getMessage(), 
                                 AIException.ErrorType.INVALID_RESPONSE, e);
        }
    }
    private String formatExistingEvents(List<Event> events){
        if (events==null||events.isEmpty()) return "None";
        StringBuilder sb=new StringBuilder();
        for (Event e:events) sb.append(e.getDate()).append(" ").append(e.getTitle()).append(" ").append(e.getStartTime()).append("-").append(e.getEndTime()).append("; ");
        return sb.toString();
    }
    private String escapeJson(String text){
        if (text==null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}