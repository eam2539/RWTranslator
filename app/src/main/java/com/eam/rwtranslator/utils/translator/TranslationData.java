package com.eam.rwtranslator.utils.translator;

import androidx.annotation.Keep;
import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * JSON格式的翻译数据结构
 * 用于OpenAI翻译器的批量翻译请求和响应
 */
@Keep
public class TranslationData {
    
    /**
     * 翻译请求的根对象
     */
    @Keep
    public static class TranslationRequest {
        @SerializedName("texts")
        public List<TextItem> texts;
        
        public TranslationRequest(List<TextItem> texts) {
            this.texts = texts;
        }
    }
    
    /**
     * 单个文本项
     */
    @Keep
    public static class TextItem {
        @SerializedName("index")
        public int index;
        
        @SerializedName("text")
        public String text;
        
        public TextItem(int index, String text) {
            this.index = index;
            this.text = text;
        }
    }
    
    /**
     * 翻译响应的根对象
     */
    @Keep
    public static class TranslationResponse {
        @SerializedName("translations")
        public List<TranslationItem> translations;
        
        public TranslationResponse() {
        }
    }
    
    /**
     * 单个翻译结果项
     */
    @Keep
    public static class TranslationItem {
        @SerializedName("index")
        public int index;
        
        @SerializedName("translation")
        public String translation;
        
        public TranslationItem() {
        }
    }
}
