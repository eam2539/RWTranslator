package com.eam.rwtranslator.utils.ini;

import org.ini4j.Wini;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class RWIniFileLoaderTest {

    @Test
    public void store_preservesSampleFormatting() throws Exception {
        assertStorePreservesFormatting("test1.ini");
    }

    @Test
    public void store_preservesTemplateFormatting() throws Exception {
        assertStorePreservesFormatting("test2.ini");
    }

    
    private static void assertStorePreservesFormatting(String sampleFileName) throws Exception {
        SampleCopy sample = copySampleToTemp(sampleFileName);

        Wini ini = RWIniFileLoader.load(sample.working().toFile());
        RWIniFileLoader.store(ini);

        String original = readUtf8(sample.source());
        String stored = readUtf8(sample.working());

        Assert.assertEquals("Stored INI content should match original for " + sampleFileName, original, stored);
    }

    private static String readUtf8(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static SampleCopy copySampleToTemp(String sampleFileName) throws Exception {
        Path samplePath = Paths.get("../samples", sampleFileName);
        if (!Files.exists(samplePath)) {
            throw new IllegalStateException("Sample INI file missing: " + samplePath.toAbsolutePath());
        }

        Path tempDir = Files.createTempDirectory("ini-enhancer-test");
        Path workingFile = tempDir.resolve(sampleFileName);
        Files.copy(samplePath, workingFile, StandardCopyOption.REPLACE_EXISTING);
        return new SampleCopy(samplePath, workingFile);
    }

    private record SampleCopy(Path source, Path working) {
    }
}
