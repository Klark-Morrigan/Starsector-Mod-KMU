package kmu.maplayers.ownermap.owners.holders.base;

import kmlib.colour.ColourPair;

import kmu.maplayers.ownermap.owners.SystemOwner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins when a spotlight opens and what it carries: the pick, and the spotlit key in the pick's own
 * palette. The pass's palette read is stood in for, having a suite of its own.
 */
final class BlocSpotlightTests {

    // The synthetic key every spotlit system carries, written out rather than read back through the
    // class that mints it, so a key that drifted would fail here.
    private static final String SPOTLIT_KEY = "$kmu_filter_spotlit";

    private static final ColourPair HEGEMONY_PALETTE = new ColourPair(Color.RED, Color.BLUE);

    private final HolderPass passMock = mock(HolderPass.class);
    private final SectorBlocPalettes palettesMock = mock(SectorBlocPalettes.class);

    @Nested
    class OpenOver {

        // Every system the pick reaches carries one holder, so it is made once here: the spotlit key
        // in the pick's own palette, which its bands take too.
        @Test
        void pairsThePickWithTheSpotlitKeyInItsPalette() {

            // ARRANGE
            var expectedSpotlight = new BlocSpotlight(
                "hegemony",
                new SystemOwner(SPOTLIT_KEY, HEGEMONY_PALETTE));

            stateThePalette("hegemony", HEGEMONY_PALETTE);

            // ACT
            var actualSpotlight = BlocSpotlight.openOver(passMock, "hegemony");

            // ASSERT
            assertThat(actualSpotlight)
                .contains(expectedSpotlight);
        }

        // No pick, no spotlight, and nothing is read for one.
        @Test
        void opensNothingOffFilter() {

            // ACT
            var actualSpotlight = BlocSpotlight.openOver(passMock, null);

            // ASSERT
            assertThat(actualSpotlight)
                .isEmpty();

            verifyNoInteractions(passMock);
        }

        // A pick whose colour faction the sector no longer names has nothing to draw in, so no
        // spotlight opens and every system draws as its real holder.
        @Test
        void opensNothingWhereThePicksColourFactionIsGone() {

            // ARRANGE
            stateThePalette("pirates", null);

            // ACT
            var actualSpotlight = BlocSpotlight.openOver(passMock, "pirates");

            // ASSERT
            assertThat(actualSpotlight)
                .isEmpty();
        }

        // What the pass's palette read answers for one bloc.
        private void stateThePalette(String blocId, ColourPair palette) {

            when(passMock.palettes())
                .thenReturn(palettesMock);
            when(palettesMock.readBlocPalette(blocId))
                .thenReturn(palette);
        }
    }

    @Nested
    class IsSpotlitBloc {

        // The pick's own systems are the ones a spotlight rekeys.
        @Test
        void acceptsThePick() {

            // ARRANGE
            var spotlight = new BlocSpotlight("hegemony", new SystemOwner(SPOTLIT_KEY, HEGEMONY_PALETTE));

            // ACT
            var actualIsSpotlit = spotlight.isSpotlitBloc("hegemony");

            // ASSERT
            assertThat(actualIsSpotlit)
                .isTrue();
        }

        // A rival keeps its own key, so the style layer recedes it.
        @Test
        void rejectsARival() {

            // ARRANGE
            var spotlight = new BlocSpotlight("hegemony", new SystemOwner(SPOTLIT_KEY, HEGEMONY_PALETTE));

            // ACT
            var actualIsSpotlit = spotlight.isSpotlitBloc("tritachyon");

            // ASSERT
            assertThat(actualIsSpotlit)
                .isFalse();
        }
    }
}
