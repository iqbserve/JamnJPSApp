/* Authored by iqbserve.de */
package iqb.jps.core;

import iqb.jps.core.wrapper.JacksonJsonToolImpl;

/**
 * Interface for a JSON serialization and deserialization tool.
 */
public interface JsonTool {
    /**
     * @return the singleton instance of the JsonTool wrapper implementation
     */
    static JsonTool Instance() {
        return JacksonJsonToolImpl.Instance();
    }

    String toString(Object object);
    String toPrettyString(Object object);
    <T> T toObject(String json, Class<T> clazz);
    String prettify(String jsonInput);
}
