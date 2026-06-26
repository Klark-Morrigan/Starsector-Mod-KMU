package kmu.conditions.ui.picker.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerModelTest {
    private static final KmuConditionPickerLocation EMPTY_LOCATION =
            new KmuConditionPickerLocation(null, null, null, null, null, null, null);

    // --- findEntry ---

    @Test
    void findEntryReturnsPresentForMatchingId() {
        var hotEntry = entry("hot", KmuConditionPickerEntryState.PRESENT);
        var model = model(hotEntry);

        assertThat(model.findEntry("hot")).contains(hotEntry);
    }

    @Test
    void findEntryReturnsEmptyForNonMatchingId() {
        var model = model(entry("hot", KmuConditionPickerEntryState.PRESENT));

        assertThat(model.findEntry("cold")).isEmpty();
    }

    // --- getVisibleCount / getAvailableCount ---

    @Test
    void getVisibleCountCountsPresentNonHiddenEntries() {
        var model = model(
                entry("hot", KmuConditionPickerEntryState.PRESENT),
                hiddenEntry("no_atmosphere"),
                entry("cold", KmuConditionPickerEntryState.ABSENT));

        assertThat(model.getVisibleCount()).isEqualTo(1);
    }

    @Test
    void getAvailableCountCountsAbsentEntries() {
        var model = model(
                entry("hot", KmuConditionPickerEntryState.PRESENT),
                entry("cold", KmuConditionPickerEntryState.ABSENT),
                entry("arid", KmuConditionPickerEntryState.ABSENT));

        assertThat(model.getAvailableCount()).isEqualTo(2);
    }

    // --- helpers ---

    private static KmuConditionPickerModel model(KmuConditionPickerEntry... entries) {
        return new KmuConditionPickerModel(List.of(entries), EMPTY_LOCATION);
    }

    private static KmuConditionPickerEntry entry(String id, KmuConditionPickerEntryState state) {
        return new KmuConditionPickerEntry(id, id, null, state, null);
    }

    private static KmuConditionPickerEntry hiddenEntry(String id) {
        return new KmuConditionPickerEntry(
                id, id, null, KmuConditionPickerEntryState.PRESENT, null, null, false, true);
    }
}
