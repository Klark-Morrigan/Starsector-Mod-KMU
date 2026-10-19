package kmu.conditions.ui.picker.model;

import kmu.util.KmuMarketConditionStringKeys;
import kmu.util.KmuStringKeys;

public enum KmuConditionPickerEntryState {

    PRESENT(KmuMarketConditionStringKeys.CONDITION_MANAGER_ENTRY_PRESENT),
    ABSENT(KmuMarketConditionStringKeys.CONDITION_MANAGER_ENTRY_ABSENT);

    private final String displayNameKey;

    KmuConditionPickerEntryState(String displayNameKey) {
        this.displayNameKey = displayNameKey;
    }

    public String getDisplayName() {
        return KmuStringKeys.get(displayNameKey);
    }
}
