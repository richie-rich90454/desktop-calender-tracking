package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import app.CalendarController;
import java.awt.*;

public class MenuBuilder{
    private static final Color NEUTRAL_BG=new Color(255, 255, 255);
    private static final Color TEXT_PRIMARY=new Color(33, 37, 41);
    private static final Color TEXT_SECONDARY=new Color(108, 117, 125);
    private static final Color NEUTRAL_LIGHT=new Color(248, 249, 250);
    private static final Color NEUTRAL_MID=new Color(233, 236, 239);
    public static JMenuBar createMenuBar(CalendarFrame frame, CalendarController controller){
        JMenuBar menuBar=new JMenuBar();
        JMenu fileMenu=createFileMenu(frame, controller);
        JMenu toolsMenu=createToolsMenu(frame); // Create the Tools menu
        JMenu helpMenu=createHelpMenu(frame);
        menuBar.add(fileMenu);
        menuBar.add(toolsMenu); // Add the Tools menu to the menubar
        menuBar.add(helpMenu);
        return menuBar;
    }
    private static JMenu createFileMenu(CalendarFrame frame, CalendarController controller){
        JMenu fileMenu=new JMenu("File");
        fileMenu.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JMenuItem saveMenuItem=new JMenuItem("Save");
        saveMenuItem.addActionListener(e -> handleSaveAction(frame, controller));
        JMenuItem preferencesMenuItem=new JMenuItem("Preferences");
        preferencesMenuItem.addActionListener(e -> PreferencesDialog.showDialog(frame));
        JMenuItem exitMenuItem=new JMenuItem("Exit");
        exitMenuItem.addActionListener(e -> frame.handleWindowClosing());
        fileMenu.add(saveMenuItem);
        fileMenu.addSeparator();
        fileMenu.add(preferencesMenuItem);
        fileMenu.addSeparator();
        fileMenu.add(exitMenuItem);
        return fileMenu;
    }

    // New helper method to create the Tools menu
    private static JMenu createToolsMenu(CalendarFrame frame) {
        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JMenuItem showAudioPlayerItem = new JMenuItem("Show Audio Player");
        showAudioPlayerItem.addActionListener(e -> frame.showAudioPlayer()); // Link to the method in CalendarFrame

        toolsMenu.add(showAudioPlayerItem);

        return toolsMenu;
    }

    private static JMenu createHelpMenu(CalendarFrame frame){
        JMenu helpMenu=new JMenu("Help");
        helpMenu.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JMenuItem aboutMenuItem=new JMenuItem("About");
        aboutMenuItem.addActionListener(e -> showAboutDialog(frame));
        JMenuItem helpMenuItem=new JMenuItem("Help Contents");
        helpMenuItem.addActionListener(e -> showHelpDialog(frame));
        helpMenu.add(helpMenuItem);
        helpMenu.addSeparator();
        helpMenu.add(aboutMenuItem);
        return helpMenu;
    }
    private static void handleSaveAction(CalendarFrame frame, CalendarController controller){
        boolean saved=controller.saveCalendar();
        if (saved){
            JOptionPane.showMessageDialog(frame,"Calendar saved to: "+controller.getStorage().getStoragePath(),"Save Successful",JOptionPane.INFORMATION_MESSAGE);
        }
        else{
            JOptionPane.showMessageDialog(frame,"Failed to save calendar","Save Error",JOptionPane.ERROR_MESSAGE);
        }
    }
    private static void showAboutDialog(CalendarFrame frame){
        JDialog aboutDialog=new JDialog(frame, "About Calendar App", true);
        aboutDialog.setSize(400, 300);
        aboutDialog.setLocationRelativeTo(frame);
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
        aboutText.setText("Calendar Application\n\nA simple calendar app for managing events.\n\n"+"Features:\n• Add, edit, delete events\n• Multiple view modes (Day, Week, Month, Agenda)\n"+"• Save/Load calendar data\n• Intuitive user interface\n\n"+"© 2026 richie-rich90454.");
        aboutText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        aboutText.setForeground(TEXT_PRIMARY);
        aboutText.setBackground(NEUTRAL_BG);
        aboutText.setEditable(false);
        aboutText.setLineWrap(true);
        aboutText.setWrapStyleWord(true);
        aboutText.setBorder(new EmptyBorder(10, 10, 10, 10));
        JScrollPane scrollPane=new JScrollPane(aboutText);
        scrollPane.setBorder(null);
        JButton closeButton=EventEditor.UIComponentFactory.createTextButton("Close",NEUTRAL_BG, NEUTRAL_LIGHT, NEUTRAL_MID, TEXT_PRIMARY);
        closeButton.addActionListener(e -> aboutDialog.dispose());
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
    private static void showHelpDialog(CalendarFrame frame){
        JOptionPane.showMessageDialog(frame,"<html><div style='width:300px;'><h3>Calendar App Help</h3>"+"<p><b>Adding Events:</b> Click '+ New Event' button or double-click on a day.</p>"+"<p><b>Editing Events:</b> Double-click an event or right-click and select 'Edit'.</p>"+"<p><b>Deleting Events:</b> Right-click an event and select 'Delete'.</p>"+"<p><b>Navigation:</b> Use Previous/Next buttons or click on dates in the calendar.</p>"+"<p><b>View Modes:</b> Switch between Day, Week, Month, and Agenda views.</p>"+"<p><b>Saving:</b> Click the Save button to save your calendar.</p></div></html>","Help", JOptionPane.INFORMATION_MESSAGE);
    }
}