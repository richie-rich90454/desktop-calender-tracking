package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileFilter;
import audio.AudioFileManager;
import audio.AudioPlayerEngine;
import audio.AudioTrack;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Self-contained dockable audio player panel with playlist display, playback
 * controls, file upload, and progress visualization. Integrates audio file
 * management with playback engine for complete audio player functionality in
 * Swing applications with modern UI design.
 */

public class AudioPlayerFrame extends JPanel{
    private static final Color NEUTRAL_BG=new Color(255, 255, 255);
    private static final Color NEUTRAL_LIGHT=new Color(248, 249, 250);
    private static final Color NEUTRAL_MID=new Color(233, 236, 239);
    private static final Color TEXT_PRIMARY=new Color(33, 37, 41);
    private static final Color TEXT_SECONDARY=new Color(108, 117, 125);
    private static final Color ACCENT_COLOR=new Color(40, 167, 69);
    private static final Color ACCENT_DARK=new Color(35, 145, 59);
    private final AudioPlayerEngine audioEngine;
    private final AudioFileManager fileManager;
    private final DefaultListModel<AudioTrack> playlistModel;
    private final JList<AudioTrack> playlistList;
    private final JButton playButton;
    private final JButton pauseButton;
    private final JButton stopButton;
    private final JButton previousButton;
    private final JButton nextButton;
    private final JButton uploadButton;
    private final JButton deleteButton;
    private final JButton clearButton;
    private final JSlider progressSlider;
    private final JLabel currentTimeLabel;
    private final JLabel totalTimeLabel;
    private final JLabel nowPlayingLabel;
    private final JLabel statusLabel;
    private final Timer playbackTimer;
    private List<AudioTrack> playlist;
    public AudioPlayerFrame(){
        this.audioEngine=new AudioPlayerEngine();
        this.fileManager=new AudioFileManager();
        this.playlist=new ArrayList<>();
        this.playlistModel=new DefaultListModel<>();
        this.playlistList=new JList<>(playlistModel);
        this.playButton=createControlButton("Play", "Play selected track");
        this.pauseButton=createControlButton("Pause", "Pause playback");
        this.stopButton=createControlButton("Stop", "Stop playback");
        this.previousButton=createControlButton("<<", "Previous track");
        this.nextButton=createControlButton(">>", "Next track");
        this.uploadButton=createActionButton("Upload", "Upload audio file");
        this.deleteButton=createActionButton("Delete", "Delete selected track");
        this.clearButton=createActionButton("Clear All", "Clear all tracks");
        this.progressSlider=new JSlider(0, 100, 0);
        this.currentTimeLabel=new JLabel("00:00");
        this.totalTimeLabel=new JLabel("00:00");
        this.nowPlayingLabel=new JLabel("Not playing");
        this.statusLabel=new JLabel("Ready");
        this.playbackTimer=new Timer(true);
        initializeComponents();
        setupLayout();
        setupListeners();
        loadPlaylist();
        startPlaybackTimer();
        UIManager.put("Button.disabledForeground", new Color(150, 150, 150));
    }
    private void initializeComponents(){
        playlistList.setCellRenderer(new AudioTrackCellRenderer());
        playlistList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playlistList.setBackground(NEUTRAL_BG);
        playlistList.setBorder(new EmptyBorder(5, 5, 5, 5));
        playlistList.setVisibleRowCount(8);
        progressSlider.setBackground(NEUTRAL_BG);
        progressSlider.setEnabled(false);
        progressSlider.setPreferredSize(new Dimension(200, 20));
        currentTimeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        currentTimeLabel.setForeground(TEXT_SECONDARY);
        totalTimeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        totalTimeLabel.setForeground(TEXT_SECONDARY);
        nowPlayingLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        nowPlayingLabel.setForeground(ACCENT_COLOR);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        statusLabel.setForeground(TEXT_SECONDARY);
    }
    private JButton createControlButton(String text, String tooltip){
        JButton button=new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        button.setBackground(NEUTRAL_BG);
        button.setForeground(TEXT_PRIMARY);
        button.setBorder(BorderFactory.createCompoundBorder(new LineBorder(NEUTRAL_MID, 1),new EmptyBorder(6, 12, 6, 12)));
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new java.awt.event.MouseAdapter(){
            public void mouseEntered(java.awt.event.MouseEvent evt){
                button.setBackground(NEUTRAL_LIGHT);
            }
            public void mouseExited(java.awt.event.MouseEvent evt){
                button.setBackground(NEUTRAL_BG);
            }
        });
        return button;
    }
    private JButton createActionButton(String text, String tooltip){
        JButton button=new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        button.setBackground(new Color(220, 220, 220));
        button.setForeground(TEXT_PRIMARY);
        button.setBorder(BorderFactory.createCompoundBorder(new LineBorder(NEUTRAL_MID, 1),new EmptyBorder(4, 8, 4, 8)));
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new java.awt.event.MouseAdapter(){
            public void mouseEntered(java.awt.event.MouseEvent evt){
                button.setBackground(new Color(200, 200, 200));
            }
            public void mouseExited(java.awt.event.MouseEvent evt){
                button.setBackground(new Color(220, 220, 220));
            }
        });
        return button;
    }
    private void setupLayout(){
        setLayout(new BorderLayout(10, 10));
        setBackground(NEUTRAL_BG);
        setBorder(new EmptyBorder(15, 15, 15, 15));
        setPreferredSize(new Dimension(480, 550));
        JPanel headerPanel=new JPanel(new BorderLayout());
        headerPanel.setBackground(NEUTRAL_BG);
        headerPanel.setBorder(new EmptyBorder(0, 0, 15, 0));
        JLabel titleLabel=new JLabel("Audio Player");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        titleLabel.setForeground(TEXT_PRIMARY);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(statusLabel, BorderLayout.EAST);
        JScrollPane playlistScroll=new JScrollPane(playlistList);
        playlistScroll.setBorder(BorderFactory.createCompoundBorder(new LineBorder(NEUTRAL_MID, 1),new EmptyBorder(2, 2, 2, 2)));
        playlistScroll.setBackground(NEUTRAL_BG);
        playlistScroll.getViewport().setBackground(NEUTRAL_BG);
        JPanel actionPanel=new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        actionPanel.setBackground(NEUTRAL_BG);
        actionPanel.add(uploadButton);
        actionPanel.add(deleteButton);
        actionPanel.add(clearButton);
        JPanel transportPanel=new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        transportPanel.setBackground(NEUTRAL_BG);
        transportPanel.add(previousButton);
        playButton.setBackground(ACCENT_COLOR);
        playButton.setForeground(TEXT_PRIMARY);
        playButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        playButton.addMouseListener(new java.awt.event.MouseAdapter(){
            public void mouseEntered(java.awt.event.MouseEvent evt){
                playButton.setBackground(ACCENT_DARK);
            }
            public void mouseExited(java.awt.event.MouseEvent evt){
                playButton.setBackground(ACCENT_COLOR);
            }
        });
        playButton.addPropertyChangeListener("enabled", evt -> {
            if (!playButton.isEnabled()){
                playButton.setBackground(new Color(160, 160, 160));
                playButton.setForeground(Color.DARK_GRAY);
            }
            else{
                playButton.setBackground(ACCENT_COLOR);
                playButton.setForeground(TEXT_PRIMARY);
            }
        });
        transportPanel.add(playButton);
        transportPanel.add(pauseButton);
        transportPanel.add(stopButton);
        transportPanel.add(nextButton);
        JPanel progressPanel=new JPanel(new BorderLayout(10, 0));
        progressPanel.setBackground(NEUTRAL_BG);
        JPanel timeWrapper=new JPanel(new BorderLayout());
        timeWrapper.setBackground(NEUTRAL_BG);
        timeWrapper.add(currentTimeLabel, BorderLayout.WEST);
        timeWrapper.add(totalTimeLabel, BorderLayout.EAST);
        progressPanel.add(timeWrapper, BorderLayout.NORTH);
        progressPanel.add(progressSlider, BorderLayout.CENTER);
        JPanel nowPlayingPanel=new JPanel(new BorderLayout());
        nowPlayingPanel.setBackground(NEUTRAL_BG);
        nowPlayingPanel.setBorder(new EmptyBorder(10, 0, 5, 0));
        nowPlayingPanel.add(nowPlayingLabel, BorderLayout.CENTER);
        JPanel bottomPanel=new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBackground(NEUTRAL_BG);
        bottomPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        bottomPanel.add(actionPanel);
        bottomPanel.add(transportPanel);
        bottomPanel.add(progressPanel);
        bottomPanel.add(nowPlayingPanel);
        add(headerPanel, BorderLayout.NORTH);
        add(playlistScroll, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        revalidate();
        repaint();
    }
    private void setupListeners(){
        playButton.addActionListener(e -> playSelectedTrack());
        pauseButton.addActionListener(e ->{
            audioEngine.pause();
            updatePlaybackState();
        });
        stopButton.addActionListener(e ->{
            audioEngine.stop();
            updatePlaybackState();
        });
        previousButton.addActionListener(e -> playPreviousTrack());
        nextButton.addActionListener(e -> playNextTrack());
        uploadButton.addActionListener(e -> uploadAudioFile());
        deleteButton.addActionListener(e -> deleteSelectedTrack());
        clearButton.addActionListener(e -> clearAllTracks());
        progressSlider.addChangeListener(e ->{
            if (progressSlider.getValueIsAdjusting()){
                AudioTrack current=audioEngine.getCurrentTrack();
                if (current!=null){
                    long duration=audioEngine.getDuration();
                    long position=(long)((double) progressSlider.getValue()/100.0*duration);
                    audioEngine.seek(position);
                }
            }
        });
        playlistList.addListSelectionListener(e ->{
            if (!e.getValueIsAdjusting()){
                updateSelectionState();
            }
        });
    }
    private void loadPlaylist(){
        playlist=fileManager.scanAudioFiles();
        playlistModel.clear();
        for (AudioTrack track:playlist){
            playlistModel.addElement(track);
        }
        statusLabel.setText(playlist.isEmpty()?"No tracks found":playlist.size()+" tracks loaded");
    }
    private void playSelectedTrack(){
        AudioTrack selected=playlistList.getSelectedValue();
        if (selected!=null){
            boolean success=audioEngine.play(selected);
            if (success){
                updatePlaybackState();
                statusLabel.setText("Playing: "+selected.getDisplayName());
                playlistList.ensureIndexIsVisible(playlistList.getSelectedIndex());
            }
            else{
                statusLabel.setText("Failed to play: "+selected.getDisplayName());
                JOptionPane.showMessageDialog(this,"Could not play audio file. Format may not be supported.","Playback Error",JOptionPane.ERROR_MESSAGE);
            }
        }
        else{
            statusLabel.setText("No track selected");
        }
    }
    private void playPreviousTrack(){
        int currentIndex=playlistList.getSelectedIndex();
        if (currentIndex >0){
            playlistList.setSelectedIndex(currentIndex -1);
            playSelectedTrack();
        }
        else if (!playlist.isEmpty()){
            playlistList.setSelectedIndex(playlist.size() -1);
            playSelectedTrack();
        }
    }
    private void playNextTrack(){
        int currentIndex=playlistList.getSelectedIndex();
        if (currentIndex >=0&&currentIndex < playlist.size() -1){
            playlistList.setSelectedIndex(currentIndex+1);
            playSelectedTrack();
        }
        else if (!playlist.isEmpty()){
            playlistList.setSelectedIndex(0);
            playSelectedTrack();
        }
    }
    private void uploadAudioFile(){
        JFileChooser fileChooser=new JFileChooser();
        fileChooser.setDialogTitle("Select Audio File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.addChoosableFileFilter(new FileFilter(){
            @Override
            public boolean accept(File f){
                if (f.isDirectory()) return true;
                String name=f.getName().toLowerCase();
                return name.endsWith(".mp3")||name.endsWith(".wav")||name.endsWith(".mid")||name.endsWith(".midi")||name.endsWith(".ogg")||name.endsWith(".flac")||name.endsWith(".aac")||name.endsWith(".m4a")||name.endsWith(".aiff")||name.endsWith(".aif")||name.endsWith(".opus")||name.endsWith(".webm")||name.endsWith(".wma")||name.endsWith(".alac")||name.endsWith(".ape")||name.endsWith(".ac3")||name.endsWith(".dts")||name.endsWith(".caf")||name.endsWith(".3gp");
            }
            @Override
            public String getDescription(){
                return "All Supported Audio Files (*.mp3, *.wav, *.mid, *.midi, *.ogg, *.flac, *.aac, *.m4a, *.aiff, *.opus, *.webm, *.wma, *.alac, *.ape, *.ac3, *.dts, *.caf, *.3gp)";
            }
        });
        int result=fileChooser.showOpenDialog(SwingUtilities.getWindowAncestor(this));
        if (result==JFileChooser.APPROVE_OPTION){
            File selectedFile=fileChooser.getSelectedFile();
            try{
                AudioTrack newTrack=fileManager.uploadAudioFile(selectedFile);
                playlist.add(newTrack);
                playlistModel.addElement(newTrack);
                playlistList.setSelectedValue(newTrack, true);
                statusLabel.setText("Uploaded: "+newTrack.getDisplayName());
            }
            catch (IOException e){
                JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),"Failed to upload file: "+e.getMessage(),"Upload Error",JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("Upload failed");
            }
        }
    }
    private void deleteSelectedTrack(){
        AudioTrack selected=playlistList.getSelectedValue();
        if (selected!=null){
            int confirm=JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(this),"Delete track '"+selected.getDisplayName()+"'?","Confirm Delete",JOptionPane.YES_NO_OPTION);
            if (confirm==JOptionPane.YES_OPTION){
                boolean deleted=fileManager.deleteAudioTrack(selected);
                if (deleted){
                    playlist.remove(selected);
                    playlistModel.removeElement(selected);
                    statusLabel.setText("Deleted: "+selected.getDisplayName());
                    if (audioEngine.getCurrentTrack() ==selected){
                        audioEngine.stop();
                        updatePlaybackState();
                    }
                }
                else{
                    statusLabel.setText("Failed to delete track");
                }
            }
        }
    }
    private void clearAllTracks(){
        if (!playlist.isEmpty()){
            int confirm=JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(this),"Delete all "+playlist.size()+" tracks?","Confirm Clear All",JOptionPane.YES_NO_OPTION);
            if (confirm==JOptionPane.YES_OPTION){
                boolean cleared=fileManager.clearAllAudioFiles();
                if (cleared){
                    playlist.clear();
                    playlistModel.clear();
                    audioEngine.stop();
                    updatePlaybackState();
                    statusLabel.setText("All tracks cleared");
                }
                else{
                    statusLabel.setText("Failed to clear all tracks");
                }
            }
        }
    }
    private void updatePlaybackState(){
        AudioTrack current=audioEngine.getCurrentTrack();
        if (current!=null){
            nowPlayingLabel.setText("Now playing: "+current.getDisplayName());
            playButton.setEnabled(!audioEngine.isPlaying());
            pauseButton.setEnabled(audioEngine.isPlaying());
            stopButton.setEnabled(audioEngine.isPlaying()||audioEngine.isPaused());
            updateProgressDisplay();
        }
        else{
            nowPlayingLabel.setText("Not playing");
            playButton.setEnabled(playlistList.getSelectedValue()!=null);
            pauseButton.setEnabled(false);
            stopButton.setEnabled(false);
            currentTimeLabel.setText("00:00");
            totalTimeLabel.setText("00:00");
            progressSlider.setValue(0);
            progressSlider.setEnabled(false);
        }
    }
    private void updateSelectionState(){
        AudioTrack selected=playlistList.getSelectedValue();
        deleteButton.setEnabled(selected!=null);
        playButton.setEnabled(selected!=null&&!audioEngine.isPlaying());
        clearButton.setEnabled(!playlist.isEmpty());
    }
    private void updateProgressDisplay(){
        AudioTrack current=audioEngine.getCurrentTrack();
        if (current!=null){
            long position=audioEngine.getCurrentPosition();
            long duration=audioEngine.getDuration();
            currentTimeLabel.setText(formatTime(position));
            totalTimeLabel.setText(formatTime(duration));
            if (duration >0){
                int progress=(int)((double) position/duration*100);
                progressSlider.setValue(progress);
            }
            progressSlider.setEnabled(true);
        }
    }
    private String formatTime(long milliseconds){
        if (milliseconds <=0) return "00:00";
        long seconds=milliseconds/1000;
        long minutes=seconds/60;
        seconds=seconds%60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    private void startPlaybackTimer(){
        playbackTimer.scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                SwingUtilities.invokeLater(() ->{
                    if (audioEngine.isPlaying()||audioEngine.isPaused()){
                        updateProgressDisplay();
                    }
                });
            }
        }, 0, 200);
    }
    public void cleanup(){
        playbackTimer.cancel();
        audioEngine.cleanup();
    }
    public static void main(String[] args){
        SwingUtilities.invokeLater(() ->{
            JFrame frame=new JFrame("Audio Player");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setApplicationIcon(frame);
            AudioPlayerFrame playerPanel=new AudioPlayerFrame();
            frame.setContentPane(playerPanel);
            frame.pack();
            frame.setMinimumSize(new Dimension(450, 400));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            Runtime.getRuntime().addShutdownHook(new Thread(playerPanel::cleanup));
        });
    }
    private static void setApplicationIcon(JFrame frame){
        Image icon=null;
        URL iconUrl=AudioPlayerFrame.class.getResource("/icon.png");
        if (iconUrl!=null){
            icon=Toolkit.getDefaultToolkit().createImage(iconUrl);
        }
        if (icon==null||icon.getWidth(null) ==-1){
            File iconFile=new File("icon.png");
            if (iconFile.exists()){
                icon=Toolkit.getDefaultToolkit().createImage(iconFile.getAbsolutePath());
            }
        }
        if (icon==null||icon.getWidth(null) ==-1){
            icon=createFallbackIcon();
            System.err.println("Warning: Using fallback icon. Place 'icon.png' in classpath root (e.g., src/main/resources/) for custom icon.");
        }
        MediaTracker tracker=new MediaTracker(frame);
        tracker.addImage(icon, 0);
        try{
            tracker.waitForID(0, 5000);
        }
        catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
        frame.setIconImage(icon);
    }
    private static Image createFallbackIcon(){
        int size=64;
        BufferedImage img=new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d=img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        GradientPaint gradient=new GradientPaint(size/2f, size/2f, new Color(40, 167, 69),size/2f, size, new Color(30, 136, 56));
        g2d.setPaint(gradient);
        g2d.fillOval(4, 4, size-8, size-8);
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(size/2+2, size/4, size/2+2, size-12);
        g2d.drawLine(size/2+2, size/3, size/2+18, size/3-8);
        g2d.fillOval(size/2-8, size/3-6, 16, 16);
        g2d.dispose();
        return img;
    }
    private static class AudioTrackCellRenderer extends JPanel implements ListCellRenderer<AudioTrack>{
        private final JLabel trackNumberLabel;
        private final JLabel trackNameLabel;
        private final JLabel durationLabel;
        private final JLabel playingIndicator;
        public AudioTrackCellRenderer(){
            setLayout(new BorderLayout(12, 0));
            setBorder(new EmptyBorder(8, 8, 8, 8));
            setOpaque(true);
            trackNumberLabel=new JLabel();
            trackNumberLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
            trackNumberLabel.setPreferredSize(new Dimension(50, 20));
            trackNameLabel=new JLabel();
            trackNameLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            durationLabel=new JLabel();
            durationLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            durationLabel.setForeground(TEXT_SECONDARY);
            playingIndicator=new JLabel(">");
            playingIndicator.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            playingIndicator.setForeground(ACCENT_COLOR);
            playingIndicator.setVisible(false);
            JPanel leftPanel=new JPanel(new BorderLayout(10, 0));
            leftPanel.setOpaque(false);
            leftPanel.add(trackNumberLabel, BorderLayout.WEST);
            leftPanel.add(trackNameLabel, BorderLayout.CENTER);
            JPanel rightPanel=new JPanel(new BorderLayout(8, 0));
            rightPanel.setOpaque(false);
            rightPanel.add(durationLabel, BorderLayout.CENTER);
            rightPanel.add(playingIndicator, BorderLayout.EAST);
            add(leftPanel, BorderLayout.CENTER);
            add(rightPanel, BorderLayout.EAST);
        }
        @Override
        public Component getListCellRendererComponent(JList<? extends AudioTrack> list,AudioTrack track, int index, boolean isSelected, boolean cellHasFocus){
            trackNumberLabel.setText(String.format("%03d", track.getTrackNumber()));
            trackNameLabel.setText(track.getDisplayName());
            durationLabel.setText(track.getFormattedDuration());
            playingIndicator.setVisible(track.isPlaying());
            if (isSelected){
                setBackground(new Color(220, 235, 220));
                trackNumberLabel.setForeground(TEXT_PRIMARY);
                trackNameLabel.setForeground(TEXT_PRIMARY);
                durationLabel.setForeground(TEXT_PRIMARY);
                playingIndicator.setForeground(Color.WHITE);
            }
            else{
                setBackground(index%2==0?NEUTRAL_BG:NEUTRAL_LIGHT);
                trackNumberLabel.setForeground(new Color(100, 100, 100));
                trackNameLabel.setForeground(TEXT_PRIMARY);
                durationLabel.setForeground(TEXT_SECONDARY);
                playingIndicator.setForeground(ACCENT_COLOR);
            }
            return this;
        }
    }
}