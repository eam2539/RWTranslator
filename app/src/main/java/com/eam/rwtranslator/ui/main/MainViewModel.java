package com.eam.rwtranslator.ui.main;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.eam.rwtranslator.data.model.DataSet;
import com.eam.rwtranslator.ui.main.MainActData;
import com.eam.rwtranslator.data.repository.ProjectRepository;
import com.eam.rwtranslator.AppConfig;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.io.InputStream;
import java.util.regex.Pattern;
import timber.log.Timber;
import org.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.eam.rwtranslator.R;
import com.eam.rwtranslator.BuildConfig;
import androidx.lifecycle.AndroidViewModel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class MainViewModel extends AndroidViewModel {
  // GitHub API URL for releases
  private static final String GITHUB_RELEASES_API_URL = 
      "https://api.github.com/repos/eam2539/RWTranslator/releases/latest";
  private static final String GITHUB_RELEASES_URL = 
      "https://github.com/eam2539/RWTranslator/releases";
  
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Context context;
  private final MutableLiveData<String> _toastMessage = new MutableLiveData<>();
  private final MutableLiveData<Throwable> _errorMessage = new MutableLiveData<>();
  private final MutableLiveData<Boolean> _showLoading = new MutableLiveData<>();
  private final MutableLiveData<VersionInfo> _newVersion = new MutableLiveData<>();
  private final ProjectRepository repository = new ProjectRepository();
  private final MutableLiveData<List<MainActData>> _projects = new MutableLiveData<>();
  private final MutableLiveData<Boolean> _importProgress = new MutableLiveData<>();
  private final MutableLiveData<String> _currentProject = new MutableLiveData<>();
  private final MutableLiveData<Boolean> _shouldNavigateToProject = new MutableLiveData<>();

  // LiveData只暴露只读接口，防止外部误操作
  public LiveData<String> currentProject() { return _currentProject; }
  public LiveData<List<MainActData>> projects() { return _projects; }
  public LiveData<Boolean> importProgress() { return _importProgress; }
  public LiveData<String> toastMessage() { return _toastMessage; }
  public LiveData<Throwable> errorMessage() { return _errorMessage; }
  public LiveData<Boolean> showLoading() { return _showLoading; }
  public LiveData<VersionInfo> newVersion() { return _newVersion; }
  public LiveData<Boolean> shouldNavigateToProject() { return _shouldNavigateToProject; }
  
  public void resetNavigationFlag() {
    _shouldNavigateToProject.postValue(false);
  }

  public MainViewModel(@NonNull Application application) {
    super(application);
    this.context = application.getApplicationContext();
  }
  
  /**
   * 检查GitHub上的最新版本
   */
  public void checkAppVersion() {
    CompletableFuture.supplyAsync(() -> {
      try {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
            .url(GITHUB_RELEASES_API_URL)
            .addHeader("Accept", "application/vnd.github.v3+json")
            .build();
        
        try (Response response = client.newCall(request).execute()) {
          if (response.isSuccessful() && response.body() != null) {
            String responseBody = response.body().string();
            JSONObject jsonObject = new JSONObject(responseBody);
            
            String tagName = jsonObject.getString("tag_name");
            String releaseName = jsonObject.getString("name");
            String releaseBody = jsonObject.optString("body", "");
            
            // 提取版本号
            String versionString = tagName.replaceFirst("^v", "");
            String[] versionParts = versionString.split("\\.");
            
            if (versionParts.length >= 3) {
              int major = Integer.parseInt(versionParts[0]);
              int minor = Integer.parseInt(versionParts[1]);
              int patch = Integer.parseInt(versionParts[2]);
              
              // 计算版本代码（假设格式：major * 10000 + minor * 100 + patch）
              int remoteVersionCode = major * 10000 + minor * 100 + patch;
              int currentVersionCode = BuildConfig.VERSION_CODE;
              
              Timber.d("Current version: %d, Remote version: %d", currentVersionCode, remoteVersionCode);
              
              if (remoteVersionCode > currentVersionCode) {
                String updateContent = String.format(
                    "%s\n\n%s", 
                    context.getString(R.string.main_act_new_version_available, tagName),
                    releaseBody.isEmpty() ? context.getString(R.string.main_act_no_release_notes) : releaseBody
                );
                return new VersionInfo(remoteVersionCode, updateContent);
              }
            }
          }
        }
      } catch (Exception e) {
        Timber.e(e, "Failed to check app version");
        // 静默失败，不显示错误给用户
      }
      return null;
    }, executor).thenAccept(versionInfo -> {
      if (versionInfo != null) {
        _newVersion.postValue(versionInfo);
      }
    });
  }


  // 数据类
  public static class VersionInfo {
    public final int versionCode;
    public final String updateContent;

    public VersionInfo(int versionCode, String content) {
      this.versionCode = versionCode;
      this.updateContent = content;
    }
  }

  // 初始化项目列表
  public void loadProjects() {
    List<MainActData> projects =
        Optional.of(
                Arrays.stream(AppConfig.externalProjectDir.list())
                    .map(MainActData::new)
                    .collect(Collectors.toList()))

            .orElseGet(Collections::emptyList);
    _projects.postValue(projects);
  }

  // 处理文件导入
  public void importProject(Uri uri) {
    _importProgress.postValue(true);
    repository
        .handleFileImport(uri, context)
        .whenComplete(
            (success, ex) -> {
              _importProgress.postValue(false);
              if (ex != null) {
                _errorMessage.postValue(ex);
              } else {
                loadProjects();
                _toastMessage.postValue(context.getString(R.string.main_act_import_success));
              }
            });
  }
  
  // 处理文件夹导入
  public void importProjectFromFolder(Uri uri) {
    _importProgress.postValue(true);
    repository
        .handleFolderImport(uri, context)
        .whenComplete(
            (success, ex) -> {
              _importProgress.postValue(false);
              if (ex != null) {
                _errorMessage.postValue(ex);
              } else {
                loadProjects();
                _toastMessage.postValue(context.getString(R.string.main_act_import_success));
              }
            });
  }

  // 处理删除操作
  public void deleteProject(int position, String projectName) {
    _showLoading.postValue(true);
    repository
        .deleteProject(projectName)
        .whenComplete(
            (success, ex) -> {
              if (ex != null) {
                _errorMessage.postValue(ex);
              } else {
                List<MainActData> current = _projects.getValue();
                if (current != null) {
                  List<MainActData> newList = new java.util.ArrayList<>(current);
                  newList.remove(position);
                  _projects.postValue(newList);
                  _showLoading.postValue(false);
                }
              }
            });
  }

  public void deleteProjectCache(String projectName) {

    repository
        .deleteProjectCache(projectName)
        .whenComplete(
            (success, ex) -> {
              if (ex != null) {
                _errorMessage.postValue(ex);
              } else {
                _toastMessage.postValue(
                    success
                        ? context.getString(R.string.main_act_cache_cleared)
                        : context.getString(R.string.main_act_cache_not_exist));
              }
            });
  }

  public void loadProject(String projectName) {
    _showLoading.postValue(true);
    repository
        .loadProjectData(projectName)
        .thenAccept(
            project -> {
              _showLoading.postValue(false);
              DataSet.setCurrentProject(project);
              _currentProject.postValue(projectName);
              _shouldNavigateToProject.postValue(true);
            })
        .exceptionally(
            ex -> {
              _showLoading.postValue(false);
              _errorMessage.postValue(ex);
              return null;
            });
  }
}
