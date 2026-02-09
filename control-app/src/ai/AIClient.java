package ai;

import model.Event;
import java.time.LocalDate;
import java.util.List;
/*
 * AI service client interface for event generation.
 *
 * Responsibilities:
 * - Define contract for AI-powered event generation from goal descriptions
 * - Manage AI provider connections and configurations
 * - Track API usage statistics for cost monitoring
 * - Support multiple AI models and providers through unified interface
 *
 * Java data types used:
 * - List<Event> - Generated and existing events
 * - LocalDate - Temporal boundaries for event generation
 * - String - Goal descriptions, API keys, endpoints, model identifiers
 *
 * Java technologies involved:
 * - Interface-based design for provider abstraction
 * - Nested static class (UsageStats) for encapsulation
 * - Exception handling with custom AIException
 * - Builder-like configuration pattern (setters)
 *
 * Design intent:
 * Clients implement this interface to support different AI providers (OpenAI, Claude, etc.).
 * The interface balances generation capabilities with operational concerns like cost tracking,
 * connection testing, and model management. UsageStats provides transparency into API consumption
 * and costs, while the configuration methods allow runtime adaptation to different AI backends.
 */
public interface AIClient{
    List<Event> generateEvents(String goalDescription, LocalDate startDate, int days, List<Event> existingEvents) throws AIException;
    List<Event> generateEvents(String goalDescription, LocalDate startDate, int days, List<Event> existingEvents, ProgressCallback callback) throws AIException;
    boolean testConnection() throws AIException;
    void setApiKey(String apiKey);
    void setEndpoint(String endpoint);
    void setModel(String model);
    String getCurrentModel();
    String getProviderName();
    boolean isOfflineCapable();
    int getMaxTokensPerRequest();
    List<String> getSupportedModels();
    UsageStats getLastUsageStats();
    UsageStats getTotalUsageStats();
    void resetUsageStats();
    void shutdown();
    class UsageStats{
        private int requestCount;
        private int totalPromptTokens;
        private int totalCompletionTokens;
        public UsageStats(){
            reset();
        }
        public void reset(){
            requestCount=0;
            totalPromptTokens=0;
            totalCompletionTokens=0;
        }
        public void addRequest(int promptTokens, int completionTokens){
            requestCount++;
            totalPromptTokens+=promptTokens;
            totalCompletionTokens+=completionTokens;
        }
        public int getRequestCount(){
            return requestCount;
        }
        public int getPromptTokens(){
            return totalPromptTokens;
        }
        public int getCompletionTokens(){
            return totalCompletionTokens;
        }
        public int getTotalTokens(){
            return totalPromptTokens+totalCompletionTokens;
        }
        @Override
        public String toString(){
            return String.format(
                "Requests: %d, Prompt: %d, Completion: %d, Total: %d",
                requestCount, totalPromptTokens, totalCompletionTokens, 
                getTotalTokens()
            );
        }
    }
}