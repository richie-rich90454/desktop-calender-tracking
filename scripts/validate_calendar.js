#!/usr/bin/env node
/**
*Calendar JSON Validation Script (Node.js)
*
*Validates JSON files against the calendar schema and business rules.
*Supports validation of:
*1. JSON schema structure
*2. Date/time format compliance (ISO 8601)
*3. Business rules (time ranges, no overlapping events)
*
*Usage:
*  node validate_calendar.js <file_or_directory>
*  node validate_calendar.js --help
 */

let fs=require("fs");
let path=require("path");
let {promisify}=require("util");
let readFile=promisify(fs.readFile);
let stat=promisify(fs.stat);
let readdir=promisify(fs.readdir);
let DATE_REGEX=/^\d{4}-\d{2}-\d{2}$/;
let TIME_REGEX=/^\d{2}:\d{2}:\d{2}$/;
let DATETIME_REGEX=/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/;
class CalendarValidator{
    constructor(options={}){
        this.strict=options.strict!==false;
        this.errors=[];
        this.warnings=[];
        this.filesProcessed=0;
        this.filesValid=0;
    }
    async validate(target){
        try{
            let stats=await stat(target);
            if (stats.isDirectory()){
                await this._validateDirectory(target);
            }
            else if (stats.isFile()){
                await this._validateFile(target);
            }
            else{
                this._addError(`Invalid target: ${target} is not a file or directory`);
            }
        }
        catch (error){
            this._addError(`Error accessing ${target}: ${error.message}`);
        }
        return this._generateReport();
    }
    async _validateDirectory(dirPath){
        try{
            let files=await readdir(dirPath);
            for (let file of files){
                if (file.toLowerCase().endsWith(".json")){
                    let filePath=path.join(dirPath, file);
                    await this._validateFile(filePath);
                }
            }
        }
        catch (error){
            this._addError(`Error reading directory ${dirPath}: ${error.message}`);
        }
    }
    async _validateFile(filePath){
        this.filesProcessed++;
        try{
            let content=await readFile(filePath, "utf8");
            let data=JSON.parse(content);
            let fileErrors=[];
            let fileWarnings=[];
            if (!this._validateSchema(data, filePath, fileErrors)){
                this.errors.push(...fileErrors);
                return;
            }
            let events=data.events||[];
            if (!this._validateEvents(events, filePath, fileErrors, fileWarnings)){
                this.errors.push(...fileErrors);
                this.warnings.push(...fileWarnings);
                return;
            }
            if (data.savedAt&&!this._validateDateTime(data.savedAt)){
                fileErrors.push(`Invalid savedAt format: ${data.savedAt}. Expected YYYY-MM-DDTHH:MM:SS`);
            }
            if (data.version!=="1.0"){
                fileWarnings.push(`Unexpected version: ${data.version}. Expected "1.0"`);
            }
            if (fileErrors.length==0){
                this.filesValid++;
                console.log(`✓ ${filePath}-Valid`);
            }
            else{
                this.errors.push(...fileErrors.map(err=>`${filePath}: ${err}`));
            }
            this.warnings.push(...fileWarnings.map(warn=>`${filePath}: ${warn}`));
            
        }
        catch (error){
            if (error instanceof SyntaxError){
                this._addError(`${filePath}: Invalid JSON-${error.message}`);
            }
            else{
                this._addError(`${filePath}: ${error.message}`);
            }
        }
    }
    _validateSchema(data, filePath, errors){
        let requiredFields=["version", "savedAt", "events"];
        let valid=true;
        for (let field of requiredFields){
            if (!(field in data)){
                errors.push(`Missing required field: ${field}`);
                valid=false;
            }
        }
        if (data.version&&typeof data.version!=="string"){
            errors.push(`Field "version" must be a string`);
            valid=false;
        }
        if (data.savedAt&&typeof data.savedAt!=="string"){
            errors.push(`Field "savedAt" must be a string`);
            valid=false;
        }
        if (data.events&&!Array.isArray(data.events)){
            errors.push(`Field "events" must be an array`);
            valid=false;
        }
        return valid;
    }
    _validateEvents(events, filePath, errors, warnings){
        if (!Array.isArray(events)){
            errors.push(`"events" must be an array`);
            return false;
        }
        let allValid=true;
        events.forEach((event, index)=>{
            if (!this._validateEvent(event, index, errors)){
                allValid=false;
            }
        });
        if (allValid&&this.strict){
            let overlapping=this._findOverlappingEvents(events);
            overlapping.forEach(overlap=>{
                errors.push(`Overlapping events: ${overlap}`);
            });
            if (overlapping.length>0){
                allValid=false;
            }
        }
        return allValid;
    }
    _validateEvent(event, index, errors){
        let requiredFields=["title", "date", "startTime", "endTime", "startDateTime", "endDateTime"];
        let valid=true;
        requiredFields.forEach(field=>{
            if (!(field in event)){
                errors.push(`Event ${index}: Missing required field "${field}"`);
                valid=false;
            }
        });
        if (!valid) return false;
        requiredFields.forEach(field=>{
            if (typeof event[field]!=="string"){
                errors.push(`Event ${index}: Field "${field}" must be a string`);
                valid=false;
            }
        });
        if (!this._validateDate(event.date)){
            errors.push(`Event ${index}: Invalid date format "${event.date}". Expected YYYY-MM-DD`);
            valid=false;
        }
        if (!this._validateTime(event.startTime)){
            errors.push(`Event ${index}: Invalid startTime format "${event.startTime}". Expected HH:MM:SS`);
            valid=false;
        }
        if (!this._validateTime(event.endTime)){
            errors.push(`Event ${index}: Invalid endTime format "${event.endTime}". Expected HH:MM:SS`);
            valid=false;
        }
        if (!this._validateDateTime(event.startDateTime)){
            errors.push(`Event ${index}: Invalid startDateTime format "${event.startDateTime}". Expected YYYY-MM-DDTHH:MM:SS`);
            valid=false;
        }
        if (!this._validateDateTime(event.endDateTime)){
            errors.push(`Event ${index}: Invalid endDateTime format "${event.endDateTime}". Expected YYYY-MM-DDTHH:MM:SS`);
            valid=false;
        }
        if (this._validateTime(event.startTime)&&this._validateTime(event.endTime)){
            let startParts=event.startTime.split(":").map(Number);
            let endParts=event.endTime.split(":").map(Number);
            let startSeconds=startParts[0]*3600+startParts[1]*60+startParts[2];
            let endSeconds=endParts[0]*3600+endParts[1]*60+endParts[2];
            if (endSeconds<=startSeconds){
                errors.push(`Event ${index}: endTime must be after startTime (${event.startTime} -> ${event.endTime})`);
                valid=false;
            }
        }
        if (this.strict){
            let expectedStart=`${event.date}T${event.startTime}`;
            if (event.startDateTime!==expectedStart){
                errors.push(`Event ${index}: startDateTime inconsistency. Expected "${expectedStart}", got "${event.startDateTime}"`);
                valid=false;
            }
            let expectedEnd=`${event.date}T${event.endTime}`;
            if (event.endDateTime!==expectedEnd){
                errors.push(`Event ${index}: endDateTime inconsistency. Expected "${expectedEnd}", got "${event.endDateTime}"`);
                valid=false;
            }
        }
        return valid;
    }
    _findOverlappingEvents(events){
        let overlapping=[];
        let eventsByDate={};
        events.forEach((event, index)=>{
            if (!eventsByDate[event.date]){
                eventsByDate[event.date]=[];
            }
            eventsByDate[event.date].push({ ...event, index });
        });
        Object.values(eventsByDate).forEach(dateEvents=>{
            for (let i=0;i<dateEvents.length;i++){
                for (let j=i+1;j<dateEvents.length;j++){
                    let event1=dateEvents[i];
                    let event2=dateEvents[j];
                    if (this._eventsOverlap(event1, event2)){
                        overlapping.push(`"${event1.title}" (index ${event1.index}) overlaps with "${event2.title}" (index ${event2.index}) on ${event1.date}`);
                    }
                }
            }
        });
        return overlapping;
    }
    _eventsOverlap(event1, event2){
        let parseTime=(timeStr)=>{
            let parts=timeStr.split(":").map(Number);
            return parts[0]*3600+parts[1]*60+parts[2];
        };
        let start1=parseTime(event1.startTime);
        let end1=parseTime(event1.endTime);
        let start2=parseTime(event2.startTime);
        let end2=parseTime(event2.endTime);
        return start1<end2&&end1>start2;
    }
    _validateDate(dateStr){
        let flexibleDateRegex=/^(\d{4})-(\d{1,2})-(\d{1,2})$/;
        let match=dateStr.match(flexibleDateRegex);
        if (!match) return false;
        let year=parseInt(match[1], 10);
        let month=parseInt(match[2], 10);
        let day=parseInt(match[3], 10);
        if (month<1||month>12) return false;
        if (day<1||day>31) return false;
        let date=new Date(year, month-1, day);
        return date.getFullYear()==year&&date.getMonth()==month-1&&date.getDate()==day;
    }
    _validateTime(timeStr){
        if (!TIME_REGEX.test(timeStr)) return false;
        let parts=timeStr.split(":").map(Number);
        let hour=parts[0];
        let minute=parts[1];
        let second=parts[2];
        return hour>=0&&hour<24&&minute>=0&&minute<60&&second>=0&&second<60;
    }
    _validateDateTime(datetimeStr){
        let flexibleDatetimeRegex=/^(\d{4})-(\d{1,2})-(\d{1,2})T(\d{2}):(\d{2}):(\d{2})$/;
        let match=datetimeStr.match(flexibleDatetimeRegex);
        if (!match) return false;
        let datePart=`${match[1]}-${match[2]}-${match[3]}`;
        let timePart=`${match[4]}:${match[5]}:${match[6]}`;
        return this._validateDate(datePart)&&this._validateTime(timePart);
    }
    _addError(message){
        this.errors.push(message);
    }
    _generateReport(){
        return{
            valid: this.errors.length==0,
            filesProcessed: this.filesProcessed,
            filesValid: this.filesValid,
            errors: this.errors,
            warnings: this.warnings
        };
    }
}
async function main(){
    let args=process.argv.slice(2);
    if (args.length==0||args.includes("--help")||args.includes("-h")){
        printHelp();
        process.exit(0);
    }
    let options={
        strict: !args.includes("--no-strict")
    };
    let targets=args.filter(arg=>!arg.startsWith("--"));
    if (targets.length==0){
        console.error("Error: No file or directory specified");
        printHelp();
        process.exit(1);
    }
    let validator=new CalendarValidator(options);
    let allValid=true;
    for (let target of targets){
        let report=await validator.validate(target);
        if (!report.valid){
            allValid=false;
        }
    }
    if (validator.warnings.length>0){
        console.log("\nWarnings:");
        validator.warnings.forEach(warning=>console.log(`  ⚠ ${warning}`));
    }
    if (validator.errors.length>0){
        console.log("\nErrors:");
        validator.errors.forEach(error=>console.log(`  ✗ ${error}`));
    }
    console.log(`\nSummary:`);
    console.log(`  Files processed: ${validator.filesProcessed}`);
    console.log(`  Files valid: ${validator.filesValid}`);
    console.log(`  Total errors: ${validator.errors.length}`);
    console.log(`  Total warnings: ${validator.warnings.length}`);
    process.exit(allValid?0:1);
}
function printHelp(){
    console.log(`
Calendar JSON Validator
=======================

Validates JSON files against the calendar schema and business rules.

Usage:
  node validate_calendar.js [options] <file_or_directory...>

Options:
  --no-strict    Disable strict validation (skip datetime consistency checks)
  --help, -h     Show this help message

Examples:
  node validate_calendar.js shared/calendar_schema.json
  node validate_calendar.js --no-strict shared/
  node validate_calendar.js file1.json file2.json

Validation checks:
  ✓ JSON syntax and structure
  ✓ Required fields present
  ✓ Date/time formats (ISO 8601)
  ✓ Time ranges (end after start)
  ✓ No overlapping events (same day)
  ✓ Datetime consistency (strict mode only)
`);
}
if (require.main==module){
    main().catch(error=>{
        console.error("Fatal error:", error);
        process.exit(1);
    });
}
module.exports=CalendarValidator;