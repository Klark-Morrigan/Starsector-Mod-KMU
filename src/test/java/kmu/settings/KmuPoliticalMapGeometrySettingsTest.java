package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the one thing this reader does with what was stored rather than hand it straight back: the
 * hatch width, taken as a percentage of the spacing and held inside the range that leaves a hatch
 * looking like one.
 *
 * <p>Both ends are pinned because both answer a value the slider cannot produce. This row was read
 * as a pixel count over a wider range before the width became a share of the spacing, and LunaLib
 * prunes nothing - so a value stored under the old reading is handed straight to this one, and the
 * old ceiling arrives here as a stroke that meets its neighbour and paints the solid fill the hatch
 * exists to read apart from.
 */
final class KmuPoliticalMapGeometrySettingsTest {

    // The live field ID, as the shipped table spells it. A rename in the reader would otherwise read
    // a row the table never writes, leaving the hatch on its fallback with nothing to say why.
    private static final String HATCH_WIDTH_FIELD = "kmu_map_dev_hatchFill_width";

    @Nested
    class GetPoliticalMapHatchWidthFraction {

        @Test
        void getPoliticalMapHatchWidthFractionHandsBackTheStoredPercentageAsAFraction() {

            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readDouble(eq(HATCH_WIDTH_FIELD), anyDouble()))
                    .thenReturn(40.0);

                assertThat(KmuPoliticalMapGeometrySettings.getPoliticalMapHatchWidthFraction())
                    .isEqualTo(0.4);
            }
        }

        @Test
        void getPoliticalMapHatchWidthFractionLiftsAWidthUnderTheFloor() {
            // The old pixel range's own floor, which as a percentage is thinner than the rasteriser
            // can draw - the stroke would come out at its minimum width whatever the row says.
            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readDouble(eq(HATCH_WIDTH_FIELD), anyDouble()))
                    .thenReturn(0.5);

                assertThat(KmuPoliticalMapGeometrySettings.getPoliticalMapHatchWidthFraction())
                    .isEqualTo(0.05);
            }
        }

        @Test
        void getPoliticalMapHatchWidthFractionHoldsAWidthOverTheCeiling() {
            // The old pixel range's own ceiling, which as a percentage inks the whole gap: ink
            // meeting ink is the solid fill, not a hatch.
            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readDouble(eq(HATCH_WIDTH_FIELD), anyDouble()))
                    .thenReturn(100.0);

                assertThat(KmuPoliticalMapGeometrySettings.getPoliticalMapHatchWidthFraction())
                    .isEqualTo(0.9);
            }
        }

        @Test
        void getPoliticalMapHatchWidthFractionClampsItsOwnFallbackWhileNothingIsStored() {
            // Stood in as a substrate answering with whatever fallback it is handed, which is what
            // an unset row - and a read taken outside a running game - gets. The fallback passes the
            // clamp untouched, so a map drawn before the settings load hatches at the shipped width.
            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readDouble(anyString(), anyDouble()))
                    .thenAnswer(invocation -> invocation.getArgument(1));

                assertThat(KmuPoliticalMapGeometrySettings.getPoliticalMapHatchWidthFraction())
                    .isEqualTo(0.5);
            }
        }
    }
}
