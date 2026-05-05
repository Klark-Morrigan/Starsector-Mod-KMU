package kmu.util;

import java.util.Optional;
import java.util.function.Function;

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

    /**
     * Calls {@code supplier} and returns its result, or {@code null} if it throws a
     * {@link RuntimeException}. Defensive wrapper for Starsector API reads that can
     * fail unpredictably at runtime.
     */
    public static <T> T readValueOrNull(SupplierWithRuntimeException<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Null-safe variant: returns {@code null} immediately when {@code value} is null,
     * otherwise delegates to {@link #readValueOrNull(SupplierWithRuntimeException)}.
     * Eliminates the recurring {@code value == null ? null : readValueOrNull(value::method)}
     * pattern at Starsector API call sites.
     */
    public static <T, R> R readValueOrNull(T value, Function<T, R> extractor) {
        return value == null ? null : readValueOrNull(() -> extractor.apply(value));
    }

    /**
     * Like {@link #readValueOrNull(SupplierWithRuntimeException)} but also normalizes
     * the string result via {@link #normalizeText}, so blank API responses are treated
     * as absent.
     */
    public static String readTextOrNull(SupplierWithRuntimeException<String> supplier) {
        return normalizeText(readValueOrNull(supplier));
    }

    /**
     * Null-safe variant of {@link #readTextOrNull(SupplierWithRuntimeException)}: returns
     * {@code null} immediately when {@code value} is null, otherwise reads and normalizes.
     */
    public static <T> String readTextOrNull(T value, Function<T, String> extractor) {
        return value == null ? null : normalizeText(readValueOrNull(() -> extractor.apply(value)));
    }

    @FunctionalInterface
    public interface SupplierWithRuntimeException<T> {
        T get();
    }
}
