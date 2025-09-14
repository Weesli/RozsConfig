package net.weesli.rozsconfig.model;

import net.weesli.rozsconfig.serializer.component.ObjectNode;

import java.util.Collection;
import java.util.Map;

public class RozsConfig {

    private ObjectNode node;

    public <T> T get(String key, Class<T> clazz) {
        return node.get(key, clazz);
    }

    public ObjectNode getNode(String key) {
        return new ObjectNode(getMap(key, Object.class));
    }

    public String getString(String key) {
        return node.getString(key);
    }

    public int getInt(String key) {
        return node.getInt(key);
    }

    public boolean getBoolean(String key) {
        return node.getBoolean(key);
    }

    public double getDouble(String key) {
        return node.getDouble(key);
    }

    public long getLong(String key) {
        return node.getLong(key);
    }

    public float getFloat(String key) {
        return node.getFloat(key);
    }

    public <T> Collection<T> getList(String key, Class<T> clazz) {
        return node.getList(key, clazz);
    }

    public <T> Map<String, T> getMap(String key, Class<T> clazz) {
        return node.getMap(key, clazz);
    }
}
