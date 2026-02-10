package ui;

import javax.swing.*;
import java.awt.*;

public class EventDotIcon implements Icon{
    private Color color;
    private static final int SIZE=8;
    public EventDotIcon(Color color){
        this.color=color;
    }
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y){
        Graphics2D g2=(Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.fillOval(x, y+(c.getHeight() -SIZE)/2, SIZE, SIZE);
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