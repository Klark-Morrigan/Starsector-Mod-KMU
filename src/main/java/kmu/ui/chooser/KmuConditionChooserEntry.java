package kmu.ui.chooser;

import java.util.Objects;
import java.util.Optional;

public final class KmuConditionChooserEntry {
    private final String conditionId;
    private final String name;
    private final String icon;
    private final KmuConditionChooserEntryState state;
    private final String tooltipText;

    public KmuConditionChooserEntry(
            String conditionId,
            String name,
            String icon,
            KmuConditionChooserEntryState state,
            String tooltipText) {
        this.conditionId = requireNonBlank(conditionId, "conditionId");
        this.name = requireNonBlank(name, "name");
        this.icon = normalizeOptional(icon);
        this.state = Objects.requireNonNull(state, "state");
        this.tooltipText = requireNonBlank(tooltipText, "tooltipText");
    }

    public String getConditionId() {
        return conditionId;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getIcon() {
        return Optional.ofNullable(icon);
    }

    public KmuConditionChooserEntryState getState() {
        return state;
    }

    public String getTooltipText() {
        return tooltipText;
    }

    public boolean isPresent() {
        return state == KmuConditionChooserEntryState.PRESENT;
    }

    public String descriptionLine() {
        return conditionId + " - " + state.getDisplayName();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
