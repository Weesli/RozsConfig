package net.weesli.rozsconfig.language;


import net.weesli.rozsconfig.serializer.ConfigMapper;

import java.io.File;
import java.io.InputStream;

public class LanguageFile<T> {
    private final LanguageMapper mapper;
    private final String languageKey;
    private final File file;
    private final InputStream defaultConfig;

    public LanguageFile(LanguageMapper mapper, String languageKey, File file, InputStream defaultConfig) {
        this.mapper = mapper;
        this.languageKey = languageKey;
        this.file = file;
        this.defaultConfig = defaultConfig;
    }

    public T getConfig(ConfigMapper mapper) {
        return mapper.
                load(defaultConfig)
                .file(file)
                .build();
    }

    public void save(ConfigMapper mapper, T object) {
        this.mapper.save(mapper, languageKey, file, object);
    }

}
