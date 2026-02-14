package ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

/**
 * Manages loading and saving of encrypted configuration settings.
 * Stores data in: ${user.home}/.calendarapp/config.dat
 * Master password is stored in: ${user.home}/.calendarapp/master.key
 */
public class SettingsManager {
    private static final String APP_DIR = ".calendarapp";
    private static final String CONFIG_FILE = "config.dat";
    private static final String MASTER_KEY_FILE = "master.key";
    private final Path configPath;
    private final Path masterKeyPath;
    private String masterPassword;

    public SettingsManager() throws IOException {
        Path homeDir = Paths.get(System.getProperty("user.home"));
        Path appDir = homeDir.resolve(APP_DIR);
        if (!Files.exists(appDir)) {
            Files.createDirectories(appDir);
        }
        configPath = appDir.resolve(CONFIG_FILE);
        masterKeyPath = appDir.resolve(MASTER_KEY_FILE);
        loadOrCreateMasterKey();
    }

    /**
     * Loads the master password from disk, or creates a new random one if it
     * doesn't exist.
     */
    private void loadOrCreateMasterKey() throws IOException {
        if (Files.exists(masterKeyPath)) {
            masterPassword = Files.readString(masterKeyPath).trim();
        } else {
            // Generate a strong random password
            SecureRandom random = new SecureRandom();
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            masterPassword = Base64.getEncoder().encodeToString(bytes);
            Files.writeString(masterKeyPath, masterPassword);
            // Set file permissions to owner-only (POSIX only; Windows inherits from parent)
            try {
                Files.setPosixFilePermissions(masterKeyPath,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows: rely on directory permissions
            }
        }
    }

    /**
     * Saves a single key-value pair (e.g., api.key) to the encrypted config file.
     * Uses Properties to store multiple settings.
     */
    public void saveSetting(String key, String value) throws Exception {
        Properties props = loadAllSettings();
        if (value == null || value.isEmpty()) {
            props.remove(key);
        } else {
            props.setProperty(key, value);
        }
        saveAllSettings(props);
    }

    /**
     * Loads a single setting by key.
     */
    public String loadSetting(String key) throws Exception {
        Properties props = loadAllSettings();
        return props.getProperty(key);
    }

    /**
     * Loads all settings from the encrypted config file.
     */
    private Properties loadAllSettings() throws Exception {
        Properties props = new Properties();
        if (Files.exists(configPath)) {
            String encryptedData = Files.readString(configPath);
            if (!encryptedData.isEmpty()) {
                String decrypted = EncryptionUtil.decrypt(encryptedData, masterPassword);
                props.load(new java.io.StringReader(decrypted));
            }
        }
        return props;
    }

    /**
     * Saves all settings to the encrypted config file.
     */
    private void saveAllSettings(Properties props) throws Exception {
        java.io.StringWriter writer = new java.io.StringWriter();
        props.store(writer, null);
        String plainData = writer.toString();
        String encrypted = EncryptionUtil.encrypt(plainData, masterPassword);
        Files.writeString(configPath, encrypted);
    }

    /**
     * Clears a specific setting.
     */
    public void clearSetting(String key) throws Exception {
        saveSetting(key, null);
    }
}