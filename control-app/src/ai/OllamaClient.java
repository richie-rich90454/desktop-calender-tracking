package ai;

import model.Event;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

/*
 * Ollama client for local AI models
 *
 * Responsibilities:
 * - Communicate with local Ollama API
 * - Use AIPromptManager for structured prompts
 * - Use AIJsonParser for robust JSON parsing
 * - Handle Ollama-specific API format
 *
 * Design intent:
 * Supports local Ollama models without API key requirement.
 * Uses different API endpoint format than OpenAI.
 */

public class OllamaClient extends BaseAIClient{
    private static final String DEFAULT_ENDPOINT="http://localhost:11434/api/chat";
    private static final String DEFAULT_MODEL="llama3.2";
    private final AIPromptManager promptManager;
    private List<String> supportedModels=new ArrayList<>();
    private boolean modelsFetched=false;
    public OllamaClient(){
        super();
        this.endpoint=DEFAULT_ENDPOINT;
        this.model=DEFAULT_MODEL;
        this.promptManager=new AIPromptManager();
    }
    public OllamaClient(String endpoint){
        this();
        if (endpoint!=null&&!endpoint.isEmpty()){
            this.endpoint=endpoint;
        }
    }
    @Override
    public String getProviderName(){
        return "Ollama (Local)";
    }
    @Override
    public boolean isOfflineCapable(){
        return true;
    }
    @Override
    public double getCostPerThousandTokens(){
        return 0.0;
    }
    @Override
    public int getMaxTokensPerRequest(){
        return 8192;
    }
    @Override
    public List<String> getSupportedModels(){
        if (!modelsFetched){
            try{
                supportedModels=fetchModelsViaAPI();
                modelsFetched=true;
            }
            catch (Exception e){
                supportedModels=getDefaultModels();
                modelsFetched=true;
            }
        }
        return new ArrayList<>(supportedModels);
    }
    private List<String> fetchModelsViaAPI() throws AIException{
        try{
            String modelsEndpoint="http://localhost:11434/api/tags";
            HttpRequest request=HttpRequest.newBuilder().uri(URI.create(modelsEndpoint)).GET().build();
            String response=httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
            return parseModelsFromResponse(response);
        }
        catch (Exception e){
            throw new AIException("Failed to fetch models from Ollama API: "+e.getMessage(),AIException.ErrorType.NETWORK_ERROR, e);
        }
    }
    private List<String> parseModelsFromResponse(String response){
        List<String> models=new ArrayList<>();
        try{
            int modelsStart=response.indexOf("\"models\"");
            if (modelsStart!=-1){
                int bracketStart=response.indexOf("[", modelsStart);
                if (bracketStart!=-1){
                    int bracketEnd=findMatchingBracket(response, bracketStart);
                    if (bracketEnd!=-1){
                        String modelsArray=response.substring(bracketStart+1, bracketEnd);
                        String[] modelItems=splitJsonObjects(modelsArray);
                        for (String item:modelItems){
                            String name=extractJsonField(item, "name");
                            if (name!=null&&!name.isEmpty()){
                                models.add(name);
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e){
            System.err.println("Error parsing Ollama models: "+e.getMessage());
        }
        if (models.isEmpty()){
            return getDefaultModels();
        }
        return models;
    }
    private List<String> getDefaultModels(){
        List<String> defaultModels=new ArrayList<>();
        defaultModels.add("llama3.2");
        defaultModels.add("llama3.1");
        defaultModels.add("llama3");
        defaultModels.add("mistral");
        defaultModels.add("codellama");
        defaultModels.add("phi");
        return defaultModels;
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
            throw new AIException("Failed to generate events: "+e.getMessage(),AIException.ErrorType.OTHER, e);
        }
    }
    @Override
    protected HttpRequest buildHttpRequest(String requestBody){
        return HttpRequest.newBuilder().uri(URI.create(endpoint)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
    }
    @Override
    protected String buildTestRequest(String prompt){
        return String.format("""
        {
            "model": "%s",
            "messages": [
                {
                    "role": "user",
                    "content": "%s"
                }
            ],
            "stream": false
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
            "stream": false,
            "format": "json",
            "options": {
                "temperature": 0.7,
                "num_predict": 2000
            }
        }
        """, model,AIJsonParser.escapeJsonString(systemPrompt),AIJsonParser.escapeJsonString(userPrompt));
    }
    @Override
    protected List<Event> parseResponse(String response, LocalDate startDate, int days, List<Event> existingEvents) throws AIException{
        try{
            String content=extractContentFromResponse(response);
            if (content==null||content.trim().isEmpty()){
                throw new AIException("Empty response from Ollama", AIException.ErrorType.INVALID_RESPONSE);
            }
            return AIJsonParser.parseAIResponse(content);
        }
        catch (AIException e){
            String errorMsg=e.getMessage()+"\nRaw response: "+(response.length() >500?response.substring(0, 500)+"...":response);
            throw new AIException(errorMsg, e.getErrorType(), e);
        }
        catch (Exception e){
            throw new AIException("Failed to parse response: "+e.getMessage(),AIException.ErrorType.INVALID_RESPONSE, e);
        }
    }
    private String extractContentFromResponse(String response){
        try{
            if (response.trim().startsWith("{")){
                int messageStart=response.indexOf("\"message\"");
                if (messageStart!=-1){
                    int braceStart=response.indexOf("{", messageStart);
                    if (braceStart!=-1){
                        int braceEnd=findMatchingBracket(response, braceStart);
                        if (braceEnd!=-1){
                            String message=response.substring(braceStart, braceEnd+1);
                            String content=extractJsonField(message, "content");
                            if (content!=null){
                                return content;
                            }
                        }
                    }
                }
                String content=extractJsonField(response, "content");
                if (content!=null){
                    return content;
                }
                String responseField=extractJsonField(response, "response");
                if (responseField!=null){
                    return responseField;
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
        while (valueStart < json.length()&&Character.isWhitespace(json.charAt(valueStart))){
            valueStart++;
        }
        if (valueStart >= json.length()) return null;
        if (json.charAt(valueStart)== '"'){
            int quoteEnd=valueStart+1;
            while (quoteEnd < json.length()){
                if (json.charAt(quoteEnd)== '"'&&json.charAt(quoteEnd - 1)!='\\'){
                    break;
                }
                quoteEnd++;
            }
            if (quoteEnd >= json.length()) return null;
            return json.substring(valueStart+1, quoteEnd);
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
            if (c== '"'&&(i==0||jsonArray.charAt(i - 1)!='\\')){
                inString=!inString;
            }
            if (!inString){
                if (c== '{'){
                    if (braceCount==0){
                        start=i;
                    }
                    braceCount++;
                }
                else if (c== '}'){
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
            if (c== '"'&&(i==0||json.charAt(i - 1)!='\\')){
                inString=!inString;
            }
            if (!inString){
                if (c== '['||c== '{'){
                    bracketCount++;
                }
                else if (c== ']'||c== '}'){
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
        try{
            String requestBody=buildTestRequest(promptManager.getTestPrompt());
            String response=sendRequest(requestBody);
            if (response==null||response.trim().isEmpty()){
                throw new AIException("Empty response from Ollama", AIException.ErrorType.NETWORK_ERROR);
            }
            if (response.contains("\"error\"")){
                String error=extractJsonField(response, "error");
                throw new AIException("Ollama error: "+(error!=null?error:"Unknown error"),AIException.ErrorType.SERVER_ERROR);
            }
            return true;
        }
        catch (AIException e){
            throw e;
        }
        catch (Exception e){
            throw new AIException("Failed to connect to Ollama: "+e.getMessage(),AIException.ErrorType.NETWORK_ERROR, e);
        }
    }
}