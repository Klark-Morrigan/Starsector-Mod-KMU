package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the faction as a verdict about the whole system rather than as an entry in its table: crest and
 * name travel inside the line's own words so the two centre together, and a faction the sector no longer
 * knows still speaks as its bare id rather than as a blank.
 */
final class FactionTooltipBannerTest {

    private static final String HEGEMONY = "hegemony";
    private static final String HEGEMONY_CREST = "graphics/hegemony_crest.png";

    // A banner led by a crest opens on that image, so its words sit one run later.
    private static final int CREST_RUN = 0;
    private static final int LABEL_RUN = 1;

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
            // The crest rides inside the label rather than in the gutter the entries below align to, so
            // a verdict about the system centres as one line instead of anchoring to a column it has
            // left.
            var row = FactionTooltipBanner.buildFactionBanner(buildSectorKnowingHegemony(), HEGEMONY);

            assertThat(readLabelRun(row, CREST_RUN))
                .isEqualTo(new ImageSpan(HEGEMONY_CREST));
            assertThat(readLabelRun(row, LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", TEXT));
        }

        @Test
        void buildFactionBannerFallsBackToTheIdForAnUnknownFaction() {

            var row = FactionTooltipBanner.buildFactionBanner(mock(SectorAPI.class), "ghost_faction");

            assertThat(row.labelRuns())
                .containsExactly(new TextSpan("ghost_faction", TEXT));
        }
    }

    private static SectorAPI buildSectorKnowingHegemony() {

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getDisplayNameLong())
            .thenReturn("The Hegemony");
        when(factionMock.getCrest())
            .thenReturn(HEGEMONY_CREST);

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getFaction(HEGEMONY))
            .thenReturn(factionMock);

        return sectorMock;
    }
}
