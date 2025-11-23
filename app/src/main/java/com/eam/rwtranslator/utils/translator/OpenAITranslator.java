package com.eam.rwtranslator.utils.translator;

import com.eam.rwtranslator.utils.TranslationKeys;
import com.eam.rwtranslator.utils.Translator;
import com.eam.rwtranslator.ui.setting.AppSettings;
import com.google.gson.Gson;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import app.nekogram.translator.Result;
import okhttp3.*;
import timber.log.Timber;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI兼容的翻译器
 * 支持批量翻译和自定义API端点
 */
public class OpenAITranslator extends BaseLLMTranslator {
    private static final Gson gson = new Gson();
    public static final String DEFAULT_API_HOST = "https://api.openai.com";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final  String MODEL_RULE= """
            Rule:
            Only output JSON format text.
            I will send you structured JSON data, like this:
            {
  "texts": [
    {"index": 0, "text": "Hello"},
    {"index": 1, "text": "World"}
  ]
}
            Your task is to complete the keys of the JSON data,which is to translate the value according the specific language code suffix,like this:
            {
  "translations": [
    {"index": 0, "translation": "你好"},
    {"index": 1, "translation": "世界"}
  ]
}
            Note:
            if value key has the ${} or %{},don't translate it's content and change the format inside.
            It is a string template syntax that can be nested with each other.
            """;
    // Token限制常量
    public static final int MAX_TOKEN_LIMIT = 100000; // 最大token限制

    @Override
    public void translate(String query, String fl, String tl, Translator.TranslateCallBack callback) {
        List<String> texts = new ArrayList<>();
        texts.add(query);
        batchTranslate(texts, fl, tl, new BatchTranslateCallback() {
            @Override
            public void onSuccess(List<String> translations, String srcLang, String tgtLang) {
                if (!translations.isEmpty()) {
                    callback.onSuccess(translations.get(0), srcLang, tgtLang);
                } else {
                    callback.onError(new IOException("Empty translation result"));
                }
            }

            @Override
            public void onError(Throwable t) {
                callback.onError(t);
            }
        });
    }

    /**
     * 批量翻译接口（扩展支持进度回调）
     */
    public interface BatchTranslateCallback {
        void onSuccess(List<String> translations, String srcLang, String tgtLang);
        void onError(Throwable t);
        
        /**
         * 进度更新回调（可选实现）
         * @param batchIndex 当前批次索引（从0开始）
         * @param totalBatches 总批次数
         * @param completedTexts 已完成的文本数量
         * @param totalTexts 总文本数量
         */
        default void onProgress(int batchIndex, int totalBatches, int completedTexts, int totalTexts) {
            // 默认空实现，子类可选择性覆盖
        }
    }

    /**
     * 批量翻译多个文本（支持自动分批和进度回调）
     * 多个INI文件的文本会被合并到一个请求中，如果超过token限制则自动分批
     * @param texts 待翻译的文本列表（可来自多个INI文件）
     * @param fl 源语言
     * @param tl 目标语言
     * @param callback 回调函数
     */
    public void batchTranslate(List<String> texts, String fl, String tl, BatchTranslateCallback callback) {
        if (texts.isEmpty()) {
            callback.onSuccess(Collections.emptyList(), fl, tl);
            return;
        }
        
        // 检查是否需要分批处理
        List<List<String>> batches = splitIntoBatches(texts, tl);
        if (batches.size() > 1) {
            Timber.d("Multi-file batch: Splitting %d texts into %d batches due to token limit (%d tokens/batch)", 
                texts.size(), batches.size(), AppSettings.getMaxTokensPerRequest());
            int totalTexts = texts.size();
            List<String> allTranslations = new ArrayList<>();
            processBatch(batches, 0, allTranslations, totalTexts, fl, tl, callback);
            return;
        }
        
        // 单批次处理
        Timber.d("Multi-file batch: Processing %d texts in single batch", texts.size());
        batchTranslateSingle(texts, 0, 1, texts.size(), fl, tl, callback);
    }

    /**
     * 解析批量翻译的JSON结果
     * @param jsonResponse JSON格式的翻译响应
     * @param expectedCount 期望的翻译数量
     * @return 按索引排序的翻译结果列表
     * @throws JsonSyntaxException 如果JSON格式无效
     * @throws IllegalArgumentException 如果翻译数量不匹配
     */
    private List<String> parseBatchTranslations(String jsonResponse, int expectedCount) {
        try {
            TranslationData.TranslationResponse response = gson.fromJson(
                jsonResponse, 
                TranslationData.TranslationResponse.class
            );
            
            if (response == null || response.translations == null) {
                throw new IllegalArgumentException("Invalid JSON response: null response or translations");
            }
            
            // 按索引排序翻译结果
            response.translations.sort(Comparator.comparingInt(a -> a.index));
            
            // 提取翻译文本
            List<String> results = new ArrayList<>();
            for (TranslationData.TranslationItem item : response.translations) {
                if (item.translation != null) {
                    results.add(item.translation);
                } else {
                    results.add(""); // 空翻译
                }
            }
            
            // 验证数量
            if (results.size() != expectedCount) {
                Timber.w("Translation count mismatch: expected %d, got %d. Response: %s", 
                    expectedCount, results.size(), jsonResponse);
            }
            
            return results;
        } catch (JsonSyntaxException e) {
            Timber.e(e, "Failed to parse JSON response: %s", jsonResponse);
            throw new IllegalArgumentException("Invalid JSON format in translation response", e);
        }
    }

    private String getLanguageName(String languageCode) {
        return TranslationKeys.LANGUAGE_CODE_TO_ENGLISH_NAME.getOrDefault(
            languageCode.toLowerCase(), languageCode);
    }

    /**
     * 估算文本的token数量（更精确的估算，基于JSON序列化长度）
     * @param text 文本内容
     * @return 估算的token数量
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            // 使用与请求中相同的JSON结构进行序列化估算，这样能更接近请求真实开销
            TranslationData.TextItem sample = new TranslationData.TextItem(0, text);
            String json = gson.toJson(sample);
            // 根据OpenAI的经验值：平均约4字符/token（保守估计），但JSON中包含大量标点和字段名，取更保守的4字符/token
            int approxTokens = (int) Math.ceil((double) json.length() / 4.0);
            // 加上一些安全余量
            return approxTokens + 2;
        } catch (Exception e) {
            // 退化到字符估算
            return (int) Math.ceil(text.length() / 2.5) + 2;
        }
    }
    
    /**
     * 将文本列表分批，确保每批不超过token限制
     * 基于JSON序列化长度与系统提示词的估算来分批，使得与实际请求更贴近
     * @param texts 待翻译的文本列表
     * @param targetLang 目标语言（用于估算系统提示词的token开销）
     * @return 分批后的文本列表
     */
    private List<List<String>> splitIntoBatches(List<String> texts, String targetLang) {
        List<List<String>> batches = new ArrayList<>();
        List<String> currentBatch = new ArrayList<>();
        int currentTokens = 0;
        
        // 估算系统提示词的token开销（构造与实际一致的近似systemPrompt）
        String systemPromptApprox = AppSettings.getLlmStylePrompt() + "\nTarget language: " + getLanguageName(targetLang) + MODEL_RULE;
        int baseTokens = estimateTokens(systemPromptApprox);
        // 额外预留：response_format、messages头部、model字段等元数据的开销
        int overhead = 100;
        int availableTokens = AppSettings.getMaxTokensPerRequest() - baseTokens - overhead;
        if (availableTokens <= 0) {
            // 保证至少能放下一个短文本
            availableTokens = Math.max(128, AppSettings.getMaxTokensPerRequest() / 4);
        }

        for (String text : texts) {
            // 使用序列化后的TextItem来估算每项的开销（更准确）
            int itemTokens = estimateTokens(text) + 6; // 6 tokens 作为索引/逗号等JSON开销的保守估计

            if (currentTokens + itemTokens > availableTokens && !currentBatch.isEmpty()) {
                // 当前批次已满，开始新批次
                batches.add(new ArrayList<>(currentBatch));
                currentBatch.clear();
                currentTokens = 0;
            }
            
            currentBatch.add(text);
            currentTokens += itemTokens;
        }
        
        // 添加最后一批
        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }
        
        return batches;
    }

    /**
     * 递归处理每一批翻译（带进度报告）
     * @param batches 分批后的文本列表
     * @param totalTexts 总文本数量
     * @param fl 源语言
     * @param tl 目标语言
     * @param callback 回调函数
     */
    private void processBatch(List<List<String>> batches, int batchIndex, 
                             List<String> allTranslations, int totalTexts,
                             String fl, String tl, 
                             BatchTranslateCallback callback) {
        if (batchIndex >= batches.size()) {
            // 所有批次处理完成
            callback.onSuccess(allTranslations, fl, tl);
            return;
        }
        
        List<String> currentBatch = batches.get(batchIndex);
        int completedTexts = allTranslations.size();
        
        Timber.d("Processing batch %d/%d with %d texts (completed: %d/%d)", 
            batchIndex + 1, batches.size(), currentBatch.size(), completedTexts, totalTexts);
        
        // 报告进度
        callback.onProgress(batchIndex, batches.size(), completedTexts, totalTexts);
        
        // 调用单批次翻译（不进行分批检查）
        batchTranslateSingle(currentBatch, batchIndex, batches.size(), totalTexts, fl, tl, new BatchTranslateCallback() {
            @Override
            public void onSuccess(List<String> translations, String srcLang, String tgtLang) {
                allTranslations.addAll(translations);
                // 继续处理下一批
                processBatch(batches, batchIndex + 1, allTranslations, totalTexts, fl, tl, callback);
            }

            @Override
            public void onError(Throwable t) {
                Timber.e(t, "Batch %d/%d failed", batchIndex + 1, batches.size());
                callback.onError(t);
            }
        });
    }
    
    /**
     * 单批次翻译（内部方法，不进行分批检查）
     * @param batchIndex 当前批次索引
     * @param totalBatches 总批次数
     * @param totalTexts 总文本数量（用于日志）
     */
    private void batchTranslateSingle(List<String> texts, int batchIndex, int totalBatches, int totalTexts, 
                                     String fl, String tl, BatchTranslateCallback callback) {
        if (texts.isEmpty()) {
            callback.onSuccess(Collections.emptyList(), fl, tl);
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        // 确保API host不以斜杠结尾
        String baseUrl = AppSettings.getApiHost().endsWith("/") ? AppSettings.getApiHost().substring(0, AppSettings.getApiHost().length() - 1) : AppSettings.getApiHost();
        String url = baseUrl + "/v1/chat/completions";

        String systemPrompt =AppSettings.getLlmStylePrompt()+ "\nTarget language: " + getLanguageName(tl) + MODEL_RULE ;

        // 构建JSON格式的翻译请求
        List<TranslationData.TextItem> textItems = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            textItems.add(new TranslationData.TextItem(i, texts.get(i)));
        }
        TranslationData.TranslationRequest translationRequest = new TranslationData.TranslationRequest(textItems);
        String userMessage = gson.toJson(translationRequest);

        // 构建OpenAI API请求
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", AppSettings.getLlmModelName());
        JsonObject formatting = new JsonObject();
        formatting.addProperty("type", "json_object");
        requestBody.add("response_format", formatting);
        JsonArray messages = new JsonArray();
        // 系统消息
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);
        
        // 用户消息
        JsonObject userMessageObj = new JsonObject();
        userMessageObj.addProperty("role", "user");
        userMessageObj.addProperty("content", userMessage);
        messages.add(userMessageObj);
        
        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.3);

        String requestBodyStr = gson.toJson(requestBody);
        Timber.d("OpenAI Request: %s", requestBodyStr);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + AppSettings.getApiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(requestBodyStr, MediaType.get("application/json")))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Timber.e("OpenAI request failed: %s", e.getMessage());
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorBody = response.body().string();
                    Timber.e("OpenAI API error: %s", errorBody);
                    callback.onError(new IOException("API request failed: " + response.code() + " - " + errorBody));
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    Timber.d("OpenAI response: %s", responseBody);
                    
                    JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                    String translatedJson = json.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();

                    Timber.d("Translated JSON content: %s", translatedJson);
                    // 解析JSON格式的批量翻译结果
                    List<String> translations = parseBatchTranslations(translatedJson, texts.size());

                    // 如果数量不匹配，尝试补齐或截断
                    if (translations.size() < texts.size()) {
                        Timber.w("Padding translations from %d to %d", translations.size(), texts.size());
                        while (translations.size() < texts.size()) {
                            translations.add("");
                        }
                    } else if (translations.size() > texts.size()) {
                        Timber.w("Truncating translations from %d to %d", translations.size(), texts.size());
                        translations = translations.subList(0, texts.size());
                    }
                    
                    callback.onSuccess(translations, fl, tl);
                } catch (JsonSyntaxException e) {
                    Timber.e(e, "Invalid JSON format in translation response");
                    callback.onError(new IOException("Invalid JSON format in translation response: " + e.getMessage(), e));
                } catch (IllegalArgumentException e) {
                    Timber.e(e, "Translation data validation failed");
                    callback.onError(new IOException("Translation data validation failed: " + e.getMessage(), e));
                } catch (Exception e) {
                    Timber.e(e, "Failed to parse OpenAI response");
                    callback.onError(new IOException("Failed to parse response: " + e.getMessage(), e));
                }
            }
        });
    }

    /**
     * 获取可用的模型列表
     */
    public void fetchAvailableModels(ModelsCallback callback) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        String baseUrl = AppSettings.getApiHost().endsWith("/") ? AppSettings.getApiHost().substring(0, AppSettings.getApiHost().length() - 1) : AppSettings.getApiHost();
        String url = baseUrl + "/v1/models";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + AppSettings.getApiKey())
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Timber.e("Failed to fetch models: %s", e.getMessage());
                callback.onError(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Failed to fetch models: " + response.code()));
                    return;
                }

                try {
                    String responseBody = response.body().string();
                    JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                    JsonArray data = json.getAsJsonArray("data");
                    
                    List<String> models = new ArrayList<>();
                    for (int i = 0; i < data.size(); i++) {
                        JsonObject model = data.get(i).getAsJsonObject();
                        String modelId = model.get("id").getAsString();
                        models.add(modelId);
                    }
                    
                    callback.onSuccess(models);
                } catch (Exception e) {
                    Timber.e(e, "Failed to parse models response");
                    callback.onError(e);
                }
            }
        });
    }

    public interface ModelsCallback {
        void onSuccess(List<String> models);
        void onError(Throwable t);
    }

    @Override
    protected Result a(String s, String s1, String s2) {
        return null;
    }

    @Override
    public List<String> getTargetLanguages() {
        return Collections.emptyList();
    }
}
