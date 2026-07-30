package net.weesli.rozsconfig.serializer;

import net.weesli.rozsconfig.annotations.IgnoreField;
import net.weesli.rozsconfig.language.LanguageConfig;
import net.weesli.rozsconfig.serializer.component.ObjectSerializer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
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

    public ConfigMapper load(InputStream is) {
        if (is == null) return this;

        try {
            String yamlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            loadAndPreserveComments(this.file, yamlContent);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return this;
    }


    public ConfigMapper load(File file) {
        if (file == null) return this;

        try (FileReader reader = new FileReader(file)) {
            String content = readerToString(reader);
            loadAndPreserveComments(file, content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public ConfigMapper load(String path) {
        return load(new File(path));
    }

    // Load helpers \ start
    private String readerToString(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int r;
        while ((r = reader.read(buf)) != -1) {
            sb.append(buf, 0, r);
        }
        return sb.toString();
    }

    private void loadAndPreserveComments(File file, String yamlContent) {
        try {
            if (!file.exists() || file.length() == 0) {
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(yamlContent);
                }
            } else {
                Map<String, Object> resourceValues = yaml.load(yamlContent);
                Map<String, Object> diskValues;
                try (FileReader reader = new FileReader(file)) {
                    diskValues = yaml.load(reader);
                }
                if (resourceValues == null) resourceValues = new HashMap<>();
                if (diskValues == null) diskValues = new HashMap<>();

                // Remove NullableFields from resourceValues so they are not forcefully merged if they don't exist on disk
                removeNullableFields(clazz, resourceValues);

                int sizeBefore = countKeys(diskValues);
                merge(resourceValues, diskValues);
                int sizeAfter = countKeys(diskValues);

                if (sizeAfter > sizeBefore) {
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(yaml.dump(diskValues));
                    }
                }
            }

            try (FileReader reader = new FileReader(file)) {
                Map<String, Object> loaded = yaml.load(reader);
                defaultValues = (loaded != null) ? loaded : new HashMap<>();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void removeNullableFields(Type currentType, Object value) {
        if (currentType == null || value == null) return;
        
        Class<?> rawClass = TypeUtils.getRawClass(currentType);
        if (rawClass == null || TypeUtils.isSimpleType(rawClass)) return;

        if (Map.class.isAssignableFrom(rawClass) && value instanceof Map) {
            Type valType = TypeUtils.getMapValueGenericType(currentType);
            if (valType != null) {
                for (Object nestedVal : ((Map<?, ?>) value).values()) {
                    removeNullableFields(valType, nestedVal);
                }
            }
        } else if (Collection.class.isAssignableFrom(rawClass) && value instanceof Collection) {
            Type elemType = TypeUtils.getCollectionElementGenericType(currentType);
            if (elemType != null) {
                for (Object nestedVal : (Collection<?>) value) {
                    removeNullableFields(elemType, nestedVal);
                }
            }
        } else if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            for (Field field : TypeUtils.getAllFields(rawClass)) {
                String key = field.getName();
                if (field.isAnnotationPresent(net.weesli.rozsconfig.annotations.ConfigKey.class)) {
                    key = field.getAnnotation(net.weesli.rozsconfig.annotations.ConfigKey.class).value();
                }

                if (field.isAnnotationPresent(net.weesli.rozsconfig.annotations.NullableField.class)) {
                    map.remove(key);
                } else if (map.containsKey(key)) {
                    removeNullableFields(field.getGenericType(), map.get(key));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private int countKeys(Map<String, Object> map) {
        int count = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            count++;
            if (entry.getValue() instanceof Map) {
                count += countKeys((Map<String, Object>) entry.getValue());
            }
        }
        return count;
    }

    private void merge(Map<String, Object> source, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {

            String key = entry.getKey();
            Object value = entry.getValue();

            if (!target.containsKey(key)) {
                target.put(key, value);
                continue;
            }

            Object targetValue = target.get(key);

            if (value instanceof Map<?, ?> sourceMap &&
                    targetValue instanceof Map<?, ?> targetMap) {

                merge((Map<String, Object>) sourceMap,
                        (Map<String, Object>) targetMap);
            }
        }
    }

    // load helpers \ end

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

            ConfigReader configReader = new ConfigReader(serializers);

            Set<String> changeablePrefixes = DeepMerger.collectChangeableMapPrefixes(clazz, currentValues);
            DeepMerger.deepMergeDefaultsIntoCurrent(defaultValues, currentValues, "", changeablePrefixes);

            configReader.applyRozsConfig(config, clazz, currentValues);
            for (Field field : TypeUtils.getAllFields(clazz)) {
                if (!processed.add(field.getName())) continue;

                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                    continue;
                }

                if (field.isAnnotationPresent(IgnoreField.class)) continue;
                field.setAccessible(true);

                if (TypeUtils.isSimpleType(field.getType())) {
                    configReader.processPrimitive(config, field, currentValues);
                } else {
                    configReader.processObject(config, field, currentValues, config);
                }
            }
            return config;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public void save(Object object) {
        try (FileWriter writer = new FileWriter(file)) {
            StringBuilder sb = new StringBuilder();
            ConfigWriter configWriter = new ConfigWriter(yaml, serializers);
            configWriter.writeYamlWithComments(object, sb);
            writer.write(sb.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Field> getAllFields(Class<?> clazz) {
        return TypeUtils.getAllFields(clazz);
    }
}
