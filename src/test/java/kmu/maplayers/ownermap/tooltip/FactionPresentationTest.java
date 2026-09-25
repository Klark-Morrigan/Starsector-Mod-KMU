package kmu.maplayers.ownermap.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.ownermap.tooltip.SectorFactionsFake.buildEmptySector;
import static kmu.maplayers.ownermap.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how a faction presents wherever a hovered system's breakdown names it: its long title beside
 * its crest, its bare ID and no crest when the sector does not know it.
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
            // ID is the only thing left that identifies it.
            var presentation =
                FactionPresentation.resolvePresentation(buildEmptySector(), "ghost_faction");

            assertThat(presentation)
                .isEqualTo(new FactionPresentation("ghost_faction", null));
        }

        @Test
        void resolvePresentationCarriesNoCrestForAFactionWithoutOne() {
            // A faction with nothing authored presents by name alone rather than by a path that
            // would fail to load wherever the line is drawn.
            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "luddic_path", "Luddic Path", null);

            var presentation =
                FactionPresentation.resolvePresentation(sectorMock, "luddic_path");

            assertThat(presentation)
                .isEqualTo(new FactionPresentation("Luddic Path", null));
        }
    }

    private static SectorAPI buildSectorKnowingHegemony() {

        var sectorMock = buildEmptySector();

        stubFaction(sectorMock, HEGEMONY, "The Hegemony", HEGEMONY_CREST);

        return sectorMock;
    }
}
