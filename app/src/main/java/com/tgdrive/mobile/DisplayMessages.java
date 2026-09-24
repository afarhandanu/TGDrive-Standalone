package com.tgdrive.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Anchored message templates preserve user filenames and provider diagnostics verbatim. */
final class DisplayMessages {
    private static final List<Template> TEMPLATES = new ArrayList<>();
    static {
        for (String key : TranslationCatalog.IDS.keySet()) {
            if (key.contains("{0}")) TEMPLATES.add(new Template(key));
        }
    }
    static String translate(String source, Function<String, String> lookup) {
        String exact = lookup.apply(source);
        if (!exact.equals(source)) return exact;
        if (source.contains("\n")) {
            String[] lines = source.split("\n", -1);
            for (int i=0; i<lines.length; i++) lines[i] = line(lines[i], lookup);
            return String.join("\n", lines);
        }
        return line(source, lookup);
    }
    private static String line(String value, Function<String,String> lookup) {
        String exact = lookup.apply(value);
        if (!exact.equals(value)) return exact;
        for (Template template : TEMPLATES) {
            Matcher match = template.pattern.matcher(value);
            if (match.matches()) {
                String translated = lookup.apply(template.key);
                // One pass: filenames containing placeholder-like text must stay untouched.
                Matcher slots = Pattern.compile("\\{([0-9]+)\\}").matcher(translated);
                StringBuffer result = new StringBuffer();
                while (slots.find()) slots.appendReplacement(result,
                    Matcher.quoteReplacement(match.group(Integer.parseInt(slots.group(1))+1)));
                slots.appendTail(result); return result.toString();
            }
        }
        Matcher error = Pattern.compile("^((?:[\\w.]+(?:Error|Exception): )+)(.*)$").matcher(value);
        if (error.matches()) return error.group(1) + line(error.group(2), lookup);
        return value;
    }
    private static final class Template {
        final String key;
        final Pattern pattern;
        Template(String key) {
            this.key = key;
            StringBuilder regex = new StringBuilder("^");
            Matcher slots = Pattern.compile("\\{[0-9]+\\}").matcher(key);
            int offset=0;
            while(slots.find()) {
                regex.append(Pattern.quote(key.substring(offset, slots.start()))).append("(.*?)");
                offset=slots.end();
            }
            regex.append(Pattern.quote(key.substring(offset))).append("$");
            pattern=Pattern.compile(regex.toString());
        }
    }
}
