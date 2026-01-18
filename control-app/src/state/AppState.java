package state;
import java.beans.PropertyChangeSupport;
import java.time.LocalDate;

import app.CalendarController;
/*
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
import model.CalendarModel;
import model.Event;
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
        this.propertyChangeSupport=new PropertyChangeSupport(model);
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
    public void syncWithController(CalendarController controller){
        CalendarModel controllerModel=controller.getModel();
        if (!controllerModel.equals(this.calendarModel)){
            CalendarModel oldModel=this.calendarModel;
            this.calendarModel=controllerModel;
            this.isUnsaved=true;
            propertyChangeSupport.firePropertyChange(PROPERTY_EVENTS_CHANGED, oldModel, controllerModel);
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
    
}