package app;
/*
 * Central application controller.
 *
 * Responsibilities:
 * - Act as the single entry point for UI actions
 * - Coordinate between model, state, and storage
 * - Expose safe methods to mutate calendar data
 *
 * Java data types used:
 * - CalendarModel
 * - Event
 * - List<Event>
 * - Optional<Event>
 *
 * Java technologies involved:
 * - MVC-style architecture
 * - Encapsulation
 *
 * Design intent:
 * UI NEVER talks directly to the model or storage.
 * All changes go through this controller.
 */
import model.CalendarModel;
import model.Event;
import service.CalendarValidationService;
import state.AppState;
import storage.JsonStore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import calendar.CalendarQuery;
import ai.*;
import ui.AIConfigDialog;
import javax.swing.*;
import java.awt.*;

import ui.AIProgressDialog;
public class CalendarController {
	private CalendarModel model;
	private CalendarValidationService validationService;
	private CalendarQuery query;
	private AppState appState;
	private JsonStore storage;
	public CalendarController(AppState appState, JsonStore storage){
		this.appState=appState;
		this.model=appState.getCalendarModel();
		this.storage=storage;
		this.validationService=new CalendarValidationService();
		this.query=new CalendarQuery(this.model);
		loadCalendar();
		Timer autoSaveTimer=new Timer(30000, e->{
			if (hasUnsavedChanges()){
				saveCalendar();
				System.out.println("Auto-saved at "+new java.util.Date());
			}
		});
		autoSaveTimer.start();
	}
	public CalendarController(){
		this.appState=new AppState();
		this.model=appState.getCalendarModel();
		this.storage=new JsonStore();
		this.validationService=new CalendarValidationService();
		this.query=new CalendarQuery(this.model);
		loadCalendar();
		Timer autoSaveTimer=new Timer(30000, e->{
			if (hasUnsavedChanges()){
				saveCalendar();
				System.out.println("Auto-saved at "+new java.util.Date());
			}
		});
		autoSaveTimer.start();
	}
	public CalendarController(AppState appState){
		this.appState=appState;
		this.model=appState.getCalendarModel();
		this.validationService=new CalendarValidationService();
		this.query=new CalendarQuery(this.model);
		loadCalendar();
		Timer autoSaveTimer=new Timer(30000, e->{
			if (hasUnsavedChanges()){
				saveCalendar();
				System.out.println("Auto-saved at "+new java.util.Date());
			}
		});
		autoSaveTimer.start();
	}
	public boolean saveCalendar(){
		boolean success=storage.saveCalendar(model);
		if (success){
			appState.markAsClean();
		}
		return success;
	}
	public boolean saveCalendarToFile(String filePath){
		JsonStore customStorage=new JsonStore(filePath);
		boolean success=customStorage.saveCalendar(model);
		if (success){
			appState.markAsClean();
		}
		return success;
	}
	public boolean loadCalendar(){
		CalendarModel loadedModel=storage.loadCalendar();
		if (loadedModel!=null){
			model.clearEvents();
			List<Event> events=loadedModel.getEvents();
			for (Event event:events){
				if (validationService.isValid(event, model)){
					model.addEvent(event);
				}
			}
			appState.markAsClean();
			appState.notifyEventsChanged();
			return true;
		}
		return false;
	}
	public boolean loadCalendarFromFile(String filePath){
		JsonStore customStorage=new JsonStore(filePath);
		CalendarModel loadedModel=customStorage.loadCalendar();
		if (loadedModel!=null){
			model.clearEvents();
			List<Event> events=loadedModel.getEvents();
			for (Event event:events){
				if (validationService.isValid(event, model)){
					model.addEvent(event);
				}
			}
			appState.markAsClean();
			appState.notifyEventsChanged();
			return true;
		}
		return false;
	}
	public boolean createBackup(){
		return storage.createManualBackup(model);
	}
	public boolean exportCalendar(String exportPath){
		return storage.exportCalendar(model, java.nio.file.Paths.get(exportPath));
	}
	public boolean importCalendar(String importPath){
		CalendarModel importedModel=storage.importCalendar(java.nio.file.Paths.get(importPath));
		if (importedModel!=null){
			int importedCount=importEventsFromModel(importedModel);
			return importedCount>0;
		}
		return false;
	}
	public boolean hasUnsavedChanges(){
		return appState.isUnsaved();
	}
	public boolean saveFileExists(){
		return storage.saveFileExists();
	}
	public String getStorageInfo(){
		return storage.getStorageInfo();
	}
	public Optional<Event> createEvent(String title, LocalDate date, LocalTime startTime, LocalTime endTime){
		try{
			Event newEvent=new Event(title, date, startTime, endTime);
			if (validationService.isValid(newEvent, model)){
				model.addEvent(newEvent);
				appState.notifyEventAdded(newEvent);
				appState.markAsDirty();
				return Optional.of(newEvent);
			}
			return Optional.empty();
		}
		catch (IllegalArgumentException exception){
			return Optional.empty();
		}
	}
	public boolean createEventUnsafe(Event event){
		try{
			model.addEvent(event);
			appState.notifyEventAdded(event);
			appState.markAsDirty();
			return true;
		}
		catch (Exception exception){
			return false;
		}
	}
	public Optional<Event> updateEvent(Event originalEvent, String newTitle, LocalDate newDate, LocalTime newStartTime, LocalTime newEndTime){
		boolean removed=model.removeEvent(originalEvent);
		if (!removed){
			return Optional.empty();
		}
		String titleUse;
		LocalDate dateUse;
		LocalTime startTimeUse;
		LocalTime endTimeUse;
		if (newTitle!=null){
			titleUse=newTitle;
		}
		else{
			titleUse=originalEvent.getTitle();
		}
		if (newDate!=null){
			dateUse=newDate;
		}
		else{
			dateUse=originalEvent.getDate();
		}
		if (newStartTime!=null){
			startTimeUse=newStartTime;
		}
		else{
			startTimeUse=originalEvent.getStartTime().toLocalTime();
		}
		if (newEndTime!=null){
			endTimeUse=newEndTime;
		}
		else{
			endTimeUse=originalEvent.getEndTime().toLocalTime();
		}
		try{
			Event updatedEvent=new Event(titleUse, dateUse, startTimeUse, endTimeUse);
			if (validationService.isValid(updatedEvent, model)){
				model.addEvent(updatedEvent);
				appState.notifyEventModified(updatedEvent);
				appState.markAsDirty();
				if (originalEvent.equals(appState.getSelectedEvent())){
					appState.setSelectedEvent(updatedEvent);
				}
				return Optional.of(updatedEvent);
			}
			else{
				model.addEvent(originalEvent);
				return Optional.empty();
			}
		}
		catch (IllegalArgumentException exception){
			model.addEvent(originalEvent);
			return Optional.empty();
		}
	}
	public boolean deleteEvent(Event event){
		boolean result=model.removeEvent(event);
		if (result){
			appState.notifyEventDeleted(event);
			appState.markAsDirty();
			if (event.equals(appState.getSelectedEvent())){
				appState.clearSelectedEvent();
			}
		}
		return result;
	}
	public void clearAllEvents(){
		model.clearEvents();
		appState.notifyEventsChanged();
		appState.markAsDirty();
		appState.clearSelectedEvent();
	}
	public List<Event> getEventsbyDate(LocalDate date){
		return query.getEventsbyDate(date);
	}
	public List<Event> getallEvents(){
		return query.getallEvents();
	}
	public List<Event> getEventsByDateRange(LocalDate startDate, LocalDate endDate){
		return query.getEventsByDateRange(startDate, endDate);
	}
	public List<Event> getActiveEvents(LocalDateTime dateTime){
		return query.getActiveEvents(dateTime);
	}
	public List<Event> getUpcomingEvents(LocalDateTime dateTime){
		// Fixed typo: getUpcommingEvents -> getUpcomingEvents
		return query.getUpcommingEvents(dateTime);
	}
	public List<Event> getPastEvents(LocalDateTime dateTime){
		return query.getPastEvents(dateTime);
	}
	public List<Event> searchEventsByTitle(String search){
		return query.searchEventsByTitle(search);
	}
	public boolean isValidEvent(Event event){
		return validationService.isValid(event, model);
	}
	public List<String> getOverlappingEventsReport(){
		return validationService.getAllOverlappingEventTitles(model);
	}
	public int getEventCount(){
		return model.getEventCount();
	}
	public boolean isEmpty(){
		return model.isEmpty();
	}
	public List<Event> getSortedEvents(){
		return model.getSortedEvents();
	}
	public List<Event> addMultipleEvents(List<Event> events){
		List<Event> added=new ArrayList<>();
		for (Event event:events){
			if (validationService.isValid(event, model)){
				model.addEvent(event);
				added.add(event);
			}
		}
		if (!added.isEmpty()){
			appState.notifyEventsChanged();
			appState.markAsDirty();
		}
		return added;
	}
	public int importEventsFromModel(CalendarModel otherModel){
		int num=0;
		List<Event> events=otherModel.getEvents();
		for (Event event:events){
			if (validationService.isValid(event, model)){
				model.addEvent(event);
				num++;
			}
		}
		if (num>0){
			appState.notifyEventsChanged();
			appState.markAsDirty();
		}
		return num;
	}
	public AppState getAppState(){
		return appState;
	}
	public JsonStore getStorage(){
		return storage;
	}
	public void setSelectedDate(LocalDate date){
		appState.setSelectedDate(date);
	}
	public LocalDate getSelectedDate(){
		return appState.getSelectedDate();
	}
	public void setSelectedEvent(Event event){
		appState.setSelectedEvent(event);
	}
	public Event getSelectedEvent(){
		return appState.getSelectedEvent();
	}
	public void goToToday(){
		appState.goToToday();
	}
	public void navigateToPreviousDay(){
		appState.navigateToPreviousDay();
	}
	public void navigateToNextDay(){
		appState.navigateToNextDay();
	}
	public void navigateToPreviousWeek(){
		appState.navigateToPreviousWeek();
	}
	public void navigateToNextWeek(){
		appState.navigateToNextWeek();
	}
	public void navigateToPreviousMonth(){
		appState.navigateToPreviousMonth();
	}
	public void navigateToNextMonth(){
		appState.navigateToNextMonth();
	}
	public void setViewMode(AppState.ViewMode viewMode){
		appState.setCurrentViewMode(viewMode);
	}
	public AppState.ViewMode getViewMode(){
		return appState.getCurrentViewMode();
	}
	public void switchToDayView(){
		appState.switchToDayView();
	}
	public void switchToWeekView(){
		appState.switchToWeekView();
	}
	public void switchToMonthView(){
		appState.switchToMonthView();
	}
	public void switchToAgendaView(){
		appState.switchToAgendaView();
	}
	public void markAsSaved(){
		appState.markAsClean();
	}
	public CalendarModel getModel(){
		return model;
	}
	public CalendarValidationService getValidationService(){
		return validationService;
	}
	public CalendarQuery getQuery(){
		return query;
	}
	public List<Event> getEventsForSelectedDate(){
		return getEventsbyDate(appState.getSelectedDate());
	}
	public List<Event> getEventsForToday(){
		return getEventsbyDate(LocalDate.now());
	}
	public void showAIConfigDialog(JFrame parentFrame){
		AIConfigDialog dialog=new AIConfigDialog(parentFrame);
		dialog.setVisible(true);
		if (dialog.isGenerationComplete()){
			List<Event> generatedEvents=dialog.getGeneratedEvents();
			if (!generatedEvents.isEmpty()){
				List<Event> addedEvents=addMultipleEvents(generatedEvents);
				if (!addedEvents.isEmpty()){
					JOptionPane.showMessageDialog(parentFrame,
						"Successfully added " + addedEvents.size() + " AI-generated events!",
						"Success", JOptionPane.INFORMATION_MESSAGE);
				}
				else{
					JOptionPane.showMessageDialog(parentFrame,
						"Could not add events (conflicts with existing events).",
						"Warning", JOptionPane.WARNING_MESSAGE);
				}
			}
		}
	}
	public void generateEventsWithAI(AIClient aiClient, String goalDescription, int days, boolean avoidConflicts){
		generateEventsWithAI(aiClient, goalDescription, days, avoidConflicts, null);
	}
	public void generateEventsWithAI(AIClient aiClient, String goalDescription, int days, boolean avoidConflicts, Frame parentFrame){
		if (aiClient==null){
			JOptionPane.showMessageDialog(parentFrame, "AI client not configured", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		AIProgressDialog progressDialog=new AIProgressDialog(parentFrame);
		progressDialog.showDialog();
		SwingWorker<List<Event>, Void> worker=new SwingWorker<>(){
			private String errorMessage;
			@Override
			protected List<Event> doInBackground() throws Exception{
				try{
					LocalDate startDate=LocalDate.now();
					List<Event> existingEvents=avoidConflicts?getallEvents():new ArrayList<>();
					progressDialog.update("Connecting to AI service...");
					if (progressDialog.isCancelled()) return new ArrayList<>();
					progressDialog.update("Sending request for: " + goalDescription);
					if (progressDialog.isCancelled()) return new ArrayList<>();
					List<Event> events=aiClient.generateEvents(goalDescription, startDate, days, existingEvents, progressDialog);
					progressDialog.updateSuccess("Generated " + events.size() + " events");
					for (Event event : events){
						if (progressDialog.isCancelled()) return new ArrayList<>();
						progressDialog.updateEvent(event);
					}
					return events;
				}
				catch (AIException e){
					errorMessage=e.getMessage();
					progressDialog.updateError("AI Error: " + e.getMessage());
					return new ArrayList<>();
				}
				catch (Exception e){
					errorMessage="Unexpected error: "+e.getMessage();
					progressDialog.updateError("Unexpected Error: " + e.getMessage());
					return new ArrayList<>();
				}
			}
			@Override
			protected void done(){
				try{
					List<Event> generatedEvents=get();
					progressDialog.update("Processing completed");
					if (generatedEvents.isEmpty()){
						if (errorMessage!=null){
							progressDialog.updateError("Failed: " + errorMessage);
							SwingUtilities.invokeLater(() -> {
								progressDialog.closeDialog();
								JOptionPane.showMessageDialog(null,
									"Failed to generate events: "+errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
							});
						}
						else{
							progressDialog.updateWarning("No events generated");
							SwingUtilities.invokeLater(() -> {
								progressDialog.closeDialog();
								JOptionPane.showMessageDialog(null,
									"No events were generated. Please try a different goal.", "Info", JOptionPane.INFORMATION_MESSAGE);
							});
						}
						return;
					}
					progressDialog.update("Adding events to calendar...");
					List<Event> addedEvents=addMultipleEvents(generatedEvents);
					if (addedEvents.isEmpty()){
						progressDialog.updateWarning("All events conflict with existing events");
						SwingUtilities.invokeLater(() -> {
							progressDialog.closeDialog();
							JOptionPane.showMessageDialog(null,
								"Generated events conflict with existing events.", "Conflict", JOptionPane.WARNING_MESSAGE);
						});
					}
					else{
						progressDialog.updateSuccess("Successfully added " + addedEvents.size() + " events");
						SwingUtilities.invokeLater(() -> {
							progressDialog.closeDialog();
							JOptionPane.showMessageDialog(null,
								"Successfully added "+addedEvents.size()+" events!", "Success", JOptionPane.INFORMATION_MESSAGE);
						});
					}
				}
				catch (Exception e){
					progressDialog.updateError("Processing error: " + e.getMessage());
					SwingUtilities.invokeLater(() -> {
						progressDialog.closeDialog();
						JOptionPane.showMessageDialog(null, "Error processing AI response: "+e.getMessage(),  "Error", JOptionPane.ERROR_MESSAGE);
					});
				}
			}
		};
		worker.execute();
	}
	// private JDialog createProgressDialog(){
	// 	JDialog dialog=new JDialog((Frame) null, "Generating Events", true);
	// 	dialog.setLayout(new BorderLayout());
	// 	JLabel label=new JLabel("Generating events with AI... Please wait.", SwingConstants.CENTER);
	// 	label.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
	// 	dialog.add(label, BorderLayout.CENTER);
	// 	dialog.setSize(300, 150);
	// 	dialog.setLocationRelativeTo(null);
	// 	dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
	// 	return dialog;
	// }
	public boolean testAIConnection(AIClient aiClient){
		if (aiClient==null){
			return false;
		}
		try{
			return aiClient.testConnection();
		}
		catch (Exception e){
			return false;
		}
	}
	public String getAIUsageStats(AIClient aiClient){
		if (aiClient==null){
			return "AI client not configured";
		}
		AIClient.UsageStats lastStats=aiClient.getLastUsageStats();
		AIClient.UsageStats totalStats=aiClient.getTotalUsageStats();
		return String.format(
			"Last request: %d prompt + %d completion tokens\n" +
			"Total usage: %d prompt + %d completion tokens",
			lastStats.getPromptTokens(), lastStats.getCompletionTokens(),
			totalStats.getPromptTokens(), totalStats.getCompletionTokens()
		);
	}
}