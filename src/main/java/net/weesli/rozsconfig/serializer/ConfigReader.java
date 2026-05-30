package net.weesli.rozsconfig.serializer;

import net.weesli.rozsconfig.annotations.IgnoreField;
import net.weesli.rozsconfig.model.RozsConfig;
import net.weesli.rozsconfig.serializer.component.ObjectNode;
import net.weesli.rozsconfig.serializer.component.ObjectSerializer;

import java.lang.reflect.*;
import java.util.*;


final class ConfigReader {

    private final List<ObjectSerializer<?>> serializers;

    ConfigReader(List<ObjectSerializer<?>> serializers) {
        this.serializers = serializers;
    }

    void applyRozsConfig(Object o, Class<?> clazz, Map<String, Object> currentValues) {
        if (RozsConfig.class.isAssignableFrom(clazz)) {
            try {
                Field field = RozsConfig.class.getDeclaredField("node");
                field.setAccessible(true);
                field.set(o, new ObjectNode(currentValues));
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    void processPrimitive(Object owner, Field field, Map<String, Object> currentMap) {
        try {
            String key = TypeUtils.resolveKey(field);
            Object raw = currentMap.get(key);

            if (raw == null) {
                for (ObjectSerializer<?> s : serializers) {
                    if (s.isType(field.getType())) {
                        ObjectNode node = new ObjectNode(currentMap);
                        Object val = s.deserialize(node);
                        if (val != null) { field.set(owner, val); }
                        return;
                    }
                }
            }

            if (raw != null) {
                Object val = TypeUtils.coerce(raw, field.getType());
                field.set(owner, val);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void processObject(Object owner, Field field, Map<String, Object> currentMap, Object parent) {
        try {
            String resolved = TypeUtils.resolveKey(field);
            Object existing = currentMap.get(resolved);
            Class<?> type = field.getType();
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                return;
            }
            if (type == ObjectNode.class) {
                return;
            }
            if (owner.getClass().isAnnotationPresent(IgnoreField.class)) return;
            if (existing == null && TypeUtils.isCollectionOrMap(type)) {
                Object empty = TypeUtils.newDefaultContainer(type);
                field.set(owner, empty);
                return;
            }

            if (existing != null && TypeUtils.isCollectionOrMap(type)) {
                Object materialized = materializeContainerFromYaml(existing, type, field);
                if (materialized != null) {
                    field.set(owner, materialized);
                    return;
                }
            }

            if (existing == null) return;

            for (ObjectSerializer<?> s : serializers) {
                if (s.isType(type)) {
                    Object raw = existing;
                    if (raw instanceof Map) {
                        ObjectNode node = new ObjectNode((Map<String, Object>) raw);
                        Object val = ((ObjectSerializer) s).deserialize(node);
                        field.set(owner, val);
                    } else {
                        ObjectNode node = new ObjectNode(Map.of(resolved, raw));
                        Object val = ((ObjectSerializer) s).deserialize(node);
                        field.set(owner, val);
                    }
                    return;
                }
            }

            if (TypeUtils.isSimpleType(type)) {
                processPrimitive(owner, field, currentMap);
                return;
            }

            Object object;
            if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers())) {
                var ctor = type.getDeclaredConstructor(parent.getClass());
                ctor.setAccessible(true);
                object = ctor.newInstance(parent);
            } else {
                var ctor = type.getDeclaredConstructor();
                ctor.setAccessible(true);
                object = ctor.newInstance();
            }
            field.set(owner, object);

            Map<String, Object> subMap = Collections.emptyMap();
            Object sub = currentMap.get(resolved);
            if (sub instanceof Map) subMap = (Map<String, Object>) sub;

            for (Field mapField : type.getDeclaredFields()) {
                mapField.setAccessible(true);
                if (TypeUtils.isSimpleType(mapField.getType())) {
                    processPrimitive(object, mapField, subMap);
                } else {
                    processObject(object, mapField, subMap, object);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Object materializeContainerFromYaml(Object yamlValue, Class<?> targetType, Field field) {
        Type genericType = field != null ? field.getGenericType() : null;
        return materializeContainerFromYaml(yamlValue, targetType, genericType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Object materializeContainerFromYaml(Object yamlValue, Class<?> targetType, Type genericType) {
        if (yamlValue == null) return null;

        if (Map.class.isAssignableFrom(targetType)) {
            if (!(yamlValue instanceof Map)) return null;
            Map<?, ?> raw = (Map<?, ?>) yamlValue;
            Map newMap = (Map) TypeUtils.newDefaultContainer(targetType);

            Type valueGenericType = TypeUtils.getMapValueGenericType(genericType);
            Class<?> valueType = valueGenericType != null ? TypeUtils.getRawClass(valueGenericType) : null;

            for (Map.Entry<?, ?> en : raw.entrySet()) {
                Object v = en.getValue();
                Object converted = v;

                if (valueType != null && valueType != Object.class) {
                    if (TypeUtils.isCollectionOrMap(valueType)) {
                        converted = materializeContainerFromYaml(v, valueType, valueGenericType);
                    } else if (v instanceof Map && !TypeUtils.isSimpleType(valueType)) {
                        converted = buildPojoFromMap(valueType, (Map<String, Object>) v);
                    } else {
                        converted = convertToType(v, valueType);
                    }
                } else if (v instanceof Map) {
                    converted = tryBuildPojoFromUnknownMap(v);
                }

                newMap.put(en.getKey(), converted);
            }

            return newMap;
        }

        if (Collection.class.isAssignableFrom(targetType)) {
            if (!(yamlValue instanceof Collection)) return null;
            Collection<?> raw = (Collection<?>) yamlValue;
            Collection newCol = (Collection) TypeUtils.newDefaultContainer(targetType);

            Type elemGenericType = TypeUtils.getCollectionElementGenericType(genericType);
            Class<?> elemType = elemGenericType != null ? TypeUtils.getRawClass(elemGenericType) : null;

            for (Object v : raw) {
                Object converted;
                if (elemType == null || elemType == Object.class) {
                    converted = v;
                } else if (TypeUtils.isCollectionOrMap(elemType)) {
                    converted = materializeContainerFromYaml(v, elemType, elemGenericType);
                } else if (v instanceof Map && !TypeUtils.isSimpleType(elemType)) {
                    converted = buildPojoFromMap(elemType, (Map<String, Object>) v);
                } else {
                    converted = convertToType(v, elemType);
                }

                newCol.add(converted);
            }
            return newCol;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Object tryBuildPojoFromUnknownMap(Object v) {
        if (!(v instanceof Map<?, ?> map)) return v;
        return map;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Object convertToType(Object raw, Class<?> targetType) {
        if (raw == null) return null;
        if (targetType.isInstance(raw)) return raw;

        if (TypeUtils.isSimpleType(targetType)) {
            return TypeUtils.coerce(raw, targetType);
        }

        ObjectSerializer serializer = TypeUtils.findSerializerFor(targetType, serializers);
        if (serializer != null) {
            if (raw instanceof Map) {
                ObjectNode node = new ObjectNode((Map<String, Object>) raw);
                return serializer.deserialize(node);
            } else {
                ObjectNode node = new ObjectNode(Map.of("value", raw));
                return serializer.deserialize(node);
            }
        }

        if (raw instanceof Map) {
            return buildPojoFromMapWithField(targetType, null, (Map<String, Object>) raw);
        }

        return raw;
    }

    @SuppressWarnings("unchecked")
    Object buildPojoFromMap(Class<?> type, Map<String, Object> raw) {
        try {
            Object obj;
            Constructor<?> ctor;
            try {
                ctor = type.getDeclaredConstructor();
                ctor.setAccessible(true);
                obj = ctor.newInstance();
            } catch (NoSuchMethodException e) {
                // Instead of swallowing the error, we provide info and throw an exception
                throw new RuntimeException("[RozsConfig] Class '" + type.getName() + "' does not have a no-args constructor! Class could not be instantiated.");
            }

            for (Field f : TypeUtils.getAllFields(type)) {
                f.setAccessible(true);
                String key = TypeUtils.resolveKey(f);
                Object rv = raw.get(key);
                if (rv == null) continue;

                Class<?> ft = f.getType();
                Object converted;
                if (TypeUtils.isSimpleType(ft)) {
                    converted = TypeUtils.coerce(rv, ft);
                } else if (TypeUtils.isCollectionOrMap(ft)) {
                    converted = materializeContainerFromYaml(rv, ft, f);
                } else {
                    if (rv instanceof Map) {
                        converted = buildPojoFromMapWithField(ft, f, (Map<String, Object>) rv);
                    } else {
                        converted = convertToType(rv, ft);
                    }
                }
                try { f.set(obj, converted); } catch (IllegalAccessException ignored) {}
            }
            return obj;
        } catch (Exception e) {
            // Instead of returning the raw map on error, we display the error
            throw new RuntimeException("[RozsConfig] Properties could not be mapped to class '" + type.getName() + "': " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    Object buildPojoFromMapWithField(Class<?> type, Field owningField, Map<String, Object> raw) {
        try {
            Object obj;
            Constructor<?> ctor;
            try {
                ctor = type.getDeclaredConstructor();
                ctor.setAccessible(true);
                obj = ctor.newInstance();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("[RozsConfig] Class '" + type.getName() + "' does not have a no-args constructor! Class could not be instantiated.");
            }

            for (Field f : TypeUtils.getAllFields(type)) {
                f.setAccessible(true);
                String key = TypeUtils.resolveKey(f);
                Object rv = raw.get(key);
                if (rv == null) continue;

                Class<?> ft = f.getType();
                Object converted;

                if (TypeUtils.isSimpleType(ft)) {
                    converted = TypeUtils.coerce(rv, ft);
                }
                else if (TypeUtils.isCollectionOrMap(ft)) {
                    converted = materializeContainerFromYaml(rv, ft, f);
                }
                else if (rv instanceof Map) {
                    converted = buildPojoFromMapWithField(ft, f, (Map<String, Object>) rv);
                }
                else {
                    converted = convertToType(rv, ft);
                }

                f.set(obj, converted);
            }
            return obj;
        } catch (Exception e) {
            throw new RuntimeException("[RozsConfig] Properties could not be mapped to class '" + type.getName() + "': " + e.getMessage(), e);
        }
    }
}
