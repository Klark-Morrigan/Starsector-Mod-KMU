package kmu.maplayers.ownermap.render.clusters;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.render.clusters.SystemOccupancyFixtures.createEmptyOccupancy;
import static kmu.maplayers.ownermap.render.clusters.SystemOccupancyFixtures.createSolidlyFilledOccupancyOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Pins the two guarantees the four per-system facts were gathered into one type for: that folding
 * one of them works whatever collection the pass answered with, and that the only way to move one
 * is a fold that reports what it moved.
 *
 * <p>Both are silent when wrong, and in the worst way. A pass answering with an immutable empty
 * collection - which the presence read does off filter, and the owner resolve does for a sector
 * with nothing in it - would leave the first system settled that session throwing inside a frame
 * rather than drawing. And a fold whose answer did not match what it wrote would leave a system
 * settled in the model and drawn as backdrop, which no later frame corrects: nothing marks a
 * system twice.
 */
final class SystemOccupancyTests {

    private static final SystemKey HELD_SYSTEM = buildCellKey("held");
    private static final SystemKey SETTLED_SYSTEM = buildCellKey("settled");
    private static final SystemKey PRESENT_SYSTEM = buildCellKey("present");

    private static final SystemOwner HEGEMONY =
        new SystemOwner("hegemony", new ColourPair(Color.GRAY, Color.GRAY));

    private static final SystemOwner TRITACHYON =
        new SystemOwner("tritachyon", new ColourPair(Color.GRAY, Color.GRAY));

    // The two owners as a resolve answers them for a system each fills solid.
    private static final ResolvedSystemOwner HELD_BY_HEGEMONY =
        ResolvedSystemOwner.createSolidlyFilledBy(HEGEMONY);

    private static final ResolvedSystemOwner HELD_BY_TRITACHYON =
        ResolvedSystemOwner.createSolidlyFilledBy(TRITACHYON);

    // Hegemony keeping a system for border and label alone, drawn with no fill.
    private static final ResolvedSystemOwner UNFILLED_BY_HEGEMONY =
        new ResolvedSystemOwner(HEGEMONY, FillState.UNFILLED);

    // Two systems the sector answers to one ID for - vanilla's own unnamed deep space - which only
    // their anchors tell apart. The pair every fact here has to be able to hold two answers for.
    private static final SystemKey FIRST_TWIN = new SystemKey("deep space", "", "8b3");
    private static final SystemKey SECOND_TWIN = new SystemKey("deep space", "", "38d53");

    @Nested
    class CreateCopyOf {

        @Test
        void readsBackEveryFactItWasGiven() {

            // ARRANGE
            var fillStates = SystemFillStates.createOf(Map.of(HELD_SYSTEM, FillState.UNFILLED));
            var owners = new ResolvedOwners(
                Map.of(HELD_SYSTEM, HEGEMONY),
                fillStates,
                Set.of(SETTLED_SYSTEM),
                Set.of(PRESENT_SYSTEM));

            // ACT
            var actualOccupancy = SystemOccupancy.createCopyOf(owners);

            // ASSERT
            assertThat(actualOccupancy.getOwnerBySystemKey())
                .containsExactly(Map.entry(HELD_SYSTEM, HEGEMONY));
            assertThat(actualOccupancy.snapshotFillStates())
                .isEqualTo(fillStates);
            assertThat(actualOccupancy.getInhabitedSystemKeys())
                .containsExactly(SETTLED_SYSTEM);
            assertThat(actualOccupancy.getSpotlitPresenceSystemKeys())
                .containsExactly(PRESENT_SYSTEM);
        }

        // The case the type exists for: every one of the reads a pass makes is free to answer with an
        // immutable collection, and the presence read does exactly that off filter. Copying here is
        // what keeps that from becoming a throw at the first system settled - a frame that dies rather
        // than a map that updates.
        @Test
        void foldsIntoCollectionsTheCallerHandedOverImmutable() {

            // ARRANGE
            var occupancy = createSolidlyFilledOccupancyOf(Map.of(), Set.of(), Set.of());

            // ACT
            occupancy.recordOwnerOf(HELD_SYSTEM, HELD_BY_HEGEMONY);

            var actualHasInhabitationMoved = occupancy.foldInhabitationOf(SETTLED_SYSTEM, true);
            var actualHasPresenceMoved = occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, true);

            // ASSERT
            assertThat(actualHasInhabitationMoved)
                .isTrue();
            assertThat(actualHasPresenceMoved)
                .isTrue();
            assertThat(occupancy.getOwnerBySystemKey())
                .containsOnlyKeys(HELD_SYSTEM);
        }

        // The mirror of the copy: a build that goes on reading what it handed over must not see a
        // later fold in it, or the pass's own record of what it resolved would drift as the map is
        // refreshed.
        @Test
        void leavesTheCallersOwnCollectionsAlone() {

            // ARRANGE
            var owners = new LinkedHashMap<SystemKey, SystemOwner>();
            var inhabited = new LinkedHashSet<SystemKey>();
            var occupancy = createSolidlyFilledOccupancyOf(owners, inhabited, Set.of());

            // ACT
            occupancy.recordOwnerOf(HELD_SYSTEM, HELD_BY_HEGEMONY);
            occupancy.foldInhabitationOf(SETTLED_SYSTEM, true);

            // ASSERT
            assertThat(owners)
                .isEmpty();
            assertThat(inhabited)
                .isEmpty();
        }

        // A fill state lives beside its owner, so one the owners name for a system nobody owns has
        // no border to draw inside and is not kept.
        @Test
        void dropsAFillStateNamedForASystemNobodyOwns() {

            // ARRANGE
            var owners = new ResolvedOwners(
                Map.of(),
                SystemFillStates.createOf(Map.of(HELD_SYSTEM, FillState.UNFILLED)),
                Set.of(),
                Set.of());

            // ACT
            var actualOccupancy = SystemOccupancy.createCopyOf(owners);

            // ASSERT
            assertThat(actualOccupancy.snapshotFillStates())
                .isEqualTo(SystemFillStates.ALL_SOLID);
            assertThat(actualOccupancy.readResolvedOwnerOf(HELD_SYSTEM))
                .isSameAs(ResolvedSystemOwner.UNOWNED);
        }
    }

    @Nested
    class GetOwnerBySystemKey {

        // Every reader is answered through this, and one of them putting an owner in would be an
        // owner change no disturbance was recorded for - a cell drawn from a fact the batch never
        // noticed had moved.
        @Test
        void refusesAWriteThroughTheView() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();

            // ACT
            var actualThrown = catchThrowable(() -> occupancy.getOwnerBySystemKey().put(HELD_SYSTEM, HEGEMONY));

            // ASSERT
            assertThat(actualThrown)
                .isInstanceOf(UnsupportedOperationException.class);
        }

        // The owners are a projection of the resolved owners, so a removal through any of its views
        // would drop an owner and leave its fill state behind, which no fold recorded.
        @Test
        void refusesARemovalThroughTheViewsKeys() {

            // ARRANGE
            var occupancy = createSolidlyFilledOccupancyOf(Map.of(HELD_SYSTEM, HEGEMONY), Set.of(), Set.of());

            // ACT
            var actualThrown = catchThrowable(() -> occupancy.getOwnerBySystemKey().keySet().remove(HELD_SYSTEM));

            // ASSERT
            assertThat(actualThrown)
                .isInstanceOf(UnsupportedOperationException.class);
        }

        // A lookup reads the owner half of the entry, answering nobody for a system with no entry.
        @Test
        void answersALookupWithTheOwnerAlone() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();

            occupancy.recordOwnerOf(HELD_SYSTEM, UNFILLED_BY_HEGEMONY);

            var owners = occupancy.getOwnerBySystemKey();

            // ACT
            var actualHeldOwner = owners.get(HELD_SYSTEM);
            var actualUnheldOwner = owners.get(SETTLED_SYSTEM);

            // ASSERT
            assertThat(actualHeldOwner)
                .isSameAs(HEGEMONY);
            assertThat(actualUnheldOwner)
                .isNull();
        }

        // The view is over the live map rather than a snapshot of it, so a reader holding one across
        // a refresh sees what the refresh wrote.
        @Test
        void showsWhatALaterFoldWrote() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();
            var owners = occupancy.getOwnerBySystemKey();

            // ACT
            occupancy.recordOwnerOf(HELD_SYSTEM, HELD_BY_HEGEMONY);

            // ASSERT
            assertThat(owners)
                .containsExactly(Map.entry(HELD_SYSTEM, HEGEMONY));
        }
    }

    @Nested
    class ReadOwnerOf {

        @Test
        void answersTheOwnerOfOneSystem() {

            // ARRANGE
            var occupancy = createSolidlyFilledOccupancyOf(
                Map.of(HELD_SYSTEM, HEGEMONY),
                Set.of(),
                Set.of());

            // ACT
            var actualOwner = occupancy.readOwnerOf(HELD_SYSTEM);

            // ASSERT
            assertThat(actualOwner)
                .isSameAs(HEGEMONY);
        }

        // An absent entry is what "nobody owns this" is, so the narrow read says the same thing a
        // lookup in the map would - every caller branches on the null.
        @Test
        void answersNothingForASystemNobodyHolds() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();

            // ACT
            var actualOwner = occupancy.readOwnerOf(HELD_SYSTEM);

            // ASSERT
            assertThat(actualOwner)
                .isNull();
        }

        // The narrow read is addressed the way the map is, so the arms that separate a colliding pair
        // still separate it - a read that had narrowed to the ID would answer whichever of them came
        // first.
        @Test
        void tellsApartTwoSystemsSharingAnId() {

            // ARRANGE
            var owners = new LinkedHashMap<SystemKey, SystemOwner>();

            owners.put(FIRST_TWIN, HEGEMONY);
            owners.put(SECOND_TWIN, TRITACHYON);

            var occupancy = createSolidlyFilledOccupancyOf(owners, Set.of(), Set.of());

            // ACT
            var actualFirstOwner = occupancy.readOwnerOf(FIRST_TWIN);
            var actualSecondOwner = occupancy.readOwnerOf(SECOND_TWIN);

            // ASSERT
            assertThat(actualFirstOwner)
                .isSameAs(HEGEMONY);
            assertThat(actualSecondOwner)
                .isSameAs(TRITACHYON);
        }

        // Off the ownership itself rather than through the view, so a caller asking after a refresh
        // reads what the refresh recorded rather than what the build resolved.
        @Test
        void showsWhatALaterFoldWrote() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();

            occupancy.recordOwnerOf(HELD_SYSTEM, HELD_BY_HEGEMONY);

            // ACT
            var actualOwner = occupancy.readOwnerOf(HELD_SYSTEM);

            // ASSERT
            assertThat(actualOwner)
                .isSameAs(HEGEMONY);
        }

        // A cell with no star of its own names no system, and is answered here rather than by each
        // caller guarding the read before it asks.
        @Test
        void answersNobodyForACellDrawnAsNoSystem() {

            // ARRANGE
            var occupancy = createSolidlyFilledOccupancyOf(
                Map.of(HELD_SYSTEM, HEGEMONY),
                Set.of(),
                Set.of());

            // ACT
            var actualOwner = occupancy.readOwnerOf(null);

            // ASSERT
            assertThat(actualOwner)
                .isNull();
        }
    }

    @Nested
    class ReadOwnerOfMembers {

        // Every member of a group is keyed by the owner of the system it draws as, so the first
        // answers for all - here an absorbed cell drawing as the held system's star.
        @Test
        void answersTheOwnerOfTheSystemTheFirstMemberDrawsAs() {

            // ARRANGE
            var absorbedCell = buildCellKey("absorbed");
            var occupancy = createSolidlyFilledOccupancyOf(
                Map.of(HELD_SYSTEM, HEGEMONY),
                Set.of(),
                Set.of());

            var cellGrouping = new CellGrouping(
                Map.of(absorbedCell, HELD_SYSTEM),
                Map.of(HELD_SYSTEM, "hegemony"));

            // ACT
            var actualOwner = occupancy.readOwnerOfMembers(cellGrouping, List.of(absorbedCell));

            // ASSERT
            assertThat(actualOwner)
                .isSameAs(HEGEMONY);
        }

        // The members and the owners share one address, so a group whose member shares a vanilla ID
        // with another system reads its own owner - where a lookup by ID alone could only have named
        // whichever of the pair the map happened to hold.
        @Test
        void readsTheFirstMembersOwnerUnderThatMembersOwnKey() {

            // ARRANGE
            var owners = new LinkedHashMap<SystemKey, SystemOwner>();

            owners.put(FIRST_TWIN, HEGEMONY);
            owners.put(SECOND_TWIN, TRITACHYON);

            var occupancy = createSolidlyFilledOccupancyOf(owners, Set.of(), Set.of());
            var cellGrouping = new CellGrouping(
                Map.of(
                    FIRST_TWIN,
                    FIRST_TWIN,
                    SECOND_TWIN,
                    SECOND_TWIN),
                Map.of(
                    FIRST_TWIN,
                    "hegemony",
                    SECOND_TWIN,
                    "tritachyon"));

            // ACT
            var actualOwner = occupancy.readOwnerOfMembers(cellGrouping, List.of(SECOND_TWIN));

            // ASSERT
            assertThat(actualOwner)
                .isSameAs(TRITACHYON);
        }
    }

    @Nested
    class GetInhabitedSystemKeys {

        @Test
        void refusesAWriteThroughTheView() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();

            // ACT
            var actualThrown = catchThrowable(() -> occupancy.getInhabitedSystemKeys().add(SETTLED_SYSTEM));

            // ASSERT
            assertThat(actualThrown)
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class GetSpotlitPresenceSystemKeys {

        @Test
        void refusesAWriteThroughTheView() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();

            // ACT
            var actualThrown = catchThrowable(() -> occupancy.getSpotlitPresenceSystemKeys().add(PRESENT_SYSTEM));

            // ASSERT
            assertThat(actualThrown)
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class RecordOwnerOf {

        @Test
        void replacesTheStandingOwner() {

            // ARRANGE
            var occupancy = createSolidlyFilledOccupancyOf(
                Map.of(HELD_SYSTEM, HEGEMONY),
                Set.of(),
                Set.of());

            // ACT
            occupancy.recordOwnerOf(HELD_SYSTEM, HELD_BY_TRITACHYON);

            // ASSERT
            assertThat(occupancy.getOwnerBySystemKey())
                .containsExactly(Map.entry(HELD_SYSTEM, TRITACHYON));
        }

        // An emptied system: the entry goes rather than being left pointing at the owner that lost
        // it, since every reader takes an absent entry for "nobody owns this".
        @Test
        void dropsTheEntryWhenNobodyHoldsItNow() {

            // ARRANGE
            var occupancy = createSolidlyFilledOccupancyOf(
                Map.of(HELD_SYSTEM, HEGEMONY),
                Set.of(),
                Set.of());

            // ACT
            occupancy.recordOwnerOf(HELD_SYSTEM, ResolvedSystemOwner.UNOWNED);

            // ASSERT
            assertThat(occupancy.getOwnerBySystemKey())
                .isEmpty();
        }

        // The collision the ownership is keyed by SystemKey to survive: each of the pair keeps its own
        // owner, where a map keyed by ID held one entry and drew the second system in the first's
        // colours.
        @Test
        void holdsTwoSystemsSharingAnIdUnderDifferentOwners() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();

            // ACT
            occupancy.recordOwnerOf(FIRST_TWIN, HELD_BY_HEGEMONY);
            occupancy.recordOwnerOf(SECOND_TWIN, HELD_BY_TRITACHYON);

            // ASSERT
            assertThat(occupancy.getOwnerBySystemKey())
                .containsExactly(
                    Map.entry(FIRST_TWIN, HEGEMONY),
                    Map.entry(SECOND_TWIN, TRITACHYON));
        }

        // The fill state is recorded beside the owner, so a system a batch re-derives from solid to
        // unfilled draws unfilled - where a fold of the owner alone left it as the rebuild drew it.
        @Test
        void recordsTheFillStateBesideTheOwner() {

            // ARRANGE
            var occupancy = createSolidlyFilledOccupancyOf(Map.of(HELD_SYSTEM, HEGEMONY), Set.of(), Set.of());

            // ACT
            occupancy.recordOwnerOf(HELD_SYSTEM, UNFILLED_BY_HEGEMONY);

            // ASSERT
            assertThat(occupancy.snapshotFillStates().getNonSolidFillStateBySystemKey())
                .containsExactly(Map.entry(HELD_SYSTEM, FillState.UNFILLED));
        }

        // A solid answer clears the state the system held, so a system filled again fills solid.
        @Test
        void clearsTheFillStateOfASystemNowFillingSolid() {

            // ARRANGE
            var occupancy = createSolidlyFilledOccupancyOf(Map.of(), Set.of(), Set.of());

            occupancy.recordOwnerOf(HELD_SYSTEM, UNFILLED_BY_HEGEMONY);

            // ACT
            occupancy.recordOwnerOf(HELD_SYSTEM, HELD_BY_HEGEMONY);

            // ASSERT
            assertThat(occupancy.snapshotFillStates())
                .isEqualTo(SystemFillStates.ALL_SOLID);
        }

        // An emptied system takes no state with it to whoever holds it next.
        @Test
        void clearsTheFillStateOfASystemNobodyHoldsNow() {

            // ARRANGE
            var occupancy = createSolidlyFilledOccupancyOf(Map.of(), Set.of(), Set.of());

            occupancy.recordOwnerOf(HELD_SYSTEM, UNFILLED_BY_HEGEMONY);

            // ACT
            occupancy.recordOwnerOf(HELD_SYSTEM, ResolvedSystemOwner.UNOWNED);

            // ASSERT
            assertThat(occupancy.snapshotFillStates())
                .isEqualTo(SystemFillStates.ALL_SOLID);
        }
    }

    @Nested
    class ReadResolvedOwnerOf {

        // The answer a re-derive compares its own against, so it carries both halves.
        @Test
        void answersTheOwnerAndTheFillStateTogether() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();
            var expectedResolved = new ResolvedSystemOwner(HEGEMONY, FillState.UNFILLED);

            occupancy.recordOwnerOf(HELD_SYSTEM, UNFILLED_BY_HEGEMONY);

            // ACT
            var actualResolved = occupancy.readResolvedOwnerOf(HELD_SYSTEM);

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(expectedResolved);
        }

        // Only the states other than solid are recorded, so a held system with none beside it fills
        // solid. A re-derive answering solid for it then finds nothing moved, where any other default
        // would refill every solid system on the first batch that marked it.
        @Test
        void answersAHeldSystemWithNoRecordedFillStateAsFillingSolid() {

            // ARRANGE
            var occupancy = createSolidlyFilledOccupancyOf(Map.of(HELD_SYSTEM, HEGEMONY), Set.of(), Set.of());
            var expectedResolved = new ResolvedSystemOwner(HEGEMONY, FillState.SOLID);

            // ACT
            var actualResolved = occupancy.readResolvedOwnerOf(HELD_SYSTEM);

            // ASSERT
            assertThat(actualResolved)
                .isEqualTo(expectedResolved);
        }

        @Test
        void answersUnownedForASystemNobodyHolds() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();

            // ACT
            var actualResolved = occupancy.readResolvedOwnerOf(HELD_SYSTEM);

            // ASSERT
            assertThat(actualResolved)
                .isSameAs(ResolvedSystemOwner.UNOWNED);
        }
    }

    @Nested
    class SnapshotFillStates {

        // A copy, so a builder traces every owner of its pass against one state of the map even
        // where a fold lands between two of its owners.
        @Test
        void keepsWhatItSawThroughALaterFold() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();
            var snapshot = occupancy.snapshotFillStates();

            // ACT
            occupancy.recordOwnerOf(HELD_SYSTEM, UNFILLED_BY_HEGEMONY);

            // ASSERT
            assertThat(snapshot)
                .isEqualTo(SystemFillStates.ALL_SOLID);
        }
    }

    @Nested
    class FoldInhabitationOf {

        @Test
        void reportsASystemTakingItsFirstSettlement() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();

            // ACT
            var actualHasMoved = occupancy.foldInhabitationOf(SETTLED_SYSTEM, true);

            // ASSERT
            assertThat(actualHasMoved)
                .isTrue();
            assertThat(occupancy.getInhabitedSystemKeys())
                .containsExactly(SETTLED_SYSTEM);
        }

        @Test
        void reportsASystemLosingItsLastSettlement() {

            // ARRANGE
            var occupancy = createSolidlyFilledOccupancyOf(
                Map.of(),
                Set.of(SETTLED_SYSTEM),
                Set.of());

            // ACT
            var actualHasMoved = occupancy.foldInhabitationOf(SETTLED_SYSTEM, false);

            // ASSERT
            assertThat(actualHasMoved)
                .isTrue();
            assertThat(occupancy.getInhabitedSystemKeys())
                .isEmpty();
        }

        // The common resize: a settlement grew. Reporting a change here would redraw a cell that
        // draws exactly as it did, on every marked change in the sector.
        @Test
        void reportsNothingForASystemThatWasAlreadySettled() {

            // ARRANGE
            var occupancy = createSolidlyFilledOccupancyOf(
                Map.of(),
                Set.of(SETTLED_SYSTEM),
                Set.of());

            // ACT
            var actualHasMoved = occupancy.foldInhabitationOf(SETTLED_SYSTEM, true);

            // ASSERT
            assertThat(actualHasMoved)
                .isFalse();
        }

        @Test
        void reportsNothingForASystemThatWasAlreadyEmpty() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();

            // ACT
            var actualHasMoved = occupancy.foldInhabitationOf(SETTLED_SYSTEM, false);

            // ASSERT
            assertThat(actualHasMoved)
                .isFalse();
        }
    }

    @Nested
    class FoldSpotlitPresenceOf {

        @Test
        void reportsThePickArrivingInASystem() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();

            // ACT
            var actualHasMoved = occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, true);

            // ASSERT
            assertThat(actualHasMoved)
                .isTrue();
            assertThat(occupancy.getSpotlitPresenceSystemKeys())
                .containsExactly(PRESENT_SYSTEM);
        }

        @Test
        void reportsThePickLeavingASystem() {

            // ARRANGE
            var occupancy = createSolidlyFilledOccupancyOf(
                Map.of(),
                Set.of(),
                Set.of(PRESENT_SYSTEM));

            // ACT
            var actualHasMoved = occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, false);

            // ASSERT
            assertThat(actualHasMoved)
                .isTrue();
            assertThat(occupancy.getSpotlitPresenceSystemKeys())
                .isEmpty();
        }

        @Test
        void reportsNothingWhereThePickWasAlreadyLiving() {

            // ARRANGE
            var occupancy = createSolidlyFilledOccupancyOf(
                Map.of(),
                Set.of(),
                Set.of(PRESENT_SYSTEM));

            // ACT
            var actualHasMoved = occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, true);

            // ASSERT
            assertThat(actualHasMoved)
                .isFalse();
        }

        // The pair the address exists to tell apart, asked of the fold: the pick arriving in one of
        // them leaves the other where it was, where a set keyed by ID would have spared both cells
        // the recede at once.
        @Test
        void movesOnlyTheSystemOfAPairSharingAnId() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();

            // ACT
            var actualHasMoved = occupancy.foldSpotlitPresenceOf(FIRST_TWIN, true);

            // ASSERT
            assertThat(actualHasMoved)
                .isTrue();
            assertThat(occupancy.getSpotlitPresenceSystemKeys())
                .containsExactly(FIRST_TWIN);
        }

        // The two folds run over the same system keys and answer the same shape of question, so a
        // set named wrong in one of them would read as correct at every other assertion here.
        @Test
        void leavesInhabitationAlone() {

            // ARRANGE
            var occupancy = createEmptyOccupancy();

            // ACT
            occupancy.foldSpotlitPresenceOf(PRESENT_SYSTEM, true);

            // ASSERT
            assertThat(occupancy.getInhabitedSystemKeys())
                .isEmpty();
        }
    }
}
