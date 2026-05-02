package kmu.conditions.ui.picker.action;

import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;

import java.util.Objects;

import static kmu.KmuValues.requireNonBlankText;

public final class KmuConditionPickerAction {
    private final String conditionId;
    private final boolean presentAtRender;

    private KmuConditionPickerAction(String conditionId, boolean presentAtRender) {
        this.conditionId = requireNonBlankText(conditionId, "conditionId");
        this.presentAtRender = presentAtRender;
    }

    public static KmuConditionPickerAction fromEntry(KmuConditionPickerEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return new KmuConditionPickerAction(entry.getConditionId(), entry.isPresent());
    }

    public String getConditionId() {
        return conditionId;
    }

    public boolean isPresentAtRender() {
        return presentAtRender;
    }
}
