package com.eam.rwtranslator.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * 多语言翻译字段枚举，定义所有支持的翻译键名。
 */
public enum TranslationKeys {
    DISPLAY_TEXT("displayText"),
    DISPLAY_DESCRIPTION("displayDescription"),
    TEXT("text"),
    DESCRIPTION("description"),
    IS_LOCKED_MESSAGE("isLockedMessage"),
    IS_LOCKED_ALT_MESSAGE("isLockedAltMessage"),
    IS_LOCKED_ALT2_MESSAGE("isLockedAlt2Message"),
    SHOW_MESSAGE_TO_PLAYERS("showMessageToPlayer"),
    SHOW_MESSAGE_TO_ALL_PLAYERS("showMessageToAllPlayer"),
    SHOW_MESSAGE_TO_ALL_ENEMY_PLAYERS("showMessageToAllEnemyPlayers"),
    CANNOT_PLACE_MESSAGE("cannotPlaceMessage"),
    SHOW_QUICK_WAR_LOG_TO_PLAYER("showQuickWarLogToPlayer"),
    SHOW_QUICK_WAR_LOG_TO_ALL_PLAYERS("showQuickWarLogToAllPlayers"),
    DISPLAY_NAME("displayName"),
    DISPLAY_NAME_SHORT("displayNameShort");
    
    private final String keyName;

    TranslationKeys(String key) {
        this.keyName = key;
    }

    public String getKeyName() {
        return this.keyName;
    }
    public static final Map<String, String> LANGUAGE_CODE_TO_ENGLISH_NAME= new HashMap<>();
    public static final Map<String, String> ENGLISH_NAME_TO_LANGUAGE_CODE= new HashMap<>();
    static {
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("en", "English");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("zh", "Simplified Chinese");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("zh-tw", "Traditional Chinese");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("ru", "Russian");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("ja", "Japanese");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("de", "German");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("es", "Spanish");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("fr", "French");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("pt", "Portuguese");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("it", "Italian");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("nl", "Dutch");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("tr", "Turkish");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("pl", "Polish");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("uk", "Ukrainian");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("ar", "Arabic");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("bg", "Bulgarian");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("ca", "Catalan");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("cs", "Czech");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("da", "Danish");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("el", "Greek");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("et", "Estonian");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("fi", "Finnish");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("he", "Hebrew");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("hi", "Hindi");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("hr", "Croatian");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("hu", "Hungarian");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("id", "Indonesian");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("is", "Icelandic");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("ko", "Korean");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("lt", "Lithuanian");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("lv", "Latvian");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("ms", "Malay");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("mt", "Maltese");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("no", "Norwegian");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("ro", "Romanian");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("sk", "Slovak");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("sl", "Slovenian");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("sv", "Swedish");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("th", "Thai");
        LANGUAGE_CODE_TO_ENGLISH_NAME.put("vi", "Vietnamese");
        ENGLISH_NAME_TO_LANGUAGE_CODE.putAll(
                LANGUAGE_CODE_TO_ENGLISH_NAME.entrySet().stream()
                        .collect(HashMap::new, (m, e) -> m.put(e.getValue(), e.getKey()), HashMap::putAll)
        );
    }
}