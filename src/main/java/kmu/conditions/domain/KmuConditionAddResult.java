package kmu.conditions.domain;

import java.util.Objects;
import java.util.Optional;

public final class KmuConditionAddResult {
    private final KmuConditionAddStatus status;
    private final String conditionId;
    private final String message;
    private final RuntimeException cause;

    private KmuConditionAddResult(
            KmuConditionAddStatus status,
            String conditionId,
            String message,
            RuntimeException cause) {
        this.status = Objects.requireNonNull(status, "status");
        this.conditionId = conditionId;
        this.message = message;
        this.cause = cause;
    }

    public static KmuConditionAddResult added(String conditionId) {
        return new KmuConditionAddResult(KmuConditionAddStatus.ADDED, conditionId, null, null);
    }

    public static KmuConditionAddResult alreadyPresent(String conditionId) {
        return new KmuConditionAddResult(KmuConditionAddStatus.ALREADY_PRESENT, conditionId, null, null);
    }

    public static KmuConditionAddResult conditionNotFound(String conditionId) {
        return new KmuConditionAddResult(KmuConditionAddStatus.CONDITION_NOT_FOUND, conditionId, null, null);
    }

    public static KmuConditionAddResult notPlanetary(String conditionId) {
        return new KmuConditionAddResult(KmuConditionAddStatus.NOT_PLANETARY, conditionId, null, null);
    }

    public static KmuConditionAddResult failed(String conditionId, String message, RuntimeException cause) {
        return new KmuConditionAddResult(
                KmuConditionAddStatus.FAILED,
                conditionId,
                Objects.requireNonNull(message, "message"),
                Objects.requireNonNull(cause, "cause"));
    }

    public KmuConditionAddStatus getStatus() {
        return status;
    }

    public Optional<String> getConditionId() {
        return Optional.ofNullable(conditionId);
    }

    public Optional<String> getMessage() {
        return Optional.ofNullable(message);
    }

    public Optional<RuntimeException> getCause() {
        return Optional.ofNullable(cause);
    }
}
