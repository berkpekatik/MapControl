package com.mapcontrol.util;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal markdown: "### " headings and **bold**. */
public final class MarkdownUtil {
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.*?)\\*\\*");

    private MarkdownUtil() {}

    public static SpannableString parse(String markdownText) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String[] lines = markdownText.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("### ")) {
                int start = builder.length();
                builder.append(line.trim().substring(4).trim());
                int end = builder.length();
                builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new RelativeSizeSpan(1.3f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                appendWithBold(builder, line);
            }
            if (i < lines.length - 1) builder.append("\n");
        }
        return new SpannableString(builder);
    }

    private static void appendWithBold(SpannableStringBuilder builder, String line) {
        Matcher matcher = BOLD.matcher(line);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) builder.append(line.substring(lastEnd, matcher.start()));
            int start = builder.length();
            builder.append(matcher.group(1));
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            lastEnd = matcher.end();
        }
        if (lastEnd < line.length()) builder.append(line.substring(lastEnd));
    }
}
