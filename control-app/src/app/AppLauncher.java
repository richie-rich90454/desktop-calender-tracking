package app;

import state.AppState;
import storage.JsonStore;
import ui.CalendarFrame;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import java.util.Locale;

/*
 * Application bootstrapper.
 *
 * Responsibilities:
 * - Initialize core application components
 * - Create AppState
 * - Create CalendarModel
 * - Wire controller, storage, and UI together
 *
 * Java data types used:
 * - AppState
 * - CalendarController
 * - CalendarModel
 *
 * Java technologies involved:
 * - Object composition
 *
 * Design intent:
 * This class contains NO business logic.
 * It only wires objects together.
 */

public class AppLauncher{
    public void launch(){
        Locale.setDefault(Locale.ENGLISH);
        AppState appState=new AppState();
        JsonStore storage=new JsonStore();
        CalendarController controller=new CalendarController(appState, storage);
        SwingUtilities.invokeLater(()->{
            try{
                new CalendarFrame(controller);
            }
            catch (Exception e){
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Failed to start Calendar App: "+e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
    public void launch(String storagePath){
        Locale.setDefault(Locale.ENGLISH);
        AppState appState=new AppState();
        JsonStore storage=new JsonStore(storagePath);
        CalendarController controller=new CalendarController(appState, storage);
        SwingUtilities.invokeLater(()->{
            try{
                new CalendarFrame(controller);
            }
            catch (Exception e){
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Failed to start Calendar App: "+e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
    public CalendarController launchHeadless(){
        Locale.setDefault(Locale.ENGLISH);
        AppState appState=new AppState();
        JsonStore storage=new JsonStore();
        return new CalendarController(appState, storage);
    }
    public void launchWithComponents(AppState appState, JsonStore storage){
        Locale.setDefault(Locale.ENGLISH);
        CalendarController controller=new CalendarController(appState, storage);
        SwingUtilities.invokeLater(()->{
            try{
                new CalendarFrame(controller);
            }
            catch (Exception e){
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Failed to start Calendar App: "+e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}