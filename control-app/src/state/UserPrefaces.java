package state;
/*
 * Stores user-configurable settings.
 *
 * Responsibilities:
 * - Store UI preferences
 * - Store overlay sync preferences
 *
 * Java data types used:
 * - Map<String, String>
 * - HashMap<String, String>
 *
 * Java technologies involved:
 * - Key-value configuration
 *
 * Design intent:
 * Preferences are separated from core state.
 */

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

public class UserPrefaces {
    public static String PREF_THEME="ui.theme";
    public static String PREF_LANGUAGE="ui.language";
    public static String PREF_FIRST_DAY_OF_WEEK="ui.firstDayOfWeek";
    public static String PREF_SHOW_WEEK_NUMBERS="ui.showWeekNumbers";
    public static String PREF_DEFAULT_VIEW="ui.defaultView";
    public static String PREF_SHOW_WORK_HOURS="ui.showWorkHours";
    public static String PREF_WORK_START_HOUR="ui.workStartHour";
    public static String PREF_WORK_END_HOUR="ui.workEndHour";
    public static String PREF_REMINDER_TIME="ui.reminderTime";
    public static String PREF_SYNC_ENABLED="sync.enabled";
    public static String PREF_SYNC_FREQUENCY="sync.frequency";
    public static String PREF_LAST_SYNC_TIME="sync.lastSyncTime";
    public static String PREF_OVERLAY_ENABLED="overlay.enabled";
    public static String PREF_OVERLAY_OPACITY="overlay.opacity";
    public static String PREF_OVERLAY_POSITION="overlay.position";
    public static String PREF_AUTO_SAVE="app.autoSave";
    public static String PREF_AUTO_SAVE_INTERVAL="app.autoSaveInterval";
    public static String PREF_CONFIRM_DELETE="app.confirmDelete";
    public static String PREF_CONFIRM_EXIT="app.confirmExit";
    public static String DEFAULT_THEME="system";
    public static String DEFAULT_LANGUAGE="en";
    public static String DEFAULT_FIRST_DAY_OF_WEEK="monday";
    public static boolean DEFAULT_SHOW_WEEK_NUMBERS=true;
    public static String DEFAULT_DEFAULT_VIEW="month";
    public static boolean DEFAULT_SHOW_WORK_HOURS=true;
    public static int DEFAULT_WORK_START_HOUR=9;
    public static int DEFAULT_WORK_END_HOUR=17;
    public static int DEFAULT_REMINDER_TIME=15;
    public static boolean DEFAULT_SYNC_ENABLED=false;
    public static String DEFAULT_SYNC_FREQUENCY="hourly";
    public static boolean DEFAULT_OVERLAY_ENABLED=true;
    public static double DEFAULT_OVERLAY_OPACITY=0.8;
    public static String DEFAULT_OVERLAY_POSITION="bottom-right";
    public static boolean DEFAULT_AUTO_SAVE=true;
    public static int DEFAULT_AUTO_SAVE_INTERVAL=5;
    public static boolean DEFAULT_CONFIRM_DELETE=true;
    public static boolean DEFAULT_CONFIRM_EXIT=true;
    private Map<String, String> preferences;
    private Preferences systemPreferences;
    public UserPrefaces(){
        this.preferences=new HashMap<>();
        this.systemPreferences=Preferences.userNodeForPackage(UserPrefaces.class);
        loadDefaults();
        loadFromSystem();
    }
    public UserPrefaces(Map<String, String> initialPreferences){
        this.preferences=new HashMap<>(initialPreferences);
        this.systemPreferences=Preferences.userNodeForPackage(UserPrefaces.class);
        loadDefaults();
    }
    private void loadDefaults(){
        setDefaultIfMissing(PREF_THEME, DEFAULT_THEME);
        setDefaultIfMissing(PREF_LANGUAGE, DEFAULT_LANGUAGE);
        setDefaultIfMissing(PREF_FIRST_DAY_OF_WEEK, DEFAULT_FIRST_DAY_OF_WEEK);
        setDefaultIfMissing(PREF_SHOW_WEEK_NUMBERS, String.valueOf(DEFAULT_SHOW_WEEK_NUMBERS));
        setDefaultIfMissing(PREF_DEFAULT_VIEW, DEFAULT_DEFAULT_VIEW);
        setDefaultIfMissing(PREF_SHOW_WORK_HOURS, String.valueOf(DEFAULT_SHOW_WORK_HOURS));
        setDefaultIfMissing(PREF_WORK_START_HOUR, String.valueOf(DEFAULT_WORK_START_HOUR));
        setDefaultIfMissing(PREF_WORK_END_HOUR, String.valueOf(DEFAULT_WORK_END_HOUR));
        setDefaultIfMissing(PREF_REMINDER_TIME, String.valueOf(DEFAULT_REMINDER_TIME));
        setDefaultIfMissing(PREF_SYNC_ENABLED, String.valueOf(DEFAULT_SYNC_ENABLED));
        setDefaultIfMissing(PREF_SYNC_FREQUENCY, DEFAULT_SYNC_FREQUENCY);
        setDefaultIfMissing(PREF_OVERLAY_ENABLED, String.valueOf(DEFAULT_OVERLAY_ENABLED));
        setDefaultIfMissing(PREF_OVERLAY_OPACITY, String.valueOf(DEFAULT_OVERLAY_OPACITY));
        setDefaultIfMissing(PREF_OVERLAY_POSITION, DEFAULT_OVERLAY_POSITION);
        setDefaultIfMissing(PREF_AUTO_SAVE, String.valueOf(DEFAULT_AUTO_SAVE));
        setDefaultIfMissing(PREF_AUTO_SAVE_INTERVAL, String.valueOf(DEFAULT_AUTO_SAVE_INTERVAL));
        setDefaultIfMissing(PREF_CONFIRM_DELETE, String.valueOf(DEFAULT_CONFIRM_DELETE));
        setDefaultIfMissing(PREF_CONFIRM_EXIT, String.valueOf(DEFAULT_CONFIRM_EXIT));
    }
    private void loadFromSystem(){
        for (String key:preferences.keySet()){
            String systemValue=systemPreferences.get(key, null);
            if (systemValue!=null){
                preferences.put(key, systemValue);
            }
        }
    }
    private void setDefaultIfMissing(String key, String defaultValue){
        if (!preferences.containsKey(key)){
            preferences.put(key, defaultValue);
        }
    }
    public String getString(String key){
        return preferences.get(key);
    }
    public String getString(String key, String defaultValue){
        return preferences.getOrDefault(key, defaultValue);
    }
    public int getInt(String key){
        try{
            return Integer.parseInt(preferences.get(key));
        }
        catch (NumberFormatException|NullPointerException e){
            return 0;
        }
    }
    public int getInt(String key, int defaultValue){
        try{
            return Integer.parseInt(preferences.get(key));
        }
        catch (NumberFormatException|NullPointerException e){
            return defaultValue;
        }
    }
    public boolean getBoolean(String key){
        String value=preferences.get(key);
        if (value==null){
            return false;
        }
        return Boolean.parseBoolean(value);
    }
    public boolean getBoolean(String key, boolean defaultValue){
        String value=preferences.get(key);
        if (value==null){
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    public double getDouble(String key){
        try{
            return Double.parseDouble(preferences.get(key));
        }
        catch (NumberFormatException|NullPointerException e){
            return 0.0;
        }
    }
    public double getDouble(String key, double defaultValue){
        try{
            return Double.parseDouble(preferences.get(key));
        }
        catch (NumberFormatException|NullPointerException e){
            return defaultValue;
        }
    }
    public void set(String key, String value){
        preferences.put(key, value);
        systemPreferences.put(key, value);
    }
    public void setInt(String key, int value){
        set(key, String.valueOf(value));
    }
    public void setBoolean(String key, boolean value){
        set(key, String.valueOf(value));
    }
    public void setDouble(String key, double value){
        set(key, String.valueOf(value));
    }
    public void remove(String key){
        preferences.remove(key);
        systemPreferences.remove(key);
    }
    public boolean contains(String key){
        return preferences.containsKey(key);
    }
    public void clear(){
        preferences.clear();
        try{
            systemPreferences.clear();
        }
        catch (Exception e){

        }
    }
    public Map<String, String> getAllPreferences(){
        return new HashMap<>(preferences);
    }
    public void setAllPreferences(Map<String, String> newPreferences){
        preferences.clear();
        preferences.putAll(newPreferences);
        for (Map.Entry<String, String> entry:newPreferences.entrySet()){
            systemPreferences.put(entry.getKey(), entry.getValue());
        }
    }
    public String getTheme(){
        return getString(PREF_THEME, DEFAULT_THEME);
    }
    public void setTheme(String theme){
        set(PREF_THEME, theme);
    }
    public String getLanguage(){
        return getString(PREF_LANGUAGE, DEFAULT_LANGUAGE);
    }
    public void setLanguage(String language){
        set(PREF_LANGUAGE, language);
    }
    public String getFirstDayOfWeek(){
        return getString(PREF_FIRST_DAY_OF_WEEK, DEFAULT_FIRST_DAY_OF_WEEK);
    }
    public void setFirstDayOfWeek(String day){
        set(PREF_FIRST_DAY_OF_WEEK, day);
    }
    public boolean getShowWeekNumbers(){
        return getBoolean(PREF_SHOW_WEEK_NUMBERS, DEFAULT_SHOW_WEEK_NUMBERS);
    }
    public void setShowWeekNumbers(boolean show){
        setBoolean(PREF_SHOW_WEEK_NUMBERS, show);
    }
    public String getDefaultView(){
        return getString(PREF_DEFAULT_VIEW, DEFAULT_DEFAULT_VIEW);
    }
    public void setDefaultView(String view){
        set(PREF_DEFAULT_VIEW, view);
    }
    public boolean getShowWorkHours(){
        return getBoolean(PREF_SHOW_WORK_HOURS, DEFAULT_SHOW_WORK_HOURS);
    }
    public void setShowWorkHours(boolean show){
        setBoolean(PREF_SHOW_WORK_HOURS, show);
    }
    public int getWorkStartHour(){
        return getInt(PREF_WORK_START_HOUR, DEFAULT_WORK_START_HOUR);
    }
    public void setWorkStartHour(int hour){
        setInt(PREF_WORK_START_HOUR, hour);
    }
    public int getWorkEndHour(){
        return getInt(PREF_WORK_END_HOUR, DEFAULT_WORK_END_HOUR);
    }
    public void setWorkEndHour(int hour){
        setInt(PREF_WORK_END_HOUR, hour);
    }
    public int getReminderTime(){
        return getInt(PREF_REMINDER_TIME, DEFAULT_REMINDER_TIME);
    }
    public void setReminderTime(int minutes){
        setInt(PREF_REMINDER_TIME, minutes);
    }
    public boolean getSyncEnabled(){
        return getBoolean(PREF_SYNC_ENABLED, DEFAULT_SYNC_ENABLED);
    }
    public void setSyncEnabled(boolean enabled){
        setBoolean(PREF_SYNC_ENABLED, enabled);
    }
    public String getSyncFrequency(){
        return getString(PREF_SYNC_FREQUENCY, DEFAULT_SYNC_FREQUENCY);
    }
    public void setSyncFrequency(String frequency){
        set(PREF_SYNC_FREQUENCY, frequency);
    }
    public boolean getOverlayEnabled(){
        return getBoolean(PREF_OVERLAY_ENABLED, DEFAULT_OVERLAY_ENABLED);
    }
    public void setOverlayEnabled(boolean enabled){
        setBoolean(PREF_OVERLAY_ENABLED, enabled);
    }
    public double getOverlayOpacity(){
        return getDouble(PREF_OVERLAY_OPACITY, DEFAULT_OVERLAY_OPACITY);
    }
    public void setOverlayOpacity(double opacity){
        setDouble(PREF_OVERLAY_OPACITY, opacity);
    }
    public String getOverlayPosition(){
        return getString(PREF_OVERLAY_POSITION, DEFAULT_OVERLAY_POSITION);
    }
    public void setOverlayPosition(String position){
        set(PREF_OVERLAY_POSITION, position);
    }
    public boolean getAutoSave(){
        return getBoolean(PREF_AUTO_SAVE, DEFAULT_AUTO_SAVE);
    }
    public void setAutoSave(boolean autoSave){
        setBoolean(PREF_AUTO_SAVE, autoSave);
    }
    public int getAutoSaveInterval(){
        return getInt(PREF_AUTO_SAVE_INTERVAL, DEFAULT_AUTO_SAVE_INTERVAL);
    }
    public void setAutoSaveInterval(int minutes){
        setInt(PREF_AUTO_SAVE_INTERVAL, minutes);
    }
    public boolean getConfirmDelete(){
        return getBoolean(PREF_CONFIRM_DELETE, DEFAULT_CONFIRM_DELETE);
    }
    public void setConfirmDelete(boolean confirm){
        setBoolean(PREF_CONFIRM_DELETE, confirm);
    }
    public boolean getConfirmExit(){
        return getBoolean(PREF_CONFIRM_EXIT, DEFAULT_CONFIRM_EXIT);
    }
    public void setConfirmExit(boolean confirm){
        setBoolean(PREF_CONFIRM_EXIT, confirm);
    }
    public void saveToSystem(){
        for (Map.Entry<String, String> entry:preferences.entrySet()){
            systemPreferences.put(entry.getKey(), entry.getValue());
        }
    }
    public void loadFromSystem(boolean keepExisting){
        if (!keepExisting){
            preferences.clear();
        }
        try{
            String[] keys=systemPreferences.keys();
            for (String key:keys){
                preferences.put(key, systemPreferences.get(key, ""));
            }
        }
        catch (Exception e){

        }
        loadDefaults();
    }
    public Map<String, String> exportPreferences(){
        return new HashMap<>(preferences);
    }
    public void importPreferences(Map<String, String> importedPrefs){
        preferences.clear();
        preferences.putAll(importedPrefs);
        saveToSystem();
    }
    @Override
    public String toString(){
        return "UserPrefaces{"+"preferences="+preferences.size()+" entries"+", theme='"+getTheme()+"'"+", language='"+getLanguage()+"'"+", syncEnabled="+getSyncEnabled()+", overlayEnabled="+getOverlayEnabled()+'}';
    }
    public String getPreferencesSummary(){
        StringBuilder stringBuilder=new StringBuilder();
        stringBuilder.append("User Preferences:\n");
        stringBuilder.append("  Theme: ").append(getTheme()).append("\n");
        stringBuilder.append("  Language: ").append(getLanguage()).append("\n");
        stringBuilder.append("  Default View: ").append(getDefaultView()).append("\n");
        stringBuilder.append("  Sync Enabled: ").append(getSyncEnabled()).append("\n");
        stringBuilder.append("  Overlay Enabled: ").append(getOverlayEnabled()).append("\n");
        stringBuilder.append("  Auto-save: ").append(getAutoSave()).append("\n");
        stringBuilder.append("  Confirm Delete: ").append(getConfirmDelete()).append("\n");
        stringBuilder.append("  Total Preferences: ").append(preferences.size()).append("\n");
        return stringBuilder.toString();
    }
}