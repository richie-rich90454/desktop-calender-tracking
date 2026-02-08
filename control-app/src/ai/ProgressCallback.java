package ai;

import model.Event;

public interface ProgressCallback {
    void update(String message);
    void updateSuccess(String message);
    void updateWarning(String message);
    void updateError(String message);
    void updateEvent(Event event);
    boolean isCancelled();
}