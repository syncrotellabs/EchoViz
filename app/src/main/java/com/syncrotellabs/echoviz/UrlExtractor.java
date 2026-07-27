package com.syncrotellabs.echoviz;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UrlExtractor {
    private static final Pattern HTTP_URL = Pattern.compile("https?://[^\\s<>()]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_URL = Pattern.compile(
            "(?:www\\.)?[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+(?:/[^\\s<>()]*)?",
            Pattern.CASE_INSENSITIVE
    );

    private UrlExtractor() {
    }

    static String firstUrl(String text) {
        if (text == null) {
            return "";
        }

        Matcher http = HTTP_URL.matcher(text);
        if (http.find()) {
            return clean(http.group());
        }

        Matcher bare = BARE_URL.matcher(text);
        if (bare.find()) {
            return clean("https://" + bare.group());
        }

        return "";
    }

    static boolean hasUrl(String text) {
        return !firstUrl(text).isEmpty();
    }

    private static String clean(String url) {
        String cleaned = url == null ? "" : url.trim();
        while (cleaned.endsWith(".")
                || cleaned.endsWith(",")
                || cleaned.endsWith(";")
                || cleaned.endsWith(":")
                || cleaned.endsWith(")")
                || cleaned.endsWith("]")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }
}
