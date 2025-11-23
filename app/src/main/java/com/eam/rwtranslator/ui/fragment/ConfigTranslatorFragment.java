package com.eam.rwtranslator.ui.fragment;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.eam.rwtranslator.R;
import com.eam.rwtranslator.ui.setting.AppSettings;
import com.eam.rwtranslator.utils.TranslationKeys;
import com.eam.rwtranslator.utils.Translator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

public class ConfigTranslatorFragment extends DialogFragment {
    private final Context context;
    private ViewGroup mContainer;
    private Spinner sp1;
    private AutoCompleteTextView targetLanguageAutoComplete;
    /*
     *  index  ->   data
     *  0-> 翻译引擎的spinner的索引值
     *  1-> 目标语言的spinner的索引值
     * */
    private final int[] selectedIndex = new int[1];
    private final Translator.TranslateTaskCallBack callback;

    public ConfigTranslatorFragment(Context context, Translator.TranslateTaskCallBack callback) {
        this.context = context;
        this.callback = callback;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppSettings.init(context);
    }

    @Override
    @MainThread
    @Nullable
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContainer = container;
        return null;
    }

    @Override
    @MainThread
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view =
                getLayoutInflater().inflate(R.layout.config_translator_content, mContainer, false);
        sp1 = view.findViewById(R.id.selscttranebgine_spinner1);
        targetLanguageAutoComplete = view.findViewById(R.id.target_language_autocomplete);
        setupEngines();
        setupLanguageAutoComplete();
        // 设置Spinner默认选项
        sp1.setSelection(selectedIndex[0]);

        // 设置AutoCompleteTextView默认值
        String savedLanguageCode = AppSettings.getCurrentTargetLanguageCode();
        String savedLanguage = TranslationKeys.LANGUAGE_CODE_TO_ENGLISH_NAME.get(savedLanguageCode);
        if (savedLanguage != null) {
            targetLanguageAutoComplete.setText(savedLanguage);
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(R.string.project_act_config_translator_dialog_title);
        builder.setView(view);
        builder.setPositiveButton(
                R.string.positive_button,
                (dialog, which) -> {
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
                    AppSettings.setCurrentFromLanguageCode("auto");
                    AppSettings.setCurrentTargetLanguageCode(languageCode);
                    AppSettings.translationProvider = Translator.getTranslatorCodeByIndex(sp1.getSelectedItemPosition());
                    AppSettings.apply();
                    callback.onTranslate(false);
                });
        builder.setNegativeButton(R.string.negative_button, null);

        return builder.create();
    }

    private void setupEngines() {
        String[] stringArray = context.getResources().getStringArray(R.array.select_engine);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item, stringArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp1.setAdapter(adapter);
        sp1.setSelection(IntStream.range(0, stringArray.length).filter(i->AppSettings.translationProvider.equals(stringArray[i])).findFirst().orElse(0));
    }

    private void setupLanguageAutoComplete() {
        // 设置AutoCompleteTextView的适配器

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<String>(TranslationKeys.LANGUAGE_CODE_TO_ENGLISH_NAME.values())
        );

        targetLanguageAutoComplete.setAdapter(adapter);
        targetLanguageAutoComplete.setThreshold(1);
    }

}
