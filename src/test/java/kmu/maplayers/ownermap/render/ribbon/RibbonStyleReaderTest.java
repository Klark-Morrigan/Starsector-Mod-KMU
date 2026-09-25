package kmu.maplayers.ownermap.render.ribbon;

import kmu.settings.KmuOwnerMapRibbonSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins that each band knob lands in the slot it names, and that the mitre limit stays the code's
 * own rather than becoming a knob beside them.
 *
 * <p>Worth pinning by value because the sizes are interchangeable by type: a width read into the
 * pad, or the two run lengths handed over the wrong way round, compiles clean and shows only as a
 * band that looks slightly off in play - and "slightly off" is indistinguishable from a slider set
 * somewhere odd. Distinct sentinels per knob turn that into a failing test.
 */
final class RibbonStyleReaderTest {

    // Distinct per knob so a slot reading its neighbour's setting is caught by value.
    private static final double WIDTH_WORLD = 111.0;
    private static final double INSET_PAD_WORLD = 22.0;
    private static final int SEGMENT_LENGTH_UNITS = 7;
    private static final int INTERJECTION_LENGTH_UNITS = 4;

    // Read as the opposite of what it ships as, so a reader hard-wiring the shipped answer instead
    // of asking for the player's would show here rather than only on a map nobody is looking at.
    private static final boolean BAND_ALWAYS_DRAWN = false;

    // The authored mitre limit, which is no knob: it is the angle past which a corner's mitre
    // becomes a spike, a property of stroking rather than of how the readout looks.
    private static final double AUTHORED_MITER_SPIKE_LIMIT = 2.0;

    @Nested
    class ReadRibbonStyle {

        @Test
        void readRibbonStyleThreadsEachBandSettingIntoItsMatchingSize() {
            try (var settingsMock = mockStatic(KmuOwnerMapRibbonSettings.class)) {

                stubBandSettings(settingsMock);

                var style = RibbonStyleReader.readRibbonStyle();

                assertThat(style.widthWorld())
                    .isEqualTo(WIDTH_WORLD);
                assertThat(style.insetPadWorld())
                    .isEqualTo(INSET_PAD_WORLD);
                assertThat(style.lengths().marketLengthUnits())
                    .isEqualTo(SEGMENT_LENGTH_UNITS);
                assertThat(style.lengths().interjectionLengthUnits())
                    .isEqualTo(INTERJECTION_LENGTH_UNITS);
                assertThat(style.isBandAlwaysDrawn())
                    .isEqualTo(BAND_ALWAYS_DRAWN);
            }
        }

        @Test
        void readRibbonStyleKeepsTheMitreLimitAuthored() {
            try (var settingsMock = mockStatic(KmuOwnerMapRibbonSettings.class)) {

                stubBandSettings(settingsMock);

                assertThat(RibbonStyleReader.readRibbonStyle().miterSpikeLimit())
                    .isEqualTo(AUTHORED_MITER_SPIKE_LIMIT);
            }
        }
    }

    private static void stubBandSettings(MockedStatic<KmuOwnerMapRibbonSettings> settingsMock) {

        settingsMock
            .when(KmuOwnerMapRibbonSettings::getOwnerMapRibbonWidth)
            .thenReturn(WIDTH_WORLD);
        settingsMock
            .when(KmuOwnerMapRibbonSettings::getOwnerMapRibbonInsetPad)
            .thenReturn(INSET_PAD_WORLD);
        settingsMock
            .when(KmuOwnerMapRibbonSettings::getOwnerMapRibbonSegmentLength)
            .thenReturn(SEGMENT_LENGTH_UNITS);
        settingsMock
            .when(KmuOwnerMapRibbonSettings::getOwnerMapRibbonInterjectionLength)
            .thenReturn(INTERJECTION_LENGTH_UNITS);
        settingsMock
            .when(KmuOwnerMapRibbonSettings::shouldAlwaysDrawOwnerMapRibbons)
            .thenReturn(BAND_ALWAYS_DRAWN);
    }
}
