package ai;

import model.Event;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages AI prompts loaded from prompts.txt file
 * Formats prompts with variables for event generation
 */

public class AIPromptManager{
    private static final String PROMPTS_FILE="prompts.txt";
    private static final DateTimeFormatter DATE_FORMATTER=DateTimeFormatter.ISO_LOCAL_DATE;
    private final Map<String, String> prompts=new HashMap<>();
    private final Path promptsPath;
    public AIPromptManager(){
        this.promptsPath=Paths.get("control-app", "src", "ai", PROMPTS_FILE);
        loadPrompts();
    }
    public AIPromptManager(String customPromptsPath){
        this.promptsPath=Paths.get(customPromptsPath);
        loadPrompts();
    }
    private void loadPrompts(){
        try{
            if (!Files.exists(promptsPath)){
                System.err.println("Warning: Prompts file not found at "+promptsPath);
                loadDefaultPrompts();
                return;
            }
            String content=Files.readString(promptsPath);
            parsePrompts(content);
            System.out.println("Loaded "+prompts.size()+" prompts from "+promptsPath);
        }
        catch (IOException e){
            System.err.println("Failed to load prompts file: "+e.getMessage());
            loadDefaultPrompts();
        }
    }
    private void parsePrompts(String content){
        String[] lines=content.split("\n");
        StringBuilder currentPrompt=new StringBuilder();
        String currentKey=null;
        boolean inPrompt=false;
        for (String line:lines){
            line=line.trim();
            if (line.startsWith("[")&&line.endsWith("]")){
                if (currentKey!=null&&currentPrompt.length() >0){
                    prompts.put(currentKey, currentPrompt.toString().trim());
                }
                currentKey=line.substring(1, line.length() -1);
                currentPrompt=new StringBuilder();
                inPrompt=true;
            }
            else if (inPrompt&&!line.startsWith("#")){
                currentPrompt.append(line).append("\n");
            }
        }
        if (currentKey!=null&&currentPrompt.length() >0){
            prompts.put(currentKey, currentPrompt.toString().trim());
        }
    }
    private void loadDefaultPrompts(){
        prompts.put("SYSTEM_PROMPT_EVENT_GENERATION","You are a calendar planning assistant. Return a JSON object with an 'events' array. "+"Each event must have: title (string), date (YYYY-MM-DD), start_time (HH:MM in 24-hour format), "+"end_time (HH:MM in 24-hour format). Return ONLY the JSON object, no other text.");
        prompts.put("USER_PROMPT_TEMPLATE","Goal: {goal}\n"+"Start Date: {start_date}\n"+"Number of days: {days}\n"+"Existing events to avoid: {existing_events}\n\n"+"Generate a schedule of events for the next {days} days starting from {start_date}. "+"Make sure events don't overlap with existing events. Return ONLY the JSON array.");
        prompts.put("TEST_PROMPT", "Say \"OK\" if you can hear me.");
        System.out.println("Loaded default prompts");
    }
    public String getPrompt(String key){
        String prompt=prompts.get(key);
        if (prompt==null){
            throw new IllegalArgumentException("Prompt not found: "+key);
        }
        return prompt;
    }
    public String getEventGenerationPrompt(String goal, LocalDate startDate, int days, List<Event> existingEvents){
        String template=getPrompt("USER_PROMPT_TEMPLATE");
        String existingEventsStr=formatExistingEvents(existingEvents);
        return template.replace("{goal}", escapeForJson(goal)).replace("{start_date}", startDate.format(DATE_FORMATTER)).replace("{days}", String.valueOf(days)).replace("{existing_events}", escapeForJson(existingEventsStr));
    }
    public String getSystemPrompt(){
        return getPrompt("SYSTEM_PROMPT_EVENT_GENERATION");
    }
    public String getTestPrompt(){
        return getPrompt("TEST_PROMPT");
    }
    private String formatExistingEvents(List<Event> events){
        if (events==null||events.isEmpty()){
            return "None";
        }
        StringBuilder sb=new StringBuilder();
        for (Event event:events){
            sb.append(event.getDate().format(DATE_FORMATTER)).append(" ").append(event.getTitle()).append(" ").append(event.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm"))).append("-").append(event.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"))).append("; ");
        }
        return sb.toString().trim();
    }
    private String escapeForJson(String text){
        if (text==null){
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    public void reloadPrompts(){
        prompts.clear();
        loadPrompts();
    }
    public String[] getPromptKeys(){
        return prompts.keySet().toArray(new String[0]);
    }
    public boolean hasPrompt(String key){
        return prompts.containsKey(key);
    }
}