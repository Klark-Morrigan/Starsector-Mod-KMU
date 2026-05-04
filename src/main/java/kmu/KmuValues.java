package kmu;

import java.util.Optional;
import java.util.function.Function;

public final class KmuValues {
    private KmuValues() {
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static Optional<String> convertToOptionalText(String value) {
        return Optional.ofNullable(normalizeText(value));
    }

    public static String getTextOrEmpty(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? "" : normalized;
    }

    public static boolean hasText(String value) {
        return normalizeText(value) != null;
    }

    public static String requireNonBlankText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public static <T> T readValueOrNull(SupplierWithRuntimeException<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static <T, R> R readValueOrNull(T value, Function<T, R> extractor) {
        return value == null ? null : readValueOrNull(() -> extractor.apply(value));
    }

    public static String readTextOrNull(SupplierWithRuntimeException<String> supplier) {
        return normalizeText(readValueOrNull(supplier));
    }

    public static <T> String readTextOrNull(T value, Function<T, String> extractor) {
        return value == null ? null : normalizeText(readValueOrNull(() -> extractor.apply(value)));
    }

    @FunctionalInterface
    public interface SupplierWithRuntimeException<T> {
        T get();
    }
}
