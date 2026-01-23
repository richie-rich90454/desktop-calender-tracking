package ui;

import app.CalendarController;
import model.Event;
import state.AppState;
import ui.EventEditor.UIComponentFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Main application window.
 */
public class CalendarFrame extends JFrame implements PropertyChangeListener {
    private static String APP_NAME="CalendarApp";
    private static Color PRIMARY_BLUE=new Color(66, 133, 244);
    private static Color PRIMARY_GREEN=new Color(30, 120, 83);
    private static Color PRIMARY_RED=new Color(220, 53, 69);
    private static Color NEUTRAL_BG=new Color(255, 255, 255);
    private static Color NEUTRAL_LIGHT=new Color(248, 249, 250);
    private static Color NEUTRAL_MID=new Color(233, 236, 239);
    private static Color NEUTRAL_DARK=new Color(222, 226, 230);
    private static Color TEXT_PRIMARY=new Color(33, 37, 41);
    private static Color TEXT_SECONDARY=new Color(108, 117, 125);
    private static Color CALENDAR_TODAY=new Color(219, 237, 255);
    private static Color CALENDAR_WEEKEND=new Color(250, 250, 252);
    private static Color CALENDAR_SELECTED=new Color(240, 248, 255);
    private static Color DISABLED_TEXT=new Color(134, 142, 150);
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
    private DateTimeFormatter monthYearFormatter=DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private DateTimeFormatter dateFormatter=DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH);
    private static DateTimeFormatter timeFormatter=DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private DateTimeFormatter dayFormatter=DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);
    private DateTimeFormatter shortDayFormatter=DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    private LocalDate currentSelectedDate;
    private JScrollPane calendarScrollPane;
    private JLabel statusLabel;
    private JLabel unsavedLabel;
    public CalendarFrame(CalendarController controller){
        Locale.setDefault(Locale.ENGLISH);
        this.controller=controller;
        this.appState=controller.getAppState();
        this.currentSelectedDate=appState.getSelectedDate();
        initializeWindow();
        setupLookAndFeel();
        createComponents();
        createMenuBar();
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

        }
    }
    private void setupLookAndFeel(){
        try{
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            Font defaultFont=new Font("Segoe UI", Font.PLAIN, 13);
            if (!"Segoe UI".equals(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()[0])){
                defaultFont=new Font("SansSerif", Font.PLAIN, 13);
            }
            UIManager.put("Button.font", defaultFont);
            UIManager.put("Label.font", defaultFont);
            UIManager.put("TextField.font", defaultFont);
            UIManager.put("List.font", defaultFont);
            UIManager.put("Panel.background", NEUTRAL_BG);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    private void createComponents(){
        monthYearLabel=new JLabel("", SwingConstants.CENTER);
        monthYearLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        monthYearLabel.setForeground(TEXT_PRIMARY);
        JButton prevMonthBtn=EventEditor.UIComponentFactory.createTextButton("Previous", NEUTRAL_BG, NEUTRAL_LIGHT, NEUTRAL_MID, TEXT_PRIMARY);
        JButton todayBtn=EventEditor.UIComponentFactory.createPrimaryButton("Today", PRIMARY_BLUE);
        JButton nextMonthBtn=EventEditor.UIComponentFactory.createTextButton("Next", NEUTRAL_BG, NEUTRAL_LIGHT, NEUTRAL_MID, TEXT_PRIMARY);
        viewModePanel=new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        viewModePanel.setBackground(NEUTRAL_BG);
        addViewModeButtons();
        calendarGrid=new JPanel();
        calendarGrid.setBackground(NEUTRAL_MID);
        calendarGrid.setBorder(new EmptyBorder(1, 1, 1, 1));
        eventsListModel=new DefaultListModel<>();
        eventsList=new JList<Event>(eventsListModel);
        eventsList.setCellRenderer(new EventListCellRenderer());
        eventsList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        eventsList.setBackground(NEUTRAL_BG);
        eventsList.setSelectionBackground(PRIMARY_BLUE);
        eventsList.setSelectionForeground(Color.WHITE);
        JScrollPane eventsScrollPane=new JScrollPane(eventsList);
        eventsScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        eventsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        selectedDateLabel=new JLabel();
        selectedDateLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        selectedDateLabel.setForeground(TEXT_PRIMARY);
        eventCountLabel=new JLabel();
        eventCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        eventCountLabel.setForeground(TEXT_SECONDARY);
        addEventButton=EventEditor.UIComponentFactory.createPrimaryButton("+ New Event", PRIMARY_BLUE);
        addEventButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        setupButtonActions(prevMonthBtn, todayBtn, nextMonthBtn);
        JPanel navigationPanel=createNavigationPanel(prevMonthBtn, todayBtn, nextMonthBtn);
        JPanel contentPanel=createContentPanel(eventsScrollPane);
        JPanel statusBar=createStatusBar();
        setLayout(new BorderLayout());
        add(navigationPanel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        JButton saveButton=EventEditor.UIComponentFactory.createPrimaryButton("Save", PRIMARY_BLUE);
        saveButton.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                handleSaveAction();
            }
        });
        ((JPanel)((BorderLayout)navigationPanel.getLayout()).getLayoutComponent(BorderLayout.WEST)).add(saveButton);
    }
    private void addViewModeButtons(){
        for (AppState.ViewMode mode:AppState.ViewMode.values()){
            JButton button=EventEditor.UIComponentFactory.createViewModeButton(
                mode.toString().replace("_VIEW", ""),
                mode,
                appState.getCurrentViewMode()==mode,
                PRIMARY_GREEN,
                NEUTRAL_BG,
                NEUTRAL_MID,
                NEUTRAL_LIGHT,
                TEXT_PRIMARY
            );
            button.addActionListener(new java.awt.event.ActionListener(){
                public void actionPerformed(java.awt.event.ActionEvent e){
                    currentSelectedDate=appState.getSelectedDate();
                    controller.setViewMode(mode);
                    updateViewModeButtonStates();
                    updateCalendar();
                }
            });
            viewModePanel.add(button);
        }
    }
    private void setupButtonActions(JButton prevBtn, JButton todayBtn, JButton nextBtn){
        prevBtn.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                switch (appState.getCurrentViewMode()){
                    case DAY_VIEW:
                        controller.navigateToPreviousDay();
                        break;
                    case WEEK_VIEW:
                        controller.navigateToPreviousWeek();
                        break;
                    case MONTH_VIEW:
                        controller.navigateToPreviousMonth();
                        break;
                    case AGENDA_VIEW:
                        controller.navigateToPreviousMonth();
                        break;
                }
            }
        });
        nextBtn.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                switch (appState.getCurrentViewMode()){
                    case DAY_VIEW:
                        controller.navigateToNextDay();
                        break;
                    case WEEK_VIEW:
                        controller.navigateToNextWeek();
                        break;
                    case MONTH_VIEW:
                        controller.navigateToNextMonth();
                        break;
                    case AGENDA_VIEW:
                        controller.navigateToNextMonth();
                        break;
                }
            }
        });
        todayBtn.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                controller.goToToday();
            }
        });
        addEventButton.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                showAddEventDialog();
            }
        });
    }
    private JPanel createNavigationPanel(JButton prevBtn, JButton todayBtn, JButton nextBtn){
        JPanel navigationPanel=new JPanel(new BorderLayout(15, 0));
        navigationPanel.setBackground(NEUTRAL_BG);
        navigationPanel.setBorder(new EmptyBorder(12, 20, 12, 20));
        JPanel navButtons=new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        navButtons.setBackground(NEUTRAL_BG);
        navButtons.add(prevBtn);
        navButtons.add(todayBtn);
        navButtons.add(nextBtn);
        navigationPanel.add(navButtons, BorderLayout.WEST);
        navigationPanel.add(monthYearLabel, BorderLayout.CENTER);
        navigationPanel.add(viewModePanel, BorderLayout.EAST);
        return navigationPanel;
    }
    private JPanel createContentPanel(JScrollPane eventsScrollPane){
        JPanel contentPanel=new JPanel(new BorderLayout(20, 0));
        contentPanel.setBackground(NEUTRAL_BG);
        contentPanel.setBorder(new EmptyBorder(0, 20, 20, 20));
        JPanel calendarContainer=new JPanel(new BorderLayout());
        calendarContainer.setBackground(NEUTRAL_BG);
        calendarContainer.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(NEUTRAL_DARK, 1),
            new EmptyBorder(15, 15, 15, 15)
        ));
        calendarScrollPane=new JScrollPane(calendarGrid);
        calendarScrollPane.setBorder(new EmptyBorder(10, 0, 0, 0));
        calendarScrollPane.getViewport().setBackground(NEUTRAL_MID);
        calendarContainer.add(calendarScrollPane, BorderLayout.CENTER);
        JPanel sidebarPanel=new JPanel(new BorderLayout(0, 20));
        sidebarPanel.setBackground(NEUTRAL_BG);
        sidebarPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        sidebarPanel.setPreferredSize(new Dimension(320, 0));
        JPanel sidebarHeader=new JPanel(new BorderLayout(0, 8));
        sidebarHeader.setBackground(NEUTRAL_BG);
        sidebarHeader.setBorder(new EmptyBorder(0, 0, 15, 0));
        sidebarHeader.add(selectedDateLabel, BorderLayout.NORTH);
        sidebarHeader.add(eventCountLabel, BorderLayout.SOUTH);
        JLabel upcomingLabel=new JLabel("Events for Selected Day");
        upcomingLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        upcomingLabel.setForeground(TEXT_PRIMARY);
        upcomingLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        JPanel eventsPanel=new JPanel(new BorderLayout());
        eventsPanel.setBackground(NEUTRAL_BG);
        eventsPanel.add(upcomingLabel, BorderLayout.NORTH);
        eventsPanel.add(eventsScrollPane, BorderLayout.CENTER);
        sidebarPanel.add(sidebarHeader, BorderLayout.NORTH);
        sidebarPanel.add(eventsPanel, BorderLayout.CENTER);
        sidebarPanel.add(addEventButton, BorderLayout.SOUTH);
        contentPanel.add(calendarContainer, BorderLayout.CENTER);
        contentPanel.add(sidebarPanel, BorderLayout.EAST);
        return contentPanel;
    }
    private JPanel createStatusBar(){
        JPanel statusBar=new JPanel(new BorderLayout());
        statusBar.setBackground(NEUTRAL_LIGHT);
        statusBar.setBorder(new EmptyBorder(6, 20, 6, 20));
        statusLabel=new JLabel("Ready");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(TEXT_SECONDARY);
        unsavedLabel=new JLabel("No unsaved changes");
        unsavedLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        unsavedLabel.setForeground(TEXT_SECONDARY);
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(unsavedLabel, BorderLayout.EAST);
        return statusBar;
    }
    private void setupListeners(){
        appState.addPropertyChangeListener(this);
        JPopupMenu popupMenu=new JPopupMenu();
        JMenuItem deleteMenuItem=new JMenuItem("Delete Event");
        deleteMenuItem.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                handleEventDeletion();
            }
        });
        popupMenu.add(deleteMenuItem);
        JMenuItem editMenuItem=new JMenuItem("Edit Event");
        editMenuItem.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                handleEventEdit();
            }
        });
        popupMenu.add(editMenuItem);
        eventsList.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e){
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
        SwingUtilities.invokeLater(new Runnable(){
            public void run(){
                updateUIFromState();
            }
        });
    }
    private void updateUIFromState(){
        updateCalendar();
        updateEvents();
        updateSidebar();
        updateStatusBar();
    }
    private void updateCalendar(){
        EventEditor.CalendarRenderer.renderCalendar(
            appState.getCurrentViewMode(),
            appState.getSelectedDate(),
            controller,
            calendarGrid,
            monthYearLabel,
            calendarScrollPane,
            currentSelectedDate,
            appState,
            this
        );
        updateViewModeButtonStates();
    }
    private void createMenuBar(){
        JMenuBar menuBar=new JMenuBar();
        JMenu fileMenu=new JMenu("File");
        fileMenu.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JMenuItem saveMenuItem=new JMenuItem("Save");
        saveMenuItem.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                handleSaveAction();
            }
        });
        JMenuItem preferencesMenuItem=new JMenuItem("Preferences");
        preferencesMenuItem.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                PreferencesDialog.showDialog(CalendarFrame.this);
            }
        });
        JMenuItem exitMenuItem=new JMenuItem("Exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                handleWindowClosing();
            }
        });
        fileMenu.add(saveMenuItem);
        fileMenu.addSeparator();
        fileMenu.add(preferencesMenuItem);
        fileMenu.addSeparator();
        fileMenu.add(exitMenuItem);
        JMenu helpMenu=new JMenu("Help");
        helpMenu.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JMenuItem aboutMenuItem=new JMenuItem("About");
        aboutMenuItem.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                showAboutDialog();
            }
        });
        JMenuItem helpMenuItem=new JMenuItem("Help Contents");
        helpMenuItem.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                showHelpDialog();
            }
        });
        helpMenu.add(helpMenuItem);
        helpMenu.addSeparator();
        helpMenu.add(aboutMenuItem);
        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
    }
    private void showAboutDialog(){
        JDialog aboutDialog=new JDialog(this, "About Calendar App", true);
        aboutDialog.setSize(400, 300);
        aboutDialog.setLocationRelativeTo(this);
        JPanel contentPanel=new JPanel(new BorderLayout());
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        contentPanel.setBackground(NEUTRAL_BG);
        JLabel titleLabel=new JLabel("Calendar App", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(TEXT_PRIMARY);
        JLabel versionLabel=new JLabel("Version 1.0.0", SwingConstants.CENTER);
        versionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        versionLabel.setForeground(TEXT_SECONDARY);
        versionLabel.setBorder(new EmptyBorder(10, 0, 20, 0));
        JTextArea aboutText=new JTextArea();
        aboutText.setText("Calendar Application\n\n"+"A simple calendar app for managing events.\n\n"+"Features:\n"+"• Add, edit, delete events\n"+"• Multiple view modes (Day, Week, Month, Agenda)\n"+"• Save/Load calendar data\n"+"• Intuitive user interface\n\n"+"© 2026 richie-rich90454.");
        aboutText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        aboutText.setForeground(TEXT_PRIMARY);
        aboutText.setBackground(NEUTRAL_BG);
        aboutText.setEditable(false);
        aboutText.setLineWrap(true);
        aboutText.setWrapStyleWord(true);
        aboutText.setBorder(new EmptyBorder(10, 10, 10, 10));
        JScrollPane scrollPane=new JScrollPane(aboutText);
        scrollPane.setBorder(null);
        JButton closeButton=UIComponentFactory.createTextButton("Close", NEUTRAL_BG, NEUTRAL_LIGHT, NEUTRAL_MID, TEXT_PRIMARY);
        closeButton.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                aboutDialog.dispose();
            }
        });
        JPanel buttonPanel=new JPanel();
        buttonPanel.setBackground(NEUTRAL_BG);
        buttonPanel.add(closeButton);
        JPanel headerPanel=new JPanel(new BorderLayout());
        headerPanel.setBackground(NEUTRAL_BG);
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(versionLabel, BorderLayout.CENTER);
        contentPanel.add(headerPanel, BorderLayout.NORTH);
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        aboutDialog.setContentPane(contentPanel);
        aboutDialog.setVisible(true);
    }
    private void showHelpDialog(){
        JOptionPane.showMessageDialog(this, "<html><div style='width:300px;'><h3>Calendar App Help</h3>"+"<p><b>Adding Events:</b> Click '+ New Event' button or double-click on a day.</p>"+"<p><b>Editing Events:</b> Double-click an event or right-click and select 'Edit'.</p>"+"<p><b>Deleting Events:</b> Right-click an event and select 'Delete'.</p>"+"<p><b>Navigation:</b> Use Previous/Next buttons or click on dates in the calendar.</p>"+"<p><b>View Modes:</b> Switch between Day, Week, Month, and Agenda views.</p>"+"<p><b>Saving:</b> Click the Save button to save your calendar.</p></div></html>","Help", JOptionPane.INFORMATION_MESSAGE);
    }
    private void updateViewModeButtonStates(){
        for (Component c:viewModePanel.getComponents()){
            if (c instanceof JButton){
                JButton button=(JButton) c;
                Object mode=button.getClientProperty("viewMode");
                if (mode instanceof AppState.ViewMode){
                    AppState.ViewMode viewMode=(AppState.ViewMode) mode;
                    boolean active=appState.getCurrentViewMode()==viewMode;
                    EventEditor.UIComponentFactory.updateViewModeButtonStyle(
                        button,
                        active,
                        PRIMARY_GREEN,
                        NEUTRAL_BG,
                        NEUTRAL_MID,
                        TEXT_PRIMARY
                    );
                }
            }
        }
    }
    private void updateEvents(){
        SwingUtilities.invokeLater(new Runnable(){
            public void run(){
                eventsListModel.clear();
                LocalDate selectedDate=appState.getSelectedDate();
                List<Event> events=controller.getEventsbyDate(selectedDate);
                events.sort(new Comparator<Event>(){
                    public int compare(Event e1, Event e2){
                        return e1.getStartTime().compareTo(e2.getStartTime());
                    }
                });
                for (Event event:events){
                    eventsListModel.addElement(event);
                }
                if (events.isEmpty()){
                    eventsListModel.addElement(null);
                }
            }
        });
    }
    private void updateSidebar(){
        SwingUtilities.invokeLater(new Runnable(){
            public void run(){
                LocalDate selectedDate=appState.getSelectedDate();
                List<Event> events=controller.getEventsbyDate(selectedDate);
                selectedDateLabel.setText(selectedDate.format(dateFormatter));
                eventCountLabel.setText(events.size()+" event"+(events.size()!=1?"s":""));
            }
        });
    }
    private void updateStatusBar(){
        SwingUtilities.invokeLater(new Runnable(){
            public void run(){
                int totalEvents=controller.getEventCount();
                List<Event> todaysEvents=controller.getEventsbyDate(LocalDate.now());
                String status=String.format("Total events: %d | Today: %d event%s | View: %s",
                    totalEvents,
                    todaysEvents.size(),
                    todaysEvents.size()!=1?"s":"",
                    appState.getCurrentViewMode().toString().replace("_VIEW", "")
                );
                statusLabel.setText(status);
                unsavedLabel.setText(controller.hasUnsavedChanges()?"Unsaved changes":"All changes saved");
                unsavedLabel.setForeground(controller.hasUnsavedChanges()?PRIMARY_RED:TEXT_SECONDARY);
            }
        });
    }
    private void showAddEventDialog(){
        EventEditor.showAddEventDialog(this, controller, appState.getSelectedDate(), PRIMARY_BLUE, PRIMARY_RED, NEUTRAL_BG, NEUTRAL_MID, TEXT_PRIMARY, TEXT_SECONDARY);
    }
    private void showEditEventDialog(Event event){
        EventEditor.showEditEventDialog(this, controller, event, PRIMARY_BLUE, PRIMARY_RED, NEUTRAL_BG, NEUTRAL_MID, TEXT_PRIMARY, TEXT_SECONDARY);
    }
    private void handleWindowClosing(){
        if (controller.hasUnsavedChanges()){
            int result=JOptionPane.showConfirmDialog(
                this,
                "You have unsaved changes. Save before exiting?",
                "Save Changes",
                JOptionPane.YES_NO_CANCEL_OPTION
            );
            if (result==JOptionPane.YES_OPTION){
                controller.saveCalendar();
            }
            else if (result==JOptionPane.CANCEL_OPTION){
                return;
            }
        }
        System.exit(0);
    }
    private void handleSaveAction(){
        boolean saved=controller.saveCalendar();
        if (saved){
            statusLabel.setText("Calendar saved successfully!");
            JOptionPane.showMessageDialog(
                this,
                "Calendar saved to: "+controller.getStorage().getStoragePath(),
                "Save Successful",
                JOptionPane.INFORMATION_MESSAGE
            );
        }
        else{
            statusLabel.setText("Save failed!");
            JOptionPane.showMessageDialog(
                this,
                "Failed to save calendar",
                "Save Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
    private void handleEventDeletion(){
        Event selectedEvent=eventsList.getSelectedValue();
        if (selectedEvent!=null){
            int confirm=JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete '"+selectedEvent.getTitle()+"'?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION
            );
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
    private static class EventListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus){
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value==null){
                setText("No events scheduled for this day");
                setForeground(DISABLED_TEXT);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(new Font("Segoe UI", Font.ITALIC, 12));
                setIcon(null);
                setBorder(new EmptyBorder(20, 10, 20, 10));
            }
            else if (value instanceof Event){
                Event event=(Event) value;
                String startTime=event.getStartTime().toLocalTime().format(timeFormatter);
                String endTime=event.getEndTime().toLocalTime().format(timeFormatter);
                setText(String.format("<html><b>%s</b><br/><font color='#6C757D' size='-1'>%s-%s</font></html>", event.getTitle(), startTime, endTime));
                setIcon(new EventDotIcon(PRIMARY_GREEN));
                setBorder(new EmptyBorder(10, 10, 10, 10));
                setFont(new Font("Segoe UI", Font.PLAIN, 13));
            }
            return this;
        }
    }
    private static class EventDotIcon implements Icon {
        private Color color;
        private static int SIZE=8;
        public EventDotIcon(Color color){
            this.color=color;
        }
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y){
            Graphics2D g2=(Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x, y+(c.getHeight()-SIZE)/2, SIZE, SIZE);
        }
        @Override
        public int getIconWidth(){
            return SIZE+6;
        }
        @Override
        public int getIconHeight(){
            return SIZE;
        }
    }
}