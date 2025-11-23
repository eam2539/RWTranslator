package com.eam.rwtranslator.utils;

import androidx.annotation.NonNull;
import androidx.collection.LruCache;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;

import app.nekogram.translator.BaiduTranslator;
import app.nekogram.translator.BaseTranslator;
import app.nekogram.translator.DeepLTranslator;
import app.nekogram.translator.MicrosoftTranslator;
import app.nekogram.translator.Result;
import app.nekogram.translator.SogouTranslator;
import app.nekogram.translator.TranSmartTranslator;
import app.nekogram.translator.GoogleAppTranslator;
import app.nekogram.translator.YandexTranslator;

import com.eam.rwtranslator.AppConfig;
import com.eam.rwtranslator.ui.setting.AppSettings;
import com.eam.rwtranslator.utils.translator.BaseLLMTranslator;
import com.eam.rwtranslator.utils.translator.OpenAITranslator;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public class Translator {
    public interface TranslateTaskCallBack {
        void onTranslate(boolean enable_llm);
    }

    public interface TranslateCallBack {
        void onSuccess(String translation, String sourceLanguage, String targetLanguage);

        void onError(Throwable t);
    }

    public interface BatchTranslateCallBack {
        void onSuccess(List<String> translations, String sourceLanguage, String targetLanguage);

        void onError(Throwable t);
        
        /**
         * 进度更新回调（可选实现，用于多批次翻译时的进度报告）
         * @param batchIndex 当前批次索引（从0开始）
         * @param totalBatches 总批次数
         * @param completedTexts 已完成的文本数量
         * @param totalTexts 总文本数量
         */
        default void onProgress(int batchIndex, int totalBatches, int completedTexts, int totalTexts) {
            // 默认空实现，子类可选择性覆盖以显示进度
        }
    }

    public static final String PROVIDER_GOOGLE = "google";
    public static final String PROVIDER_MICROSOFT = "microsoft";
    public static final String PROVIDER_YANDEX = "yandex";
    public static final String PROVIDER_DEEPL = "deepl";
    public static final String PROVIDER_BAIDU = "baidu";
    public static final String PROVIDER_SOGOU = "sogou";
    public static final String PROVIDER_TENCENT = "tencent";
    //LLM 翻译器标签
    public static final String PROVIDER_OPENAI = "openai";

    private static final ListeningExecutorService executorService =
            MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    private static final LruCache<Pair<String, String>, Pair<String, String>> cache =
            new LruCache<>(200);

    public static ListeningExecutorService getExecutorService() {
        return executorService;
    }

    public static BaseTranslator getCurrentTranslator() {
        return getTranslator(AppSettings.translationProvider);
    }

    public static BaseTranslator getTranslator(String type) {
        return switch (type) {
            case PROVIDER_DEEPL -> {
                DeepLTranslator.setFormality(AppSettings.deepLFormality);
                yield DeepLTranslator.getInstance();
            }
            case PROVIDER_MICROSOFT -> MicrosoftTranslator.getInstance();
            case PROVIDER_BAIDU -> BaiduTranslator.getInstance();
            case PROVIDER_SOGOU -> SogouTranslator.getInstance();
            case PROVIDER_TENCENT -> TranSmartTranslator.getInstance();
            case PROVIDER_YANDEX -> YandexTranslator.getInstance();
            case PROVIDER_OPENAI -> new OpenAITranslator();
            default -> GoogleAppTranslator.getInstance();
        };
    }

    public static String getTranslatorCodeByIndex(int index) {
        return switch (index) {
            case 0 -> PROVIDER_TENCENT;
            case 1 -> PROVIDER_BAIDU;
            case 2 -> PROVIDER_SOGOU;
            case 3 -> PROVIDER_GOOGLE;
            case 4 -> PROVIDER_MICROSOFT;
            case 5 -> PROVIDER_DEEPL;
            case 6 -> PROVIDER_YANDEX;
            default -> PROVIDER_SOGOU; // 默认返回有道翻译
        };
    }

    public static void translate(String query, TranslateCallBack translateCallBack) {
        translate(query, AppSettings.getCurrentFromLanguageCode(), AppSettings.getCurrentTargetLanguageCode(), translateCallBack);
    }

    public static void translate(
            String query, String fl, String tl, TranslateCallBack translateCallBack) {
        BaseTranslator translator = getCurrentTranslator();
        String language;
        language = tl == null ? AppSettings.getCurrentTargetLanguageCode() : tl;

        if (!translator.supportLanguage(language)) {
            translateCallBack.onError(new UnsupportedTargetLanguageException(language));
        } else {
            startTask(translator, query, fl, language, translateCallBack);
        }
    }

    public static void LLM_translate(String query, String fl, String tl, TranslateCallBack translateCallBack) {
        BaseLLMTranslator llmTranslator = (BaseLLMTranslator) getCurrentTranslator();
        llmTranslator.translate(query, fl, tl, translateCallBack);
    }

    /**
     * 批量LLM翻译，将多个文本合并为一个请求
     * @param queries 待翻译的文本列表
     * @param fl 源语言
     * @param tl 目标语言
     * @param batchCallBack 批量翻译回调
     */
    public static void LLM_batchTranslate(List<String> queries, String fl, String tl, BatchTranslateCallBack batchCallBack) {
        BaseTranslator translator = getCurrentTranslator();
        
        // 只有OpenAI翻译器支持真正的批量翻译
        OpenAITranslator openAITranslator = (OpenAITranslator) translator;
        openAITranslator.batchTranslate(queries, fl, tl, new OpenAITranslator.BatchTranslateCallback() {
            @Override
            public void onSuccess(List<String> translations, String srcLang, String tgtLang) {
                batchCallBack.onSuccess(translations, srcLang, tgtLang);
            }

            @Override
            public void onError(Throwable t) {
                batchCallBack.onError(t);
            }

            @Override
            public void onProgress(int batchIndex, int totalBatches, int completedTexts, int totalTexts) {
                // 传递进度回调
                batchCallBack.onProgress(batchIndex, totalBatches, completedTexts, totalTexts);
            }
        });
    }

    private static class UnsupportedTargetLanguageException extends IllegalArgumentException {
        public UnsupportedTargetLanguageException(String targetLanguage) {
            super("Unsupported Target Language: " + targetLanguage);
        }
    }

    private static void startTask(
            BaseTranslator translator,
            String query,
            String fromLang,
            String toLang,
            TranslateCallBack translateCallBack) {
        var result = cache.get(Pair.create(query, toLang + "|" + AppSettings.translationProvider));
        if (result != null) {
            translateCallBack.onSuccess(
                    result.first,
                    result.second == null ? fromLang : translator.convertLanguageCode(result.second, ""),
                    toLang);
        } else {
            TranslateTask translateTask =
                    new TranslateTask(translator, query, fromLang, toLang, translateCallBack);
            ListenableFuture<Pair<String, String>> future = getExecutorService().submit(translateTask);
            Futures.addCallback(
                    future, translateTask, ContextCompat.getMainExecutor(AppConfig.applicationContext));
        }
    }

    private record TranslateTask(BaseTranslator translator, String query, String fl, String tl,
                                 TranslateCallBack translateCallBack)
            implements Callable<Pair<String, String>>, FutureCallback<Pair<String, String>> {

        @Override
        public Pair<String, String> call() {
            String from =translator.convertLanguageCode(fl, "");
            // 为不同的翻译器设置正确的自动检测语言代码
            if (translator instanceof MicrosoftTranslator || translator instanceof YandexTranslator) {
               from=null;
            }
            var to = translator.convertLanguageCode(tl, "");
            Result result = translator.translate(query, from, to);
            return Pair.create(result.translation, result.sourceLanguage);
        }

        @Override
        public void onSuccess(Pair<String, String> result) {
            translateCallBack.onSuccess(
                    result.first,
                    result.second == null ? fl : translator.convertLanguageCode(result.second, ""),
                    tl);
            cache.put(Pair.create(query, tl + "|" + AppSettings.translationProvider), result);
        }

        @Override
        public void onFailure(@NonNull Throwable t) {
            translateCallBack.onError(t);
        }
    }

}
