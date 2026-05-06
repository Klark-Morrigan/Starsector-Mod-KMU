package kmu.conditions.ui.editor;

import java.util.Objects;
import java.util.Optional;

import static kmu.util.KmuValues.normalizeText;

public final class KmuConditionEditorOpenResult {
    private final KmuConditionEditorOpenStatus status;
    private final String message;
    private final RuntimeException cause;

    private KmuConditionEditorOpenResult(
            KmuConditionEditorOpenStatus status,
            String message,
            RuntimeException cause) {
        this.status = Objects.requireNonNull(status, "status");
        this.message = Objects.requireNonNull(message, "message");
        this.cause = cause;
    }

    public static KmuConditionEditorOpenResult opened() {
        return new KmuConditionEditorOpenResult(
                KmuConditionEditorOpenStatus.OPENED,
                "Opened Market Condition Manager.",
                null);
    }

    public static KmuConditionEditorOpenResult noMarketContext() {
        return new KmuConditionEditorOpenResult(
                KmuConditionEditorOpenStatus.NO_MARKET_CONTEXT,
                "No active context supports market condition editing.",
                null);
    }

    public static KmuConditionEditorOpenResult unsupportedTarget(String reason) {
        String normalizedReason = normalizeText(reason);
        String message = normalizedReason == null
                ? "Current market does not support market condition editing."
                : normalizedReason;
        return new KmuConditionEditorOpenResult(
                KmuConditionEditorOpenStatus.UNSUPPORTED_TARGET,
                message,
                null);
    }

    public static KmuConditionEditorOpenResult failed(String message, RuntimeException cause) {
        return new KmuConditionEditorOpenResult(
                KmuConditionEditorOpenStatus.FAILED,
                Objects.requireNonNull(message, "message"),
                Objects.requireNonNull(cause, "cause"));
    }

    public KmuConditionEditorOpenStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Optional<RuntimeException> getCause() {
        return Optional.ofNullable(cause);
    }

    public boolean isOpened() {
        return status == KmuConditionEditorOpenStatus.OPENED;
    }
}
