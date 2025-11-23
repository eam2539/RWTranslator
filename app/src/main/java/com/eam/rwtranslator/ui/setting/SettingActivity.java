package com.eam.rwtranslator.ui.setting;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.eam.rwtranslator.R;
import com.eam.rwtranslator.databinding.ActivitySettingsBinding;

public class SettingActivity extends AppCompatActivity {
    ActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle arg0) {
        super.onCreate(arg0);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager
                .beginTransaction()
                .replace(R.id.setting_framelayout, new SettingsFragment())
                .commit();
        binding.settingActToolbar.setNavigationOnClickListener(v ->
                {
                    AppSettings.apply();
                    finish();
                }
        );
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                AppSettings.apply();
                finish();
            }
        });
    }
}
