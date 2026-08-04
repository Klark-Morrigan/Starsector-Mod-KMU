package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins how a faction presents wherever a hovered system's breakdown names it: its long title beside
 * its crest, its bare id and no crest when the sector no longer knows it.
 */
final class FactionPresentationTest {

    private static final String HEGEMONY = "hegemony";
    private static final String HEGEMONY_CREST = "graphics/hegemony_crest.png";

    @Nested
    class ResolvePresentation {

        @Test
        void resolvePresentationNamesTheFactionByItsLongTitleAndCrest() {

            var presentation =
                FactionPresentation.resolvePresentation(buildSectorKnowingHegemony(), HEGEMONY);

            assertThat(presentation)
                .isEqualTo(new FactionPresentation("The Hegemony", HEGEMONY_CREST));
        }

        @Test
        void resolvePresentationFallsBackToTheIdForAnUnknownFaction() {
            // A breakdown naming a faction the sector has lost still has to name something, and the
            // id is the only thing left that identifies it.
            var sectorMock = mock(SectorAPI.class);

            var presentation =
                FactionPresentation.resolvePresentation(sectorMock, "ghost_faction");

            assertThat(presentation)
                .isEqualTo(new FactionPresentation("ghost_faction", null));
        }

        @Test
        void resolvePresentationCarriesNoCrestForAFactionWithoutOne() {
            // A faction with nothing authored presents by name alone rather than by a path that
            // would fail to load wherever the line is drawn.
            var factionMock = mock(FactionAPI.class);

            when(factionMock.getDisplayNameLong())
                .thenReturn("Luddic Path");

            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getFaction("luddic_path"))
                .thenReturn(factionMock);

            var presentation =
                FactionPresentation.resolvePresentation(sectorMock, "luddic_path");

            assertThat(presentation)
                .isEqualTo(new FactionPresentation("Luddic Path", null));
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
