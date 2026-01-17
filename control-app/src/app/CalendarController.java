package app;
/*
 * Central application controller.
 *
 * Responsibilities:
 * - Act as the single entry point for UI actions
 * - Coordinate between model, state, and storage
 * - Expose safe methods to mutate calendar data
 *
 * Java data types used:
 * - CalendarModel
 * - Event
 * - List<Event>
 * - Optional<Event>
 *
 * Java technologies involved:
 * - MVC-style architecture
 * - Encapsulation
 *
 * Design intent:
 * UI NEVER talks directly to the model or storage.
 * All changes go through this controller.
 */
import model.CalendarModel;
import model.Event;
import service.CalendarValidationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import calendar.CalendarQuery;
public class CalendarController {
    private CalendarModel model;
    private CalendarValidationService validationService;
    private CalendarQuery query;
    public CalendarController(){
        this.model=new CalendarModel();
        this.validationService=new CalendarValidationService();
        this.query=new CalendarQuery(this.model);
    }
    public Optional<Event> createEvent(String title, LocalDate date, LocalTime starTime, LocalTime endTime){
        try{
            Event newEvent=new Event(title, date, starTime, endTime);
            if (validationService.isValid(newEvent, model)){
                model.addEvent(newEvent);
                return Optional.of(newEvent);
            }
            return Optional.empty();
        }
        catch (IllegalArgumentException exception){
            return Optional.empty();
        }
    }
    public boolean createEventUnsafe(Event event){
        try{
            model.addEvent(event);
            return true;
        }
        catch (Exception exception){
            return false;
        }
    }
    public Optional<Event> updateEvent(Event originalEvent, String newTitle, LocalDate newDate, LocalTime newStartTime, LocalTime newEndTime){
        boolean removed=model.removeEvent(originalEvent);
        if (!removed){
            return Optional.empty();
        }
        String titleUse=new String();
        LocalDate dateUse=LocalDate.of(1970, 1, 1);
        LocalTime startTimeUse=LocalTime.of(0, 0, 0);
        LocalTime endTimeUse=LocalTime.of(0, 0, 1);
        if (newTitle!=null){
            titleUse=newTitle;
        }
        else{
            titleUse=originalEvent.getTitle();
        }
        if (newDate!=null){
            dateUse=newDate;
        }
        else{
            dateUse=originalEvent.getDate();
        }
        if (newStartTime!=null){
            startTimeUse=newStartTime;
        }
        else{
            startTimeUse=originalEvent.getStartTime().toLocalTime();
        }
        if (newEndTime!=null){
            endTimeUse=newEndTime;
        }
        else{
            endTimeUse=originalEvent.getEndTime().toLocalTime();
        }
        try{
            Event updatedEvent=new Event(titleUse, dateUse, startTimeUse, endTimeUse);
            if (validationService.isValid(updatedEvent, model)){
                model.addEvent(updatedEvent);
                return Optional.of(updatedEvent);
            }
            else{
                model.addEvent(originalEvent);
                return Optional.empty();
            }
        }
        catch (IllegalArgumentException exception){
            model.addEvent(originalEvent);
            return Optional.empty();
        }
    }
    public boolean deleteEvent(Event event){
        return model.removeEvent(event);
    }
    public void clearAllEvents(){
        model.clearEvents();
    }
    public List<Event> getEventsbyDate(LocalDate date){
        return query.getEventsbyDate(date);
    }
    public List<Event> getallEvents(){
        return query.getallEvents();
    }
    public List<Event> getEventsByDateRange(LocalDate starDate, LocalDate endDate){
        return query.getEventsByDateRange(starDate, endDate);
    }
    public List<Event> getActivevEvents(LocalDateTime dateTime){
        return query.getActiveEvents(dateTime);
    }
    public List<Event> getUpcomingEvents(LocalDateTime dateTime){
        return query.getUpcommingEvents(dateTime);
    }
    public List<Event> getPastEvents(LocalDateTime dateTime){
        return query.getPastEvents(dateTime);
    }
    public List<Event> searchEventsByTitle(String search){
        return query.searchEventsByTitle(search);
    }
    public boolean isValidEvent(Event event){
        return validationService.isValid(event, model);
    }
    public List<String> getOverlappingEventsReport(){
        return validationService.getAllOverlappingEventTitles(model);
    }
    public int getEventCount(){
        return model.getEventCount();
    }
    public boolean isEmpty(){
        return model.isEmpty();
    }
    public List<Event> getSortedEvents(){
        return model.getSortedEvents();
    }
    public List<Event> addMultipleEvents(List<Event> events){
        List<Event> added=new ArrayList<>();
        for (Event event:events){
            if (validationService.isValid(event, model)){
                model.addEvent(event);
                added.add(event);
            }
        }
        return added;
    }
    public int importEventsFromModel(CalendarModel otherModel){
        int num=0;
        List<Event> events=otherModel.getEvents();
        for (Event event:events){
            if (validationService.isValid(event, otherModel)){
                model.addEvent(event);
                num++;
            }
        }
        return num;
    }
    public CalendarModel getModel(){
        return model;
    }
    public CalendarValidationService getValidationService(){
        return validationService;
    }
    public CalendarQuery getQuery(){
        return query;
    }
}