package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.buildEmptySector;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how a faction appears wherever a hovered system's breakdown lists one: its long title beside its
 * crest, its bare ID when the sector no longer knows it, and whatever the block counts it in.
 */
final class FactionTooltipLineTest {

    private static final String HEGEMONY = "hegemony";
    private static final String HEGEMONY_CREST = "graphics/hegemony_crest.png";
    private static final CellTooltipMark HEGEMONY_MARK =
        CellTooltipMark.resolveMarkAsAuthored(HEGEMONY_CREST);

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

    private static SectorAPI buildSectorKnowingHegemony() {

        var sectorMock = buildEmptySector();

        stubFaction(sectorMock, HEGEMONY, "The Hegemony", HEGEMONY_CREST);

        return sectorMock;
    }
}
