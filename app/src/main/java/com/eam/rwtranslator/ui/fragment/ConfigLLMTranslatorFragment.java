package com.eam.rwtranslator.ui.fragment;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.eam.rwtranslator.R;
import com.eam.rwtranslator.ui.setting.AppSettings;
import com.eam.rwtranslator.utils.TranslationKeys;
import com.eam.rwtranslator.utils.Translator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class ConfigLLMTranslatorFragment extends DialogFragment {
    private final Context context;
    private ViewGroup mContainer;
    private Spinner spinner;
    private AutoCompleteTextView targetLanguageAutoComplete;
    private TextInputEditText systemPromptEditText;
    private final Translator.TranslateTaskCallBack callback;

    public ConfigLLMTranslatorFragment(Context context, Translator.TranslateTaskCallBack callback) {
        this.context = context;
        this.callback = callback;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    @MainThread
    @Nullable
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContainer = container;
        return null;
    }

    @SuppressLint("MissingInflatedId")
    @Override
    @MainThread
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view =
                getLayoutInflater().inflate(R.layout.config_llm_translator_content, mContainer, false);
        spinner = view.findViewById(R.id.llm_model_spinner);
        targetLanguageAutoComplete = view.findViewById(R.id.target_language_autocomplete);
        systemPromptEditText = view.findViewById(R.id.system_prompt_edit_text);

        setupModelSpinner();
        setupLanguageAutoComplete();

        // 初始化选择状态
        spinner.setSelection(0);

        // 从SharedPreferences加载语言代码并设置到AutoCompleteTextView
        String languageInfo = TranslationKeys.LANGUAGE_CODE_TO_ENGLISH_NAME.get(AppSettings.getCurrentTargetLanguageCode());
        if (languageInfo != null) {
            targetLanguageAutoComplete.setText(languageInfo, false);
        }

        // 加载系统提示词
        systemPromptEditText.setText(AppSettings.getLlmStylePrompt());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(R.string.project_act_config_translator_dialog_title);
        builder.setView(view);
        builder.setPositiveButton(
                R.string.positive_button,
                (dialog, which) -> {
                    //检测系统提示词格式
                    var text = systemPromptEditText.getText();
                    dialog.dismiss();
                    // 从AutoCompleteTextView获取语言代码
                    String selectedText = targetLanguageAutoComplete.getText().toString();
                    String languageCode = TranslationKeys.ENGLISH_NAME_TO_LANGUAGE_CODE.get(selectedText);

                    if (languageCode == null || languageCode.isEmpty()) {
                        // 如果没有选择有效的语言代码，尝试直接使用输入的文本作为语言代码
                        languageCode = selectedText.trim().toLowerCase();
                        if (TranslationKeys.LANGUAGE_CODE_TO_ENGLISH_NAME.containsKey(languageCode)) {
                            languageCode = "en"; // 默认使用英语
                        }
                    }

                    // 设置语言配置，from language使用auto
                    AppSettings.setCurrentFromLanguageCode("auto");
                    AppSettings.setCurrentTargetLanguageCode(languageCode);
                    AppSettings.translationProvider = Translator.PROVIDER_OPENAI;
                    AppSettings.setLlmStylePrompt(String.valueOf(systemPromptEditText.getText()));
                    AppSettings.apply();
                    callback.onTranslate(true);
                });
        builder.setNegativeButton(R.string.negative_button, null);

        Dialog dialog = builder.create();

        // 设置软键盘模式，防止EditText被输入法遮挡
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        return dialog;
    }

    private void setupModelSpinner() {
        // 设置LLM模型列表
        List<String> modelNames = List.of(
                "OpenAI Compatible"
        );
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item, modelNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setEnabled(false); // 目前仅支持OpenAI Compatible模型，禁用选择
    }

    private void setupLanguageAutoComplete() {
        // 设置AutoCompleteTextView的适配器
        List<String> languageDisplayList = new ArrayList<>(TranslationKeys.LANGUAGE_CODE_TO_ENGLISH_NAME.values());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_dropdown_item_1line,
                languageDisplayList
        );

        targetLanguageAutoComplete.setAdapter(adapter);
        targetLanguageAutoComplete.setThreshold(1);
    }
}