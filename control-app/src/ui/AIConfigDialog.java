package ui;

import ai.*;
import model.Event;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;

public class AIConfigDialog extends JDialog {
    private static final String ENCRYPTION_PASSWORD = "calendar-app-ai-key"; // kept for compatibility, not used
    private final JComboBox<String> providerCombo;
    private final JTextField endpointField;
    private final JPasswordField apiKeyField;
    private final JComboBox<String> modelCombo;
    private final JButton testButton;
    private final JButton saveSettingsButton;
    private final JButton generateButton;
    private final JButton cancelButton;
    private final JTextArea goalTextArea;
    private final JSpinner daysSpinner;
    private final JCheckBox avoidConflictsCheck;

    private AIClient currentClient;
    private List<Event> generatedEvents = new ArrayList<>();
    private boolean generationComplete = false;
    private SettingsManager settingsManager;
    private String savedModel; // Store the saved model to select after loading

    public AIConfigDialog(Frame owner) {
        super(owner, "AI Event Generation", true);

        String[] providers = {"OpenAI", "DeepSeek", "OpenRouter", "Ollama"};
        providerCombo = new JComboBox<>(providers);
        endpointField = new JTextField(30);
        apiKeyField = new JPasswordField(30);
        modelCombo = new JComboBox<>();
        testButton = new JButton("Test Connection");
        saveSettingsButton = new JButton("Save Settings");
        generateButton = new JButton("Generate Events");
        cancelButton = new JButton("Cancel");
        goalTextArea = new JTextArea(5, 40);
        daysSpinner = new JSpinner(new SpinnerNumberModel(7, 1, 30, 1));
        avoidConflictsCheck = new JCheckBox("Avoid conflicts with existing events", true);

        setupUI();
        setupEvents();

        try {
            settingsManager = new SettingsManager();
            loadSavedSettings();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Could not initialize settings storage: " + e.getMessage(),
                    "Warning", JOptionPane.WARNING_MESSAGE);
        }

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Do NOT auto-save here – let the user decide.
                cleanup();
            }
        });
    }

    private void setupUI() {
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(new TitledBorder("AI Provider Configuration"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        configPanel.add(new JLabel("Provider:"), gbc);
        gbc.gridx = 1;
        configPanel.add(providerCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        configPanel.add(new JLabel("Endpoint:"), gbc);
        gbc.gridx = 1;
        configPanel.add(endpointField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        configPanel.add(new JLabel("API Key:"), gbc);
        gbc.gridx = 1;
        configPanel.add(apiKeyField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        configPanel.add(new JLabel("Model:"), gbc);
        gbc.gridx = 1;
        configPanel.add(modelCombo, gbc);

        // Button row (Test + Save)
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonRow.add(testButton);
        buttonRow.add(saveSettingsButton);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.EAST;
        configPanel.add(buttonRow, gbc);

        JPanel goalPanel = new JPanel(new BorderLayout(5, 5));
        goalPanel.setBorder(new TitledBorder("Goal Description"));
        goalTextArea.setLineWrap(true);
        goalTextArea.setWrapStyleWord(true);
        JScrollPane goalScroll = new JScrollPane(goalTextArea);
        goalPanel.add(goalScroll, BorderLayout.CENTER);
        goalTextArea.setText("I want to learn Python programming over the next week. I have 2 hours available each weekday evening and 4 hours on weekends.");

        JPanel optionsPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        optionsPanel.setBorder(new TitledBorder("Generation Options"));
        JPanel daysPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        daysPanel.add(new JLabel("Generate for next"));
        daysPanel.add(daysSpinner);
        daysPanel.add(new JLabel("days"));
        optionsPanel.add(daysPanel);
        optionsPanel.add(avoidConflictsCheck);

        JPanel bottomButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomButtonPanel.add(generateButton);
        bottomButtonPanel.add(cancelButton);

        mainPanel.add(configPanel, BorderLayout.NORTH);
        mainPanel.add(goalPanel, BorderLayout.CENTER);
        mainPanel.add(optionsPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
        add(bottomButtonPanel, BorderLayout.SOUTH);

        updateEndpointForProvider((String) providerCombo.getSelectedItem());
        pack();
        setLocationRelativeTo(getOwner());
    }

    private void setupEvents() {
        providerCombo.addActionListener(e -> {
            String provider = (String) providerCombo.getSelectedItem();
            updateEndpointForProvider(provider);
            clearModelList();
            // Do NOT auto-save here
        });

        testButton.addActionListener(e -> testConnection());

        saveSettingsButton.addActionListener(e -> saveCurrentSettings());

        generateButton.addActionListener(e -> {
            if (validateInput()) {
                saveCurrentSettings();   // Save before generating
                generateEvents();
            }
        });

        cancelButton.addActionListener(e -> {
            cleanup();
            dispose();
        });

        apiKeyField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // When user manually changes API key, reload models (without saved model selection)
                loadModels(null);
            }
        });
    }

    private void updateEndpointForProvider(String provider) {
        switch (provider) {
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

    private void clearModelList() {
        modelCombo.removeAllItems();
        modelCombo.addItem("Loading...");
    }

    /**
     * Load models asynchronously. If savedModel is not null, it will be selected after loading.
     */
    private void loadModels(String savedModelToSelect) {
        String provider = (String) providerCombo.getSelectedItem();
        String endpoint = endpointField.getText();
        String apiKey = new String(apiKeyField.getPassword());

        if (!provider.equals("Ollama") && apiKey.isEmpty()) {
            return;
        }

        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                try {
                    String providerKey = provider.toLowerCase();
                    return ModelFetcher.fetchModels(providerKey, apiKey, endpoint);
                } catch (Exception e) {
                    return Collections.emptyList();
                }
            }

            @Override
            protected void done() {
                try {
                    List<String> models = get();
                    modelCombo.removeAllItems();
                    if (models.isEmpty()) {
                        modelCombo.addItem("No models found");
                    } else {
                        for (String model : models) {
                            modelCombo.addItem(model);
                        }
                        // If we have a saved model to select, try to select it
                        if (savedModelToSelect != null && !savedModelToSelect.isEmpty()) {
                            modelCombo.setSelectedItem(savedModelToSelect);
                        }
                    }
                } catch (Exception e) {
                    modelCombo.removeAllItems();
                    modelCombo.addItem("Error loading models");
                }
            }
        };
        worker.execute();
    }

    private void testConnection() {
        String provider = (String) providerCombo.getSelectedItem();
        String endpoint = endpointField.getText();
        String apiKey = new String(apiKeyField.getPassword());

        if (!provider.equals("Ollama") && apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter an API key", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        testButton.setEnabled(false);
        testButton.setText("Testing...");

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            private String errorMessage;

            @Override
            protected Boolean doInBackground() {
                try {
                    currentClient = createClient(provider, apiKey, endpoint);
                    return currentClient.testConnection();
                } catch (Exception e) {
                    errorMessage = e.getMessage();
                    return false;
                }
            }

            @Override
            protected void done() {
                testButton.setEnabled(true);
                testButton.setText("Test Connection");
                try {
                    boolean success = get();
                    if (success) {
                        JOptionPane.showMessageDialog(AIConfigDialog.this,
                                "Connection successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(AIConfigDialog.this,
                                "Connection failed: " + errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(AIConfigDialog.this,
                            "Connection failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void generateEvents() {
        String provider = (String) providerCombo.getSelectedItem();
        String endpoint = endpointField.getText();
        String apiKey = new String(apiKeyField.getPassword());
        String model = modelCombo.getSelectedItem() != null ? modelCombo.getSelectedItem().toString() : "";

        if (model.contains("Loading") || model.contains("Error") || model.contains("No models found") || model.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a valid model", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        currentClient = createClient(provider, apiKey, endpoint);
        currentClient.setModel(model);

        String goal = getGoalDescription();
        int days = getDaysToGenerate();

        AIProgressDialog progressDialog = new AIProgressDialog((Frame) getOwner());
        generateButton.setEnabled(false);
        generateButton.setText("Generating...");

        SwingWorker<List<Event>, String> worker = new SwingWorker<>() {
            @Override
            protected List<Event> doInBackground() {
                try {
                    publish("Starting AI event generation...");
                    publish("Goal: " + goal);
                    publish("Days: " + days);
                    publish("Provider: " + provider);
                    publish("Model: " + model);

                    List<Event> existingEvents = new ArrayList<>();
                    List<Event> events = currentClient.generateEvents(goal,
                            java.time.LocalDate.now(), days, existingEvents, new ProgressCallback() {
                                @Override
                                public void update(String message) {
                                    publish(message);
                                }

                                @Override
                                public void updateSuccess(String message) {
                                    publish("SUCCESS: " + message);
                                }

                                @Override
                                public void updateWarning(String message) {
                                    publish("WARNING: " + message);
                                }

                                @Override
                                public void updateError(String message) {
                                    publish("ERROR: " + message);
                                }

                                @Override
                                public void updateEvent(Event event) {
                                    publish("EVENT: " + event.getTitle() + " on " + event.getDate()
                                            + " from " + event.getStartTime() + " to " + event.getEndTime());
                                }

                                @Override
                                public boolean isCancelled() {
                                    return progressDialog.isCancelled();
                                }
                            });
                    publish("Generated " + events.size() + " events");
                    return events;
                } catch (Exception e) {
                    publish("ERROR: " + e.getMessage());
                    e.printStackTrace();
                    return new ArrayList<>();
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    progressDialog.update(message);
                }
            }

            @Override
            protected void done() {
                try {
                    generatedEvents = get();
                    generationComplete = true;
                    if (generatedEvents.isEmpty()) {
                        progressDialog.updateError("No events were generated");
                        JOptionPane.showMessageDialog(AIConfigDialog.this,
                                "No events were generated. Check the AI response.",
                                "Warning", JOptionPane.WARNING_MESSAGE);
                    } else {
                        progressDialog.updateSuccess("Successfully generated " + generatedEvents.size() + " events!");
                        int option = JOptionPane.showConfirmDialog(AIConfigDialog.this,
                                "Successfully generated " + generatedEvents.size() + " events!\n" +
                                        "Add them to your calendar?",
                                "Generation Complete", JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE);
                        if (option == JOptionPane.YES_OPTION) {
                            progressDialog.closeDialog();
                            cleanup();
                            dispose();
                        }
                    }
                } catch (Exception e) {
                    progressDialog.updateError("Generation failed: " + e.getMessage());
                    JOptionPane.showMessageDialog(AIConfigDialog.this,
                            "Generation failed: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    generateButton.setEnabled(true);
                    generateButton.setText("Generate Events");
                }
            }
        };
        worker.execute();
        progressDialog.showDialog();
    }

    private AIClient createClient(String provider, String apiKey, String endpoint) {
        if ("Ollama".equals(provider)) {
            return new OllamaClient(endpoint);
        } else {
            return new OpenAICompatibleClient(apiKey, endpoint);
        }
    }

    private boolean validateInput() {
        String goal = goalTextArea.getText().trim();
        String apiKey = new String(apiKeyField.getPassword());
        String provider = (String) providerCombo.getSelectedItem();

        if (goal.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a goal description", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (!provider.equals("Ollama") && apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter an API key", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        String model = modelCombo.getSelectedItem() != null ? modelCombo.getSelectedItem().toString() : "";
        if (model.contains("Loading") || model.contains("Error") || model.contains("No models found") || model.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a valid model", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    private void loadSavedSettings() {
        if (settingsManager == null) {
            System.out.println("SettingsManager is null, cannot load.");
            return;
        }
        try {
            String provider = settingsManager.loadSetting("provider");
            System.out.println("Loaded provider: " + provider);
            if (provider != null) {
                providerCombo.setSelectedItem(provider);
            }

            String endpoint = settingsManager.loadSetting("endpoint");
            System.out.println("Loaded endpoint: " + endpoint);
            if (endpoint != null) {
                endpointField.setText(endpoint);
            }

            String apiKey = settingsManager.loadSetting("apiKey");
            System.out.println("Loaded apiKey: " + (apiKey != null ? "[non-null]" : "null"));
            if (apiKey != null) {
                apiKeyField.setText(apiKey);
            }

            // Store the saved model to select after models are loaded
            savedModel = settingsManager.loadSetting("model");
            System.out.println("Loaded model: " + savedModel);

            // After setting API key, trigger model loading (with saved model)
            if (apiKey != null && !apiKey.isEmpty()) {
                loadModels(savedModel);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to load saved settings:\n" + e.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveCurrentSettings() {
        if (settingsManager == null) {
            System.out.println("SettingsManager is null, cannot save.");
            return;
        }
        try {
            String provider = (String) providerCombo.getSelectedItem();
            String endpoint = endpointField.getText();
            String apiKey = new String(apiKeyField.getPassword());
            String model = (String) modelCombo.getSelectedItem();

            System.out.println("Saving provider: " + provider);
            System.out.println("Saving endpoint: " + endpoint);
            System.out.println("Saving apiKey: " + (apiKey.isEmpty() ? "empty" : "[non-empty]"));
            System.out.println("Saving model: " + model);

            settingsManager.saveSetting("provider", provider);
            settingsManager.saveSetting("endpoint", endpoint);
            settingsManager.saveSetting("apiKey", apiKey);
            settingsManager.saveSetting("model", model);

            System.out.println("Settings saved successfully.");
            JOptionPane.showMessageDialog(this, "Settings saved.", "Info", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to save settings:\n" + e.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isGenerationComplete() {
        return generationComplete;
    }

    public List<Event> getGeneratedEvents() {
        return new ArrayList<>(generatedEvents);
    }

    public String getGoalDescription() {
        return goalTextArea.getText().trim();
    }

    public int getDaysToGenerate() {
        return (Integer) daysSpinner.getValue();
    }

    public boolean shouldAvoidConflicts() {
        return avoidConflictsCheck.isSelected();
    }

    private void cleanup() {
        if (currentClient != null) {
            currentClient.shutdown();
            currentClient = null;
        }
    }

    public boolean isConfigured() {
        return generationComplete && !generatedEvents.isEmpty();
    }

    public AIClient getAIClient() {
        return currentClient;
    }

    public static void showAndGenerate(Frame parent, List<Event> existingEvents) {
        AIConfigDialog dialog = new AIConfigDialog(parent);
        dialog.setVisible(true);
        if (dialog.isGenerationComplete()) {
            List<Event> generated = dialog.getGeneratedEvents();
            if (!generated.isEmpty()) {
                JOptionPane.showMessageDialog(parent,
                        "Added " + generated.size() + " events to your calendar!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("AI Event Generator Test");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);
            JButton testButton = new JButton("Test AI Generation");
            testButton.addActionListener(e -> showAndGenerate(frame, new ArrayList<>()));
            frame.add(testButton);
            frame.setVisible(true);
        });
    }
}