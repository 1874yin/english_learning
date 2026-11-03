package com.xiaojie.englishlearning.lesson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Splits plain-text transcripts into sentences and estimates a per-sentence
 * timeline from the real audio duration. This is intentionally not a forced
 * aligner: without word timestamps the result is a proportional estimate.
 */
@Component
public class TranscriptProcessor {

    public static final int MAX_TEXT_LENGTH = 100000;
    public static final int MAX_SENTENCES = 1500;

    private static final Set<String> ABBREVIATIONS = new HashSet<String>(Arrays.asList(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc",
            "inc", "ltd", "co", "dept", "fig", "approx", "no", "vol", "al",
            "ca", "cf", "eds", "est", "min", "max", "e.g", "i.e", "u.s",
            "a.m", "p.m", "jan", "feb", "mar", "apr", "jun", "jul", "aug",
            "sep", "sept", "oct", "nov", "dec"
    ));

    public List<String> split(String rawText) {
        if (rawText == null) {
            throw new IllegalArgumentException("请填写台词文本。");
        }
        if (rawText.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("台词过长，请控制在 10 万字符以内。");
        }

        String normalized = rawText.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(' ', ' ')
                .replace('　', ' ')
                .trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请填写台词文本。");
        }

        List<String> sentences = new ArrayList<String>();
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        for (String paragraph : paragraphs) {
            String block = paragraph.trim();
            if (block.isEmpty()) {
                continue;
            }
            boolean hasTerminalPunctuation = block.matches("(?s).*[.!?].*");
            if (!hasTerminalPunctuation && block.contains("\n")) {
                for (String line : block.split("\\n")) {
                    addSentence(line, sentences);
                }
            } else {
                String collapsed = block.replaceAll("\\s*\\n\\s*", " ")
                        .replaceAll("[\\t ]+", " ")
                        .trim();
                splitParagraph(collapsed, sentences);
            }
            if (sentences.size() > MAX_SENTENCES) {
                throw new IllegalArgumentException("台词句数过多，请控制在 " + MAX_SENTENCES + " 句以内。");
            }
        }

        if (sentences.isEmpty()) {
            throw new IllegalArgumentException("没有从台词中识别出可用的句子。");
        }
        return sentences;
    }

    public List<TranscriptLine> buildFromText(String rawText, double duration) {
        return estimateTimeline(split(rawText), duration);
    }

    public List<TranscriptLine> estimateTimeline(List<String> sentences, double duration) {
        if (sentences == null || sentences.isEmpty()) {
            throw new IllegalArgumentException("台词不能为空。");
        }
        if (!(duration > 0)) {
            throw new IllegalArgumentException("无法读取音频时长。");
        }

        int count = sentences.size();
        double gap = count > 1 && duration > count * 0.6
                ? Math.min(0.22, (duration * 0.06) / (count - 1))
                : 0;
        double speechBudget = Math.max(0.01, duration - gap * (count - 1));

        double[] weights = new double[count];
        double totalWeight = 0;
        for (int i = 0; i < count; i += 1) {
            weights[i] = sentenceWeight(sentences.get(i));
            totalWeight += weights[i];
        }
        if (totalWeight <= 0) {
            totalWeight = count;
            Arrays.fill(weights, 1);
        }

        List<TranscriptLine> lines = new ArrayList<TranscriptLine>(count);
        double cursor = 0;
        for (int i = 0; i < count; i += 1) {
            double start = cursor;
            double speech = speechBudget * weights[i] / totalWeight;
            double end = i == count - 1 ? duration : Math.min(duration, start + speech);
            if (end <= start) {
                end = Math.min(duration, start + 0.01);
            }

            TranscriptLine line = new TranscriptLine();
            line.setStart(round(start));
            line.setEnd(i == count - 1 ? round(duration) : round(end));
            line.setText(sentences.get(i));
            lines.add(line);

            cursor = end + gap;
        }

        // Rounding can never move the final line past the audio duration.
        TranscriptLine last = lines.get(count - 1);
        last.setEnd(round(duration));
        return lines;
    }

    private void splitParagraph(String paragraph, List<String> sentences) {
        int start = 0;
        int length = paragraph.length();
        for (int i = 0; i < length; i += 1) {
            char current = paragraph.charAt(i);
            if (current != '.' && current != '!' && current != '?') {
                continue;
            }

            int end = i + 1;
            while (end < length && isClosingOrTerminal(paragraph.charAt(end))) {
                end += 1;
            }

            if (current == '.' && isNonTerminalPeriod(paragraph, i)) {
                continue;
            }

            int next = end;
            while (next < length && Character.isWhitespace(paragraph.charAt(next))) {
                next += 1;
            }
            // A lowercase word after punctuation is usually a dialogue tag
            // or an abbreviation continuation, not a new sentence.
            if (next < length && Character.isLowerCase(paragraph.charAt(next))) {
                continue;
            }

            addSentence(paragraph.substring(start, end), sentences);
            start = end;
            i = end - 1;
        }
        if (start < length) {
            addSentence(paragraph.substring(start), sentences);
        }
    }

    private boolean isNonTerminalPeriod(String text, int periodIndex) {
        if (periodIndex > 0 && periodIndex + 1 < text.length()
                && Character.isDigit(text.charAt(periodIndex - 1))
                && Character.isDigit(text.charAt(periodIndex + 1))) {
            return true;
        }

        int start = periodIndex - 1;
        while (start >= 0 && !Character.isWhitespace(text.charAt(start))) {
            start -= 1;
        }
        String token = text.substring(start + 1, periodIndex).trim();
        if (token.isEmpty()) {
            return false;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        if (ABBREVIATIONS.contains(lower)) {
            return true;
        }
        // Single capital initials such as "J. K. Rowling".
        return token.length() == 1 && Character.isUpperCase(token.charAt(0));
    }

    private boolean isClosingOrTerminal(char value) {
        return ".!?\"'”’)]}»".indexOf(value) >= 0;
    }

    private void addSentence(String rawSentence, List<String> sentences) {
        if (rawSentence == null) {
            return;
        }
        String sentence = rawSentence.replaceAll("\\s+", " ").trim();
        if (!sentence.isEmpty()) {
            sentences.add(sentence);
        }
    }

    private double sentenceWeight(String sentence) {
        int syllables = 0;
        int words = 0;
        int clauseMarks = 0;
        for (String word : sentence.split("\\s+")) {
            String cleaned = word.replaceAll("[^A-Za-z']", "");
            if (cleaned.isEmpty()) {
                continue;
            }
            words += 1;
            syllables += countSyllables(cleaned);
        }
        for (int i = 0; i < sentence.length(); i += 1) {
            char ch = sentence.charAt(i);
            if (ch == ',' || ch == ';' || ch == ':' || ch == '—' || ch == '-') {
                clauseMarks += 1;
            }
        }
        return Math.max(1.0, syllables + words * 0.08 + clauseMarks * 0.35 + 0.4);
    }

    private int countSyllables(String word) {
        int groups = 0;
        boolean previousVowel = false;
        for (int i = 0; i < word.length(); i += 1) {
            char ch = Character.toLowerCase(word.charAt(i));
            boolean vowel = ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u' || ch == 'y';
            if (vowel && !previousVowel) {
                groups += 1;
            }
            previousVowel = vowel;
        }
        return Math.max(1, groups);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
