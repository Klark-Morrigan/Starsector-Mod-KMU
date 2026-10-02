package kmu.maplayers.ownermap.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.SystemOwner;

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
 * collection - which the presence read does off filter, and the owner resolve does for a sector
 * with nothing in it - would leave the first colony founded that session throwing inside a frame
 * rather than drawing. And a fold whose answer did not match what it wrote would leave a system
 * settled in the model and drawn as backdrop, which no later frame corrects: nothing marks a
 * system twice.
 */
final class SystemOccupancyTests {

    private static final SystemKey HELD_SYSTEM = buildCellKey("held");
    private static final SystemKey SETTLED_SYSTEM = buildCellKey("settled");
    private static final SystemKey PRESENT_SYSTEM = buildCellKey("present");

    private static final SystemOwner HEGEMONY =
        new SystemOwner("hegemony", new OwnerPalette(Color.GRAY, Color.GRAY));

    private static final SystemOwner TRITACHYON =
        new SystemOwner("tritachyon", new OwnerPalette(Color.GRAY, Color.GRAY));

    // Two systems the sector answers to one ID for - vanilla's own unnamed deep space - which only
    // their anchors tell apart. The pair every fact here has to be able to hold two answers for.
    private static final SystemKey FIRST_TWIN = new SystemKey("deep space", "", "8b3");
    private static final SystemKey SECOND_TWIN = new SystemKey("deep space", "", "38d53");

    @Nested
    class CreateCopyOf {

        @Test
        void readsBackEveryFactItWasGiven() {

            var occupancy = SystemOccupancy.createCopyOf(
                Map.of(HELD_SYSTEM, HEGEMONY),
                Set.of(SETTLED_SYSTEM),
                Set.of(PRESENT_SYSTEM));

            assertThat(occupancy.getOwnerBySystemKey())
                .containsExactly(Map.entry(HELD_SYSTEM, HEGEMONY));
            assertThat(occupancy.getInhabitedSystemKeys())
                .containsExactly(SETTLED_SYSTEM);
            assertThat(occupancy.getSpotlitPresenceSystemKeys())
                .containsExactly(PRESENT_SYSTEM);
        }

        @Test
        void foldsIntoCollectionsTheCallerHandedOverImmutable() {
            // The case the type exists for: every one of the three reads a pass makes is free to
            // answer with an immutable collection, and the presence read does exactly that off
            // filter. Copying here is what keeps that from becoming a throw at the first colony
            // founded - a frame that dies rather than a map that updates.
            var occupancy = SystemOccupancy.createCopyOf(Map.of(), Set.of(), Set.of());

            occupancy.recordOwnerOf(HELD_SYSTEM, HEGEMONY);

            assertThat(occupancy.foldInhabitationOf(SETTLED_SYSTEM, true))
                .isTrue();
            assertThat(occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, true))
                .isTrue();
            assertThat(occupancy.getOwnerBySystemKey())
                .containsOnlyKeys(HELD_SYSTEM);
        }

        @Test
        void leavesTheCallersOwnCollectionsAlone() {
            // The mirror of the copy: a build that goes on reading what it handed over must not
            // see a later fold in it, or the pass's own record of what it resolved would drift as
            // the map is refreshed.
            var owners = new LinkedHashMap<SystemKey, SystemOwner>();
            var inhabited = new LinkedHashSet<SystemKey>();

            var occupancy = SystemOccupancy.createCopyOf(owners, inhabited, Set.of());

            occupancy.recordOwnerOf(HELD_SYSTEM, HEGEMONY);
            occupancy.foldInhabitationOf(SETTLED_SYSTEM, true);

            assertThat(owners)
                .isEmpty();
            assertThat(inhabited)
                .isEmpty();
        }
    }

    @Nested
    class CreateEmpty {

        @Test
        void holdsNothingAndStillFolds() {
            // What a build that never ran carries. It has to be foldable rather than inert: the
            // placeholder is replaced by a real build before anything reads it, but a refresh
            // reaching it first must write rather than throw.
            var occupancy = SystemOccupancy.createEmpty();

            assertThat(occupancy.getOwnerBySystemKey())
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
    class GetOwnerBySystemKey {

        @Test
        void refusesAWriteThroughTheView() {
            // Every reader is answered through this, and one of them putting an owner in would be
            // an owner change no disturbance was recorded for - a cell drawn from a fact the batch
            // never noticed had moved.
            var occupancy = SystemOccupancy.createEmpty();

            assertThatThrownBy(() -> occupancy.getOwnerBySystemKey().put(HELD_SYSTEM, HEGEMONY))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void showsWhatALaterFoldWrote() {
            // The view is over the live map rather than a snapshot of it, so a reader holding one
            // across a refresh sees what the refresh wrote.
            var occupancy = SystemOccupancy.createEmpty();
            var owners = occupancy.getOwnerBySystemKey();

            occupancy.recordOwnerOf(HELD_SYSTEM, HEGEMONY);

            assertThat(owners)
                .containsExactly(Map.entry(HELD_SYSTEM, HEGEMONY));
        }
    }

    @Nested
    class ReadOwnerOf {

        @Test
        void answersTheOwnerOfOneSystem() {

            var occupancy = SystemOccupancy.createCopyOf(
                Map.of(HELD_SYSTEM, HEGEMONY),
                Set.of(),
                Set.of());

            assertThat(occupancy.readOwnerOf(HELD_SYSTEM))
                .isSameAs(HEGEMONY);
        }

        @Test
        void answersNothingForASystemNobodyHolds() {
            // An absent entry is what "nobody owns this" is, so the narrow read says the same
            // thing a lookup in the map would - every caller branches on the null.
            assertThat(SystemOccupancy.createEmpty().readOwnerOf(HELD_SYSTEM))
                .isNull();
        }

        @Test
        void tellsApartTwoSystemsSharingAnId() {
            // The narrow read is addressed the way the map is, so the arms that separate a
            // colliding pair still separate it - a read that had narrowed to the ID would answer
            // whichever of them came first.
            var owners = new LinkedHashMap<SystemKey, SystemOwner>();

            owners.put(FIRST_TWIN, HEGEMONY);
            owners.put(SECOND_TWIN, TRITACHYON);

            var occupancy = SystemOccupancy.createCopyOf(owners, Set.of(), Set.of());

            assertThat(occupancy.readOwnerOf(FIRST_TWIN))
                .isSameAs(HEGEMONY);
            assertThat(occupancy.readOwnerOf(SECOND_TWIN))
                .isSameAs(TRITACHYON);
        }

        @Test
        void showsWhatALaterFoldWrote() {
            // Off the ownership itself rather than through the view, so a caller asking after a
            // refresh reads what the refresh recorded rather than what the build resolved.
            var occupancy = SystemOccupancy.createEmpty();

            occupancy.recordOwnerOf(HELD_SYSTEM, HEGEMONY);

            assertThat(occupancy.readOwnerOf(HELD_SYSTEM))
                .isSameAs(HEGEMONY);
        }
    }

    @Nested
    class GetInhabitedSystemKeys {

        @Test
        void refusesAWriteThroughTheView() {

            var occupancy = SystemOccupancy.createEmpty();

            assertThatThrownBy(() -> occupancy.getInhabitedSystemKeys().add(SETTLED_SYSTEM))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class GetSpotlitPresenceSystemKeys {

        @Test
        void refusesAWriteThroughTheView() {

            var occupancy = SystemOccupancy.createEmpty();

            assertThatThrownBy(() -> occupancy.getSpotlitPresenceSystemKeys().add(PRESENT_SYSTEM))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class RecordOwnerOf {

        @Test
        void replacesTheStandingOwner() {

            var occupancy = SystemOccupancy.createCopyOf(
                Map.of(HELD_SYSTEM, HEGEMONY),
                Set.of(),
                Set.of());

            occupancy.recordOwnerOf(HELD_SYSTEM, TRITACHYON);

            assertThat(occupancy.getOwnerBySystemKey())
                .containsExactly(Map.entry(HELD_SYSTEM, TRITACHYON));
        }

        @Test
        void dropsTheEntryWhenNobodyHoldsItNow() {
            // A decivilised or bombed-out system: the entry goes rather than being left pointing
            // at the faction that lost it, since every reader takes an absent entry for "nobody
            // owns this".
            var occupancy = SystemOccupancy.createCopyOf(
                Map.of(HELD_SYSTEM, HEGEMONY),
                Set.of(),
                Set.of());

            occupancy.recordOwnerOf(HELD_SYSTEM, null);

            assertThat(occupancy.getOwnerBySystemKey())
                .isEmpty();
        }

        @Test
        void holdsTwoSystemsSharingAnIdUnderDifferentBlocs() {
            // The collision the ownership is keyed by SystemKey to survive: each of the pair keeps
            // its own owner, where a map keyed by ID held one entry and drew the second system in
            // the first's colours.
            var occupancy = SystemOccupancy.createEmpty();

            occupancy.recordOwnerOf(FIRST_TWIN, HEGEMONY);
            occupancy.recordOwnerOf(SECOND_TWIN, TRITACHYON);

            assertThat(occupancy.getOwnerBySystemKey())
                .containsExactly(
                    Map.entry(FIRST_TWIN, HEGEMONY),
                    Map.entry(SECOND_TWIN, TRITACHYON));
        }
    }

    @Nested
    class FoldInhabitationOf {

        @Test
        void reportsASystemTakingItsFirstColony() {

            var occupancy = SystemOccupancy.createEmpty();

            assertThat(occupancy.foldInhabitationOf(SETTLED_SYSTEM, true))
                .isTrue();
            assertThat(occupancy.getInhabitedSystemKeys())
                .containsExactly(SETTLED_SYSTEM);
        }

        @Test
        void reportsASystemLosingItsLastColony() {

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
        void reportsNothingForASystemThatWasAlreadySettled() {
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
        void reportsNothingForASystemThatWasAlreadyEmpty() {

            var occupancy = SystemOccupancy.createEmpty();

            assertThat(occupancy.foldInhabitationOf(SETTLED_SYSTEM, false))
                .isFalse();
        }
    }

    @Nested
    class FoldSpotlitPresenceOf {

        @Test
        void reportsThePickArrivingInASystem() {

            var occupancy = SystemOccupancy.createEmpty();

            assertThat(occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, true))
                .isTrue();
            assertThat(occupancy.getSpotlitPresenceSystemKeys())
                .containsExactly(PRESENT_SYSTEM);
        }

        @Test
        void reportsThePickLeavingASystem() {

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
        void reportsNothingWhereThePickWasAlreadyLiving() {

            var occupancy = SystemOccupancy.createCopyOf(
                Map.of(),
                Set.of(),
                Set.of(PRESENT_SYSTEM));

            assertThat(occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, true))
                .isFalse();
        }

        @Test
        void movesOnlyTheSystemOfAPairSharingAnId() {
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
        void leavesInhabitationAlone() {
            // The two folds run over the same system keys and answer the same shape of question, so
            // a set named wrong in one of them would read as correct at every other assertion here.
            var occupancy = SystemOccupancy.createEmpty();

            occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, true);

            assertThat(occupancy.getInhabitedSystemKeys())
                .isEmpty();
        }
    }
}
