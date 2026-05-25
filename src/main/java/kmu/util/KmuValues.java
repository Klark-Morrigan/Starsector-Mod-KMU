package kmu.util;

import java.util.Optional;

public final class KmuValues {
    private KmuValues() {
    }

    /**
     * Trims {@code value} and returns {@code null} if the result is empty,
     * otherwise returns the trimmed string. This is the canonical absent-or-present
     * check used throughout the codebase: a blank API response is treated the same
     * as a missing one.
     */
    public static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Wraps {@link #normalizeText} in an {@code Optional} for callers that use Optional idioms. */
    public static Optional<String> convertToOptionalText(String value) {
        return Optional.ofNullable(normalizeText(value));
    }

    /** Returns the normalized text, or {@code ""} when absent - useful in string-building contexts. */
    public static String getTextOrEmpty(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? "" : normalized;
    }

    /** Returns {@code true} when {@link #normalizeText} would produce a non-null result. */
    public static boolean hasText(String value) {
        return normalizeText(value) != null;
    }

    /**
     * Validates that {@code value} has text, throwing {@link IllegalArgumentException} if not.
     * Use at constructor/method boundaries to reject blank inputs early.
     */
    public static String requireNonBlankText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
