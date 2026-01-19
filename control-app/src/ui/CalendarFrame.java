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
import java.util.List;
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
    private static Color PRIMARY_GREEN=new Color(52, 168, 83);
    private static Color PRIMARY_RED=new Color(234, 67, 53);
    private static Color NEUTRAL_BG=new Color(255, 255, 255);
    private static Color NEUTRAL_LIGHT=new Color(248, 249, 250);
    private static Color NEUTRAL_MID=new Color(233, 236, 239);
    private static Color NEUTRAL_DARK=new Color(222, 226, 230);
    private static Color TEXT_PRIMARY=new Color(33, 37, 41);
    private static Color TEXT_SECONDARY=new Color(108, 117, 125);
    private static Color TEXT_LIGHT=new Color(173, 181, 189);
    private static Color CALENDAR_TODAY=new Color(219, 237, 255);
    private static Color CALENDAR_WEEKEND=new Color(252, 249, 244);
    private static Color CALENDAR_SELECTED=new Color(240, 248, 255);
    private CalendarController controller;
    private AppState appState;
    private JLabel monthYearLabel;
    private JPanel calendarGrid;
    private JList<Event> eventsList;
    private DefaultListModel<Event> eventsListModel;
    private JLabel selectedDateLabel;
    private JLabel eventCountLabel;
    private JButton addEventButton;
    private DateTimeFormatter monthYearFormatter=DateTimeFormatter.ofPattern("MMMM yyyy");
    private DateTimeFormatter dateFormatter=DateTimeFormatter.ofPattern("EEEE, MMMM d");
    private static DateTimeFormatter timeFormatter=DateTimeFormatter.ofPattern("h:mm a");
    private LocalDate currentSelectedDate;
    public CalendarFrame(CalendarController controller){
        this.controller=controller;
        this.appState=controller.getAppState();
        this.currentSelectedDate=appState.getSelectedDate();
        initializeWindow();
        setupLookAndFeel();
        createComponents();
        setupLayout();
        setupListeners();
        updateUIFromState();
        setVisible(true);
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
            UIManager.put("Button.font", new Font("SansSerif", Font.PLAIN, 13));
            UIManager.put("Label.font", new Font("SansSerif", Font.PLAIN, 13));
            UIManager.put("TextField.font", new Font("SansSerif", Font.PLAIN, 13));
            UIManager.put("List.font", new Font("SansSerif", Font.PLAIN, 13));
            UIManager.put("Panel.background", NEUTRAL_BG);
            
        }
        catch (Exception e){
            System.err.println("Note: Could not set system look and feel: "+e.getMessage());
        }
    }
    private void createComponents(){
        monthYearLabel=new JLabel("", SwingConstants.CENTER);
        monthYearLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        monthYearLabel.setForeground(TEXT_PRIMARY);
        JButton prevMonthBtn=createTextButton("◀ Previous");
        JButton todayBtn=createPrimaryButton("Today");
        JButton nextMonthBtn=createTextButton("Next ▶");
        JPanel viewModePanel=new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        viewModePanel.setBackground(NEUTRAL_BG);
        JButton dayViewBtn=createViewModeButton("Day", AppState.ViewMode.DAY_VIEW);
        JButton weekViewBtn=createViewModeButton("Week", AppState.ViewMode.WEEK_VIEW);
        JButton monthViewBtn=createViewModeButton("Month", AppState.ViewMode.MONTH_VIEW);
        JButton agendaViewBtn=createViewModeButton("Agenda", AppState.ViewMode.AGENDA_VIEW);
        viewModePanel.add(dayViewBtn);
        viewModePanel.add(weekViewBtn);
        viewModePanel.add(monthViewBtn);
        viewModePanel.add(agendaViewBtn);
        calendarGrid=new JPanel(new GridLayout(0, 7, 1, 1));
        calendarGrid.setBackground(NEUTRAL_MID);
        calendarGrid.setBorder(new EmptyBorder(1, 1, 1, 1));
        eventsListModel=new DefaultListModel<>();
        eventsList=new JList<>(eventsListModel);
        eventsList.setCellRenderer(new EventListCellRenderer());
        eventsList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        eventsList.setBackground(NEUTRAL_BG);
        eventsList.setSelectionBackground(PRIMARY_BLUE);
        eventsList.setSelectionForeground(Color.WHITE);
        JScrollPane eventsScrollPane=new JScrollPane(eventsList);
        eventsScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        eventsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        selectedDateLabel=new JLabel();
        selectedDateLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        selectedDateLabel.setForeground(TEXT_PRIMARY);
        eventCountLabel=new JLabel();
        eventCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        eventCountLabel.setForeground(TEXT_SECONDARY);
        addEventButton=createPrimaryButton("+ New Event");
        addEventButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        prevMonthBtn.addActionListener(e->controller.navigateToPreviousMonth());
        nextMonthBtn.addActionListener(e->controller.navigateToNextMonth());
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
        JPanel dayHeaders=createDayHeaders();
        calendarContainer.add(dayHeaders, BorderLayout.NORTH);
        JScrollPane calendarScrollPane=new JScrollPane(calendarGrid);
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
        upcomingLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
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
        JLabel statusLabel=new JLabel("Ready");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(TEXT_SECONDARY);
        JLabel unsavedLabel=new JLabel("No unsaved changes");
        unsavedLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        unsavedLabel.setForeground(TEXT_LIGHT);
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(unsavedLabel, BorderLayout.EAST);
        setLayout(new BorderLayout());
        add(navigationPanel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
    }
    private JPanel createDayHeaders(){
        JPanel panel=new JPanel(new GridLayout(1, 7, 1, 1));
        panel.setBackground(NEUTRAL_MID);
        String[] days={"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        for (int i=0;i<days.length;i++){
            JLabel label=new JLabel(days[i].substring(0, 3), SwingConstants.CENTER);
            label.setFont(new Font("SansSerif", Font.BOLD, 12));
            label.setBackground(NEUTRAL_MID);
            label.setOpaque(true);
            label.setBorder(new EmptyBorder(8, 2, 8, 2));
            if (i==0||i==6){
                label.setForeground(PRIMARY_RED);
            }
            else{
                label.setForeground(TEXT_PRIMARY);
            }
            panel.add(label);
        }
        return panel;
    }
    private JButton createTextButton(String text){
        JButton button=new JButton(text);
        button.setFont(new Font("SansSerif", Font.PLAIN, 13));
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
        button.setFont(new Font("SansSerif", Font.PLAIN, 13));
        button.setBackground(PRIMARY_BLUE);
        button.setForeground(Color.WHITE);
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
                button.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(PRIMARY_BLUE.darker(), 1),
                    new EmptyBorder(8, 16, 8, 16)
                ));
            }
            @Override
            public void mouseExited(MouseEvent e){
                button.setBackground(PRIMARY_BLUE);
                button.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(PRIMARY_BLUE, 1),
                    new EmptyBorder(8, 16, 8, 16)
                ));
            }
        });
        return button;
    }
    private JButton createViewModeButton(String text, AppState.ViewMode viewMode){
        JButton button=createTextButton(text);
        button.setFont(new Font("SansSerif", Font.PLAIN, 12));
        button.setBorder(new EmptyBorder(6, 12, 6, 12));
        if (appState.getCurrentViewMode()==viewMode){
            button.setBackground(PRIMARY_GREEN);
            button.setForeground(Color.WHITE);
            button.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(PRIMARY_GREEN, 1),
                new EmptyBorder(6, 12, 6, 12)
            ));
        }
        button.addActionListener(e->{
            controller.setViewMode(viewMode);
            updateViewModeButtonStates();
        });
        return button;
    }
    private void updateViewModeButtonStates(){
        updateCalendar();
    }
    private void setupLayout(){

    }
    private void setupListeners(){
        appState.addPropertyChangeListener(this);
        appState.addPropertyChangeListener(AppState.PROPERTY_SELECTED_DATE, 
            new PropertyChangeListener(){
                @Override
                public void propertyChange(PropertyChangeEvent evt){
                    currentSelectedDate=appState.getSelectedDate();
                    updateCalendar();
                    updateEvents();
                    updateSidebar();
                }
            });
        appState.addPropertyChangeListener(AppState.PROPERTY_EVENTS_CHANGED,
            new PropertyChangeListener(){
                @Override
                public void propertyChange(PropertyChangeEvent evt){
                    updateEvents();
                    updateSidebar();
                }
            });
    }
    @Override
    public void propertyChange(PropertyChangeEvent evt){
        SwingUtilities.invokeLater(new Runnable(){
            @Override
            public void run(){
                updateUIFromState();
            }
        });
    }
    private void updateUIFromState(){
        updateCalendar();
        updateEvents();
        updateSidebar();
    }
    private void updateCalendar(){
        LocalDate selectedDate=appState.getSelectedDate();
        monthYearLabel.setText(selectedDate.format(monthYearFormatter));
        calendarGrid.removeAll();
        LocalDate firstDayOfMonth=selectedDate.withDayOfMonth(1);
        int startDayOfWeek=firstDayOfMonth.getDayOfWeek().getValue()%7;
        int daysInMonth=selectedDate.lengthOfMonth();
        for (int i=0;i<startDayOfWeek;i++){
            calendarGrid.add(createDayCell(null));
        }
        for (int day=1;day <= daysInMonth;day++){
            LocalDate date=selectedDate.withDayOfMonth(day);
            calendarGrid.add(createDayCell(date));
        }
        int totalCells=42;
        int cellsUsed=startDayOfWeek+daysInMonth;
        for (int i=cellsUsed;i<totalCells;i++){
            calendarGrid.add(createDayCell(null));
        }
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
            dayLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
            LocalDate today=LocalDate.now();
            LocalDate selectedDate=appState.getSelectedDate();
            if (date.equals(today)){
                cell.setBackground(CALENDAR_TODAY);
                dayLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
                dayLabel.setForeground(PRIMARY_BLUE);
            }
            else if (date.equals(currentSelectedDate)){
                cell.setBackground(CALENDAR_SELECTED);
                dayLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
                dayLabel.setForeground(TEXT_PRIMARY);
            }
            else if (date.getMonth()!=selectedDate.getMonth()){
                dayLabel.setForeground(TEXT_LIGHT);
            }
            else if (date.getDayOfWeek().getValue()>=6){
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
                    eventLabel.setFont(new Font("SansSerif", Font.PLAIN, 9));
                    eventLabel.setForeground(PRIMARY_GREEN);
                    eventLabel.setBackground(cell.getBackground());
                    eventLabel.setOpaque(true);
                    eventLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
                    eventsPanel.add(eventLabel);
                }
                if (dayEvents.size()>2){
                    JLabel moreLabel=new JLabel("+"+(dayEvents.size()-2)+" more");
                    moreLabel.setFont(new Font("SansSerif", Font.PLAIN, 8));
                    moreLabel.setForeground(TEXT_LIGHT);
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
                    else if (date.getDayOfWeek().getValue()>=6){
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
        SwingUtilities.invokeLater(new Runnable(){
            @Override
            public void run(){
                eventsListModel.clear();
                LocalDate selectedDate=appState.getSelectedDate();
                List<Event> events=controller.getEventsbyDate(selectedDate);
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
            @Override
            public void run(){
                LocalDate selectedDate=appState.getSelectedDate();
                List<Event> events=controller.getEventsbyDate(selectedDate);
                selectedDateLabel.setText(selectedDate.format(dateFormatter));
                eventCountLabel.setText(events.size()+" event"+(events.size()!=1 ? "s":""));
            }
        });
    }
    private void showAddEventDialog(){
        JDialog dialog=new JDialog(this, "Add New Event", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(420, 280);
        dialog.setLocationRelativeTo(this);
        JPanel formPanel=new JPanel(new GridBagLayout());
        formPanel.setBackground(NEUTRAL_BG);
        formPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc=new GridBagConstraints();
        gbc.fill=GridBagConstraints.HORIZONTAL;
        gbc.insets=new Insets(6, 6, 6, 6);
        gbc.gridx=0;gbc.gridy=0;
        JLabel titleLabel=new JLabel("Event Title:");
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        formPanel.add(titleLabel, gbc);
        gbc.gridx=1;gbc.gridwidth=2;gbc.weightx=1.0;
        JTextField titleField=new JTextField(20);
        titleField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        formPanel.add(titleField, gbc);
        gbc.gridx=0;gbc.gridy=1;gbc.gridwidth=1;gbc.weightx=0;
        JLabel dateLabel=new JLabel("Date:");
        dateLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        formPanel.add(dateLabel, gbc);
        gbc.gridx=1;gbc.gridwidth=2;gbc.weightx=1.0;
        JTextField dateField=new JTextField(appState.getSelectedDate().toString());
        dateField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        formPanel.add(dateField, gbc);
        gbc.gridx=0;gbc.gridy=2;gbc.gridwidth=1;gbc.weightx=0;
        JLabel startLabel=new JLabel("Start Time:");
        startLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        formPanel.add(startLabel, gbc);
        gbc.gridx=1;gbc.gridwidth=1;gbc.weightx=0.5;
        JTextField startTimeField=new JTextField("09:00");
        startTimeField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        formPanel.add(startTimeField, gbc);
        gbc.gridx=2;gbc.gridwidth=1;gbc.weightx=0.5;
        JTextField endTimeField=new JTextField("10:00");
        endTimeField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        formPanel.add(endTimeField, gbc);
        gbc.gridx=1;gbc.gridy=3;gbc.gridwidth=2;gbc.weightx=1.0;
        JLabel formatHint=new JLabel("Format: HH:MM (24-hour)");
        formatHint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        formatHint.setForeground(TEXT_SECONDARY);
        formPanel.add(formatHint, gbc);
        JPanel buttonPanel=new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(NEUTRAL_BG);
        buttonPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        JButton cancelBtn=createTextButton("Cancel");
        cancelBtn.addActionListener(e->dialog.dispose());
        JButton saveBtn=createPrimaryButton("Create Event");
        saveBtn.addActionListener(e->{
            try{
                String title=titleField.getText().trim();
                if (title.isEmpty()){
                    JOptionPane.showMessageDialog(dialog, "Please enter an event title.", "Input Required", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                LocalDate date=LocalDate.parse(dateField.getText().trim());
                LocalTime startTime=LocalTime.parse(startTimeField.getText().trim());
                LocalTime endTime=LocalTime.parse(endTimeField.getText().trim());
                if (!endTime.isAfter(startTime)){
                    JOptionPane.showMessageDialog(dialog, "End time must be after start time.", "Invalid Time Range", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                boolean success=controller.createEvent(title, date, startTime, endTime).isPresent();
                if (success){
                    JOptionPane.showMessageDialog(dialog, "Event created successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();
                }
                else{
                    JOptionPane.showMessageDialog(dialog, "Could not create event. There may be overlapping events.", "Conflict Detected", JOptionPane.ERROR_MESSAGE);
                }
                
            }
            catch (Exception ex){
                JOptionPane.showMessageDialog(dialog, "Invalid input format. Please check your entries.\n"+"Date: YYYY-MM-DD\nTime: HH:MM", "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        buttonPanel.add(cancelBtn);
        buttonPanel.add(saveBtn);
        gbc.gridx=0;gbc.gridy=4;gbc.gridwidth=3;gbc.weightx=1.0;
        formPanel.add(buttonPanel, gbc);
        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.setVisible(true);
    }
    private static class EventListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus){
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value==null){
                setText("No events scheduled for this day");
                setForeground(new Color(173, 181, 189));
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(new Font("SansSerif", Font.ITALIC, 12));
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
                setFont(new Font("SansSerif", Font.PLAIN, 13));
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
            g2.fillOval(x, y+(c.getHeight()-SIZE) / 2, SIZE, SIZE);
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