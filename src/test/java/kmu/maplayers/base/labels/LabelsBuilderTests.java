package kmu.maplayers.base.labels;

import kmlib.math.geometry.Segment;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.installed.LazyFontCache;
import kmlib.testfixtures.profiling.RecordedCapture;

import kmu.maplayers.base.labels.anchor.AnchorFitFingerprint;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.labels.anchor.ClusterIdentity;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.profiling.MapBuildCounters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lazywizard.lazylib.ui.LazyFont.DrawableString;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the pure placement-to-label decision - {@link LabelsBuilder#planLabels} - which
 * turns the resolved cluster placements into the text, colour, hang point, slant, and font
 * size each label line draws with, before any GL string is minted: one plan per wrapped
 * line, the lines stacked along the accepted axis's perpendicular and centred as a block on
 * the anchor. The string minting, font load, and rendering only resolve in-engine, so they
 * are not covered here; the geometry that drives them is.
 *
 * <p>No test hands the planner a line spacing, because the planner takes none: the step
 * between stacked lines is read back out of the band the fit reserved. The fixtures size that
 * band the way the fit does, so the spacing a test names is the one the fit would have
 * recorded, and the planner is held to the box rather than to a setting.
 */
final class LabelsBuilderTests {

    private static final Color OWNER_COLOUR = Color.RED;

    // The row a mint lands on.
    private static final String BUILD_SECTION = "mapLayer.buildLabels";

    // An accepted line for a placement whose geometry no case in the mint cares about.
    private static final Segment HORIZONTAL_AXIS = new Segment(0f, 200f, 200f, 200f);

    // Which cluster a fixture placement was fitted to. The planner reads text, colour and
    // geometry only, so one identity serves every fixture.
    private static final ClusterIdentity CLUSTER_IDENTITY =
        new ClusterIdentity("owner", Set.of(buildCellKey("system")));

    // The stack geometry the multi-line tests compute by hand.
    private static final float FONT_HEIGHT = 100f;
    private static final double LINE_SPACING = 1.15;

    // The face the fixture placements were fitted in: a settled one rather than the face labels ask
    // for, so a mint drawing in the asked-for face cannot pass by coincidence.
    private static final StarsectorFont SETTLED_LABEL_FACE = StarsectorFont.VANILLA_INSIGNIA_25;

    @Nested
    class RebuildLabels {

        @Test
        void countsTheLinesItMinted() {
            // The number the mint's duration is read against, which used to be printed in a log
            // line the profiler never saw. How many clusters they came from rides on the call's
            // name instead, being a fact about one call rather than a volume of work.
            var fontMock = mock(LazyFont.class);

            when(fontMock.createText(anyString(), any(), anyFloat()))
                .thenReturn(mock(DrawableString.class));

            var labels = new ArrayList<Label>();
            var standingAnchors = buildStandingAnchors(
                buildAcceptedAnchor(List.of("Persean League"), 0, 0, HORIZONTAL_AXIS),
                buildAcceptedAnchor(List.of("Hegemony"), 500, 0, HORIZONTAL_AXIS));

            try (var fontsMock = mockStatic(LazyFontCache.class)) {

                fontsMock.when(() -> LazyFontCache.loadByFace(any()))
                    .thenReturn(fontMock);

                var capture = RecordedCapture.recordWhile(() ->
                    LabelsBuilder.rebuildLabels(labels, standingAnchors, true));

                var buildRow = capture.findNode(BUILD_SECTION);

                assertThat(buildRow.findCount(MapBuildCounters.LABELS).getTotals().getTotal())
                    .isEqualTo(2);
                assertThat(buildRow.getWorstCall().getTag())
                    .isEqualTo("ofClusters=2");
            }
        }

        @Test
        void mintsInTheFaceThePlacementsWereFittedIn() {
            // The boxes were sized to that face's glyphs, so a name minted in any other face would spill
            // out of or rattle inside the box it was fitted to. A settled face other than the one labels
            // ask for, so a face written down anywhere on the way would be caught.
            var labels = new ArrayList<Label>();
            var standingAnchors = buildStandingAnchors(
                buildAcceptedAnchor(List.of("Hegemony"), 0, 0, HORIZONTAL_AXIS));

            try (var fontsMock = mockStatic(LazyFontCache.class)) {

                LabelsBuilder.rebuildLabels(labels, standingAnchors, true);

                fontsMock.verify(() -> LazyFontCache.loadByFace(SETTLED_LABEL_FACE));
            }
        }

        @Test
        void mintsNothingWhereNothingWasFitted() {
            // A session's first frame and a discarded sector both stand at no fit, so there is neither a
            // name to mint nor a face to mint it in.
            var labels = new ArrayList<Label>();

            try (var fontsMock = mockStatic(LazyFontCache.class)) {

                LabelsBuilder.rebuildLabels(labels, new StandingClusterAnchors(), true);

                assertThat(labels)
                    .isEmpty();
                fontsMock.verifyNoInteractions();
            }
        }

        @Test
        void recordsNothingWhereNamesAreNotDrawn() {
            // The mint is skipped outright rather than measured as a call that did nothing, so a
            // reader following a rebuild is not shown a row for a stage that never ran.
            var capture = RecordedCapture.recordWhile(() ->
                LabelsBuilder.rebuildLabels(new ArrayList<>(), buildStandingAnchors(), false));

            assertThat(capture.hasNode(BUILD_SECTION))
                .isFalse();
        }
    }

    @Nested
    class PlanLabels {

        @Test
        void plansOneLineWithItsTextColourAndFontHeight() {
            var anchors = List.of(buildAcceptedAnchor(
                List.of("Persean League"),
                100f,
                200f,
                new Segment(0f, 200f, 200f, 200f)));

            var plans = LabelsBuilder.planLabels(anchors);

            assertThat(plans).singleElement().satisfies(plan -> {
                assertThat(plan.text()).isEqualTo("Persean League");
                assertThat(plan.colour()).isEqualTo(OWNER_COLOUR);
                assertThat(plan.fontHeight()).isEqualTo(FONT_HEIGHT);
            });
        }

        @Test
        void hangsASingleLineAtTheAnchorPoint() {
            // One line has no stack to spread: its centre is the block centre, the anchor.
            var anchors = List.of(buildAcceptedAnchor(
                List.of("Persean League"),
                100f,
                200f,
                new Segment(0f, 200f, 200f, 200f)));

            var plan = LabelsBuilder.planLabels(anchors).get(0);

            assertThat(plan.hangX()).isEqualTo(100f);
            assertThat(plan.hangY()).isEqualTo(200f);
        }

        @Test
        void stacksTwoLinesAcrossAHorizontalAxisFirstLineOnTop() {
            // A horizontal axis stacks straight up the y axis: line centres half a step
            // (font height times spacing) above and below the anchor, first line on the
            // upper side so the block reads top-down.
            var anchors = List.of(buildAcceptedAnchor(
                List.of("Persean", "League"),
                100f,
                200f,
                new Segment(0f, 200f, 200f, 200f)));

            var plans = LabelsBuilder.planLabels(anchors);

            assertThat(plans).hasSize(2);
            var halfStep = FONT_HEIGHT * (float) LINE_SPACING / 2f;

            assertThat(plans.get(0).text()).isEqualTo("Persean");
            assertThat(plans.get(0).hangX()).isCloseTo(100f, within(1e-3f));
            assertThat(plans.get(0).hangY()).isCloseTo(200f + halfStep, within(1e-3f));
            assertThat(plans.get(1).text()).isEqualTo("League");
            assertThat(plans.get(1).hangX()).isCloseTo(100f, within(1e-3f));
            assertThat(plans.get(1).hangY()).isCloseTo(200f - halfStep, within(1e-3f));
        }

        @Test
        void stacksAlongTheSlantedAxisPerpendicular() {
            // A 45-degree axis: the stack runs along its "up" perpendicular
            // (-sin45, cos45), so each line centre is offset half a step along it.
            var anchors = List.of(buildAcceptedAnchor(
                List.of("Persean", "League"),
                100f,
                200f,
                new Segment(0f, 100f, 200f, 300f)));

            var plans = LabelsBuilder.planLabels(anchors);

            var halfStep = FONT_HEIGHT * (float) LINE_SPACING / 2f;
            var component = halfStep * (float) (Math.sqrt(2.0) / 2.0);

            assertThat(plans.get(0).hangX()).isCloseTo(100f - component, within(1e-2f));
            assertThat(plans.get(0).hangY()).isCloseTo(200f + component, within(1e-2f));
            assertThat(plans.get(1).hangX()).isCloseTo(100f + component, within(1e-2f));
            assertThat(plans.get(1).hangY()).isCloseTo(200f - component, within(1e-2f));
        }

        @Test
        void takesTheSlantFromTheAcceptedAxis() {
            // A line rising 45 degrees to the right: the label leans at +45.
            var anchors = List.of(buildAcceptedAnchor(
                List.of("Persean League"),
                50f,
                50f,
                new Segment(0f, 0f, 100f, 100f)));

            var plan = LabelsBuilder.planLabels(anchors).get(0);

            assertThat(plan.slantDegrees()).isCloseTo(45f, within(1e-3f));
        }

        @Test
        void foldsALeftPointingAxisUprightSoTheNameIsNotUpsideDown() {
            // The accepted axis points into the left half-plane (end left of start). Left
            // as is it would render the name upside down (~180 degrees); folded upright it
            // reads left-to-right at the same shallow lean (here dead level, 0) - and the
            // stack still puts the first line on the upper side, from the folded direction.
            var anchors = List.of(buildAcceptedAnchor(
                List.of("Persean", "League"),
                100f,
                200f,
                new Segment(200f, 200f, 0f, 200f)));

            var plans = LabelsBuilder.planLabels(anchors);

            assertThat(plans.get(0).slantDegrees()).isCloseTo(0f, within(1e-3f));
            assertThat(plans.get(0).hangY()).isGreaterThan(plans.get(1).hangY());
        }

        @Test
        void fillsTheFittedBandExactly() {
            // The invariant the whole stack exists to hold: the outermost line centres, plus
            // half a line height at each end, span exactly the band the fit reserved. Checked
            // on three lines, where a wrong step compounds rather than cancelling.
            var anchors = List.of(buildAcceptedAnchor(
                List.of("Tri", "Tachyon", "Corporation"),
                100f,
                200f,
                new Segment(0f, 200f, 200f, 200f)));

            var plans = LabelsBuilder.planLabels(anchors);
            var spannedThickness = plans.get(0).hangY() - plans.get(2).hangY() + FONT_HEIGHT;

            assertThat(spannedThickness)
                .isCloseTo(anchors.get(0).thickness(), within(1e-2f));
        }

        @Test
        void takesTheStepFromTheBandNotTheSpacingSetting() {
            // The band is what the fit reserved, so a cluster fitted at a wider spacing stacks
            // wider - with no spacing read here to tell it so. Fitted at double the ordinary
            // spacing, the step doubles with it.
            var wideSpacing = LINE_SPACING * 2.0;
            var anchors = List.of(buildAcceptedAnchorFittedAt(
                wideSpacing,
                List.of("Persean", "League"),
                100f,
                200f,
                new Segment(0f, 200f, 200f, 200f)));

            var plans = LabelsBuilder.planLabels(anchors);
            var halfStep = FONT_HEIGHT * (float) wideSpacing / 2f;

            assertThat(plans.get(0).hangY()).isCloseTo(200f + halfStep, within(1e-2f));
            assertThat(plans.get(1).hangY()).isCloseTo(200f - halfStep, within(1e-2f));
        }

        @Test
        void skipsACollapsedPlacementWithNoAcceptedAxis() {
            // A cluster whose search collapsed to the dot carries no accepted line, so it
            // gets no name rather than an empty box.
            var anchors = List.of(buildCollapsedAnchor(100f, 200f));

            assertThat(LabelsBuilder.planLabels(anchors)).isEmpty();
        }

        @Test
        void skipsAClusterWithNoWrappedName() {
            // An accepted box whose fit ran on the aspect stand-in (font or faction name
            // unresolved) carries no lines, so no label is planned for it - the debug band
            // is that cluster's only footprint.
            var anchors = List.of(buildAcceptedAnchor(
                List.of(),
                100f,
                200f,
                new Segment(0f, 200f, 200f, 200f)));

            assertThat(LabelsBuilder.planLabels(anchors)).isEmpty();
        }
    }

    // The placements a fit left behind, fitted in the settled face. The mint reads nothing of the
    // record but the face, so the rest of it is whatever any fit would carry.
    private static StandingClusterAnchors buildStandingAnchors(ClusterAnchor... anchors) {

        var standingAnchors = new StandingClusterAnchors();

        standingAnchors.replaceAnchors(List.of(anchors), new AnchorFitFingerprint(null, 0, SETTLED_LABEL_FACE));

        return standingAnchors;
    }

    // A placement that accepted a label line, hung at (anchorX, anchorY) with the given
    // accepted axis and wrapped lines at the shared font height - the input labels are
    // built from, fitted at the ordinary line spacing.
    private static ClusterAnchor buildAcceptedAnchor(
            List<String> nameLines,
            float anchorX,
            float anchorY,
            Segment acceptedAxis) {

        return buildAcceptedAnchorFittedAt(LINE_SPACING, nameLines, anchorX, anchorY, acceptedAxis);
    }

    // The same placement with its band sized at a named spacing, mirroring the fit's own
    // thickness formula (one line height plus a step per gap). The spacing reaches the
    // planner only through this thickness - nothing hands it the multiple - so a test that
    // varies it here is varying exactly what the fit would have written into the band.
    private static ClusterAnchor buildAcceptedAnchorFittedAt(
            double lineSpacing,
            List<String> nameLines,
            float anchorX,
            float anchorY,
            Segment acceptedAxis) {

        var lineCount = Math.max(nameLines.size(), 1);
        return new ClusterAnchor(
            CLUSTER_IDENTITY,
            anchorX,
            anchorY,
            OWNER_COLOUR,
            nameLines,
            FONT_HEIGHT,
            acceptedAxis,
            null,
            null,
            (float) (FONT_HEIGHT * ((lineCount - 1) * lineSpacing + 1)),
            lineCount);
    }

    // A collapsed placement: only the dot, no accepted line, so no name is drawn.
    private static ClusterAnchor buildCollapsedAnchor(float anchorX, float anchorY) {
        return new ClusterAnchor(
            CLUSTER_IDENTITY,
            anchorX, anchorY,
            OWNER_COLOUR,
            List.of(),
            0f,
            null,
            null,
            null,
            0f,
            0);
    }
}
