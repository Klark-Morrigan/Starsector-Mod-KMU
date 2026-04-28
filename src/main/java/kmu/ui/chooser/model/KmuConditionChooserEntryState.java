package kmu.ui.chooser.model;

public enum KmuConditionChooserEntryState {
    PRESENT("Present"),
    ABSENT("Absent");

    private final String displayName;

    KmuConditionChooserEntryState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
