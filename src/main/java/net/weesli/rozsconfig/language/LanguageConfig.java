package net.weesli.rozsconfig.language;

import net.weesli.rozsconfig.serializer.ConfigMapper;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LanguageConfig<T> {

    private final Map<String, ConfigMapper> languageMap = new HashMap<>();
    private final Map<String, T> objects = new HashMap<>();

    public LanguageConfig(List<String> languageKeys, Path path, String configName, @Nullable String defaultConfig, Class<T> clazz) {
        for (String languageKey : languageKeys) {
            ConfigMapper mapper = ConfigMapper.of(clazz)
                    .load(defaultConfig.replace("{language}", languageKey))
                    .file(path.resolve(languageKey).resolve(configName + ".yml").toFile());
            languageMap.put(languageKey, mapper);
        }
    }

    public T get(String languageKey ){
        if (objects.containsKey(languageKey)) return objects.get(languageKey);
        T object = languageMap.get(languageKey).build();
        objects.put(languageKey, object);
        return object;
    }

    public void save(String languageKey){
        languageMap.get(languageKey)
                .save(objects.get(languageKey));
    }

    public List<String> getLanguageKeys() {
        return languageMap.keySet().stream().toList();
    }

    public void saveAll() {
        for (String languageKey : languageMap.keySet()) {
            save(languageKey);
        }
    }
}
