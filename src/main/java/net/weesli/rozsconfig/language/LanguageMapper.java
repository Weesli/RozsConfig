package net.weesli.rozsconfig.language;

import net.weesli.rozsconfig.serializer.ConfigMapper;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;

public final class LanguageMapper {

    private final Path languageFolderPath;

    public  LanguageMapper(Path languageFolderPath) {
        this.languageFolderPath = languageFolderPath;
        if (!languageFolderPath.toFile().exists()) {
            languageFolderPath.toFile().mkdirs();
        }
    }

    public <T> LanguageFile<T> get(String languageKey, String file, InputStream defaultConfig) {
        return new LanguageFile<>(this, languageKey, languageFolderPath.resolve(languageKey).resolve(file + ".yml").toFile(), defaultConfig);
    }

    public void save(ConfigMapper mapper, String languageKey, File file, Object object) {
        final Path languageFolder = languageFolderPath.resolve(languageKey);
        if (!languageFolder.toFile().exists()) {
            languageFolder.toFile().mkdirs();
        }
        mapper.file(file).save(object);
    }
}
