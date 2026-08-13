package com.tripflow.backend.ai;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single-pass {@code {{placeholder}}} substitution, shared by every prompt template.
 * Chained {@code String.replace()} calls re-scan the *entire current string* on each
 * call, including whatever a previous call just substituted in — so user-supplied text
 * (e.g. an interest entry that happens to contain the literal text {@code "{{budget}}"})
 * could be found and replaced by a later call in the chain, landing another field's
 * value inside what was meant to be free-text content. Scanning the raw template exactly
 * once and looking up each match avoids that entirely: a value is never itself re-scanned
 * for placeholders.
 */
final class TemplateSubstitution {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private TemplateSubstitution() {
    }

    static String substitute(String rawTemplate, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(rawTemplate);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = values.getOrDefault(matcher.group(1), matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
