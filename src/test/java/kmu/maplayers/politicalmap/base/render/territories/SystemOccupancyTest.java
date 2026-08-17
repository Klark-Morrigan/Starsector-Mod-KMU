package kmu.maplayers.politicalmap.base.render.territories;

import kmu.maplayers.politicalmap.base.politics.DominantHolder;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the two guarantees the three per-system facts were gathered into one type for: that folding
 * one of them works whatever collection the pass answered with, and that the only way to move one
 * is a fold that reports what it moved.
 *
 * <p>Both are silent when wrong, and in the worst way. A pass answering with an immutable empty
 * collection - which the presence read does off filter, and the holder resolve does for a sector
 * with nothing in it - would leave the first colony founded that session throwing inside a frame
 * rather than drawing. And a fold whose answer did not match what it wrote would leave a system
 * settled in the model and drawn as backdrop, which no later frame corrects: nothing marks a
 * system twice.
 */
final class SystemOccupancyTest {

    private static final String HELD_SYSTEM = "held";
    private static final String SETTLED_SYSTEM = "settled";
    private static final String PRESENT_SYSTEM = "present";

    private static final DominantHolder HEGEMONY =
        new DominantHolder("hegemony", Color.GRAY, Color.GRAY);

    private static final DominantHolder TRITACHYON =
        new DominantHolder("tritachyon", Color.GRAY, Color.GRAY);

    @Nested
    class CreateCopyOf {

        @Test
        void createCopyOfReadsBackEveryFactItWasGiven() {

            var occupancy = SystemOccupancy.createCopyOf(
                Map.of(HELD_SYSTEM, HEGEMONY),
                Set.of(SETTLED_SYSTEM),
                Set.of(PRESENT_SYSTEM));

            assertThat(occupancy.getHolderBySystemId())
                .containsExactly(Map.entry(HELD_SYSTEM, HEGEMONY));
            assertThat(occupancy.getInhabitedSystemIds())
                .containsExactly(SETTLED_SYSTEM);
            assertThat(occupancy.getSpotlitPresenceSystemIds())
                .containsExactly(PRESENT_SYSTEM);
        }

        @Test
        void createCopyOfFoldsIntoCollectionsTheCallerHandedOverImmutable() {
            // The case the type exists for: every one of the three reads a pass makes is free to
            // answer with an immutable collection, and the presence read does exactly that off
            // filter. Copying here is what keeps that from becoming a throw at the first colony
            // founded - a frame that dies rather than a map that updates.
            var occupancy = SystemOccupancy.createCopyOf(Map.of(), Set.of(), Set.of());

            occupancy.recordHolderOf(HELD_SYSTEM, HEGEMONY);

            assertThat(occupancy.foldInhabitationOf(SETTLED_SYSTEM, true))
                .isTrue();
            assertThat(occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, true))
                .isTrue();
            assertThat(occupancy.getHolderBySystemId())
                .containsOnlyKeys(HELD_SYSTEM);
        }

        @Test
        void createCopyOfLeavesTheCallersOwnCollectionsAlone() {
            // The mirror of the copy: a build that goes on reading what it handed over must not
            // see a later fold in it, or the pass's own record of what it resolved would drift as
            // the map is refreshed.
            var holders = new LinkedHashMap<String, DominantHolder>();
            var inhabited = new LinkedHashSet<String>();

            var occupancy = SystemOccupancy.createCopyOf(holders, inhabited, Set.of());

            occupancy.recordHolderOf(HELD_SYSTEM, HEGEMONY);
            occupancy.foldInhabitationOf(SETTLED_SYSTEM, true);

            assertThat(holders)
                .isEmpty();
            assertThat(inhabited)
                .isEmpty();
        }
    }

    @Nested
    class CreateEmpty {

        @Test
        void createEmptyHoldsNothingAndStillFolds() {
            // What a build that never ran carries. It has to be foldable rather than inert: the
            // placeholder is replaced by a real build before anything reads it, but a refresh
            // reaching it first must write rather than throw.
            var occupancy = SystemOccupancy.createEmpty();

            assertThat(occupancy.getHolderBySystemId())
                .isEmpty();
            assertThat(occupancy.getInhabitedSystemIds())
                .isEmpty();
            assertThat(occupancy.getSpotlitPresenceSystemIds())
                .isEmpty();

            assertThat(occupancy.foldInhabitationOf(SETTLED_SYSTEM, true))
                .isTrue();
        }
    }

    @Nested
    class GetHolderBySystemId {

        @Test
        void getHolderBySystemIdRefusesAWriteThroughTheView() {
            // Every reader is answered through this, and one of them putting a holder in would be
            // a holder change no disturbance was recorded for - a cell drawn from a fact the batch
            // never noticed had moved.
            var occupancy = SystemOccupancy.createEmpty();

            assertThatThrownBy(() -> occupancy.getHolderBySystemId().put(HELD_SYSTEM, HEGEMONY))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getHolderBySystemIdShowsWhatALaterFoldWrote() {
            // The view is over the live map rather than a snapshot of it, so a reader holding one
            // across a refresh sees what the refresh wrote.
            var occupancy = SystemOccupancy.createEmpty();
            var holders = occupancy.getHolderBySystemId();

            occupancy.recordHolderOf(HELD_SYSTEM, HEGEMONY);

            assertThat(holders)
                .containsExactly(Map.entry(HELD_SYSTEM, HEGEMONY));
        }
    }

    @Nested
    class GetInhabitedSystemIds {

        @Test
        void getInhabitedSystemIdsRefusesAWriteThroughTheView() {

            var occupancy = SystemOccupancy.createEmpty();

            assertThatThrownBy(() -> occupancy.getInhabitedSystemIds().add(SETTLED_SYSTEM))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class GetSpotlitPresenceSystemIds {

        @Test
        void getSpotlitPresenceSystemIdsRefusesAWriteThroughTheView() {

            var occupancy = SystemOccupancy.createEmpty();

            assertThatThrownBy(() -> occupancy.getSpotlitPresenceSystemIds().add(PRESENT_SYSTEM))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class RecordHolderOf {

        @Test
        void recordHolderOfReplacesTheStandingHolder() {

            var occupancy = SystemOccupancy.createCopyOf(
                Map.of(HELD_SYSTEM, HEGEMONY),
                Set.of(),
                Set.of());

            occupancy.recordHolderOf(HELD_SYSTEM, TRITACHYON);

            assertThat(occupancy.getHolderBySystemId())
                .containsExactly(Map.entry(HELD_SYSTEM, TRITACHYON));
        }

        @Test
        void recordHolderOfDropsTheEntryWhenNobodyHoldsItNow() {
            // A decivilised or bombed-out system: the entry goes rather than being left pointing
            // at the faction that lost it, since every reader takes an absent entry for "nobody
            // holds this".
            var occupancy = SystemOccupancy.createCopyOf(
                Map.of(HELD_SYSTEM, HEGEMONY),
                Set.of(),
                Set.of());

            occupancy.recordHolderOf(HELD_SYSTEM, null);

            assertThat(occupancy.getHolderBySystemId())
                .isEmpty();
        }
    }

    @Nested
    class FoldInhabitationOf {

        @Test
        void foldInhabitationOfReportsASystemTakingItsFirstColony() {

            var occupancy = SystemOccupancy.createEmpty();

            assertThat(occupancy.foldInhabitationOf(SETTLED_SYSTEM, true))
                .isTrue();
            assertThat(occupancy.getInhabitedSystemIds())
                .containsExactly(SETTLED_SYSTEM);
        }

        @Test
        void foldInhabitationOfReportsASystemLosingItsLastColony() {

            var occupancy = SystemOccupancy.createCopyOf(
                Map.of(),
                Set.of(SETTLED_SYSTEM),
                Set.of());

            assertThat(occupancy.foldInhabitationOf(SETTLED_SYSTEM, false))
                .isTrue();
            assertThat(occupancy.getInhabitedSystemIds())
                .isEmpty();
        }

        @Test
        void foldInhabitationOfReportsNothingForASystemThatWasAlreadySettled() {
            // The common resize: a colony grew. Reporting a change here would redraw a cell that
            // draws exactly as it did, on every colony event in the sector.
            var occupancy = SystemOccupancy.createCopyOf(
                Map.of(),
                Set.of(SETTLED_SYSTEM),
                Set.of());

            assertThat(occupancy.foldInhabitationOf(SETTLED_SYSTEM, true))
                .isFalse();
        }

        @Test
        void foldInhabitationOfReportsNothingForASystemThatWasAlreadyEmpty() {

            var occupancy = SystemOccupancy.createEmpty();

            assertThat(occupancy.foldInhabitationOf(SETTLED_SYSTEM, false))
                .isFalse();
        }
    }

    @Nested
    class FoldSpotlitPresenceOf {

        @Test
        void foldSpotlitPresenceOfReportsThePickArrivingInASystem() {

            var occupancy = SystemOccupancy.createEmpty();

            assertThat(occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, true))
                .isTrue();
            assertThat(occupancy.getSpotlitPresenceSystemIds())
                .containsExactly(PRESENT_SYSTEM);
        }

        @Test
        void foldSpotlitPresenceOfReportsThePickLeavingASystem() {

            var occupancy = SystemOccupancy.createCopyOf(
                Map.of(),
                Set.of(),
                Set.of(PRESENT_SYSTEM));

            assertThat(occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, false))
                .isTrue();
            assertThat(occupancy.getSpotlitPresenceSystemIds())
                .isEmpty();
        }

        @Test
        void foldSpotlitPresenceOfReportsNothingWhereThePickWasAlreadyLiving() {

            var occupancy = SystemOccupancy.createCopyOf(
                Map.of(),
                Set.of(),
                Set.of(PRESENT_SYSTEM));

            assertThat(occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, true))
                .isFalse();
        }

        @Test
        void foldSpotlitPresenceOfLeavesInhabitationAlone() {
            // The two folds run over the same system ids and answer the same shape of question, so
            // a set named wrong in one of them would read as correct at every other assertion here.
            var occupancy = SystemOccupancy.createEmpty();

            occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, true);

            assertThat(occupancy.getInhabitedSystemIds())
                .isEmpty();
        }
    }
}
