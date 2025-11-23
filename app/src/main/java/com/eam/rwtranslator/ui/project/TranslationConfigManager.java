package com.eam.rwtranslator.ui.project;


import com.eam.rwtranslator.AppConfig;
import com.eam.rwtranslator.data.model.SectionModel;
import com.eam.rwtranslator.utils.FilesHandler;
import com.eam.rwtranslator.utils.TranslationKeys;
import com.eam.rwtranslator.utils.deserializer.FileDeserializer;
import com.eam.rwtranslator.utils.deserializer.HashMapWiniDeserializer;
import com.eam.rwtranslator.utils.deserializer.WiniDeserializer;
import com.eam.rwtranslator.utils.ini.RWIniFileLoader;
import com.eam.rwtranslator.utils.serializer.FileSerializer;
import com.eam.rwtranslator.utils.serializer.HashMapWiniSerializer;
import com.eam.rwtranslator.utils.serializer.WiniSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.ini4j.Ini;
import org.ini4j.Wini;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;

import timber.log.Timber;

/**
 * 项目翻译配置管理类，负责INI文件的加载、序列化、反序列化、多语言键值对管理等。
 */
public class TranslationConfigManager {
    // 用于记录加载异常的INI文件,异常，信息
    public static final Map<File, Exception> errorFiles = Collections.synchronizedMap(new HashMap<>());
    private static final String PROJECT_FILE_EXTENSION = ".json";
    // INI文件映射
    public HashMap<String, Wini> translationIniFiles;
    // 项目名
    public String projectName;
    // 项目根目录
    public File projectRootDir;
    // 项目序列化文件
    public File projectFile;
    // 线程池
    private transient ExecutorService executorService;

    /**
     * 无参构造函数，主要用于反序列化。
     * 使用此构造函数创建的对象需要手动设置projectFile字段。
     */
    public TranslationConfigManager() {
        this.executorService = Executors.newFixedThreadPool(10);
    }

    public TranslationConfigManager(File projectRootDir, HashMap<String, Wini> translationIniFiles) throws IOException {
        this.executorService = Executors.newFixedThreadPool(10);
        this.translationIniFiles = translationIniFiles;
        this.projectRootDir = projectRootDir;
        this.projectFile =
                new File(
                        AppConfig.externalCacheSerialDir, projectRootDir.getName() + PROJECT_FILE_EXTENSION);
        serialize();
    }

    /**
     * 根据项目根目录获取项目实例。
     *
     * @param projectRootDir 项目根目录
     * @return 项目实例
     */

    public static TranslationConfigManager getInstance(File projectRootDir)
            throws IOException, ClassNotFoundException {

        File projectFile =
                new File(
                        AppConfig.externalCacheSerialDir, projectRootDir.getName() + PROJECT_FILE_EXTENSION);

        if (projectFile.exists()) {
            return deserialize(projectFile);
        } else {
            var translationIniFiles = loadTranslationIniFiles(FilesHandler.LeachFilename(projectRootDir));
            projectFile.createNewFile();

            return new TranslationConfigManager(projectRootDir, translationIniFiles);
        }
    }

    /**
     * 获取含有多语言键值对的文件
     *
     * @param files 文件列表
     * @return 含有多语言键值对的INI配置文件列表
     */
    public static HashMap<String, Wini> loadTranslationIniFiles(ArrayList<File> files) {
        // 清空异常文件列表，准备新的加载过程
        errorFiles.clear();

        HashMap<String, Wini> translationIniFilesMap = new HashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (File file : files) {
            CompletableFuture<Void> future =
                    CompletableFuture.supplyAsync(
                                    () -> {
                                        try {
                                            Wini ini = RWIniFileLoader.load(file);
                                            for (Ini.Section section : ini.values()) {
                                                for (TranslationKeys tranKey : TranslationKeys.values()) {
                                                    if (section.containsKey(tranKey.getKeyName())) {
                                                        translationIniFilesMap.put(file.getPath(), ini);
                                                        return ini;
                                                    }
                                                }
                                            }
                                        } catch (Exception err) {
                                            // 记录异常信息
                                            Timber.e(err, " Exception occurred while parsing ini file:%s", file.getAbsolutePath());
                                            synchronized (errorFiles) {
                                                errorFiles.put(file, err);
                                            }
                                        }
                                        return null;
                                    })
                            .thenAccept(ini -> {
                            });

            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();


        return translationIniFilesMap;
    }

    private static TranslationConfigManager deserialize(File metafile)
            throws IOException {
        try (Reader reader = new FileReader(metafile)) {
            Timber.d("Starting deserialization of file: %s, size: %d bytes", metafile.getAbsolutePath(), metafile.length());

            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(Wini.class, new WiniDeserializer());
            gsonBuilder.registerTypeAdapter(File.class, new FileDeserializer());
            // 为HashMap<String, Wini>注册类型适配器
            Type mapType = new TypeToken<HashMap<String, Wini>>() {
            }.getType();
            gsonBuilder.registerTypeAdapter(mapType, new HashMapWiniDeserializer());
            Gson gson = gsonBuilder.create();
            TranslationConfigManager manager = gson.fromJson(reader, TranslationConfigManager.class);
            manager.rehydrateTranslationIniFilesIfNeeded();

            // 重新初始化transient字段
            if (manager.executorService == null) {
                manager.executorService = Executors.newFixedThreadPool(10);
            }

            return manager;
        }
    }

    /**
     * 提交任务到线程池中执行。
     *
     * @param task 任务
     */
    private Future<?> submitTask(Runnable task) {
        return executorService.submit(task);
    }

    /**
     * 关闭线程池，建议在项目关闭时调用。
     */
    public void shutdown() {
        executorService.shutdown();
    }

    /**
     * 设置INI配置文件中的多语言键值对并保存。
     *
     * @param dir 索引
     * @param map 键值对映射表
     */
    public boolean setPairs(String dir, Map<String, Map<String, String>> map) {
        final Wini ini = translationIniFiles.get(dir);
        if (ini == null) {

            return false; // 如果ini为null，返回false表示未找到对应索引的ini对象
        }
        try {
            submitTask(
                    () -> {
                        try {
                            for (String sectionName : map.keySet()) {
                                Wini.Section section = ini.get(sectionName);
                                if (section != null) {
                                    Map<String, String> keyMap = map.get(sectionName);
                                    section.putAll(keyMap);
                                }
                            }
                            RWIniFileLoader.store(ini);
                        } catch (IOException e) {
                            Timber.e(e);
                            throw new RuntimeException(e); // 抛出运行时异常
                        }
                    })
                    .get(); // 等待任务执行完成

            return true; // 表示任务执行成功
        } catch (InterruptedException | ExecutionException e) {
            Timber.e(e);
            return false; // 返回false表示任务执行失败
        }
    }

    public Map<String, List<SectionModel.Pair>> getTranMap(Wini ini) {
        Map<String, List<SectionModel.Pair>> map = new HashMap<>();

        for (Wini.Section section : ini.values()) {
            List<SectionModel.Pair> pairs = createPairsFromSection(section);
            if (!pairs.isEmpty()) {
                map.put(section.getName(), pairs);
            }
        }
        return map;
    }

  /*
  单独创建一个pair对象
  */

    /**
     * 从Section对象创建Pair集合
     */
    private List<SectionModel.Pair> createPairsFromSection(Wini.Section section) {
        SectionModel.Pair pair = null;
        List<SectionModel.Pair> list = new LinkedList<>();
        for (TranslationKeys key : TranslationKeys.values()) {
            if (section.containsKey(key.getKeyName())) {
                String value = section.get(key.getKeyName());
                // 过滤掉值为内置变量引用的键
                if (!value.startsWith("i:gui")) {
                    pair = new SectionModel.Pair();
                    pair.setKey(key);
                    pair.setOri_val(value);
                    Map<String, String> langPairs = createLangPairs(section, key);
                    if (!langPairs.isEmpty()) {
                        pair.setLang_pairs(langPairs);
                    }
                    list.add(pair);
                }
            }
        }

        return list;
    }

    /*
    为指定节的键根据Lang_Suffix创建多语言映射Map
    */
    private Map<String, String> createLangPairs(Wini.Section section, TranslationKeys key) {
        Map<String, String> langPairs = new HashMap<>();
        for (String suffix : TranslationKeys.LANGUAGE_CODE_TO_ENGLISH_NAME.keySet()) {
            String langKey = key.getKeyName() + '_' + suffix;
            if (section.containsKey(langKey)) {
                langPairs.put(suffix, section.get(langKey));
            }
        }

        return langPairs;
    }

    /**
     * 获取INI配置文件列表。
     *
     * @return INI配置文件列表
     */
    public ArrayList<Wini> getTranslationIniFiles() {
        return new ArrayList<>(this.translationIniFiles.values());
    }

    void rehydrateTranslationIniFilesIfNeeded() {
        if (translationIniFiles == null) {
            translationIniFiles = new HashMap<>();
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> unsafeMap = (Map<String, Object>) (Map<?, ?>) translationIniFiles;
        boolean needsFix = false;
        for (Object value : unsafeMap.values()) {
            if (!(value instanceof Wini)) {
                needsFix = true;
                break;
            }
        }
        if (!needsFix) {
            return;
        }

        Gson rawGson = new Gson();
        Gson winiGson = new GsonBuilder().registerTypeAdapter(Wini.class, new WiniDeserializer()).create();
        HashMap<String, Wini> healed = new HashMap<>();
        for (Map.Entry<String, Object> entry : unsafeMap.entrySet()) {
            String path = entry.getKey();
            Object rawValue = entry.getValue();
            if (rawValue instanceof Wini) {
                healed.put(path, (Wini) rawValue);
                continue;
            }
            boolean restored = false;
            if (rawValue != null) {
                try {
                    JsonElement element = rawGson.toJsonTree(rawValue);
                    Wini wini = winiGson.fromJson(element, Wini.class);
                    if (wini != null) {
                        if (wini.getFile() == null && path != null) {
                            wini.setFile(new File(path));
                        }
                        try {
                            RWIniFileLoader.ensureDocument(wini);
                        } catch (IOException e) {
                            Timber.w(e, "Failed to rebuild INI document for %s", path);
                        }
                        healed.put(path, wini);
                        restored = true;
                    }
                } catch (Exception ex) {
                    Timber.w(ex, "Failed to rehydrate cached INI entry %s", path);
                }
            }
            if (!restored && path != null) {
                reloadIniFromDisk(healed, path);
            }
        }
        translationIniFiles = healed;
    }

    private void reloadIniFromDisk(HashMap<String, Wini> target, String path) {
        try {
            Wini reloaded = RWIniFileLoader.load(new File(path));
            target.put(path, reloaded);
        } catch (IOException loadError) {
            Timber.e(loadError, "Failed to reload INI file %s", path);
        }
    }

    public void serialize() throws IOException {
        try (Writer writer = new FileWriter(projectFile)) {
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(Wini.class, new WiniSerializer());
            gsonBuilder.registerTypeAdapter(File.class, new FileSerializer());
            // 为HashMap<String, Wini>注册类型适配器
            Type mapType = new TypeToken<HashMap<String, Wini>>() {
            }.getType();
            gsonBuilder.registerTypeAdapter(mapType, new HashMapWiniSerializer());
            Gson gson = gsonBuilder.create();
            gson.toJson(this, writer);
        }
    }
}
