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

/**
 * Handles event editing and UI component creation
 */
public class EventEditor {
    public static class UIComponentFactory{
        public static JButton createTextButton(String text, Color bg, Color hoverBg, Color border, Color textColor){
            JButton button=new JButton(text);
            button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            button.setBackground(bg);
            button.setForeground(textColor);
            button.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(border, 1),
                new EmptyBorder(6, 12, 6, 12)
            ));
            button.setFocusPainted(false);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addMouseListener(new MouseAdapter(){
                @Override
                public void mouseEntered(MouseEvent e){
                    button.setBackground(hoverBg);
                }
                @Override
                public void mouseExited(MouseEvent e){
                    button.setBackground(bg);
                }
            });
            return button;
        }
        public static JButton createPrimaryButton(String text, Color primary){
            JButton button=new JButton(text);
            button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            button.setBackground(primary);
            button.setForeground(Color.BLACK);
            button.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(primary, 1),
                new EmptyBorder(8, 16, 8, 16)
            ));
            button.setFocusPainted(false);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addMouseListener(new MouseAdapter(){
                @Override
                public void mouseEntered(MouseEvent e){
                    button.setBackground(primary.darker());
                    button.setForeground(Color.BLACK);
                    button.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(primary.darker(), 1),
                        new EmptyBorder(8, 16, 8, 16)
                    ));
                }
                @Override
                public void mouseExited(MouseEvent e){
                    button.setBackground(primary);
                    button.setForeground(Color.BLACK);
                    button.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(primary, 1),
                        new EmptyBorder(8, 16, 8, 16)
                    ));
                }
            });
            return button;
        }
        public static JButton createViewModeButton(String text, AppState.ViewMode viewMode, boolean active, Color primary, Color bg, Color border, Color hoverBg, Color textColor){
            JButton button=new JButton(text);
            button.putClientProperty("viewMode", viewMode);
            button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            if (active){
                button.setBackground(primary);
                button.setForeground(Color.BLACK);
                button.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(primary, 1),
                    new EmptyBorder(6, 12, 6, 12)
                ));
            }
            else{
                button.setBackground(bg);
                button.setForeground(Color.BLACK);
                button.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(border, 1),
                    new EmptyBorder(6, 12, 6, 12)
                ));
            }
            button.setFocusPainted(false);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addMouseListener(new MouseAdapter(){
                @Override
                public void mouseEntered(MouseEvent e){
                    Object mode=button.getClientProperty("viewMode");
                    if (mode instanceof AppState.ViewMode){
                        // AppState.ViewMode buttonMode=(AppState.ViewMode) mode;
                        Color currentBg=button.getBackground();
                        if (currentBg.equals(primary)){
                            button.setBackground(primary.darker());
                            button.setForeground(Color.BLACK);
                        }
                        else{
                            button.setBackground(hoverBg);
                            button.setForeground(Color.BLACK);
                        }
                    }
                }
                @Override
                public void mouseExited(MouseEvent e){
                    Object mode=button.getClientProperty("viewMode");
                    if (mode instanceof AppState.ViewMode){
                        // AppState.ViewMode buttonMode=(AppState.ViewMode) mode;
                        Color currentBg=button.getBackground();
                        if (currentBg.equals(primary.darker())||currentBg.equals(primary)){
                            button.setBackground(primary);
                            button.setForeground(Color.BLACK);
                        }
                        else{
                            button.setBackground(bg);
                            button.setForeground(Color.BLACK);
                        }
                    }
                }
            });
            return button;
        }
        public static void updateViewModeButtonStyle(JButton button, boolean active, Color primary, Color bg, Color border, Color textColor){
            if (active){
                button.setBackground(primary);
                button.setForeground(Color.BLACK);
                button.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(primary, 1),
                    new EmptyBorder(6, 12, 6, 12)
                ));
            }
            else{
                button.setBackground(bg);
                button.setForeground(textColor);
                button.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(border, 1),
                    new EmptyBorder(6, 12, 6, 12)
                ));
            }
        }
    }
    public static class CalendarRenderer{
        private static Color CALENDAR_TODAY=new Color(219, 237, 255);
        private static Color CALENDAR_WEEKEND=new Color(250, 250, 252);
        private static Color CALENDAR_SELECTED=new Color(240, 248, 255);
        private static Color TEXT_SECONDARY=new Color(108, 117, 125);
        private static Color PRIMARY_RED=new Color(220, 53, 69);
        private static Color PRIMARY_BLUE=new Color(66, 133, 244);
        private static Color PRIMARY_GREEN=new Color(30, 120, 83);
        private static Color NEUTRAL_BG=new Color(255, 255, 255);
        private static Color NEUTRAL_LIGHT=new Color(248, 249, 250);
        private static Color NEUTRAL_MID=new Color(233, 236, 239);
        private static Color TEXT_PRIMARY=new Color(33, 37, 41);
        private static DateTimeFormatter timeFormatter=DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
        public static void renderCalendar(AppState.ViewMode viewMode, LocalDate selectedDate, CalendarController controller, JPanel calendarGrid, JLabel monthYearLabel, JScrollPane calendarScrollPane, LocalDate currentSelectedDate, AppState appState, JFrame parent){
            calendarScrollPane.getViewport().removeAll();
            switch (viewMode){
                case MONTH_VIEW:
                    renderMonthView(selectedDate, controller, calendarGrid, monthYearLabel, calendarScrollPane, currentSelectedDate, appState, parent);
                    break;
                case WEEK_VIEW:
                    calendarScrollPane.setViewportView(calendarGrid);
                    renderWeekView(selectedDate, controller, calendarGrid, monthYearLabel);
                    break;
                case DAY_VIEW:
                    calendarScrollPane.setViewportView(calendarGrid);
                    renderDayView(selectedDate, controller, calendarGrid, monthYearLabel, parent);
                    break;
                case AGENDA_VIEW:
                    calendarScrollPane.setViewportView(calendarGrid);
                    renderAgendaView(selectedDate, controller, calendarGrid, monthYearLabel, parent);
                    break;
            }
        }
        private static void renderMonthView(LocalDate date, CalendarController controller, JPanel calendarGrid, JLabel monthYearLabel, JScrollPane calendarScrollPane, LocalDate currentSelectedDate, AppState appState, JFrame parent){
            DateTimeFormatter monthYearFormatter=DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
            monthYearLabel.setText(date.format(monthYearFormatter));
            calendarGrid.removeAll();
            calendarGrid.setLayout(new GridLayout(0, 7, 1, 1));
            JPanel headerPanel=new JPanel(new GridLayout(1, 7, 1, 1));
            headerPanel.setBackground(NEUTRAL_MID);
            String[] days={"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            for (int i=0;i<days.length;i++){
                JLabel label=new JLabel(days[i].substring(0, 3), SwingConstants.CENTER);
                label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
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
            int viewportWidth=calendarScrollPane.getViewport().getWidth();
            if (viewportWidth<=0){
                viewportWidth=calendarScrollPane.getPreferredSize().width;
            }
            int cellWidth=(viewportWidth - 6) / 7;
            if (cellWidth<40) cellWidth=40;
            LocalDate firstDayOfMonth=date.withDayOfMonth(1);
            int startDayOfWeek=firstDayOfMonth.getDayOfWeek().getValue()%7;
            int daysInMonth=date.lengthOfMonth();
            for (int i=0;i<startDayOfWeek;i++){
                calendarGrid.add(createDayCell(null, null, null, null, cellWidth));
            }
            for (int day=1;day<=daysInMonth;day++){
                LocalDate cellDate=date.withDayOfMonth(day);
                calendarGrid.add(createDayCell(cellDate, controller, appState, parent, cellWidth));
            }
            int totalCells=42;
            int cellsUsed=startDayOfWeek+daysInMonth;
            for (int i=cellsUsed;i<totalCells;i++){
                calendarGrid.add(createDayCell(null, null, null, null, cellWidth));
            }
            calendarGrid.revalidate();
            calendarGrid.repaint();
        }
        private static JPanel createDayCell(LocalDate date, CalendarController controller, AppState appState, JFrame parent, int cellWidth){
            JPanel cell=new JPanel(new BorderLayout(0, 4));
            cell.setBackground(NEUTRAL_BG);
            cell.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(NEUTRAL_MID, 1),
                    new EmptyBorder(6, 4, 6, 4)
            ));
            if (date!=null){
                JLabel dayLabel=new JLabel(String.valueOf(date.getDayOfMonth()), SwingConstants.CENTER);
                dayLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                LocalDate today=LocalDate.now();
                LocalDate selectedDate=appState.getSelectedDate();
                if (date.equals(today)){
                    cell.setBackground(CALENDAR_TODAY);
                    dayLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                    dayLabel.setForeground(PRIMARY_BLUE);
                }
                else if (date.equals(appState.getSelectedDate())){
                    cell.setBackground(CALENDAR_SELECTED);
                    dayLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                    dayLabel.setForeground(TEXT_PRIMARY);
                }
                else if (date.getMonth()!=selectedDate.getMonth()){
                    dayLabel.setForeground(TEXT_SECONDARY);
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
                    int availableWidth=cellWidth - 10;
                    Font eventFont=new Font(Font.SANS_SERIF, Font.PLAIN, 10);
                    FontMetrics metrics=cell.getFontMetrics(eventFont);
                    int maxEvents=Math.min(dayEvents.size(), 2);
                    for (int i=0;i<maxEvents;i++){
                        Event event=dayEvents.get(i);
                        String fullTitle=event.getTitle();
                        String displayText=truncateText(fullTitle, metrics, availableWidth);
                        JLabel eventLabel=new JLabel(displayText);
                        eventLabel.setFont(eventFont);
                        eventLabel.setForeground(PRIMARY_GREEN);
                        eventLabel.setToolTipText(fullTitle);
                        eventLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                        eventsPanel.add(eventLabel);
                    }
                    if (dayEvents.size()>2){
                        int moreCount=dayEvents.size() - 2;
                        String moreText="+"+moreCount+" more";
                        String displayMore=truncateText(moreText, metrics, availableWidth);
                        JLabel moreLabel=new JLabel(displayMore);
                        moreLabel.setFont(eventFont.deriveFont(Font.PLAIN, 8));
                        moreLabel.setForeground(TEXT_SECONDARY);
                        moreLabel.setToolTipText("More events...");
                        moreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
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
                        else if (date.equals(appState.getSelectedDate())){
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
            if (cellWidth>0){
                cell.setPreferredSize(new Dimension(cellWidth, cell.getPreferredSize().height));
            }
            return cell;
        }
        private static String truncateText(String text, FontMetrics metrics, int maxWidth){
            if (metrics.stringWidth(text)<=maxWidth){
                return text;
            }
            String ellipsis="...";
            int ellipsisWidth=metrics.stringWidth(ellipsis);
            int available=maxWidth - ellipsisWidth;
            if (available<=0){
                return ellipsis;
            }
            for (int i=text.length();i>0;i--){
                String sub=text.substring(0, i);
                if (metrics.stringWidth(sub)<=available){
                    return sub+ellipsis;
                }
            }
            return ellipsis;
        }
        private static void renderWeekView(LocalDate date, CalendarController controller, JPanel calendarGrid, JLabel monthYearLabel){
            DateTimeFormatter shortDayFormatter=DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
            LocalDate startOfWeek=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            LocalDate endOfWeek=date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            monthYearLabel.setText("Week of "+startOfWeek.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))+"-"+endOfWeek.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)));
            calendarGrid.removeAll();
            calendarGrid.setLayout(new GridLayout(0, 7, 1, 1));
            for (int i=0;i<7;i++){
                LocalDate dayDate=startOfWeek.plusDays(i);
                JPanel dayHeader=new JPanel(new BorderLayout());
                dayHeader.setBackground(NEUTRAL_MID);
                dayHeader.setBorder(new EmptyBorder(4, 4, 4, 4));
                JLabel dayName=new JLabel(dayDate.format(shortDayFormatter), SwingConstants.CENTER);
                dayName.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                JLabel dateLabel=new JLabel(String.valueOf(dayDate.getDayOfMonth()), SwingConstants.CENTER);
                dateLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
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
                        timeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
                        timeLabel.setForeground(TEXT_SECONDARY);
                        timeSlot.add(timeLabel, BorderLayout.WEST);
                    }
                    List<Event> events=controller.getEventsbyDate(dayDate);
                    for (Event event:events){
                        LocalTime eventStart=event.getStartTime().toLocalTime();
                        LocalTime eventEnd=event.getEndTime().toLocalTime();
                        if (!eventEnd.isBefore(LocalTime.of(hour, 0))&&!eventStart.isAfter(LocalTime.of(hour+1, 0))){
                            JLabel eventLabel=new JLabel(event.getTitle());
                            eventLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
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
        private static void renderDayView(LocalDate date, CalendarController controller, JPanel calendarGrid, JLabel monthYearLabel, JFrame parent){
            DateTimeFormatter dayViewFormatter=DateTimeFormatter.ofPattern("MMMM d (EEEE), yyyy",Locale.ENGLISH);
            monthYearLabel.setText(date.format(dayViewFormatter));
            calendarGrid.removeAll();
            calendarGrid.setLayout(new GridLayout(0,1,0,1));
            for(int hour=0;hour<24;hour++){
                JPanel hourPanel=new JPanel(new BorderLayout());
                hourPanel.setBackground(NEUTRAL_BG);
                hourPanel.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(NEUTRAL_MID, 1),
                    new EmptyBorder(4, 8, 4, 8)
                ));
                JLabel timeLabel=new JLabel(String.format("%02d:00", hour));
                timeLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                timeLabel.setForeground(TEXT_SECONDARY);
                timeLabel.setPreferredSize(new Dimension(60, 0));
                JPanel eventsPanel=new JPanel();
                eventsPanel.setLayout(new BoxLayout(eventsPanel, BoxLayout.Y_AXIS));
                eventsPanel.setBackground(NEUTRAL_BG);
                List<Event> events=controller.getEventsbyDate(date);
                for(Event event:events){
                    LocalTime eventStart=event.getStartTime().toLocalTime();
                    LocalTime eventEnd=event.getEndTime().toLocalTime();
                    if(eventStart.getHour()==hour||(eventStart.getHour()<=hour&&eventEnd.getHour()>hour)){
                        JPanel eventPanel=new JPanel(new BorderLayout());
                        eventPanel.setBackground(new Color(230, 245, 230));
                        eventPanel.setBorder(new EmptyBorder(4, 8, 4, 8));
                        eventPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
                        JLabel titleLabel=new JLabel(event.getTitle());
                        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                        titleLabel.setForeground(PRIMARY_GREEN);
                        String timeRange=eventStart.format(timeFormatter)+"-"+eventEnd.format(timeFormatter);
                        JLabel timeRangeLabel=new JLabel(timeRange);
                        timeRangeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
                        timeRangeLabel.setForeground(TEXT_SECONDARY);
                        eventPanel.add(titleLabel, BorderLayout.NORTH);
                        eventPanel.add(timeRangeLabel, BorderLayout.SOUTH);
                        eventPanel.addMouseListener(new MouseAdapter(){
                            @Override
                            public void mouseClicked(MouseEvent e){
                                if(e.getClickCount()==2){
                                    EventEditor.showEditEventDialog(parent, controller, event, new Color(66, 133, 244), new Color(220, 53, 69), new Color(255, 255, 255), new Color(233, 236, 239), new Color(33, 37, 41), new Color(108, 117, 125));
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
        private static void renderAgendaView(LocalDate date, CalendarController controller, JPanel calendarGrid, JLabel monthYearLabel, JFrame parent){
            DateTimeFormatter monthYearFormatter=DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
            DateTimeFormatter dateFormatter=DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH);
            monthYearLabel.setText("Agenda - "+date.format(monthYearFormatter));
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
                    dateLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
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
                JLabel timeLabel=new JLabel(event.getStartTime().format(timeFormatter)+" - "+event.getEndTime().format(timeFormatter));
                timeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                timeLabel.setForeground(TEXT_SECONDARY);
                timeLabel.setPreferredSize(new Dimension(120, 0));
                JLabel titleLabel=new JLabel(event.getTitle());
                titleLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                titleLabel.setForeground(TEXT_PRIMARY);
                JPopupMenu eventMenu=new JPopupMenu();
                JMenuItem deleteItem=new JMenuItem("Delete");
                deleteItem.addActionListener(e->{
                    int confirm=JOptionPane.showConfirmDialog(parent,
                        "Delete '"+event.getTitle()+"'?",
                        "Confirm Delete",
                        JOptionPane.YES_NO_OPTION);
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
                            EventEditor.showEditEventDialog(parent, controller, event,
                                PRIMARY_BLUE, PRIMARY_RED, NEUTRAL_BG, NEUTRAL_MID,
                                TEXT_PRIMARY, TEXT_SECONDARY);
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
                emptyLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 14));
                emptyLabel.setForeground(TEXT_SECONDARY);
                emptyLabel.setBorder(new EmptyBorder(100, 0, 0, 0));
                agendaPanel.add(emptyLabel);
            }
            calendarGrid.add(agendaPanel, BorderLayout.CENTER);
            calendarGrid.revalidate();
            calendarGrid.repaint();
        }
    }
    public static void showAddEventDialog(JFrame parent, CalendarController controller, LocalDate defaultDate, Color primary, Color danger, Color bg, Color border, Color textPrimary, Color textSecondary){
        showEventDialog(parent, controller, null, defaultDate, primary, danger, bg, border, textPrimary, textSecondary);
    }
    public static void showEditEventDialog(JFrame parent, CalendarController controller, Event existingEvent, Color primary, Color danger, Color bg, Color border, Color textPrimary, Color textSecondary){
        showEventDialog(parent, controller, existingEvent, existingEvent.getDate(), primary, danger, bg, border, textPrimary, textSecondary);
    }
    private static void showEventDialog(JFrame parent, CalendarController controller, Event existingEvent, LocalDate defaultDate, Color primary, Color danger, Color bg, Color border, Color textPrimary, Color textSecondary){
        boolean isEdit=existingEvent!=null;
        JDialog dialog=new JDialog(parent, isEdit?"Edit Event":"Add New Event", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(450, 350);
        dialog.setLocationRelativeTo(parent);
        JPanel formPanel=new JPanel(new GridBagLayout());
        formPanel.setBackground(bg);
        formPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc=new GridBagConstraints();
        gbc.fill=GridBagConstraints.HORIZONTAL;
        gbc.insets=new Insets(6, 6, 6, 6);
        gbc.gridx=0;
        gbc.gridy=0;
        JLabel titleLabel=new JLabel("Event Title:");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        titleLabel.setForeground(textPrimary);
        formPanel.add(titleLabel, gbc);
        gbc.gridx=1;
        gbc.gridwidth=2;
        gbc.weightx=1.0;
        JTextField titleField=new JTextField(20);
        titleField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        if (isEdit){
            titleField.setText(existingEvent.getTitle());
        }
        formPanel.add(titleField, gbc);
        gbc.gridx=0;
        gbc.gridy=1;
        gbc.gridwidth=1;
        gbc.weightx=0;
        JLabel dateLabel=new JLabel("Date:");
        dateLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        dateLabel.setForeground(textPrimary);
        formPanel.add(dateLabel, gbc);
        gbc.gridx=1;
        gbc.gridwidth=2;
        gbc.weightx=1.0;
        JTextField dateField=new JTextField(20);
        dateField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        dateField.setText(isEdit?existingEvent.getDate().toString():defaultDate.toString());
        formPanel.add(dateField, gbc);
        gbc.gridx=0;
        gbc.gridy=2;
        gbc.gridwidth=1;
        gbc.weightx=0;
        JLabel startLabel=new JLabel("Start Time:");
        startLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        startLabel.setForeground(textPrimary);
        formPanel.add(startLabel, gbc);
        gbc.gridx=1;
        gbc.gridwidth=1;
        gbc.weightx=0.5;
        JTextField startTimeField=new JTextField(10);
        startTimeField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
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
        endLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        endLabel.setForeground(textPrimary);
        formPanel.add(endLabel, gbc);
        gbc.gridx=3;
        gbc.gridwidth=1;
        gbc.weightx=0.5;
        JTextField endTimeField=new JTextField(10);
        endTimeField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
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
        formatHint.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        formatHint.setForeground(textSecondary);
        formPanel.add(formatHint, gbc);
        JPanel buttonPanel=new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(bg);
        if (isEdit){
            JButton deleteBtn=new JButton("Delete");
            deleteBtn.setBackground(danger);
            deleteBtn.setForeground(Color.BLACK);
            deleteBtn.addActionListener(new java.awt.event.ActionListener(){
                public void actionPerformed(java.awt.event.ActionEvent e){
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
                }
            });
            buttonPanel.add(deleteBtn);
        }
        JButton cancelBtn=UIComponentFactory.createTextButton("Cancel", bg, bg.brighter(), border, textPrimary);
        cancelBtn.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                dialog.dispose();
            }
        });
        JButton saveBtn=UIComponentFactory.createPrimaryButton(isEdit?"Update Event":"Create Event", primary);
        saveBtn.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
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
                                new java.util.function.Consumer<Event>(){
                                    public void accept(Event updatedEvent){
                                        JOptionPane.showMessageDialog(dialog,
                                            "Event updated successfully!",
                                            "Success",
                                            JOptionPane.INFORMATION_MESSAGE);
                                        dialog.dispose();
                                    }
                                },
                                new Runnable(){
                                    public void run(){
                                        JOptionPane.showMessageDialog(dialog,
                                            "Could not update event. There may be overlapping events.",
                                            "Conflict Detected",
                                            JOptionPane.ERROR_MESSAGE);
                                    }
                                }
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
                        "Date: YYYY-MM-DD (e.g., 2026-01-22)\n" +
                        "Time: HH:MM (24-hour, e.g., 14:30)",
                        "Input Error",
                        JOptionPane.ERROR_MESSAGE);
                }
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
}