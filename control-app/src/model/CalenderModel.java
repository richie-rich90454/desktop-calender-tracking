package model;
/*
 * In-memory calendar data container.
 *
 * Responsibilities:
 * - Store all calendar events
 * - Provide basic accessors
 *
 * Java data types used:
 * - List<Event>
 * - ArrayList<Event>
 *
 * Java technologies involved:
 * - Java Collections Framework
 *
 * Design intent:
 * This class is a data holder, not a rule enforcer.
 */

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CalenderModel {
    public List<Event> allEvents;
    public CalenderModel(){
        this.allEvents=new ArrayList<>();
    }
    public void addEvent(Event newEvent){
        this.allEvents.add(newEvent);
    }
    public void addEvents(List<Event> newEvents){
        this.allEvents.addAll(newEvents);
    }
    public boolean removeEvent(Event eventToRemove){
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
        return this.allEvents;
    }
    public List<Event> getEventsByDate(LocalDate searchingDate){
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
        String eventsString=new String();
        for (int i=0;i<allEvents.size();i++){
            eventsString.concat("Event "+Integer.toString(i+1)+": "+this.allEvents.get(i).toString()+" \n ");
        }
        return eventsString;
    }
}