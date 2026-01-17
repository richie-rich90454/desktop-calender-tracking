package calendar;
/*
 * Calendar query utilities.
 *
 * Responsibilities:
 * - Retrieve events by date
 * - Retrieve events by time range
 * - Retrieve upcoming or active events
 *
 * Java data types used:
 * - List<Event>
 * - Predicate<Event>
 * - LocalDate
 * - LocalDateTime
 *
 * Java technologies involved:
 * - Java Streams
 * - Functional interfaces
 *
 * Design intent:
 * Query logic should be reusable and declarative.
 */
import model.Event;
import model.CalendarModel;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
public class CalendarQuery {
    private CalendarModel model;
    public CalendarQuery(CalendarModel calenderModel){
        this.model=calenderModel;
    }
    public List<Event> getEventsbyDate(LocalDate date){
        return model.getEventsByDate(date);
    }
    public List<Event> getallEvents(){
        return model.getEvents();
    }
    public int getEventCount(){
        return model.getEventCount();
    }
    public boolean isEmpty(){
        return model.isEmpty();
    }
    public List<Event> getEventsByDateRange(LocalDate starDate, LocalDate endDate){
        List<Event> result=new ArrayList<>();
        for (LocalDate date=starDate;!date.isAfter(endDate);date=date.plusDays(1)){
            List<Event> eventsOnDate=model.getEventsByDate(date);
            result.addAll(eventsOnDate);
        }
        return result;
    }
    public List<Event> getActiveEvents(LocalDateTime dateTime){
        List<Event> allEvents=model.getEvents();
        List<Event> result=new ArrayList<>();
        for (int i=0;i<allEvents.size();i++){
            Event event=allEvents.get(i);
            LocalDateTime eventStart=event.getStartTime();
            LocalDateTime eventEnd=event.getEndTime();
            if ((dateTime.equals(eventStart)||dateTime.isAfter(eventStart)&&(dateTime.equals(eventEnd)||dateTime.isBefore(eventEnd)))){
                result.add(event);
            }
        }
        return result;
    }
    public List<Event> getUpcommingEvents(LocalDateTime dateTime){
        List<Event> allEvents=model.getEvents();
        List<Event> result=new ArrayList<>();
        for (int i=0;i<allEvents.size();i++){
            Event event=allEvents.get(i);
            if (event.getStartTime().isAfter(dateTime)){
                result.add(event);
            }
        }
        return result;
    }
    public List<Event> getPastEvents(LocalDateTime dateTime){
        List<Event> allEvents=model.getEvents();
        List<Event> result=new ArrayList<>();
        for (int i=0;i<allEvents.size();i++){
            Event event=allEvents.get(i);
            if (event.getEndTime().isBefore(dateTime)){
                result.add(event);
            }
        }
        return result;
    }
    public List<Event> getEventsOverlappingPeriod(LocalDateTime startTime, LocalDateTime endTime){
        List<Event> allEvents=model.getEvents();
        List<Event> result=new ArrayList<>();
        for (int i=0;i<allEvents.size();i++){
            Event event=allEvents.get(i);
            LocalDateTime eventStart=event.getStartTime();
            LocalDateTime eventEnd=event.getEndTime();
            if (eventStart.isBefore(endTime)&&eventEnd.isAfter(startTime)){
                result.add(event);
            }
        }
        return result;
    }
    public List<Event> searchEventsByTitle(String searchText){
        List<Event> allEvents=model.getEvents();
        List<Event> result=new ArrayList<>();
        if (searchText==null||searchText.isEmpty()){
            return result;
        }
        String searchLowerCase=searchText.toLowerCase();
        for (int i=0;i<allEvents.size();i++){
            Event event=allEvents.get(i);
            String title=event.getTitle();
            if (title!=null&&title.toLowerCase().contains(searchLowerCase)){
                result.add(event);
            }
        }
        return result;
    }
    public List<Event> getMorningEvents(){
        List<Event> allEvents=model.getEvents();
        List<Event> result=new ArrayList<>();
        for (int i=0;i<allEvents.size();i++){
            Event event=allEvents.get(i);
            if (event.getStartTime().getHour()<12){
                result.add(event);
            }
        }
        return result;
    }
    public List<Event> getAfternoonEvents(){
        List<Event> allEvents=model.getEvents();
        List<Event> result=new ArrayList<>();
        for (int i=0;i<allEvents.size();i++){
            Event event=allEvents.get(i);
            if (event.getStartTime().getHour()>=12){
                result.add(event);
            }
        }
        return result;
    }
    public List<Event> getEventsLongerThan(int hours){
        List<Event> allEvents=model.getEvents();
        List<Event> result=new ArrayList<>();
        for (int i=0;i<allEvents.size();i++){
            Event event=allEvents.get(i);
            Duration duration=Duration.between(event.getStartTime(), event.getEndTime());
            if (duration.toHours()>hours){
                result.add(event);
            }
        }
        return result;
    }
    public List<Event> filterEvents(Predicate<Event> predicate){
        List<Event> allEvents=model.getEvents();
        List<Event> result=new ArrayList<>();
        for (int i=0;i<allEvents.size();i++){
            Event event=allEvents.get(i);
            if (predicate.test(event)){
                result.add(event);
            }
        }
        return result;
    }
    public static Predicate<Event> createDateRangePredicate(final LocalDate startDate, final LocalDate endDate){
        return new Predicate<Event>(){
            public boolean test(Event event){
                LocalDate eventDate=event.getDate();
                return !eventDate.isBefore(startDate)&&!eventDate.isAfter(endDate);
            }
        };
    }
    public static Predicate<Event> createUpcommingPredicate(final LocalDateTime dateTime){
        return new Predicate<Event>(){
            public boolean test(Event event){
                return event.getStartTime().isAfter(dateTime);
            }
        };
    }
    public static Predicate<Event> createTitleContainsPredicate(final String searchText){
        return new Predicate<Event>(){
            public boolean test(Event event){
                String title=event.getTitle();
                return title!=null&&title.contains(searchText);
            }
        };
    }
}