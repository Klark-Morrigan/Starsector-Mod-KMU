package kmu.maplayers.politicalmap.base;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;

/**
 * Pins the aggregator's one job: a load runs every political-map save self-heal, so a heal added to
 * the set is covered by construction and the composition root never has to name each one. Each heal's
 * own behaviour is its holder's test; here only that each runs is pinned, with all six heals stubbed.
 */
final class PoliticalMapSaveMigrationsTest {

    @Nested
    class HealLoadedSave {

        @Test
        void healLoadedSaveRunsEveryPoliticalMapSaveHeal() {
            try (MockedStatic<PoliticalMapViewRegistry> viewRegistryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<PoliticalMapLayer> layerMock = mockStatic(PoliticalMapLayer.class);
                    MockedStatic<RecedePreferences> recedeMock =
                            mockStatic(RecedePreferences.class);
                    MockedStatic<FilterSelectionHeal> filterHealMock =
                            mockStatic(FilterSelectionHeal.class)) {
                PoliticalMapSaveMigrations.healLoadedSave();

                viewRegistryMock.verify(PoliticalMapViewRegistry::migrateLegacyOverlaySelection);
                layerMock.verify(PoliticalMapLayer::migrateLegacyStoredId);
                recedeMock.verify(RecedePreferences::migrateLegacyKeys);
                recedeMock.verify(RecedePreferences::migrateSharedKeysIntoFilterSet);
                filterHealMock.verify(
                        FilterSelectionHeal::migrateLegacySharedSelectionToActiveView);
                filterHealMock.verify(FilterSelectionHeal::healStaleSelectionAgainstActiveView);
            }
        }
    }
}
