package com.eam.rwtranslator.ui.project;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import com.eam.rwtranslator.utils.ini.RWIniFileLoader;
import com.google.gson.Gson;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.ini4j.Wini;
import org.junit.Test;

public class TranslationConfigManagerTest {

    @Test
    public void rehydrateTranslationIniFiles_convertsLinkedTreeMapEntries() throws Exception {
        TranslationConfigManager manager = new TranslationConfigManager();
        manager.translationIniFiles = new HashMap<>();

        File tempIni = File.createTempFile("rehydrate", ".ini");
        tempIni.deleteOnExit();
        Files.write(tempIni.toPath(), "[core]\nname:value\n".getBytes(StandardCharsets.UTF_8));

        String json = "{" +
            "\"configFile\":\"" + tempIni.getAbsolutePath().replace("\\", "\\\\") + "\"," +
            "\"core\":{\"name\":\"value\"}" +
            "}";

        Object raw = new Gson().fromJson(json, Object.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> unsafeMap = (Map<String, Object>) (Map<?, ?>) manager.translationIniFiles;
        unsafeMap.put(tempIni.getAbsolutePath(), raw);

        manager.rehydrateTranslationIniFilesIfNeeded();

        Wini restored = manager.translationIniFiles.get(tempIni.getAbsolutePath());
        assertNotNull(restored);
        assertEquals("value", restored.get("core", "name"));
    }

    @Test
    public void rehydrateTranslationIniFiles_keepsExistingWiniInstances() throws Exception {
        TranslationConfigManager manager = new TranslationConfigManager();
        manager.translationIniFiles = new HashMap<>();

        File tempIni = File.createTempFile("rehydrate-existing", ".ini");
        tempIni.deleteOnExit();
        Files.write(tempIni.toPath(), "[core]\nname:value\n".getBytes(StandardCharsets.UTF_8));

        Wini ini = RWIniFileLoader.createEmpty();
        ini.setFile(tempIni);
        ini.add("core", "name", "value");
        RWIniFileLoader.ensureDocument(ini);

        manager.translationIniFiles.put(tempIni.getAbsolutePath(), ini);

        manager.rehydrateTranslationIniFilesIfNeeded();

        assertSame(ini, manager.translationIniFiles.get(tempIni.getAbsolutePath()));
    }
}
