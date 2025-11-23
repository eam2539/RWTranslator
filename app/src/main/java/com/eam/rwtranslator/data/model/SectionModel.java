package com.eam.rwtranslator.data.model;

import com.eam.rwtranslator.utils.TranslationKeys;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * INI文件分组数据模型，包含分组名和条目列表。
 *
 * @param name  分组名
 * @param items 条目列表
 */
public record SectionModel(String name, List<Pair> items) {

    /**
     * 向分组添加条目
     *
     * @param key 键
     * @param src 原始值
     */
    public void addItem(TranslationKeys key, String src) {
        items.add(new Pair(key, src));
    }

    @Override
    public @NotNull String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SectionModel{name='").append(name).append("', items=[");
        for (int i = 0; i < items.size(); i++) {
            sb.append(items.get(i));
            if (i < items.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * 条目数据结构，包含键、原始值和多语言翻译。
     */

    public static class Pair {
        // 键
        TranslationKeys key;
        // 原始值
        String ori_val;
        // 多语言翻译对
        Map<String, String> lang_pairs;

        public Pair() {
        }

        public Pair(TranslationKeys key, String ori_val) {
            this.key = key;
            this.ori_val = ori_val;
        }

        public TranslationKeys getKey() {
            return this.key;
        }

        public void setKey(TranslationKeys key) {
            this.key = key;
        }

        public String getOri_val() {
            return this.ori_val;
        }

        public void setOri_val(String ori_val) {
            this.ori_val = ori_val;
        }

        public Map<String, String> getLang_pairs() {
            if (lang_pairs == null) {
                lang_pairs = new HashMap<>();
            }
            return this.lang_pairs;
        }

        public void setLang_pairs(Map<String, String> lang_pairs) {
            this.lang_pairs = lang_pairs;
        }

        /**
         * 获取指定索引的语言对（如用于遍历）
         */
        public Map.Entry<String, String> getLangEntryByIndex(int index) {
            if (lang_pairs == null || lang_pairs.isEmpty() || index < 0 || index >= lang_pairs.size()) {
                return null;
            }
            Iterator<Map.Entry<String, String>> iterator = lang_pairs.entrySet().iterator();
            for (int i = 0; i < index; i++) {
                iterator.next();
            }
            return iterator.next();
        }

        @Override
        public @NotNull String toString() {
            return "pairs{" + "key=" + key + ", ori_val='" + ori_val + '\'' + '}';
        }
    }
}
