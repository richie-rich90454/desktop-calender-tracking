package ui;
import app.CalendarController;
import model.Event;
import state.AppState;
import ui.EventEditor.UIComponentFactory;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Main application window.
 */
public class CalendarFrame extends JFrame implements PropertyChangeListener{
    private static final String APP_NAME="CalendarApp";
    private static final Color PRIMARY_BLUE=new Color(66, 133, 244);
    private static final Color PRIMARY_GREEN=new Color(30, 120, 83);
    private static final Color PRIMARY_RED=new Color(220, 53, 69);
    private static final Color NEUTRAL_BG=new Color(255, 255, 255);
    private static final Color NEUTRAL_LIGHT=new Color(248, 249, 250);
    private static final Color NEUTRAL_MID=new Color(233, 236, 239);
    private static final Color TEXT_PRIMARY=new Color(33, 37, 41);
    private static final Color TEXT_SECONDARY=new Color(108, 117, 125);
    private CalendarController controller;
    private AppState appState;
    private JLabel monthYearLabel;
    private JPanel calendarGrid;
    private JList<Event> eventsList;
    private DefaultListModel<Event> eventsListModel;
    private JLabel selectedDateLabel;
    private JLabel eventCountLabel;
    private JButton addEventButton;
    private JPanel viewModePanel;
    private DateTimeFormatter dateFormatter=DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH);
    private LocalDate currentSelectedDate;
    private JScrollPane calendarScrollPane;
    private JLabel statusLabel;
    private JLabel unsavedLabel;
    private JFrame audioPlayerWindow;
    public CalendarFrame(CalendarController controller){
        Locale.setDefault(Locale.ENGLISH);
        this.controller=controller;
        this.appState=controller.getAppState();
        this.currentSelectedDate=appState.getSelectedDate();
        initializeWindow();
        setupLookAndFeel();
        createComponents();
        setJMenuBar(MenuBuilder.createMenuBar(this, controller));
        setupListeners();
        updateUIFromState();
        setVisible(true);
        addWindowListener(new java.awt.event.WindowAdapter(){
            @Override
            public void windowClosing(java.awt.event.WindowEvent e){
                handleWindowClosing();
            }
        });
    }
    private void initializeWindow(){
        setTitle(APP_NAME);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        try{
            setIconImage(Toolkit.getDefaultToolkit().createImage(getClass().getResource("/icon.png")));
        }
        catch (Exception e){
            // Icon loading failed, continue without icon
        }
    }
    private void setupLookAndFeel(){
        try{
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            String[] fontPreference={"Dialog", "SansSerif", "Arial Unicode MS", "DejaVu Sans", "Noto Sans"};
            Font defaultFont=null;
            for (String fontName:fontPreference){
                if (GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ENGLISH).length > 0){
                    defaultFont=new Font(fontName, Font.PLAIN, 13);
                    break;
                }
            }
            if (defaultFont==null){
                defaultFont=new Font(Font.SANS_SERIF, Font.PLAIN, 13);
            }
            UIManager.put("Button.font", defaultFont);
            UIManager.put("Label.font", defaultFont);
            UIManager.put("TextField.font", defaultFont);
            UIManager.put("TextArea.font", defaultFont);
            UIManager.put("List.font", defaultFont);
            UIManager.put("ComboBox.font", defaultFont);
            UIManager.put("Menu.font", defaultFont);
            UIManager.put("MenuItem.font", defaultFont);
            UIManager.put("CheckBox.font", defaultFont);
            UIManager.put("RadioButton.font", defaultFont);
            UIManager.put("Panel.background", NEUTRAL_BG);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    private void createComponents(){
        monthYearLabel=new JLabel("", SwingConstants.CENTER);
        // Use logical SansSerif for broad Unicode support
        monthYearLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        monthYearLabel.setForeground(TEXT_PRIMARY);
        JButton prevMonthBtn=UIComponentFactory.createTextButton("Previous", NEUTRAL_BG, NEUTRAL_LIGHT, NEUTRAL_MID, TEXT_PRIMARY);
        JButton todayBtn=UIComponentFactory.createPrimaryButton("Today", PRIMARY_BLUE);
        JButton nextMonthBtn=UIComponentFactory.createTextButton("Next", NEUTRAL_BG, NEUTRAL_LIGHT, NEUTRAL_MID, TEXT_PRIMARY);
        viewModePanel=new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        viewModePanel.setBackground(NEUTRAL_BG);
        addViewModeButtons();
        calendarGrid=new JPanel();
        calendarGrid.setBackground(NEUTRAL_MID);
        calendarGrid.setBorder(new EmptyBorder(1, 1, 1, 1));
        eventsListModel=new DefaultListModel<>();
        eventsList=new JList<>(eventsListModel);
        eventsList.setCellRenderer(new EventListCellRenderer());
        eventsList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        eventsList.setBackground(NEUTRAL_BG);
        eventsList.setSelectionBackground(PRIMARY_BLUE);
        eventsList.setSelectionForeground(Color.WHITE);
        JScrollPane eventsScrollPane=new JScrollPane(eventsList);
        eventsScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        eventsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        selectedDateLabel=new JLabel();
        selectedDateLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        selectedDateLabel.setForeground(TEXT_PRIMARY);
        eventCountLabel=new JLabel();
        eventCountLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        eventCountLabel.setForeground(TEXT_SECONDARY);
        addEventButton=UIComponentFactory.createPrimaryButton("+ New Event", PRIMARY_BLUE);
        addEventButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        setupButtonActions(prevMonthBtn, todayBtn, nextMonthBtn);
        calendarScrollPane=new JScrollPane(calendarGrid);
        calendarScrollPane.setPreferredSize(new Dimension(600, 400));
        JPanel navigationPanel=CalendarComponents.createNavigationPanel(prevMonthBtn, todayBtn, nextMonthBtn, monthYearLabel, viewModePanel);
        JPanel contentPanel=CalendarComponents.createContentPanel(calendarGrid, eventsScrollPane, selectedDateLabel, eventCountLabel, addEventButton, controller, calendarScrollPane);
        statusLabel=new JLabel("Ready");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statusLabel.setForeground(TEXT_SECONDARY);
        unsavedLabel=new JLabel("No unsaved changes");
        unsavedLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        unsavedLabel.setForeground(TEXT_SECONDARY);
        JPanel statusBar=CalendarComponents.createStatusBar(statusLabel, unsavedLabel);
        setLayout(new BorderLayout());
        add(navigationPanel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        JButton saveButton=UIComponentFactory.createPrimaryButton("Save", PRIMARY_BLUE);
        saveButton.addActionListener(e->handleSaveAction());
        ((JPanel)((BorderLayout) navigationPanel.getLayout()).getLayoutComponent(BorderLayout.WEST)).add(saveButton);
        JButton aiPopulateBtn=null;
        for (Component comp:((JPanel)((BorderLayout) navigationPanel.getLayout()).getLayoutComponent(BorderLayout.WEST)).getComponents()){
            if (comp instanceof JButton&&"Populate by AI".equals(((JButton) comp).getText())){
                aiPopulateBtn=(JButton) comp;
                break;
            }
        }
        if (aiPopulateBtn!=null){
            aiPopulateBtn.addActionListener(e->controller.showAIConfigDialog(CalendarFrame.this));
        }
    }
    private void addViewModeButtons(){
        for (AppState.ViewMode mode:AppState.ViewMode.values()){
            JButton button=UIComponentFactory.createViewModeButton(mode.toString().replace("_VIEW", ""), mode, appState.getCurrentViewMode()==mode, PRIMARY_GREEN, NEUTRAL_BG, NEUTRAL_MID, NEUTRAL_LIGHT, TEXT_PRIMARY);
            button.addActionListener(e->{
                currentSelectedDate=appState.getSelectedDate();
                controller.setViewMode(mode);
                updateViewModeButtonStates();
                updateCalendar();
            });
            viewModePanel.add(button);
        }
    }
    private void setupButtonActions(JButton prevBtn, JButton todayBtn, JButton nextBtn){
        prevBtn.addActionListener(e->{
            switch (appState.getCurrentViewMode()){
                case DAY_VIEW: controller.navigateToPreviousDay(); break;
                case WEEK_VIEW: controller.navigateToPreviousWeek(); break;
                case MONTH_VIEW: controller.navigateToPreviousMonth(); break;
                case AGENDA_VIEW: controller.navigateToPreviousMonth(); break;
            }
        });
        nextBtn.addActionListener(e->{
            switch (appState.getCurrentViewMode()){
                case DAY_VIEW: controller.navigateToNextDay(); break;
                case WEEK_VIEW: controller.navigateToNextWeek(); break;
                case MONTH_VIEW: controller.navigateToNextMonth(); break;
                case AGENDA_VIEW: controller.navigateToNextMonth(); break;
            }
        });
        todayBtn.addActionListener(e->controller.goToToday());
        addEventButton.addActionListener(e->showAddEventDialog());
    }
    private void setupListeners(){
        appState.addPropertyChangeListener(this);
        JPopupMenu popupMenu=new JPopupMenu();
        JMenuItem deleteMenuItem=new JMenuItem("Delete Event");
        deleteMenuItem.addActionListener(e->handleEventDeletion());
        JMenuItem editMenuItem=new JMenuItem("Edit Event");
        editMenuItem.addActionListener(e->handleEventEdit());
        popupMenu.add(deleteMenuItem);
        popupMenu.add(editMenuItem);
        eventsList.addMouseListener(new java.awt.event.MouseAdapter(){
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e){
                if (SwingUtilities.isRightMouseButton(e)){
                    int index=eventsList.locationToIndex(e.getPoint());
                    if (index!=-1){
                        eventsList.setSelectedIndex(index);
                        popupMenu.show(eventsList, e.getX(), e.getY());
                    }
                }
            }
        });
    }
    @Override
    public void propertyChange(PropertyChangeEvent evt){
        SwingUtilities.invokeLater(()->updateUIFromState());
    }
    private void updateUIFromState(){
        updateCalendar();
        updateEvents();
        updateSidebar();
        updateStatusBar();
    }
    private void updateCalendar(){
        EventEditor.CalendarRenderer.renderCalendar(appState.getCurrentViewMode(), appState.getSelectedDate(), controller, calendarGrid, monthYearLabel, calendarScrollPane, currentSelectedDate, appState, this);
        updateViewModeButtonStates();
    }
    private void updateViewModeButtonStates(){
        for (Component c:viewModePanel.getComponents()){
            if (c instanceof JButton){
                JButton button=(JButton) c;
                Object mode=button.getClientProperty("viewMode");
                if (mode instanceof AppState.ViewMode){
                    AppState.ViewMode viewMode=(AppState.ViewMode) mode;
                    boolean active=appState.getCurrentViewMode()==viewMode;
                    UIComponentFactory.updateViewModeButtonStyle(button, active, PRIMARY_GREEN, NEUTRAL_BG, NEUTRAL_MID, TEXT_PRIMARY);
                }
            }
        }
    }
    private void updateEvents(){
        SwingUtilities.invokeLater(()->{
            eventsListModel.clear();
            LocalDate selectedDate=appState.getSelectedDate();
            List<Event> events=controller.getEventsbyDate(selectedDate);
            events.sort(Comparator.comparing(Event::getStartTime));
            for (Event event:events){
                eventsListModel.addElement(event);
            }
            if (events.isEmpty()){
                eventsListModel.addElement(null);
            }
        });
    }
    private void updateSidebar(){
        SwingUtilities.invokeLater(()->{
            LocalDate selectedDate=appState.getSelectedDate();
            List<Event> events=controller.getEventsbyDate(selectedDate);
            selectedDateLabel.setText(selectedDate.format(dateFormatter));
            eventCountLabel.setText(events.size()+" event"+(events.size()!=1 ? "s":""));
        });
    }

    private void updateStatusBar(){
        SwingUtilities.invokeLater(()->{
            int totalEvents=controller.getEventCount();
            List<Event> todaysEvents=controller.getEventsbyDate(LocalDate.now());
            String status=String.format("Total events: %d | Today: %d event%s | View: %s", totalEvents, todaysEvents.size(), todaysEvents.size()!=1 ? "s":"", appState.getCurrentViewMode().toString().replace("_VIEW", ""));
            statusLabel.setText(status);
            unsavedLabel.setText(controller.hasUnsavedChanges() ? "Unsaved changes":"All changes saved");
            unsavedLabel.setForeground(controller.hasUnsavedChanges() ? PRIMARY_RED:TEXT_SECONDARY);
        });
    }
    private void showAddEventDialog(){
        EventEditor.showAddEventDialog(this, controller, appState.getSelectedDate(), PRIMARY_BLUE, PRIMARY_RED, NEUTRAL_BG, NEUTRAL_MID, TEXT_PRIMARY, TEXT_SECONDARY);
    }
    private void showEditEventDialog(Event event){
        EventEditor.showEditEventDialog(this, controller, event, PRIMARY_BLUE, PRIMARY_RED, NEUTRAL_BG, NEUTRAL_MID, TEXT_PRIMARY, TEXT_SECONDARY);
    }
    public void handleWindowClosing(){
        if (controller.hasUnsavedChanges()){
            int result=JOptionPane.showConfirmDialog(this, "You have unsaved changes. Save before exiting?", "Save Changes", JOptionPane.YES_NO_CANCEL_OPTION);
            if (result==JOptionPane.YES_OPTION){
                controller.saveCalendar();
            }
            else if (result==JOptionPane.CANCEL_OPTION){
                return;
            }
        }
        if (audioPlayerWindow!=null&&audioPlayerWindow.isVisible()){
             Component contentPane=audioPlayerWindow.getContentPane();
             if (contentPane instanceof ui.AudioPlayerFrame){
                 ((ui.AudioPlayerFrame) contentPane).cleanup();
             }
             audioPlayerWindow.dispose();
         }
        System.exit(0);
    }
    private void handleSaveAction(){
        boolean saved=controller.saveCalendar();
        if (saved){
            statusLabel.setText("Calendar saved successfully!");
        }
        else{
            statusLabel.setText("Save failed!");
        }
    }
    private void handleEventDeletion(){
        Event selectedEvent=eventsList.getSelectedValue();
        if (selectedEvent!=null){
            int confirm=JOptionPane.showConfirmDialog(this, "Are you sure you want to delete '"+selectedEvent.getTitle()+"'?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (confirm==JOptionPane.YES_OPTION){
                boolean deleted=controller.deleteEvent(selectedEvent);
                if (deleted){
                    statusLabel.setText("Event deleted: "+selectedEvent.getTitle());
                    updateEvents();
                    updateSidebar();
                }
            }
        }
    }
    private void handleEventEdit(){
        Event selectedEvent=eventsList.getSelectedValue();
        if (selectedEvent!=null){
            showEditEventDialog(selectedEvent);
        }
    }
    public void showAudioPlayer(){
        if (audioPlayerWindow==null||!audioPlayerWindow.isVisible()){
            audioPlayerWindow=new JFrame("Audio Player");
            ui.AudioPlayerFrame audioPanel=new ui.AudioPlayerFrame();
            audioPlayerWindow.setContentPane(audioPanel);
            audioPlayerWindow.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            audioPlayerWindow.setSize(500, 400);
            audioPlayerWindow.setMinimumSize(new Dimension(400, 300));
            audioPlayerWindow.setLocationRelativeTo(this);
            audioPlayerWindow.setVisible(true);
            audioPlayerWindow.addWindowListener(new java.awt.event.WindowAdapter(){
                @Override
                public void windowClosed(java.awt.event.WindowEvent e){
                    audioPanel.cleanup();
                    audioPlayerWindow=null;
                }
            });
        }
        else{
            audioPlayerWindow.toFront();
        }
    }
}