package assettracking.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

public class AppSettingsDAO {

    private static final Logger logger = LoggerFactory.getLogger(AppSettingsDAO.class);
    // Define the path to the local settings file in the user's home directory.
    private static final String SETTINGS_FILE_PATH = System.getProperty("user.home") + File.separator + ".asset_tracker_settings.properties";

    /**
     * Retrieves a setting value from the local properties file.
     *
     * @param key The name of the setting to retrieve.
     * @return An Optional containing the value if found, otherwise empty.
     */
    public Optional<String> getSetting(String key) {
        Properties properties = loadProperties();
        return Optional.ofNullable(properties.getProperty(key));
    }

    /**
     * Saves or updates a setting in the local properties file.
     *
     * @param key   The name of the setting.
     * @param value The value to save.
     */
    public void saveSetting(String key, String value) {
        Properties properties = loadProperties();
        properties.setProperty(key, value);
        saveProperties(properties);
    }

    /**
     * Helper method to load properties from the local file.
     */
    private Properties loadProperties() {
        Properties properties = new Properties();
        File settingsFile = new File(SETTINGS_FILE_PATH);
        if (settingsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(settingsFile)) {
                properties.load(fis);
            } catch (IOException e) {
                logger.error("Could not load local settings file at: " + SETTINGS_FILE_PATH, e);
            }
        }
        return properties;
    }

    /**
     * Helper method to save properties to the local file.
     */
    private void saveProperties(Properties properties) {
        try (FileOutputStream fos = new FileOutputStream(SETTINGS_FILE_PATH)) {
            properties.store(fos, "Asset Tracker User Settings");
        } catch (IOException e) {
            logger.error("Could not save local settings file at: " + SETTINGS_FILE_PATH, e);
        }
    }
}