package kmu.conditions.ui.picker.action;

import kmu.conditions.domain.KmuConditionAddResult;
import kmu.conditions.domain.KmuConditionAddStatus;
import kmu.util.KmuMarketConditionStringKeys;
import kmu.util.KmuStringKeys;

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

        var conditionId = result
            .getConditionId()
            .orElseGet(() -> KmuStringKeys.get(
                KmuMarketConditionStringKeys.CONDITION_MANAGER_FEEDBACK_UNKNOWN_CONDITION));

        var status = result.getStatus();

        return new KmuConditionPickerFeedback(
            status,
            KmuStringKeys.format(
                KmuMarketConditionStringKeys.CONDITION_MANAGER_FEEDBACK,
                describeStatus(status),
                conditionId));
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
    // construction site above owns the shared "<label>: <condition ID>" shape. The switch
    // is exhaustive over the enum, so a new status breaks the build instead of reaching a
    // runtime throw.
    private static String describeStatus(KmuConditionAddStatus status) {

        return KmuStringKeys.get(switch (status) {
            case ADDED -> KmuMarketConditionStringKeys.CONDITION_MANAGER_FEEDBACK_ADDED;
            case ALREADY_PRESENT -> KmuMarketConditionStringKeys.CONDITION_MANAGER_FEEDBACK_ALREADY_PRESENT;
            case CONDITION_NOT_FOUND -> KmuMarketConditionStringKeys.CONDITION_MANAGER_FEEDBACK_CONDITION_NOT_FOUND;
            case NOT_OFFERABLE -> KmuMarketConditionStringKeys.CONDITION_MANAGER_FEEDBACK_NOT_OFFERABLE;
            case FAILED -> KmuMarketConditionStringKeys.CONDITION_MANAGER_FEEDBACK_FAILED;
        });
    }
}
