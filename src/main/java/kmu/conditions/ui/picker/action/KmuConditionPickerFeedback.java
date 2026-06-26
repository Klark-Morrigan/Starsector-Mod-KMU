package kmu.conditions.ui.picker.action;

import kmu.conditions.domain.KmuConditionAddResult;
import kmu.conditions.domain.KmuConditionAddStatus;

import java.util.Objects;

public final class KmuConditionPickerFeedback {
    private final KmuConditionAddStatus status;
    private final String message;

    private KmuConditionPickerFeedback(KmuConditionAddStatus status, String message) {
        this.status = Objects.requireNonNull(status, "status");
        this.message = Objects.requireNonNull(message, "message");
    }

    public static KmuConditionPickerFeedback from(KmuConditionAddResult result) {
        Objects.requireNonNull(result, "result");
        var conditionId = result.getConditionId().orElse("unknown condition");
        switch (result.getStatus()) {
            case ADDED:
                return new KmuConditionPickerFeedback(result.getStatus(), "Added condition: " + conditionId);
            case ALREADY_PRESENT:
                return new KmuConditionPickerFeedback(result.getStatus(), "Already present: " + conditionId);
            case CONDITION_NOT_FOUND:
                return new KmuConditionPickerFeedback(result.getStatus(), "Condition not found: " + conditionId);
            case NOT_PLANETARY:
                return new KmuConditionPickerFeedback(result.getStatus(), "Not a market condition: " + conditionId);
            case FAILED:
                return new KmuConditionPickerFeedback(result.getStatus(), "Failed to add condition: " + conditionId);
            default:
                throw new IllegalStateException("Unhandled condition add status: " + result.getStatus());
        }
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
}
