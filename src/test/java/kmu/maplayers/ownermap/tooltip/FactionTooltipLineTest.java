package kmu.maplayers.ownermap.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.ownermap.tooltip.SectorFactionsFake.buildEmptySector;
import static kmu.maplayers.ownermap.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how a faction appears wherever a hovered system's breakdown lists one: its long title beside its
 * crest, its bare ID when the sector does not know it, and whatever the block counts it in - quiet
 * where the mechanic behind the block never weighed it, since every block listing both kinds has to
 * quieten the same one.
 */
final class FactionTooltipLineTest {

    private static final String HEGEMONY = "hegemony";
    private static final String HEGEMONY_CREST = "graphics/hegemony_crest.png";
    private static final CellTooltipMark HEGEMONY_MARK =
        CellTooltipMark.resolveMarkAsAuthored(HEGEMONY_CREST);

    // Whether the mechanic behind the block weighed the faction at all, named so the case reads as
    // the kind of standing it poses rather than as a bare flag.
    private static final boolean IS_WEIGHED = true;
    private static final boolean IS_NEVER_WEIGHED = false;

    @Nested
    class BuildFactionLine {

        @Test
        void buildFactionLineNamesTheFactionWithItsCrestAndValue() {

            var line = FactionTooltipLine.buildFactionLine(
                buildSectorKnowingHegemony(),
                HEGEMONY,
                "1,200");

            assertThat(line)
                .isEqualTo(CellTooltipEntryLine.createLine(
                    HEGEMONY_MARK,
                    "The Hegemony",
                    "1,200"));
        }

        @Test
        void buildFactionLineFallsBackToTheIdForAnUnknownFaction() {
            // A line naming one faction reads better as a bare ID than as a blank where the name
            // belongs, and a faction with no crest simply draws its name alone.
            var line = FactionTooltipLine.buildFactionLine(
                buildEmptySector(),
                "ghost_faction",
                CellTooltipEntryLine.NO_SCORE);

            assertThat(line.labelText())
                .isEqualTo("ghost_faction");
            assertThat(line.mark())
                .isNull();
        }
    }

    @Nested
    class BuildCountedFactionLine {

        @Test
        void buildCountedFactionLineStatesAWeighedFactionsCountInTheListsOwnShade() {
            // A faction the mechanic weighed competed on its number, so the number reads as loudly as
            // every other score in the block.
            var line = FactionTooltipLine.buildCountedFactionLine(
                buildSectorKnowingHegemony(),
                HEGEMONY,
                1200,
                IS_WEIGHED);

            assertThat(line.labelText())
                .isEqualTo("The Hegemony");
            assertThat(line.mark())
                .isEqualTo(HEGEMONY_MARK);
            assertThat(line.valueText())
                .isEqualTo("1,200");
            assertThat(line.countedValue())
                .isEqualTo(1200);
            assertThat(line.isValueUncounted())
                .isFalse();
        }

        @Test
        void buildCountedFactionLineQuietensTheNoughtOfAFactionNeverWeighed() {
            // The nought is the mechanic's statement about a faction it never reached. Drawn as loudly
            // as the scores around it, it would read as one competed for and lost.
            var line = FactionTooltipLine.buildCountedFactionLine(
                buildSectorKnowingHegemony(),
                HEGEMONY,
                0,
                IS_NEVER_WEIGHED);

            assertThat(line.labelText())
                .isEqualTo("The Hegemony");
            assertThat(line.valueText())
                .isEqualTo("0");
            assertThat(line.isValueUncounted())
                .isTrue();
        }
    }

    private static SectorAPI buildSectorKnowingHegemony() {

        var sectorMock = buildEmptySector();

        stubFaction(sectorMock, HEGEMONY, "The Hegemony", HEGEMONY_CREST);

        return sectorMock;
    }
}
