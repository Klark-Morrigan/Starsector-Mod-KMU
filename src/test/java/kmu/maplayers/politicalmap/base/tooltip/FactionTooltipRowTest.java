package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;

import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins how a faction appears wherever a tooltip line names one: its long title beside its crest, its
 * bare id when the sector no longer knows it, whatever value the caller hands over, and always
 * indented as an entry under the block above it.
 */
final class FactionTooltipRowTest {

    private static final Color TEXT = Color.LIGHT_GRAY;

    private static final String HEGEMONY = "hegemony";
    private static final String HEGEMONY_CREST = "graphics/hegemony_crest.png";

    // The line's label is one run - the qualifier a caller may add continues it afterwards.
    private static final int LABEL_RUN = 0;

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void installColours() {
        // Settings first, then the Misc statics: Misc's class initialiser reads the settings, so
        // mocking it against an uninstalled settings proxy would fail on class load.
        StarsectorSettingsFake.installSettings();

        miscMock = Mockito.mockStatic(Misc.class);
        miscMock
            .when(Misc::getTextColor)
            .thenReturn(TEXT);
    }

    @AfterEach
    void clearColours() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class BuildFactionRow {

        @Test
        void buildFactionRowNamesTheFactionWithItsCrest() {

            var row = FactionTooltipRow.buildFactionRow(sectorKnowing(), HEGEMONY, "1,200");

            assertThat(readLabelRun(row, LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", TEXT));
            assertThat(row.labelledRow().leadingRowSlot())
                .isEqualTo(new RowSlot.Image(HEGEMONY_CREST));
        }

        @Test
        void buildFactionRowCarriesTheValueItIsHanded() {

            var row = FactionTooltipRow.buildFactionRow(sectorKnowing(), HEGEMONY, "1,200");

            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", TEXT)));
        }

        @Test
        void buildFactionRowLeavesTheValueBlankForALineCarryingNone() {
            // A line stating a fact rather than a number still fills the slot, so the value column
            // collapses for it instead of the row losing its shape.
            var row = FactionTooltipRow.buildFactionRow(sectorKnowing(), HEGEMONY, "");

            assertThat(row.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(TextSpan.createBlank(TEXT)));
        }

        @Test
        void buildFactionRowIndentsTheLineAsAnEntry() {
            // Every line naming a faction belongs to a block above it - a section heading or a group
            // header - so it reads as an entry rather than as opening a block of its own.
            var row = FactionTooltipRow.buildFactionRow(sectorKnowing(), HEGEMONY, "1,200");

            assertThat(row.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void buildFactionRowFallsBackToTheIdForAnUnknownFaction() {
            // A line naming one faction reads better as a bare id than as a blank where the name
            // belongs, and a faction with no crest simply draws its name alone.
            var sectorMock = mock(SectorAPI.class);
            var row = FactionTooltipRow.buildFactionRow(sectorMock, "ghost_faction", "");

            assertThat(readLabelTextRun(row, LABEL_RUN).text())
                .isEqualTo("ghost_faction");
            assertThat(row.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
        }
    }

    private static SectorAPI sectorKnowing() {

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
