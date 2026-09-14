package kmu.conditions.domain;

public enum KmuConditionAddStatus {
    ADDED(true),
    ALREADY_PRESENT(false),
    CONDITION_NOT_FOUND(false),
    NOT_OFFERABLE(false),
    FAILED(false);

    private final boolean mutationApplied;

    KmuConditionAddStatus(boolean mutationApplied) {
        this.mutationApplied = mutationApplied;
    }

    public boolean isMutationApplied() {
        return mutationApplied;
    }
}
