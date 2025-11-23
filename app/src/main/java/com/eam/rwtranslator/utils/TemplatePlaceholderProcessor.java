package com.eam.rwtranslator.utils;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * Utility to protect ${var} placeholders during translation.
 */
public final class TemplatePlaceholderProcessor {
    private  static  final String PLACEHOLDER_MASK = "\uE000";
    private static final Pattern PRIVATE_UNICODE = Pattern.compile(Pattern.quote(PLACEHOLDER_MASK));
    private TemplatePlaceholderProcessor() {
    }
    @Contract("null -> new")
    public static @NotNull Payload mask(String text) {
        if (text == null || text.isEmpty()) {
            return new Payload(text, Collections.emptyList());
        }

        List<String> placeholders = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();
        Deque<Character> typeStack = new ArrayDeque<>();
        int lastIndex=0;
        StringBuilder maskedText = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);

            // 检查是否是 ${ 或 %{
            if (i + 1 < text.length() && (current == '$' || current == '%') && text.charAt(i + 1) == '{') {
                stack.push(i); // 记录起始位置
                typeStack.push(current); // 记录类型
                i++; // 跳过下一个字符 '{'
                continue;
            }

            if (current == '}' && !stack.isEmpty()) {
                int start = stack.pop();
                char type = typeStack.pop();

                // 如果栈为空，说明这是最外层的括号
                if (stack.isEmpty()) {
                    String content = text.substring(start, i + 1);
                    placeholders.add(content);

                    // 将前面的文本添加到结果中
                    maskedText.append(text, lastIndex, start);
                    // 添加掩码
                    maskedText.append(PLACEHOLDER_MASK);
                    lastIndex = i + 1;

                    Timber.d("Masked placeholder: %s", content);
                }
            }
        }

        // 添加剩余文本
        if (lastIndex < text.length()) {
            maskedText.append(text, lastIndex, text.length());
        }

        Timber.d("Total placeholders masked: %d", placeholders.size());
        Timber.d("maskText: %s", maskedText.toString());

        return new Payload(maskedText.toString(), placeholders);
    }


    public static String restore(String translatedText, List<String> placeholders) {
        if (translatedText == null || translatedText.isEmpty() || placeholders == null || placeholders.isEmpty()) {
            return translatedText;
        }
        Matcher matcher = PRIVATE_UNICODE.matcher(translatedText);
        StringBuffer buffer = new StringBuffer();
        int index = 0;
        while (matcher.find() && index < placeholders.size()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(placeholders.get(index++)));
            Timber.d("Restored placeholder: %s", placeholders.get(index - 1));
        }
        matcher.appendTail(buffer);
        Timber.d("restoreText: %s", buffer.toString());
        return buffer.toString();
    }

    public static final class Payload {
        private final String maskedText;
        private final List<String> placeholders;

        private Payload(String maskedText, List<String> placeholders) {
            this.maskedText = maskedText;
            this.placeholders = placeholders;
        }

        public String maskedText() {
            return maskedText;
        }

        public List<String> placeholders() {
            return placeholders;
        }

        public boolean hasPlaceholders() {
            return !placeholders.isEmpty();
        }
    }
}
