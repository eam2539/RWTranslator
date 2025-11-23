package com.eam.rwtranslator.utils.ini;

import org.ini4j.Profile;
import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class IniDocument {
    private final File file;
    private final List<Segment> segments;
    private final String newline;
    private final boolean endsWithNewline;

    private IniDocument(File file, List<Segment> segments, String newline, boolean endsWithNewline) {
        this.file = file;
        this.segments = segments;
        this.newline = newline;
        this.endsWithNewline = endsWithNewline;
    }

    static IniDocument parse(File file) throws IOException {
        String content = file.exists()
            ? readUtf8File(file)
            : "";
        String newline = detectLineSeparator(content);
        boolean endsWithNewline = content.endsWith("\n");
        List<String> lines = splitLines(content);
        List<Segment> segments = new ArrayList<>();
        String currentSection = "";
        PendingTriple pending = null;
        int placeholderIndex = 0;

        for (String line : lines) {
            if (pending != null) {
                pending.consumeNextLine(line);
                if (pending.isClosed()) {
                    segments.add(pending.toSegment());
                    pending = null;
                }
                continue;
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) {
                segments.add(new RawSegment(line));
                continue;
            }

            if (trimmed.startsWith("[") && trimmed.contains("]")) {
                currentSection = trimmed.substring(1, trimmed.indexOf(']')).trim();
                segments.add(new SectionSegment(line, currentSection));
                continue;
            }

            int delimiter = findDelimiter(line);
            if (delimiter < 0) {
                if (looksLikeBareKey(trimmed)) {
                    segments.add(new BareEntrySegment(currentSection, trimmed, line));
                } else {
                    segments.add(new RawSegment(line));
                }
                continue;
            }

            String key = line.substring(0, delimiter).trim();
            if (key.isEmpty()) {
                segments.add(new RawSegment(line));
                continue;
            }

            EntryParseResult parseResult = parseEntryLine(line, delimiter, currentSection, key, newline,
                    buildPlaceholder(placeholderIndex));
            if (parseResult.entrySegment != null) {
                segments.add(parseResult.entrySegment);
                if (parseResult.entrySegment.style == ValueStyle.TRIPLE_QUOTED) {
                    placeholderIndex++;
                }
            } else if (parseResult.pendingTriple != null) {
                pending = parseResult.pendingTriple;
                placeholderIndex++;
            } else {
                segments.add(new RawSegment(line));
            }
        }

        if (pending != null) {
            segments.add(pending.forceClose());
        }

        return new IniDocument(file, segments, newline, endsWithNewline);
    }

    String toSanitizedContent() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            builder.append(segments.get(i).sanitizedText());
            if (i < segments.size() - 1 || endsWithNewline) {
                builder.append(newline);
            }
        }
        return builder.toString();
    }

    void restoreRawValues(Wini wini) {
        if (wini == null) {
            return;
        }
        for (Segment segment : segments) {
            if (segment instanceof EntrySegment entry && entry.style == ValueStyle.TRIPLE_QUOTED) {
                Profile.Section section = wini.get(entry.section);
                if (section == null) {
                    continue;
                }
                String current = section.get(entry.key);
                if (current != null && current.contains(entry.placeholder)) {
                    section.put(entry.key, current.replace(entry.placeholder, entry.tripleContent));
                }
            }
        }
    }

    void write(Wini wini) throws IOException {
        Map<String, LinkedHashMap<String, String>> snapshot = snapshotValues(wini);
        List<String> outputLines = new ArrayList<>();
        Set<String> seenSections = new LinkedHashSet<>();
        String activeSection = null;

        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            outputLines.add(segment.render(snapshot));
            if (segment instanceof SectionSegment sectionSegment) {
                activeSection = sectionSegment.sectionName;
                seenSections.add(activeSection);
            } else if (segment instanceof EntrySegment entrySegment) {
                activeSection = entrySegment.section;
                seenSections.add(activeSection);
            } else if (segment instanceof BareEntrySegment bareEntrySegment) {
                activeSection = bareEntrySegment.section;
                seenSections.add(activeSection);
            }

            if (shouldFlushSection(activeSection, i, snapshot)) {
                outputLines.addAll(renderRemainingEntries(activeSection, snapshot));
            }
        }

        outputLines.addAll(renderRemainingSections(snapshot, seenSections));

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < outputLines.size(); i++) {
            builder.append(outputLines.get(i));
            if (i < outputLines.size() - 1 || endsWithNewline) {
                builder.append(newline);
            }
        }
        writeUtf8File(file, builder.toString());
    }

    private static String readUtf8File(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeUtf8File(File file, String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static String detectLineSeparator(String content) {
        int index = content.indexOf('\n');
        if (index > 0 && content.charAt(index - 1) == '\r') {
            return "\r\n";
        }
        return "\n";
    }

    private static List<String> splitLines(String content) {
        List<String> result = new ArrayList<>();
        if (content.isEmpty()) {
            return result;
        }
        int lineStart = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n') {
                int end = (i > 0 && content.charAt(i - 1) == '\r') ? i - 1 : i;
                result.add(content.substring(lineStart, end));
                lineStart = i + 1;
            }
        }
        if (lineStart < content.length()) {
            result.add(content.substring(lineStart));
        }
        return result;
    }

    private static int findDelimiter(String line) {
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            }
            if (!inQuotes && (c == '=' || c == ':')) {
                return i;
            }
        }
        return -1;
    }

    private static EntryParseResult parseEntryLine(String line, int delimiter, String section, String key,
                                                   String newline, String placeholder) {
        String afterDelimiter = line.substring(delimiter + 1);
        int valueStart = firstNonWhitespaceIndex(afterDelimiter);
        int prefixEnd = delimiter + 1 + (valueStart >= 0 ? valueStart : afterDelimiter.length());
        String beforeValue = line.substring(0, prefixEnd);
        String remainder = valueStart >= 0 ? afterDelimiter.substring(valueStart) : "";
        if (remainder.startsWith("\"\"\"")) {
            return handleTriple(line, section, key, beforeValue, remainder.substring(3), newline, placeholder, "");
        }

        int inlineTripleIndex = findInlineTripleQuote(remainder);
        if (inlineTripleIndex >= 0) {
            String prefix = remainder.substring(0, inlineTripleIndex);
            return handleTriple(line, section, key, beforeValue,
                    remainder.substring(inlineTripleIndex + 3), newline, placeholder, prefix);
        }

        ValueSplit split = splitValueAndComment(remainder);
        ValueStyle style = determineStyle(split.valueToken);
        EntrySegment segment = new EntrySegment(section, key, beforeValue, split.suffix,
            line, style, placeholder, "", split.valueToken, "");
        return new EntryParseResult(segment, null);
    }

    private static EntryParseResult handleTriple(String originalLine, String section, String key,
                                                 String beforeValue, String afterStart, String newline,
                                                 String placeholder, String inlinePrefix) {
        int closingIndex = afterStart.indexOf("\"\"\"");
        if (closingIndex >= 0) {
            String content = afterStart.substring(0, closingIndex);
            String suffix = afterStart.substring(closingIndex + 3);
                EntrySegment segment = new EntrySegment(section, key, beforeValue, suffix,
                    originalLine, ValueStyle.TRIPLE_QUOTED, placeholder, content, null, inlinePrefix);
            return new EntryParseResult(segment, null);
        }
        PendingTriple pending = new PendingTriple(section, key, beforeValue, inlinePrefix,
                newline, placeholder, originalLine);
        pending.appendInitial(afterStart);
        return new EntryParseResult(null, pending);
    }

    private static ValueSplit splitValueAndComment(String remainder) {
        boolean inQuotes = false;
        for (int i = 0; i < remainder.length(); i++) {
            char c = remainder.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            }
            if (!inQuotes && c == ';') {
                int beforeCommentEnd = trimTrailingWhitespaceIndex(remainder, i);
                String valueToken = remainder.substring(0, beforeCommentEnd);
                String suffix = remainder.substring(beforeCommentEnd, i) + remainder.substring(i);
                return new ValueSplit(valueToken, suffix);
            }
        }
        int trimmedEnd = trimTrailingWhitespaceIndex(remainder, remainder.length());
        String valueToken = remainder.substring(0, trimmedEnd);
        String suffix = remainder.substring(trimmedEnd);
        return new ValueSplit(valueToken, suffix);
    }

    private static int trimTrailingWhitespaceIndex(String value, int limit) {
        int end = limit;
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return end;
    }

    private static ValueStyle determineStyle(String token) {
        String trimmed = token.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return ValueStyle.DOUBLE_QUOTED;
        }
        return ValueStyle.UNQUOTED;
    }

    private static int findInlineTripleQuote(String text) {
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                if (!inQuotes && hasTripleAt(text, i)) {
                    return i;
                }
                inQuotes = !inQuotes;
            }
        }
        return -1;
    }

    private static boolean hasTripleAt(String text, int index) {
        return index + 2 < text.length()
                && text.charAt(index) == '"'
                && text.charAt(index + 1) == '"'
                && text.charAt(index + 2) == '"';
    }

    private static int firstNonWhitespaceIndex(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String buildPlaceholder(int index) {
        return "__RW_RAW_" + index + "__";
    }

    private static boolean looksLikeBareKey(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '=' || c == ':') {
                return false;
            }
        }
        return true;
    }

    private static Map<String, LinkedHashMap<String, String>> snapshotValues(Wini wini) {
        Map<String, LinkedHashMap<String, String>> snapshot = new LinkedHashMap<>();
        if (wini == null) {
            return snapshot;
        }
        for (String sectionName : wini.keySet()) {
            Profile.Section section = wini.get(sectionName);
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            if (section != null) {
                for (String key : section.keySet()) {
                    values.put(key, section.get(key));
                }
            }
            snapshot.put(sectionName, values);
        }
        return snapshot;
    }

    private boolean shouldFlushSection(String section, int currentIndex,
                                       Map<String, LinkedHashMap<String, String>> remaining) {
        if (section == null || section.isEmpty()) {
            return false;
        }
        LinkedHashMap<String, String> values = remaining.get(section);
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (int i = currentIndex + 1; i < segments.size(); i++) {
            Segment next = segments.get(i);
            if (next instanceof SectionSegment sectionSegment) {
                return !section.equals(sectionSegment.sectionName);
            }
            if (next instanceof EntrySegment entrySegment) {
                return !section.equals(entrySegment.section);
            }
            if (next instanceof BareEntrySegment bareEntrySegment) {
                return !section.equals(bareEntrySegment.section);
            }
        }
        return true;
    }

    private static List<String> renderRemainingEntries(String section,
                                                       Map<String, LinkedHashMap<String, String>> remaining) {
        List<String> lines = new ArrayList<>();
        LinkedHashMap<String, String> values = remaining.get(section);
        if (values == null) {
            return lines;
        }
        List<String> keys = new ArrayList<>(values.keySet());
        for (String key : keys) {
            String value = values.remove(key);
            lines.add(defaultEntryLine(key, value));
        }
        return lines;
    }

    private static List<String> renderRemainingSections(Map<String, LinkedHashMap<String, String>> remaining,
                                                        Set<String> seenSections) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, LinkedHashMap<String, String>> entry : remaining.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            if (!entry.getKey().isEmpty()) {
                boolean alreadySeen = seenSections.contains(entry.getKey());
                if (!lines.isEmpty() || alreadySeen) {
                    lines.add("");
                }
                lines.add("[" + entry.getKey() + "]");
            }
            List<String> keys = new ArrayList<>(entry.getValue().keySet());
            for (String key : keys) {
                String value = entry.getValue().remove(key);
                lines.add(defaultEntryLine(key, value));
            }
        }
        return lines;
    }

    private static String removeSnapshotValue(Map<String, LinkedHashMap<String, String>> values,
                                              String section, String key) {
        String sectionKey = (section == null) ? "" : section;
        LinkedHashMap<String, String> sectionValues = values.get(sectionKey);
        String removed = sectionValues != null ? sectionValues.remove(key) : null;
        if (removed == null && !sectionKey.isEmpty()) {
            LinkedHashMap<String, String> globalValues = values.get("");
            if (globalValues != null) {
                removed = globalValues.remove(key);
            }
        }
        return removed;
    }

    private static String defaultEntryLine(String key, String value) {
        if (value == null) {
            value = "";
        }
        if (value.contains("\n") || value.contains("\r")) {
            return key + " = \"\"\"" + value + "\"\"\"";
        }
        boolean needQuotes = value.isEmpty()
            || Character.isWhitespace(value.charAt(0))
            || Character.isWhitespace(value.charAt(value.length() - 1))
                || value.contains(";") || value.contains("#");
        if (needQuotes) {
            return key + " = \"" + escapeQuotes(value) + "\"";
        }
        return key + " = " + value;
    }

    private static String escapeQuotes(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private sealed interface Segment permits RawSegment, SectionSegment, EntrySegment, BareEntrySegment {
        String sanitizedText();

        String render(Map<String, LinkedHashMap<String, String>> values);
    }

    private static final class RawSegment implements Segment {
        private final String line;

        private RawSegment(String line) {
            this.line = line;
        }

        @Override
        public String sanitizedText() {
            return line;
        }

        @Override
        public String render(Map<String, LinkedHashMap<String, String>> values) {
            return line;
        }
    }

    private static final class SectionSegment implements Segment {
        private final String line;
        private final String sectionName;

        private SectionSegment(String line, String sectionName) {
            this.line = line;
            this.sectionName = sectionName;
        }

        @Override
        public String sanitizedText() {
            return line;
        }

        @Override
        public String render(Map<String, LinkedHashMap<String, String>> values) {
            return line;
        }
    }

    private static final class EntrySegment implements Segment {
        private final String section;
        private final String key;
        private final String beforeValue;
        private final String suffix;
        private final String originalLiteral;
        private final ValueStyle style;
        private final String placeholder;
        private final String tripleContent;
        private final String originalValueToken;
        private final String inlinePrefix;

        private EntrySegment(String section, String key, String beforeValue, String suffix,
                             String originalLiteral, ValueStyle style, String placeholder,
                             String tripleContent, String originalValueToken, String inlinePrefix) {
            this.section = section;
            this.key = key;
            this.beforeValue = beforeValue;
            this.suffix = suffix;
            this.originalLiteral = originalLiteral;
            this.style = style;
            this.placeholder = placeholder;
            this.tripleContent = tripleContent;
            this.originalValueToken = originalValueToken;
            this.inlinePrefix = inlinePrefix == null ? "" : inlinePrefix;
        }

        @Override
        public String sanitizedText() {
            if (style == ValueStyle.TRIPLE_QUOTED) {
                return beforeValue + inlinePrefix + placeholder + suffix;
            }
            return originalLiteral;
        }

        @Override
        public String render(Map<String, LinkedHashMap<String, String>> values) {
            String value = removeSnapshotValue(values, section, key);
            if (value == null) {
                return originalLiteral;
            }
                boolean originalUsedEscapedBreaks = originalValueToken != null
                    && (originalValueToken.contains("\\n") || originalValueToken.contains("\\r"));
                boolean containsLineBreak = value.contains("\n") || value.contains("\r");
                if (style != ValueStyle.TRIPLE_QUOTED && containsLineBreak && !originalUsedEscapedBreaks) {
                return beforeValue + "\"\"\"" + value + "\"\"\"" + suffix;
            }
            ValueStyle targetStyle = style;
            if (targetStyle == ValueStyle.TRIPLE_QUOTED) {
                String tripleValue = stripInlinePrefix(value);
                return beforeValue + inlinePrefix + "\"\"\"" + tripleValue + "\"\"\"" + suffix;
            }
            String adjustedValue = adjustEscapes(value);
            if (targetStyle == ValueStyle.DOUBLE_QUOTED || needsQuotes(adjustedValue)) {
                return beforeValue + '"' + escapeQuotes(adjustedValue) + '"' + suffix;
            }
            return beforeValue + adjustedValue + suffix;
        }

        private String adjustEscapes(String value) {
            if (originalValueToken == null) {
                return value;
            }
            String result = value;
            if (originalValueToken.contains("\\n")) {
                result = result.replace("\n", "\\n");
            }
            if (originalValueToken.contains("\\r")) {
                result = result.replace("\r", "\\r");
            }
            if (originalValueToken.contains("\\t")) {
                result = result.replace("\t", "\\t");
            }
            return result;
        }

        private String stripInlinePrefix(String value) {
            if (inlinePrefix.isEmpty() || value == null) {
                return value;
            }
            if (value.startsWith(inlinePrefix)) {
                return value.substring(inlinePrefix.length());
            }
            return value;
        }

        private static boolean needsQuotes(String value) {
            return value.isEmpty()
                    || Character.isWhitespace(value.charAt(0))
                    || Character.isWhitespace(value.charAt(value.length() - 1))
                    || value.indexOf(';') >= 0;
        }

    }

    private static final class BareEntrySegment implements Segment {
        private final String section;
        private final String key;
        private final String line;

        private BareEntrySegment(String section, String key, String line) {
            this.section = section;
            this.key = key;
            this.line = line;
        }

        @Override
        public String sanitizedText() {
            return line;
        }

        @Override
        public String render(Map<String, LinkedHashMap<String, String>> values) {
            removeSnapshotValue(values, section, key);
            return line;
        }
    }

    private enum ValueStyle {
        UNQUOTED,
        DOUBLE_QUOTED,
        TRIPLE_QUOTED
    }

    private record ValueSplit(String valueToken, String suffix) {
    }

    private record EntryParseResult(EntrySegment entrySegment, PendingTriple pendingTriple) {
    }

    private static final class PendingTriple {
        private final String section;
        private final String key;
        private final String beforeValue;
        private final String inlinePrefix;
        private final String newline;
        private final String placeholder;
        private final StringBuilder literalBuilder;
        private final StringBuilder contentBuilder = new StringBuilder();
        private String suffix = "";
        private boolean closed;
        private boolean needsLineBreak;

        private PendingTriple(String section, String key, String beforeValue, String inlinePrefix,
                              String newline, String placeholder, String firstLine) {
            this.section = section;
            this.key = key;
            this.beforeValue = beforeValue;
            this.inlinePrefix = inlinePrefix == null ? "" : inlinePrefix;
            this.newline = newline;
            this.placeholder = placeholder;
            this.literalBuilder = new StringBuilder(firstLine);
        }

        private void appendInitial(String text) {
            contentBuilder.append(text);
            needsLineBreak = true;
        }

        private void consumeNextLine(String line) {
            if (closed) {
                return;
            }
            literalBuilder.append(newline).append(line);
            if (needsLineBreak) {
                contentBuilder.append(newline);
            }
            int closingIndex = line.indexOf("\"\"\"");
            if (closingIndex >= 0) {
                contentBuilder.append(line, 0, closingIndex);
                suffix = line.substring(closingIndex + 3);
                closed = true;
            } else {
                contentBuilder.append(line);
            }
            needsLineBreak = true;
        }

        private boolean isClosed() {
            return closed;
        }

        private EntrySegment toSegment() {
                return new EntrySegment(section, key, beforeValue, suffix,
                    literalBuilder.toString(), ValueStyle.TRIPLE_QUOTED, placeholder,
                    contentBuilder.toString(), null, inlinePrefix);
        }

        private EntrySegment forceClose() {
            closed = true;
            return toSegment();
        }
    }
}
