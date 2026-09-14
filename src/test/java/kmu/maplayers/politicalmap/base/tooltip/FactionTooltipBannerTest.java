package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.MARKED_LABEL_RUN;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.MARK_RUN;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.buildEmptySector;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the faction as a verdict about the whole system rather than as an entry in its table: crest and
 * name travel inside the line's own words so the two centre together, and a faction the sector no longer
 * knows still speaks as its bare ID rather than as a blank.
 */
final class FactionTooltipBannerTest {

    private static final String HEGEMONY = "hegemony";
    private static final String HEGEMONY_CREST = "graphics/hegemony_crest.png";

    @BeforeEach
    void installColours() {
        CellTooltipPaletteFake.installPalette();
    }

    @AfterEach
    void clearColours() {
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class BuildFactionBanner {

        @Test
        void buildFactionBannerCarriesTheCrestAndTheNameAsOneSentence() {
            // The crest rides inside the label, so a verdict about the system centres as one line
            // instead of anchoring its image to a column a centred line has left - and it opens on that
            // image exactly as the listed lines below it do.
            var row = FactionTooltipBanner.buildFactionBanner(buildSectorKnowingHegemony(), HEGEMONY);

            assertThat(readLabelRun(row, MARK_RUN))
                .isEqualTo(new ImageSpan(HEGEMONY_CREST));
            assertThat(readLabelRun(row, MARKED_LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", TEXT));
        }

        @Test
        void buildFactionBannerFallsBackToTheIdForAnUnknownFaction() {

            var row = FactionTooltipBanner.buildFactionBanner(buildEmptySector(), "ghost_faction");

            assertThat(row.labelRuns())
                .containsExactly(new TextSpan("ghost_faction", TEXT));
        }
    }

    private static SectorAPI buildSectorKnowingHegemony() {

        var sectorMock = buildEmptySector();

        stubFaction(sectorMock, HEGEMONY, "The Hegemony", HEGEMONY_CREST);

        return sectorMock;
    }
}
