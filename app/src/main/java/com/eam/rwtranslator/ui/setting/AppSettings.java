package com.eam.rwtranslator.ui.setting;

import static com.eam.rwtranslator.utils.translator.OpenAITranslator.MAX_TOKEN_LIMIT;

import android.content.Context;
import android.content.SharedPreferences;

import com.eam.rwtranslator.R;
import com.eam.rwtranslator.utils.Translator;
import com.eam.rwtranslator.utils.translator.OpenAITranslator;

import app.nekogram.translator.DeepLTranslator;

public class AppSettings {
    private static final String PREF_NAME = "AppSettings";


    // 翻译服务提供者
    public static String translationProvider = Translator.PROVIDER_SOGOU;
    // DeepL翻译正式/非正式风格
    public static int deepLFormality = DeepLTranslator.FORMALITY_DEFAULT;
    private static String currentFromLanguageCode = "en";
    private static String currentTargetLanguageCode = "zh";
    // LLM翻译默认系统提示词
    public static String DefaultLLMStylePrompt = """
            Style:
            You are a professional translation assistant.
  """;


    private static Boolean isOverride = false;
    private static String apiHost = OpenAITranslator.DEFAULT_API_HOST;

    private static int maxTokensPerRequest = 8000;
    private static String apiKey = "";

    private static String customExportPath;
    private static String llmModelName = "";
    private static String llmStylePrompt = DefaultLLMStylePrompt;
    private static SharedPreferences preferences;
    private static final String KEY_TRANSLATOR_PROVIDER = "translator_provider_key";
    public static final String KEY_DEEPL_FORMALITY = "deepl_formality_key";
    private static final String KEY_CURRENT_FROM_LANGUAGE = "current_from_language_key";
    private static final String KEY_CURRENT_TARGET_LANGUAGE = "current_target_language_key";
    private static final String KEY_LLM_STYLE_PROMPT = "llm_style_prompt_key";
    //设置选项
    private static final String KEY_IS_OVERRIDE = "is_override_key";
    private static final String KEY_CUSTOM_EXPORT_PATH = "custom_export_path_key";
    private static final String KEY_API_HOST = "api_host_key";
    private static  final String KEY_API_KEY = "api_key_key";
    private static final String KEY_MAX_TOKENS_PER_REQUEST = "max_tokens_per_request_key";
    private static final String KEY_LLM_MODEL_INDEX = "llm_model_key";




    public static void init(Context context) {
        customExportPath = context.getString(R.string.setting_act_export_path_default_value);
        if (preferences == null) {
            preferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            translationProvider = preferences.getString(KEY_TRANSLATOR_PROVIDER, translationProvider);
            deepLFormality = preferences.getInt(KEY_DEEPL_FORMALITY, deepLFormality);
            currentFromLanguageCode = preferences.getString(KEY_CURRENT_FROM_LANGUAGE, currentFromLanguageCode);
            currentTargetLanguageCode = preferences.getString(KEY_CURRENT_TARGET_LANGUAGE, currentTargetLanguageCode);
            isOverride = preferences.getBoolean(KEY_IS_OVERRIDE, isOverride);
            customExportPath = preferences.getString(KEY_CUSTOM_EXPORT_PATH, customExportPath);
            apiHost = preferences.getString(KEY_API_HOST, apiHost);
            apiKey = preferences.getString(KEY_API_KEY, apiKey);
            maxTokensPerRequest = preferences.getInt(KEY_MAX_TOKENS_PER_REQUEST, maxTokensPerRequest);
            llmModelName = preferences.getString(KEY_LLM_MODEL_INDEX, llmModelName);
            llmStylePrompt = preferences.getString(KEY_LLM_STYLE_PROMPT, llmStylePrompt);
        }
    }

    public static String getCurrentFromLanguageCode() {
        return currentFromLanguageCode;
    }

    public static void setCurrentFromLanguageCode(String lang) {
        currentFromLanguageCode = lang;
    }

    public static String getCurrentTargetLanguageCode() {
        return currentTargetLanguageCode;
    }

    public static void setCurrentTargetLanguageCode(String lang) {
        currentTargetLanguageCode = lang;
    }
    public static Boolean getIsOverride() {
        return isOverride;
    }

    public static void setIsOverride(Boolean isOverride) {
        AppSettings.isOverride = isOverride;
    }
    public static String getCustomExportPath() {
        return customExportPath;
    }

    public static void setCustomExportPath(String customExportPath) {
        AppSettings.customExportPath = customExportPath;
    }
    public static String getApiHost() {
        return apiHost;
    }

    public static void setApiHost(String apiHost) {
        AppSettings.apiHost = apiHost;
    }
    public static String getApiKey() {
        return apiKey;
    }

    public static void setApiKey(String apiKey) {
        AppSettings.apiKey = apiKey;
    }
    public static int getMaxTokensPerRequest() {
        return maxTokensPerRequest;
    }

    public static void setMaxTokensPerRequest(int tokensLimit) {
        if (tokensLimit > 0 && tokensLimit <= MAX_TOKEN_LIMIT) {
            maxTokensPerRequest = tokensLimit;
        }
    }
    public static String getLlmModelName() {
        return llmModelName;
    }

    public static void setLlmModelName(String marker) {
        llmModelName = marker;
    }
    public static String getLlmStylePrompt() {
        return llmStylePrompt;
    }

    public static void setLlmStylePrompt(String prompt) {
        llmStylePrompt = prompt;
    }

    public static void apply() {
        preferences.edit()
                .putString(KEY_TRANSLATOR_PROVIDER, translationProvider)
                .putInt(KEY_DEEPL_FORMALITY, deepLFormality)
                .putString(KEY_CURRENT_FROM_LANGUAGE, currentFromLanguageCode)
                .putString(KEY_CURRENT_TARGET_LANGUAGE, currentTargetLanguageCode)
                .putBoolean(KEY_IS_OVERRIDE, isOverride)
                .putString(KEY_CUSTOM_EXPORT_PATH, customExportPath)
                .putString(KEY_API_HOST, apiHost)
                .putString(KEY_API_KEY, apiKey)
                .putInt(KEY_MAX_TOKENS_PER_REQUEST, maxTokensPerRequest)
                .putString(KEY_LLM_MODEL_INDEX, llmModelName)
                .putString(KEY_LLM_STYLE_PROMPT, llmStylePrompt)
                .apply();
    }

}
