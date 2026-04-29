package kmu.conditions;

import java.util.Set;

public interface KmuEditableMarket {
    Set<String> getConditionIds();

    boolean hasCondition(String conditionId);

    default boolean isConditionSuppressed(String conditionId) {
        return false;
    }

    void addCondition(String conditionId);

    void markConditionSurveyed(String conditionId);

    void reapplyConditions();
}
