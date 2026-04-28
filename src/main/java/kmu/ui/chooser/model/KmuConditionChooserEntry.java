package kmu.ui.chooser.model;

import kmu.ui.chooser.tooltip.KmuConditionTooltipRenderer;

import java.util.Objects;
import java.util.Optional;

public final class KmuConditionChooserEntry {
    private final String conditionId;
    private final String name;
    private final String icon;
    private final KmuConditionChooserEntryState state;
    private final String tooltipText;
    private final String sourceModName;
    private final KmuConditionTooltipRenderer tooltipRenderer;

    public KmuConditionChooserEntry(
            String conditionId,
            String name,
            String icon,
            KmuConditionChooserEntryState state,
            String tooltipText) {
        this(conditionId, name, icon, state, tooltipText, null);
    }

    public KmuConditionChooserEntry(
            String conditionId,
            String name,
            String icon,
            KmuConditionChooserEntryState state,
            String tooltipText,
            String sourceModName) {
        this(conditionId, name, icon, state, tooltipText, sourceModName, null);
    }

    KmuConditionChooserEntry(
            String conditionId,
            String name,
            String icon,
            KmuConditionChooserEntryState state,
            String tooltipText,
            String sourceModName,
            KmuConditionTooltipRenderer tooltipRenderer) {
        this.conditionId = requireNonBlank(conditionId, "conditionId");
        this.name = requireNonBlank(name, "name");
        this.icon = normalizeOptional(icon);
        this.state = Objects.requireNonNull(state, "state");
        this.tooltipText = tooltipText == null ? "" : tooltipText.trim();
        this.sourceModName = normalizeOptional(sourceModName);
        this.tooltipRenderer = tooltipRenderer;
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

    public Optional<String> getSourceModName() {
        return Optional.ofNullable(sourceModName);
    }

    public Optional<KmuConditionTooltipRenderer> getTooltipRenderer() {
        return Optional.ofNullable(tooltipRenderer);
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
