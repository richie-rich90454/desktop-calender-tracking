# Calendar Schema Documentation

## Overview
This document describes the JSON schema used by the Desktop Calendar Tracking application for storing calendar events. The schema defines the structure for serializing and deserializing calendar data between the Java application and persistent storage.

## Schema Version
- **Current Version:** 1.0
- **Schema Location:** `shared/calendar_schema.json`
- **Used By:** `JsonStore.java` for saving/loading calendar data

## Root Object Structure
The JSON file contains a single root object with the following properties:

| Property | Type | Required | Description | Example |
|----------|------|----------|-------------|---------|
| `version` | string | Yes | Schema version identifier | `"1.0"` |
| `savedAt` | string | Yes | ISO 8601 timestamp when the file was saved | `"2026-01-18T14:30:00"` |
| `events` | array | Yes | Array of event objects | `[{...}, {...}]` |

## Event Object Structure
Each event in the `events` array has the following properties:

| Property | Type | Required | Description | Format | Example |
|----------|------|----------|-------------|--------|---------|
| `title` | string | Yes | Event title/description | Plain text | `"Team Meeting"` |
| `date` | string | Yes | Event date (local date) | ISO 8601 (YYYY-MM-DD) | `"2026-01-18"` |
| `startTime` | string | Yes | Event start time (local time) | ISO 8601 (HH:MM:SS) | `"09:00:00"` |
| `endTime` | string | Yes | Event end time (local time) | ISO 8601 (HH:MM:SS) | `"10:00:00"` |
| `startDateTime` | string | Yes | Combined start date and time | ISO 8601 (YYYY-MM-DDTHH:MM:SS) | `"2026-01-18T09:00:00"` |
| `endDateTime` | string | Yes | Combined end date and time | ISO 8601 (YYYY-MM-DDTHH:MM:SS) | `"2026-01-18T10:00:00"` |

## Field Relationships and Constraints

### 1. Time Consistency
- `startDateTime` must equal `date + "T" + startTime`
- `endDateTime` must equal `date + "T" + endTime`
- The date portion of `startDateTime` and `endDateTime` must match the `date` field

### 2. Time Range Validation
- `endTime` must be chronologically after `startTime`
- `endDateTime` must be chronologically after `startDateTime`

### 3. Business Rules (Enforced by CalendarValidationService)
- No overlapping events on the same day
- Event titles cannot be null or blank (blank titles are replaced with "Blank event")

## Example JSON
```json
{
    "version": "1.0",
    "savedAt": "2026-01-18T14:30:00",
    "events": [
        {
            "title": "Team Meeting",
            "date": "2026-01-18",
            "startTime": "09:00:00",
            "endTime": "10:00:00",
            "startDateTime": "2026-01-18T09:00:00",
            "endDateTime": "2026-01-18T10:00:00"
        },
        {
            "title": "Lunch Break",
            "date": "2026-01-18",
            "startTime": "12:00:00",
            "endTime": "13:00:00",
            "startDateTime": "2026-01-18T12:00:00",
            "endDateTime": "2026-01-18T13:00:00"
        }
    ]
}
```

## Usage in Java Application

### Serialization (Saving)
The `JsonStore.convertToJson()` method:
1. Creates the root object with current timestamp
2. Converts each `Event` object to JSON format
3. Ensures all date/time fields use ISO 8601 formatting

### Deserialization (Loading)
The `JsonStore.parseFromJson()` method:
1. Parses the JSON string
2. Extracts events array
3. Creates `Event` objects from JSON data
4. Adds events to `CalendarModel`

### Validation
The `CalendarValidationService` validates:
- Time range validity (`endTime > startTime`)
- No overlapping events on the same day

## File Location and Naming
- **Default Path:** `~/.calendarapp/calendar_events.json` (user's home directory)
- **Backup Path:** `~/.calendarapp/calendar_events_backup.json`
- **Shared Schema:** `shared/calendar_schema.json` (example/reference schema)

## Migration Considerations
When updating the schema version:
1. Update the `version` field
2. Maintain backward compatibility in `JsonStore.parseFromJson()`
3. Consider adding migration logic in `JsonStore` if needed

## Related Files
- `control-app/src/storage/JsonStore.java` - JSON serialization/deserialization
- `control-app/src/service/CalendarValidationService.java` - Business rule validation
- `control-app/src/model/Event.java` - Event data model
- `control-app/src/model/CalendarModel.java` - Calendar data model

---

*Last Updated: 2026-01-18*  
*Schema Version: 1.0*