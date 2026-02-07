package ai;

import model.Event;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
*Interface for AI clients that generate calendar events.
*Supports multiple AI providers with different API formats.
 */
public interface AIClient{
    /**
    *Generates calendar events based on a user's goal description.
    *
    *@param goalDescription User's goal or plan description
    *@param startDate Starting date for event generation
    *@param days Number of days to generate events for
    *@param existingEvents Existing events to avoid conflicts with
    *@return List of generated events
    *@throws AIException if event generation fails
     */
    List<Event> generateEvents(String goalDescription, LocalDate startDate, int days, List<Event> existingEvents) throws AIException;
    /**
    *Tests the connection to the AI API.
    *
    *@return true if connection is successful
    *@throws AIException if connection test fails
     */
    boolean testConnection() throws AIException;
    String getProviderName();
    boolean isOfflineCapable();
    double getCostPerThousandTokens();
    int getMaxTokensPerRequest();
    List<String> getSupportedModels();
    void setModel(String model);
    String getCurrentModel();
    void setApiKey(String apiKey);
    void setEndpoint(String endpoint);
    UsageStats getLastUsageStats();
    void resetUsageStats();
    UsageStats getTotalUsageStats();
    class AIException extends Exception{
        private final ErrorType errorType;
        public AIException(String message, ErrorType errorType){
            super(message);
            this.errorType=errorType;
        }
        public AIException(String message, ErrorType errorType, Throwable cause){
            super(message, cause);
            this.errorType=errorType;
        }
        public ErrorType getErrorType(){
            return errorType;
        }
        public enum ErrorType{
            NETWORK_ERROR,
            AUTHENTICATION_ERROR,
            RATE_LIMIT_ERROR,
            INVALID_RESPONSE,
            QUOTA_EXCEEDED,
            SERVER_ERROR,
            TIMEOUT,
            OTHER
        }
    }
    class UsageStats{
        private int requestCount;
        private int totalTokens;
        private int promptTokens;
        private int completionTokens;
        private double estimatedCost;
        private long lastRequestTime;
        public UsageStats(){
            this.requestCount=0;
            this.totalTokens=0;
            this.promptTokens=0;
            this.completionTokens=0;
            this.estimatedCost=0.0;
            this.lastRequestTime=System.currentTimeMillis();
        }
        public void addRequest(int promptTokens, int completionTokens, double costPerThousandTokens){
            this.requestCount++;
            this.promptTokens+=promptTokens;
            this.completionTokens+=completionTokens;
            this.totalTokens=this.promptTokens+this.completionTokens;
            this.estimatedCost+=(totalTokens/1000.0)*costPerThousandTokens;
            this.lastRequestTime=System.currentTimeMillis();
        }
        public int getRequestCount(){
            return requestCount;
        }
        public int getTotalTokens(){
            return totalTokens;
        }
        public int getPromptTokens(){
            return promptTokens;
        }
        public int getCompletionTokens(){
            return completionTokens;
        }
        public double getEstimatedCost(){
            return estimatedCost;
        }
        public long getLastRequestTime(){
            return lastRequestTime;
        }
        public void reset(){
            this.requestCount=0;
            this.totalTokens=0;
            this.promptTokens=0;
            this.completionTokens=0;
            this.estimatedCost=0.0;
            this.lastRequestTime=System.currentTimeMillis();
        }
        @Override
        public String toString(){
            return String.format(
                "Requests: %d, Tokens: %d (Prompt: %d, Completion: %d), Estimated Cost: $%.4f",
                requestCount, totalTokens, promptTokens, completionTokens, estimatedCost
            );
        }
    }
}