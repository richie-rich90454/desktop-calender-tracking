package ai;

import model.Event;

/**
 * Callback interface for progress updates during asynchronous operations.
 * Provides methods for reporting status at different severity levels and
 * supports cancellation checks for long-running tasks.
 */
public interface ProgressCallback {
    void update(String message);
    void updateSuccess(String message);
    void updateWarning(String message);
    void updateError(String message);
    void updateEvent(Event event);
    boolean isCancelled();
}