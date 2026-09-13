package kmu.maplayers.base.machinery;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.refresh.MovableSystemSectorFake;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.refresh.MovableSystemSectorFake.FORCED_ONTO_MAP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the lifetime: that two sectors get two machinery, that installing again on one replaces
 * rather than accumulates, that removal and a load release what they drop and leave none of a
 * released sector's state to the machinery after it, and that a sector with nothing installed
 * resolves to something rather than to null.
 *
 * <p>What each machinery is made <em>over</em> is pinned here too: the sector a rebuild cuts from
 * and a poll walks is the one it was installed on, and the shared detached machinery is over no
 * sector at all.
 *
 * <p>The location resolution is pinned beside the sector one: a render surface is terrain and reaches
 * only its containing location, so machinery has to be findable by the hyperspace its sector's
 * surfaces sit in - and has to stop being findable there the moment it is released.
 *
 * <p>The index is process-wide, so every case starts from a cleared one.
 */
class SectorMapMachineryIndexTest {

    // The ID the staged drifting system reports. A staged system states no centre and no anchor,
    // so this alone is the key a motion observation is made under - and so what a discarded
    // machinery could leave behind for the next one.
    private static final String DRIFTER_ID = "a";

    // The ID a published hover names, a hover being the other thing keyed by bare system ID that a
    // discarded machinery could leave lit for the next one.
    private static final String HOVERED_SYSTEM_ID = "b";

    @BeforeEach
    void clearEveryMachinery() {
        SectorMapMachineryIndex.disposeAllMachinery();
    }

    @Nested
    class InstallMachineryOn {

        @Test
        void givesEachSectorMachineryOfItsOwn() {

            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);

            var machinery = SectorMapMachineryIndex.installMachineryOn(sectorMock);
            var otherMachinery = SectorMapMachineryIndex.installMachineryOn(otherSectorMock);

            assertThat(machinery)
                .isNotSameAs(otherMachinery);
            assertThat(SectorMapMachineryIndex.resolveMachineryFor(sectorMock))
                .isSameAs(machinery);
            assertThat(SectorMapMachineryIndex.resolveMachineryFor(otherSectorMock))
                .isSameAs(otherMachinery);
        }

        @Test
        void replacesTheSectorsStandingMachineryRatherThanAddingASecond() {
            // The hazard a second install poses is not a wasted allocation, it is two machinery
            // answering for one sector - so the first has to be released and forgotten, not merely
            // shadowed.
            var sectorMock = mock(SectorAPI.class);

            var firstMachinery = SectorMapMachineryIndex.installMachineryOn(sectorMock);
            var secondMachinery = SectorMapMachineryIndex.installMachineryOn(sectorMock);

            assertThat(secondMachinery)
                .isNotSameAs(firstMachinery);
            assertThat(firstMachinery.isDisposed())
                .isTrue();
            assertThat(SectorMapMachineryIndex.resolveMachineryFor(sectorMock))
                .isSameAs(secondMachinery);
        }

        @Test
        void givesEachMachineryTheSectorItWasInstalledOn() {
            // What the seams vanilla names no sector at read: the map hook and a terrain surface
            // reach a factor and a location, so the sector a rebuild cuts from and a poll walks
            // comes back out of the machinery rather than out of the running game. Two of them, so
            // the claim is that each answers its own rather than that either answers something.
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);

            var machinery = SectorMapMachineryIndex.installMachineryOn(sectorMock);
            var otherMachinery = SectorMapMachineryIndex.installMachineryOn(otherSectorMock);

            assertThat(machinery.resolveSector())
                .isSameAs(sectorMock);
            assertThat(otherMachinery.resolveSector())
                .isSameAs(otherSectorMock);
        }

        @Test
        void indexesNothingForNoSector() {
            // The switch can be flipped with no game loaded, so the entry point can reach here
            // holding null. The detached machinery is what that answers with, which is the same
            // holder any uninstalled sector resolves to.
            var machinery = SectorMapMachineryIndex.installMachineryOn(null);

            assertThat(machinery)
                .isSameAs(SectorMapMachineryIndex.resolveMachineryFor(mock(SectorAPI.class)));
        }
    }

    @Nested
    class UninstallMachineryFrom {

        @Test
        void releasesOnlyTheSectorItWasAskedAbout() {

            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);

            var machinery = SectorMapMachineryIndex.installMachineryOn(sectorMock);
            var otherMachinery = SectorMapMachineryIndex.installMachineryOn(otherSectorMock);

            SectorMapMachineryIndex.uninstallMachineryFrom(sectorMock);

            assertThat(machinery.isDisposed())
                .isTrue();
            assertThat(SectorMapMachineryIndex.resolveMachineryFor(sectorMock))
                .isNotSameAs(machinery);

            assertThat(otherMachinery.isDisposed())
                .isFalse();
            assertThat(SectorMapMachineryIndex.resolveMachineryFor(otherSectorMock))
                .isSameAs(otherMachinery);
        }

        @Test
        void standsDownForASectorWithNothingInstalled() {
            // A load that finds the overlay switched off takes it back from a sector it was never
            // installed on, so this is an ordinary path rather than a misuse - and the detached
            // machinery an uninstalled sector resolves to is shared, so releasing it here would
            // reach every other sector-less caller.
            var sectorMock = mock(SectorAPI.class);

            SectorMapMachineryIndex.uninstallMachineryFrom(sectorMock);

            assertThat(SectorMapMachineryIndex.resolveMachineryFor(sectorMock).isDisposed())
                .isFalse();
        }

        @Test
        void stopsAnsweringForTheReleasedSectorsHyperspace() {
            // The render surfaces are terrain in that hyperspace and ask by location, so a key left
            // behind would have them go on painting a sector the layers were taken off.
            var hyperspaceMock = mock(LocationAPI.class);
            var sector = buildSectorInHyperspace(hyperspaceMock);

            SectorMapMachineryIndex.installMachineryOn(sector);
            SectorMapMachineryIndex.uninstallMachineryFrom(sector);

            assertThat(SectorMapMachineryIndex.resolveMachineryIn(hyperspaceMock))
                .isNull();
        }

        @Test
        void standsDownForNoSector() {
            // The switch can be flipped with no game loaded, so the entry point reaches here
            // holding null.
            SectorMapMachineryIndex.uninstallMachineryFrom(null);

            var detachedMachinery =
                SectorMapMachineryIndex.resolveMachineryFor(mock(SectorAPI.class));

            assertThat(detachedMachinery.isDisposed())
                .isFalse();
        }
    }

    @Nested
    class DisposeAllMachinery {

        @Test
        void releasesEveryInstalledSectorsMachinery() {
            // What a load runs. Nothing tells this index that a sector was replaced, so an
            // machinery left standing here is one nothing would ever come back for.
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);

            var machinery = SectorMapMachineryIndex.installMachineryOn(sectorMock);
            var otherMachinery = SectorMapMachineryIndex.installMachineryOn(otherSectorMock);

            SectorMapMachineryIndex.disposeAllMachinery();

            assertThat(machinery.isDisposed())
                .isTrue();
            assertThat(otherMachinery.isDisposed())
                .isTrue();

            assertThat(SectorMapMachineryIndex.resolveMachineryFor(sectorMock))
                .isNotSameAs(machinery);
            assertThat(SectorMapMachineryIndex.resolveMachineryFor(otherSectorMock))
                .isNotSameAs(otherMachinery);
        }

        @Test
        void leavesNoneOfADiscardedSectorsMotionObservationsToTheMachineryAfterIt() {
            // The whole of what a per-load flush of the motion tracker used to be for. An
            // observation is keyed by system key, so a save reloaded in the same session would
            // otherwise have its systems measured against the positions the previous save last
            // saw them at, and a system that never moved would read as having teleported.
            var sectorFake = new MovableSystemSectorFake(DRIFTER_ID);
            var sector = sectorFake.getSector();

            var movingSystems = SectorMapMachineryIndex
                .installMachineryOn(sector)
                .resolveMovingSystems();

            sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();
            sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP);

            SectorMapMachineryIndex.disposeAllMachinery();

            var reinstalledMovingSystems = SectorMapMachineryIndex
                .installMachineryOn(sector)
                .resolveMovingSystems();

            assertThat(reinstalledMovingSystems.getMovingSystemKeys())
                .isEmpty();

            // And the first observation after the load seeds a baseline rather than reporting the
            // move it inherited.
            assertThat(sectorFake.observePositionsInto(reinstalledMovingSystems, FORCED_ONTO_MAP))
                .isFalse();
        }

        @Test
        void leavesNoneOfADiscardedSectorsHoverToTheMachineryAfterIt() {
            // The whole of what a per-load hover park used to be for. The hover names its system by
            // bare ID, so a save reloaded in the same session would otherwise open with a cell lit -
            // and a box naming it - for whatever the loaded sector happens to hold that id.
            var sectorMock = mock(SectorAPI.class);

            SectorMapMachineryIndex
                .installMachineryOn(sectorMock)
                .resolveHoverState()
                .publishHover(new MapHover(buildCellKey(HOVERED_SYSTEM_ID), List.of(buildCellKey(HOVERED_SYSTEM_ID))));

            SectorMapMachineryIndex.disposeAllMachinery();

            assertThat(SectorMapMachineryIndex
                    .installMachineryOn(sectorMock)
                    .resolveHoverState()
                    .getHover())
                .isSameAs(MapHover.NONE);
        }

        @Test
        void stopsAnsweringForEveryDiscardedSectorsHyperspace() {
            // A load replaces the sector, so a location key left standing would answer a surface
            // rebuilt from the loaded save with the machinery of the save before it.
            var hyperspaceMock = mock(LocationAPI.class);

            SectorMapMachineryIndex.installMachineryOn(buildSectorInHyperspace(hyperspaceMock));
            SectorMapMachineryIndex.disposeAllMachinery();

            assertThat(SectorMapMachineryIndex.resolveMachineryIn(hyperspaceMock))
                .isNull();
        }

        @Test
        void leavesTheDetachedMachineryStandingForAnUninstalledSector() {
            // The detached one is nobody's sector, so a load has nothing to discard of it - and a
            // caller reaching it after a load must not find a released machinery.
            var sectorMock = mock(SectorAPI.class);

            SectorMapMachineryIndex.disposeAllMachinery();

            assertThat(SectorMapMachineryIndex.resolveMachineryFor(sectorMock).isDisposed())
                .isFalse();
        }
    }

    @Nested
    class GetAllMachinery {

        @Test
        void listsTheMachineryOfEverySectorInstalledOn() {
            // What a preference shared by every campaign is applied through: a sector left out of
            // the walk would keep wiring the player took off the bar for all of them.
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);

            var machinery = SectorMapMachineryIndex.installMachineryOn(sectorMock);
            var otherMachinery = SectorMapMachineryIndex.installMachineryOn(otherSectorMock);

            assertThat(SectorMapMachineryIndex.getAllMachinery())
                .containsExactlyInAnyOrder(machinery, otherMachinery);
        }

        @Test
        void listsNoneOfTheDetachedMachineryOrTheSectorsReleased() {
            // The detached one is nobody's sector and a released one is a sector the load replaced,
            // so neither is something a walk over the installed sectors may act on.
            SectorMapMachineryIndex.installMachineryOn(mock(SectorAPI.class));
            SectorMapMachineryIndex.installMachineryOn(null);
            SectorMapMachineryIndex.disposeAllMachinery();

            assertThat(SectorMapMachineryIndex.getAllMachinery())
                .isEmpty();
        }
    }

    @Nested
    class ResolveMachineryFor {

        @Test
        void yieldsOneDetachedMachineryWhereTheSectorHasNone() {
            // Null here would make every seam below branch on a case that means "draw as you always
            // did", which is why an uninstalled sector answers with a holder rather than with
            // nothing. One holder for all of them, which is the arrangement a sector-less caller
            // has always had.
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);

            var machinery = SectorMapMachineryIndex.resolveMachineryFor(sectorMock);

            assertThat(machinery)
                .isNotNull();
            assertThat(machinery)
                .isSameAs(SectorMapMachineryIndex.resolveMachineryFor(otherSectorMock));
        }

        @Test
        void yieldsDetachedMachineryOverNoSectorAtAll() {
            // The detached one is shared by every sector-less caller, so it cannot answer with the
            // sector of whichever of them asked - and answering with the running game's would have
            // a stage reached through it draw a sector nobody asked it about. It has none, and says
            // so.
            var machinery = SectorMapMachineryIndex.resolveMachineryFor(mock(SectorAPI.class));

            assertThat(machinery.resolveSector())
                .isNull();
        }
    }

    @Nested
    class ResolveMachineryIn {

        @Test
        void yieldsTheMachineryOfTheSectorWhoseHyperspaceItIs() {
            // How a render surface finds what it is drawing. It is terrain, so the handle it has is
            // its own entity's containing location - and that location has to reach the same
            // machinery the sector does, or the surface paints through a sector's machinery
            // nobody installed.
            var hyperspaceMock = mock(LocationAPI.class);
            var otherHyperspaceMock = mock(LocationAPI.class);

            var machinery = SectorMapMachineryIndex.installMachineryOn(
                buildSectorInHyperspace(hyperspaceMock));

            SectorMapMachineryIndex.installMachineryOn(
                buildSectorInHyperspace(otherHyperspaceMock));

            assertThat(SectorMapMachineryIndex.resolveMachineryIn(hyperspaceMock))
                .isSameAs(machinery);
        }

        @Test
        void yieldsTheReplacementRatherThanTheMachineryAReinstallReleased() {
            // The hazard a location index of its own would carry. A second key has to be written
            // after the sector one, so between the two a surface resolving by location would be
            // handed the machinery the reinstall had just released - and would build a renderer,
            // and the GL buffers behind it, onto a holder nothing will ever release again.
            var hyperspaceMock = mock(LocationAPI.class);
            var sector = buildSectorInHyperspace(hyperspaceMock);

            var releasedMachinery = SectorMapMachineryIndex.installMachineryOn(sector);
            var machinery = SectorMapMachineryIndex.installMachineryOn(sector);

            assertThat(SectorMapMachineryIndex.resolveMachineryIn(hyperspaceMock))
                .isSameAs(machinery);
            assertThat(releasedMachinery.isDisposed())
                .isTrue();
        }

        @Test
        void yieldsNothingForALocationWithNothingInstalled() {
            // The one resolution that answers null rather than with the detached machinery. A
            // surface in such a location belongs to a sector nothing is drawing - a save carrying
            // the terrain with the overlay switched off - and painting it through the holder every
            // sector-less caller shares would be drawing no sector at all.
            assertThat(SectorMapMachineryIndex.resolveMachineryIn(mock(LocationAPI.class)))
                .isNull();
        }

        @Test
        void yieldsNothingForNoLocation() {
            // A surface the engine has built but not yet seated has no location to be asked about.
            assertThat(SectorMapMachineryIndex.resolveMachineryIn(null))
                .isNull();
        }
    }

    @Nested
    class ResolveMachineryForLiveSector {

        @Test
        void yieldsTheRunningSectorsMachinery() {
            // The seams vanilla drives without naming a sector land here, so what they resolve has
            // to be the running sector's rather than whichever sector was installed on last.
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);

            SectorMapMachineryIndex.installMachineryOn(otherSectorMock);

            var machinery = SectorMapMachineryIndex.installMachineryOn(sectorMock);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                assertThat(SectorMapMachineryIndex.resolveMachineryForLiveSector())
                    .isSameAs(machinery);
            }
        }

        @Test
        void yieldsTheDetachedMachineryWithNoGameLoaded() {

            var sectorMock = mock(SectorAPI.class);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThat(SectorMapMachineryIndex.resolveMachineryForLiveSector())
                    .isSameAs(SectorMapMachineryIndex.resolveMachineryFor(sectorMock));
            }
        }
    }

    // A sector answering only for the hyperspace its render surfaces would be installed in, which
    // is the second key machinery is indexed under.
    private static SectorAPI buildSectorInHyperspace(LocationAPI hyperspace) {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getHyperspace())
            .thenReturn(hyperspace);

        return sectorMock;
    }
}
