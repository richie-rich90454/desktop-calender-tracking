package ui;

import ai.ProgressCallback;
import model.Event;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
/**
 * A modal dialog that displays real-time progress of AI event generation operations.
 * Implements the ProgressCallback interface to receive status updates and provides
 * a user-friendly interface with timestamps, cancellation capability, and output copying.
 * The dialog uses Swing's Event Dispatch Thread for all UI updates and maintains
 * thread safety through SwingUtilities.invokeLater calls.
 */
public class AIProgressDialog extends JDialog implements ProgressCallback {
    private final JTextArea outputArea;
    private final JButton cancelButton;
    private final JButton copyButton;
    private boolean cancelled;
    private static final DateTimeFormatter TIME_FORMATTER=DateTimeFormatter.ofPattern("HH:mm:ss");
    public AIProgressDialog(Frame owner){
        super(owner, "Generating AI Events", true);
        outputArea=new JTextArea();
        cancelButton=new JButton("Cancel");
        copyButton=new JButton("Copy Output");
        cancelled=false;
        setupUI();
        setupEvents();
    }
    private void setupUI(){
        setLayout(new BorderLayout(10, 10));
        JPanel mainPanel=new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        JScrollPane scrollPane=new JScrollPane(outputArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(new Dimension(500, 300));
        JPanel buttonPanel=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(copyButton);
        buttonPanel.add(cancelButton);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(mainPanel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(getOwner());
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    }
    private void setupEvents(){
        cancelButton.addActionListener(e->{
            cancelled=true;
            cancelButton.setEnabled(false);
            cancelButton.setText("Cancelling...");
            appendLine("[CANCELLED] User requested cancellation");
        });
        copyButton.addActionListener(e->{
            String text=outputArea.getText();
            if (text!=null&&!text.isEmpty()){
                java.awt.datatransfer.StringSelection selection=new java.awt.datatransfer.StringSelection(text);
                java.awt.datatransfer.Clipboard clipboard=Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(selection, selection);
                appendLine("[INFO] Output copied to clipboard");
            }
        });
    }
    private void appendLine(String line){
        SwingUtilities.invokeLater(()->{
            String timestamp=LocalTime.now().format(TIME_FORMATTER);
            outputArea.append("[" + timestamp + "] " + line + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }
    @Override
    public void update(String message){
        appendLine(message);
    }
    @Override
    public void updateSuccess(String message){
        appendLine("✓ " + message);
    }
    @Override
    public void updateWarning(String message){
        appendLine("⚠ " + message);
    }
    @Override
    public void updateError(String message){
        appendLine("✗ " + message);
    }
    @Override
    public void updateEvent(Event event){
        String line="• " + event.getTitle() + " (" + event.getDate() + ", " + event.getStartTime().toLocalTime() + "-" + event.getEndTime().toLocalTime() + ")";
        appendLine(line);
    }
    @Override
    public boolean isCancelled(){
        return cancelled;
    }
    public void showDialog(){
        appendLine("AI Progress Dialog initialized...");
        setVisible(true);
    }
    public void closeDialog(){
        SwingUtilities.invokeLater(()->{
            setVisible(false);
            dispose();
        });
    }
}