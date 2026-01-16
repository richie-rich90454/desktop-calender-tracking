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
        if (title==null||title.isBlank()){
            this.title="Blank event";
        }
        else{
            this.title=title;
        }
        if (date==null){
            throw new IllegalArgumentException("Date cannot be null");
        }
        this.date=date;
        if (startTime==null||endTime==null){
            throw new IllegalArgumentException("Start/end time cannot be null");
        }
        this.startTime=LocalDateTime.of(date, startTime);
        this.endTime=LocalDateTime.of(date, endTime);
        if (!this.endTime.isAfter(this.startTime)){
            throw new IllegalArgumentException("End time must be after start time");
        }
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