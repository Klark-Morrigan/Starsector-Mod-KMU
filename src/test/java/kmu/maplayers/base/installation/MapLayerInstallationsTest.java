package kmu.maplayers.base.installation;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.refresh.MovableSystemSectorFake;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.refresh.MovableSystemSectorFake.FORCED_ONTO_MAP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the lifetime: that two sectors get two installations, that installing again on one replaces
 * rather than accumulates, that removal and a load release what they drop and leave none of a
 * released sector's state to the installation after it, and that a sector with nothing installed
 * resolves to something rather than to null.
 *
 * <p>The index is process-wide, so every case starts from a cleared one.
 */
class MapLayerInstallationsTest {

    // The id the staged drifting system reports, which is what a motion observation is keyed by
    // and so what a discarded installation could leave behind for the next one.
    private static final String DRIFTER_ID = "a";

    @BeforeEach
    void clearEveryInstallation() {
        MapLayerInstallations.disposeEveryInstallation();
    }

    @Nested
    class InstallMachineryOn {

        @Test
        void givesEachSectorAnInstallationOfItsOwn() {

            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);

            var installation = MapLayerInstallations.installMachineryOn(sectorMock);
            var otherInstallation = MapLayerInstallations.installMachineryOn(otherSectorMock);

            assertThat(installation)
                .isNotSameAs(otherInstallation);
            assertThat(MapLayerInstallations.resolveInstallationFor(sectorMock))
                .isSameAs(installation);
            assertThat(MapLayerInstallations.resolveInstallationFor(otherSectorMock))
                .isSameAs(otherInstallation);
        }

        @Test
        void replacesTheSectorsStandingInstallationRatherThanAddingASecond() {
            // The hazard a second install poses is not a wasted allocation, it is two installations
            // answering for one sector - so the first has to be released and forgotten, not merely
            // shadowed.
            var sectorMock = mock(SectorAPI.class);

            var firstInstallation = MapLayerInstallations.installMachineryOn(sectorMock);
            var secondInstallation = MapLayerInstallations.installMachineryOn(sectorMock);

            assertThat(secondInstallation)
                .isNotSameAs(firstInstallation);
            assertThat(firstInstallation.isDisposed())
                .isTrue();
            assertThat(MapLayerInstallations.resolveInstallationFor(sectorMock))
                .isSameAs(secondInstallation);
        }

        @Test
        void indexesNothingForNoSector() {
            // The switch can be flipped with no game loaded, so the entry point can reach here
            // holding null. The detached installation is what that answers with, which is the same
            // holder any uninstalled sector resolves to.
            var installation = MapLayerInstallations.installMachineryOn(null);

            assertThat(installation)
                .isSameAs(MapLayerInstallations.resolveInstallationFor(mock(SectorAPI.class)));
        }
    }

    @Nested
    class UninstallMachineryFrom {

        @Test
        void releasesOnlyTheSectorItWasAskedAbout() {

            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);

            var installation = MapLayerInstallations.installMachineryOn(sectorMock);
            var otherInstallation = MapLayerInstallations.installMachineryOn(otherSectorMock);

            MapLayerInstallations.uninstallMachineryFrom(sectorMock);

            assertThat(installation.isDisposed())
                .isTrue();
            assertThat(MapLayerInstallations.resolveInstallationFor(sectorMock))
                .isNotSameAs(installation);

            assertThat(otherInstallation.isDisposed())
                .isFalse();
            assertThat(MapLayerInstallations.resolveInstallationFor(otherSectorMock))
                .isSameAs(otherInstallation);
        }

        @Test
        void standsDownForASectorWithNothingInstalled() {
            // A load that finds the overlay switched off takes it back from a sector it was never
            // installed on, so this is an ordinary path rather than a misuse - and the detached
            // installation an uninstalled sector resolves to is shared, so releasing it here would
            // reach every other sector-less caller.
            var sectorMock = mock(SectorAPI.class);

            MapLayerInstallations.uninstallMachineryFrom(sectorMock);

            assertThat(MapLayerInstallations.resolveInstallationFor(sectorMock).isDisposed())
                .isFalse();
        }

        @Test
        void standsDownForNoSector() {
            // The switch can be flipped with no game loaded, so the entry point reaches here
            // holding null.
            MapLayerInstallations.uninstallMachineryFrom(null);

            var detachedInstallation =
                MapLayerInstallations.resolveInstallationFor(mock(SectorAPI.class));

            assertThat(detachedInstallation.isDisposed())
                .isFalse();
        }
    }

    @Nested
    class DisposeEveryInstallation {

        @Test
        void releasesEveryInstalledSectorsMachinery() {
            // What a load runs. Nothing tells this index that a sector was replaced, so an
            // installation left standing here is one nothing would ever come back for.
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);

            var installation = MapLayerInstallations.installMachineryOn(sectorMock);
            var otherInstallation = MapLayerInstallations.installMachineryOn(otherSectorMock);

            MapLayerInstallations.disposeEveryInstallation();

            assertThat(installation.isDisposed())
                .isTrue();
            assertThat(otherInstallation.isDisposed())
                .isTrue();

            assertThat(MapLayerInstallations.resolveInstallationFor(sectorMock))
                .isNotSameAs(installation);
            assertThat(MapLayerInstallations.resolveInstallationFor(otherSectorMock))
                .isNotSameAs(otherInstallation);
        }

        @Test
        void leavesNoneOfADiscardedSectorsMotionObservationsToTheInstallationAfterIt() {
            // The whole of what a per-load flush of the motion tracker used to be for. An
            // observation is keyed by system id, so a save reloaded in the same session would
            // otherwise have its systems measured against the positions the previous save last
            // saw them at, and a system that never moved would read as having teleported.
            var sectorFake = new MovableSystemSectorFake(DRIFTER_ID);
            var sector = sectorFake.getSector();

            var movingSystems = MapLayerInstallations
                .installMachineryOn(sector)
                .resolveMovingSystems();

            sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();
            sectorFake.observePositionsInto(movingSystems, FORCED_ONTO_MAP);

            MapLayerInstallations.disposeEveryInstallation();

            var reinstalledMovingSystems = MapLayerInstallations
                .installMachineryOn(sector)
                .resolveMovingSystems();

            assertThat(reinstalledMovingSystems.getMovingSystemIds())
                .isEmpty();

            // And the first observation after the load seeds a baseline rather than reporting the
            // move it inherited.
            assertThat(sectorFake.observePositionsInto(reinstalledMovingSystems, FORCED_ONTO_MAP))
                .isFalse();
        }

        @Test
        void leavesTheDetachedInstallationStandingForAnUninstalledSector() {
            // The detached one is nobody's sector, so a load has nothing to discard of it - and a
            // caller reaching it after a load must not find a released installation.
            var sectorMock = mock(SectorAPI.class);

            MapLayerInstallations.disposeEveryInstallation();

            assertThat(MapLayerInstallations.resolveInstallationFor(sectorMock).isDisposed())
                .isFalse();
        }
    }

    @Nested
    class ResolveInstallationFor {

        @Test
        void yieldsOneDetachedInstallationWhereTheSectorHasNone() {
            // Null here would make every seam below branch on a case that means "draw as you always
            // did", which is why an uninstalled sector answers with a holder rather than with
            // nothing. One holder for all of them, which is the arrangement a sector-less caller
            // has always had.
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);

            var installation = MapLayerInstallations.resolveInstallationFor(sectorMock);

            assertThat(installation)
                .isNotNull();
            assertThat(installation)
                .isSameAs(MapLayerInstallations.resolveInstallationFor(otherSectorMock));
        }
    }

    @Nested
    class ResolveInstallationForLiveSector {

        @Test
        void yieldsTheRunningSectorsInstallation() {
            // The seams vanilla drives without naming a sector land here, so what they resolve has
            // to be the running sector's rather than whichever sector was installed on last.
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);

            MapLayerInstallations.installMachineryOn(otherSectorMock);

            var installation = MapLayerInstallations.installMachineryOn(sectorMock);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                assertThat(MapLayerInstallations.resolveInstallationForLiveSector())
                    .isSameAs(installation);
            }
        }

        @Test
        void yieldsTheDetachedInstallationWithNoGameLoaded() {

            var sectorMock = mock(SectorAPI.class);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThat(MapLayerInstallations.resolveInstallationForLiveSector())
                    .isSameAs(MapLayerInstallations.resolveInstallationFor(sectorMock));
            }
        }
    }
}
