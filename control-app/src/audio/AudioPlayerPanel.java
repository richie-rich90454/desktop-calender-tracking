package audio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
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
public class AudioPlayerPanel extends JPanel{
    private static final Color NEUTRAL_BG=new Color(255, 255, 255);
    private static final Color NEUTRAL_LIGHT=new Color(248, 249, 250);
    private static final Color NEUTRAL_MID=new Color(233, 236, 239);
    private static final Color TEXT_PRIMARY=new Color(33, 37, 41);
    private static final Color TEXT_SECONDARY=new Color(108, 117, 125);
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
    private final JSlider volumeSlider;
    private final JSlider progressSlider;
    private final JLabel currentTimeLabel;
    private final JLabel totalTimeLabel;
    private final JLabel nowPlayingLabel;
    private final JLabel statusLabel;
    private final Timer playbackTimer;
    private List<AudioTrack> playlist;
    public AudioPlayerPanel(){
        this.audioEngine=new AudioPlayerEngine();
        this.fileManager=new AudioFileManager();
        this.playlist=new ArrayList<>();
        this.playlistModel=new DefaultListModel<>();
        this.playlistList=new JList<>(playlistModel);
        this.playButton=createControlButton("▶", "Play selected track");
        this.pauseButton=createControlButton("⏸", "Pause playback");
        this.stopButton=createControlButton("⏹", "Stop playback");
        this.previousButton=createControlButton("⏮", "Previous track");
        this.nextButton=createControlButton("⏭", "Next track");
        this.uploadButton=createControlButton("📁", "Upload audio file");
        this.deleteButton=createControlButton("🗑", "Delete selected track");
        this.clearButton=createControlButton("🗑️ All", "Clear all tracks");
        this.volumeSlider=new JSlider(0, 100, 80);
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
    }
    private void initializeComponents(){
        playlistList.setCellRenderer(new AudioTrackCellRenderer());
        playlistList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playlistList.setBackground(NEUTRAL_BG);
        playlistList.setBorder(new EmptyBorder(5, 5, 5, 5));
        volumeSlider.setBackground(NEUTRAL_BG);
        volumeSlider.setPaintTicks(true);
        volumeSlider.setPaintTrack(true);
        volumeSlider.setMajorTickSpacing(25);
        volumeSlider.setMinorTickSpacing(5);
        progressSlider.setBackground(NEUTRAL_BG);
        progressSlider.setEnabled(false);
        currentTimeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        currentTimeLabel.setForeground(TEXT_SECONDARY);
        totalTimeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        totalTimeLabel.setForeground(TEXT_SECONDARY);
        nowPlayingLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        nowPlayingLabel.setForeground(TEXT_PRIMARY);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setForeground(TEXT_SECONDARY);
    }
    private JButton createControlButton(String text, String tooltip){
        JButton button=new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        button.setBackground(NEUTRAL_BG);
        button.setForeground(TEXT_PRIMARY);
        button.setBorder(BorderFactory.createCompoundBorder(new LineBorder(NEUTRAL_MID, 1), new EmptyBorder(4, 8, 4, 8)
        ));
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
    private void setupLayout(){
        setLayout(new BorderLayout(10, 10));
        setBackground(NEUTRAL_BG);
        setBorder(new EmptyBorder(10, 10, 10, 10));
        JPanel headerPanel=new JPanel(new BorderLayout());
        headerPanel.setBackground(NEUTRAL_BG);
        headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
        JLabel titleLabel=new JLabel("Audio Player");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(TEXT_PRIMARY);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(statusLabel, BorderLayout.EAST);
        JScrollPane playlistScroll=new JScrollPane(playlistList);
        playlistScroll.setBorder(new LineBorder(NEUTRAL_MID, 1));
        playlistScroll.setBackground(NEUTRAL_BG);
        JPanel controlPanel=new JPanel(new BorderLayout(10, 10));
        controlPanel.setBackground(NEUTRAL_BG);
        controlPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        JPanel transportPanel=new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        transportPanel.setBackground(NEUTRAL_BG);
        transportPanel.add(previousButton);
        transportPanel.add(playButton);
        transportPanel.add(pauseButton);
        transportPanel.add(stopButton);
        transportPanel.add(nextButton);
        JPanel progressPanel=new JPanel(new BorderLayout(10, 5));
        progressPanel.setBackground(NEUTRAL_BG);
        progressPanel.add(currentTimeLabel, BorderLayout.WEST);
        progressPanel.add(progressSlider, BorderLayout.CENTER);
        progressPanel.add(totalTimeLabel, BorderLayout.EAST);
        JPanel nowPlayingPanel=new JPanel(new BorderLayout());
        nowPlayingPanel.setBackground(NEUTRAL_BG);
        nowPlayingPanel.setBorder(new EmptyBorder(5, 0, 5, 0));
        nowPlayingPanel.add(nowPlayingLabel, BorderLayout.CENTER);
        JPanel volumePanel=new JPanel(new BorderLayout(10, 0));
        volumePanel.setBackground(NEUTRAL_BG);
        JLabel volumeLabel=new JLabel("🔊");
        volumeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        volumePanel.add(volumeLabel, BorderLayout.WEST);
        volumePanel.add(volumeSlider, BorderLayout.CENTER);
        controlPanel.add(transportPanel, BorderLayout.NORTH);
        controlPanel.add(progressPanel, BorderLayout.CENTER);
        controlPanel.add(nowPlayingPanel, BorderLayout.SOUTH);
        JPanel actionPanel=new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        actionPanel.setBackground(NEUTRAL_BG);
        actionPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        actionPanel.add(uploadButton);
        actionPanel.add(deleteButton);
        actionPanel.add(clearButton);
        actionPanel.add(volumePanel);
        add(headerPanel, BorderLayout.NORTH);
        add(playlistScroll, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);
        add(actionPanel, BorderLayout.AFTER_LAST_LINE);
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
        volumeSlider.addChangeListener(e ->{
            float volume=(float) volumeSlider.getValue() / 100.0f * 86.0f - 80.0f;
            audioEngine.setVolume(volume);
        });
        progressSlider.addChangeListener(e ->{
            if (progressSlider.getValueIsAdjusting()){
                AudioTrack current=audioEngine.getCurrentTrack();
                if (current != null){
                    long duration=audioEngine.getDuration();
                    long position=(long) ((double) progressSlider.getValue() / 100.0 * duration);
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
        for (AudioTrack track : playlist){
            playlistModel.addElement(track);
        }
        statusLabel.setText(playlist.size() + " tracks loaded");
    }
    private void playSelectedTrack(){
        AudioTrack selected=playlistList.getSelectedValue();
        if (selected != null){
            boolean success=audioEngine.play(selected);
            if (success){
                updatePlaybackState();
                statusLabel.setText("Playing: " + selected.getDisplayName());
            }
            else{
                statusLabel.setText("Failed to play: " + selected.getDisplayName());
            }
        }
    }
    private void playPreviousTrack(){
        int currentIndex=playlistList.getSelectedIndex();
        if (currentIndex > 0){
            playlistList.setSelectedIndex(currentIndex - 1);
            playSelectedTrack();
        }
        else if (!playlist.isEmpty()){
            playlistList.setSelectedIndex(playlist.size() - 1);
            playSelectedTrack();
        }
    }
    private void playNextTrack(){
        int currentIndex=playlistList.getSelectedIndex();
        if (currentIndex >= 0 && currentIndex < playlist.size() - 1){
            playlistList.setSelectedIndex(currentIndex + 1);
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
                if (f.isDirectory()){
                    return true;
                }
                String name=f.getName().toLowerCase();
                return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".mid") || name.endsWith(".midi") || name.endsWith(".ogg") || name.endsWith(".flac");
            }
            @Override
            public String getDescription(){
                return "Audio Files (*.mp3, *.wav, *.mid, *.midi, *.ogg, *.flac)";
            }
        });
        int result=fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION){
            File selectedFile=fileChooser.getSelectedFile();
            try{
                AudioTrack newTrack=fileManager.uploadAudioFile(selectedFile);
                playlist.add(newTrack);
                playlistModel.addElement(newTrack);
                statusLabel.setText("Uploaded: " + newTrack.getDisplayName());
            }
            catch (IOException e){
                JOptionPane.showMessageDialog(this,
                        "Failed to upload file: " + e.getMessage(),
                        "Upload Error",
                        JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("Upload failed");
            }
        }
    }
    private void deleteSelectedTrack(){
        AudioTrack selected=playlistList.getSelectedValue();
        if (selected != null){
            int confirm=JOptionPane.showConfirmDialog(this, "Delete track '" + selected.getDisplayName() + "'?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION){
                boolean deleted=fileManager.deleteAudioTrack(selected);
                if (deleted){
                    playlist.remove(selected);
                    playlistModel.removeElement(selected);
                    statusLabel.setText("Deleted: " + selected.getDisplayName());
                    if (audioEngine.getCurrentTrack() == selected){
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
            int confirm=JOptionPane.showConfirmDialog(this, "Delete all " + playlist.size() + " tracks?",
                    "Confirm Clear All", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION){
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
        if (current != null){
            nowPlayingLabel.setText("Now playing: " + current.getDisplayName());
            playButton.setEnabled(!audioEngine.isPlaying());
            pauseButton.setEnabled(audioEngine.isPlaying());
            stopButton.setEnabled(audioEngine.isPlaying() || audioEngine.isPaused());
            updateProgressDisplay();
        }
        else{
            nowPlayingLabel.setText("Not playing");
            playButton.setEnabled(true);
            pauseButton.setEnabled(false);
            stopButton.setEnabled(false);
            currentTimeLabel.setText("00:00");
            totalTimeLabel.setText("00:00");
            progressSlider.setValue(0);
        }
    }
    private void updateSelectionState(){
        AudioTrack selected=playlistList.getSelectedValue();
        deleteButton.setEnabled(selected != null);
        playButton.setEnabled(selected != null);
    }
    private void updateProgressDisplay(){
        AudioTrack current=audioEngine.getCurrentTrack();
        if (current != null){
            long position=audioEngine.getCurrentPosition();
            long duration=audioEngine.getDuration();
            currentTimeLabel.setText(formatTime(position));
            totalTimeLabel.setText(formatTime(duration));
            if (duration > 0){
                int progress=(int) ((double) position / duration * 100);
                progressSlider.setValue(progress);
            }
        }
    }
    private String formatTime(long milliseconds){
        if (milliseconds <= 0){
            return "00:00";
        }
        long seconds=milliseconds / 1000;
        long minutes=seconds / 60;
        seconds=seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    private void startPlaybackTimer(){
        playbackTimer.scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                SwingUtilities.invokeLater(() -> updatePlaybackState());
            }
        }, 0, 500);
    }
    public void cleanup(){
        playbackTimer.cancel();
        audioEngine.cleanup();
    }
    private static class AudioTrackCellRenderer extends JPanel implements ListCellRenderer<AudioTrack>{
        private final JLabel trackNumberLabel;
        private final JLabel trackNameLabel;
        private final JLabel durationLabel;
        private final JLabel playingIndicator;
        public AudioTrackCellRenderer(){
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(5, 5, 5, 5));
            setOpaque(true);
            trackNumberLabel=new JLabel();
            trackNumberLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            trackNumberLabel.setPreferredSize(new Dimension(40, 20));
            trackNameLabel=new JLabel();
            trackNameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            durationLabel=new JLabel();
            durationLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            durationLabel.setForeground(TEXT_SECONDARY);
            playingIndicator=new JLabel("▶");
            playingIndicator.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            playingIndicator.setForeground(new Color(30, 120, 83));
            playingIndicator.setVisible(false);
            JPanel leftPanel=new JPanel(new BorderLayout(5, 0));
            leftPanel.setOpaque(false);
            leftPanel.add(trackNumberLabel, BorderLayout.WEST);
            leftPanel.add(trackNameLabel, BorderLayout.CENTER);
            JPanel rightPanel=new JPanel(new BorderLayout(5, 0));
            rightPanel.setOpaque(false);
            rightPanel.add(durationLabel, BorderLayout.CENTER);
            rightPanel.add(playingIndicator, BorderLayout.EAST);
            add(leftPanel, BorderLayout.CENTER);
            add(rightPanel, BorderLayout.EAST);
        }
        @Override
        public Component getListCellRendererComponent(JList<? extends AudioTrack> list, AudioTrack track, int index, boolean isSelected, boolean cellHasFocus){
            trackNumberLabel.setText(String.format("%03d.", track.getTrackNumber()));
            trackNameLabel.setText(track.getDisplayName());
            durationLabel.setText(track.getFormattedDuration());
            playingIndicator.setVisible(track.isPlaying());
            if (isSelected){
                setBackground(NEUTRAL_MID);
                trackNumberLabel.setForeground(TEXT_PRIMARY);
                trackNameLabel.setForeground(TEXT_PRIMARY);
            }
            else{
                setBackground(index % 2 == 0 ? NEUTRAL_BG : NEUTRAL_LIGHT);
                trackNumberLabel.setForeground(TEXT_SECONDARY);
                trackNameLabel.setForeground(TEXT_PRIMARY);
            }
            return this;
        }
    }
}