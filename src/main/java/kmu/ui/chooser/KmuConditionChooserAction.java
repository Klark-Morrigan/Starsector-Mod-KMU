package kmu.ui.chooser;

import java.util.Objects;

public final class KmuConditionChooserAction {
    private final String conditionId;
    private final boolean presentAtRender;

    private KmuConditionChooserAction(String conditionId, boolean presentAtRender) {
        this.conditionId = requireNonBlank(conditionId, "conditionId");
        this.presentAtRender = presentAtRender;
    }

    public static KmuConditionChooserAction fromEntry(KmuConditionChooserEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return new KmuConditionChooserAction(entry.getConditionId(), entry.isPresent());
    }

    public String getConditionId() {
        return conditionId;
    }

    public boolean isPresentAtRender() {
        return presentAtRender;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
