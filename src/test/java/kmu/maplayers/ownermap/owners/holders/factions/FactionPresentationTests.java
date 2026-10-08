package kmu.maplayers.ownermap.owners.holders.factions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.starsector.SectorFactionFixtures.HEGEMONY;
import static kmu.starsector.SectorFactionFixtures.HEGEMONY_CREST;
import static kmu.starsector.SectorFactionFixtures.buildEmptySector;
import static kmu.starsector.SectorFactionFixtures.buildSectorKnowingHegemony;
import static kmu.starsector.SectorFactionFixtures.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how a faction presents wherever a hovered system's breakdown names it: its long title beside
 * its crest, its bare ID and no crest when the sector does not know it.
 */
final class FactionPresentationTests {

    @Nested
    class ResolvePresentation {

        @Test
        void namesTheFactionByItsLongTitleAndCrest() {

            var presentation =
                FactionPresentation.resolvePresentation(buildSectorKnowingHegemony(), HEGEMONY);

            assertThat(presentation)
                .isEqualTo(new FactionPresentation("The Hegemony", HEGEMONY_CREST));
        }

        @Test
        void fallsBackToTheIdForAnUnknownFaction() {
            // A breakdown naming a faction the sector has lost still has to name something, and the
            // ID is the only thing left that identifies it.
            var presentation =
                FactionPresentation.resolvePresentation(buildEmptySector(), "ghost_faction");

            assertThat(presentation)
                .isEqualTo(new FactionPresentation("ghost_faction", null));
        }

        @Test
        void presentsTheIdWithNoCrestWithoutASector() {

            var presentation = FactionPresentation.resolvePresentation(null, HEGEMONY);

            assertThat(presentation)
                .isEqualTo(new FactionPresentation("hegemony", null));
        }

        @Test
        void presentsTheIdForAKnownFactionWithABlankLongName() {

            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "luddic_path", "  ", null);

            assertThat(FactionPresentation.resolvePresentation(sectorMock, "luddic_path"))
                .isEqualTo(new FactionPresentation("luddic_path", null));
        }

        @Test
        void presentsTheIdForAKnownFactionWithNoLongName() {

            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "luddic_path", null, null);

            assertThat(FactionPresentation.resolvePresentation(sectorMock, "luddic_path"))
                .isEqualTo(new FactionPresentation("luddic_path", null));
        }

        @Test
        void trimsTheWhitespaceAroundALongName() {

            var sectorMock = buildEmptySector();

            stubFaction(sectorMock, "luddic_path", "  Luddic Path  ", null);

            assertThat(FactionPresentation.resolvePresentation(sectorMock, "luddic_path"))
                .isEqualTo(new FactionPresentation("Luddic Path", null));
        }

        @Test
        void carriesNoCrestForAFactionWithoutOne() {
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
}
