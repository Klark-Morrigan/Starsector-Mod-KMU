package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.politicalmap.base.politics.DominantHolder;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a batch of flips is taken to have disturbed, which is what decides how much of the
 * map redraws. Both halves are silent when wrong: too few cells leaves a border drawn down one
 * side only, too few factions leaves a territory holding the shape it lost, and a batch wrongly
 * read as having flipped nothing skips the redraw entirely.
 *
 * <p>The one-sided transfers are the cases worth guarding hardest. A system taking its first
 * colony has no old holder and a decivilised one has no new holder, so a reading that took "both
 * sides present" for the definition of a flip would skip exactly the two changes a player is most
 * likely to be watching for.
 */
final class HolderFlipDisturbanceTest {

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    private static final String FLIPPED_SYSTEM = "flipped";
    private static final String NEIGHBOUR_SYSTEM = "neighbour";

    @Nested
    class HasFlips {

        @Test
        void hasFlipsIsFalseBeforeAnythingIsRecorded() {
            // The common frame: every marked system resized without changing hands, so the
            // whole redraw below the re-derive has to be skipped rather than run over nothing.
            assertThat(new HolderFlipDisturbance().hasFlips())
                .isFalse();
        }

        @Test
        void hasFlipsIsTrueForASystemThatOnlyGainedAHolder() {
            // A first colony has no losing side. Reading a flip off both sides being present
            // would leave the new territory undrawn until some later full rebuild.
            var disturbance = new HolderFlipDisturbance();

            disturbance.recordFlip(FLIPPED_SYSTEM, Set.of(), null, holderOf(HEGEMONY));

            assertThat(disturbance.hasFlips())
                .isTrue();
        }

        @Test
        void hasFlipsIsTrueForASystemThatOnlyLostItsHolder() {
            // The mirror: a decivilised system has no gaining side, and its old holder's
            // territory is exactly the one that has to stop drawing the cell.
            var disturbance = new HolderFlipDisturbance();

            disturbance.recordFlip(FLIPPED_SYSTEM, Set.of(), holderOf(HEGEMONY), null);

            assertThat(disturbance.hasFlips())
                .isTrue();
        }
    }

    @Nested
    class RecordFlip {

        @Test
        void recordFlipReshapesTheFlippedSystemAndItsNeighbours() {
            // The neighbour's own holder did not move, but the edge it shares with the flipped
            // system just turned from a same-faction seam into a national border, so it re-shapes
            // too or the border draws down one side only.
            var disturbance = new HolderFlipDisturbance();

            disturbance.recordFlip(
                FLIPPED_SYSTEM,
                List.of(NEIGHBOUR_SYSTEM),
                holderOf(HEGEMONY),
                holderOf(TRITACHYON));

            assertThat(disturbance.getCellIdsToReshape())
                .containsExactly(FLIPPED_SYSTEM, NEIGHBOUR_SYSTEM);
        }

        @Test
        void recordFlipNamesBothSidesOfATransfer() {
            // Both outlines moved - one lost the cell, the other gained it - so both rebuild,
            // and no third faction's rings trace a cell that moved.
            var disturbance = new HolderFlipDisturbance();

            disturbance.recordFlip(
                FLIPPED_SYSTEM,
                List.of(NEIGHBOUR_SYSTEM),
                holderOf(HEGEMONY),
                holderOf(TRITACHYON));

            assertThat(disturbance.getAffectedFactionIds())
                .containsExactly(HEGEMONY, TRITACHYON);
        }

        @Test
        void recordFlipNamesOnlyTheGainingFactionWhenNobodyHeldItBefore() {
            // Nobody held it, so there is no losing territory to rebuild - and naming one would
            // send a faction that never drew this cell through a rebuild for nothing.
            var disturbance = new HolderFlipDisturbance();

            disturbance.recordFlip(FLIPPED_SYSTEM, Set.of(), null, holderOf(TRITACHYON));

            assertThat(disturbance.getAffectedFactionIds())
                .containsExactly(TRITACHYON);
        }

        @Test
        void recordFlipNamesOnlyTheLosingFactionWhenNobodyHoldsItNow() {
            
            var disturbance = new HolderFlipDisturbance();

            disturbance.recordFlip(FLIPPED_SYSTEM, Set.of(), holderOf(HEGEMONY), null);

            assertThat(disturbance.getAffectedFactionIds())
                .containsExactly(HEGEMONY);
        }

        @Test
        void recordFlipCountsACellOnceWhenTwoFlipsDisturbIt() {
            // Two adjacent systems flipping in one frame disturb overlapping rings: each is the
            // other's neighbour, and both name the same cells. Re-shaping a cell twice would
            // cost the frame this fold exists to save, which is why the batch accumulates rather
            // than each flip redrawing on its own.
            var disturbance = new HolderFlipDisturbance();

            disturbance.recordFlip(
                FLIPPED_SYSTEM,
                List.of(NEIGHBOUR_SYSTEM),
                holderOf(HEGEMONY),
                holderOf(TRITACHYON));
            disturbance.recordFlip(
                NEIGHBOUR_SYSTEM,
                List.of(FLIPPED_SYSTEM),
                holderOf(HEGEMONY),
                holderOf(TRITACHYON));

            assertThat(disturbance.getCellIdsToReshape())
                .containsExactly(FLIPPED_SYSTEM, NEIGHBOUR_SYSTEM);
            assertThat(disturbance.getAffectedFactionIds())
                .containsExactly(HEGEMONY, TRITACHYON);
        }
    }

    // Only the faction id is read here, so the shades are inert placeholders.
    private static DominantHolder holderOf(String factionId) {
        return new DominantHolder(factionId, Color.GRAY, Color.GRAY);
    }
}
