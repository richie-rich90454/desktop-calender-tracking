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
        setDate(date);
        setStartTime(startTime);
        setEndTime(endTime);
    }
    public void setTitle(String title){
        if (!title.equals("")){
            this.title=title;
        }
        else{
            this.title="Blank event";
        }
    }
    public void setDate(LocalDate date){
        if (this.date.isAfter(date)||this.date.equals(null)){
            this.date=date;
        }
        else{
            this.date=LocalDate.now();
        }
    }
    public void setStartTime(LocalTime startTimeWithoutDate){
        this.startTime=LocalDateTime.of(this.date, startTimeWithoutDate);
    }
    public void setEndTime(LocalTime endTimeWithoutDate){
        this.startTime=LocalDateTime.of(this.date, endTimeWithoutDate);
    }
    public String getTitle(){
        return title;
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