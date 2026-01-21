package ui;
import app.CalendarController;
import model.Event;
import state.AppState;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
/**
 * Main application window.
 *
 * Responsibilities:
 *-Display calendar overview
 *-Display current and upcoming events
 *
 * Java data types used:
 *-JFrame
 *-JPanel
 *
 * Java technologies involved:
 *-Swing (Java 8 compatible)
 *-PropertyChangeListener for reactive updates
 *
 * Design intent:
 * UI renders data, never owns it.
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
    private static Color BUTTON_TEXT_ON_DARK=Color.WHITE;
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
        setupListeners();
        updateUIFromState();
        setVisible(true);
        addWindowListener(new java.awt.event.WindowAdapter(){
            @Override
            public void windowClosing(java.awt.event.WindowEvent e){
                if (controller.hasUnsavedChanges()){
                    int result=JOptionPane.showConfirmDialog(CalendarFrame.this,"You have unsaved changes. Save before exiting?","Save Changes",
                        JOptionPane.YES_NO_CANCEL_OPTION);
                    if (result==JOptionPane.YES_OPTION){
                        controller.saveCalendar();
                    }
                    else if (result==JOptionPane.CANCEL_OPTION){
                        return;
                    }
                }
                System.exit(0);
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
        JButton prevMonthBtn=createTextButton("Previous");
        JButton todayBtn=createPrimaryButton("Today");
        JButton nextMonthBtn=createTextButton("Next");
        viewModePanel=new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        viewModePanel.setBackground(NEUTRAL_BG);
        JButton dayViewBtn=createViewModeButton("Day", AppState.ViewMode.DAY_VIEW);
        JButton weekViewBtn=createViewModeButton("Week", AppState.ViewMode.WEEK_VIEW);
        JButton monthViewBtn=createViewModeButton("Month", AppState.ViewMode.MONTH_VIEW);
        JButton agendaViewBtn=createViewModeButton("Agenda", AppState.ViewMode.AGENDA_VIEW);
        viewModePanel.add(dayViewBtn);
        viewModePanel.add(weekViewBtn);
        viewModePanel.add(monthViewBtn);
        viewModePanel.add(agendaViewBtn);
        calendarGrid=new JPanel();
        calendarGrid.setBackground(NEUTRAL_MID);
        calendarGrid.setBorder(new EmptyBorder(1, 1, 1, 1));
        eventsListModel=new DefaultListModel<>();
        eventsList=new JList<>(eventsListModel);
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
        addEventButton=createPrimaryButton("+ New Event");
        addEventButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        prevMonthBtn.addActionListener(e->{
            switch (appState.getCurrentViewMode()){
                case DAY_VIEW: controller.navigateToPreviousDay();break;
                case WEEK_VIEW: controller.navigateToPreviousWeek();break;
                case MONTH_VIEW: controller.navigateToPreviousMonth();break;
                case AGENDA_VIEW: controller.navigateToPreviousMonth();break;
            }
        });
        nextMonthBtn.addActionListener(e->{
            switch (appState.getCurrentViewMode()){
                case DAY_VIEW: controller.navigateToNextDay();break;
                case WEEK_VIEW: controller.navigateToNextWeek();break;
                case MONTH_VIEW: controller.navigateToNextMonth();break;
                case AGENDA_VIEW: controller.navigateToNextMonth();break;
            }
        });
        todayBtn.addActionListener(e->controller.goToToday());
        addEventButton.addActionListener(e->showAddEventDialog());
        JPanel navigationPanel=new JPanel(new BorderLayout(15, 0));
        navigationPanel.setBackground(NEUTRAL_BG);
        navigationPanel.setBorder(new EmptyBorder(12, 20, 12, 20));
        JPanel navButtons=new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        navButtons.setBackground(NEUTRAL_BG);
        navButtons.add(prevMonthBtn);
        navButtons.add(todayBtn);
        navButtons.add(nextMonthBtn);
        navigationPanel.add(navButtons, BorderLayout.WEST);
        navigationPanel.add(monthYearLabel, BorderLayout.CENTER);
        navigationPanel.add(viewModePanel, BorderLayout.EAST);
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
        setLayout(new BorderLayout());
        add(navigationPanel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        JButton saveButton=createPrimaryButton("Save");
        saveButton.addActionListener(e->{
            boolean saved=controller.saveCalendar();
            if (saved){
                statusLabel.setText("Calendar saved successfully!");
                JOptionPane.showMessageDialog(this, "Calendar saved to: "+controller.getStorage().getStoragePath(),"Save Successful", JOptionPane.INFORMATION_MESSAGE);
            }
            else{
                statusLabel.setText("Save failed!");
                JOptionPane.showMessageDialog(this, "Failed to save calendar", "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        navButtons.add(saveButton);
    }
    private JButton createTextButton(String text){
        JButton button=new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        button.setBackground(NEUTRAL_BG);
        button.setForeground(TEXT_PRIMARY);
        button.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(NEUTRAL_MID, 1),
            new EmptyBorder(6, 12, 6, 12)
        ));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseEntered(MouseEvent e){
                button.setBackground(NEUTRAL_LIGHT);
            }
            @Override
            public void mouseExited(MouseEvent e){
                button.setBackground(NEUTRAL_BG);
            }
        });
        return button;
    }
    private JButton createPrimaryButton(String text){
        JButton button=new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        button.setBackground(PRIMARY_BLUE);
        button.setForeground(BUTTON_TEXT_ON_DARK);
        button.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(PRIMARY_BLUE, 1),
            new EmptyBorder(8, 16, 8, 16)
        ));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseEntered(MouseEvent e){
                button.setBackground(PRIMARY_BLUE.darker());
                button.setForeground(BUTTON_TEXT_ON_DARK);
                button.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(PRIMARY_BLUE.darker(), 1),
                    new EmptyBorder(8, 16, 8, 16)
                ));
            }
            @Override
            public void mouseExited(MouseEvent e){
                button.setBackground(PRIMARY_BLUE);
                button.setForeground(BUTTON_TEXT_ON_DARK);
                button.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(PRIMARY_BLUE, 1),
                    new EmptyBorder(8, 16, 8, 16)
                ));
            }
        });
        return button;
    }
    private JButton createViewModeButton(String text, AppState.ViewMode viewMode){
        JButton button=new JButton(text);
        button.putClientProperty("viewMode", viewMode);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        boolean active=appState.getCurrentViewMode()==viewMode;
        if (active){
            button.setBackground(PRIMARY_GREEN);
            button.setForeground(BUTTON_TEXT_ON_DARK);
            button.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(PRIMARY_GREEN, 1),
                new EmptyBorder(6, 12, 6, 12)
            ));
        }
        else{
            button.setBackground(NEUTRAL_BG);
            button.setForeground(TEXT_PRIMARY);
            button.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(NEUTRAL_MID, 1),
                new EmptyBorder(6, 12, 6, 12)
            ));
        }
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseEntered(MouseEvent e){
                if (appState.getCurrentViewMode()!=viewMode){
                    button.setBackground(NEUTRAL_LIGHT);
                }
                else{
                    button.setBackground(PRIMARY_GREEN.darker());
                }
            }
            @Override
            public void mouseExited(MouseEvent e){
                if (appState.getCurrentViewMode()!=viewMode){
                    button.setBackground(NEUTRAL_BG);
                }
                else{
                    button.setBackground(PRIMARY_GREEN);
                }
            }
        });
        button.addActionListener(e->{
            controller.setViewMode(viewMode);
            updateViewModeButtonStates();
            updateCalendar();
        });
        return button;
    }
    private void updateViewModeButtonStates(){
        for (Component c:viewModePanel.getComponents()){
            if (c instanceof JButton){
                JButton button=(JButton) c;
                Object mode=button.getClientProperty("viewMode");
                if (mode instanceof AppState.ViewMode){
                    AppState.ViewMode viewMode=(AppState.ViewMode) mode;
                    boolean active=appState.getCurrentViewMode()==viewMode;
                    if (active){
                        button.setBackground(PRIMARY_GREEN);
                        button.setForeground(BUTTON_TEXT_ON_DARK);
                        button.setBorder(BorderFactory.createCompoundBorder(
                            new LineBorder(PRIMARY_GREEN, 1),
                            new EmptyBorder(6, 12, 6, 12)
                        ));
                    }
                    else{
                        button.setBackground(NEUTRAL_BG);
                        button.setForeground(TEXT_PRIMARY);
                        button.setBorder(BorderFactory.createCompoundBorder(
                            new LineBorder(NEUTRAL_MID, 1),
                            new EmptyBorder(6, 12, 6, 12)
                        ));
                    }
                }
            }
        }
    }
    private void setupListeners(){
        appState.addPropertyChangeListener(this);
        JPopupMenu popupMenu=new JPopupMenu();
        JMenuItem deleteMenuItem=new JMenuItem("Delete Event");
        deleteMenuItem.addActionListener(e->{
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
        });
        popupMenu.add(deleteMenuItem);
        JMenuItem editMenuItem=new JMenuItem("Edit Event");
        editMenuItem.addActionListener(e->{
            Event selectedEvent=eventsList.getSelectedValue();
            if (selectedEvent!=null){
                showEditEventDialog(selectedEvent);
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
        SwingUtilities.invokeLater(()->{
            updateUIFromState();
        });
    }
    private void updateUIFromState(){
        updateCalendar();
        updateEvents();
        updateSidebar();
        updateStatusBar();
    }
    private void updateCalendar(){
        AppState.ViewMode viewMode=appState.getCurrentViewMode();
        LocalDate selectedDate=appState.getSelectedDate();
        switch (viewMode){
            case MONTH_VIEW:
                updateMonthView(selectedDate);
                break;
            case WEEK_VIEW:
                updateWeekView(selectedDate);
                break;
            case DAY_VIEW:
                updateDayView(selectedDate);
                break;
            case AGENDA_VIEW:
                updateAgendaView(selectedDate);
                break;
        }
        updateViewModeButtonStates();
    }
    private void updateMonthView(LocalDate date){
        monthYearLabel.setText(date.format(monthYearFormatter));
        calendarGrid.removeAll();
        calendarGrid.setLayout(new GridLayout(0, 7, 1, 1));
        JPanel headerPanel=new JPanel(new GridLayout(1, 7, 1, 1));
        headerPanel.setBackground(NEUTRAL_MID);
        String[] days={"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        for (int i=0;i<days.length;i++){
            JLabel label=new JLabel(days[i].substring(0, 3), SwingConstants.CENTER);
            label.setFont(new Font("Segoe UI", Font.BOLD, 12));
            label.setBackground(NEUTRAL_MID);
            label.setOpaque(true);
            label.setBorder(new EmptyBorder(8, 2, 8, 2));
            if (i==0||i==6){
                label.setForeground(PRIMARY_RED);
                label.setBackground(new Color(245, 245, 247));
            }
            else{
                label.setForeground(TEXT_PRIMARY);
            }
            headerPanel.add(label);
        }
        calendarScrollPane.getViewport().removeAll();
        JPanel container=new JPanel(new BorderLayout());
        container.add(headerPanel, BorderLayout.NORTH);
        JPanel gridContainer=new JPanel(new BorderLayout());
        gridContainer.add(calendarGrid, BorderLayout.CENTER);
        JScrollPane innerScroll=new JScrollPane(gridContainer);
        innerScroll.setBorder(null);
        container.add(innerScroll, BorderLayout.CENTER);
        calendarScrollPane.setViewportView(container);
        LocalDate firstDayOfMonth=date.withDayOfMonth(1);
        int startDayOfWeek=firstDayOfMonth.getDayOfWeek().getValue() % 7;
        int daysInMonth=date.lengthOfMonth();
        for (int i=0;i<startDayOfWeek;i++){
            calendarGrid.add(createDayCell(null));
        }
        for (int day=1;day<=daysInMonth;day++){
            LocalDate cellDate=date.withDayOfMonth(day);
            calendarGrid.add(createDayCell(cellDate));
        }
        int totalCells=42;
        int cellsUsed=startDayOfWeek+daysInMonth;
        for (int i=cellsUsed;i<totalCells;i++){
            calendarGrid.add(createDayCell(null));
        }
        calendarGrid.revalidate();
        calendarGrid.repaint();
    }
    private void updateWeekView(LocalDate date){
        LocalDate startOfWeek=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate endOfWeek=date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        monthYearLabel.setText("Week of "+startOfWeek.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))+ "-"+endOfWeek.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)));
        calendarGrid.removeAll();
        calendarGrid.setLayout(new GridLayout(0, 7, 1, 1));
        for (int i=0;i<7;i++){
            LocalDate dayDate=startOfWeek.plusDays(i);
            JPanel dayHeader=new JPanel(new BorderLayout());
            dayHeader.setBackground(NEUTRAL_MID);
            dayHeader.setBorder(new EmptyBorder(4, 4, 4, 4));
            JLabel dayName=new JLabel(dayDate.format(shortDayFormatter), SwingConstants.CENTER);
            dayName.setFont(new Font("Segoe UI", Font.BOLD, 11));
            JLabel dateLabel=new JLabel(String.valueOf(dayDate.getDayOfMonth()), SwingConstants.CENTER);
            dateLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            if (dayDate.getDayOfWeek()==DayOfWeek.SUNDAY||dayDate.getDayOfWeek()==DayOfWeek.SATURDAY){
                dayName.setForeground(PRIMARY_RED);
                dateLabel.setForeground(PRIMARY_RED);
                dayHeader.setBackground(new Color(245, 245, 247));
            }
            else{
                dayName.setForeground(TEXT_PRIMARY);
                dateLabel.setForeground(TEXT_PRIMARY);
            }
            if (dayDate.equals(LocalDate.now())){
                dateLabel.setForeground(PRIMARY_BLUE);
                dayHeader.setBackground(CALENDAR_TODAY);
            }
            dayHeader.add(dayName, BorderLayout.NORTH);
            dayHeader.add(dateLabel, BorderLayout.CENTER);
            calendarGrid.add(dayHeader);
        }
        for (int hour=8;hour<=20;hour++){
            for (int day=0;day<7;day++){
                LocalDate dayDate=startOfWeek.plusDays(day);
                JPanel timeSlot=new JPanel(new BorderLayout());
                timeSlot.setBackground(NEUTRAL_BG);
                timeSlot.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(NEUTRAL_MID, 1),
                    new EmptyBorder(2, 4, 2, 4)
                ));
                if (day==0){
                    JLabel timeLabel=new JLabel(String.format("%2d:00", hour));
                    timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                    timeLabel.setForeground(TEXT_SECONDARY);
                    timeSlot.add(timeLabel, BorderLayout.WEST);
                }
                List<Event> events=controller.getEventsbyDate(dayDate);
                for (Event event:events){
                    LocalTime eventStart=event.getStartTime().toLocalTime();
                    LocalTime eventEnd=event.getEndTime().toLocalTime();
                    if (!eventEnd.isBefore(LocalTime.of(hour, 0))&&
                        !eventStart.isAfter(LocalTime.of(hour+1, 0))){
                        JLabel eventLabel=new JLabel(event.getTitle());
                        eventLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                        eventLabel.setForeground(PRIMARY_GREEN);
                        eventLabel.setBackground(new Color(230, 245, 230));
                        eventLabel.setOpaque(true);
                        eventLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
                        timeSlot.add(eventLabel, BorderLayout.CENTER);
                        break;
                    }
                }
                calendarGrid.add(timeSlot);
            }
        }
        calendarGrid.revalidate();
        calendarGrid.repaint();
    }
    private void updateDayView(LocalDate date){
        monthYearLabel.setText(date.format(dayFormatter)+", "+date.format(monthYearFormatter));
        calendarGrid.removeAll();
        calendarGrid.setLayout(new GridLayout(0, 1, 0, 1));
        for (int hour=0;hour<24;hour++){
            JPanel hourPanel=new JPanel(new BorderLayout());
            hourPanel.setBackground(NEUTRAL_BG);
            hourPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(NEUTRAL_MID, 1),
                new EmptyBorder(4, 8, 4, 8)
            ));
            JLabel timeLabel=new JLabel(String.format("%02d:00", hour));
            timeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            timeLabel.setForeground(TEXT_SECONDARY);
            timeLabel.setPreferredSize(new Dimension(60, 0));
            JPanel eventsPanel=new JPanel();
            eventsPanel.setLayout(new BoxLayout(eventsPanel, BoxLayout.Y_AXIS));
            eventsPanel.setBackground(NEUTRAL_BG);
            List<Event> events=controller.getEventsbyDate(date);
            for (Event event:events){
                LocalTime eventStart=event.getStartTime().toLocalTime();
                LocalTime eventEnd=event.getEndTime().toLocalTime();
                if (eventStart.getHour()==hour||(eventStart.getHour()<=hour&&eventEnd.getHour()>hour)){
                    JPanel eventPanel=new JPanel(new BorderLayout());
                    eventPanel.setBackground(new Color(230, 245, 230));
                    eventPanel.setBorder(new EmptyBorder(4, 8, 4, 8));
                    eventPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
                    JLabel titleLabel=new JLabel(event.getTitle());
                    titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
                    titleLabel.setForeground(PRIMARY_GREEN);
                    String timeRange=eventStart.format(timeFormatter)+"-"+eventEnd.format(timeFormatter);
                    JLabel timeRangeLabel=new JLabel(timeRange);
                    timeRangeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                    timeRangeLabel.setForeground(TEXT_SECONDARY);
                    eventPanel.add(titleLabel, BorderLayout.NORTH);
                    eventPanel.add(timeRangeLabel, BorderLayout.SOUTH);
                    eventPanel.addMouseListener(new MouseAdapter(){
                        @Override
                        public void mouseClicked(MouseEvent e){
                            if (e.getClickCount()==2){
                                showEditEventDialog(event);
                            }
                        }
                    });
                    eventsPanel.add(eventPanel);
                    eventsPanel.add(Box.createVerticalStrut(4));
                }
            }
            hourPanel.add(timeLabel, BorderLayout.WEST);
            hourPanel.add(eventsPanel, BorderLayout.CENTER);
            calendarGrid.add(hourPanel);
        }
        calendarGrid.revalidate();
        calendarGrid.repaint();
    }
    private void updateAgendaView(LocalDate date){
        monthYearLabel.setText("Agenda-"+date.format(monthYearFormatter));
        calendarGrid.removeAll();
        calendarGrid.setLayout(new BorderLayout());
        LocalDate firstOfMonth=date.withDayOfMonth(1);
        LocalDate lastOfMonth=date.withDayOfMonth(date.lengthOfMonth());
        List<Event> monthEvents=controller.getEventsByDateRange(firstOfMonth, lastOfMonth);
        monthEvents.sort(Comparator.comparing(Event::getStartTime));
        JPanel agendaPanel=new JPanel();
        agendaPanel.setLayout(new BoxLayout(agendaPanel, BoxLayout.Y_AXIS));
        agendaPanel.setBackground(NEUTRAL_BG);
        LocalDate currentDate=null;
        for (Event event:monthEvents){
            LocalDate eventDate=event.getDate();
            if (!eventDate.equals(currentDate)){
                currentDate=eventDate;
                JPanel dateHeader=new JPanel(new BorderLayout());
                dateHeader.setBackground(NEUTRAL_LIGHT);
                dateHeader.setBorder(new EmptyBorder(8, 16, 8, 16));
                dateHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
                JLabel dateLabel=new JLabel(eventDate.format(dateFormatter));
                dateLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
                dateLabel.setForeground(TEXT_PRIMARY);
                if (eventDate.equals(LocalDate.now())){
                    dateLabel.setForeground(PRIMARY_BLUE);
                    dateHeader.setBackground(CALENDAR_TODAY);
                }
                else if (eventDate.getDayOfWeek()==DayOfWeek.SUNDAY||eventDate.getDayOfWeek()==DayOfWeek.SATURDAY){
                    dateLabel.setForeground(PRIMARY_RED);
                }
                dateHeader.add(dateLabel, BorderLayout.WEST);
                agendaPanel.add(dateHeader);
                agendaPanel.add(Box.createVerticalStrut(4));
            }
            JPanel eventPanel=new JPanel(new BorderLayout());
            eventPanel.setBackground(NEUTRAL_BG);
            eventPanel.setBorder(new EmptyBorder(8, 32, 8, 16));
            eventPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
            JLabel timeLabel=new JLabel(event.getStartTime().format(timeFormatter)+"-" +event.getEndTime().format(timeFormatter));
            timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            timeLabel.setForeground(TEXT_SECONDARY);
            timeLabel.setPreferredSize(new Dimension(120, 0));
            JLabel titleLabel=new JLabel(event.getTitle());
            titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            titleLabel.setForeground(TEXT_PRIMARY);
            JPopupMenu eventMenu=new JPopupMenu();
            JMenuItem deleteItem=new JMenuItem("Delete");
            deleteItem.addActionListener(e->{
                int confirm=JOptionPane.showConfirmDialog(this, "Delete '"+event.getTitle()+"'?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
                if (confirm==JOptionPane.YES_OPTION){
                    controller.deleteEvent(event);
                }
            });
            eventMenu.add(deleteItem);
            eventPanel.addMouseListener(new MouseAdapter(){
                @Override
                public void mouseClicked(MouseEvent e){
                    if (SwingUtilities.isRightMouseButton(e)){
                        eventMenu.show(eventPanel, e.getX(), e.getY());
                    }
                    else if (e.getClickCount()==2){
                        showEditEventDialog(event);
                    }
                }
            });
            eventPanel.add(timeLabel, BorderLayout.WEST);
            eventPanel.add(titleLabel, BorderLayout.CENTER);
            agendaPanel.add(eventPanel);
            agendaPanel.add(Box.createVerticalStrut(4));
        }
        if (monthEvents.isEmpty()){
            JLabel emptyLabel=new JLabel("No events scheduled for this month", SwingConstants.CENTER);
            emptyLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            emptyLabel.setForeground(DISABLED_TEXT);
            emptyLabel.setBorder(new EmptyBorder(100, 0, 0, 0));
            agendaPanel.add(emptyLabel);
        }
        JScrollPane scrollPane=new JScrollPane(agendaPanel);
        scrollPane.setBorder(null);
        calendarGrid.add(scrollPane, BorderLayout.CENTER);
        calendarGrid.revalidate();
        calendarGrid.repaint();
    }
    private JPanel createDayCell(LocalDate date){
        JPanel cell=new JPanel(new BorderLayout(0, 4));
        cell.setBackground(NEUTRAL_BG);
        cell.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(NEUTRAL_MID, 1),
            new EmptyBorder(6, 4, 6, 4)
        ));
        if (date!=null){
            JLabel dayLabel=new JLabel(String.valueOf(date.getDayOfMonth()), SwingConstants.CENTER);
            dayLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            LocalDate today=LocalDate.now();
            LocalDate selectedDate=appState.getSelectedDate();
            if (date.equals(today)){
                cell.setBackground(CALENDAR_TODAY);
                dayLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
                dayLabel.setForeground(PRIMARY_BLUE);
            }
            else if (date.equals(currentSelectedDate)){
                cell.setBackground(CALENDAR_SELECTED);
                dayLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
                dayLabel.setForeground(TEXT_PRIMARY);
            }
            else if (date.getMonth()!=selectedDate.getMonth()){
                dayLabel.setForeground(TEXT_SECONDARY);
            }
            else if (date.getDayOfWeek().getValue() >= 6){
                cell.setBackground(CALENDAR_WEEKEND);
                dayLabel.setForeground(PRIMARY_RED);
            }
            else{
                dayLabel.setForeground(TEXT_PRIMARY);
            }
            cell.add(dayLabel, BorderLayout.NORTH);
            List<Event> dayEvents=controller.getEventsbyDate(date);
            if (!dayEvents.isEmpty()){
                JPanel eventsPanel=new JPanel();
                eventsPanel.setLayout(new BoxLayout(eventsPanel, BoxLayout.Y_AXIS));
                eventsPanel.setBackground(cell.getBackground());
                eventsPanel.setBorder(new EmptyBorder(2, 0, 0, 0));
                int maxEvents=Math.min(dayEvents.size(), 2);
                for (int i=0;i<maxEvents;i++){
                    Event event=dayEvents.get(i);
                    JLabel eventLabel=new JLabel("• "+event.getTitle());
                    eventLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                    eventLabel.setForeground(PRIMARY_GREEN);
                    eventLabel.setBackground(cell.getBackground());
                    eventLabel.setOpaque(true);
                    eventLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
                    eventsPanel.add(eventLabel);
                }
                if (dayEvents.size()>2){
                    JLabel moreLabel=new JLabel("+"+(dayEvents.size()-2)+" more");
                    moreLabel.setFont(new Font("Segoe UI", Font.PLAIN, 8));
                    moreLabel.setForeground(TEXT_SECONDARY);
                    eventsPanel.add(moreLabel);
                }
                cell.add(eventsPanel, BorderLayout.CENTER);
            }
            cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cell.addMouseListener(new MouseAdapter(){
                @Override
                public void mouseClicked(MouseEvent e){
                    controller.setSelectedDate(date);
                }
                @Override
                public void mouseEntered(MouseEvent e){
                    Color currentBg=cell.getBackground();
                    if (!currentBg.equals(CALENDAR_TODAY)&&!currentBg.equals(CALENDAR_SELECTED)){
                        cell.setBackground(NEUTRAL_LIGHT);
                    }
                }
                @Override
                public void mouseExited(MouseEvent e){
                    LocalDate today=LocalDate.now();
                    if (date.equals(today)){
                        cell.setBackground(CALENDAR_TODAY);
                    }
                    else if (date.equals(currentSelectedDate)){
                        cell.setBackground(CALENDAR_SELECTED);
                    }
                    else if (date.getDayOfWeek().getValue() >= 6){
                        cell.setBackground(CALENDAR_WEEKEND);
                    }
                    else{
                        cell.setBackground(NEUTRAL_BG);
                    }
                }
            });
        }
        else{
            cell.setBackground(NEUTRAL_LIGHT);
        }
        return cell;
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
            eventCountLabel.setText(events.size()+" event"+(events.size()!=1?"s":""));
        });
    }
    private void updateStatusBar(){
        SwingUtilities.invokeLater(()->{
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
        });
    }
    private void showAddEventDialog(){
        showEventDialog(null);
    }
    private void showEditEventDialog(Event event){
        showEventDialog(event);
    }
    private void showEventDialog(Event existingEvent){
        boolean isEdit=existingEvent!=null;
        JDialog dialog=new JDialog(this, isEdit?"Edit Event":"Add New Event", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(450, 350);
        dialog.setLocationRelativeTo(this);
        JPanel formPanel=new JPanel(new GridBagLayout());
        formPanel.setBackground(NEUTRAL_BG);
        formPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc=new GridBagConstraints();
        gbc.fill=GridBagConstraints.HORIZONTAL;
        gbc.insets=new Insets(6, 6, 6, 6);
        gbc.gridx=0;
        gbc.gridy=0;
        JLabel titleLabel=new JLabel("Event Title:");
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        titleLabel.setForeground(TEXT_PRIMARY);
        formPanel.add(titleLabel, gbc);
        gbc.gridx=1;
        gbc.gridwidth=2;
        gbc.weightx=1.0;
        JTextField titleField=new JTextField(20);
        titleField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        if (isEdit){
            titleField.setText(existingEvent.getTitle());
        }
        formPanel.add(titleField, gbc);
        gbc.gridx=0;
        gbc.gridy=1;
        gbc.gridwidth=1;
        gbc.weightx=0;
        JLabel dateLabel=new JLabel("Date:");
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        dateLabel.setForeground(TEXT_PRIMARY);
        formPanel.add(dateLabel, gbc);
        gbc.gridx=1;
        gbc.gridwidth=2;
        gbc.weightx=1.0;
        JTextField dateField=new JTextField(20);
        dateField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        dateField.setText(isEdit?existingEvent.getDate().toString():appState.getSelectedDate().toString());
        formPanel.add(dateField, gbc);
        gbc.gridx=0;
        gbc.gridy=2;
        gbc.gridwidth=1;
        gbc.weightx=0;
        JLabel startLabel=new JLabel("Start Time:");
        startLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        startLabel.setForeground(TEXT_PRIMARY);
        formPanel.add(startLabel, gbc);
        gbc.gridx=1;
        gbc.gridwidth=1;
        gbc.weightx=0.5;
        JTextField startTimeField=new JTextField(10);
        startTimeField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        if (isEdit){
            startTimeField.setText(existingEvent.getStartTime().toLocalTime().toString());
        }
        else{
            startTimeField.setText("09:00");
        }
        formPanel.add(startTimeField, gbc);
        gbc.gridx=2;
        gbc.gridwidth=1;
        gbc.weightx=0.5;
        JLabel endLabel=new JLabel("End Time:");
        endLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        endLabel.setForeground(TEXT_PRIMARY);
        formPanel.add(endLabel, gbc);
        gbc.gridx=3;
        gbc.gridwidth=1;
        gbc.weightx=0.5;
        JTextField endTimeField=new JTextField(10);
        endTimeField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        if (isEdit){
            endTimeField.setText(existingEvent.getEndTime().toLocalTime().toString());
        }
        else{
            endTimeField.setText("10:00");
        }
        formPanel.add(endTimeField, gbc);
        gbc.gridx=1;
        gbc.gridy=3;
        gbc.gridwidth=3;
        gbc.weightx=1.0;
        JLabel formatHint=new JLabel("Format: HH:MM (24-hour), e.g., 14:30 for 2:30 PM");
        formatHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        formatHint.setForeground(TEXT_SECONDARY);
        formPanel.add(formatHint, gbc);
        JPanel buttonPanel=new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(NEUTRAL_BG);
        if (isEdit){
            JButton deleteBtn=new JButton("Delete");
            deleteBtn.setBackground(PRIMARY_RED);
            deleteBtn.setForeground(Color.WHITE);
            deleteBtn.addActionListener(e->{
                int confirm=JOptionPane.showConfirmDialog(dialog,
                    "Are you sure you want to delete '"+existingEvent.getTitle()+"'?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION);
                if (confirm==JOptionPane.YES_OPTION){
                    boolean deleted=controller.deleteEvent(existingEvent);
                    if (deleted){
                        JOptionPane.showMessageDialog(dialog,
                            "Event deleted successfully!",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                        dialog.dispose();
                    }
                }
            });
            buttonPanel.add(deleteBtn);
        }
        JButton cancelBtn=createTextButton("Cancel");
        cancelBtn.addActionListener(e->dialog.dispose());
        JButton saveBtn=createPrimaryButton(isEdit?"Update Event":"Create Event");
        saveBtn.addActionListener(e->{
            try{
                String title=titleField.getText().trim();
                if (title.isEmpty()){
                    JOptionPane.showMessageDialog(dialog,
                        "Please enter an event title.",
                        "Input Required",
                        JOptionPane.WARNING_MESSAGE);
                    return;
                }
                LocalDate date=LocalDate.parse(dateField.getText().trim());
                LocalTime startTime=LocalTime.parse(startTimeField.getText().trim());
                LocalTime endTime=LocalTime.parse(endTimeField.getText().trim());
                if (!endTime.isAfter(startTime)){
                    JOptionPane.showMessageDialog(dialog,
                        "End time must be after start time.",
                        "Invalid Time Range",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (isEdit){
                    controller.updateEvent(existingEvent, title, date, startTime, endTime)
                        .ifPresentOrElse(
                            updatedEvent->{
                                JOptionPane.showMessageDialog(dialog,
                                    "Event updated successfully!",
                                    "Success",
                                    JOptionPane.INFORMATION_MESSAGE);
                                dialog.dispose();
                            },
                            ()->JOptionPane.showMessageDialog(dialog,
                                "Could not update event. There may be overlapping events.",
                                "Conflict Detected",
                                JOptionPane.ERROR_MESSAGE)
                        );
                }
                else{
                    boolean success=controller.createEvent(title, date, startTime, endTime).isPresent();
                    if (success){
                        JOptionPane.showMessageDialog(dialog,
                            "Event created successfully!",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                        dialog.dispose();
                    }
                    else{
                        JOptionPane.showMessageDialog(dialog,
                            "Could not create event. There may be overlapping events.",
                            "Conflict Detected",
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
            catch (Exception ex){
                JOptionPane.showMessageDialog(dialog,
                    "Invalid input format. Please check your entries.\n" +
                    "Date: YYYY-MM-DD (e.g., 2024-01-20)\n" +
                    "Time: HH:MM (24-hour, e.g., 14:30)",
                    "Input Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        });
        buttonPanel.add(cancelBtn);
        buttonPanel.add(saveBtn);
        gbc.gridx=0;
        gbc.gridy=4;
        gbc.gridwidth=4;
        gbc.weightx=1.0;
        formPanel.add(buttonPanel, gbc);
        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.setVisible(true);
    }
    private static class EventListCellRenderer extends DefaultListCellRenderer{
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
                setText(String.format("<html><b>%s</b><br/><font color='#6C757D' size='-1'>%s-%s</font></html>",
                    event.getTitle(), startTime, endTime));
                setIcon(new EventDotIcon(PRIMARY_GREEN));
                setBorder(new EmptyBorder(10, 10, 10, 10));
                setFont(new Font("Segoe UI", Font.PLAIN, 13));
            }
            return this;
        }
    }
    private static class EventDotIcon implements Icon{
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
    public static void main(String[] args){
        Locale.setDefault(Locale.ENGLISH);
        SwingUtilities.invokeLater(()->{
            try{
                CalendarController controller=new CalendarController();
                new CalendarFrame(controller);
            }
            catch (Exception e){
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                    "Failed to start Calendar App: "+e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}