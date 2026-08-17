package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.politicalmap.base.politics.DominantHolder;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a batch of stale systems is taken to have disturbed, which is what decides how much of
 * the map redraws. Both halves are silent when wrong: too few cells leaves a border drawn down one
 * side only, too few factions leaves a territory holding the shape it lost, and a batch wrongly
 * read as having flipped nothing skips the clustering and territory work entirely.
 *
 * <p>The one-sided transfers are the cases worth guarding hardest. A system taking its first
 * colony has no old holder and a decivilised one has no new holder, so a reading that took "both
 * sides present" for the definition of a flip would skip exactly the two changes a player is most
 * likely to be watching for.
 *
 * <p>The restyle cases guard the other direction: a cell that draws differently owes a redraw and
 * nothing beyond it, so a restyle recorded as a flip would send two factions through a territory
 * rebuild for a change that moved no border.
 */
final class StalePoliticsDisturbanceTest {

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    private static final String FLIPPED_SYSTEM = "flipped";
    private static final String NEIGHBOUR_SYSTEM = "neighbour";
    private static final String RESTYLED_SYSTEM = "restyled";

    @Nested
    class HasFlips {

        @Test
        void hasFlipsIsFalseBeforeAnythingIsRecorded() {
            // The common frame: every marked system resized without changing hands, so the
            // clustering, territory and name work has to be skipped rather than run over nothing.
            assertThat(new StalePoliticsDisturbance().hasFlips())
                .isFalse();
        }

        @Test
        void hasFlipsIsTrueForASystemThatOnlyGainedAHolder() {
            // A first colony has no losing side. Reading a flip off both sides being present
            // would leave the new territory undrawn until some later full rebuild.
            var disturbance = new StalePoliticsDisturbance();

            disturbance.recordFlip(FLIPPED_SYSTEM, Set.of(), null, buildHolderOf(HEGEMONY));

            assertThat(disturbance.hasFlips())
                .isTrue();
        }

        @Test
        void hasFlipsIsTrueForASystemThatOnlyLostItsHolder() {
            // The mirror: a decivilised system has no gaining side, and its old holder's
            // territory is exactly the one that has to stop drawing the cell.
            var disturbance = new StalePoliticsDisturbance();

            disturbance.recordFlip(FLIPPED_SYSTEM, Set.of(), buildHolderOf(HEGEMONY), null);

            assertThat(disturbance.hasFlips())
                .isTrue();
        }

        @Test
        void hasFlipsIsFalseForARestyleAlone() {
            // A system whose last colony went, or one the spotlit bloc just settled, draws
            // differently with its holder exactly where it was - so nothing above the cell is
            // owed, and a batch of these must not re-fit every name on the map.
            var disturbance = new StalePoliticsDisturbance();

            disturbance.recordRestyle(RESTYLED_SYSTEM);

            assertThat(disturbance.hasFlips())
                .isFalse();
        }
    }

    @Nested
    class RecordFlip {

        @Test
        void recordFlipRedrawsTheFlippedSystemAndItsNeighbours() {
            // The neighbour's own holder did not move, but the edge it shares with the flipped
            // system just turned from a same-faction seam into a national border, so it re-shapes
            // too or the border draws down one side only.
            var disturbance = new StalePoliticsDisturbance();

            disturbance.recordFlip(
                FLIPPED_SYSTEM,
                List.of(NEIGHBOUR_SYSTEM),
                buildHolderOf(HEGEMONY),
                buildHolderOf(TRITACHYON));

            assertThat(disturbance.getCellIdsToRedraw())
                .containsExactly(FLIPPED_SYSTEM, NEIGHBOUR_SYSTEM);
        }

        @Test
        void recordFlipNamesBothSidesOfATransfer() {
            // Both outlines moved - one lost the cell, the other gained it - so both rebuild,
            // and no third faction's rings trace a cell that moved.
            var disturbance = new StalePoliticsDisturbance();

            disturbance.recordFlip(
                FLIPPED_SYSTEM,
                List.of(NEIGHBOUR_SYSTEM),
                buildHolderOf(HEGEMONY),
                buildHolderOf(TRITACHYON));

            assertThat(disturbance.getAffectedFactionIds())
                .containsExactly(HEGEMONY, TRITACHYON);
        }

        @Test
        void recordFlipNamesOnlyTheGainingFactionWhenNobodyHeldItBefore() {
            // Nobody held it, so there is no losing territory to rebuild - and naming one would
            // send a faction that never drew this cell through a rebuild for nothing.
            var disturbance = new StalePoliticsDisturbance();

            disturbance.recordFlip(FLIPPED_SYSTEM, Set.of(), null, buildHolderOf(TRITACHYON));

            assertThat(disturbance.getAffectedFactionIds())
                .containsExactly(TRITACHYON);
        }

        @Test
        void recordFlipNamesOnlyTheLosingFactionWhenNobodyHoldsItNow() {

            var disturbance = new StalePoliticsDisturbance();

            disturbance.recordFlip(FLIPPED_SYSTEM, Set.of(), buildHolderOf(HEGEMONY), null);

            assertThat(disturbance.getAffectedFactionIds())
                .containsExactly(HEGEMONY);
        }

        @Test
        void recordFlipCountsACellOnceWhenTwoFlipsDisturbIt() {
            // Two adjacent systems flipping in one frame disturb overlapping rings: each is the
            // other's neighbour, and both name the same cells. Redrawing a cell twice would
            // cost the frame this fold exists to save, which is why the batch accumulates rather
            // than each flip redrawing on its own.
            var disturbance = new StalePoliticsDisturbance();

            disturbance.recordFlip(
                FLIPPED_SYSTEM,
                List.of(NEIGHBOUR_SYSTEM),
                buildHolderOf(HEGEMONY),
                buildHolderOf(TRITACHYON));
            disturbance.recordFlip(
                NEIGHBOUR_SYSTEM,
                List.of(FLIPPED_SYSTEM),
                buildHolderOf(HEGEMONY),
                buildHolderOf(TRITACHYON));

            assertThat(disturbance.getCellIdsToRedraw())
                .containsExactly(FLIPPED_SYSTEM, NEIGHBOUR_SYSTEM);
            assertThat(disturbance.getAffectedFactionIds())
                .containsExactly(HEGEMONY, TRITACHYON);
        }
    }

    @Nested
    class RecordRestyle {

        @Test
        void recordRestyleRedrawsTheSystemsOwnCellAlone() {
            // What a cell is settled from moved without any seam moving with it, so its
            // neighbours draw exactly as they did and re-shaping them would spend the frame this
            // fold exists to save.
            var disturbance = new StalePoliticsDisturbance();

            disturbance.recordRestyle(RESTYLED_SYSTEM);

            assertThat(disturbance.getCellIdsToRedraw())
                .containsExactly(RESTYLED_SYSTEM);
        }

        @Test
        void recordRestyleRebuildsNoTerritory() {
            // No bloc's outline moved - the system is drawn by whoever drew it before, or by
            // nobody as before - so naming a faction here would rebuild a territory that traces
            // the same cells it already did.
            var disturbance = new StalePoliticsDisturbance();

            disturbance.recordRestyle(RESTYLED_SYSTEM);

            assertThat(disturbance.getAffectedFactionIds())
                .isEmpty();
        }

        @Test
        void recordRestyleCountsACellOnceWhenItAlsoFlipped() {
            // Both facts can move in one batch - a system taking its first colony changes hands
            // and becomes settled at once - and the cell is worth redrawing once.
            var disturbance = new StalePoliticsDisturbance();

            disturbance.recordFlip(FLIPPED_SYSTEM, Set.of(), null, buildHolderOf(HEGEMONY));
            disturbance.recordRestyle(FLIPPED_SYSTEM);

            assertThat(disturbance.getCellIdsToRedraw())
                .containsExactly(FLIPPED_SYSTEM);
        }
    }

    // Only the faction id is read here, so the shades are inert placeholders.
    private static DominantHolder buildHolderOf(String factionId) {
        return new DominantHolder(factionId, Color.GRAY, Color.GRAY);
    }
}
