package com.eam.rwtranslator.ui.editor;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;

import com.eam.rwtranslator.R;
import com.eam.rwtranslator.data.model.SectionModel;
import com.eam.rwtranslator.ui.common.ExpandableListMultiSelectAdapter;
import com.eam.rwtranslator.ui.common.MultiSelectAdapter;
import com.eam.rwtranslator.ui.common.MultiSelectDecorator;
import com.eam.rwtranslator.ui.common.UniversalMultiSelectManager;
import com.eam.rwtranslator.ui.setting.AppSettings;
import com.eam.rwtranslator.ui.setting.SettingsFragment;
import com.eam.rwtranslator.utils.DialogUtils;
import com.eam.rwtranslator.utils.TemplatePlaceholderProcessor;
import com.eam.rwtranslator.utils.Translator;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

public class SectionEditorAdapter extends BaseExpandableListAdapter
        implements Translator.TranslateTaskCallBack, MultiSelectAdapter {
    private final Context context;
    private final Resources res;
    private final List<SectionModel> groups;
    private final UniversalMultiSelectManager multiSelectManager;
    private final ExpandableListMultiSelectAdapter adapterWrapper;

    private final HashMap<SectionModel.Pair, Integer> itemPositionMap;
    private final Handler handler;

    private onItemClickListener clickListener;

    // 性能优化：缓存选中状态，避免重复计算
    private final Map<String, Boolean> selectionCache = new HashMap<>();
    private boolean cacheValid = false;

    public SectionEditorAdapter(
            Context context,
            List<SectionModel> groups,
            View multiselectView,
            MaterialToolbar toolbar,
            ExpandableListView listView) {
        this.context = context;
        this.groups = groups;
        this.res = context.getResources();
        this.handler = new Handler(context.getMainLooper());

        // 初始化多选装饰器
        MultiSelectDecorator multiSelectDecorator = new MultiSelectDecorator(context);

        // 初始化通用多选管理器
        this.adapterWrapper = new ExpandableListMultiSelectAdapter(this, listView);
        this.multiSelectManager = new UniversalMultiSelectManager();
        this.multiSelectManager.setAdapter(this); // 直接使用SectionEditorAdapter
        this.multiSelectManager.setDecorator(multiSelectDecorator);
        this.multiSelectManager.setToolbar(toolbar);
        this.multiSelectManager.setMultiSelectButton(multiselectView);

        // 设置选中状态变化监听器
        this.multiSelectManager.setOnSelectionChangedListener((selectedCount, isItemSelected) -> {
            if (toolbar != null) {
                if (selectedCount > 0) {
                    toolbar.setSubtitle(selectedCount + "/" + getTotalItemCount());
                } else {
                    toolbar.setSubtitle(null);
                }
            }
        });

        this.itemPositionMap = new HashMap<>();
        initItemPositionMap();

    }

    private void initItemPositionMap() {
        int position = 0;
        for (SectionModel group : groups) {
            for (SectionModel.Pair item : group.items()) {
                itemPositionMap.put(item, position++);
            }
        }
    }

    public int getPosition(SectionModel.Pair item) {
        return itemPositionMap.getOrDefault(item, -1);
    }

    @Override
    public int getGroupCount() {
        return groups.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return groups.get(groupPosition).items().size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return groups.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return groups.get(groupPosition).items().get(childPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(
            int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        GroupViewHolder holder;
        if (convertView == null) {
            convertView =
                    LayoutInflater.from(context).inflate(R.layout.section_editor_item_group, parent, false);
            holder = new GroupViewHolder();
            holder.nameTextView = convertView.findViewById(R.id.tv_sectionTitle);
            holder.groupIndicator = convertView.findViewById(R.id.group_indicator);
            convertView.setTag(holder);
        } else {
            holder = (GroupViewHolder) convertView.getTag();
        }
        SectionModel group = (SectionModel) getGroup(groupPosition);
        holder.nameTextView.setText(group.name());
        if (!isExpanded) holder.groupIndicator.setImageResource(R.drawable.chevron_up);
        else {
            holder.groupIndicator.setImageResource(R.drawable.chevron_down);
        }
        return convertView;
    }

    @Override
    public View getChildView(
            int groupPosition,
            int childPosition,
            boolean isLastChild,
            View convertView,
            ViewGroup parent) {
        ItemViewHolder holder;
        if (convertView == null) {
            convertView =
                    LayoutInflater.from(context).inflate(R.layout.section_editor_item_item, parent, false);
            holder = new ItemViewHolder();
            holder.tvKey = convertView.findViewById(R.id.key_text);
            holder.tvSrc = convertView.findViewById(R.id.src_text);
            convertView.setTag(holder);
        } else {
            holder = (ItemViewHolder) convertView.getTag();
        }

        // 重置View状态，避免复用时的状态残留
        convertView.setBackgroundColor(0x00000000); // 先设置为透明

        SectionModel.Pair item =
                (SectionModel.Pair) getChild(groupPosition, childPosition);
        holder.tvKey.setText(item.getKey().getKeyName());
        holder.tvSrc.setText(item.getOri_val());
        convertView.setOnClickListener(v -> handleItemClick(groupPosition, childPosition));
        convertView.setOnLongClickListener(
                v -> {
                    handleItemLongClick(groupPosition, childPosition);
                    return true;
                });
        // 应用多选状态 - 使用缓存优化性能
        if (multiSelectManager != null && multiSelectManager.isMultiSelectMode()) {
            String cacheKey = groupPosition + "-" + childPosition;
            boolean isSelected;

            if (cacheValid && selectionCache.containsKey(cacheKey)) {
                // 使用缓存的选中状态
                isSelected = selectionCache.get(cacheKey);
            } else {
                // 计算并缓存选中状态
                int flatPosition = getFlatPosition(groupPosition, childPosition);
                isSelected = multiSelectManager.isSelected(flatPosition);
                selectionCache.put(cacheKey, isSelected);
                // 标记缓存为有效
                if (!cacheValid) {
                    validateSelectionCache();
                }
            }

            // 直接设置背景色，避免Canvas绘制开销
            if (isSelected) {
                convertView.setBackgroundColor(getSelectionBackgroundColor()); // 根据主题设置背景色
            } else {
                convertView.setBackgroundColor(0x00000000); // 透明背景
            }
        } else {
            // 非多选模式下直接设置透明背景
            convertView.setBackgroundColor(0x00000000);
        }
        return convertView;
    }

    private void handleItemClick(int groupPosition, int childPosition) {
        int flatPosition = getFlatPosition(groupPosition, childPosition);

        // 添加边界检查，确保位置有效
        if (flatPosition < 0 || flatPosition >= getFlatCount()) {
            return;
        }

        if (multiSelectManager.isMultiSelectMode()) {
            // 选中状态变化时清除缓存
            invalidateSelectionCache();
            multiSelectManager.toggleItemSelection(flatPosition);
        } else {
            clickListener.onItemClick(groupPosition, childPosition);
        }
    }

    private void handleItemLongClick(int groupPosition, int childPosition) {
        int flatPosition = getFlatPosition(groupPosition, childPosition);

        // 添加边界检查，确保位置有效
        if (flatPosition < 0 || flatPosition >= getFlatCount()) {
            return;
        }

        if (!multiSelectManager.isMultiSelectMode()) {
            invalidateSelectionCache();
            multiSelectManager.enterMultiSelectMode();
        }
        // 选中状态变化时清除缓存
        invalidateSelectionCache();
        multiSelectManager.toggleItemSelection(flatPosition);
    }

    /**
     * 计算子项目在扁平列表中的位置（不包含组头）
     *
     * @param groupPosition 组位置
     * @param childPosition 子项目位置
     * @return 扁平位置
     */
    private int getFlatPosition(int groupPosition, int childPosition) {
        int position = 0;
        // 只计算前面组的子项目数量，不包含组头
        for (int i = 0; i < groupPosition; i++) {
            position += getChildrenCount(i);
        }
        return position + childPosition;
    }

    /**
     * 获取所有子项目的总数（不包含组头）
     *
     * @return 子项目总数
     */
    public int getFlatCount() {
        int count = 0;
        for (SectionModel group : groups) {
            count += group.items().size();
        }
        return count;
    }

    /**
     * 获取所有子项目的总数（与getFlatCount相同）
     *
     * @return 子项目总数
     */


    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    public void toggleItemSelection(int groupPosition, int childPosition) {
        int flatPosition = getFlatPosition(groupPosition, childPosition);
        multiSelectManager.toggleItemSelection(flatPosition);
        // 局部更新由 UniversalMultiSelectManager 处理，无需 notifyDataSetChanged
    }

    // 多选模式管理已移至 UniversalMultiSelectManager，移除重复逻辑

    public void enterMultiSelectMode() {
        invalidateSelectionCache();
        // 通过反射或直接访问来设置多选模式状态
        if (multiSelectManager != null) {
            try {
                java.lang.reflect.Field field = multiSelectManager.getClass().getDeclaredField("isMultiSelectMode");
                field.setAccessible(true);
                field.set(multiSelectManager, true);
                multiSelectManager.rotateMultiSelectButton(true);
                multiSelectManager.updateSubtitle();
            } catch (Exception e) {
                // 如果反射失败，则调用原方法但不会造成循环
                // 因为我们已经实现了接口方法
            }
        }
        notifyDataSetChanged();
    }

    public void exitMultiSelectMode() {
        invalidateSelectionCache();
        // 通过反射或直接访问来设置多选模式状态
        if (multiSelectManager != null) {
            try {
                java.lang.reflect.Field field = multiSelectManager.getClass().getDeclaredField("isMultiSelectMode");
                field.setAccessible(true);
                field.set(multiSelectManager, false);
                if (multiSelectManager.getDecorator() != null) {
                    multiSelectManager.getDecorator().clearSelections();
                }
                multiSelectManager.rotateMultiSelectButton(false);
                multiSelectManager.updateSubtitle();
            } catch (Exception e) {
                // 如果反射失败，则调用原方法但不会造成循环
                // 因为我们已经实现了接口方法
            }
        }
        notifyDataSetChanged();
    }

    /**
     * 清除选中状态缓存
     */
    private void invalidateSelectionCache() {
        selectionCache.clear();
        cacheValid = false;
    }

    /**
     * 标记缓存为有效
     */
    private void validateSelectionCache() {
        cacheValid = true;
    }

    public void selectAll() {
        multiSelectManager.selectAll();
    }

    public void inverseSelection() {
        multiSelectManager.inverseSelection();
    }

    @Override
    public void notifyDataSetChanged() {
        // 数据变化时清除选中状态缓存
        invalidateSelectionCache();
        super.notifyDataSetChanged();
    }

    // 实现 IMultiSelectAdapter 接口
    @Override
    public int getTotalItemCount() {
        return getFlatCount();
    }

    @Override
    public void notifyAllSelectionChanged() {
        notifyDataSetChanged();
    }

    @Override
    public void notifyItemSelectionChanged(int position) {
        // 清除缓存并通知数据变化
        invalidateSelectionCache();
        notifyDataSetChanged();
    }

    @Override
    public void notifyItemsSelectionChanged(Set<Integer> positions) {
        // 清除缓存
        invalidateSelectionCache();

        // 对于大量更新（超过30%的项目），使用全量更新
        if (positions.size() > getTotalItemCount() * 0.3) {
            notifyDataSetChanged();
        } else {
            // 对于少量更新，使用局部更新机制
            updateSelectedViews(positions);
        }
    }

    @Override
    public void notifyEnterMultiSelectMode() {
        // 进入多选模式时清除缓存
        invalidateSelectionCache();
        notifyDataSetChanged();
    }

    @Override
    public void notifyExitMultiSelectMode() {
        // 退出多选模式时清除缓存
        invalidateSelectionCache();
        notifyDataSetChanged();
    }

    @Override
    public AdapterType getAdapterType() {
        return AdapterType.EXPANDABLE_LIST_VIEW;
    }

    /**
     * 局部更新选中状态的视图
     *
     * @param positions 需要更新的扁平位置集合
     */
    private void updateSelectedViews(Set<Integer> positions) {
        ExpandableListView listView = adapterWrapper.getListView();
        if (listView == null || positions.isEmpty()) return;

        listView.post(() -> {
            for (Integer flatPosition : positions) {
                updateSingleView(flatPosition);
            }
        });
    }

    /**
     * 更新单个视图的选中状态
     *
     * @param flatPosition 扁平位置
     */
    private void updateSingleView(int flatPosition) {
        ExpandableListView listView = adapterWrapper.getListView();
        if (listView == null) return;

        // 将扁平位置转换为组和子位置
        int[] groupChild = convertFlatPositionToGroupChild(flatPosition);
        if (groupChild == null) return;

        int groupPosition = groupChild[0];
        int childPosition = groupChild[1];

        // 获取可见的子视图
        long packedPosition = ExpandableListView.getPackedPositionForChild(groupPosition, childPosition);
        int flatPos = listView.getFlatListPosition(packedPosition);

        // 如果getFlatListPosition返回-1，说明位置无效
        if (flatPos == -1) return;

        int firstVisible = listView.getFirstVisiblePosition();
        int lastVisible = listView.getLastVisiblePosition();

        if (flatPos >= firstVisible && flatPos <= lastVisible) {
            int viewIndex = flatPos - firstVisible;
            if (viewIndex >= 0 && viewIndex < listView.getChildCount()) {
                View childView = listView.getChildAt(viewIndex);
                if (childView != null) {
                    // 直接更新视图的选中状态，避免重新绑定
                    boolean isSelected = multiSelectManager.isSelected(flatPosition);
                    updateViewSelectionState(childView, isSelected);
                }
            }
        }
    }

    /**
     * 将扁平位置转换为组和子位置
     *
     * @param flatPosition 扁平位置（只计算子项目）
     * @return [groupPosition, childPosition] 或 null（如果位置无效）
     */
    private int[] convertFlatPositionToGroupChild(int flatPosition) {
        int currentPos = 0;

        for (int groupPos = 0; groupPos < getGroupCount(); groupPos++) {
            int childCount = getChildrenCount(groupPos);

            if (flatPosition < currentPos + childCount) {
                return new int[]{groupPos, flatPosition - currentPos};
            }

            currentPos += childCount;
        }

        return null; // 位置无效
    }

    /**
     * 更新视图的选中状态
     *
     * @param view       要更新的视图
     * @param isSelected 是否选中
     */
    private void updateViewSelectionState(View view, boolean isSelected) {
        if (view == null) return;

        // 更新背景色
        if (isSelected) {
            view.setBackgroundColor(getSelectionBackgroundColor());
        } else {
            view.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    public void onTranslate(boolean enable_llm) {
        if (context instanceof SectionEditorActivity) {
            ((SectionEditorActivity) context).setModified(true);
        }
        // 显示加载对话框
        DialogUtils dialogUtils = new DialogUtils(context);
        AlertDialog dialog = dialogUtils.createLoadingDialog(res.getString(R.string.section_act_loading_dialog_message, 0, 0));
        dialog.show();
        int[] totalTasks = {0};

        // 第一次遍历：统计任务数
        for (SectionModel group : groups) {
            for (SectionModel.Pair item : group.items()) {
                if (multiSelectManager == null || !multiSelectManager.isMultiSelectMode() || multiSelectManager.isSelected(getPosition(item))) {
                    totalTasks[0]++;
                }
            }
        }
        TextView progressTextView = dialog.findViewById(R.id.mainactivityloadingTextView);
        assert progressTextView != null;
        progressTextView.setText(res.getString(R.string.section_act_loading_dialog_message, 0, totalTasks[0]));
        if (totalTasks[0] == 0) {
            dialog.dismiss();
            if (multiSelectManager != null && multiSelectManager.isMultiSelectMode()) exitMultiSelectMode();
            return;
        }
        // 原子计数器保证线程安全
        AtomicInteger completedTasks = new AtomicInteger(0);

        for (SectionModel group : groups) {
            for (SectionModel.Pair item : group.items()) {
                if (multiSelectManager == null || !multiSelectManager.isMultiSelectMode() || multiSelectManager.isSelected(getPosition(item))) {

                    TemplatePlaceholderProcessor.Payload payload = TemplatePlaceholderProcessor.mask(item.getOri_val());
                    List<String> placeholders = payload.placeholders();
                    String textForTranslation = payload.maskedText();

                    var translateCallBack = new Translator.TranslateCallBack() {
                        @Override
                        public void onSuccess(
                                String translation, String sourceLanguage, String targetLanguage) {
                            String finalTranslation = TemplatePlaceholderProcessor.restore(translation, placeholders);

                            if (!AppSettings.getIsOverride()) {
                                var lang_pairs = item.getLang_pairs();
                                lang_pairs.put(targetLanguage, finalTranslation);
                            } else {
                                item.setOri_val(finalTranslation);
                            }
                            updateProgress(completedTasks, totalTasks[0], progressTextView, dialog);
                        }

                        @Override
                        public void onError(Throwable t) {

                            Timber.e(t);
                            new Handler(context.getMainLooper()).post(() -> Toast.makeText(context, t.toString(), Toast.LENGTH_SHORT).show());
                            updateProgress(completedTasks, totalTasks[0], progressTextView, dialog);
                        }
                    };
                    if (enable_llm) {
                        Translator.LLM_translate(textForTranslation,
                                AppSettings.getCurrentFromLanguageCode(),
                                AppSettings.getCurrentTargetLanguageCode(),
                                translateCallBack);
                    } else {
                        Translator.translate(
                                textForTranslation,
                                translateCallBack);
                    }
                }
            }
        }


    }

    private void updateProgress(AtomicInteger counter, int total, TextView tv, AlertDialog dialog) {
        int current = counter.incrementAndGet();
        handler.post(
                () -> {
                    tv.setText(res.getString(R.string.section_act_loading_dialog_message, current, total));
                    if (current == total) {
                        dialog.dismiss();
                        if (multiSelectManager.isMultiSelectMode()) exitMultiSelectMode();
                    }
                });
    }

    public void setOnItemClickListener(onItemClickListener listener) {
        this.clickListener = listener;
    }

    // RotateMultiselectButton 功能已移至 UniversalMultiSelectManager

    public Set<Integer> getSelectedItems() {
        if (multiSelectManager != null) {
            return multiSelectManager.getSelectedPositions();
        }
        return new HashSet<>();
    }

    public boolean isMultiSelectMode() {
        return multiSelectManager != null && multiSelectManager.isMultiSelectMode();
    }

    public SectionEditorAdapter getAdapter() {
        return this;
    }

    public Context getContext() {
        return this.context;
    }

    public List<SectionModel> getGroups() {
        return this.groups;
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

    // 装饰器负责管理多选模式下的视觉效果
    public class SectionEditorDecorator {
        private final Set<Integer> selectedPositions = new HashSet<>();

        public void toggleSelection(int position) {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position);
            } else {
                selectedPositions.add(position);
            }
        }

        public void clearSelections() {
            selectedPositions.clear();
        }

        public void applyDecoration(View view, int position) {
            if (selectedPositions.contains(position)) {
                view.setBackgroundColor(getSelectionBackgroundColor()); // 根据主题设置背景色
            } else {
                view.setBackgroundColor(Color.TRANSPARENT); // 非选中项背景
            }
        }
    }

    public interface onItemClickListener {
        void onItemClick(int parentPosition, int childposition);
    }

    static class GroupViewHolder {
        TextView nameTextView;
        ImageView groupIndicator;
    }

    static class ItemViewHolder {
        TextView tvKey;
        TextView tvSrc;
    }
}
