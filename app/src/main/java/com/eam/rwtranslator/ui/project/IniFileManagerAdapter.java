package com.eam.rwtranslator.ui.project;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.eam.rwtranslator.R;
import com.eam.rwtranslator.data.model.IniFileModel;
import com.eam.rwtranslator.data.model.SectionModel;
import com.eam.rwtranslator.ui.common.MultiSelectAdapter;
import com.eam.rwtranslator.ui.common.RecyclerViewMultiSelectAdapter;
import com.eam.rwtranslator.ui.common.UniversalMultiSelectManager;
import com.eam.rwtranslator.ui.setting.AppSettings;
import com.eam.rwtranslator.utils.DialogUtils;
import com.eam.rwtranslator.utils.TemplatePlaceholderProcessor;
import com.eam.rwtranslator.utils.Translator;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

public class IniFileManagerAdapter extends ListAdapter<IniFileModel, RecyclerView.ViewHolder>
        implements Translator.TranslateTaskCallBack, MultiSelectAdapter {

    // 翻译错误列表
    public static final List<TranslationError> translationErrors = new LinkedList<>();
    // DiffUtil.ItemCallback 用于计算列表差异
    private static final DiffUtil.ItemCallback<IniFileModel> DIFF_CALLBACK = new DiffUtil.ItemCallback<IniFileModel>() {
        @Override
        public boolean areItemsTheSame(@NonNull IniFileModel oldItem, @NonNull IniFileModel newItem) {
            // 使用文件路径作为唯一标识
            return oldItem.getRwini().getFile().getPath().equals(newItem.getRwini().getFile().getPath());
        }

        @Override
        public boolean areContentsTheSame(@NonNull IniFileModel oldItem, @NonNull IniFileModel newItem) {
            // 比较文件名、查看状态、修改状态
            return oldItem.getIniname().equals(newItem.getIniname()) &&
                    oldItem.isViewed() == newItem.isViewed() &&
                    oldItem.isModified() == newItem.isModified() &&
                    oldItem.getCachedSectionCount() == newItem.getCachedSectionCount() &&
                    oldItem.getCachedItemCount() == newItem.getCachedItemCount();
        }
    };
    private final Context context;
    private final SparseArray<ViewHolder> visibleHolders = new SparseArray<>();
    private onItemClickListener clickListener;
    private onItemLongClickListener onclickListener;
    private List<IniFileModel> sourceList; // 用于过滤功能的原始列表
    private UniversalMultiSelectManager multiSelectManager;
    private OnTranslationCompleteListener translationCompleteListener;
    private OnFileMarkListener fileMarkListener;
    private RecyclerView attachedRecyclerView;
    public IniFileManagerAdapter(Context context, List<IniFileModel> data) {
        super(DIFF_CALLBACK);
        this.context = context;
        sourceList = new ArrayList<>(data);
        // 启用稳定ID以提升性能
        setHasStableIds(true);
        // 预计算每个文件的统计缓存，避免在 onBind 中重复计算
        precomputeCaches(data);
        // 提交初始数据到 ListAdapter
        submitList(new ArrayList<>(data));
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        attachedRecyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        attachedRecyclerView = null;
        visibleHolders.clear();
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder instanceof ViewHolder viewHolder) {
            int position = viewHolder.getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                visibleHolders.put(position, viewHolder);
                viewHolder.updateSelectionState(isPositionSelected(position));
            }
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (holder instanceof ViewHolder viewHolder) {
            int position = viewHolder.getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                visibleHolders.remove(position);
            }
        }
    }

    public void setOnItemClickListener(onItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnTranslationCompleteListener(OnTranslationCompleteListener listener) {
        this.translationCompleteListener = listener;
    }

    public void setOnFileMarkListener(OnFileMarkListener listener) {
        this.fileMarkListener = listener;
    }

    /**
     * 切换文件标记状态
     *
     * @param position 文件位置
     */
    public void toggleFileMark(int position) {
        List<IniFileModel> currentList = getCurrentList();
        if (position >= 0 && position < currentList.size()) {
            IniFileModel file = currentList.get(position);
            boolean newMarkState = !file.isViewed();
            file.setViewed(newMarkState);

            // 通知监听器
            if (fileMarkListener != null) {
                fileMarkListener.onFileMark(file, newMarkState);
            }

            // 更新UI
            notifyItemChanged(position);
        }
    }

    /**
     * 设置多选管理器
     *
     * @param multiSelectManager 通用多选管理器
     */
    public void setMultiSelectManager(UniversalMultiSelectManager multiSelectManager) {
        this.multiSelectManager = multiSelectManager;
        // 直接将ProjectFileManagerAdapter注册到多选管理器
        multiSelectManager.setAdapter(this);
    }

    /**
     * 获取选中的项目
     *
     * @return 选中的IniFileModel列表
     */
    public List<IniFileModel> getSelectedItems() {
        List<IniFileModel> selectedItems = new LinkedList<>();
        if (multiSelectManager != null) {
            Set<Integer> selectedPositions = multiSelectManager.getSelectedPositions();
            List<IniFileModel> currentList = getCurrentList();
            for (Integer position : selectedPositions) {
                if (position >= 0 && position < currentList.size()) {
                    selectedItems.add(currentList.get(position));
                }
            }
        }
        return selectedItems;
    }

    public void filterIniFileModel(@NotNull String text) {
        if (text.isEmpty()) {
            submitList(new ArrayList<>(sourceList));
            return;
        }
        // 使用简单高效的循环过滤，避免 parallelStream 带来的线程开销
        List<IniFileModel> filtered = new ArrayList<>();
        for (IniFileModel item : sourceList) {
            if (item.getIniname().contains(text)) filtered.add(item);
        }
        filtered.sort(Comparator.comparingInt(o -> o.getIniname().indexOf(text)));
        // 使用 submitList 自动进行差分更新
        submitList(filtered);
    }

    public void clear() {
        sourceList.clear();
        submitList(new ArrayList<>());
    }

    public void addAll(List<IniFileModel> data) {
        sourceList = new ArrayList<>(data);
        precomputeCaches(data);
        submitList(new ArrayList<>(data));
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
        View view =
                LayoutInflater.from(parent.getContext()).inflate(R.layout.project_act_item, parent, false);
        ViewHolder vh = new ViewHolder(view);
        view.setOnClickListener(v1 -> {
            int position = vh.getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            boolean handledByMultiSelect = multiSelectManager != null && multiSelectManager.handleItemClick(position);
            if (handledByMultiSelect) {
                vh.updateSelectionState(isPositionSelected(position));
                return;
            }
            if (clickListener != null) {
                clickListener.onItemClick(position);
            }
        });
        view.setOnLongClickListener(
                v2 -> {
                    int position = vh.getAdapterPosition();
                    if (position == RecyclerView.NO_POSITION) {
                        return true;
                    }
                    // 如果多选管理器处理了长按事件，返回true
                    if (multiSelectManager != null && multiSelectManager.handleItemLongClick(position)) {
                        vh.updateSelectionState(isPositionSelected(position));
                        return true;
                    }
                    // 如果不在多选模式，长按切换文件标记状态
                    if (multiSelectManager == null || !multiSelectManager.isMultiSelectMode()) {
                        toggleFileMark(position);
                        return true;
                    }
                    // 否则执行原来的长按逻辑
                    if (onclickListener != null) {
                        onclickListener.onItemLongClick(position, view);
                    }
                    return true;
                });
        return vh;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(RecyclerView.@NotNull ViewHolder holder, int position) {
        IniFileModel data = getItem(position);
        ViewHolder mholder = (ViewHolder) holder;
        trackVisibleHolder(mholder, position);

        // 基本数据绑定（只在文本变化时更新）
        String fileName = data.getIniname();
        if (!fileName.equals(mholder.iniNameView.getText().toString())) {
            mholder.iniNameView.setText(fileName);
        }

        // 使用预计算的缓存文本（避免每次 bind 都拼接字符串）
        String sectionText = data.getCachedSectionText();
        String itemText = data.getCachedItemText();
        if (!sectionText.equals(mholder.tv_sectionCount.getText().toString())) {
            mholder.tv_sectionCount.setText(sectionText);
        }
        if (!itemText.equals(mholder.tv_itemCount.getText().toString())) {
            mholder.tv_itemCount.setText(itemText);
        }

        // 优化：只在多选模式下才查询选中状态
        mholder.updateSelectionState(isPositionSelected(position));

        // 更新查看和修改状态（只在变化时更新）
        mholder.updateViewedState(data.isViewed());
        mholder.updateModifyState(data.isModified());
    }

    /**
     * 重载方法，支持payload参数进行局部更新
     *
     * @param holder   ViewHolder
     * @param position 位置
     * @param payloads payload列表
     */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            // 如果没有payload，执行完整绑定
            onBindViewHolder(holder, position);
        } else {
            // 处理payload，只更新选中状态（性能优化：只查询一次）
            ViewHolder mholder = (ViewHolder) holder;
            boolean hasSelectionChange = false;
            for (Object payload : payloads) {
                if ("selection_changed".equals(payload) || RecyclerViewMultiSelectAdapter.getSelectionPayload().equals(payload)) {
                    hasSelectionChange = true;
                    break;
                }
            }
            if (hasSelectionChange) {
                mholder.updateSelectionState(isPositionSelected(position));
            }
        }
    }

    /**
     * 提供稳定的ID，提升RecyclerView性能
     *
     * @param position 位置
     * @return 稳定的ID
     */
    @Override
    public long getItemId(int position) {
        List<IniFileModel> currentList = getCurrentList();
        if (position >= 0 && position < currentList.size()) {
            // 使用文件名的hashCode作为稳定ID
            return currentList.get(position).getRwini().getFile().getPath().hashCode();
        }
        return RecyclerView.NO_ID;
    }

    /**
     * Get selection background color based on current theme (day/night mode)
     *
     * @return Color value for selected item background with high contrast
     */
    private int getSelectionBackgroundColor() {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return switch (nightModeFlags) {
            case Configuration.UI_MODE_NIGHT_YES ->
                // Night mode: Use bright blue with good contrast against dark background
                    0xFF1976D2; // Material Blue 700 - high contrast for dark theme
            default ->
                // Day mode: Use light blue with good contrast against light background
                    0xFFBBDEFB; // Material Blue 100 - high contrast for light theme
        };
    }

    @Override
    public void onTranslate(boolean enable_llm) {
        // 初始化对话框
        DialogUtils dialogUtils = new DialogUtils(context);
        AlertDialog dialog = dialogUtils.createLoadingDialog("Initializing...");
        dialog.show();

        List<IniFileModel> selecttionList;
        List<IniFileModel> currentList = getCurrentList();

        if (multiSelectManager.isMultiSelectMode()) {
            selecttionList = new LinkedList<>();
            for (var i : multiSelectManager.getSelectedPositions()) {
                selecttionList.add(currentList.get(i));
            }
        } else
            selecttionList = new ArrayList<>(currentList);

        //打上修改标记
        selecttionList.forEach(i -> i.setModified(true));

        // 清空翻译错误列表
        synchronized (translationErrors) {
            translationErrors.clear();
        }

        // 如果是LLM翻译，则使用批量翻译
        if (enable_llm) {
            translateWithBatch(selecttionList, dialog);
        } else {
            translateIndividually(selecttionList, dialog);
        }
    }

    // 批量翻译方法（用于OpenAI，支持多INI文件整合翻译）
    private void translateWithBatch(List<IniFileModel> selecttionList, AlertDialog dialog) {
        // 收集所有待翻译的文本（跨多个INI文件）
        List<String> textsToTranslate = new ArrayList<>();
        List<TranslationContext> contexts = new ArrayList<>();

        for (IniFileModel iniFile : selecttionList) {
            for (SectionModel section : iniFile.getData()) {
                for (SectionModel.Pair pair : section.items()) {
                    TemplatePlaceholderProcessor.Payload payload = TemplatePlaceholderProcessor.mask(pair.getOri_val());
                    textsToTranslate.add(payload.maskedText());
                    contexts.add(new TranslationContext(iniFile, section, pair, payload.placeholders()));
                }
            }
        }

        final int totalTasks = textsToTranslate.size();
        final int totalFiles = selecttionList.size();

        // 获取进度文本视图
        TextView progressTextView = dialog.findViewById(R.id.mainactivityloadingTextView);
        assert progressTextView != null;
        progressTextView.setText(String.format(Locale.getDefault(), "Translating %d files, %d texts (0/%d)...",
                totalFiles, totalTasks, totalTasks));

        // 无任务直接返回
        if (totalTasks == 0) {
            dialog.dismiss();
            return;
        }

        // 使用支持分批的批量翻译
        Translator.LLM_batchTranslate(
                textsToTranslate,
                AppSettings.getCurrentFromLanguageCode(),
                AppSettings.getCurrentTargetLanguageCode(),
                new Translator.BatchTranslateCallBack() {
                    @Override
                    public void onSuccess(List<String> translations, String srcLang, String tgtLang) {

                        // 应用翻译结果
                        for (int i = 0; i < translations.size() && i < contexts.size(); i++) {
                            TranslationContext ctx = contexts.get(i);
                            String translation = translations.get(i);
                            String finalTranslation = TemplatePlaceholderProcessor.restore(translation, ctx.placeholders);

                            // 更新翻译结果
                            if (!AppSettings.getIsOverride()) {
                                ctx.pair.getLang_pairs().put(tgtLang, finalTranslation);
                            } else {
                                ctx.pair.setOri_val(finalTranslation);
                            }
                        }

                        // 更新UI
                        new Handler(Looper.getMainLooper()).post(() -> {
                            progressTextView.setText(String.format(Locale.getDefault(), "Translation completed! %d files, %d texts",
                                    totalFiles, totalTasks));
                            dialog.dismiss();
                            // ListAdapter 会自动通过 DiffUtil 更新变化的项
                            notifyItemRangeChanged(0, getItemCount());
                            if (multiSelectManager.isMultiSelectMode()) multiSelectManager.exitMultiSelectMode();
                            translationCompleteListener.onTranslationComplete(selecttionList);
                        });
                    }

                    @Override
                    public void onError(Throwable t) {
                        Timber.e(t, "Batch translation failed");
                        new Handler(Looper.getMainLooper()).post(() -> {
                            progressTextView.setText("Translation failed: " + t.getMessage());

                            new DialogUtils(context).createSimpleDialog(
                                    "Translation Error",
                                    t.getMessage(),
                                    (dialog1, which) -> dialog1.dismiss()
                            ).show();
                            dialog.dismiss();
                        });
                    }

                    @Override
                    public void onProgress(int batchIndex, int totalBatches, int completedTexts, int totalTexts) {
                        // 更新进度显示
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (totalBatches > 1) {
                                progressTextView.setText(String.format(Locale.getDefault(),
                                        "Translating %d files: Batch %d/%d (%d/%d texts)...",
                                        totalFiles, batchIndex + 1, totalBatches, completedTexts, totalTexts));
                            } else {
                                progressTextView.setText(String.format(Locale.getDefault(),
                                        "Translating %d files (%d/%d texts)...",
                                        totalFiles, completedTexts, totalTexts));
                            }
                        });
                    }
                }
        );
    }

    // 逐个翻译方法（用于非OpenAI的翻译器）
    private void translateIndividually(List<IniFileModel> selecttionList, AlertDialog dialog) {
        // 预计算总任务数
        final int[] totalTasks = {0};
        for (IniFileModel iniFile : selecttionList) {
            for (SectionModel section : iniFile.getData()) {
                for (SectionModel.Pair ignored : section.items()) {
                    totalTasks[0]++;
                }
            }
        }

        // 获取进度文本视图
        TextView progressTextView = dialog.findViewById(R.id.mainactivityloadingTextView);
        assert progressTextView != null;
        progressTextView.setText("Translating (0/" + totalTasks[0] + ")...");

        // 无任务直接返回
        if (totalTasks[0] == 0) {
            dialog.dismiss();
            return;
        }
        // 原子计数器跟踪完成数
        AtomicInteger completedTasks = new AtomicInteger(0);
        // 遍历选中的IniFileModel进行翻译
        for (IniFileModel iniFile : selecttionList) {
            for (SectionModel section : iniFile.getData()) {
                for (SectionModel.Pair pair : section.items()) {
                    TemplatePlaceholderProcessor.Payload payload = TemplatePlaceholderProcessor.mask(pair.getOri_val());
                    List<String> placeholders = payload.placeholders();
                    String textForTranslation = payload.maskedText();

                    var translateCallBack = new Translator.TranslateCallBack() {
                        @Override
                        public void onSuccess(String translation, String srcLang, String tgtLang) {
                            String finalTranslation = TemplatePlaceholderProcessor.restore(translation, placeholders);
                            // 更新翻译结果
                            if (!AppSettings.getIsOverride()) {
                                pair.getLang_pairs().put(tgtLang, finalTranslation);
                            } else {
                                pair.setOri_val(finalTranslation);
                            }
                            updateProgress(completedTasks, totalTasks[0], progressTextView, dialog, selecttionList);
                        }

                        @Override
                        public void onError(Throwable t) {
                            updateProgress(completedTasks, totalTasks[0], progressTextView, dialog, selecttionList);

                            // 记录详细的日志信息
                            String filePath = iniFile.getRwini().getFile().getAbsolutePath();
                            String keyName = pair.getKey().getKeyName();
                            String originalValue = pair.getOri_val();

                            Timber.e(t, "Translation error for file: %s, section: %s, key: %s, value: %s",
                                    filePath, section.name(), keyName, originalValue);

                            // 添加到翻译错误列表
                            synchronized (translationErrors) {
                                translationErrors.add(new TranslationError(
                                        filePath,
                                        section.name(),
                                        keyName,
                                        originalValue,
                                        t
                                ));
                            }
                        }
                    };
                    Translator.translate(
                            textForTranslation,
                            translateCallBack);
                }
            }
        }
    }

    // 进度更新方法
    @SuppressLint({"SetTextI18n", "NotifyDataSetChanged"})
    private void updateProgress(AtomicInteger counter, int total, TextView tv, AlertDialog dialog, List<IniFileModel> translatedFiles) {
        int current = counter.incrementAndGet();
        new Handler(Looper.getMainLooper())
                .post(
                        () -> {
                            tv.setText("Translating (" + current + "/" + total + ")...");
                            if (current == total) {
                                dialog.dismiss();
                                // ListAdapter 会自动通过 DiffUtil 更新变化的项
                                notifyItemRangeChanged(0, getItemCount());
                                if (multiSelectManager.isMultiSelectMode())
                                    multiSelectManager.exitMultiSelectMode();
                                // 通知翻译完成
                                translationCompleteListener.onTranslationComplete(translatedFiles);
                            }
                        });
    }

    /**
     * 覆盖 ListAdapter 的 protected getItem() 方法，提供公共访问
     *
     * @param position 位置
     * @return IniFileModel
     */
    @Override
    public IniFileModel getItem(int position) {
        return super.getItem(position);
    }

    public List<IniFileModel> getMData() {
        return getCurrentList();
    }

    public void setMData(List<IniFileModel> data) {
        sourceList = new ArrayList<>(data);
        precomputeCaches(data);
        submitList(new ArrayList<>(data));
    }

    private boolean isPositionSelected(int position) {
        return multiSelectManager != null
                && multiSelectManager.isMultiSelectMode()
                && multiSelectManager.isSelected(position);
    }

    private boolean updateVisibleSelectionState(int position) {
        ViewHolder holder = visibleHolders.get(position);
        if (holder != null) {
            holder.updateSelectionState(isPositionSelected(position));
            return true;
        }
        return false;
    }

    private void updateAllVisibleSelectionStates() {
        for (int i = 0; i < visibleHolders.size(); i++) {
            int key = visibleHolders.keyAt(i);
            ViewHolder holder = visibleHolders.valueAt(i);
            if (holder != null) {
                holder.updateSelectionState(isPositionSelected(key));
            }
        }
    }

    private void trackVisibleHolder(ViewHolder holder, int position) {
        for (int i = visibleHolders.size() - 1; i >= 0; i--) {
            if (visibleHolders.valueAt(i) == holder) {
                visibleHolders.removeAt(i);
                break;
            }
        }
        visibleHolders.put(position, holder);
    }

    /**
     * 预计算每个 IniFileModel 的统计缓存，避免在 onBind 中重复计算
     */
    private void precomputeCaches(List<IniFileModel> list) {
        if (list == null) return;
        for (IniFileModel data : list) {
            if (data == null) continue;
            if (data.getCachedSectionCount() == -1) {
                List<SectionModel> groups = data.getData();
                int sectionCnt = groups == null ? 0 : groups.size();
                int itemCnt = 0;
                if (groups != null) {
                    for (SectionModel g : groups) {
                        if (g != null && g.items() != null) itemCnt += g.items().size();
                    }
                }
                data.setCachedSectionCount(sectionCnt);
                data.setCachedItemCount(itemCnt);
            }
            if (data.getCachedSectionText() == null) {
                data.setCachedSectionText(data.getCachedSectionCount() + " section");
            }
            if (data.getCachedItemText() == null) {
                data.setCachedItemText(data.getCachedItemCount() + " Pair");
            }
        }
    }

    // 实现 MultiSelectAdapter 接口
    @Override
    public int getTotalItemCount() {
        return getItemCount();
    }

    @Override
    public void notifyItemSelectionChanged(int position) {
        if (!updateVisibleSelectionState(position)) {
            notifyItemChanged(position, RecyclerViewMultiSelectAdapter.getSelectionPayload());
        }
    }

    @Override
    public void notifyAllSelectionChanged() {
        updateAllVisibleSelectionStates();
        if (attachedRecyclerView == null) {
            notifyItemRangeChanged(0, getItemCount(), RecyclerViewMultiSelectAdapter.getSelectionPayload());
        }
    }

    @Override
    public void notifyItemsSelectionChanged(Set<Integer> positions) {
        if (positions.isEmpty()) {
            return;
        }
        boolean anyMissingHolder = false;
        if (attachedRecyclerView != null) {
            for (Integer position : positions) {
                if (!updateVisibleSelectionState(position)) {
                    anyMissingHolder = true;
                }
            }
        } else {
            anyMissingHolder = true;
        }

        if (anyMissingHolder) {
            if (positions.size() > getItemCount() / 2) {
                notifyItemRangeChanged(0, getItemCount(), RecyclerViewMultiSelectAdapter.getSelectionPayload());
            } else {
                for (Integer position : positions) {
                    notifyItemChanged(position, RecyclerViewMultiSelectAdapter.getSelectionPayload());
                }
            }
        }
    }

    public void notifyEnterMultiSelectMode() {
        // 进入多选模式时不需要刷新所有项（因为都是未选中状态）
        // 只需要更新 toolbar 等 UI 即可，ViewHolder 会在下次 bind 时自动更新
    }

    @Override
    public void notifyExitMultiSelectMode() {
        updateAllVisibleSelectionStates();
        if (attachedRecyclerView == null) {
            notifyItemRangeChanged(0, getItemCount(), RecyclerViewMultiSelectAdapter.getSelectionPayload());
        }
    }

    @Override
    public AdapterType getAdapterType() {
        return AdapterType.RECYCLER_VIEW;
    }

    public interface onItemClickListener {
        void onItemClick(int position);
    }

    public interface onItemLongClickListener {
        void onItemLongClick(int position, View view);
    }

    public interface OnTranslationCompleteListener {
        void onTranslationComplete(List<IniFileModel> translatedFiles);
    }


    public interface OnFileMarkListener {
        void onFileMark(IniFileModel file, boolean isMarked);
    }

    /**
     * @param filePath      文件路径
     * @param sectionName   分组名称
     * @param keyName       键名
     * @param originalValue 原始值
     * @param error         异常对象
     */ // 用于记录翻译过程中的异常信息
    public record TranslationError(String filePath, String sectionName, String keyName,
                                   String originalValue, Throwable error) {

        @NonNull
        @Override
        public String toString() {
            return "File: " + filePath +
                    "\nSection: " + sectionName +
                    "\nKey: " + keyName +
                    "\nOriginal Value: " + originalValue +
                    "\nError: " + (error != null ? error.getMessage() : "Unknown error");
        }
    }

    // 翻译上下文类，用于批量翻译
        private record TranslationContext(IniFileModel iniFile, SectionModel section, SectionModel.Pair pair,
                                          List<String> placeholders) {
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final Context context;
        TextView iniNameView, tv_sectionCount, tv_itemCount;
        View viewMarkIndicator;
        View modifyMarkIndicator;
        private boolean isSelected = false;
        private boolean isViewed = false;
        private boolean isModified = false;

        // 性能优化：缓存颜色值，避免重复计算
        private int cachedSelectionBgColor = -1;
        private int cachedViewedIndicatorColor = -1;
        private int cachedModifyIndicatorColor = -1;
        private boolean colorCacheValid = false;


        public ViewHolder(View itemView) {
            super(itemView);
            this.context = itemView.getContext();
            iniNameView = itemView.findViewById(R.id.IniListItemTextView1);
            tv_sectionCount = itemView.findViewById(R.id.IniListItemTextView2);
            tv_itemCount = itemView.findViewById(R.id.IniListItemTextView3);
            viewMarkIndicator = itemView.findViewById(R.id.mark_indicator_view);
            modifyMarkIndicator = itemView.findViewById(R.id.mark_indicator_modify);

            // 初始化颜色缓存
            initializeColorCache();
        }

        /**
         * 初始化颜色缓存，避免重复计算主题相关颜色
         */
        private void initializeColorCache() {
            int nightModeFlags = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            boolean isNightMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;

            if (isNightMode) {
                cachedSelectionBgColor = 0xFF1976D2; // Material Blue 700
                cachedViewedIndicatorColor = 0xFFFF9800; // Material Orange 500
                cachedModifyIndicatorColor = 0xFF4CAF50; // Material Green 500
            } else {
                cachedSelectionBgColor = 0xFFE3F2FD; // Material Blue 50
                cachedViewedIndicatorColor = 0xFFE65100; // Material Deep Orange 900
                cachedModifyIndicatorColor = 0xFF2E7D32; // Material Green 800
            }
            colorCacheValid = true;
        }

        /**
         * 更新选中状态，避免重复设置背景色
         *
         * @param selected 是否选中
         */
        public void updateSelectionState(boolean selected) {
            if (this.isSelected != selected) {
                this.isSelected = selected;
                updateBackgroundColor();
            }
        }

        /**
         * 更新查看状态
         *
         * @param viewed 是否查看
         */
        public void updateViewedState(boolean viewed) {
            if (this.isViewed != viewed) {
                this.isViewed = viewed;
                // 显示/隐藏右边的竖线指示器（优化：减少不必要的方法调用）
                if (viewMarkIndicator != null) {
                    int visibility = viewed ? View.VISIBLE : View.GONE;
                    if (viewMarkIndicator.getVisibility() != visibility) {
                        viewMarkIndicator.setVisibility(visibility);
                        if (viewed) {
                            viewMarkIndicator.setBackgroundColor(getCachedViewedIndicatorColor());
                        }
                    }
                }
            }
        }

        /**
         * 更新修改状态
         *
         * @param modified 是否修改
         */
        public void updateModifyState(boolean modified) {
            if (this.isModified != modified) {
                this.isModified = modified;
                // 显示/隐藏左边的修改指示器（优化：减少不必要的方法调用）
                if (modifyMarkIndicator != null) {
                    int visibility = modified ? View.VISIBLE : View.GONE;
                    if (modifyMarkIndicator.getVisibility() != visibility) {
                        modifyMarkIndicator.setVisibility(visibility);
                        if (modified) {
                            modifyMarkIndicator.setBackgroundColor(getCachedModifyIndicatorColor());
                        }
                    }
                }
            }
        }

        /**
         * 更新背景色，只考虑选中状态
         */
        private void updateBackgroundColor() {
            if (isSelected) {
                itemView.setBackgroundColor(getCachedSelectionBackgroundColor());
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT);
            }
        }

        /**
         * 获取缓存的选中状态背景色
         *
         * @return 背景色
         */
        private int getCachedSelectionBackgroundColor() {
            if (!colorCacheValid) {
                initializeColorCache();
            }
            return cachedSelectionBgColor;
        }

        /**
         * 获取缓存的查看指示器颜色
         *
         * @return 指示器颜色
         */
        private int getCachedViewedIndicatorColor() {
            if (!colorCacheValid) {
                initializeColorCache();
            }
            return cachedViewedIndicatorColor;
        }

        /**
         * 获取缓存的修改指示器颜色（绿色）
         *
         * @return 指示器颜色
         */
        private int getCachedModifyIndicatorColor() {
            if (!colorCacheValid) {
                initializeColorCache();
            }
            return cachedModifyIndicatorColor;
        }

        /**
         * 重置颜色缓存，用于主题变化时
         */
        public void resetColorCache() {
            this.colorCacheValid = false;
            initializeColorCache();
        }
    }

}

