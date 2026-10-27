package kmu.maplayers.ownermap.render.rebuild;

import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that each base view hands itself to its own case and to no other. The cases sit in adjacent
 * same-shaped slots, so a view answering through the wrong one would still compile, and a frame
 * would paint the debug overlay's case over draw lists, or fold into the placeholder.
 */
final class BuiltViewTests {

    // The overlay a debug view carries. Empty, since no case reads into it.
    private static final ClusterBorderStageOverlay BORDER_STAGE_OVERLAY =
        new ClusterBorderStageOverlay(List.of(), List.of(), List.of());

    @Nested
    class SelectByCase {

        // The production case is the one the cursor, the bands and a fold all read the draw lists
        // through.
        @Test
        void handsTheProductionViewsDrawListsToTheProductionCase() {

            // ARRANGE
            var clusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());
            var builtView = new BuiltView.Production(clusters);

            // ACT
            var actualAnswer = selectCaseNameOf(builtView);

            // ASSERT
            assertThat(actualAnswer)
                .isEqualTo("production");
        }

        @Test
        void handsTheDebugViewsOverlayToTheDebugCase() {

            // ARRANGE
            var builtView = new BuiltView.Debug(BORDER_STAGE_OVERLAY);

            // ACT
            var actualAnswer = selectCaseNameOf(builtView);

            // ASSERT
            assertThat(actualAnswer)
                .isEqualTo("debug");
        }

        @Test
        void answersThePlaceholderCaseForThePlaceholder() {

            // ACT
            var actualAnswer = selectCaseNameOf(BuiltView.PLACEHOLDER);

            // ASSERT
            assertThat(actualAnswer)
                .isEqualTo("placeholder");
        }

        // The value each case is handed, by identity, so a case handed a copy would fail.
        @Test
        void handsEachCaseTheValueItsViewHolds() {

            // ARRANGE
            var clusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());

            // ACT
            var actualClusters = new BuiltView.Production(clusters).selectByCase(
                handedClusters -> handedClusters,
                borderStageOverlay -> null,
                () -> null);
            var actualOverlay = new BuiltView.Debug(BORDER_STAGE_OVERLAY).selectByCase(
                handedClusters -> null,
                borderStageOverlay -> borderStageOverlay,
                () -> null);

            // ASSERT
            assertThat(actualClusters)
                .isSameAs(clusters);
            assertThat(actualOverlay)
                .isSameAs(BORDER_STAGE_OVERLAY);
        }

        // Which case a view answered through, named.
        private static String selectCaseNameOf(BuiltView builtView) {

            return builtView.selectByCase(
                clusters -> "production",
                borderStageOverlay -> "debug",
                () -> "placeholder");
        }
    }

    @Nested
    class RunByCase {

        // The action form is written over the value form, so it is pinned to run exactly the one
        // case and none beside it.
        @Test
        void runsOnlyTheCaseOfTheViewItIsAsked() {

            // ARRANGE
            var clusters = OwnerMapClusterFixtures.createClustersOwnedBy(Map.of());
            var builtView = new BuiltView.Production(clusters);
            var actualRunCases = new ArrayList<String>();
            var actualHandedClusters = new ArrayList<OwnerMapClusters>();

            // ACT
            builtView.runByCase(
                handedClusters -> {

                    actualRunCases.add("production");
                    actualHandedClusters.add(handedClusters);
                },
                borderStageOverlay -> actualRunCases.add("debug"),
                () -> actualRunCases.add("placeholder"));

            // ASSERT
            assertThat(actualRunCases)
                .containsExactly("production");
            assertThat(actualHandedClusters)
                .singleElement()
                .isSameAs(clusters);
        }

        @Test
        void runsThePlaceholderCaseForThePlaceholder() {

            // ARRANGE
            var actualRunCases = new ArrayList<String>();

            // ACT
            BuiltView.PLACEHOLDER.runByCase(
                clusters -> actualRunCases.add("production"),
                borderStageOverlay -> actualRunCases.add("debug"),
                () -> actualRunCases.add("placeholder"));

            // ASSERT
            assertThat(actualRunCases)
                .containsExactly("placeholder");
        }
    }
}
