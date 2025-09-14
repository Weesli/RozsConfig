package net.weesli.rozsconfig.serializer.component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ObjectNode {
    private final LinkedHashMap<String, Object> variableMap = new LinkedHashMap<>();

    public ObjectNode(Map<String, Object> variableMap) {
        this.variableMap.putAll(variableMap);
    }

    public void set(String key, Object value) {
        variableMap.put(key, value);
    }

    public <T> T get(String key, Class<T> clazz) {
        return clazz.cast(variableMap.get(key));
    }

    public String getString(String key) {
        return (String) variableMap.get(key);
    }

    public int getInt(String key) {
        return (int) variableMap.get(key);
    }

    public boolean getBoolean(String key) {
        return (boolean) variableMap.get(key);
    }

    public double getDouble(String key) {
        return (double) variableMap.get(key);
    }

    public long getLong(String key) {
        return (long) variableMap.get(key);
    }

    public float getFloat(String key) {
        return (float) variableMap.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> Collection<T> getList(String key, Class<T> clazz) {
        return (Collection<T>) variableMap.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T>Map<String, T> getMap(String key, Class<T> clazz) {
        return (Map<String, T>) variableMap.get(key);
    }

    public Object getVariableMap() {
        return variableMap;
    }
}
