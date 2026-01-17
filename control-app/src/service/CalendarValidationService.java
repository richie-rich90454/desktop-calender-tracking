package service;
/*
3. service/CalendarValidationService.java

Why:

Ensures business rules like no overlapping events, end > start.

Tasks:

boolean isValid(Event e, CalendarModel model)

Detect overlapping events

Detect invalid time ranges (extra safety)

Java types:

Event, List<Event>, Comparator<Event>

Tip: Use Streams API for filtering and comparisons.
 */
import model.CalendarModel;
import model.Event;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Iterator;
public class CalendarValidationService {
    public boolean isValid(Event event, CalendarModel model){
        if (!isTimeRangeValid(event)){
            return false;
        }
        if (hasOverlappingEvents(event, model)){
            return false;
        }
        return true;
    }
    private boolean isTimeRangeValid(Event event){
        return event.getEndTime().isAfter(event.getStartTime());
    }
    private boolean hasOverlappingEvents(Event newEvent, CalendarModel model){
        List<Event> sameDayEvents=model.getEventsByDate(newEvent.getDate());
        if (sameDayEvents.isEmpty()){
            return false;
        }
        for (Event existingEvent:sameDayEvents){
            if (eventsOverlap(newEvent, existingEvent)){
                return true;
            }
        }
        return false;
    }
    private boolean eventsOverlap(Event eventOne, Event eventTwo){
        LocalDateTime startOne=eventOne.getStartTime();
        LocalDateTime startTwo=eventTwo.getStartTime();
        LocalDateTime endOne=eventOne.getEndTime();
        LocalDateTime endTwo=eventTwo.getEndTime();
        return startOne.isBefore(endTwo)&&endOne.isAfter(startTwo);
    }
    public boolean areAllTimeRangesValid(List<Event> events){
        if (events==null){
            return true;
        }
        for (Event event:events){
            if (!isTimeRangeValid(event)){
                return false;
            }
        }
        return true;
    }
    public boolean areAllTimeRangesValidWithIterator(List<Event> events){
        if (events==null){
            return true;
        }
        Iterator<Event> iterator=events.iterator();
        while (iterator.hasNext()){
            Event event=iterator.next();
            if (!isTimeRangeValid(event)){
                return false;
            }
        }
        return true;
    }
    public List<String> getAllOverlappingEventTitles(CalendarModel model){
        List<String> overlappingPairs=new ArrayList<>();
        List<Event> events=model.getEvents();
        for (int i=0;i<events.size();i++){
            for (int j=i+1;j<events.size();j++){
                Event eventOne=events.get(i);
                Event eventTwo=events.get(j);
                if (eventOne.getDate().equals(eventTwo.getDate())){
                    if (eventsOverlap(eventOne, eventTwo)){
                        overlappingPairs.add(eventOne.getTitle()+" overlaps with "+eventTwo.getTitle());
                    }
                }
            }
        }
        return overlappingPairs;
    }
}
