package com.example.reviewscraper.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;

/**
 * Loads selector configuration JSON from resources/config/.
 *
 * Example:
 *   SelectorConfig cfg = new SelectorConfig("capterra_selectors.json");
 *   String blockSelector = cfg.getString("reviewBlock");
 */
public class SelectorConfig {

    private final JsonObject selectors;

    public SelectorConfig(String fileName) throws Exception {  // ✅ fixed constructor name
        String path = "/config/" + fileName;
        InputStream in = getClass().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalArgumentException("Could not find selector config file: " + path);
        }
        try (InputStreamReader reader = new InputStreamReader(in)) {
            this.selectors = JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /**
     * Get a string value from the loaded JSON.
     * @param key JSON key
     * @return the value or null if missing
     */
    public String getString(String key) {
        if (selectors.has(key)) {
            return selectors.get(key).getAsString();
        }
        return null;
    }

    /**
     * Optional: get the raw JsonObject if you want advanced usage.
     */
    public JsonObject getJson() {
        return selectors;
    }

    @Override
    public String toString() {
        return Objects.toString(selectors);
    }
}
