package ai;

import model.Event;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

/*
 * OpenAI SDK-compatible AI client for multiple providers (DeepSeek, OpenRouter, etc.)
 *
 * Responsibilities:
 * - Communicate with OpenAI-compatible APIs
 * - Use AIPromptManager for structured prompts
 * - Use AIJsonParser for robust JSON parsing
 * - Handle provider-specific configurations
 *
 * Design intent:
 * Supports all OpenAI SDK-compatible APIs with proper JSON response parsing.
 * Uses separate prompts.txt file for easy editing.
 */

public class OpenAICompatibleClient extends BaseAIClient{
    private static final String DEFAULT_ENDPOINT="https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL="gpt-3.5-turbo";
    private final AIPromptManager promptManager;
    private List<String> supportedModels=new ArrayList<>();
    private boolean modelsFetched=false;
    public OpenAICompatibleClient(){
        super();
        this.endpoint=DEFAULT_ENDPOINT;
        this.model=DEFAULT_MODEL;
        this.promptManager=new AIPromptManager();
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
    public int getMaxTokensPerRequest(){
        return 4000;
    }
    @Override
    public List<String> getSupportedModels(){
        if (!modelsFetched&&apiKey!=null&&!apiKey.isEmpty()){
            try{
                supportedModels=fetchModelsViaAPI();
                modelsFetched=true;
            }
            catch (Exception e){
                supportedModels=new ArrayList<>();
            }
        }
        return new ArrayList<>(supportedModels);
    }
    @Override
    public List<Event> generateEvents(String goalDescription, LocalDate startDate, int days, List<Event> existingEvents) throws AIException{
        return generateEvents(goalDescription, startDate, days, existingEvents, null);
    }
    @Override
    public List<Event> generateEvents(String goalDescription, LocalDate startDate, int days,List<Event> existingEvents, ProgressCallback callback) throws AIException{
        try{
            System.out.println("=== ENTERING generateEvents ===");
            System.out.println("Goal: "+goalDescription);
            System.out.println("Start Date: "+startDate);
            System.out.println("Days: "+days);
            System.out.println("Existing events: "+(existingEvents!=null?existingEvents.size():0));
            if (callback!=null){
                callback.update("Starting AI event generation...");
                callback.update("Goal: "+goalDescription);
                callback.update("Start date: "+startDate+", Days: "+days);
            }
            else{
                System.out.println("WARNING: No callback provided!");
            }
            System.out.println("Checking configuration...");
            System.out.println("API Key set: "+(apiKey!=null&&!apiKey.isEmpty()));
            System.out.println("Endpoint: "+endpoint);
            System.out.println("Model: "+model);
            ensureConfigured();
            if (callback!=null){
                callback.update("Configuration OK. Building request...");
            }
            System.out.println("Building request...");
            String requestBody=buildEventGenerationRequest(goalDescription, startDate, days, existingEvents);
            System.out.println("Request body length: "+requestBody.length());
            if (callback!=null){
                callback.update("Request built. Sending to AI API...");
                callback.update("Endpoint: "+endpoint);
                callback.update("Model: "+model);
            }
            System.out.println("Sending request...");
            String response=sendRequest(requestBody);
            System.out.println("Response received, length: "+response.length());
            updateUsageStats(estimateTokens(requestBody), estimateTokens(response));
            if (callback!=null){
                callback.update("Response received. Parsing...");
            }
            System.out.println("Parsing response...");
            List<Event> events=parseResponse(response, startDate, days, existingEvents);
            System.out.println("Parsed "+events.size()+" events");
            if (callback!=null){
                callback.updateSuccess("Successfully parsed "+events.size()+" events");
                for (Event event:events){
                    if (callback.isCancelled()) break;
                    callback.updateEvent(event);
                }
            }
            System.out.println("=== EXITING generateEvents ===");
            return events;
        }
        catch (AIException e){
            System.err.println("AIException in generateEvents: "+e.getMessage());
            e.printStackTrace();
            if (callback!=null){
                callback.updateError("AI Error: "+e.getMessage());
                callback.updateError("Error type: "+e.getErrorType());
            }
            throw e;
        }
        catch (Exception e){
            System.err.println("Exception in generateEvents: "+e.getMessage());
            e.printStackTrace();
            if (callback!=null){
                callback.updateError("Unexpected error: "+e.getMessage());
            }
            throw new AIException("Failed to generate events: "+e.getMessage(),AIException.ErrorType.OTHER, e);
        }
    }
    private List<String> fetchModelsViaAPI() throws AIException{
        try{
            String modelsEndpoint=constructModelsEndpoint();
            HttpRequest request=HttpRequest.newBuilder().uri(URI.create(modelsEndpoint)).header("Authorization", "Bearer "+apiKey).GET().build();
            HttpResponse<String> response=httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200){
                throw new AIException("Failed to fetch models: HTTP "+response.statusCode(),AIException.ErrorType.SERVER_ERROR);
            }
            return parseModelsFromResponse(response.body());
        }
        catch (Exception e){
            throw new AIException("Failed to fetch models from API: "+e.getMessage(),AIException.ErrorType.NETWORK_ERROR, e);
        }
    }
    private String constructModelsEndpoint(){
        if (endpoint.contains("/chat/completions")){
            return endpoint.replace("/chat/completions", "/models");
        }
        if (endpoint.contains("/v1/chat/completions")){
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
            int dataStart=response.indexOf("\"data\"");
            if (dataStart!=-1){
                int arrayStart=response.indexOf("[", dataStart);
                if (arrayStart!=-1){
                    int arrayEnd=findMatchingBracket(response, arrayStart);
                    if (arrayEnd!=-1){
                        String dataArray=response.substring(arrayStart+1, arrayEnd);
                        String[] modelEntries=dataArray.split("\\},\\s*\\{");
                        for (String entry:modelEntries){
                            String cleaned=entry.trim();
                            if (!cleaned.startsWith("{")) cleaned="{"+cleaned;
                            if (!cleaned.endsWith("}")) cleaned=cleaned+"}";
                            String id=extractJsonField(cleaned, "id");
                            if (id!=null&&!id.isEmpty()&&!id.startsWith("ft:")){
                                models.add(id);
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        if (models.isEmpty()){
            models.add("gpt-3.5-turbo");
            models.add("gpt-4");
            models.add("gpt-4-turbo");
        }
        return models;
    }
    public void refreshModels() throws AIException{
        supportedModels=fetchModelsViaAPI();
        modelsFetched=true;
    }
    @Override
    protected HttpRequest buildHttpRequest(String requestBody){
        return HttpRequest.newBuilder().uri(URI.create(endpoint)).header("Content-Type", "application/json").header("Authorization", "Bearer "+apiKey).header("Accept", "application/json").timeout(Duration.ofSeconds(60)).POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
    }
    @Override
    protected String buildTestRequest(String prompt){
        return String.format("{\"model\": \"%s\", \"messages\": [{\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 10, \"temperature\": 0.1}",model,AIJsonParser.escapeJsonString(prompt));
    }
    protected String buildEventGenerationRequest(String goalDescription, LocalDate startDate, int days, List<Event> existingEvents){
        String systemPrompt=promptManager.getSystemPrompt();
        String userPrompt=promptManager.getEventGenerationPrompt(goalDescription, startDate, days, existingEvents);
        System.out.println("=== SYSTEM PROMPT ===");
        System.out.println(systemPrompt);
        System.out.println("=== USER PROMPT ===");
        System.out.println(userPrompt);
        return String.format("{\"model\": \"%s\", \"messages\": [{\"role\": \"system\", \"content\": \"%s\"}, {\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 2000, \"temperature\": 0.7}",model,AIJsonParser.escapeJsonString(systemPrompt),AIJsonParser.escapeJsonString(userPrompt));
    }
    @Override
    protected List<Event> parseResponse(String response, LocalDate startDate, int days, List<Event> existingEvents) throws AIException{
        try{
            if (response.contains("\"error\"")){
                String errorMsg=extractJsonField(response, "message");
                if (errorMsg==null) errorMsg="Unknown API error";
                throw new AIException("API Error: "+errorMsg, AIException.ErrorType.SERVER_ERROR);
            }
            String content=extractContentFromResponse(response);
            if (content==null||content.trim().isEmpty()){
                System.out.println("Content extraction failed, trying alternative parsing...");
                content=extractContentAlternative(response);
            }
            if (content==null||content.trim().isEmpty()){
                throw new AIException("Empty response from AI", AIException.ErrorType.INVALID_RESPONSE);
            }
            System.out.println("=== EXTRACTED CONTENT ===");
            System.out.println(content);
            System.out.println("========================");
            content=cleanJsonContent(content);
            return AIJsonParser.parseAIResponse(content);
        }
        catch (AIException e){
            String errorMsg=e.getMessage()+"\nRaw response: "+(response.length() >500?response.substring(0, 500)+"...":response);
            throw new AIException(errorMsg, e.getErrorType(), e);
        }
        catch (Exception e){
            e.printStackTrace();
            throw new AIException("Failed to parse response: "+e.getMessage()+"\nResponse: "+response.substring(0, Math.min(200, response.length())),AIException.ErrorType.INVALID_RESPONSE, e);
        }
    }
    private String extractContentFromResponse(String response){
        try{
            System.out.println("=== EXTRACTING CONTENT ===");
            if (response.trim().startsWith("{")){
                int contentStart=response.indexOf("\"content\"");
                if (contentStart!=-1){
                    int colon=response.indexOf(":", contentStart);
                    int quoteStart=response.indexOf("\"", colon+1);
                    if (quoteStart!=-1){
                        int quoteEnd=quoteStart+1;
                        int backslashCount=0;
                        while (quoteEnd < response.length()){
                            char c=response.charAt(quoteEnd);
                            if (c == '\\'){
                                backslashCount++;
                            }
                            else if (c == '"'&&(backslashCount%2==0)){
                                break;
                            }
                            else{
                                backslashCount=0;
                            }
                            quoteEnd++;
                        }
                        if (quoteEnd < response.length()){
                            String content=response.substring(quoteStart+1, quoteEnd);
                            System.out.println("Extracted content from \"content\" field");
                            return unescapeJsonString(content);
                        }
                    }
                }
            }
            System.out.println("Returning full response as content");
            return response.trim();
        }
        catch (Exception e){
            System.out.println("Error extracting content: "+e.getMessage());
            return response.trim();
        }
    }
    private String unescapeJsonString(String input){
        if (input==null) return "";
        return input.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\\", "\\");
    }
    private String extractContentAlternative(String response){
        int start=response.indexOf('{');
        if (start==-1) return null;
        int braceCount=0;
        boolean inString=false;
        int end=-1;
        for (int i=start; i < response.length(); i++){
            char c=response.charAt(i);
            if (c == '"'&&(i ==0||response.charAt(i-1)!='\\')){
                inString=!inString;
            }
            if (!inString){
                if (c == '{') braceCount++;
                else if (c == '}'){
                    braceCount--;
                    if (braceCount==0){
                        end=i;
                        break;
                    }
                }
            }
        }
        if (end > start){
            return response.substring(start, end+1);
        }
        return null;
    }
    private String cleanJsonContent(String content){
        content=content.trim();
        if (content.startsWith("```json")){
            content=content.substring(7);
        }
        else if (content.startsWith("```")){
            content=content.substring(3);
        }
        if (content.endsWith("```")){
            content=content.substring(0, content.length() -3);
        }
        content=content.trim();
        int jsonStart=content.indexOf('{');
        if (jsonStart >0){
            content=content.substring(jsonStart);
        }
        return content;
    }
    private String extractJsonField(String json, String fieldName){
        String pattern="\""+fieldName+"\"";
        int pos=json.indexOf(pattern);
        if (pos==-1) return null;
        int colon=json.indexOf(":", pos+pattern.length());
        if (colon==-1) return null;
        int valueStart=colon+1;
        while (valueStart < json.length()&&Character.isWhitespace(json.charAt(valueStart))){
            valueStart++;
        }
        if (valueStart >= json.length()) return null;
        char firstChar=json.charAt(valueStart);
        if (firstChar == '"'){
            int quoteEnd=valueStart+1;
            int backslashCount=0;
            while (quoteEnd < json.length()){
                char c=json.charAt(quoteEnd);
                if (c == '\\'){
                    backslashCount++;
                }
                else if (c == '"'&&(backslashCount%2==0)){
                    break;
                }
                else{
                    backslashCount=0;
                }
                quoteEnd++;
            }
            if (quoteEnd < json.length()){
                return json.substring(valueStart+1, quoteEnd);
            }
        }
        else{
            int valueEnd=valueStart;
            while (valueEnd < json.length()&&json.charAt(valueEnd) != ','&&json.charAt(valueEnd)!='}'&&!Character.isWhitespace(json.charAt(valueEnd))){
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd).trim();
        }
        return null;
    }
    private String[] splitJsonObjects(String jsonArray){
        List<String> objects=new ArrayList<>();
        int start=0;
        int braceCount=0;
        boolean inString=false;
        for (int i=0; i < jsonArray.length(); i++){
            char c=jsonArray.charAt(i);
            if (c == '"'&&(i ==0||jsonArray.charAt(i-1)!='\\')){
                inString=!inString;
            }
            if (!inString){
                if (c == '{'){
                    if (braceCount==0){
                        start=i;
                    }
                    braceCount++;
                }
                else if (c == '}'){
                    braceCount--;
                    if (braceCount==0){
                        objects.add(jsonArray.substring(start, i+1));
                    }
                }
            }
        }
        return objects.toArray(new String[0]);
    }
    private int findMatchingBracket(String json, int start){
        int bracketCount=0;
        boolean inString=false;
        for (int i=start; i < json.length(); i++){
            char c=json.charAt(i);
            if (c == '"'&&(i ==0||json.charAt(i-1)!='\\')){
                inString=!inString;
            }
            if (!inString){
                if (c == '['||c == '{'){
                    bracketCount++;
                }
                else if (c == ']'||c == '}'){
                    bracketCount--;
                    if (bracketCount==0){
                        return i;
                    }
                }
            }
        }
        return -1;
    }
    @Override
    public boolean testConnection() throws AIException{
        ensureConfigured();
        try{
            String response=sendRequest(buildTestRequest(promptManager.getTestPrompt()));
            return response!=null&&response.contains("OK");
        }
        catch (Exception e){
            throw new AIException("Connection test failed: "+e.getMessage(),AIException.ErrorType.NETWORK_ERROR, e);
        }
    }
}