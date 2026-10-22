package kmu.maplayers.ownermap.render.labels;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.font.StarsectorFont;

import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;
import kmu.maplayers.ownermap.render.clusters.OwnerMapBuildInputs;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.clusters.SystemOccupancy;
import kmu.maplayers.ownermap.render.style.MapStyling;
import kmu.maplayers.ownermap.render.style.OwnerCategoriesFake;
import kmu.settings.KmuOwnerMapDiagnosticsSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
                LABEL_FACE);

            assertThat(styling.paints().getContentInputs().isFiltering())
                .isFalse();
            assertThat(styling.paints().getContentInputs().nameFormat())
                .isEqualTo(spotlightPicks.nameFormat());
        }
    }

    @Nested
    class CreateFitInputs {

        private MockedStatic<KmuOwnerMapDiagnosticsSettings> settingsMock;

        @BeforeEach
        void openTheSettingsSeam() {

            settingsMock = mockStatic(KmuOwnerMapDiagnosticsSettings.class);
            settingsMock
                .when(KmuOwnerMapDiagnosticsSettings::getOwnerMapShowClusterAnchors)
                .thenReturn(false);
        }

        @AfterEach
        void closeTheSettingsSeam() {
            settingsMock.close();
        }

        @Test
        void cutsTheClustersByOwnerId() {
            // The fit clusters cells by the key it is handed, so the owner tier hands it the owner
            // ID its fills fuse by - a spotlight key included, so the names trace as the fills do.
            var fitInputs = buildTracedSnapshot(ContentInputs.createEmpty()).createFitInputs();

            assertThat(fitInputs.ownerKeyBySystemKey())
                .isEqualTo(Map.of(buildCellKey("corvus"), SPOTLIT_OWNER_ID));
        }

        @Test
        void carriesTheFaceTheSnapshotSettled() {

            var fitInputs = buildTracedSnapshot(ContentInputs.createEmpty()).createFitInputs();

            assertThat(fitInputs.labelFace())
                .isEqualTo(LABEL_FACE);
        }

        @Test
        void drawsNamesWhereThePicksSpellThem() {

            var fitInputs = buildTracedSnapshot(
                    ContentInputsFixtures.createInputsSpellingNames(OwnerNameFormatChoice.FULL))
                .createFitInputs();

            assertThat(fitInputs.areNamesDrawn())
                .isTrue();
        }

        @Test
        void drawsNoNamesWhereThePicksSpellNone() {

            var fitInputs = buildTracedSnapshot(
                    ContentInputsFixtures.createInputsSpellingNames(OwnerNameFormatChoice.NONE))
                .createFitInputs();

            assertThat(fitInputs.areNamesDrawn())
                .isFalse();
        }

        @Test
        void showsTheAnchorOverlayWhereThePlayerHasItOn() {
            // Read here, the one point every path asks for its inputs, so every path gates alike.
            settingsMock
                .when(KmuOwnerMapDiagnosticsSettings::getOwnerMapShowClusterAnchors)
                .thenReturn(true);

            var fitInputs = buildTracedSnapshot(ContentInputs.createEmpty()).createFitInputs();

            assertThat(fitInputs.isAnchorOverlayShown())
                .isTrue();
        }

        // A snapshot over one owned system under the given picks, with a scheme no case reads into.
        private ClusterLabelStylingSnapshot buildTracedSnapshot(ContentInputs contentInputs) {

            return ClusterLabelStylingSnapshot.resolveForTracing(
                Map.of(
                    buildCellKey("corvus"),
                    new SystemOwner(SPOTLIT_OWNER_ID, new ColourPair(Color.BLUE, Color.DARK_GRAY))),
                buildStyling(),
                OwnerReadingFake.createAnsweringNothing(),
                contentInputs,
                LABEL_FACE);
        }
    }

    private static OwnerMapClusters buildClusters(
            Map<SystemKey, SystemOwner> ownerBySystemKey,
            ViewReading viewReading,
            ContentInputs contentInputs) {

        return new OwnerMapClusters(
            SystemOccupancy.createCopyOf(ownerBySystemKey, Set.of(), Set.of()),
            new OwnerMapBuildInputs(
                buildStyling(),
                viewReading,
                contentInputs,
                SystemFillStates.ALL_SOLID));
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
