package ai;

import model.Event;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

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
    public List<Event> generateEvents(String goalDescription, LocalDate startDate, int days, List<Event> existingEvents, ProgressCallback callback) throws AIException{
        try{
            if (callback!=null){
                callback.update("Starting AI event generation...");
                callback.update("Goal: "+goalDescription);
                callback.update("Start date: "+startDate+", Days: "+days);
            }
            if (callback!=null){
                callback.update("Checking AI configuration...");
            }
            ensureConfigured();
            if (callback!=null){
                callback.update("Configuration OK. Building request...");
            }
            String requestBody=buildEventGenerationRequest(goalDescription, startDate, days, existingEvents);
            if (callback!=null){
                callback.update("Request built. Sending to AI API...");
                callback.update("Endpoint: "+endpoint);
                callback.update("Model: "+model);
            }
            String response=sendRequest(requestBody);
            updateUsageStats(estimateTokens(requestBody), estimateTokens(response));
            if (callback!=null){
                callback.update("Response received. Parsing...");
            }
            List<Event> events=parseResponse(response, startDate, days, existingEvents);
            if (callback!=null){
                callback.updateSuccess("Successfully parsed "+events.size()+" events");
                for (Event event:events){
                    if (callback.isCancelled()) break;
                    callback.updateEvent(event);
                }
            }
            return events;
        }
        catch (AIException e){
            if (callback!=null){
                callback.updateError("AI Error: "+e.getMessage());
                callback.updateError("Error type: "+e.getErrorType());
            }
            throw e;
        }
        catch (Exception e){
            if (callback!=null){
                callback.updateError("Unexpected error: "+e.getMessage());
                callback.updateError("Error class: "+e.getClass().getName());
            }
            throw new AIException("Failed to generate events: "+e.getMessage(), AIException.ErrorType.OTHER, e);
        }
    }

    private List<String> fetchModelsViaAPI() throws AIException{
        try{
            String modelsEndpoint=constructModelsEndpoint();
            HttpRequest request=HttpRequest.newBuilder().uri(URI.create(modelsEndpoint)).header("Authorization", "Bearer "+apiKey).GET().build();
            HttpResponse<String> response=httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200){
                throw new AIException("Failed to fetch models: HTTP "+response.statusCode(), AIException.ErrorType.SERVER_ERROR);
            }
            return parseModelsFromResponse(response.body());
        }
        catch (Exception e){
            throw new AIException("Failed to fetch models from API: "+e.getMessage(), AIException.ErrorType.NETWORK_ERROR, e);
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
        String[] lines=response.split("\n");
        for (String line:lines){
            line=line.trim();
            if (line.contains("\"id\"")){
                int start=line.indexOf("\"id\"");
                int colon=line.indexOf(":", start);
                int quote1=line.indexOf("\"", colon+1);
                int quote2=line.indexOf("\"", quote1+1);
                if (quote1!=-1&&quote2!=-1&&quote2>quote1){
                    String modelId=line.substring(quote1+1, quote2);
                    if (!modelId.isEmpty()&&!modelId.startsWith("ft:")){
                        models.add(modelId);
                    }
                }
            }
        }
        return models;
    }

    public void refreshModels() throws AIException{
        supportedModels=fetchModelsViaAPI();
        modelsFetched=true;
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
        """, model, AIJsonParser.escapeJsonString(prompt));
    }

    protected String buildEventGenerationRequest(String goalDescription, LocalDate startDate, int days, List<Event> existingEvents){
        String systemPrompt=promptManager.getSystemPrompt();
        String userPrompt=promptManager.getEventGenerationPrompt(goalDescription, startDate, days, existingEvents);
        return String.format("""
        {
            "model": "%s",
            "messages": [
                {
                    "role": "system",
                    "content": "%s"
                },
                {
                    "role": "user",
                    "content": "%s"
                }
            ],
            "max_tokens": 2000,
            "temperature": 0.7,
            "response_format": { "type": "json_object" }
        }
        """, model, AIJsonParser.escapeJsonString(systemPrompt), AIJsonParser.escapeJsonString(userPrompt));
    }

    @Override
    protected List<Event> parseResponse(String response, LocalDate startDate, int days, List<Event> existingEvents) throws AIException{
        try{
            String content=extractContentFromResponse(response);
            if (content==null||content.trim().isEmpty()){
                throw new AIException("Empty response from AI", AIException.ErrorType.INVALID_RESPONSE);
            }
            return AIJsonParser.parseAIResponse(content);
        }
        catch (AIException e){
            String errorMsg=e.getMessage()+"\nRaw response: "+(response.length()>500?response.substring(0, 500)+"...":response);
            throw new AIException(errorMsg, e.getErrorType(), e);
        }
        catch (Exception e){
            throw new AIException("Failed to parse response: "+e.getMessage(), AIException.ErrorType.INVALID_RESPONSE, e);
        }
    }

    private String extractContentFromResponse(String response){
        try{
            if (response.trim().startsWith("{")){
                int contentStart=response.indexOf("\"content\"");
                if (contentStart!=-1){
                    int colon=response.indexOf(":", contentStart);
                    int quote1=response.indexOf("\"", colon+1);
                    if (quote1!=-1){
                        int quote2=response.indexOf("\"", quote1+1);
                        while (quote2!=-1&&response.charAt(quote2-1)=='\\'){
                            quote2=response.indexOf("\"", quote2+1);
                        }
                        if (quote2!=-1){
                            return response.substring(quote1+1, quote2);
                        }
                    }
                }
                int choicesStart=response.indexOf("\"choices\"");
                if (choicesStart!=-1){
                    int bracketStart=response.indexOf("[", choicesStart);
                    if (bracketStart!=-1){
                        int bracketEnd=findMatchingBracket(response, bracketStart);
                        if (bracketEnd!=-1){
                            String choices=response.substring(bracketStart+1, bracketEnd);
                            String[] choiceItems=splitJsonObjects(choices);
                            for (String choice:choiceItems){
                                String message=extractJsonField(choice, "message");
                                if (message!=null){
                                    String content=extractJsonField(message, "content");
                                    if (content!=null){
                                        return content;
                                    }
                                }
                                String text=extractJsonField(choice, "text");
                                if (text!=null){
                                    return text;
                                }
                            }
                        }
                    }
                }
            }
            return response.trim();
        }
        catch (Exception e){
            return response.trim();
        }
    }

    private String extractJsonField(String json, String fieldName){
        String pattern="\""+fieldName+"\"";
        int pos=json.indexOf(pattern);
        if (pos==-1) return null;
        int colon=json.indexOf(":", pos+pattern.length());
        if (colon==-1) return null;
        int valueStart=colon+1;
        while (valueStart<json.length()&&Character.isWhitespace(json.charAt(valueStart))){
            valueStart++;
        }
        if (valueStart>=json.length()) return null;
        if (json.charAt(valueStart)=='"'){
            int quoteEnd=valueStart+1;
            while (quoteEnd<json.length()){
                if (json.charAt(quoteEnd)=='"'&&json.charAt(quoteEnd-1)!='\\'){
                    break;
                }
                quoteEnd++;
            }
            if (quoteEnd>=json.length()) return null;
            return json.substring(valueStart+1, quoteEnd);
        }
        return null;
    }

    private String[] splitJsonObjects(String jsonArray){
        List<String> objects=new ArrayList<>();
        int start=0;
        int braceCount=0;
        boolean inString=false;
        for (int i=0; i<jsonArray.length(); i++){
            char c=jsonArray.charAt(i);
            if (c=='"'&&(i==0||jsonArray.charAt(i-1)!='\\')){
                inString=!inString;
            }
            if (!inString){
                if (c=='{'){
                    if (braceCount==0){
                        start=i;
                    }
                    braceCount++;
                }
                else if (c=='}'){
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
        for (int i=start; i<json.length(); i++){
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
}