package kmu.maplayers.ownermap.owners.holders.base;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.ownermap.render.style.OwnerPaletteSlot;
import kmu.settings.KmuOwnerMapStyleSettings;
import kmu.settings.OwnerPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins how the four holder category styles read the settings: each owned category threads its
 * eight settings into the matching {@link CategoryStyle} slots (a swapped fill/outer/inner or
 * opacity/width would show here), and a factionless category collapses to neutral-colour elements
 * with no colour choice - a decivilised cell a fill plus an outline, an uninhabited cell an outline
 * whose on/off is the sidebar toggle.
 */
final class HolderCategoryStyleReaderTests {

    // Distinct sentinels so a slot that reads the wrong setting is caught by value, not
    // just by type.
    private static final double FILL_OPACITY = 0.11;
    private static final double OUTER_OPACITY = 0.22;
    private static final double OUTER_WIDTH = 3.3;
    private static final double INNER_OPACITY = 0.44;
    private static final double INNER_WIDTH = 5.5;
    private static final double NEUTRAL_OPACITY = 0.66;
    private static final double NEUTRAL_WIDTH = 7.7;
    private static final double NEUTRAL_FILL_OPACITY = 0.88;

    @Nested
    class ReadFactionStyle {

        @Test
        void threadsEachFactionSettingIntoItsMatchingSlot() {

            try (var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getFactionFillColour)
                    .thenReturn(OwnerPaletteChoice.PRIMARY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getFactionFillOpacity)
                    .thenReturn(FILL_OPACITY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getFactionOuterBorderColour)
                    .thenReturn(OwnerPaletteChoice.SECONDARY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getFactionOuterBorderOpacity)
                    .thenReturn(OUTER_OPACITY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getFactionOuterBorderWidth)
                    .thenReturn(OUTER_WIDTH);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getFactionInnerBorderColour)
                    .thenReturn(OwnerPaletteChoice.NONE);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getFactionInnerBorderOpacity)
                    .thenReturn(INNER_OPACITY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getFactionInnerBorderWidth)
                    .thenReturn(INNER_WIDTH);

                var style = HolderCategoryStyleReader.readFactionStyle();

                assertThat(style)
                    .isEqualTo(new CategoryStyle(
                        new ElementStyle(
                            OwnerPaletteSlot.PRIMARY,
                            FILL_OPACITY),
                        new ElementStyle(
                            OwnerPaletteSlot.SECONDARY,
                            OUTER_OPACITY),
                        OUTER_WIDTH,
                        new ElementStyle(
                            null,
                            INNER_OPACITY),
                        INNER_WIDTH));
            }
        }
    }

    @Nested
    class ReadIndependentStyle {

        @Test
        void threadsEachIndependentSettingIntoItsMatchingSlot() {

            try (var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getIndependentFillColour)
                    .thenReturn(OwnerPaletteChoice.SECONDARY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getIndependentFillOpacity)
                    .thenReturn(FILL_OPACITY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getIndependentOuterBorderColour)
                    .thenReturn(OwnerPaletteChoice.PRIMARY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getIndependentOuterBorderOpacity)
                    .thenReturn(OUTER_OPACITY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getIndependentOuterBorderWidth)
                    .thenReturn(OUTER_WIDTH);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getIndependentInnerBorderColour)
                    .thenReturn(OwnerPaletteChoice.NONE);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getIndependentInnerBorderOpacity)
                    .thenReturn(INNER_OPACITY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getIndependentInnerBorderWidth)
                    .thenReturn(INNER_WIDTH);

                var style = HolderCategoryStyleReader.readIndependentStyle();

                assertThat(style)
                    .isEqualTo(new CategoryStyle(
                        new ElementStyle(
                            OwnerPaletteSlot.SECONDARY,
                            FILL_OPACITY),
                        new ElementStyle(
                            OwnerPaletteSlot.PRIMARY,
                            OUTER_OPACITY),
                        OUTER_WIDTH,
                        new ElementStyle(
                            null,
                            INNER_OPACITY),
                        INNER_WIDTH));
            }
        }
    }

    @Nested
    class ReadDecivilisedStyle {

        @Test
        void drawsTheFillAndOutlineInTheNeutralColour() {

            try (var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getDecivilisedFillOpacity)
                    .thenReturn(NEUTRAL_FILL_OPACITY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getDecivilisedBorderOpacity)
                    .thenReturn(NEUTRAL_OPACITY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getDecivilisedBorderWidth)
                    .thenReturn(NEUTRAL_WIDTH);

                var style = HolderCategoryStyleReader.readDecivilisedStyle();

                // Both drawn elements route through a PRIMARY palette slot (which resolves to
                // the neutral colour for a factionless cell), with only the inner seam off -
                // and each takes its own opacity, so a swapped pair would show as a value swap.
                assertThat(style)
                    .isEqualTo(new CategoryStyle(
                        new ElementStyle(
                            OwnerPaletteSlot.PRIMARY,
                            NEUTRAL_FILL_OPACITY),
                        new ElementStyle(
                            OwnerPaletteSlot.PRIMARY,
                            NEUTRAL_OPACITY),
                        NEUTRAL_WIDTH,
                        ElementStyle.NOT_DRAWN,
                        0));
            }
        }

        @Test
        void leavesTheFillUndrawnAtZeroOpacity() {

            try (var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getDecivilisedFillOpacity)
                    .thenReturn(0.0);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getDecivilisedBorderOpacity)
                    .thenReturn(NEUTRAL_OPACITY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getDecivilisedBorderWidth)
                    .thenReturn(NEUTRAL_WIDTH);

                var style = HolderCategoryStyleReader.readDecivilisedStyle();

                // The fill opacity is the fill's only on/off - a factionless cell has no colour
                // choice to turn off - so zero has to read as "not drawn" all the way down.
                assertThat(style.fill().isDrawn())
                    .isFalse();
                assertThat(style.outer().isDrawn())
                    .isTrue();
            }
        }
    }

    @Nested
    class ReadUninhabitedStyle {

        @Test
        void drawsTheOutlineInTheNeutralColourWhenTheSidebarToggleIsOn() {
            // The on/off arrives from the rebuild's own sampling of the sidebar preference rather
            // than from a settings field, so this category is settled by the argument and the two
            // stubbed knobs together and neither can satisfy the assertion alone.
            try (var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getUninhabitedBorderOpacity)
                    .thenReturn(NEUTRAL_OPACITY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getUninhabitedBorderWidth)
                    .thenReturn(NEUTRAL_WIDTH);

                var style = HolderCategoryStyleReader.readUninhabitedStyle(true);

                assertThat(style)
                    .isEqualTo(new CategoryStyle(
                        ElementStyle.NOT_DRAWN,
                        new ElementStyle(
                            OwnerPaletteSlot.PRIMARY,
                            NEUTRAL_OPACITY
                        ),
                        NEUTRAL_WIDTH,
                        ElementStyle.NOT_DRAWN,
                        0));
            }
        }

        @Test
        void hidesTheOutlineButKeepsItsGeometryWhenTheSidebarToggleIsOff() {

            try (var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getUninhabitedBorderOpacity)
                    .thenReturn(NEUTRAL_OPACITY);
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::getUninhabitedBorderWidth)
                    .thenReturn(NEUTRAL_WIDTH);

                var style = HolderCategoryStyleReader.readUninhabitedStyle(false);

                // The outer slot turns off, yet its opacity and width still pass through so the
                // sole difference from the drawn case is the slot. Off reaches the theme as an
                // absent selection rather than as a named "No color", since the tier reads
                // absence as "paints nothing" without knowing this map's option set.
                assertThat(style.outer().colour())
                    .isNull();
                assertThat(style.outer().opacity())
                    .isEqualTo(NEUTRAL_OPACITY);
                assertThat(style.outerWidth())
                    .isEqualTo(NEUTRAL_WIDTH);
            }
        }
    }
}
