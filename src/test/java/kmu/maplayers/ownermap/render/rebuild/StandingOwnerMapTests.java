package kmu.maplayers.ownermap.render.rebuild;

import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.AnchorFitFingerprint;
import kmu.maplayers.base.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.base.labels.anchor.ClusterNameDisturbance;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.ribbon.CellRibbonsBaker;
import kmu.maplayers.ownermap.render.ribbon.RibbonBakeSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;

/**
 * Pins the stages the full rebuild and a batch both run over a standing map: the name fit lands in
 * the map's own placements over its own cells, the batch's re-fit measures in the face the standing
 * names were fitted in and mints the labels under the name choice the build was baked under, and the
 * band bake is stood up over the map's own parts.
 *
 * <p>A unit test, so the fit, the mint and the bake are stood in for at their static seams: each is
 * pinned by its own suite, and what belongs here is what the map hands them.
 */
final class StandingOwnerMapTests {

    // The face the standing names were fitted in, and a different face a full rebuild fits in, so a
    // re-fit reaching for the wrong one cannot pass.
    private static final StarsectorFont STANDING_LABEL_FACE = StarsectorFont.VANILLA_INSIGNIA_25;
    private static final StarsectorFont REBUILD_LABEL_FACE = StarsectorFont.VANILLA_INSIGNIA_42;

    // What the standing placements were fitted under. Only the face is read here.
    private static final AnchorFitFingerprint STANDING_FIT =
        new AnchorFitFingerprint(null, 0, STANDING_LABEL_FACE);

    // The walk a pass bakes over. Over no sector, the bake being stood in for.
    private static final SectorWalk WALK =
        new SectorWalk(new SectorPassIndex(null), MapVisibilityRules.BASE);

    // The classes this arrangement stands in for, closed on the way out.
    private final StaticSeams seams = new StaticSeams();

    private final StandingClusterAnchors standingAnchors = new StandingClusterAnchors();
    private final List<Label> ownerLabels = new ArrayList<>();
    private final RevisedCellGeometry cellGeometry = new RevisedCellGeometry(new CellGeometryCache(), 1);

    private MockedStatic<ClusterAnchorsBuilder> anchorsMock;
    private MockedStatic<LabelsBuilder> labelsMock;

    @BeforeEach
    void openSeams() {

        anchorsMock = seams.openSeam(ClusterAnchorsBuilder.class);
        labelsMock = seams.openSeam(LabelsBuilder.class);

        // The placements arrive fitted by a pass before this one, which is where a re-fit reads its
        // face from.
        standingAnchors.replaceAnchors(List.of(), STANDING_FIT);
    }

    @AfterEach
    void closeSeams() {
        seams.closeEverySeam();
    }

    @Nested
    class FitNamesIn {

        // The fit replaces the placements it is handed, so a copy handed down would leave the map
        // holding placements labelled by the pass before this one.
        @Test
        void fitsIntoTheMapsOwnPlacementsOverItsOwnCells() {

            // ARRANGE
            var standingMap = buildStandingMapOver(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            // ACT
            standingMap.fitNamesIn(REBUILD_LABEL_FACE);

            // ASSERT
            anchorsMock.verify(() -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                same(standingAnchors),
                same(cellGeometry),
                argThat(fitInputs -> fitInputs.labelFace() == REBUILD_LABEL_FACE)));
        }

        // The bands after the fit are re-laid around what it moved, so its answer is handed on.
        @Test
        void answersWhatTheFitMoved() {

            // ARRANGE
            var standingMap = buildStandingMapOver(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));
            var expectedNameDisturbanceMock = mock(ClusterNameDisturbance.class);

            anchorsMock
                .when(() -> ClusterAnchorsBuilder.rebuildClusterAnchors(any(), any(), any()))
                .thenReturn(expectedNameDisturbanceMock);

            // ACT
            var actualNameDisturbance = standingMap.fitNamesIn(REBUILD_LABEL_FACE);

            // ASSERT
            assertThat(actualNameDisturbance)
                .isSameAs(expectedNameDisturbanceMock);
        }
    }

    @Nested
    class RefitNames {

        // A batch re-fits some names and carries the rest over, so the re-fitted ones have to be
        // measured in the face the carried-over ones were, read off their own record.
        @Test
        void refitsInTheFaceTheStandingNamesWereFittedIn() {

            // ARRANGE
            var standingMap = buildStandingMapOver(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));

            // ACT
            standingMap.refitNames();

            // ASSERT
            anchorsMock.verify(() -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                same(standingAnchors),
                same(cellGeometry),
                argThat(fitInputs -> fitInputs.labelFace() == STANDING_LABEL_FACE)));
        }

        // A batch edits the build already on screen, so whether its labels draw is what that build
        // was baked under. Posed with the names on, so a mint handed a constant off cannot pass.
        @Test
        void mintsTheLabelsUnderTheNameChoiceTheBuildWasBakedUnder() {

            // ARRANGE
            var standingMap = buildStandingMapOver(OwnerMapClusterFixtures.createClustersSpellingNames(
                Map.of(),
                OwnerNameFormatChoice.FULL));

            // ACT
            standingMap.refitNames();

            // ASSERT
            labelsMock.verify(() -> LabelsBuilder.rebuildLabels(
                same(ownerLabels),
                same(standingAnchors),
                eq(true)));
        }

        // The other half of the case above, so a mint handed a constant on cannot pass either.
        @Test
        void mintsNoLabelsUnderABuildBakedWithTheNamesOff() {

            // ARRANGE
            var standingMap = buildStandingMapOver(OwnerMapClusterFixtures.createClustersSpellingNames(
                Map.of(),
                OwnerNameFormatChoice.NONE));

            // ACT
            standingMap.refitNames();

            // ASSERT
            labelsMock.verify(() -> LabelsBuilder.rebuildLabels(
                same(ownerLabels),
                same(standingAnchors),
                eq(false)));
        }

        @Test
        void answersWhatTheRefitMoved() {

            // ARRANGE
            var standingMap = buildStandingMapOver(OwnerMapClusterFixtures.createClustersOwnedBy(Map.of()));
            var expectedNameDisturbanceMock = mock(ClusterNameDisturbance.class);

            anchorsMock
                .when(() -> ClusterAnchorsBuilder.rebuildClusterAnchors(any(), any(), any()))
                .thenReturn(expectedNameDisturbanceMock);

            // ACT
            var actualNameDisturbance = standingMap.refitNames();

            // ASSERT
            assertThat(actualNameDisturbance)
                .isSameAs(expectedNameDisturbanceMock);
        }
    }

    @Nested
    class CreateRibbonsBakerOver {

        // The bands are laid in the map's own cells, around its own names, and counted over the
        // pass's walk, so a pass stood up over any other part would bake a different map.
        @Test
        void standsTheBakeUpOverTheMapsOwnPartsAndTheGivenWalk() {

            // ARRANGE
            var clusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());
            var standingMap = buildStandingMapOver(clusters);
            var expectedBakerMock = mock(CellRibbonsBaker.class);

            // The knobs are read live, from LunaLib no test JVM holds.
            seams.openSeam(RibbonBakeSettings.class);

            var bakerMock = seams.openSeam(CellRibbonsBaker.class);

            bakerMock
                .when(() -> CellRibbonsBaker.createForPass(
                    same(clusters),
                    same(cellGeometry.cells()),
                    same(WALK),
                    same(standingAnchors),
                    any()))
                .thenReturn(expectedBakerMock);

            // ACT
            var actualBaker = standingMap.createRibbonsBakerOver(WALK);

            // ASSERT
            assertThat(actualBaker)
                .isSameAs(expectedBakerMock);
        }
    }

    // A standing map over the given clusters and this suite's own placements, labels and cells, so a
    // case can read back which of them a stage was handed.
    private StandingOwnerMap buildStandingMapOver(OwnerMapClusters clusters) {
        return new StandingOwnerMap(clusters, standingAnchors, ownerLabels, cellGeometry);
    }
}
