package kmu.conditions.ui.picker.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerModelTest {
    private static final KmuConditionPickerLocation EMPTY_LOCATION =
            new KmuConditionPickerLocation(null, null, null, null, null, null, null);

    @Nested
    class FindEntry {

        @Test
        void findEntryReturnsPresentForMatchingId() {
            var hotEntry = buildEntry("hot", KmuConditionPickerEntryState.PRESENT);
            var model = buildModel(hotEntry);

            assertThat(model.findEntry("hot")).contains(hotEntry);
        }

        @Test
        void findEntryReturnsEmptyForNonMatchingId() {
            var model = buildModel(buildEntry("hot", KmuConditionPickerEntryState.PRESENT));

            assertThat(model.findEntry("cold")).isEmpty();
        }
    }

    @Nested
    class GetVisibleCount {

        @Test
        void getVisibleCountCountsPresentNonHiddenEntries() {
            var model = buildModel(
                    buildEntry("hot", KmuConditionPickerEntryState.PRESENT),
                    buildHiddenEntry("no_atmosphere"),
                    buildEntry("cold", KmuConditionPickerEntryState.ABSENT));

            assertThat(model.getVisibleCount()).isEqualTo(1);
        }
    }

    @Nested
    class GetAvailableCount {

        @Test
        void getAvailableCountCountsAbsentEntries() {
            var model = buildModel(
                    buildEntry("hot", KmuConditionPickerEntryState.PRESENT),
                    buildEntry("cold", KmuConditionPickerEntryState.ABSENT),
                    buildEntry("arid", KmuConditionPickerEntryState.ABSENT));

            assertThat(model.getAvailableCount()).isEqualTo(2);
        }
    }

    // --- helpers ---

    private static KmuConditionPickerModel buildModel(KmuConditionPickerEntry... entries) {
        return new KmuConditionPickerModel(List.of(entries), EMPTY_LOCATION);
    }

    private static KmuConditionPickerEntry buildEntry(String id, KmuConditionPickerEntryState state) {
        return new KmuConditionPickerEntry(id, id, null, state, null);
    }

    private static KmuConditionPickerEntry buildHiddenEntry(String id) {
        return new KmuConditionPickerEntry(
                id, id, null, KmuConditionPickerEntryState.PRESENT, null, null, false, true);
    }
}
