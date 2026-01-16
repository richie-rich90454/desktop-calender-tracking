package model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class Event {
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDate date;
    public Event (String title, LocalDate date, LocalTime startTime, LocalTime endTime){
        setTitle(title);
        setEventDateTime(date, startTime, endTime);
    }
    public void setTitle(String newTitle){
        if (newTitle==null||newTitle.isBlank()){
            this.title="Blank event";
        }
        else{
            this.title=newTitle;
        }
    }
    public void setEventDateTime(LocalDate newDate, LocalTime newStartTime, LocalTime newEndTime){
        if (newDate==null){
            throw new IllegalArgumentException("Date cannot be null");
        }
        this.date=newDate;
        if (newStartTime==null||newEndTime==null){
            throw new IllegalArgumentException("Start/end time cannot be null");
        }
        else if (!newEndTime.isAfter(newStartTime)){
            throw new IllegalArgumentException("End time must be after start time");
        }
        this.startTime=LocalDateTime.of(date, newStartTime);
        this.endTime=LocalDateTime.of(date, newEndTime);
    }
    public String getTitle(){
        return title;
    }
    public LocalDate getDate(){
        return date;
    }
    public LocalDateTime getStartTime(){
        return startTime;
    }
    public LocalDateTime getEndTime(){
        return endTime;
    }
}
// Plain data object representing a calendar event.
// Contains:
// - Title
// - Start time
// - End time
//
// No logic beyond basic validation.
// This is a pure data model.