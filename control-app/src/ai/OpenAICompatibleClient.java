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
                    "content": "You are a calendar planning assistant. Return events as JSON array with title, date, start_time, end_time."
                },
                {
                    "role": "user",
                    "content": "Goal: %s\\nStart Date: %s\\nDays: %d\\nExisting events: %s"
                }
            ],
            "max_tokens": 2000,
            "temperature": 0.7
        }
        """, model, escapeJson(goalDescription), startDate, days, escapeJson(formatExistingEvents(existingEvents)));
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
        for (String c:choices){
            String msg=extractJsonValue(c, "message");
            if (msg!=null){
                content=extractJsonValue(msg, "content");
                if (content!=null) return content;
            }
        }
        return response;
    }
    private Event parseEventItem(String json) throws AIException{
        String title=extractJsonValue(json, "title");
        String date=extractJsonValue(json, "date");
        String start=normalizeTimeFormat(extractJsonValue(json, "start_time"));
        String end=normalizeTimeFormat(extractJsonValue(json, "end_time"));
        if (title==null||date==null||start==null||end==null) throw new AIException("Invalid event item: "+json, AIException.ErrorType.INVALID_RESPONSE);
        LocalTime st=LocalTime.parse(start, TIME_FORMATTER);
        LocalTime et=LocalTime.parse(end, TIME_FORMATTER);
        if (!et.isAfter(st)) throw new AIException("End time before start time", AIException.ErrorType.INVALID_RESPONSE);
        return createEvent(title, date, start, end);
    }
    private String normalizeTimeFormat(String time) throws AIException{
        try{
            if (time==null) throw new IllegalArgumentException();
            time=time.trim();
            if (time.length() ==5) time=time+":00";
            return LocalTime.parse(time).format(TIME_FORMATTER);
        }
        catch (Exception e){
            throw new AIException("Invalid time format: "+time, AIException.ErrorType.INVALID_RESPONSE);
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