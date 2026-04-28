package kmu.ui.chooser.action;

import kmu.conditions.KmuConditionAddResult;
import kmu.conditions.KmuConditionAddStatus;

import java.util.Objects;

public final class KmuConditionChooserFeedback {
    private final KmuConditionAddStatus status;
    private final String message;

    private KmuConditionChooserFeedback(KmuConditionAddStatus status, String message) {
        this.status = Objects.requireNonNull(status, "status");
        this.message = Objects.requireNonNull(message, "message");
    }

    public static KmuConditionChooserFeedback from(KmuConditionAddResult result) {
        Objects.requireNonNull(result, "result");
        String conditionId = result.getConditionId().orElse("unknown condition");
        switch (result.getStatus()) {
            case ADDED:
                return new KmuConditionChooserFeedback(result.getStatus(), "Added condition: " + conditionId);
            case ALREADY_PRESENT:
                return new KmuConditionChooserFeedback(result.getStatus(), "Already present: " + conditionId);
            case CONDITION_NOT_FOUND:
                return new KmuConditionChooserFeedback(result.getStatus(), "Condition not found: " + conditionId);
            case NOT_PLANETARY:
                return new KmuConditionChooserFeedback(result.getStatus(), "Not a planetary condition: " + conditionId);
            case FAILED:
                return new KmuConditionChooserFeedback(result.getStatus(), "Failed to add condition: " + conditionId);
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
