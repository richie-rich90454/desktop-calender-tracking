package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Preferences/settings dialog
 */
public class PreferencesDialog extends JDialog {
    private static Color NEUTRAL_BG=new Color(255, 255, 255);
    private static Color NEUTRAL_MID=new Color(233, 236, 239);
    private static Color PRIMARY_BLUE=new Color(66, 133, 244);
    private static Color TEXT_PRIMARY=new Color(33, 37, 41);
    private static Color TEXT_SECONDARY=new Color(108, 117, 125);
    public PreferencesDialog(JFrame parent){
        super(parent, "Preferences", true);
        setSize(500, 400);
        setLocationRelativeTo(parent);
        setResizable(false);
        JTabbedPane tabbedPane=new JTabbedPane();
        tabbedPane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        tabbedPane.addTab("General", createGeneralPanel());
        tabbedPane.addTab("Appearance", createAppearancePanel());
        tabbedPane.addTab("Calendar", createCalendarPanel());
        tabbedPane.addTab("About", createAboutPanel());
        JPanel buttonPanel=new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(NEUTRAL_BG);
        buttonPanel.setBorder(new EmptyBorder(10, 20, 20, 20));
        JButton closeButton=new JButton("Close");
        closeButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        closeButton.setBackground(NEUTRAL_BG);
        closeButton.setForeground(TEXT_PRIMARY);
        closeButton.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(NEUTRAL_MID, 1),
            new EmptyBorder(6, 12, 6, 12)
        ));
        closeButton.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                dispose();
            }
        });
        JButton applyButton=new JButton("Apply");
        applyButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        applyButton.setBackground(PRIMARY_BLUE);
        applyButton.setForeground(TEXT_PRIMARY);
        applyButton.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(PRIMARY_BLUE, 1),
            new EmptyBorder(6, 12, 6, 12)
        ));
        applyButton.addActionListener(new java.awt.event.ActionListener(){
            public void actionPerformed(java.awt.event.ActionEvent e){
                JOptionPane.showMessageDialog(PreferencesDialog.this, 
                    "Preferences applied!", "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        buttonPanel.add(closeButton);
        buttonPanel.add(applyButton);
        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }
    private JPanel createGeneralPanel(){
        JPanel panel=new JPanel(new GridBagLayout());
        panel.setBackground(NEUTRAL_BG);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc=new GridBagConstraints();
        gbc.fill=GridBagConstraints.HORIZONTAL;
        gbc.insets=new Insets(8, 8, 8, 8);
        gbc.gridx=0;
        gbc.gridy=0;
        gbc.weightx=0;
        JLabel weekStartLabel=new JLabel("Week starts on:");
        weekStartLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        panel.add(weekStartLabel, gbc);
        gbc.gridx=1;
        gbc.weightx=1.0;
        String[] weekDays={"Sunday", "Monday"};
        JComboBox<String> weekStartCombo=new JComboBox<String>(weekDays);
        weekStartCombo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        panel.add(weekStartCombo, gbc);
        gbc.gridx=0;
        gbc.gridy=1;
        gbc.weightx=0;
        JLabel timeFormatLabel=new JLabel("Time format:");
        timeFormatLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        panel.add(timeFormatLabel, gbc);
        gbc.gridx=1;
        gbc.weightx=1.0;
        String[] timeFormats={"12-hour (AM/PM)", "24-hour"};
        JComboBox<String> timeFormatCombo=new JComboBox<String>(timeFormats);
        timeFormatCombo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        panel.add(timeFormatCombo, gbc);
        gbc.gridx=0;
        gbc.gridy=2;
        gbc.weightx=0;
        JLabel defaultViewLabel=new JLabel("Default view:");
        defaultViewLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        panel.add(defaultViewLabel, gbc);
        gbc.gridx=1;
        gbc.weightx=1.0;
        String[] views={"Month", "Week", "Day", "Agenda"};
        JComboBox<String> defaultViewCombo=new JComboBox<String>(views);
        defaultViewCombo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        panel.add(defaultViewCombo, gbc);
        gbc.gridx=0;
        gbc.gridy=3;
        gbc.gridwidth=2;
        gbc.fill=GridBagConstraints.NONE;
        JCheckBox showWeekNumbers=new JCheckBox("Show week numbers");
        showWeekNumbers.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        showWeekNumbers.setBackground(NEUTRAL_BG);
        panel.add(showWeekNumbers, gbc);
        return panel;
    }
    private JPanel createAppearancePanel(){
        JPanel panel=new JPanel(new GridBagLayout());
        panel.setBackground(NEUTRAL_BG);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc=new GridBagConstraints();
        gbc.fill=GridBagConstraints.HORIZONTAL;
        gbc.insets=new Insets(8, 8, 8, 8);
        gbc.gridx=0;
        gbc.gridy=0;
        gbc.weightx=0;
        JLabel themeLabel=new JLabel("Theme:");
        themeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        panel.add(themeLabel, gbc);
        gbc.gridx=1;
        gbc.weightx=1.0;
        String[] themes={"Light", "Dark", "System"};
        JComboBox<String> themeCombo=new JComboBox<String>(themes);
        themeCombo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        panel.add(themeCombo, gbc);
        gbc.gridx=0;
        gbc.gridy=1;
        gbc.weightx=0;
        JLabel fontSizeLabel=new JLabel("Font size:");
        fontSizeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        panel.add(fontSizeLabel, gbc);
        gbc.gridx=1;
        gbc.weightx=1.0;
        JSlider fontSizeSlider=new JSlider(10, 16, 13);
        fontSizeSlider.setMajorTickSpacing(2);
        fontSizeSlider.setMinorTickSpacing(1);
        fontSizeSlider.setPaintTicks(true);
        fontSizeSlider.setPaintLabels(true);
        panel.add(fontSizeSlider, gbc);
        gbc.gridx=0;
        gbc.gridy=2;
        gbc.gridwidth=2;
        gbc.fill=GridBagConstraints.NONE;
        JCheckBox compactView=new JCheckBox("Compact view (show more events per day)");
        compactView.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        compactView.setBackground(NEUTRAL_BG);
        panel.add(compactView, gbc);
        gbc.gridy=3;
        JCheckBox colorCode=new JCheckBox("Color code event types");
        colorCode.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        colorCode.setBackground(NEUTRAL_BG);
        panel.add(colorCode, gbc);
        return panel;
    }
    private JPanel createCalendarPanel(){
        JPanel panel=new JPanel(new GridBagLayout());
        panel.setBackground(NEUTRAL_BG);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc=new GridBagConstraints();
        gbc.fill=GridBagConstraints.HORIZONTAL;
        gbc.insets=new Insets(8, 8, 8, 8);
        gbc.gridx=0;
        gbc.gridy=0;
        gbc.weightx=0;
        JLabel workHoursLabel=new JLabel("Work hours:");
        workHoursLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        panel.add(workHoursLabel, gbc);
        gbc.gridx=1;
        gbc.weightx=1.0;
        String[] hours={"8:00 AM - 5:00 PM", "9:00 AM - 6:00 PM", "Custom"};
        JComboBox<String> workHoursCombo=new JComboBox<String>(hours);
        workHoursCombo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        panel.add(workHoursCombo, gbc);
        gbc.gridx=0;
        gbc.gridy=1;
        gbc.weightx=0;
        JLabel defaultDurationLabel=new JLabel("Default event duration:");
        defaultDurationLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        panel.add(defaultDurationLabel, gbc);
        gbc.gridx=1;
        gbc.weightx=1.0;
        String[] durations={"30 minutes", "1 hour", "1.5 hours", "2 hours"};
        JComboBox<String> durationCombo=new JComboBox<String>(durations);
        durationCombo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        panel.add(durationCombo, gbc);
        gbc.gridx=0;
        gbc.gridy=2;
        gbc.gridwidth=2;
        gbc.fill=GridBagConstraints.NONE;
        JCheckBox showHolidays=new JCheckBox("Show holidays");
        showHolidays.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        showHolidays.setBackground(NEUTRAL_BG);
        panel.add(showHolidays, gbc);
        gbc.gridy=3;
        JCheckBox allowOverlap=new JCheckBox("Allow overlapping events");
        allowOverlap.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        allowOverlap.setBackground(NEUTRAL_BG);
        panel.add(allowOverlap, gbc);
        return panel;
    }
    private JPanel createAboutPanel(){
        JPanel panel=new JPanel(new BorderLayout());
        panel.setBackground(NEUTRAL_BG);
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));
        JLabel titleLabel=new JLabel("Calendar App", SwingConstants.CENTER);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        titleLabel.setForeground(TEXT_PRIMARY);
        JLabel versionLabel=new JLabel("Version 1.0.0", SwingConstants.CENTER);
        versionLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        versionLabel.setForeground(TEXT_SECONDARY);
        versionLabel.setBorder(new EmptyBorder(10, 0, 20, 0));
        JLabel copyrightLabel=new JLabel("© 2026 richie-rich90454.", SwingConstants.CENTER);
        copyrightLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        copyrightLabel.setForeground(TEXT_SECONDARY);
        JPanel infoPanel=new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(NEUTRAL_BG);
        infoPanel.add(titleLabel);
        infoPanel.add(versionLabel);
        String currentYear=LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy", Locale.ENGLISH));
        JLabel yearLabel=new JLabel("Current Year: " + currentYear, SwingConstants.CENTER);
        yearLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        yearLabel.setForeground(TEXT_SECONDARY);
        yearLabel.setBorder(new EmptyBorder(20, 0, 10, 0));
        panel.add(infoPanel, BorderLayout.CENTER);
        panel.add(yearLabel, BorderLayout.SOUTH);
        return panel;
    }
    public static void showDialog(JFrame parent){
        PreferencesDialog dialog=new PreferencesDialog(parent);
        dialog.setVisible(true);
    }
}