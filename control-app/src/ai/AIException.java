package ai;
/*
 * Custom exception for AI service operations.
 *
 * Responsibilities:
 * - Provide typed error handling for AI client failures
 * - Categorize errors to enable targeted recovery strategies
 * - Preserve causal chain for debugging and logging
 *
 * Java data types used:
 * - Enum (ErrorType) - Categorized error classification
 * - Throwable - Wrapped root causes
 *
 * Java technologies involved:
 * - Custom exception hierarchy (extends Exception)
 * - Enum pattern for type-safe error categories
 * - Chained exception constructors
 *
 * Error classifications:
 * NETWORK_ERROR    - Connection failures, timeouts, unreachable endpoints
 * RATE_LIMIT_ERROR - API quota exceeded, too many requests
 * AUTHENTICATION_ERROR - Invalid API keys, unauthorized access
 * SERVER_ERROR     - AI provider server issues (5xx responses)
 * INVALID_RESPONSE - Malformed or unexpected API responses
 * OTHER            - Uncategorized or unexpected errors
 *
 * Design intent:
 * Enables callers to implement granular error handling based on error type.
 * For example: retry on NETWORK_ERROR, wait on RATE_LIMIT_ERROR, or alert user on AUTHENTICATION_ERROR.
 * The exception preserves both human-readable messages and programmatically useful error types.
 */
public class AIException extends Exception{
    public enum ErrorType{
        NETWORK_ERROR,
        RATE_LIMIT_ERROR,
        AUTHENTICATION_ERROR,
        SERVER_ERROR,
        INVALID_RESPONSE,
        OTHER
    }
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
}