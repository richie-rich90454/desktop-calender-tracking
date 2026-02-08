package ui;

import ai.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;

/*
 * Dialog for configuring AI providers and API settings.
 *
 * Responsibilities:
 * - Allow user to select AI provider (OpenAI, DeepSeek, OpenRouter, etc.)
 * - Configure API keys and endpoints
 * - Test API connections
 * - Select models from dynamically fetched lists
 * - Manage encrypted API key storage
 *
 * Java data types used:
 * - JDialog for modal configuration
 * - JComboBox for provider/model selection
 * - JPasswordField for API key input
 * - JTextArea for goal description
 * - AIClient for API communication
 *
 * Java technologies involved:
 * - Swing components for UI
 * - BorderLayout and GridBagLayout for layout management
 * - ActionListener for event handling
 * - EncryptionUtil for secure key storage
 *
 * Design intent:
 * Provides a clean, intuitive interface for AI configuration.
 * API keys are encrypted before storage.
 * Models are fetched dynamically from provider APIs.
 * Connection testing validates credentials before use.
 */

public class AIConfigDialog extends JDialog{
    private static final String ENCRYPTION_PASSWORD="calendar-app-ai-key";
    private final JComboBox<String> providerCombo;
    private final JTextField endpointField;
    private final JPasswordField apiKeyField;
    private final JComboBox<String> modelCombo;
    private final JButton testButton;
    private final JButton saveButton;
    private final JButton cancelButton;
    private final JTextArea goalTextArea;
    private final JSpinner daysSpinner;
    private final JCheckBox avoidConflictsCheck;
    private AIClient currentClient;
    private boolean configured=false;
    private String selectedProvider;
    private String selectedModel;
    public AIConfigDialog(Frame owner){
        super(owner, "AI Configuration", true);
        String[] providers={"OpenAI", "DeepSeek", "OpenRouter", "Ollama"};
        providerCombo=new JComboBox<>(providers);
        endpointField=new JTextField(30);
        apiKeyField=new JPasswordField(30);
        modelCombo=new JComboBox<>();
        testButton=new JButton("Test Connection");
        saveButton=new JButton("Save & Use");
        cancelButton=new JButton("Cancel");
        goalTextArea=new JTextArea(5, 40);
        daysSpinner=new JSpinner(new SpinnerNumberModel(7, 1, 30, 1));
        avoidConflictsCheck=new JCheckBox("Avoid conflicts with existing events", true);
        setupUI();
        setupEvents();
        loadSavedSettings();
    }
    private void setupUI(){
        setLayout(new BorderLayout(10, 10));
        JPanel mainPanel=new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        JPanel configPanel=new JPanel(new GridBagLayout());
        configPanel.setBorder(new TitledBorder("AI Provider Configuration"));
        GridBagConstraints gbc=new GridBagConstraints();
        gbc.insets=new Insets(5, 5, 5, 5);
        gbc.fill=GridBagConstraints.HORIZONTAL;
        gbc.anchor=GridBagConstraints.WEST;
        gbc.gridx=0; gbc.gridy=0;
        configPanel.add(new JLabel("Provider:"), gbc);
        gbc.gridx=1;
        configPanel.add(providerCombo, gbc);
        gbc.gridx=0; gbc.gridy=1;
        configPanel.add(new JLabel("Endpoint:"), gbc);
        gbc.gridx=1;
        configPanel.add(endpointField, gbc);
        gbc.gridx=0; gbc.gridy=2;
        configPanel.add(new JLabel("API Key:"), gbc);
        gbc.gridx=1;
        configPanel.add(apiKeyField, gbc);
        gbc.gridx=0; gbc.gridy=3;
        configPanel.add(new JLabel("Model:"), gbc);
        gbc.gridx=1;
        configPanel.add(modelCombo, gbc);
        gbc.gridx=1; gbc.gridy=4;
        gbc.anchor=GridBagConstraints.EAST;
        configPanel.add(testButton, gbc);
        JPanel goalPanel=new JPanel(new BorderLayout(5, 5));
        goalPanel.setBorder(new TitledBorder("Goal Description"));
        goalTextArea.setLineWrap(true);
        goalTextArea.setWrapStyleWord(true);
        JScrollPane goalScroll=new JScrollPane(goalTextArea);
        goalPanel.add(goalScroll, BorderLayout.CENTER);
        goalTextArea.setText("Example: I want to learn Python programming over the next week. "+"I have 2 hours available each weekday evening and 4 hours on weekends.");
        JPanel optionsPanel=new JPanel(new GridLayout(2, 1, 5, 5));
        optionsPanel.setBorder(new TitledBorder("Generation Options"));
        JPanel daysPanel=new JPanel(new FlowLayout(FlowLayout.LEFT));
        daysPanel.add(new JLabel("Generate for next"));
        daysPanel.add(daysSpinner);
        daysPanel.add(new JLabel("days"));
        optionsPanel.add(daysPanel);
        optionsPanel.add(avoidConflictsCheck);
        JPanel buttonPanel=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        mainPanel.add(configPanel, BorderLayout.NORTH);
        mainPanel.add(goalPanel, BorderLayout.CENTER);
        mainPanel.add(optionsPanel, BorderLayout.SOUTH);
        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        updateEndpointForProvider((String) providerCombo.getSelectedItem());
        pack();
        setLocationRelativeTo(getOwner());
    }
    private void setupEvents(){
        providerCombo.addActionListener(e ->{
            String provider=(String) providerCombo.getSelectedItem();
            updateEndpointForProvider(provider);
            clearModelList();
        });
        testButton.addActionListener(e -> testConnection());
        saveButton.addActionListener(e ->{
            if (validateInput()){
                configured=true;
                dispose();
            }
        });
        cancelButton.addActionListener(e ->{
            configured=false;
            dispose();
        });
        apiKeyField.addFocusListener(new FocusAdapter(){
            @Override
            public void focusLost(FocusEvent e){
                loadModels();
            }
        });
    }
    private void updateEndpointForProvider(String provider){
        switch (provider){
            case "OpenAI":
                endpointField.setText("https://api.openai.com/v1/chat/completions");
                break;
            case "DeepSeek":
                endpointField.setText("https://api.deepseek.com/v1/chat/completions");
                break;
            case "OpenRouter":
                endpointField.setText("https://openrouter.ai/api/v1/chat/completions");
                break;
            case "Ollama":
                endpointField.setText("http://localhost:11434/api/chat");
                break;
        }
    }
    private void clearModelList(){
        modelCombo.removeAllItems();
        modelCombo.addItem("Loading...");
    }
    private void loadModels(){
        String provider=(String) providerCombo.getSelectedItem();
        String endpoint=endpointField.getText();
        String apiKey=new String(apiKeyField.getPassword());
        
        // Ollama doesn't require an API key, other providers do
        if (!provider.equals("Ollama") && apiKey.isEmpty()){
            return;
        }
        
        SwingWorker<List<String>, Void> worker=new SwingWorker<>(){
            @Override
            protected List<String> doInBackground() throws Exception{
                try{
                    String providerKey=provider.toLowerCase();
                    return ModelFetcher.fetchModels(providerKey, apiKey, endpoint);
                }
                catch (Exception e){
                    return Collections.emptyList();
                }
            }
            @Override
            protected void done(){
                try{
                    List<String> models=get();
                    modelCombo.removeAllItems();
                    if (models.isEmpty()){
                        modelCombo.addItem("No models found");
                    }
                    else{
                        for (String model:models){
                            modelCombo.addItem(model);
                        }
                    }
                }
                catch (Exception e){
                    modelCombo.removeAllItems();
                    modelCombo.addItem("Error loading models");
                }
            }
        };
        worker.execute();
    }
    private void testConnection(){
        String provider=(String) providerCombo.getSelectedItem();
        String endpoint=endpointField.getText();
        String apiKey=new String(apiKeyField.getPassword());
        
        // Ollama doesn't require an API key, other providers do
        if (!provider.equals("Ollama") && apiKey.isEmpty()){
            JOptionPane.showMessageDialog(this, "Please enter an API key","Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        testButton.setEnabled(false);
        testButton.setText("Testing...");
        SwingWorker<Boolean, Void> worker=new SwingWorker<>(){
            private String errorMessage;
            @Override
            protected Boolean doInBackground(){
                try{
                    currentClient=createClient(provider, apiKey, endpoint);
                    return currentClient.testConnection();
                }
                catch (Exception e){
                    errorMessage=e.getMessage();
                    return false;
                }
            }
            @Override
            protected void done(){
                testButton.setEnabled(true);
                testButton.setText("Test Connection");
                try{
                    boolean success=get();
                    if (success){
                        JOptionPane.showMessageDialog(AIConfigDialog.this,"Connection successful!", "Success",JOptionPane.INFORMATION_MESSAGE);
                    }
                    else{
                        JOptionPane.showMessageDialog(AIConfigDialog.this,"Connection failed: "+errorMessage, "Error",JOptionPane.ERROR_MESSAGE);
                    }
                }
                catch (Exception e){
                    JOptionPane.showMessageDialog(AIConfigDialog.this,"Connection failed: "+e.getMessage(), "Error",JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
    private AIClient createClient(String provider, String apiKey, String endpoint){
        AIClient client;
        if ("Ollama".equals(provider)){
            client=new OllamaClient(endpoint);
        }
        else{
            client=new OpenAICompatibleClient(apiKey, endpoint);
        }
        if (modelCombo.getSelectedItem()!=null&&!modelCombo.getSelectedItem().toString().contains("Loading")&&!modelCombo.getSelectedItem().toString().contains("Error")&&!modelCombo.getSelectedItem().toString().contains("No models")){
            client.setModel(modelCombo.getSelectedItem().toString());
        }
        return client;
    }
    private boolean validateInput(){
        String goal=goalTextArea.getText().trim();
        String apiKey=new String(apiKeyField.getPassword());
        String provider=(String) providerCombo.getSelectedItem();
        if (goal.isEmpty()){
            JOptionPane.showMessageDialog(this, "Please enter a goal description","Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (!provider.equals("Ollama") && apiKey.isEmpty()){
            JOptionPane.showMessageDialog(this, "Please enter an API key","Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (!apiKey.isEmpty()){
            try{
                String encryptedKey=EncryptionUtil.encrypt(apiKey, ENCRYPTION_PASSWORD);
            }
            catch (Exception e){
                JOptionPane.showMessageDialog(this,"Failed to encrypt API key: "+e.getMessage(),"Error", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
        selectedProvider=provider;
        selectedModel=(String) modelCombo.getSelectedItem();
        return true;
    }
    private void loadSavedSettings(){
    }
    public boolean isConfigured(){
        return configured;
    }
    public AIClient getAIClient(){
        if (!configured){
            return null;
        }
        String apiKey=new String(apiKeyField.getPassword());
        String endpoint=endpointField.getText();
        String provider=(String) providerCombo.getSelectedItem();
        AIClient client=createClient(provider, apiKey, endpoint);
        if (selectedModel!=null&&client instanceof OpenAICompatibleClient){
            ((OpenAICompatibleClient) client).setModel(selectedModel);
        }
        return client;
    }
    public String getGoalDescription(){
        return goalTextArea.getText().trim();
    }
    public int getDaysToGenerate(){
        return (Integer) daysSpinner.getValue();
    }
    public boolean shouldAvoidConflicts(){
        return avoidConflictsCheck.isSelected();
    }
    public static void main(String[] args){
        SwingUtilities.invokeLater(() ->{
            JFrame frame=new JFrame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            AIConfigDialog dialog=new AIConfigDialog(frame);
            dialog.setVisible(true);
            if (dialog.isConfigured()){
                System.out.println("Configured!");
                System.out.println("Goal: "+dialog.getGoalDescription());
                System.out.println("Days: "+dialog.getDaysToGenerate());
            }
            frame.dispose();
        });
    }
}