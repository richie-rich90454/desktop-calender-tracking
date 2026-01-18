package state;
import model.CalendarModel;
import model.Event;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.time.LocalDate;
import java.util.List;
/**
 * Global application state holder.
 *
 * Responsibilities:
 * - Hold current CalendarModel
 * - Track application lifecycle state
 * - Notify listeners on changes
 *
 * Java data types used:
 * - CalendarModel
 * - PropertyChangeSupport
 *
 * Java technologies involved:
 * - Observer pattern
 *
 * Design intent:
 * Single source of truth for the running app.
 */
public class AppState {
    public static String PROPERTY_MODEL="calendarModel";
    public static String PROPERTY_SELECTED_DATE="selectedDate";
    public static String PROPERTY_SELECTED_EVENT="selectedEvent";
    public static String PROPERTY_VIEW_MODE="viewMode";
    public static String PROPERTY_FILTER_TEXT="filterText";
    public static String PROPERTY_EVENTS_CHANGED="eventsChanged";
    public enum ViewMode{
        DAY_VIEW,
        WEEK_VIEW,
        MONTH_VIEW,
        AGENDA_VIEW
    }
    private CalendarModel calendarModel;
    private LocalDate selectedDate;
    private Event selectedEvent;
    private ViewMode currentViewMode;
    private String filterText;
    private boolean isUnsaved;
    private PropertyChangeSupport propertyChangeSupport;
    public AppState(){
        this.calendarModel=new CalendarModel();
        this.selectedDate=LocalDate.now();
        this.selectedEvent=null;
        this.currentViewMode=ViewMode.MONTH_VIEW;
        this.filterText="";
        this.isUnsaved=false;
        this.propertyChangeSupport=new PropertyChangeSupport(this);
    }
    public AppState(CalendarModel model){
        this.calendarModel=model;
        this.selectedDate=LocalDate.now();
        this.selectedEvent=null;
        this.currentViewMode=ViewMode.MONTH_VIEW;
        this.filterText="";
        this.isUnsaved=false;
        this.propertyChangeSupport=new PropertyChangeSupport(this);
    }
    public CalendarModel getCalendarModel(){
        return calendarModel;
    }
    public void setCalendarModel(CalendarModel newModel){
        CalendarModel oldModel=this.calendarModel;
        this.calendarModel=newModel;
        this.isUnsaved=true;
        propertyChangeSupport.firePropertyChange(PROPERTY_MODEL, oldModel, newModel);
    }
    public void syncWithController(app.CalendarController controller){
        CalendarModel controllerModel=controller.getModel();
        if (controllerModel!=this.calendarModel){
            CalendarModel oldModel=this.calendarModel;
            this.calendarModel=controllerModel;
            this.isUnsaved=true;
            propertyChangeSupport.firePropertyChange(PROPERTY_MODEL, oldModel, controllerModel);
        }
    }
    public LocalDate getSelectedDate(){
        return selectedDate;
    }
    public void setSelectedDate(LocalDate date){
        LocalDate oldDate=this.selectedDate;
        this.selectedDate=date;
        propertyChangeSupport.firePropertyChange(PROPERTY_SELECTED_DATE, oldDate, date);
    }
    public model.Event getSelectedEvent(){
        return selectedEvent;
    }
    public void setSelectedEvent(model.Event event){
        model.Event oldEvent=this.selectedEvent;
        this.selectedEvent=event;
        propertyChangeSupport.firePropertyChange(PROPERTY_SELECTED_EVENT, oldEvent, event);
    }
    public void clearSelectedEvent(){
        setSelectedEvent(null);
    }
    public ViewMode getCurrentViewMode(){
        return currentViewMode;
    }
    public void setCurrentViewMode(ViewMode viewMode){
        ViewMode oldMode=this.currentViewMode;
        this.currentViewMode=viewMode;
        propertyChangeSupport.firePropertyChange(PROPERTY_VIEW_MODE, oldMode, viewMode);
    }
    public void switchToDayView(){
        setCurrentViewMode(ViewMode.DAY_VIEW);
    }
    public void switchToWeekView(){
        setCurrentViewMode(ViewMode.WEEK_VIEW);
    }
    public void switchToMonthView(){
        setCurrentViewMode(ViewMode.MONTH_VIEW);
    }
    public void switchToAgendaView(){
        setCurrentViewMode(ViewMode.AGENDA_VIEW);
    }
    public void cycleViewMode(){
        ViewMode[] modes=ViewMode.values();
        int currentIndex=currentViewMode.ordinal();
        int nextIndex=(currentIndex+1)%modes.length;
        setCurrentViewMode(modes[nextIndex]);
    }
    public String getFilterText(){
        return filterText;
    }
    public void setFilterText(String text){
        String oldText=this.filterText;
        this.filterText=text;
        propertyChangeSupport.firePropertyChange(PROPERTY_FILTER_TEXT, oldText, text);
    }
    public void clearFilter(){
        setFilterText("");
    }
    public boolean isUnsaved(){
        return isUnsaved;
    }
    public void markAsDirty(){
        boolean oldDirty=this.isUnsaved;
        this.isUnsaved=true;
        if (!oldDirty){
            propertyChangeSupport.firePropertyChange("dirty", oldDirty, true);
        }
    }
    public void markAsClean(){
        boolean oldDirty=this.isUnsaved;
        this.isUnsaved=false;
        if (oldDirty){
            propertyChangeSupport.firePropertyChange("dirty", oldDirty, false);
        }
    }
    public void goToToday(){
        setSelectedDate(LocalDate.now());
    }
    public void goToDate(LocalDate date){
        setSelectedDate(date);
    }
    public void navigateToPreviousDay(){
        setSelectedDate(selectedDate.minusDays(1));
    }
    public void navigateToNextDay(){
        setSelectedDate(selectedDate.plusDays(1));
    }
    public void navigateToPreviousWeek(){
        setSelectedDate(selectedDate.minusWeeks(1));
    }
    public void navigateToNextWeek(){
        setSelectedDate(selectedDate.plusWeeks(1));
    }
    public void navigateToPreviousMonth(){
        setSelectedDate(selectedDate.minusMonths(1));
    }
    public void navigateToNextMonth(){
        setSelectedDate(selectedDate.plusMonths(1));
    }
    public List<model.Event> getEventsForSelectedDate(){
        return calendarModel.getEventsByDate(selectedDate);
    }
    public List<model.Event> getEventsForToday(){
        return calendarModel.getEventsByDate(LocalDate.now());
    }
    public boolean isEventOnSelectedDate(model.Event event){
        return event.getDate().equals(selectedDate);
    }
    public List<model.Event> getAllEventsSorted(){
        return calendarModel.getSortedEvents();
    }
    public int getEventCount(){
        return calendarModel.getEventCount();
    }
    public boolean isEmpty(){
        return calendarModel.isEmpty();
    }
    public void notifyEventsChanged(){
        markAsDirty();
        propertyChangeSupport.firePropertyChange(PROPERTY_EVENTS_CHANGED, null, calendarModel);
    }
    public void notifyEventModified(model.Event event){
        markAsDirty();
        propertyChangeSupport.firePropertyChange("eventModified", null, event);
    }
    public void notifyEventAdded(model.Event event){
        markAsDirty();
        propertyChangeSupport.firePropertyChange("eventAdded", null, event);
    }
    public void notifyEventDeleted(model.Event event){
        markAsDirty();
        propertyChangeSupport.firePropertyChange("eventDeleted", null, event);
    }
    public void addPropertyChangeListener(PropertyChangeListener listener){
        propertyChangeSupport.addPropertyChangeListener(listener);
    }
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener){
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }
    public void removePropertyChangeListener(PropertyChangeListener listener){
        propertyChangeSupport.removePropertyChangeListener(listener);
    }
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener){
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }
    public PropertyChangeListener[] getPropertyChangeListeners(){
        return propertyChangeSupport.getPropertyChangeListeners();
    }
    public void reset(){
        CalendarModel oldModel=this.calendarModel;
        LocalDate oldDate=this.selectedDate;
        ViewMode oldViewMode=this.currentViewMode;
        String oldFilterText=this.filterText;
        this.calendarModel=new CalendarModel();
        this.selectedDate=LocalDate.now();
        this.selectedEvent=null;
        this.currentViewMode=ViewMode.MONTH_VIEW;
        this.filterText="";
        this.isUnsaved=false;
        propertyChangeSupport.firePropertyChange(PROPERTY_MODEL, oldModel, calendarModel);
        propertyChangeSupport.firePropertyChange(PROPERTY_SELECTED_DATE, oldDate, selectedDate);
        propertyChangeSupport.firePropertyChange(PROPERTY_VIEW_MODE, oldViewMode, currentViewMode);
        propertyChangeSupport.firePropertyChange(PROPERTY_FILTER_TEXT, oldFilterText, filterText);
        propertyChangeSupport.firePropertyChange("reset", false, true);
    }
    public StateSnapshot createSnapshot(){
        return new StateSnapshot(
            this.calendarModel,
            this.selectedDate,
            this.selectedEvent,
            this.currentViewMode,
            this.filterText,
            this.isUnsaved
        );
    }
    public void restoreFromSnapshot(StateSnapshot snapshot){
        if (snapshot!=null){
            this.calendarModel=snapshot.getModel();
            this.selectedDate=snapshot.getSelectedDate();
            this.selectedEvent=snapshot.getSelectedEvent();
            this.currentViewMode=snapshot.getViewMode();
            this.filterText=snapshot.getFilterText();
            this.isUnsaved=snapshot.isUnsaved();
            propertyChangeSupport.firePropertyChange("stateRestored", false, true);
        }
    }
    @Override
    public String toString(){
        return String.format(
            "AppState{date=%s, view=%s, events=%d, dirty=%s}",
            selectedDate,
            currentViewMode,
            calendarModel.getEventCount(),
            isUnsaved
        );
    }
    public String getStateReport(){
        return String.format("Current Date: %s\n"+"View Mode: %s\n"+"Filter Text: \"%s\"\n"+"Total Events: %d\n"+"Events Today: %d\n"+"Selected Event: %s\n"+"Has Unsaved Changes: %s",
            selectedDate,
            currentViewMode,
            filterText,
            calendarModel.getEventCount(),
            calendarModel.getEventsByDate(LocalDate.now()).size(),
            selectedEvent!=null?selectedEvent.getTitle():"None",
            isUnsaved?"Yes":"No"
        );
    }
    public static class StateSnapshot{
        private CalendarModel model;
        private LocalDate selectedDate;
        private model.Event selectedEvent;
        private ViewMode viewMode;
        private String filterText;
        private boolean dirty;
        private StateSnapshot(CalendarModel model, LocalDate selectedDate, model.Event selectedEvent, ViewMode viewMode, String filterText, boolean dirty){
            this.model=model;
            this.selectedDate=selectedDate;
            this.selectedEvent=selectedEvent;
            this.viewMode=viewMode;
            this.filterText=filterText;
            this.dirty=dirty;
        }
        public CalendarModel getModel(){
            return model;
        }
        public LocalDate getSelectedDate(){
            return selectedDate;
        }
        public model.Event getSelectedEvent(){
            return selectedEvent;
        }
        public ViewMode getViewMode(){
            return viewMode;
        }
        public String getFilterText(){
            return filterText;
        }
        public boolean isUnsaved(){
            return dirty;
        }
    }
}