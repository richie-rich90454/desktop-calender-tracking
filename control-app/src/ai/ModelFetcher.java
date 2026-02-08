package ai;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.*;

/*
 * Fetches available models from AI provider APIs dynamically.
 *
 * Responsibilities:
 * - Fetch model lists from various AI provider endpoints
 * - Parse model information from API responses
 * - Cache model lists to reduce API calls
 * - Handle provider-specific response formats
 *
 * Java data types used:
 * - HttpClient for API requests
 * - HttpRequest/HttpResponse for HTTP communication
 * - List<String> for model names
 * - Map for caching results
 *
 * Java technologies involved:
 * - java.net.http package for HTTP/2
 * - JSON parsing with string manipulation
 * - Caching with expiration
 *
 * Design intent:
 * Models are fetched dynamically from provider APIs.
 * Results are cached to avoid excessive API calls.
 * Each provider has specific endpoint and parsing logic.
 */
public class ModelFetcher {
    private static final HttpClient HTTP_CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).version(HttpClient.Version.HTTP_2).build();
    private static final Map<String, CacheEntry> modelCache=new HashMap<>();
    private static final long CACHE_DURATION_MS=5*60*1000;
    public static List<String> fetchModels(String provider, String apiKey, String customEndpoint) throws AIException{
        String cacheKey=provider+":"+apiKey+":"+customEndpoint;
        CacheEntry cached=modelCache.get(cacheKey);
        if (cached!=null&&!cached.isExpired()){
            return cached.models;
        }
        try{
            List<String> models;
            switch (provider.toLowerCase()){
                case "openai":
                    models=fetchOpenAIModels(apiKey, customEndpoint);
                    break;
                case "deepseek":
                    models=fetchDeepSeekModels(apiKey, customEndpoint);
                    break;
                case "openrouter":
                    models=fetchOpenRouterModels(apiKey, customEndpoint);
                    break;
                case "ollama":
                    models=fetchOllamaModels(customEndpoint);
                    break;
                default:
                    throw new AIException("Unknown provider: "+provider, AIException.ErrorType.OTHER);
            }
            modelCache.put(cacheKey, new CacheEntry(models));
            return models;

        }
        catch (AIException e){
            throw e;
        }
        catch (Exception e){
            throw new AIException("Failed to fetch models: "+e.getMessage(), AIException.ErrorType.NETWORK_ERROR, e);
        }
    }
    private static List<String> fetchOpenAIModels(String apiKey, String customEndpoint)
            throws Exception{
        String endpoint=customEndpoint!=null&&!customEndpoint.isEmpty()?customEndpoint.replace("/chat/completions", "/models"):"https://api.openai.com/v1/models";
        String response=sendModelRequest(endpoint, apiKey);
        return parseOpenAIResponse(response);
    }
    private static List<String> fetchDeepSeekModels(String apiKey, String customEndpoint) throws Exception{
        String endpoint=customEndpoint!=null&&!customEndpoint.isEmpty()?customEndpoint.replace("/chat/completions", "/models"):"https://api.deepseek.com/v1/models";
        String response=sendModelRequest(endpoint, apiKey);
        return parseOpenAIResponse(response);
    }
    private static List<String> fetchOpenRouterModels(String apiKey, String customEndpoint) throws Exception{
        String endpoint=customEndpoint!=null&&!customEndpoint.isEmpty()?customEndpoint:"https://openrouter.ai/api/v1/models";
        String response=sendModelRequest(endpoint, apiKey);
        return parseOpenRouterResponse(response);
    }
    private static List<String> fetchOllamaModels(String customEndpoint) throws Exception{
        String endpoint=customEndpoint!=null&&!customEndpoint.isEmpty()?customEndpoint:"http://localhost:11434/api/tags";
        String response=sendModelRequest(endpoint, null);
        return parseOllamaResponse(response);
    }
    private static String sendModelRequest(String endpoint, String apiKey) throws Exception{
        HttpRequest.Builder requestBuilder=HttpRequest.newBuilder().uri(URI.create(endpoint)).timeout(Duration.ofSeconds(15));
        if (apiKey!=null&&!apiKey.isEmpty()){
            requestBuilder.header("Authorization", "Bearer "+apiKey);
        }
        HttpRequest request=requestBuilder.GET().build();
        HttpResponse<String> response=HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()!=200){
            throw new AIException("Failed to fetch models: HTTP "+response.statusCode(), AIException.ErrorType.SERVER_ERROR);
        }
        return response.body();
    }
    private static List<String> parseOpenAIResponse(String json){
        List<String> models=new ArrayList<>();
        try{
            // Extract data array
            List<String> dataItems=extractJsonArray(json, "data");
            if (dataItems.isEmpty()){
                // Try direct parsing
                dataItems=extractJsonArray(json, "");
            }

            for (String item:dataItems){
                String id=extractJsonValue(item, "id");
                if (id!=null&&!id.isEmpty()){
                    models.add(id);
                }
            }

            // Sort models alphabetically
            Collections.sort(models);

        }
        catch (Exception e){
            // Don't fallback to hardcoded models - throw exception
            throw new RuntimeException("Failed to parse OpenAI response: "+e.getMessage(), e);
        }

        return models;
    }

    /**
     * Parses OpenRouter model response.
     */
    private static List<String> parseOpenRouterResponse(String json){
        List<String> models=new ArrayList<>();

        try{
            List<String> dataItems=extractJsonArray(json, "data");

            for (String item:dataItems){
                String id=extractJsonValue(item, "id");
                if (id!=null&&!id.isEmpty()){
                    models.add(id);
                }
            }

            Collections.sort(models);

        }
        catch (Exception e){
            throw new RuntimeException("Failed to parse OpenRouter response: "+e.getMessage(), e);
        }

        return models;
    }

    /**
     * Parses Ollama model response.
     */
    private static List<String> parseOllamaResponse(String json){
        List<String> models=new ArrayList<>();

        try{
            List<String> modelItems=extractJsonArray(json, "models");

            for (String item:modelItems){
                String name=extractJsonValue(item, "name");
                if (name!=null&&!name.isEmpty()){
                    models.add(name);
                }
            }

            Collections.sort(models);

        }
        catch (Exception e){
            throw new RuntimeException("Failed to parse Ollama response: "+e.getMessage(), e);
        }

        return models;
    }

    /**
     * Clears the model cache.
     */
    public static void clearCache(){
        modelCache.clear();
    }

    /**
     * Clears cache for a specific provider.
     */
    public static void clearCache(String provider, String apiKey, String customEndpoint){
        String cacheKey=provider+":"+apiKey+":"+customEndpoint;
        modelCache.remove(cacheKey);
    }

    // Helper methods for JSON parsing (similar to BaseAIClient)

    private static String extractJsonValue(String json, String key){
        String searchKey="\""+key+"\":";
        int startIndex=json.indexOf(searchKey);
        if (startIndex == -1)
            return null;

        startIndex += searchKey.length();
        int endIndex=json.indexOf(",", startIndex);
        if (endIndex == -1)
            endIndex=json.indexOf("}", startIndex);
        if (endIndex == -1)
            return null;

        String value=json.substring(startIndex, endIndex).trim();

        if (value.startsWith("\"")&&value.endsWith("\"")){
            value=value.substring(1, value.length() - 1);
        }

        return value;
    }

    private static List<String> extractJsonArray(String json, String key){
        List<String> result=new ArrayList<>();

        String searchKey=key.isEmpty() ?"[":"\""+key+"\":";
        int startIndex=json.indexOf(searchKey);
        if (startIndex == -1)
            return result;

        if (!key.isEmpty()){
            startIndex += searchKey.length();
            startIndex=json.indexOf("[", startIndex);
            if (startIndex == -1)
                return result;
        }

        int bracketCount=1;
        int currentIndex=startIndex+1;

        while (currentIndex < json.length()&&bracketCount > 0){
            char c=json.charAt(currentIndex);
            if (c == '[')
                bracketCount++;
            else if (c == ']')
                bracketCount--;
            currentIndex++;
        }

        if (bracketCount!=0)
            return result;

        String arrayContent=json.substring(startIndex+1, currentIndex - 1).trim();
        if (arrayContent.isEmpty())
            return result;

        StringBuilder currentItem=new StringBuilder();
        int nestedBraces=0;
        int nestedBrackets=0;

        for (int i=0; i < arrayContent.length(); i++){
            char c=arrayContent.charAt(i);

            if (c == '{')
                nestedBraces++;
            else if (c == '}')
                nestedBraces--;
            else if (c == '[')
                nestedBrackets++;
            else if (c == ']')
                nestedBrackets--;

            if (c == ','&&nestedBraces == 0&&nestedBrackets == 0){
                String item=currentItem.toString().trim();
                if (!item.isEmpty()){
                    result.add(item);
                }
                currentItem=new StringBuilder();
            } else{
                currentItem.append(c);
            }
        }

        String lastItem=currentItem.toString().trim();
        if (!lastItem.isEmpty()){
            result.add(lastItem);
        }

        return result;
    }

    /**
     * Cache entry with expiration.
     */
    private static class CacheEntry{
        final List<String> models;
        final long timestamp;

        CacheEntry(List<String> models){
            this.models=new ArrayList<>(models);
            this.timestamp=System.currentTimeMillis();
        }

        boolean isExpired(){
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS;
        }
    }
}