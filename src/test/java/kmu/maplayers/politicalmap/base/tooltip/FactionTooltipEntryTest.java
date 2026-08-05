package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipRows;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.buildEmptySector;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how a faction appears wherever a hovered system's breakdown lists one: its long title beside its
 * crest, its bare id when the sector no longer knows it, and whatever the block counts it in. Also that
 * a faction listed on its own is an entry made up of nothing, which is what keeps a claim - where no
 * faction breaks down any further - flat.
 */
final class FactionTooltipEntryTest {

    private static final String HEGEMONY = "hegemony";
    private static final String HEGEMONY_CREST = "graphics/hegemony_crest.png";

    @Nested
    class BuildFactionLine {

        @Test
        void buildFactionLineNamesTheFactionWithItsCrestAndValue() {

            var line = FactionTooltipEntry.buildFactionLine(
                buildSectorKnowingHegemony(),
                HEGEMONY,
                "1,200");

            assertThat(line)
                .isEqualTo(CellTooltipEntryLine.createLine(
                    HEGEMONY_CREST,
                    "The Hegemony",
                    "1,200"));
        }

        @Test
        void buildFactionLineFallsBackToTheIdForAnUnknownFaction() {
            // A line naming one faction reads better as a bare id than as a blank where the name
            // belongs, and a faction with no crest simply draws its name alone.
            var line = FactionTooltipEntry.buildFactionLine(
                buildEmptySector(),
                "ghost_faction",
                CellTooltipRows.NO_SCORE);

            assertThat(line.labelText())
                .isEqualTo("ghost_faction");
            assertThat(line.iconSpritePath())
                .isNull();
        }
    }

    @Nested
    class BuildFactionEntry {

        @Test
        void buildFactionEntryListsTheFactionAsMadeUpOfNothing() {
            // Every faction listed anywhere but under an alliance breaks down no further, so the entry
            // carries the line alone - which is what leaves the claims box flat.
            var entry = FactionTooltipEntry.buildFactionEntry(
                buildSectorKnowingHegemony(),
                HEGEMONY,
                "1,200");

            assertThat(entry.line())
                .isEqualTo(CellTooltipEntryLine.createLine(
                    HEGEMONY_CREST,
                    "The Hegemony",
                    "1,200"));
            assertThat(entry.children())
                .isEmpty();
        }
    }

    private static SectorAPI buildSectorKnowingHegemony() {

        var sectorMock = buildEmptySector();

        stubFaction(sectorMock, HEGEMONY, "The Hegemony", HEGEMONY_CREST);

        return sectorMock;
    }
}
