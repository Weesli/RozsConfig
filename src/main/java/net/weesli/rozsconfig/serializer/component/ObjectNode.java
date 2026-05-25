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

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object raw = variableMap.get(key);
        if (raw == null) return null;
        if (clazz.isInstance(raw)) return clazz.cast(raw);
        // attempt safe coercion
        Object coerced = safeCast(raw, clazz);
        return (T) coerced;
    }

    public String getString(String key) {
        Object raw = variableMap.get(key);
        if (raw == null) return null;
        return String.valueOf(raw);
    }

    public int getInt(String key) {
        Object raw = variableMap.get(key);
        if (raw == null) return 0;
        if (raw instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(raw));
    }

    public boolean getBoolean(String key) {
        Object raw = variableMap.get(key);
        if (raw == null) return false;
        if (raw instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(raw));
    }

    public double getDouble(String key) {
        Object raw = variableMap.get(key);
        if (raw == null) return 0.0;
        if (raw instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(raw));
    }

    public long getLong(String key) {
        Object raw = variableMap.get(key);
        if (raw == null) return 0L;
        if (raw instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(raw));
    }

    public float getFloat(String key) {
        Object raw = variableMap.get(key);
        if (raw == null) return 0.0f;
        if (raw instanceof Number n) return n.floatValue();
        return Float.parseFloat(String.valueOf(raw));
    }

    @SuppressWarnings("unchecked")
    public <T> Collection<T> getList(String key, Class<T> clazz) {
        return (Collection<T>) variableMap.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> Map<String, T> getMap(String key, Class<T> clazz) {
        return (Map<String, T>) variableMap.get(key);
    }

    public Object getVariableMap() {
        return variableMap;
    }

    // ── Safe casting helper ──────────────────────────────────────────────
    private static Object safeCast(Object raw, Class<?> target) {
        if (raw == null) return null;
        if (target.isInstance(raw)) return raw;

        if (target == String.class) return String.valueOf(raw);

        if (target == boolean.class || target == Boolean.class) {
            if (raw instanceof Boolean) return raw;
            return Boolean.parseBoolean(String.valueOf(raw));
        }

        // Number targets – accept both Number and String sources
        if (raw instanceof Number n) {
            if (target == int.class || target == Integer.class) return n.intValue();
            if (target == long.class || target == Long.class)   return n.longValue();
            if (target == double.class || target == Double.class) return n.doubleValue();
            if (target == float.class || target == Float.class) return n.floatValue();
            if (target == short.class || target == Short.class) return n.shortValue();
            if (target == byte.class || target == Byte.class)   return n.byteValue();
        }

        // String → Number parsing
        String str = String.valueOf(raw);
        try {
            if (target == int.class || target == Integer.class) return Integer.parseInt(str);
            if (target == long.class || target == Long.class)   return Long.parseLong(str);
            if (target == double.class || target == Double.class) return Double.parseDouble(str);
            if (target == float.class || target == Float.class) return Float.parseFloat(str);
            if (target == short.class || target == Short.class) return Short.parseShort(str);
            if (target == byte.class || target == Byte.class)   return Byte.parseByte(str);
        } catch (NumberFormatException ignored) {}

        return raw;
    }
}
