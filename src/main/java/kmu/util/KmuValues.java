package kmu.util;

import java.util.Optional;

/**
 * One answer to "is there anything here?", for text arriving from outside the mod.
 *
 * <p>The game and the mods around it report a missing name three ways - null, empty, and a cell of
 * spaces - and none of them means anything different to a caller. Deciding that per site leaves a
 * reader unable to tell a deliberate blank from a forgotten trim, and leaves two sites free to
 * disagree about the same value. So the decision is made once here, and every shape below is that
 * one decision handed back in whatever form the caller works in: null, an {@link Optional}, an
 * empty string, a flag, or an exception.
 */
public final class KmuValues {

    private KmuValues() {
    }

    /**
     * Trims {@code value} and returns {@code null} if the result is empty,
     * otherwise returns the trimmed string. This is the canonical absent-or-present
     * check used throughout the codebase: a blank API response is treated the same
     * as a missing one.
     */
    public static String normaliseText(String value) {
        if (value == null) {
            return null;
        }

        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Wraps {@link #normaliseText} in an {@code Optional} for callers that use Optional idioms. */
    public static Optional<String> convertToOptionalText(String value) {
        return Optional.ofNullable(normaliseText(value));
    }

    /** Returns the normalised text, or {@code ""} when absent - useful in string-building contexts. */
    public static String getTextOrEmpty(String value) {
        var normalised = normaliseText(value);
        return normalised == null ? "" : normalised;
    }

    /** Returns {@code true} when {@link #normaliseText} would produce a non-null result. */
    public static boolean hasText(String value) {
        return normaliseText(value) != null;
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
