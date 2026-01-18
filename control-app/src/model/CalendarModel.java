package model;
/*
*In-memory calendar data container.
 *
*Responsibilities:
*- Store all calendar events
*- Provide basic accessors
 *
*Java data types used:
*- List<Event>
*- ArrayList<Event>
 *
*Java technologies involved:
*- Java Collections Framework
 *
*Design intent:
*This class is a data holder, not a rule enforcer.
 */

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CalendarModel{
    public List<Event> allEvents;
    public CalendarModel(){
        this.allEvents=new ArrayList<>();
    }
    public void addEvent(Event newEvent){
        this.allEvents.add(newEvent);
        sortEvents();
    }
    public void addEvents(List<Event> newEvents){
        sortEvents();
        this.allEvents.addAll(newEvents);
        sortEvents();
    }
    public boolean removeEvent(Event eventToRemove){
        sortEvents();
        for (int i=0;i<allEvents.size();i++){
            if (eventToRemove.equals(this.allEvents.get(i))){
                this.allEvents.remove(i);
                return true;
            }
        }
        return false;
    }
    public void clearEvents(){
        this.allEvents.clear();
    }
    public int getEventCount(){
        return this.allEvents.size();
    }
    public List<Event> getEvents(){
        sortEvents();
        return this.allEvents;
    }
    public List<Event> getEventsByDate(LocalDate searchingDate){
        sortEvents();
        List<Event> newEventsList=new ArrayList<>();
        for (int i=0;i<this.allEvents.size();i++){
            if (this.allEvents.get(i).getDate().equals(searchingDate)){
                newEventsList.add(new Event(allEvents.get(i).getTitle(), allEvents.get(i).getDate(), allEvents.get(i).getStartTime().toLocalTime(), allEvents.get(i).getEndTime().toLocalTime()));
            }
        }
        return newEventsList;
    }
    public boolean isEmpty(){
        return this.allEvents.isEmpty();
    }
    public String toString(){
        sortEvents();
        String eventsString=new String();
        for (int i=0;i<allEvents.size();i++){
            eventsString.concat("Event "+Integer.toString(i+1)+": "+this.allEvents.get(i).toString()+" \n ");
        }
        return eventsString;
    }
    public void sortEvents(){
        Collections.sort(this.allEvents, Comparator.comparing(Event::getDate).thenComparing(Event::getStartTime));
    }
    public List<Event> getSortedEvents(){
        List<Event> sortedEvents=new ArrayList<>(allEvents);
        sortedEvents.sort(Comparator.comparing(Event::getDate).thenComparing(Event::getStartTime));
        return sortedEvents;
    }
    @Override
    public boolean equals(Object obj){
        if (this==obj){
            return true;
        }
        if (obj==null||getClass()!=obj.getClass()) return false;
        CalendarModel that=(CalendarModel) obj;
        if (allEvents.size()!=that.allEvents.size()) return false;
        List<Event> sortedThis=getSortedEvents();
        List<Event> sortedThat=that.getSortedEvents();
        for (int i=0;i<sortedThis.size();i++){
            if (!sortedThis.get(i).equals(sortedThat.get(i))){
                return false;
            }
        }
        return true;
    }
    @Override
    public int hashCode(){
        List<Event> sortedEvents=getSortedEvents();
        int result=1;
        for (Event event:sortedEvents){
            result=31*result+(event==null?0:event.hashCode());
        }
        return result;
    }
}