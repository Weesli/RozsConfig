package net.weesli.rozsconfig.serializer;

import net.weesli.rozsconfig.annotations.Comment;
import net.weesli.rozsconfig.annotations.ConfigKey;
import net.weesli.rozsconfig.annotations.IgnoreField;
import net.weesli.rozsconfig.annotations.IgnoreKeys;
import net.weesli.rozsconfig.language.LanguageConfig;
import net.weesli.rozsconfig.model.RozsConfig;
import net.weesli.rozsconfig.serializer.component.ObjectNode;
import net.weesli.rozsconfig.serializer.component.ObjectSerializer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;

public final class ConfigMapper {

    private final Yaml yaml;
    private Class<?> clazz;
    private File file;
    private Map<String, Object> defaultValues = new HashMap<>();
    private Map<String, Object> currentValues = new HashMap<>();
    private final List<ObjectSerializer<?>> serializers = new ArrayList<>();

    public ConfigMapper() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        yaml = new Yaml(options);
    }

    public static ConfigMapper of(Class<?> clazz) {
        ConfigMapper mapper = new ConfigMapper();
        mapper.clazz = clazz;
        return mapper;
    }

    public ConfigMapper file(File file){
        this.file = file;
        createFile(this.file);
        return this;
    }

    public ConfigMapper file(String path){
        this.file = new File(path);
        createFile(this.file);
        return this;
    }

    private void createFile(File file){
        File parent = this.file.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
        if (!this.file.exists()) {
            try {
                this.file.createNewFile();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public ConfigMapper load(InputStream is){
        if (is == null) return this;
        Map<String, Object> loaded = yaml.load(is);
        defaultValues = (loaded != null) ? loaded : new HashMap<>();
        return this;
    }

    public ConfigMapper load(File file){
        if (file == null) return this;
        try(FileReader reader = new FileReader(file)){
            Map<String, Object> loaded = yaml.load(reader);
            defaultValues = (loaded != null) ? loaded : new HashMap<>();
        } catch (Exception e){
            throw new RuntimeException(e);
        }
        return this;
    }

    public ConfigMapper load(String path){
        if (path == null) return this;
        try(FileReader reader = new FileReader(path)){
            Map<String, Object> loaded = yaml.load(reader);
            defaultValues = (loaded != null) ? loaded : new HashMap<>();
        } catch (Exception e){
            throw new RuntimeException(e);
        }
        return this;
    }

    public ConfigMapper withSerializer(ObjectSerializer<?> serializer){
        serializers.add(serializer);
        return this;
    }

    public <T> LanguageConfig<T> asLanguageConfig(
            List<String> languageKeys,
            Path path,
            String configName,
            Map<String,InputStream> defaultConfig,
            Class<T> clazz
    ){
        return new LanguageConfig<>(languageKeys, path, configName, defaultConfig,clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T build() {
        try {
            T config = (T) clazz.getDeclaredConstructor().newInstance();
            Set<String> processed = new HashSet<>();
            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> loaded = yaml.load(reader);
                currentValues = (loaded != null) ? loaded : new HashMap<>();
            }
            Set<String> changeablePrefixes = collectChangeableMapPrefixes(clazz);
            deepMergeDefaultsIntoCurrent(defaultValues, currentValues, "", changeablePrefixes);

            applyRozsConfig(config,clazz, currentValues);
            for (Field field : getAllFields(clazz)) {
                if (!processed.add(field.getName())) continue;

                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                    continue;
                }

                if (field.isAnnotationPresent(IgnoreField.class)) continue;
                field.setAccessible(true);

                if (isSimpleType(field.getType())) {
                    processPrimitive(config, field, currentValues);
                } else {
                    processObject(config, field, currentValues, config);
                }
            }
            return config;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void applyRozsConfig(Object o, Class<?> clazz, Map<String, Object> currentValues) {
        if (RozsConfig.class.isAssignableFrom(clazz)) {
            try {
                Field field = RozsConfig.class.getDeclaredField("node");
                field.setAccessible(true);
                field.set(o, new ObjectNode(currentValues));
            }catch (NoSuchFieldException ignored){
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void save(Object object) {
        try (FileWriter writer = new FileWriter(file)) {
            StringBuilder sb = new StringBuilder();
            writeYamlWithComments(object, sb, 0);
            writer.write(sb.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeYamlWithComments(Object obj, StringBuilder sb, int indent) throws IllegalAccessException {
        for (Field field : getAllFields(obj.getClass())) {
            if (field.getType() == ObjectNode.class) continue;
            if (field.isAnnotationPresent(IgnoreField.class)) continue;
            field.setAccessible(true);

            Object value = field.get(obj);
            if (value == null) continue;

            String key = resolveKey(field);

            if (field.isAnnotationPresent(Comment.class)) {
                Comment comment = field.getAnnotation(Comment.class);
                for (String s : comment.value()) {
                    indent(sb, indent).append("# ").append(s).append("\n");
                }
            }

            Object plain = toPlain(value);
            dumpSingleEntry(sb, indent, key, plain);
        }
    }

    private void dumpSingleEntry(StringBuilder sb, int indent, String key, Object plainVal) {
        Map<String, Object> one = new LinkedHashMap<>();
        one.put(key, plainVal);

        String dumped = yaml.dump(one);
        if (dumped.endsWith("\n")) dumped = dumped.substring(0, dumped.length() - 1);

        String[] lines = dumped.split("\n", -1);
        for (String line : lines) {
            if (line.isEmpty()) continue;
            indent(sb, indent).append(line).append("\n");
        }
    }

    private void writeFields(Object obj, StringBuilder sb, int indent) throws IllegalAccessException {
        for (Field field : getAllFields(obj.getClass())) {
            if (field.getType() == ObjectNode.class) continue;
            field.setAccessible(true);
            Object value = field.get(obj);
            if (value == null) continue;

            String key = resolveKey(field);

            if (field.isAnnotationPresent(Comment.class)) {
                Comment comment = field.getAnnotation(Comment.class);
                for (String s : comment.value()) {
                    indent(sb, indent).append("# ").append(s).append("\n");
                }
            }

            if (isSimpleType(field.getType())) {
                indent(sb, indent).append(key).append(": ").append(value).append("\n");
            } else {
                indent(sb, indent).append(key).append(":\n");
                writeFields(value, sb, indent + 2);
            }
        }
    }

    private StringBuilder indent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append(" ");
        return sb;
    }



    private void processPrimitive(Object owner, Field field, Map<String,Object> currentMap){
        try {
            String key = resolveKey(field);
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
                Object val = coerce(raw, field.getType());
                field.set(owner, val);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void processObject(Object owner, Field field, Map<String, Object> currentMap, Object parent) {
        try {
            String resolved = resolveKey(field);
            Object existing = currentMap.get(resolved);
            Class<?> type = field.getType();
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                return;
            }
            if (type == ObjectNode.class){
                return;
            }
            if (owner.getClass().isAnnotationPresent(IgnoreField.class)) return;
            if (existing == null && isCollectionOrMap(type)) {
                Object empty = newDefaultContainer(type);
                field.set(owner, empty);
                return;
            }

            if (existing != null && isCollectionOrMap(type)) {
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


            if (isSimpleType(type)) {
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
                if (isSimpleType(mapField.getType())) {
                    processPrimitive(object, mapField, subMap);
                } else {
                    processObject(object, mapField, subMap, object);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            fields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }
        return fields;
    }

    private static boolean isWrapper(Class<?> c) {
        return c == Integer.class || c == Long.class || c == Double.class ||
                c == Float.class || c == Boolean.class || c == Byte.class ||
                c == Character.class || c == Short.class;
    }

    private static boolean isSimpleType(Class<?> c) {
        return c.isPrimitive() || isWrapper(c) || c == String.class || c.isEnum();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object toPlain(Object value) {
        if (value == null) return null;
        Class<?> t = value.getClass();

        if (isSimpleType(t) && !value.getClass().isEnum()) return value;
        if (isSimpleType(t) && value.getClass().isEnum()) return ((Enum<?>) value).name();

        ObjectSerializer ser = findSerializerFor(t);
        if (ser != null) {
            ObjectNode node = new ObjectNode(new LinkedHashMap<>());
            ser.serialize(value, node);
            return node.getVariableMap();
        }

        if (value instanceof Map<?,?> map) {
            Map<Object,Object> out = new LinkedHashMap<>();
            for (Map.Entry<?,?> e : map.entrySet()) {
                Object k = e.getKey();
                Object v = e.getValue();
                out.put(k, toPlain(v));
            }
            return out;
        }

        if (value instanceof Enum<?>){
            return ((Enum<?>) value).name();
        }

        if (value instanceof Collection<?> col) {
            List<Object> out = new ArrayList<>(col.size());
            for (Object v : col) out.add(toPlain(v));
            return out;
        }

        Map<String,Object> out = new LinkedHashMap<>();
        for (Field f : getAllFields(t)) {
            try {
                if (f.getType() == ObjectNode.class) continue;
                f.setAccessible(true);
                Object fv = f.get(value);
                if (fv == null) continue;
                out.put(resolveKey(f), toPlain(fv));
            } catch (IllegalAccessException ignored) {}
        }
        return out;
    }

    private static boolean isCollectionOrMap(Class<?> c) {
        return Map.class.isAssignableFrom(c) || Collection.class.isAssignableFrom(c);
    }

    @SuppressWarnings("unchecked")
    private static Object newDefaultContainer(Class<?> c) {
        if (Map.class.isAssignableFrom(c)) {
            if (SortedMap.class.isAssignableFrom(c)) return new TreeMap<>();
            if (java.util.concurrent.ConcurrentMap.class.isAssignableFrom(c)) return new java.util.concurrent.ConcurrentHashMap<>();
            return new LinkedHashMap<>();
        }
        if (Set.class.isAssignableFrom(c)) {
            if (SortedSet.class.isAssignableFrom(c)) return new TreeSet<>();
            return new LinkedHashSet<>();
        }
        if (List.class.isAssignableFrom(c)) return new ArrayList<>();
        if (Deque.class.isAssignableFrom(c) || Queue.class.isAssignableFrom(c)) return new ArrayDeque<>();
        if (Collection.class.isAssignableFrom(c)) return new ArrayList<>();
        throw new IllegalArgumentException("Unsupported container type for default: " + c.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object materializeContainerFromYaml(Object yamlValue, Class<?> targetType, Field field) {
        if (yamlValue == null) return null;

        if (Map.class.isAssignableFrom(targetType)) {
            if (!(yamlValue instanceof Map)) return null;
            Map<?, ?> raw = (Map<?, ?>) yamlValue;
            Map newMap = (Map) newDefaultContainer(targetType);

            Class<?> valueType = getMapValueType(field);
            for (Map.Entry<?, ?> en : raw.entrySet()) {
                Object v = en.getValue();
                Object converted;
                if (valueType == null || valueType == Object.class) {
                    converted = v;
                } else if (v instanceof Map && !isSimpleType(valueType)) {
                    converted = buildPojoFromMap(valueType, (Map<String, Object>) v);
                } else {
                    converted = convertToType(v, valueType);
                }

                newMap.put(en.getKey(), converted);
            }
            return newMap;
        }

        if (Collection.class.isAssignableFrom(targetType)) {
            if (!(yamlValue instanceof Collection)) return null;
            Collection<?> raw = (Collection<?>) yamlValue;
            Collection newCol = (Collection) newDefaultContainer(targetType);

            Class<?> elemType = getCollectionElementType(field);
            for (Object v : raw) {
                Object converted;
                if (elemType == null || elemType == Object.class) {
                    converted = v;
                } else if (v instanceof Map && !isSimpleType(elemType)) {
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

    private Class<?> getMapValueType(Field f) {
        Type t = f.getGenericType();
        if (t instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 2) {
                Type val = args[1];
                if (val instanceof Class<?>) return (Class<?>) val;
                if (val instanceof ParameterizedType pval && pval.getRawType() instanceof Class<?>)
                    return (Class<?>) pval.getRawType();
            }
        }
        return null;
    }

    private Class<?> getCollectionElementType(Field f) {
        Type t = f.getGenericType();
        if (t instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 1) {
                Type val = args[0];
                if (val instanceof Class<?>) return (Class<?>) val;
                if (val instanceof ParameterizedType pval && pval.getRawType() instanceof Class<?>)
                    return (Class<?>) pval.getRawType();
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convertToType(Object raw, Class<?> targetType) {
        if (raw == null) return null;
        if (targetType.isInstance(raw)) return raw;

        if (isSimpleType(targetType)) {
            return coerce(raw, targetType);
        }

        ObjectSerializer serializer = findSerializerFor(targetType);
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
            return buildPojoFromMap(targetType, (Map<String, Object>) raw);
        }

        return raw;
    }

    private ObjectSerializer<?> findSerializerFor(Class<?> type) {
        for (ObjectSerializer<?> s : serializers) {
            if (s.isType(type)) return s;
        }
        return null;
    }
    @SuppressWarnings("unchecked")
    private Object buildPojoFromMap(Class<?> type, Map<String, Object> raw) {
        try {
            Object obj;
            Constructor<?> ctor = null;
            try { ctor = type.getDeclaredConstructor(); } catch (NoSuchMethodException ignored) {}
            if (ctor != null) {
                ctor.setAccessible(true);
                obj = ctor.newInstance();
            } else {
                return raw;
            }

            for (Field f : getAllFields(type)) {
                f.setAccessible(true);
                String key = resolveKey(f);
                Object rv = raw.get(key);
                if (rv == null) continue;

                Class<?> ft = f.getType();
                Object converted;
                if (isSimpleType(ft)) {
                    converted = coerce(rv, ft);
                } else if (isCollectionOrMap(ft)) {
                    converted = materializeContainerFromYaml(rv, ft, f);
                } else {
                    if (rv instanceof Map) {
                        converted = buildPojoFromMap(ft, (Map<String, Object>) rv);
                    } else {
                        converted = convertToType(rv, ft);
                    }
                }
                try { f.set(obj, converted); } catch (IllegalAccessException ignored) {}
            }
            return obj;
        } catch (Exception e) {
            return raw;
        }
    }

    private static String resolveKey(Field f) {
        if (f.isAnnotationPresent(ConfigKey.class)) {
            return f.getAnnotation(ConfigKey.class).value();
        }
        return f.getName();
    }

    @SuppressWarnings("unchecked")
    private static Object coerce(Object v, Class<?> target) {
        if (v == null) return null;
        if (target.isInstance(v)) return v;
        if (target.isEnum()) return Enum.valueOf((Class<? extends Enum>) target, v.toString());
        if (target == String.class) return String.valueOf(v);
        if (target == boolean.class || target == Boolean.class)
            return (v instanceof Boolean) ? v : Boolean.parseBoolean(v.toString());
        if (v instanceof Number n) {
            if (target == int.class || target == Integer.class) return n.intValue();
            if (target == long.class || target == Long.class)   return n.longValue();
            if (target == double.class || target == Double.class) return n.doubleValue();
            if (target == float.class || target == Float.class) return n.floatValue();
            if (target == short.class || target == Short.class) return n.shortValue();
            if (target == byte.class || target == Byte.class)   return n.byteValue();
        }
        return v;
    }
    @SuppressWarnings("unchecked")
    private static void deepMergeDefaultsIntoCurrent(Map<String, Object> defaults, Map<String, Object> current, String path, Set<String> changeablePrefixes) {
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
            else if (!ignoreChildren && dVal instanceof Collection && cVal instanceof Collection) {
                Collection<?> defaultCol = (Collection<?>) dVal;
                Collection<?> currentCol = (Collection<?>) cVal;

                if (currentCol.isEmpty() && !defaultCol.isEmpty()) {
                    current.put(key, new ArrayList<>(defaultCol));
                }
            }
        }
    }

    private Set<String> collectChangeableMapPrefixes(Class<?> root) {
        Set<String> out = new HashSet<>();
        collectChangeableMapPrefixesRecursive(root, "", out, new HashSet<>(), currentValues);
        return out;
    }
    @SuppressWarnings("unchecked")
    private void collectChangeableMapPrefixesRecursive(
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

        for (Field f : getAllFields(type)) {
            f.setAccessible(true);
            String key = resolveKey(f);

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
                Class<?> valueType = getMapValueType(f);
                Map<String, Object> nextMap = (next instanceof Map) ? (Map<String, Object>) next : null;

                if (valueType != null && !isSimpleType(valueType)) {
                    collectChangeableMapPrefixesRecursive(valueType, full, out, visited, nextMap);
                }
                continue;
            }

            if (Collection.class.isAssignableFrom(ft)) {
                Class<?> elemType = getCollectionElementType(f);
                if (elemType != null && !isSimpleType(elemType)) {
                    collectChangeableMapPrefixesRecursive(elemType, full, out, visited, null);
                }
                continue;
            }
            if (!isSimpleType(ft)) {
                Map<String, Object> nextMap = (next instanceof Map) ? (Map<String, Object>) next : null;
                collectChangeableMapPrefixesRecursive(ft, full, out, visited, nextMap);
            }
        }
    }


}
