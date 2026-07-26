package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.util.Misc;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link CoreTerritoryRow}: a system under no decree yields nothing, a decreed one names its
 * faction with the core status marked out in the highlight colour and no score, and a faction the
 * sector cannot resolve still shows as its bare id.
 */
final class CoreTerritoryRowTest {
    private static final Color HIGHLIGHT = new Color(255, 200, 100);
    private static final String CREST = "graphics/hegemony_crest.png";
    private static final float MEMBER_INDENT = 14f;
    private static final float TOLERANCE = 0.001f;

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void installStringsAndColours() {
        // Settings first, then the Misc statics: Misc's class initialiser reads the settings, so
        // mocking it against an uninstalled settings proxy would fail on class load.
        StarsectorSettingsFake.installSettings();
        miscMock = Mockito.mockStatic(Misc.class);
        miscMock.when(Misc::getTextColor).thenReturn(Color.LIGHT_GRAY);
        miscMock.when(Misc::getHighlightColor).thenReturn(HIGHLIGHT);
    }

    @AfterEach
    void clearStringsAndColours() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class ResolveCoreTerritoryRow {

        @Test
        void resolveCoreTerritoryRowIsEmptyWithoutACoreFaction() {
            assertThat(CoreTerritoryRow.resolveCoreTerritoryRow(sectorKnowing(), null)).isEmpty();
        }

        @Test
        void resolveCoreTerritoryRowIsEmptyForABlankCoreFaction() {
            // A memory flag written empty is no decree, so it must not draw a nameless core line.
            assertThat(CoreTerritoryRow.resolveCoreTerritoryRow(sectorKnowing(), " ")).isEmpty();
        }

        @Test
        void resolveCoreTerritoryRowNamesTheCoreFactionWithItsCrest() {
            var sector = sectorKnowing();

            var row = CoreTerritoryRow.resolveCoreTerritoryRow(sector, "hegemony").orElseThrow();

            assertThat(row.text()).isEqualTo("The Hegemony");
            assertThat(row.crestSpritePath()).isEqualTo(CREST);
        }

        @Test
        void resolveCoreTerritoryRowMarksTheCoreStatusInTheHighlightColour() {
            // The status is the point of the line, so it is picked out beside the plainly-coloured
            // faction name rather than blending into it.
            var row = CoreTerritoryRow.resolveCoreTerritoryRow(sectorKnowing(), "hegemony")
                    .orElseThrow();

            assertThat(row.marker()).isEqualTo("core territory");
            assertThat(row.markerColor()).isEqualTo(HIGHLIGHT);
            assertThat(row.textColor()).isEqualTo(Color.LIGHT_GRAY);
        }

        @Test
        void resolveCoreTerritoryRowCarriesNoScore() {
            // A core is held by decree, not won by a number, so the row leaves the value column empty.
            var row = CoreTerritoryRow.resolveCoreTerritoryRow(sectorKnowing(), "hegemony")
                    .orElseThrow();

            assertThat(row.value()).isEmpty();
        }

        @Test
        void resolveCoreTerritoryRowNestsTheLineUnderTheSystemName() {
            // The core is a fact about the system named in the header above it, not a block of its
            // own, so it indents like the status line rather than sitting flush as a section would.
            var row = CoreTerritoryRow.resolveCoreTerritoryRow(sectorKnowing(), "hegemony")
                    .orElseThrow();

            assertThat(row.indent()).isCloseTo(MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void resolveCoreTerritoryRowFallsBackToTheIdForAnUnknownFaction() {
            var sectorMock = mock(SectorAPI.class);

            var row = CoreTerritoryRow.resolveCoreTerritoryRow(sectorMock, "ghost_faction")
                    .orElseThrow();

            assertThat(row.text()).isEqualTo("ghost_faction");
            assertThat(row.crestSpritePath()).isNull();
        }
    }

    private static SectorAPI sectorKnowing() {
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getDisplayNameLong()).thenReturn("The Hegemony");
        when(factionMock.getCrest()).thenReturn(CREST);
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getFaction("hegemony")).thenReturn(factionMock);
        return sectorMock;
    }
}
