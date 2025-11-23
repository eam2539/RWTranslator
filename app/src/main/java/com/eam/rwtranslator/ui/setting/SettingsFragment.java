package com.eam.rwtranslator.ui.setting;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.*;

import com.eam.rwtranslator.AppConfig;
import com.eam.rwtranslator.R;
import com.eam.rwtranslator.utils.UrlUtils;
import com.eam.rwtranslator.utils.translator.OpenAITranslator;
import com.takisoft.preferencex.PreferenceFragmentCompat;
import com.takisoft.preferencex.SwitchPreferenceCompat;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import scala.App;
import timber.log.Timber;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final int REQUEST_CODE_CUSTOM_EXPORT_PATH = 1002;
    private Boolean isSuccessfullyRefreshingModels = false;
    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        PreferenceCategory projectCategory = findPreference("project");
        PreferenceCategory developCategory = findPreference("development");
        PreferenceCategory LLMCategory = findPreference("llm_settings");
        SwitchPreferenceCompat pre = projectCategory.findPreference("is_override");
        // 初始化 is_override 的显示和监听
        if (pre != null) {
            pre.setChecked(AppSettings.getIsOverride());
            pre.setOnPreferenceChangeListener((prefer, val) -> {
                AppSettings.setIsOverride((boolean) val);
                return true;
            });
        }
        // 初始化 custom_export_path 的显示
        Preference customExportPref = projectCategory.findPreference("custom_export_path");
        if (customExportPref != null) {
            String current = AppSettings.getCustomExportPath();
            customExportPref.setSummary(current);
        }
        projectCategory.findPreference("defaulter_export_path")
                .setOnPreferenceClickListener(p2 -> {
                    var defaultPath = getString(R.string.setting_act_export_path_default_value);
                    AppSettings.setCustomExportPath(defaultPath);
                    showMsg(getString(R.string.setting_act_export_path_set_success, defaultPath));
                    p2.getParent().findPreference("custom_export_path").setSummary(defaultPath);
                    return true;
                });

        projectCategory
                .findPreference("clean")
                .setOnPreferenceClickListener(
                        p1 -> {
                            for (File file : AppConfig.externalCacheSerialDir.listFiles()) file.delete();
                            showMsg(getString(R.string.setting_act_clear_message));
                            return true;
                        });

        // 设置自定义导出路径点击处理
        projectCategory.findPreference("custom_export_path")
                .setOnPreferenceClickListener(
                        p1 -> {
                            openDirectoryPicker();
                            return true;
                        });


        // LLM设置
        EditTextPreference apiHostPref = LLMCategory.findPreference("llm_api_host");
        EditTextPreference apiKeyPref = LLMCategory.findPreference("llm_api_key");
        EditTextPreference maxTokensPref = LLMCategory.findPreference("llm_max_tokens");
        ListPreference modelPref = LLMCategory.findPreference("llm_model");
        Preference refreshModelsPref = LLMCategory.findPreference("llm_refresh_models");
        EditTextPreference customizeModelPref = LLMCategory.findPreference("custom_llm_model_id");
        // 设置API Host改变监听
        if (apiHostPref != null) {
            apiHostPref.setDialogMessage(getString(R.string.setting_fragment_llm_api_host_dialog_message));
            apiHostPref.setText(AppSettings.getApiHost());
            apiHostPref.setOnPreferenceChangeListener((preference, newValue) -> {
                AppSettings.setApiHost(newValue.toString());
                return true;
            });
        }

        // 设置API Key改变监听
        if (apiKeyPref != null) {
            apiKeyPref.setText(AppSettings.getApiKey());
            apiKeyPref.setOnPreferenceChangeListener((preference, newValue) -> {
                AppSettings.setApiKey(newValue.toString());
                return true;
            });
        }

        // 设置Max Tokens改变监听
        if (maxTokensPref != null) {
            maxTokensPref.setText(String.valueOf(AppSettings.getMaxTokensPerRequest()));
            maxTokensPref.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int tokens = Integer.parseInt(newValue.toString());
                    if (tokens > 0 && tokens <= OpenAITranslator.MAX_TOKEN_LIMIT) {
                        AppSettings.setMaxTokensPerRequest(tokens);
                        return true;
                    } else {
                        showMsg("Token limit must be between 1 and " + OpenAITranslator.MAX_TOKEN_LIMIT);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    showMsg("Invalid number format");
                    Timber.e(e);
                    return false;
                }
            });
        }

        // 设置Model改变监听
        String modelName = AppSettings.getLlmModelName();
        if (modelPref != null) {
            modelPref.setValue(modelName);
            modelPref.setOnPreferenceChangeListener((preference, newValue) -> {
                AppSettings.setLlmModelName(newValue.toString());
                customizeModelPref.setSummary(getString(R.string.setting_act_llm_customize_model_summary,newValue.toString()));
                return true;
            });
        }

        // 刷新模型列表
        if (refreshModelsPref != null) {
            refreshModelsPref.setOnPreferenceClickListener(p -> {
                refreshModels();
                return true;
            });
        }

        if (customizeModelPref != null) {
            customizeModelPref.setText(modelName);
            customizeModelPref.setSummary(getString(R.string.setting_act_llm_customize_model_summary,modelName));
            customizeModelPref.setOnPreferenceChangeListener((preference, newValue) -> {
                AppSettings.setLlmModelName(newValue.toString());
                preference.setSummary(getString(R.string.setting_act_llm_customize_model_summary,newValue.toString()));
                return true;
            });
        }
        developCategory
                .findPreference("joinGroup")
                .setOnPreferenceClickListener(
                        p1 -> {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.setData(Uri.parse("https://discord.gg/ubxRGXBcSd"));
                            try {
                                startActivity(intent);
                            } catch (Exception e) {
                                showMsg(getString(R.string.setting_act_open_url_failed_message));
                                Timber.e(e);
                            }
                            return true;
                        });
        developCategory
                .findPreference("about_development")
                .setOnPreferenceClickListener(
                        p1 -> {
                            UrlUtils.openGitHubRepo(getContext());
                            return true;
                        });
    }

    @Override
    public void onDisplayPreferenceDialog(@NotNull Preference preference) {
        // 拦截 llm_model 的对话框，当未成功刷新模型时阻止显示对话框
        if ("llm_model".equals(preference.getKey()) && !isSuccessfullyRefreshingModels) {
            showMsg("Please refresh the model list successfully first.");
            return; // 不调用 super，阻止默认的对话框显示
        }
        super.onDisplayPreferenceDialog(preference);
    }
    private void refreshModels() {
        showMsg("Fetching models...");
        OpenAITranslator translator = new OpenAITranslator();
        translator.fetchAvailableModels(new OpenAITranslator.ModelsCallback() {
            @Override
            public void onSuccess(List<String> models) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        ListPreference modelPref = findPreference("llm_model");
                        if (modelPref != null && !models.isEmpty()) {
                            isSuccessfullyRefreshingModels = true;
                            CharSequence[] entries = models.toArray(new CharSequence[0]);
                            CharSequence[] entryValues = models.toArray(new CharSequence[0]);
                            modelPref.setEntries(entries);
                            modelPref.setEntryValues(entryValues);
                            showMsg(getString(R.string.setting_act_llm_models_refreshed));
                        }
                    });
                }
            }

            @Override
            public void onError(Throwable t) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        isSuccessfullyRefreshingModels = false;
                        Timber.e(t);
                        showMsg(getString(R.string.setting_act_llm_refresh_failed, t.getMessage()));
                    });
                }
            }
        });
    }

    private void showMsg(String message) {
        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
    }

    private void openDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_CUSTOM_EXPORT_PATH);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CUSTOM_EXPORT_PATH && resultCode == Activity.RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                // 获得持久化权限
                getActivity().getContentResolver().takePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                // 将URI转换为路径并保存到SharedPreferences
                String selectedPath = treeUri.getPath();
                if (selectedPath != null) {
                    // 处理URI路径格式
                    if (selectedPath.startsWith("/tree/primary:")) {
                        selectedPath = selectedPath.replace("/tree/primary:", "/storage/emulated/0/");
                    } else if (selectedPath.startsWith("/tree/")) {
                        selectedPath = selectedPath.substring(6); // 移除"/tree/"前缀
                    }

                    // 保存到SharedPreferences
                    AppSettings.setCustomExportPath(selectedPath);

                    // 更新preference的summary显示
                    Preference customExportPathPref = findPreference("custom_export_path");
                    if (customExportPathPref != null) {
                        customExportPathPref.setSummary(selectedPath);
                    }

                    showMsg(getString(R.string.setting_act_export_path_set_success, selectedPath));
                }
            }
        }
    }
}
