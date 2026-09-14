package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.politicalmap.base.politics.DominantHolder;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

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

    private static final SystemKey HELD_SYSTEM = buildCellKey("held");
    private static final SystemKey SETTLED_SYSTEM = buildCellKey("settled");
    private static final SystemKey PRESENT_SYSTEM = buildCellKey("present");

    private static final DominantHolder HEGEMONY =
        new DominantHolder("hegemony", Color.GRAY, Color.GRAY);

    private static final DominantHolder TRITACHYON =
        new DominantHolder("tritachyon", Color.GRAY, Color.GRAY);

    // Two systems the sector answers to one ID for - vanilla's own unnamed deep space - which only
    // their anchors tell apart. The pair every fact here has to be able to hold two answers for.
    private static final SystemKey FIRST_TWIN = new SystemKey("deep space", "", "8b3");
    private static final SystemKey SECOND_TWIN = new SystemKey("deep space", "", "38d53");

    @Nested
    class CreateCopyOf {

        @Test
        void createCopyOfReadsBackEveryFactItWasGiven() {

            var occupancy = SystemOccupancy.createCopyOf(
                Map.of(HELD_SYSTEM, HEGEMONY),
                Set.of(SETTLED_SYSTEM),
                Set.of(PRESENT_SYSTEM));

            assertThat(occupancy.getHolderBySystemKey())
                .containsExactly(Map.entry(HELD_SYSTEM, HEGEMONY));
            assertThat(occupancy.getInhabitedSystemKeys())
                .containsExactly(SETTLED_SYSTEM);
            assertThat(occupancy.getSpotlitPresenceSystemKeys())
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
            assertThat(occupancy.getHolderBySystemKey())
                .containsOnlyKeys(HELD_SYSTEM);
        }

        @Test
        void createCopyOfLeavesTheCallersOwnCollectionsAlone() {
            // The mirror of the copy: a build that goes on reading what it handed over must not
            // see a later fold in it, or the pass's own record of what it resolved would drift as
            // the map is refreshed.
            var holders = new LinkedHashMap<SystemKey, DominantHolder>();
            var inhabited = new LinkedHashSet<SystemKey>();

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

            assertThat(occupancy.getHolderBySystemKey())
                .isEmpty();
            assertThat(occupancy.getInhabitedSystemKeys())
                .isEmpty();
            assertThat(occupancy.getSpotlitPresenceSystemKeys())
                .isEmpty();

            assertThat(occupancy.foldInhabitationOf(SETTLED_SYSTEM, true))
                .isTrue();
        }
    }

    @Nested
    class GetHolderBySystemKey {

        @Test
        void getHolderBySystemKeyRefusesAWriteThroughTheView() {
            // Every reader is answered through this, and one of them putting a holder in would be
            // a holder change no disturbance was recorded for - a cell drawn from a fact the batch
            // never noticed had moved.
            var occupancy = SystemOccupancy.createEmpty();

            assertThatThrownBy(() -> occupancy.getHolderBySystemKey().put(HELD_SYSTEM, HEGEMONY))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getHolderBySystemKeyShowsWhatALaterFoldWrote() {
            // The view is over the live map rather than a snapshot of it, so a reader holding one
            // across a refresh sees what the refresh wrote.
            var occupancy = SystemOccupancy.createEmpty();
            var holders = occupancy.getHolderBySystemKey();

            occupancy.recordHolderOf(HELD_SYSTEM, HEGEMONY);

            assertThat(holders)
                .containsExactly(Map.entry(HELD_SYSTEM, HEGEMONY));
        }
    }

    @Nested
    class ReadHolderOf {

        @Test
        void readHolderOfAnswersTheHolderOfOneSystem() {

            var occupancy = SystemOccupancy.createCopyOf(
                Map.of(HELD_SYSTEM, HEGEMONY),
                Set.of(),
                Set.of());

            assertThat(occupancy.readHolderOf(HELD_SYSTEM))
                .isSameAs(HEGEMONY);
        }

        @Test
        void readHolderOfAnswersNothingForASystemNobodyHolds() {
            // An absent entry is what "nobody holds this" is, so the narrow read says the same
            // thing a lookup in the map would - every caller branches on the null.
            assertThat(SystemOccupancy.createEmpty().readHolderOf(HELD_SYSTEM))
                .isNull();
        }

        @Test
        void readHolderOfTellsApartTwoSystemsSharingAnId() {
            // The narrow read is addressed the way the map is, so the arms that separate a
            // colliding pair still separate it - a read that had narrowed to the ID would answer
            // whichever of them came first.
            var holders = new LinkedHashMap<SystemKey, DominantHolder>();

            holders.put(FIRST_TWIN, HEGEMONY);
            holders.put(SECOND_TWIN, TRITACHYON);

            var occupancy = SystemOccupancy.createCopyOf(holders, Set.of(), Set.of());

            assertThat(occupancy.readHolderOf(FIRST_TWIN))
                .isSameAs(HEGEMONY);
            assertThat(occupancy.readHolderOf(SECOND_TWIN))
                .isSameAs(TRITACHYON);
        }

        @Test
        void readHolderOfShowsWhatALaterFoldWrote() {
            // Off the holding itself rather than through the view, so a caller asking after a
            // refresh reads what the refresh recorded rather than what the build resolved.
            var occupancy = SystemOccupancy.createEmpty();

            occupancy.recordHolderOf(HELD_SYSTEM, HEGEMONY);

            assertThat(occupancy.readHolderOf(HELD_SYSTEM))
                .isSameAs(HEGEMONY);
        }
    }

    @Nested
    class GetInhabitedSystemKeys {

        @Test
        void getInhabitedSystemKeysRefusesAWriteThroughTheView() {

            var occupancy = SystemOccupancy.createEmpty();

            assertThatThrownBy(() -> occupancy.getInhabitedSystemKeys().add(SETTLED_SYSTEM))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class GetSpotlitPresenceSystemKeys {

        @Test
        void getSpotlitPresenceSystemKeysRefusesAWriteThroughTheView() {

            var occupancy = SystemOccupancy.createEmpty();

            assertThatThrownBy(() -> occupancy.getSpotlitPresenceSystemKeys().add(PRESENT_SYSTEM))
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

            assertThat(occupancy.getHolderBySystemKey())
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

            assertThat(occupancy.getHolderBySystemKey())
                .isEmpty();
        }

        @Test
        void recordHolderOfHoldsTwoSystemsSharingAnIdUnderDifferentBlocs() {
            // The collision the holding is keyed by SystemKey to survive: each of the pair keeps
            // its own holder, where a map keyed by ID held one entry and drew the second system in
            // the first's colours.
            var occupancy = SystemOccupancy.createEmpty();

            occupancy.recordHolderOf(FIRST_TWIN, HEGEMONY);
            occupancy.recordHolderOf(SECOND_TWIN, TRITACHYON);

            assertThat(occupancy.getHolderBySystemKey())
                .containsExactly(
                    Map.entry(FIRST_TWIN, HEGEMONY),
                    Map.entry(SECOND_TWIN, TRITACHYON));
        }
    }

    @Nested
    class FoldInhabitationOf {

        @Test
        void foldInhabitationOfReportsASystemTakingItsFirstColony() {

            var occupancy = SystemOccupancy.createEmpty();

            assertThat(occupancy.foldInhabitationOf(SETTLED_SYSTEM, true))
                .isTrue();
            assertThat(occupancy.getInhabitedSystemKeys())
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
            assertThat(occupancy.getInhabitedSystemKeys())
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
            assertThat(occupancy.getSpotlitPresenceSystemKeys())
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
            assertThat(occupancy.getSpotlitPresenceSystemKeys())
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
        void foldSpotlitPresenceOfMovesOnlyTheSystemOfAPairSharingAnId() {
            // The pair the address exists to tell apart, asked of the fold: the pick arriving in
            // one of them leaves the other where it was, where a set keyed by ID would have
            // spared both cells the recede at once.
            var occupancy = SystemOccupancy.createEmpty();

            assertThat(occupancy.foldSpotlitPresenceOf(FIRST_TWIN, true))
                .isTrue();

            assertThat(occupancy.getSpotlitPresenceSystemKeys())
                .containsExactly(FIRST_TWIN);
        }

        @Test
        void foldSpotlitPresenceOfLeavesInhabitationAlone() {
            // The two folds run over the same system keys and answer the same shape of question, so
            // a set named wrong in one of them would read as correct at every other assertion here.
            var occupancy = SystemOccupancy.createEmpty();

            occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, true);

            assertThat(occupancy.getInhabitedSystemKeys())
                .isEmpty();
        }
    }
}
