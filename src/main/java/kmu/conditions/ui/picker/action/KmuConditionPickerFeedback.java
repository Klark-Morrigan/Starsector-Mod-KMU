package kmu.conditions.ui.picker.action;

import kmu.conditions.domain.KmuConditionAddResult;
import kmu.conditions.domain.KmuConditionAddStatus;

import java.util.Objects;

public final class KmuConditionPickerFeedback {
    private static final String MESSAGE_SEPARATOR = ": ";
    private static final String UNKNOWN_CONDITION_ID = "unknown condition";

    private final KmuConditionAddStatus status;
    private final String message;

    private KmuConditionPickerFeedback(KmuConditionAddStatus status, String message) {
        this.status = Objects.requireNonNull(status, "status");
        this.message = Objects.requireNonNull(message, "message");
    }

    public static KmuConditionPickerFeedback from(KmuConditionAddResult result) {
        Objects.requireNonNull(result, "result");
        var conditionId = result.getConditionId().orElse(UNKNOWN_CONDITION_ID);
        var status = result.getStatus();
        return new KmuConditionPickerFeedback(
            status,
            describeStatus(status) + MESSAGE_SEPARATOR + conditionId);
    }

    public KmuConditionAddStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public boolean isFailure() {
        return status == KmuConditionAddStatus.FAILED;
    }

    // Only the wording differs per status, so the label alone varies here and the single
    // construction site above owns the shared "<label>: <condition id>" shape. The switch
    // is exhaustive over the enum, so a new status breaks the build instead of reaching a
    // runtime throw.
    private static String describeStatus(KmuConditionAddStatus status) {
        return switch (status) {
            case ADDED -> "Added condition";
            case ALREADY_PRESENT -> "Already present";
            case CONDITION_NOT_FOUND -> "Condition not found";
            case NOT_OFFERABLE -> "Not a placeable condition";
            case FAILED -> "Failed to add condition";
        };
    }
}
