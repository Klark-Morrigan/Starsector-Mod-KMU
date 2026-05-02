package kmu.conditions.ui.picker.model;

public enum KmuConditionPickerEntryState {
    PRESENT("Present"),
    ABSENT("Absent");

    private final String displayName;

    KmuConditionPickerEntryState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
