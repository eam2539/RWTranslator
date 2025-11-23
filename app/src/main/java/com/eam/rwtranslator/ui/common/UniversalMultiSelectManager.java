package com.eam.rwtranslator.ui.common;

import android.annotation.SuppressLint;
import android.view.View;
import androidx.annotation.Nullable;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import android.os.Handler;
import android.os.Looper;

/**
 * 通用多选管理器核心类
 * 支持RecyclerView和ExpandableListView等不同类型的适配器
 * 通过批量更新缓存等机制减少不必要的UI更新，提高性能
 */
public class UniversalMultiSelectManager {
    
    private MultiSelectAdapter adapter;
    private MultiSelectDecorator decorator;
    private MaterialToolbar toolbar;
    private View multiSelectButton;
    private OnMultiSelectListener listener;
    
    private boolean isMultiSelectMode = false;
    
    // 性能优化：批量更新缓存
    private final Set<Integer> pendingUpdates = new HashSet<>();
    private boolean isBatchUpdateMode = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingUpdateRunnable;

    // 状态变化监听器
    private OnSelectionChangedListener selectionChangedListener;
    
    /**
     * 选中状态变化监听器接口
     */
    public interface OnSelectionChangedListener {
        /**
         * 选中状态发生变化时调用
         * @param selectedCount 当前选中的项目数量
         * @param isItemSelected 当前操作是否为选中（true为选中，false为取消选中）
         */
        void onSelectionChanged(int selectedCount, boolean isItemSelected);
    }
    
    /**
     * 调度批量更新
     * @param position 需要更新的位置
     */
    private void scheduleBatchUpdate(int position) {
        pendingUpdates.add(position);
        
        // 取消之前的更新任务
        if (pendingUpdateRunnable != null) {
            uiHandler.removeCallbacks(pendingUpdateRunnable);
        }
        
        // 创建新的更新任务
        pendingUpdateRunnable = () -> {
            if (adapter != null && !pendingUpdates.isEmpty()) {
                adapter.notifyItemsSelectionChanged(new HashSet<>(pendingUpdates));
                pendingUpdates.clear();
            }
        };
        
        // 延迟执行批量更新
        uiHandler.postDelayed(pendingUpdateRunnable, MultiSelectPerformanceConfig.BATCH_UPDATE_DELAY_MS);
    }
    
    /**
     * 标准全选实现
     *
     */
    @SuppressLint("SuspiciousIndentation")
    public void selectAll() {
        if (!isMultiSelectMode) {
            enterMultiSelectMode();
        }

        Set<Integer> newSelections = new HashSet<>();
        
        for (int i = 0; i < adapter.getTotalItemCount(); i++) {
            if(!decorator.isSelected(i)){
                newSelections.add(i);
            }

        }
        for (Integer position : newSelections) {
            decorator.setSelection(position, true);
        }
            updateSubtitle();
            adapter.notifyItemsSelectionChanged(newSelections);

    }
    

    
    /**
     * 反选实现
     *
     */
    public void inverseSelection() {
        if (!isMultiSelectMode) {
            enterMultiSelectMode();
        }
        for (int i = 0; i < adapter.getTotalItemCount(); i++) {
            toggleItemSelection(i);
        }
        adapter.notifyAllSelectionChanged();
        if(decorator.getSelectedCount()==0)exitMultiSelectMode();
    }
    /**
     * 默认构造函数
     */
    public UniversalMultiSelectManager() {
        // 空构造函数，稍后通过setter设置适配器
    }
    
    /**
     * 设置适配器
     * @param adapter 多选适配器接口
     */
    public void setAdapter(MultiSelectAdapter adapter) {
        this.adapter = adapter;
    }
    
    /**
     * 设置装饰器
     * @param decorator 多选装饰器
     */
    public void setDecorator(MultiSelectDecorator decorator) {
        this.decorator = decorator;
    }
    
    /**
     * 设置工具栏
     * @param toolbar 材料工具栏
     */
    public void setToolbar(MaterialToolbar toolbar) {
        this.toolbar = toolbar;
    }
    
    /**
     * 设置多选按钮
     * @param multiSelectButton 多选按钮视图
     */
    public void setMultiSelectButton(View multiSelectButton) {
        this.multiSelectButton = multiSelectButton;
        // 设置多选按钮点击事件
        if (multiSelectButton != null) {
            multiSelectButton.setOnClickListener(v -> toggleMultiSelectMode());
        }
    }
    
    /**
     * 设置多选监听器
     * @param listener 多选监听器
     */
    public void setOnMultiSelectListener(OnMultiSelectListener listener) {
        this.listener = listener;
    }
    
    /**
     * 设置选中状态变化监听器
     * @param listener 监听器
     */
    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }
    
    public UniversalMultiSelectManager(MultiSelectAdapter adapter,
                                       MultiSelectDecorator decorator,
                                       MaterialToolbar toolbar,
                                       @Nullable View multiSelectButton,
                                       @Nullable OnMultiSelectListener listener) {
        this.adapter = adapter;
        this.decorator = decorator;
        this.toolbar = toolbar;
        this.multiSelectButton = multiSelectButton;
        this.listener = listener;
        
        // 设置多选按钮点击事件
        if (multiSelectButton != null) {
            multiSelectButton.setOnClickListener(v -> toggleMultiSelectMode());
        }
    }
    
    /**
     * 处理项目点击
     * @param position 点击的位置
     * @return 是否处理了点击事件
     */
    public boolean handleItemClick(int position) {
        if (isMultiSelectMode) {
            toggleItemSelection(position);
            return true;
        }
        return false;
    }
    
    /**
     * 处理项目长按
     * @param position 长按的位置
     * @return 是否处理了长按事件
     */
    public boolean handleItemLongClick(int position) {
        if (!isMultiSelectMode) {
            enterMultiSelectMode();
            toggleItemSelection(position);
            return true;
        }
        return false;
    }
    
    /**
     * 切换指定位置的选中状态
     * @param position 位置
     */
    public void toggleItemSelection(int position) {

        boolean wasSelected = decorator.isSelected(position);
        decorator.toggleSelection(position);
        updateSubtitle();
        
        // 通知状态变化
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(decorator.getSelectedCount(), !wasSelected);
        }
        
        // 高性能局部更新
        if (!isBatchUpdateMode) {
            if (adapter != null) {
                adapter.notifyItemSelectionChanged(position);
            }
        } else {
            pendingUpdates.add(position);
        }
        
        // 如果没有选中项，退出多选模式
        if (decorator.getSelectedCount() == 0) {
            exitMultiSelectMode();
        }
    }
    
    /**
     * 开始批量更新模式
     */
    public void beginBatchUpdate() {
        isBatchUpdateMode = true;
        pendingUpdates.clear();
    }
    
    /**
     * 结束批量更新模式并应用所有更新
     */
    public void endBatchUpdate() {
        if (isBatchUpdateMode && !pendingUpdates.isEmpty() && adapter != null) {
            adapter.notifyItemsSelectionChanged(new HashSet<>(pendingUpdates));
            pendingUpdates.clear();
        }
        isBatchUpdateMode = false;
    }
    


    /**
     * 进入多选模式
     */
    public void enterMultiSelectMode() {
        if (adapter == null) {
            throw new IllegalStateException("Adapter not initialized. Call setAdapter() first.");
        }
        
        if (!isMultiSelectMode) {
            isMultiSelectMode = true;
            rotateMultiSelectButton(true);
            updateSubtitle();
            adapter.notifyEnterMultiSelectMode();
            
            if (listener != null) {
                listener.onEnterMultiSelectMode();
            }
        }
    }
    
    /**
     * 退出多选模式
     */
    public void exitMultiSelectMode() {
        if (adapter == null || decorator == null) {
            throw new IllegalStateException("Adapter and Decorator must be initialized. Call setAdapter() and setDecorator() first.");
        }
        
        if (isMultiSelectMode) {
            isMultiSelectMode = false;
            Set<Integer> previouslySelected = decorator.getSelectedPositions();
            decorator.clearSelections();
            rotateMultiSelectButton(false);
            updateSubtitle();
            if (adapter != null && previouslySelected != null && !previouslySelected.isEmpty()) {
                adapter.notifyItemsSelectionChanged(previouslySelected);
            }
            adapter.notifyExitMultiSelectMode();
            
            if (listener != null) {
                listener.onExitMultiSelectMode();
            }
        }
    }
    
    /**
     * 切换多选模式
     */
    public void toggleMultiSelectMode() {
        if (isMultiSelectMode) {
            exitMultiSelectMode();
        } else {
            enterMultiSelectMode();
        }
    }
    
    /**
     * 旋转多选按钮
     * @param toMultiSelectMode 是否进入多选模式
     */
    public void rotateMultiSelectButton(boolean toMultiSelectMode) {
        if (multiSelectButton != null) {
            View iconView = multiSelectButton.findViewById(com.eam.rwtranslator.R.id.imageview_multi_select);
            if (iconView != null) {
                iconView.setRotation(toMultiSelectMode ? 45f : 0f);
            }
        }
    }
    
    /**
     * 更新副标题
     */
    public void updateSubtitle() {
        if (toolbar != null && decorator != null && adapter != null) {
            int selectedCount = decorator.getSelectedCount();
            int totalCount = adapter.getTotalItemCount();
            toolbar.setSubtitle(selectedCount + "/" + totalCount);
        }
    }
    
    // Getter方法
    public boolean isMultiSelectMode() {
        return isMultiSelectMode;
    }
    
    public boolean isInMultiSelectMode() {
        return isMultiSelectMode;
    }
    
    /**
     * 获取选中的位置集合
     * @return 选中位置的集合
     */
    public Set<Integer> getSelectedPositions() {
        if (decorator == null) {
            return new HashSet<>();
        }
        return decorator.getSelectedPositions();
    }
    
    /**
     * 获取选中项目的数量
     * @return 选中项目数量
     */
    public int getSelectedCount() {
        if (decorator == null) {
            return 0;
        }
        return decorator.getSelectedCount();
    }
    
    /**
     * 检查指定位置是否被选中
     * @param position 位置
     * @return 是否被选中
     */
    public boolean isSelected(int position) {
        if (decorator == null) {
            return false;
        }
        return decorator.isSelected(position);
    }
    
    /**
     * 批量检查选中状态，提高大数据集性能
     * @param positions 要检查的位置集合
     * @return 位置到选中状态的映射
     */
    public Map<Integer, Boolean> getSelectionStates(Set<Integer> positions) {
        Map<Integer, Boolean> states = new HashMap<>();
        if (decorator == null) {
            for (Integer position : positions) {
                states.put(position, false);
            }
            return states;
        }
        
        Set<Integer> selectedPositions = decorator.getSelectedPositions();
        for (Integer position : positions) {
            states.put(position, selectedPositions.contains(position));
        }
        return states;
    }
    
    public MultiSelectDecorator getDecorator() {
        return decorator;
    }
    
    /**
     * 多选监听器接口
     */
    public interface OnMultiSelectListener {
        /**
         * 进入多选模式时调用
         */
        void onEnterMultiSelectMode();
        
        /**
         * 退出多选模式时调用
         */
        void onExitMultiSelectMode();
    }
}