package app;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OverlayManager {
    private static String OVERLAY_EXE="CalendarOverlay.exe";
    private static String DATA_FILE=System.getProperty("user.home")+"/AppData/Roaming/DesktopCalendar/calendar_events.json";
    private static DateTimeFormatter FORMATTER=DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private Process overlayProcess;
    private ScheduledExecutorService scheduler;
    private CalendarController controller;
    public OverlayManager(CalendarController controller){
        this.controller=controller;
        ensureDataDirectory();
    }
    public void startOverlay(){
        if (isOverlayRunning()){
            System.out.println("Overlay is already running");
            return;
        }
        try{
            Path overlayPath=Paths.get("overlay-windows", "build", "Release", OVERLAY_EXE);
            if (!Files.exists(overlayPath)){
                overlayPath=Paths.get("native", OVERLAY_EXE);
            }
            if (!Files.exists(overlayPath)){
                System.err.println("Overlay executable not found: "+overlayPath);
                return;
            }
            ProcessBuilder pb=new ProcessBuilder(overlayPath.toString(), "--silent");
            pb.directory(overlayPath.getParent().toFile());
            overlayProcess=pb.start();
            System.out.println("Started overlay process");
            startDataSync();
            
        }
        catch (IOException e){
            System.err.println("Failed to start overlay: "+e.getMessage());
        }
    }
    public void stopOverlay(){
        if (overlayProcess!=null&&overlayProcess.isAlive()){
            overlayProcess.destroy();
            try{
                overlayProcess.waitFor(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException e){
                overlayProcess.destroyForcibly();
            }
            System.out.println("Stopped overlay process");
        }
        if (scheduler!=null){
            scheduler.shutdown();
        }
    }
    public void updateOverlayData(){
        try{
            String jsonData=generateEventsJson();
            Files.write(Paths.get(DATA_FILE), jsonData.getBytes());
            System.out.println("Updated overlay data file");
        }
        catch (IOException e){
            System.err.println("Failed to update overlay data: "+e.getMessage());
        }
    }
    private void startDataSync(){
        scheduler=Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(()->updateOverlayData(), 0, 30, TimeUnit.SECONDS);
    }
    private String generateEventsJson(){
        StringBuilder json=new StringBuilder();
        json.append("{\n");
        json.append("  \"version\": \"1.0\",\n");
        json.append("  \"generatedAt\": \"").append(LocalDateTime.now().format(FORMATTER)).append("\",\n");
        json.append("  \"events\": [\n");
        List<model.Event> events=controller.getEventsForToday();
        for (int i=0;i<events.size();i++){
            model.Event event=events.get(i);
            json.append("   {\n");
            json.append("      \"title\": \"").append(escapeJson(event.getTitle())).append("\",\n");
            json.append("      \"date\": \"").append(event.getDate().toString()).append("\",\n");
            json.append("      \"startTime\": \"").append(event.getStartTime().toLocalTime().toString()).append("\",\n");
            json.append("      \"endTime\": \"").append(event.getEndTime().toLocalTime().toString()).append("\"\n");
            json.append("    }");
            if (i<events.size()-1){
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}");
        return json.toString();
    }
    private String escapeJson(String input){
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    private boolean isOverlayRunning(){
        try{
            // String tasklist=System.getenv("windir")+"\\system32\\tasklist.exe";
            Process process = new ProcessBuilder("tasklist").start();
            BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line=reader.readLine())!=null){
                if (line.contains(OVERLAY_EXE)){
                    return true;
                }
            }
        }
        catch (IOException e){
            
        }
        return false;
    }
    private void ensureDataDirectory(){
        try{
            Path dataDir=Paths.get(DATA_FILE).getParent();
            if (!Files.exists(dataDir)){
                Files.createDirectories(dataDir);
            }
        }
        catch (IOException e){
            System.err.println("Failed to create data directory: "+e.getMessage());
        }
    }
}