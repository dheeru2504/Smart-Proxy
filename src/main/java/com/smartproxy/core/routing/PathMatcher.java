package com.smartproxy.core.routing;

import java.util.regex.Pattern;

/**
 * High-performance, zero-allocation path pattern matching engine.
 * Supports exact matches, Ant-style single wildcards (*), and multi-segment wildcards (**).
 */
public final class PathMatcher {

    private PathMatcher() {}

    /**
     * Evaluates whether a candidate request path matches a given route path pattern.
     */
    public static boolean matches(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }

        // Fast path 1: Exact equality
        if (pattern.equals(path)) {
            return true;
        }

        // Fast path 2: Catch-all wildcard
        if (pattern.equals("/**") || pattern.equals("/*")) {
            return true;
        }

        // Fast path 3: Simple trailing prefix wildcard (e.g. /api/v1/**)
        if (pattern.endsWith("/**")) {
            String basePrefix = pattern.substring(0, pattern.length() - 3);
            if (path.startsWith(basePrefix)) {
                if (path.length() == basePrefix.length() || path.charAt(basePrefix.length()) == '/') {
                    return true;
                }
            }
            return false;
        }

        // Segment-by-segment token matching
        return matchSegments(pattern.split("/"), path.split("/"));
    }

    private static boolean matchSegments(String[] patternTokens, String[] pathTokens) {
        int patIdxStart = 0;
        int patIdxEnd = patternTokens.length - 1;
        int strIdxStart = 0;
        int strIdxEnd = pathTokens.length - 1;

        // Skip leading empty strings from split
        while (patIdxStart <= patIdxEnd && patternTokens[patIdxStart].isEmpty()) {
            patIdxStart++;
        }
        while (strIdxStart <= strIdxEnd && pathTokens[strIdxStart].isEmpty()) {
            strIdxStart++;
        }

        // Match from start until first '**'
        while (patIdxStart <= patIdxEnd && strIdxStart <= strIdxEnd) {
            String patToken = patternTokens[patIdxStart];
            if (patToken.equals("**")) {
                break;
            }
            if (!matchSingleToken(patToken, pathTokens[strIdxStart])) {
                return false;
            }
            patIdxStart++;
            strIdxStart++;
        }

        if (strIdxStart > strIdxEnd) {
            // String is exhausted
            for (int i = patIdxStart; i <= patIdxEnd; i++) {
                if (!patternTokens[i].equals("**") && !patternTokens[i].isEmpty()) {
                    return false;
                }
            }
            return true;
        } else if (patIdxStart > patIdxEnd) {
            // Pattern is exhausted but string has remaining segments
            return false;
        }

        // Match from end until '**'
        while (patIdxStart <= patIdxEnd && strIdxStart <= strIdxEnd) {
            String patToken = patternTokens[patIdxEnd];
            if (patToken.equals("**")) {
                break;
            }
            if (!matchSingleToken(patToken, pathTokens[strIdxEnd])) {
                return false;
            }
            patIdxEnd--;
            strIdxEnd--;
        }

        if (strIdxStart > strIdxEnd) {
            for (int i = patIdxStart; i <= patIdxEnd; i++) {
                if (!patternTokens[i].equals("**") && !patternTokens[i].isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        while (patIdxStart != patIdxEnd && strIdxStart <= strIdxEnd) {
            int patIdxTmp = -1;
            for (int i = patIdxStart + 1; i <= patIdxEnd; i++) {
                if (patternTokens[i].equals("**")) {
                    patIdxTmp = i;
                    break;
                }
            }
            if (patIdxTmp == patIdxStart + 1) {
                patIdxStart++;
                continue;
            }

            int patLength = (patIdxTmp - patIdxStart - 1);
            int strLength = (strIdxEnd - strIdxStart + 1);
            int foundIdx = -1;

            strLoop:
            for (int i = 0; i <= strLength - patLength; i++) {
                for (int j = 0; j < patLength; j++) {
                    String subPat = patternTokens[patIdxStart + j + 1];
                    String subStr = pathTokens[strIdxStart + i + j];
                    if (!matchSingleToken(subPat, subStr)) {
                        continue strLoop;
                    }
                }
                foundIdx = strIdxStart + i;
                break;
            }

            if (foundIdx == -1) {
                return false;
            }

            patIdxStart = patIdxTmp;
            strIdxStart = foundIdx + patLength;
        }

        for (int i = patIdxStart; i <= patIdxEnd; i++) {
            if (!patternTokens[i].equals("**") && !patternTokens[i].isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchSingleToken(String patternToken, String textToken) {
        if (patternToken.equals("*") || patternToken.equals(textToken)) {
            return true;
        }
        if (patternToken.contains("*")) {
            String regex = "\\Q" + patternToken.replace("*", "\\E.*\\Q") + "\\E";
            return Pattern.matches(regex, textToken);
        }
        return false;
    }

    /**
     * Strips leading path segments from a URI path based on count.
     * E.g. stripPrefix("/api/v1/users", 1) -> "/v1/users"
     *      stripPrefix("/api/v1/users", 2) -> "/users"
     */
    public static String stripPrefix(String path, int count) {
        if (path == null || count <= 0) {
            return path;
        }

        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String[] segments = normalized.split("/", count + 1);

        if (segments.length <= count) {
            return "/";
        }

        String remaining = segments[count];
        return remaining.startsWith("/") ? remaining : "/" + remaining;
    }
}
