package net.weesli.rozsconfig.serializer.component;

public interface ObjectSerializer<T> {
    T deserialize(ObjectNode node);
    void serialize(T obj, ObjectNode node);

    Class<T> getType();

    default boolean isType(Class<?> clazz) {
        return getType().equals(clazz);
    }
}
