/* Authored by iqbserve.de */
package iqb.jps.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.databind.json.JsonMapper;

public class JsonTool {

    private final JsonMapper jsonMapper;

    private static final JsonTool instance = new JsonTool();
    public static final JsonTool Instance(){
        return instance;
    }    

    private JsonTool() {
        this.jsonMapper = JsonMapper.builder()
                .changeDefaultVisibility(v -> v
                        .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                        .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE))
                .build();
    }

    /**
     * Convert any Java object to a JSON string.
     */
    public String toString(Object object) {
        try {
            return jsonMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new UncheckedJsonException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Convert any Java object to a prettified JSON string.
     */
    public String toPrettyString(Object object) {
        try {
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (Exception e) {
            throw new UncheckedJsonException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Convert a JSON string into a specific Java class instance.
     */
    public <T> T toObject(String json, Class<T> clazz) {
        try {
            return jsonMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new UncheckedJsonException("Failed to deserialize JSON to " + clazz.getName(), e);
        }
    }

    /**
     * Prettify a JSON string for better readability.
     */
    public String prettify(String jsonInput) {
        try {
            Object jsonObj = jsonMapper.readValue(jsonInput, Object.class);
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObj);
        } catch (Exception e) {
            throw new UncheckedJsonException("Failed to prettify JSON", e);
        }
    }

    /**
     */
    public static class UncheckedJsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UncheckedJsonException(String msg, Throwable cause) {
            super(msg, cause);
        }

        public UncheckedJsonException(String msg) {
            super(msg);
        }
    }

}
