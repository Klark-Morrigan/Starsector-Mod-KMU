package kmu.maplayers.politicalmap.base.ribbon;

import kmu.settings.KmuPoliticalMapSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what an uncontested cell's runs are laid at, and that each of the two knobs behind it lands
 * in the half of the rule it names.
 *
 * <p>Both halves fail quietly if they drift. A shortening that reached the parting as well would
 * leave a tally whose ticks and gaps are the same length, which reads as one long run rather than
 * as a count; and the two knobs are of one type, so a read crossed with its neighbour compiles
 * clean and shows only as bands that appear where a player switched them off - or stay away where
 * they were asked for.
 */
final class UncontestedCellBandsTest {

    // A market three widths long parted by two, so a shortened run is told from the parting beside
    // it by value rather than by both happening to come out at one.
    private static final RibbonSegmentLengths AUTHORED_LENGTHS = new RibbonSegmentLengths(3, 2);

    private static final int ONE_WIDTH = 1;

    @Nested
    class ResolveRunLengths {

        @Test
        void resolveRunLengthsCutsTheMarketRunToOneWidthWhereTheRunsAreShortened() {

            var lengths = new UncontestedCellBands(true, true)
                .resolveRunLengths(AUTHORED_LENGTHS);

            assertThat(lengths.marketLengthUnits())
                .isEqualTo(ONE_WIDTH);
        }

        @Test
        void resolveRunLengthsLeavesThePartingAtItsAuthoredLengthWhereTheRunsAreShortened() {
            // The parting says the same thing on an uncontested cell as on any other - one colony
            // ends, the next begins - so the shortening has no business with it.
            var lengths = new UncontestedCellBands(true, true)
                .resolveRunLengths(AUTHORED_LENGTHS);

            assertThat(lengths.interjectionLengthUnits())
                .isEqualTo(2);
        }

        @Test
        void resolveRunLengthsKeepsTheAuthoredLengthsWhereTheRunsAreNotShortened() {

            assertThat(new UncontestedCellBands(true, false).resolveRunLengths(AUTHORED_LENGTHS))
                .isEqualTo(new RibbonSegmentLengths(3, 2));
        }
    }

    @Nested
    class ReadFromLunaSettings {

        @Test
        void readFromLunaSettingsThreadsEachKnobIntoTheHalfOfTheRuleItNames() {
            // Answered the two ways round so a read crossed with its neighbour cannot pass: the
            // cells are admitted while the shortening is off.
            try (var settingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                settingsMock
                    .when(KmuPoliticalMapSettings::shouldDrawPoliticalMapUncontestedRibbons)
                    .thenReturn(true);
                settingsMock
                    .when(KmuPoliticalMapSettings::shouldShortenPoliticalMapUncontestedRibbonRuns)
                    .thenReturn(false);

                assertThat(UncontestedCellBands.readFromLunaSettings())
                    .isEqualTo(new UncontestedCellBands(true, false));
            }
        }
    }
}
