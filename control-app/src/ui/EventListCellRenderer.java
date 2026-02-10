package ui;

import model.Event;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class EventListCellRenderer extends DefaultListCellRenderer{
    private static final Color DISABLED_TEXT=new Color(134, 142, 150);
    private static final DateTimeFormatter timeFormatter=DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,boolean isSelected, boolean cellHasFocus){
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
            setText(String.format("<html><b>%s</b><br/><font color='#6C757D' size='-1'>%s-%s</font></html>",event.getTitle(), startTime, endTime));
            setIcon(new EventDotIcon(new Color(30, 120, 83)));
            setBorder(new EmptyBorder(10, 10, 10, 10));
            setFont(new Font("Segoe UI", Font.PLAIN, 13));
        }
        return this;
    }
}