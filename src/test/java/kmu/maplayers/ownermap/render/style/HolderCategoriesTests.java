package kmu.maplayers.ownermap.render.style;

import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuOwnerMapStyleSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the categories every layer painting holders declares: the four styles read into one map
 * keyed by the holder categories, the uninhabited outline riding on the rebuild's own sampling of
 * the sidebar pick, each owned category's name style threaded from its own two settings, a
 * faction as the full-strength category, and a settled unowned cell falling to decivilised where an
 * empty one falls to uninhabited.
 */
final class HolderCategoriesTests {

    // Distinct sentinels so a name style fed from the wrong setting is caught by value.
    private static final double FACTION_NAME_OPACITY = 0.25;
    private static final double INDEPENDENT_NAME_OPACITY = 0.75;

    @Nested
    class ReadCategoryStyles {

        @Test
        void readsOneStylePerHolderCategoryInDeclarationOrder() {

            try (var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                var styles = HolderCategories.INSTANCE.readCategoryStyles(
                    ContentInputsFixtures.createInputsOutlining(false));

                assertThat(styles)
                    .containsOnlyKeys(OwnerMapCategory.values());
                assertThat(styles.keySet())
                    .containsExactly(OwnerMapCategory.values());
            }
        }

        @Test
        void outlinesUninhabitedSpaceWhenTheSampledPickDrawsIt() {

            try (var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                var styles = HolderCategories.INSTANCE.readCategoryStyles(
                    ContentInputsFixtures.createInputsOutlining(true));

                assertThat(styles.get(OwnerMapCategory.UNINHABITED).outer().colour())
                    .isEqualTo(FactionPaletteSlot.PRIMARY);
            }
        }

        @Test
        void leavesUninhabitedSpaceUnoutlinedWhenTheSampledPickHidesIt() {

            try (var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                var styles = HolderCategories.INSTANCE.readCategoryStyles(
                    ContentInputsFixtures.createInputsOutlining(false));

                assertThat(styles.get(OwnerMapCategory.UNINHABITED).outer().colour())
                    .isNull();
            }
        }
    }

    @Nested
    class ReadNameStyles {

        @Test
        void threadsEachOwnedCategorysColourChoiceAndOpacityIntoItsOwnStyle() {
            // Both owned categories hold the same shape - an outer-border colour choice and a name
            // opacity - so a crossed pair would still type-check and would only show as independent
            // space's names taking the core factions' shade in play.
            try (var settingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                settingsMock
                    .when(KmuOwnerMapStyleSettings::getFactionOuterBorderColour)
                    .thenReturn(FactionPaletteChoice.PRIMARY);
                settingsMock
                    .when(KmuOwnerMapStyleSettings::getFactionNameOpacity)
                    .thenReturn(FACTION_NAME_OPACITY);
                settingsMock
                    .when(KmuOwnerMapStyleSettings::getIndependentOuterBorderColour)
                    .thenReturn(FactionPaletteChoice.SECONDARY);
                settingsMock
                    .when(KmuOwnerMapStyleSettings::getIndependentNameOpacity)
                    .thenReturn(INDEPENDENT_NAME_OPACITY);

                assertThat(HolderCategories.INSTANCE.readNameStyles())
                    .containsOnly(
                        entry(
                            OwnerMapCategory.FACTION,
                            new ElementStyle(FactionPaletteSlot.PRIMARY, FACTION_NAME_OPACITY)),
                        entry(
                            OwnerMapCategory.INDEPENDENT,
                            new ElementStyle(FactionPaletteSlot.SECONDARY, INDEPENDENT_NAME_OPACITY)));
            }
        }
    }

    @Nested
    class ResolveFullStrengthCategory {

        @Test
        void answersTheFactionCategory() {

            assertThat(HolderCategories.INSTANCE.resolveFullStrengthCategory())
                .isEqualTo(OwnerMapCategory.FACTION);
        }
    }

    @Nested
    class ResolveUnownedCategory {

        @Test
        void answersDecivilisedForASettledCell() {

            assertThat(HolderCategories.INSTANCE.resolveUnownedCategory(true))
                .isEqualTo(OwnerMapCategory.DECIVILISED);
        }

        @Test
        void answersUninhabitedForAnEmptyCell() {

            assertThat(HolderCategories.INSTANCE.resolveUnownedCategory(false))
                .isEqualTo(OwnerMapCategory.UNINHABITED);
        }
    }
}
