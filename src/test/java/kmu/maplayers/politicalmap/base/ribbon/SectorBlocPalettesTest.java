package kmu.maplayers.politicalmap.base.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.starsector.StarsectorFactionFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins how a bloc's two shades are found: through the grouping's colour faction rather than
 * through the bloc id, and as nothing at all when that faction is gone.
 *
 * <p>Both matter because a bloc is not always a faction. An alliance bloc carries a synthetic id
 * no {@code FactionAPI} answers to, so asking the sector for it directly would leave every
 * alliance colourless on a view where the fills are perfectly well coloured; and a bloc the sector
 * cannot name at all has to come back null, since that null is what makes the counting rules drop
 * it instead of painting a run in a stand-in shade.
 */
final class SectorBlocPalettesTest {

    private static final String ALLIANCE_BLOC = "hegemony_compact";
    private static final String LEAD_MEMBER = "hegemony";

    private static final Color BRIGHT = new Color(140, 160, 220);
    private static final Color DARK = new Color(40, 60, 120);

    @Nested
    class ReadBlocPalette {

        @Test
        void readsTheShadesOfTheFactionTheGroupingColoursTheBlocBy() {
            // The alliance case: the bloc's own id names no faction, and its lead member's authored
            // pair is what the map paints it in.
            var sectorMock = StarsectorFactionFixtures.buildSectorShadingFaction(
                LEAD_MEMBER,
                BRIGHT,
                DARK);

            assertThat(readPaletteFrom(sectorMock, ALLIANCE_BLOC))
                .isEqualTo(new FactionPalette(BRIGHT, DARK));
        }

        @Test
        void readsNoPaletteForABlocWhoseColourFactionIsGone() {

            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getFaction(LEAD_MEMBER))
                .thenReturn(null);

            assertThat(readPaletteFrom(sectorMock, ALLIANCE_BLOC))
                .isNull();
        }

        @Test
        void readsNoPaletteWithoutASector() {
            // The sector is absent outside a running game, and a band asked for one then answers
            // with no colour rather than throwing on the way to a map nobody is looking at.
            assertThat(readPaletteFrom(null, ALLIANCE_BLOC))
                .isNull();
        }
    }

    // The palettes as one alliance's bloc reads them: the bloc folds to its lead member, which is
    // the faction the sector is asked for.
    private static FactionPalette readPaletteFrom(SectorAPI sector, String blocId) {
        var grouping = new HolderGrouping(
            Map.of(LEAD_MEMBER, ALLIANCE_BLOC),
            Map.of(ALLIANCE_BLOC, LEAD_MEMBER),
            Map.of(ALLIANCE_BLOC, "The Hegemony Compact"));

        return new SectorBlocPalettes(sector, grouping)
            .readBlocPalette(blocId);
    }
}
