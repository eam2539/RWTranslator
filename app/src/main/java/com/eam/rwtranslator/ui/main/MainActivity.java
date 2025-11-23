package com.eam.rwtranslator.ui.main;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedDispatcher;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.eam.rwtranslator.R;
import com.eam.rwtranslator.databinding.MainActLayoutBinding;
import com.eam.rwtranslator.ui.project.IniFileManagerActivity;
import com.eam.rwtranslator.ui.setting.AppSettings;
import com.eam.rwtranslator.ui.setting.SettingActivity;
import com.eam.rwtranslator.utils.DialogUtils;
import com.eam.rwtranslator.utils.PopupUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.jetbrains.annotations.NotNull;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

  private static final String UPDATE_APP_URL = "https://github.com/EAM-25/RWTranslator/releases";
  private MainActLayoutBinding binding;
  private ActivityResultLauncher<Intent> launcher;
  private ActivityResultLauncher<Intent> folderLauncher;
  private final Context context = this;
  private boolean isCheckVersion = false;
  private long firstBackTime;
  private MainViewModel viewModel;
  MainActListAdapter adapter;
  DialogUtils du;
  Resources res;
  private boolean isFabMenuOpen = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);
    initView();
    initObserver();

    checkStoragePermission();
  }

  private void initView() {
    viewModel = new ViewModelProvider(this).get(MainViewModel.class);
    du = new DialogUtils(context);
    res = this.getResources();
    adapter = new MainActListAdapter(context);
    binding = MainActLayoutBinding.inflate(getLayoutInflater());
    binding.mainActRecyclerview.setAdapter(adapter);
    binding.mainActRecyclerview.setLayoutManager(new LinearLayoutManager(this));
    binding.mainActToolbar.setTitle(R.string.app_name);
    binding.mainActToolbar.setSubtitle(R.string.main_act_copyright);
    binding.gotoSettingButton.setOnClickListener(
        v4 -> startActivity(new Intent(context, SettingActivity.class)));
    binding.mainActFabAdd.setOnClickListener(v3 -> toggleFabMenu());
    
    // Sub FAB click listeners
    binding.mainActFabLoadZip.setOnClickListener(v -> {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType("application/zip");
      intent.putExtra(
          Intent.EXTRA_MIME_TYPES,
          new String[] {"application/zip", "application/octet-stream"});
      launcher.launch(intent);
      closeFabMenu();
    });
    
    binding.mainActFabSelectFolder.setOnClickListener(v -> {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
      folderLauncher.launch(intent);
      closeFabMenu();
    });
    setContentView(binding.getRoot());
    AppSettings.init(context);
    // 文件选择回调
    launcher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK) {
                Uri uri = result.getData().getData();
                viewModel.importProject(uri);
              }
            });
    
    // 文件夹选择回调
    folderLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
               if (result.getResultCode() == RESULT_OK) {
                 Uri uri = result.getData().getData();
                 viewModel.importProjectFromFolder(uri);
               }
             });
    // 列表点击事件
    adapter.setOnItemClickListener(
        position -> {
          du.createLoadingDialog().show();
          String projectName = adapter.getCurrentList().get(position).getProjectName();
          viewModel.loadProject(projectName);
        });
    // 长按菜单处理
    adapter.setOnItemLongClickListener(
        (position, view) -> {
          String projectName = adapter.getCurrentList().get(position).getProjectName();
          showContextMenu(view, projectName, position);
          return true;
        });
  }

  private void initObserver() {
    viewModel.loadProjects();
    // 初始化列表观察
    viewModel
        .projects()
        .observe(
            this,
            projects -> {
              adapter.submitList(projects);
              if (adapter.getItemCount() != 0) binding.emptyView.setVisibility(View.GONE);
              else binding.emptyView.setVisibility(View.VISIBLE);
            });
    // 观察导入进度
    viewModel
        .importProgress()
        .observe(
            this,
            isLoading -> {
              if (isLoading)
                du.createLoadingDialog(res.getString(R.string.main_act_unzip_dialog_tips)).show();
              else du.dismissLoadingDialog();
            });
    // 观察加载状态
    viewModel
        .showLoading()
        .observe(
            this,
            isLoading -> {
              if (isLoading) {
                du.createLoadingDialog().show();
              } else {
                du.dismissLoadingDialog();
              }
            });
    
    // 观察项目导航
    viewModel
        .shouldNavigateToProject()
        .observe(
            this,
            shouldNavigate -> {
              if (shouldNavigate) {
                viewModel.resetNavigationFlag();
                new Handler(Looper.getMainLooper())
                    .postDelayed(
                        () -> {
                          Intent intent = new Intent(context, IniFileManagerActivity.class);
                          intent.putExtra("clickdir", viewModel.currentProject().getValue());
                          startActivity(intent);
                        },
                        100);
              }
            });
    viewModel.toastMessage().observe(this, this::showMsg);
    viewModel.errorMessage().observe(this, throwable -> {
        Timber.e(throwable);
        showError(throwable);
    });
    // 观察版本更新
    viewModel
        .newVersion()
        .observe(
            this,
            versionInfo -> {
              new MaterialAlertDialogBuilder(this)
                  .setTitle(R.string.main_act_update_info_title)
                  .setMessage(versionInfo.updateContent)
                  .setPositiveButton(
                      R.string.main_act_positive_button,
                      (d, w) ->
                          startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_APP_URL))))
                  .setNegativeButton(R.string.negative_button, null)
                  .show();
            });
     // 处理返回按钮事件
      OnBackPressedDispatcher dispatcher = getOnBackPressedDispatcher();
      dispatcher.addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
          @Override
          public void handleOnBackPressed() {
              if (isFabMenuOpen) {
                  closeFabMenu();
                  return;
              }
              if (System.currentTimeMillis() - firstBackTime > 2000) {
                  showMsg(res.getString(R.string.main_act_exit_toast));
                  firstBackTime = System.currentTimeMillis();
              }else finish();
          }
        });
  }

  // 存储权限请求回调处理
  @Override
  public void onRequestPermissionsResult(int requestCode, String @NotNull [] permissions, int @NotNull [] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == 102) {
      boolean allGranted = true;
      for (int result : grantResults) {
        if (result != PackageManager.PERMISSION_GRANTED) {
          allGranted = false;
          break;
        }
      }
      if (!allGranted) {
        showMsg(res.getString(R.string.main_act_storage_permission_denied));
      } else {
        showMsg(res.getString(R.string.main_act_storage_permission_granted));
      }
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (!isCheckVersion) {
      viewModel.checkAppVersion();
      isCheckVersion = true;
    }
  }



  private void showContextMenu(View anchor, String projectName, int position) {
    PopupUtils.showPopupMenu(
        this,
        R.menu.mainact_list_longclickmenu,
        anchor,
        item -> {
          if (item.getItemId() == R.id.delete_project) {
            confirmDeleteProject(projectName, position);
          } else if (item.getItemId() == R.id.delete_cache) {
            viewModel.deleteProjectCache(projectName);
          }
          return false;
        });
  }

  private void confirmDeleteProject(String projectName, int position) {
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.main_act_confirm_delete_project_dialog_title)
        .setMessage(res.getString(R.string.main_act_confirm_delete_project_dialog_message, projectName))
        .setPositiveButton(
            R.string.positive_button, (d, w) -> viewModel.deleteProject(position, projectName))
        .setNegativeButton(R.string.negative_button, null)
        .show();
  }

  private void showMsg(String message) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
  }

  private void showError(Throwable throwable) {
    showError(throwable, null);
  }

  private void showError(Throwable throwable, String additionalMessage) {
    String errorMessage = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
    String message = getString(R.string.main_act_exception_occurred,errorMessage);
    if (additionalMessage != null) {
      message += "\n" + additionalMessage;
    }
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.main_act_operation_failed)
        .setMessage(message)
        .setPositiveButton(R.string.positive_button, null)
        .show();
  }

  private void toggleFabMenu() {
    if (isFabMenuOpen) {
      closeFabMenu();
    } else {
      openFabMenu();
    }
  }
  
  private void openFabMenu() {
    isFabMenuOpen = true;
    binding.mainActFabLoadZip.setVisibility(View.VISIBLE);
    binding.mainActFabSelectFolder.setVisibility(View.VISIBLE);
    binding.mainActLabelLoadZip.setVisibility(View.VISIBLE);
    binding.mainActLabelSelectFolder.setVisibility(View.VISIBLE);
    
    // Animate FABs
    binding.mainActFabLoadZip.animate().translationY(0).alpha(1.0f).setDuration(300);
    binding.mainActFabSelectFolder.animate().translationY(0).alpha(1.0f).setDuration(300);
    binding.mainActLabelLoadZip.animate().translationX(0).alpha(1.0f).setDuration(300);
    binding.mainActLabelSelectFolder.animate().translationX(0).alpha(1.0f).setDuration(300);
    
    // Rotate main FAB
    binding.mainActFabAdd.animate().rotation(45f).setDuration(300);
  }
  
  private void closeFabMenu() {
    isFabMenuOpen = false;
    
    // Animate FABs out
    binding.mainActFabLoadZip.animate().translationY(100).alpha(0.0f).setDuration(300)
        .withEndAction(() -> binding.mainActFabLoadZip.setVisibility(View.GONE));
    binding.mainActFabSelectFolder.animate().translationY(100).alpha(0.0f).setDuration(300)
        .withEndAction(() -> binding.mainActFabSelectFolder.setVisibility(View.GONE));
    binding.mainActLabelLoadZip.animate().translationX(100).alpha(0.0f).setDuration(300)
        .withEndAction(() -> binding.mainActLabelLoadZip.setVisibility(View.GONE));
    binding.mainActLabelSelectFolder.animate().translationX(100).alpha(0.0f).setDuration(300)
        .withEndAction(() -> binding.mainActLabelSelectFolder.setVisibility(View.GONE));
    
    // Rotate main FAB back
    binding.mainActFabAdd.animate().rotation(0f).setDuration(300);
  }

  public void checkStoragePermission() {
     if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.Q) {
      String[] permissions = new String[] {
          Manifest.permission.WRITE_EXTERNAL_STORAGE,
          Manifest.permission.READ_EXTERNAL_STORAGE
      };

      for (String permission : permissions) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
          requestPermissions(permissions, 102);
          break;
        }
      }
    }
  }
}
