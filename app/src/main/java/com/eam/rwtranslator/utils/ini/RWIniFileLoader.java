package com.eam.rwtranslator.utils.ini;

import org.ini4j.Config;
import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Provides comment-preserving load/store helpers around INTJ.
 */
public final class RWIniFileLoader {
    private static final Map<Wini, IniDocument> DOCUMENTS = Collections.synchronizedMap(new WeakHashMap<>());

    private RWIniFileLoader() {
    }

    /**
     * Loads an INI file with triple-quote and comment preservation.
     */
    public static Wini load(File file) throws IOException {
        if (file == null) {
            throw new IOException("File reference is null");
        }
        IniDocument document = IniDocument.parse(file);
        Wini wini = createEmpty();
        try (Reader reader = new StringReader(document.toSanitizedContent())) {
            wini.load(reader);
        }
        wini.setFile(file);
        document.restoreRawValues(wini);
        DOCUMENTS.put(wini, document);
        return wini;
    }

    /**
     * Ensures the metadata required for comment-preserving store exists.
     */
    public static void ensureDocument(Wini wini) throws IOException {
        if (wini == null || DOCUMENTS.containsKey(wini)) {
            return;
        }
        File file = wini.getFile();
        if (file == null || !file.exists()) {
            return;
        }
        DOCUMENTS.put(wini, IniDocument.parse(file));
    }

    /**
     * Stores the INI file while retaining comments and triple-quoted content.
     */
    public static void store(Wini wini) throws IOException {
        if (wini == null) {
            return;
        }
        File file = wini.getFile();
        if (file == null) {
            throw new IOException("Cannot store Wini without backing file");
        }
        IniDocument document = DOCUMENTS.get(wini);
        if (document == null) {
            document = IniDocument.parse(file);
        }
        document.write(wini);
        DOCUMENTS.put(wini, IniDocument.parse(file));
    }

    /**
     * Creates an empty Wini with the expected config flags.
     */
    public static Wini createEmpty() {
        Config config = new Config();
        config.setMultiSection(true);
        config.setEmptyOption(true);
        config.setEscape(false);
        Wini wini = new Wini();
        wini.setConfig(config);
        return wini;
    }
}
