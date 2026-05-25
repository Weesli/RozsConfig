package net.weesli.rozsconfig.serializer;

import net.weesli.rozsconfig.annotations.IgnoreKeys;

import java.lang.reflect.Field;
import java.util.*;


final class DeepMerger {

    private DeepMerger() {}

    @SuppressWarnings("unchecked")
    static void deepMergeDefaultsIntoCurrent(Map<String, Object> defaults, Map<String, Object> current, String path, Set<String> changeablePrefixes) {
        if (defaults == null) return;
        if (current == null) return;
        for (Map.Entry<String, Object> e : defaults.entrySet()) {
            String key = e.getKey();
            String fullPath = path.isEmpty() ? key : path + "." + key;
            if (!current.containsKey(key)) {
                current.put(key, e.getValue());
                continue;
            }
            Object dVal = e.getValue();
            Object cVal = current.get(key);
            boolean ignoreChildren = changeablePrefixes.contains(fullPath);

            if (!ignoreChildren && dVal instanceof Map && cVal instanceof Map) {
                deepMergeDefaultsIntoCurrent(
                        (Map<String, Object>) dVal,
                        (Map<String, Object>) cVal,
                        fullPath,
                        changeablePrefixes
                );
            }
            else if (!ignoreChildren && dVal instanceof Collection<?> defaultCol && cVal instanceof Collection<?> currentCol) {
                if (currentCol.isEmpty() && !defaultCol.isEmpty()) {
                    current.put(key, new ArrayList<>(defaultCol));
                }
            }
        }
    }

    static Set<String> collectChangeableMapPrefixes(Class<?> root, Map<String, Object> currentValues) {
        Set<String> out = new HashSet<>();
        collectChangeableMapPrefixesRecursive(root, "", out, new HashSet<>(), currentValues);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collectChangeableMapPrefixesRecursive(
            Class<?> type,
            String path,
            Set<String> out,
            Set<Class<?>> visited,
            Map<String, Object> currentAtLevel
    ) {
        if (type == null || type == Object.class) return;
        if (!visited.add(type)) return;
        if (type.isAnnotationPresent(IgnoreKeys.class) && !path.isEmpty()) {
            out.add(path);
        }

        for (Field f : TypeUtils.getAllFields(type)) {
            f.setAccessible(true);
            String key = TypeUtils.resolveKey(f);

            if (currentAtLevel != null && !currentAtLevel.containsKey(key)) {
                continue;
            }

            String full = path.isEmpty() ? key : path + "." + key;
            Class<?> ft = f.getType();
            Object next = (currentAtLevel != null) ? currentAtLevel.get(key) : null;

            if (f.isAnnotationPresent(IgnoreKeys.class)) {
                out.add(full);
            }

            if (Map.class.isAssignableFrom(ft)) {
                Class<?> valueType = TypeUtils.getMapValueType(f);
                Map<String, Object> nextMap = (next instanceof Map) ? (Map<String, Object>) next : null;

                if (valueType != null && !TypeUtils.isSimpleType(valueType)) {
                    collectChangeableMapPrefixesRecursive(valueType, full, out, visited, nextMap);
                }
                continue;
            }

            if (Collection.class.isAssignableFrom(ft)) {
                Class<?> elemType = TypeUtils.getCollectionElementType(f);
                if (elemType != null && !TypeUtils.isSimpleType(elemType)) {
                    collectChangeableMapPrefixesRecursive(elemType, full, out, visited, null);
                }
                continue;
            }
            if (!TypeUtils.isSimpleType(ft)) {
                Map<String, Object> nextMap = (next instanceof Map) ? (Map<String, Object>) next : null;
                collectChangeableMapPrefixesRecursive(ft, full, out, visited, nextMap);
            }
        }
    }
}
