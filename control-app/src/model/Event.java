package model;
/*
 * Mutable calendar event model.
 *
 * Responsibilities:
 * - Store event title
 * - Store event date
 * - Store start and end timestamps
 *
 * Java data types used:
 * - String
 * - LocalDate
 * - LocalDateTime
 *
 * Java technologies involved:
 * - java.time API
 *
 * Design intent:
 * This class represents a single real-world event.
 * It contains no UI or storage logic.
 */

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
        return new String(title);
    }
    public LocalDate getDate(){
        return LocalDate.of(this.date.getYear(), this.date.getMonthValue(), this.date.getDayOfMonth());
    }
    public LocalDateTime getStartTime(){
        return LocalDateTime.of(this.startTime.toLocalDate(),this.startTime.toLocalTime());
    }
    public LocalDateTime getEndTime(){
                return LocalDateTime.of(this.endTime.toLocalDate(),this.endTime.toLocalTime());
    }
    public String toString(){
        return "Event starts at "+startTime.toString()+" and ends at "+endTime.toString();
    }
}