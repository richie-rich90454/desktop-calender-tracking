package ui;

import app.CalendarController;
import ui.EventEditor.UIComponentFactory;
import audio.AudioPlayerPanel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public class CalendarComponents{
    private static final Color NEUTRAL_BG=new Color(255, 255, 255);
    private static final Color NEUTRAL_LIGHT=new Color(248, 249, 250);
    private static final Color NEUTRAL_MID=new Color(233, 236, 239);
    private static final Color NEUTRAL_DARK=new Color(222, 226, 230);
    private static final Color TEXT_PRIMARY=new Color(33, 37, 41);
    // private static final Color TEXT_SECONDARY=new Color(108, 117, 125);
    // private static final Color PRIMARY_BLUE=new Color(66, 133, 244);
    private static final Color PRIMARY_GREEN=new Color(30, 120, 83);
    public static JPanel createNavigationPanel(JButton prevBtn, JButton todayBtn, JButton nextBtn,JLabel monthYearLabel, JPanel viewModePanel){
        JPanel navigationPanel=new JPanel(new BorderLayout(15, 0));
        navigationPanel.setBackground(NEUTRAL_BG);
        navigationPanel.setBorder(new EmptyBorder(12, 20, 12, 20));
        JPanel navButtons=new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        navButtons.setBackground(NEUTRAL_BG);
        navButtons.add(prevBtn);
        navButtons.add(todayBtn);
        JButton aiPopulateBtn=UIComponentFactory.createPrimaryButton("Populate by AI", PRIMARY_GREEN);
        navButtons.add(aiPopulateBtn);
        navButtons.add(nextBtn);
        navigationPanel.add(navButtons, BorderLayout.WEST);
        navigationPanel.add(monthYearLabel, BorderLayout.CENTER);
        navigationPanel.add(viewModePanel, BorderLayout.EAST);
        return navigationPanel;
    }
    public static JPanel createContentPanel(JPanel calendarGrid, JScrollPane eventsScrollPane,JLabel selectedDateLabel, JLabel eventCountLabel,JButton addEventButton, CalendarController controller,JScrollPane calendarScrollPane){
        JPanel contentPanel=new JPanel(new BorderLayout(20, 0));
        contentPanel.setBackground(NEUTRAL_BG);
        contentPanel.setBorder(new EmptyBorder(0, 20, 20, 20));
        JPanel calendarContainer=new JPanel(new BorderLayout());
        calendarContainer.setBackground(NEUTRAL_BG);
        calendarContainer.setBorder(BorderFactory.createCompoundBorder(new LineBorder(NEUTRAL_DARK, 1),new EmptyBorder(15, 15, 15, 15)));
        calendarScrollPane.setBorder(new EmptyBorder(10, 0, 0, 0));
        calendarScrollPane.getViewport().setBackground(NEUTRAL_MID);
        calendarContainer.add(calendarScrollPane, BorderLayout.CENTER);
        JPanel sidebarPanel=createSidebarPanel(eventsScrollPane, selectedDateLabel,eventCountLabel, addEventButton, controller);
        contentPanel.add(calendarContainer, BorderLayout.CENTER);
        contentPanel.add(sidebarPanel, BorderLayout.EAST);
        return contentPanel;
    }
    private static JPanel createSidebarPanel(JScrollPane eventsScrollPane, JLabel selectedDateLabel,JLabel eventCountLabel, JButton addEventButton, CalendarController controller){
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
        AudioPlayerPanel audioPlayerPanel=new AudioPlayerPanel();
        JTabbedPane sidebarTabs=new JTabbedPane(JTabbedPane.TOP);
        sidebarTabs.setBackground(NEUTRAL_BG);
        sidebarTabs.setForeground(TEXT_PRIMARY);
        sidebarTabs.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JPanel eventsTab=new JPanel(new BorderLayout());
        eventsTab.setBackground(NEUTRAL_BG);
        eventsTab.add(eventsPanel, BorderLayout.CENTER);
        eventsTab.add(addEventButton, BorderLayout.SOUTH);
        sidebarTabs.addTab("Events", eventsTab);
        sidebarTabs.addTab("Audio Player", audioPlayerPanel);
        sidebarPanel.add(sidebarHeader, BorderLayout.NORTH);
        sidebarPanel.add(sidebarTabs, BorderLayout.CENTER);
        return sidebarPanel;
    }
    public static JPanel createStatusBar(JLabel statusLabel, JLabel unsavedLabel){
        JPanel statusBar=new JPanel(new BorderLayout());
        statusBar.setBackground(NEUTRAL_LIGHT);
        statusBar.setBorder(new EmptyBorder(6, 20, 6, 20));
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(unsavedLabel, BorderLayout.EAST);
        return statusBar;
    }
}