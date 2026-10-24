package kmu.maplayers.ownermap.render.labels;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.installed.LazyFontCache;

import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.SpotlitOwners;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;
import kmu.maplayers.ownermap.render.clusters.OwnerMapBuildInputs;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.clusters.SystemOccupancy;
import kmu.maplayers.ownermap.render.style.MapStyling;
import kmu.maplayers.ownermap.render.style.OwnerCategoriesFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the reads that turn a finished build, or a diagnostic's traced owners, into the values a
 * label rebuild styles from. The point of the type is that they describe one pass, so the build's
 * read is pinned by identity checks against the clusters' own retained state rather than by value
 * comparisons: a snapshot that copied or re-derived any of them could drift from the fills it must
 * match. What the snapshot then hands the fit drive is pinned last.
 *
 * <p>Whether a fit is wanted is the one answer the two paths give differently, so each path's
 * answer is pinned under every reading of the names and the overlay that tells it apart. Both are
 * the rebuild's own samplings, so no case here stubs a setting.
 */
final class ClusterLabelStylingSnapshotTests {

    private static final String SPOTLIT_OWNER_ID = "hegemony";

    // The face a snapshot is handed, for the cases about anything else it carries.
    private static final StarsectorFont LABEL_FACE = StarsectorFont.VANILLA_INSIGNIA_42;

    @Nested
    class ResolveFrom {

        @Test
        void carriesTheOwnersOfTheClusters() {

            var ownerBySystemKey = Map.of(
                buildCellKey("corvus"),
                new SystemOwner(
                    SPOTLIT_OWNER_ID,
                    new ColourPair(Color.BLUE, Color.DARK_GRAY)));

            var clusters = buildClusters(
                ownerBySystemKey,
                buildViewReading(),
                buildSpotlightPicks());

            var styling = ClusterLabelStylingSnapshot.resolveFrom(clusters, LABEL_FACE);

            // Against what the built map itself hands out rather than against what was handed to
            // it: the owners are the occupancy's own, so identity here is what says the snapshot
            // reads them live rather than taking a copy that a later refresh would leave behind.
            assertThat(styling.ownerBySystemKey())
                .isSameAs(clusters.getOccupancy().getOwnerBySystemKey());
        }

        @Test
        void carriesTheBuildsOwnPaints() {
            // The very paints the fills were painted in, not a second set over the same parts: a
            // name's colour is then read off the style decision its fill was, remembered once.
            var clusters = buildClusters(
                Map.of(),
                buildViewReading(),
                buildSpotlightPicks());

            var styling = ClusterLabelStylingSnapshot.resolveFrom(clusters, LABEL_FACE);

            assertThat(styling.paints())
                .isSameAs(clusters.getBuildInputs().getPaints());
        }

        @Test
        void carriesTheBuildsStylingReadingAndSampledPicksWhole() {
            // Taken as the retained values rather than re-resolved, so the scheme a label is
            // coloured by, the reading it is named under, the filter it recedes by and the format
            // it is spelled in cannot come from two passes.
            var viewReading = buildViewReading();
            var contentInputs = buildSpotlightPicks();

            var clusters = buildClusters(
                Map.of(),
                viewReading,
                contentInputs);

            var styling = ClusterLabelStylingSnapshot.resolveFrom(clusters, LABEL_FACE);

            assertThat(styling.paints().getStyling())
                .isSameAs(clusters.getBuildInputs().getStyling());
            assertThat(styling.paints().getReading())
                .isSameAs(viewReading.reading());
            assertThat(styling.paints().getContentInputs())
                .isSameAs(contentInputs);
        }

        @Test
        void carriesAnUnfilteredPassAsUnfiltered() {
            // A pass with no spotlight still answers the filter question, so the label rebuild
            // reads "nothing recedes" off the snapshot rather than off a null it must interpret.
            var styling = ClusterLabelStylingSnapshot.resolveFrom(
                buildClusters(
                    Map.of(),
                    buildViewReading(),
                    ContentInputs.createEmpty()),
                LABEL_FACE);

            assertThat(styling.paints().getContentInputs().isFiltering())
                .isFalse();
        }

        @Test
        void carriesTheFaceTheSectorSettledItsLabelsOn() {
            // Settled by the cache that asked for the rebuild, so the names are fitted in the face
            // they are minted in rather than in one the snapshot chose for itself.
            var styling = ClusterLabelStylingSnapshot.resolveFrom(
                buildClusters(
                    Map.of(),
                    buildViewReading(),
                    ContentInputs.createEmpty()),
                StarsectorFont.VANILLA_INSIGNIA_25);

            assertThat(styling.labelFace())
                .isEqualTo(StarsectorFont.VANILLA_INSIGNIA_25);
        }

        // The names hang off the placements, so drawing them is reason enough to fit.
        @Test
        void wantsAFitWhileTheBuildDrawsNames() {

            // ARRANGE
            var clusters = buildClusters(
                Map.of(),
                buildViewReading(),
                ContentInputsFixtures.createInputsSpellingNames(OwnerNameFormatChoice.FULL),
                false);

            // ACT
            var actualIsFitWanted = ClusterLabelStylingSnapshot
                .resolveFrom(clusters, LABEL_FACE)
                .isFitWanted();

            // ASSERT
            assertThat(actualIsFitWanted)
                .isTrue();
        }

        // The anchor overlay draws the same placements the names hang off, so it has to be able to
        // hold the fit open on its own. Otherwise the diagnostic shows nothing exactly when the
        // player turned the names off to look at it.
        @Test
        void wantsAFitForTheOverlayWhileTheBuildDrawsNoNames() {

            // ARRANGE
            var clusters = buildClusters(
                Map.of(),
                buildViewReading(),
                ContentInputsFixtures.createInputsSpellingNames(OwnerNameFormatChoice.NONE),
                true);

            // ACT
            var actualIsFitWanted = ClusterLabelStylingSnapshot
                .resolveFrom(clusters, LABEL_FACE)
                .isFitWanted();

            // ASSERT
            assertThat(actualIsFitWanted)
                .isTrue();
        }

        // Nothing drawn reads the placements, so the sector's whole cluster search is skipped.
        @Test
        void wantsNoFitWhileTheBuildDrawsNeitherNamesNorTheOverlay() {

            // ARRANGE
            var clusters = buildClusters(
                Map.of(),
                buildViewReading(),
                ContentInputsFixtures.createInputsSpellingNames(OwnerNameFormatChoice.NONE),
                false);

            // ACT
            var actualIsFitWanted = ClusterLabelStylingSnapshot
                .resolveFrom(clusters, LABEL_FACE)
                .isFitWanted();

            // ASSERT
            assertThat(actualIsFitWanted)
                .isFalse();
        }
    }

    @Nested
    class ResolveForTracing {

        @Test
        void carriesTheTracedOwnersTheHandedStylingAndTheViewsReading() {
            // The styling is the one the overlay's outlines were traced under, carried by identity:
            // a snapshot re-reading the theme for itself could style the names under knobs the
            // outlines beneath them were not traced under.
            var ownerBySystemKey = Map.of(
                buildCellKey("corvus"),
                new SystemOwner(SPOTLIT_OWNER_ID, new ColourPair(Color.BLUE, Color.DARK_GRAY)));

            var mapStyling = buildStyling();
            var reading = OwnerReadingFake.createAnsweringNothing();

            var styling = ClusterLabelStylingSnapshot.resolveForTracing(
                ownerBySystemKey,
                mapStyling,
                reading,
                ContentInputs.createEmpty(),
                false,
                LABEL_FACE);

            assertThat(styling.ownerBySystemKey())
                .isSameAs(ownerBySystemKey);
            assertThat(styling.paints().getStyling())
                .isSameAs(mapStyling);
            assertThat(styling.paints().getReading())
                .isSameAs(reading);
            assertThat(styling.labelFace())
                .isEqualTo(StarsectorFont.VANILLA_INSIGNIA_42);
        }

        @Test
        void dropsThePickButKeepsTheNameFormat() {
            // The diagnostic traces owners with nobody spotlit, so a pick carried through would
            // recede every owner but one around a spotlight it does not paint - while the names are
            // still spelled the way the player asked.
            var spotlightPicks = buildSpotlightPicks();

            var styling = ClusterLabelStylingSnapshot.resolveForTracing(
                Map.of(),
                buildStyling(),
                OwnerReadingFake.createAnsweringNothing(),
                spotlightPicks,
                false,
                LABEL_FACE);

            assertThat(styling.paints().getContentInputs().isFiltering())
                .isFalse();
            assertThat(styling.paints().getContentInputs().nameFormat())
                .isEqualTo(spotlightPicks.nameFormat());
        }

        // The view is a look at the anchors, so the overlay holds the fit open whatever the names do:
        // its dots still show for a player reading the map by colour alone.
        @Test
        void wantsAFitWhileTheOverlayIsShownThoughNoNamesAreDrawn() {

            // ARRANGE
            var contentInputs = ContentInputsFixtures.createInputsSpellingNames(OwnerNameFormatChoice.NONE);

            // ACT
            var actualIsFitWanted = buildTracedSnapshot(contentInputs, true).isFitWanted();

            // ASSERT
            assertThat(actualIsFitWanted)
                .isTrue();
        }

        // The fit is the cost here, so the overlay gates it and not just the drawing, even with the
        // names on: the view fits nothing while nobody is looking at the anchors.
        @Test
        void wantsNoFitWhileTheOverlayIsOffThoughNamesAreDrawn() {

            // ARRANGE
            var contentInputs = ContentInputsFixtures.createInputsSpellingNames(OwnerNameFormatChoice.FULL);

            // ACT
            var actualIsFitWanted = buildTracedSnapshot(contentInputs, false).isFitWanted();

            // ASSERT
            assertThat(actualIsFitWanted)
                .isFalse();
        }
    }

    @Nested
    class CreateFitInputs {

        @Test
        void cutsTheClustersByOwnerId() {
            // The fit clusters cells by the key it is handed, so the owner tier hands it the owner
            // ID its fills fuse by, and the names trace as the fills do.
            var fitInputs = buildTracedSnapshot(ContentInputs.createEmpty(), false).createFitInputs();

            assertThat(fitInputs.ownerKeyBySystemKey())
                .isEqualTo(Map.of(buildCellKey("corvus"), SPOTLIT_OWNER_ID));
        }

        @Test
        void cutsAProductionBuildBySpotlightKeyWhereItsFillsFuseByOne() {
            // A filtered build fuses the spotlit cells under one synthetic key, so the fit has to be
            // handed that key rather than the selected owner's ID, or the solid and contested
            // clusters would merge under one name the fills keep apart.
            var spotlitOwner = SpotlitOwners.createSpotlitOwner(new ColourPair(Color.BLUE, Color.DARK_GRAY));
            var snapshot = ClusterLabelStylingSnapshot.resolveFrom(
                buildClusters(
                    Map.of(buildCellKey("corvus"), spotlitOwner),
                    buildViewReading(),
                    buildSpotlightPicks()),
                LABEL_FACE);

            var expectedOwnerKeys = Map.of(buildCellKey("corvus"), spotlitOwner.ownerId());
            var actualOwnerKeys = snapshot.createFitInputs().ownerKeyBySystemKey();

            assertThat(actualOwnerKeys)
                .isEqualTo(expectedOwnerKeys);
        }

        // A pass that wants no fit fits nothing, so handing over the inputs must not reach the label
        // font: the resolvers arriving as a function is what defers it.
        @Test
        void loadsNoFontUntilTheResolversAreAsked() {

            // ARRANGE
            var snapshot = buildTracedSnapshot(ContentInputs.createEmpty(), false);

            try (var fontsMock = mockStatic(LazyFontCache.class)) {

                // ACT
                snapshot.createFitInputs();

                // ASSERT
                fontsMock.verifyNoInteractions();
            }
        }

        // The names are measured in the face the fit hands over, not the snapshot's own: the fit
        // records that face beside its placements, so measuring in another would label them falsely.
        @Test
        void loadsTheFaceTheFitHandsOverOnceTheResolversAreAsked() {

            // ARRANGE
            var fitInputs = buildTracedSnapshot(ContentInputs.createEmpty(), false).createFitInputs();

            try (var fontsMock = mockStatic(LazyFontCache.class)) {

                // ACT
                fitInputs
                    .labelResolversByFace()
                    .apply(StarsectorFont.VANILLA_INSIGNIA_25);

                // ASSERT
                fontsMock.verify(() -> LazyFontCache.loadByFace(StarsectorFont.VANILLA_INSIGNIA_25));
            }
        }

        // The resolvers are this snapshot's, so a name shades in the palette its owner was painted
        // in rather than one the fit drive could look up for itself.
        @Test
        void shadesEachOwnerByTheSnapshotsOwnPaints() {

            // ARRANGE
            var fitInputs = buildTracedSnapshot(ContentInputs.createEmpty(), false).createFitInputs();
            var expectedColour = Color.BLUE;

            try (var fontsMock = mockStatic(LazyFontCache.class)) {

                // ACT
                var actualColour = fitInputs
                    .labelResolversByFace()
                    .apply(LABEL_FACE)
                    .labelColourByOwner()
                    .apply(SPOTLIT_OWNER_ID);

                // ASSERT
                assertThat(actualColour)
                    .isEqualTo(expectedColour);
            }
        }

        @Test
        void carriesTheFaceTheSnapshotSettled() {

            var fitInputs = buildTracedSnapshot(ContentInputs.createEmpty(), false).createFitInputs();

            assertThat(fitInputs.labelFace())
                .isEqualTo(LABEL_FACE);
        }

        // The demand is the snapshot's, answered by the path that made it; handing it over is all
        // this does, so a snapshot that wants a fit hands over a fit wanted.
        @Test
        void wantsAFitWhereTheSnapshotDoes() {

            // ARRANGE
            var snapshot = buildTracedSnapshot(ContentInputs.createEmpty(), true);

            // ACT
            var actualIsFitWanted = snapshot.createFitInputs().isFitWanted();

            // ASSERT
            assertThat(actualIsFitWanted)
                .isTrue();
        }

        // The other half of the case above, so a demand handed over as a constant cannot pass both.
        @Test
        void wantsNoFitWhereTheSnapshotDoesNot() {

            // ARRANGE
            var snapshot = buildTracedSnapshot(ContentInputs.createEmpty(), false);

            // ACT
            var actualIsFitWanted = snapshot.createFitInputs().isFitWanted();

            // ASSERT
            assertThat(actualIsFitWanted)
                .isFalse();
        }
    }

    // A built map whose build sampled the anchor overlay off, for the cases about anything else.
    private static OwnerMapClusters buildClusters(
            Map<SystemKey, SystemOwner> ownerBySystemKey,
            ViewReading viewReading,
            ContentInputs contentInputs) {

        return buildClusters(
            ownerBySystemKey,
            viewReading,
            contentInputs,
            false);
    }

    private static OwnerMapClusters buildClusters(
            Map<SystemKey, SystemOwner> ownerBySystemKey,
            ViewReading viewReading,
            ContentInputs contentInputs,
            boolean isAnchorOverlayShown) {

        return new OwnerMapClusters(
            SystemOccupancy.createCopyOf(ownerBySystemKey, Set.of(), Set.of()),
            new OwnerMapBuildInputs(
                buildStyling(),
                viewReading,
                contentInputs,
                isAnchorOverlayShown,
                SystemFillStates.ALL_SOLID));
    }

    // A tracing snapshot over one owned system under the given picks and overlay, with a scheme no
    // case reads into.
    private static ClusterLabelStylingSnapshot buildTracedSnapshot(
            ContentInputs contentInputs,
            boolean isAnchorOverlayShown) {

        return ClusterLabelStylingSnapshot.resolveForTracing(
            Map.of(
                buildCellKey("corvus"),
                new SystemOwner(SPOTLIT_OWNER_ID, new ColourPair(Color.BLUE, Color.DARK_GRAY))),
            buildStyling(),
            OwnerReadingFake.createAnsweringNothing(),
            contentInputs,
            isAnchorOverlayShown,
            LABEL_FACE);
    }

    // A scheme no case reads into: the snapshot carries it whole, so only its identity matters.
    private static MapStyling buildStyling() {

        return new MapStyling(
            null,
            null,
            OwnerCategoriesFake.SHARED_FAKE,
            OwnerMapClusterFixtures.NEUTRAL_PALETTE,
            new ColourPair(Color.LIGHT_GRAY, Color.GRAY),
            OwnerMapClusterFixtures.NEUTRAL_PALETTE);
    }

    // The view is a seam the snapshot never reads, so a mock stands in for whichever concrete view
    // was being painted; the reading is the one it carries on.
    private static ViewReading buildViewReading() {

        return new ViewReading(
            mock(OwnerPaintedView.class),
            OwnerReadingFake.createAnsweringNothing(),
            new OwnerSourceFake());
    }

    private static ContentInputs buildSpotlightPicks() {

        return ContentInputsFixtures.createInputsSpotlighting(SPOTLIT_OWNER_ID);
    }
}
