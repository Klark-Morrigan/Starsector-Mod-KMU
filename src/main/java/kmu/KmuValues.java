package kmu;

import java.util.Optional;

public final class KmuValues {
    private KmuValues() {
    }

    public static Optional<String> convertToOptionalText(String value) {
        return Optional.ofNullable(normalizeText(value));
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    public static String readTextOrNull(SupplierWithRuntimeException<String> supplier) {
        return normalizeText(readValueOrNull(supplier));
    }

    @FunctionalInterface
    public interface SupplierWithRuntimeException<T> {
        T get();
    }
}
