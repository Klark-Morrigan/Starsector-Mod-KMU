package kmu.conditions.ui.editor;

import kmlib.mods.console.ConsoleStrings;

import java.util.Objects;
import java.util.Optional;

import static kmlib.text.KmlibStrings.normaliseText;

import static kmu.util.KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_NO_MARKET_CONTEXT;
import static kmu.util.KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_OPENED;
import static kmu.util.KmuMarketConditionStringKeys.CONSOLE_CONDITION_MANAGER_UNSUPPORTED_TARGET;
import static kmu.util.KmuStringKeys.CATEGORY;

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
            ConsoleStrings.get(CATEGORY, CONSOLE_CONDITION_MANAGER_OPENED),
            null);
    }

    public static KmuConditionEditorOpenResult noMarketContext() {

        return new KmuConditionEditorOpenResult(
            KmuConditionEditorOpenStatus.NO_MARKET_CONTEXT,
            ConsoleStrings.get(CATEGORY, CONSOLE_CONDITION_MANAGER_NO_MARKET_CONTEXT),
            null);
    }

    public static KmuConditionEditorOpenResult unsupportedTarget(String reason) {

        var normalisedReason = normaliseText(reason);
        var message = normalisedReason == null
            ? ConsoleStrings.get(CATEGORY, CONSOLE_CONDITION_MANAGER_UNSUPPORTED_TARGET)
            : normalisedReason;

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
