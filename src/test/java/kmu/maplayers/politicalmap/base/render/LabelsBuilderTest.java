package kmu.maplayers.politicalmap.base.render;

import kmlib.math.geometry.Segment;

import kmu.maplayers.politicalmap.base.render.model.ClusterAnchor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the pure placement-to-label decision - {@link LabelsBuilder#planLabels} - which
 * turns the resolved cluster placements into the text, colour, hang point, slant, and font
 * size each label line draws with, before any GL string is minted: one plan per wrapped
 * line, the lines stacked along the accepted axis's perpendicular and centred as a block on
 * the anchor. The string minting, font load, and rendering only resolve in-engine, so they
 * are not covered here; the geometry that drives them is.
 */
final class LabelsBuilderTest {

    private static final Color OWNER_COLOR = Color.RED;

    // The stack geometry the multi-line tests compute by hand.
    private static final float FONT_HEIGHT = 100f;
    private static final double LINE_SPACING = 1.15;
    // The geometry tests exercise the placement, not the fade, so they plan at full
    // opacity where scaleAlpha is the identity and the owner colour survives unchanged.
    private static final double FULL_OPACITY = 1.0;

    @Nested
    class PlanLabels {

        @Test
        void planLabelsPlansOneLineWithItsTextColorAndFontHeight() {
            var anchors = List.of(acceptedAnchor(List.of("Persean League"), 100f, 200f,
                    new Segment(0f, 200f, 200f, 200f)));

            var plans = LabelsBuilder.planLabels(anchors, LINE_SPACING, FULL_OPACITY);

            assertThat(plans).singleElement().satisfies(plan -> {
                assertThat(plan.text()).isEqualTo("Persean League");
                assertThat(plan.color()).isEqualTo(OWNER_COLOR);
                assertThat(plan.fontHeight()).isEqualTo(FONT_HEIGHT);
            });
        }

        @Test
        void planLabelsHangsASingleLineAtTheAnchorPoint() {
            // One line has no stack to spread: its centre is the block centre, the anchor.
            var anchors = List.of(acceptedAnchor(List.of("Persean League"), 100f, 200f,
                    new Segment(0f, 200f, 200f, 200f)));

            var plan = LabelsBuilder.planLabels(anchors, LINE_SPACING, FULL_OPACITY).get(0);

            assertThat(plan.hangX()).isEqualTo(100f);
            assertThat(plan.hangY()).isEqualTo(200f);
        }

        @Test
        void planLabelsStacksTwoLinesAcrossAHorizontalAxisFirstLineOnTop() {
            // A horizontal axis stacks straight up the y axis: line centres half a step
            // (font height times spacing) above and below the anchor, first line on the
            // upper side so the block reads top-down.
            var anchors = List.of(acceptedAnchor(List.of("Persean", "League"), 100f, 200f,
                    new Segment(0f, 200f, 200f, 200f)));

            var plans = LabelsBuilder.planLabels(anchors, LINE_SPACING, FULL_OPACITY);

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
        void planLabelsStacksAlongTheSlantedAxisPerpendicular() {
            // A 45-degree axis: the stack runs along its "up" perpendicular
            // (-sin45, cos45), so each line centre is offset half a step along it.
            var anchors = List.of(acceptedAnchor(List.of("Persean", "League"), 100f, 200f,
                    new Segment(0f, 100f, 200f, 300f)));

            var plans = LabelsBuilder.planLabels(anchors, LINE_SPACING, FULL_OPACITY);

            var halfStep = FONT_HEIGHT * (float) LINE_SPACING / 2f;
            var component = halfStep * (float) (Math.sqrt(2.0) / 2.0);
            assertThat(plans.get(0).hangX()).isCloseTo(100f - component, within(1e-2f));
            assertThat(plans.get(0).hangY()).isCloseTo(200f + component, within(1e-2f));
            assertThat(plans.get(1).hangX()).isCloseTo(100f + component, within(1e-2f));
            assertThat(plans.get(1).hangY()).isCloseTo(200f - component, within(1e-2f));
        }

        @Test
        void planLabelsTakesTheSlantFromTheAcceptedAxis() {
            // A line rising 45 degrees to the right: the label leans at +45.
            var anchors = List.of(acceptedAnchor(List.of("Persean League"), 50f, 50f,
                    new Segment(0f, 0f, 100f, 100f)));

            var plan = LabelsBuilder.planLabels(anchors, LINE_SPACING, FULL_OPACITY).get(0);

            assertThat(plan.slantDegrees()).isCloseTo(45f, within(1e-3f));
        }

        @Test
        void planLabelsFoldsALeftPointingAxisUprightSoTheNameIsNotUpsideDown() {
            // The accepted axis points into the left half-plane (end left of start). Left
            // as is it would render the name upside down (~180 degrees); folded upright it
            // reads left-to-right at the same shallow lean (here dead level, 0) - and the
            // stack still puts the first line on the upper side, from the folded direction.
            var anchors = List.of(acceptedAnchor(List.of("Persean", "League"), 100f, 200f,
                    new Segment(200f, 200f, 0f, 200f)));

            var plans = LabelsBuilder.planLabels(anchors, LINE_SPACING, FULL_OPACITY);

            assertThat(plans.get(0).slantDegrees()).isCloseTo(0f, within(1e-3f));
            assertThat(plans.get(0).hangY()).isGreaterThan(plans.get(1).hangY());
        }

        @Test
        void planLabelsSkipsACollapsedPlacementWithNoAcceptedAxis() {
            // A cluster whose search collapsed to the dot carries no accepted line, so it
            // gets no name rather than an empty box.
            var anchors = List.of(collapsedAnchor(100f, 200f));

            assertThat(LabelsBuilder.planLabels(anchors, LINE_SPACING, FULL_OPACITY)).isEmpty();
        }

        @Test
        void planLabelsSkipsAClusterWithNoWrappedName() {
            // An accepted box whose fit ran on the aspect stand-in (font or faction name
            // unresolved) carries no lines, so no label is planned for it - the debug band
            // is that cluster's only footprint.
            var anchors = List.of(acceptedAnchor(List.of(), 100f, 200f,
                    new Segment(0f, 200f, 200f, 200f)));

            assertThat(LabelsBuilder.planLabels(anchors, LINE_SPACING, FULL_OPACITY)).isEmpty();
        }

        @Test
        void planLabelsFadesEachLineColorByTheNameOpacity() {
            // The global name opacity scales the owner colour's alpha into the baked plan
            // colour, leaving its RGB alone, so every name recedes uniformly.
            var anchors = List.of(acceptedAnchor(List.of("Persean League"), 100f, 200f,
                    new Segment(0f, 200f, 200f, 200f)));

            var plan = LabelsBuilder.planLabels(anchors, LINE_SPACING, 0.5).get(0);

            assertThat(plan.color().getAlpha())
                    .isEqualTo(Math.round(OWNER_COLOR.getAlpha() * 0.5f));
            assertThat(plan.color().getRed()).isEqualTo(OWNER_COLOR.getRed());
            assertThat(plan.color().getGreen()).isEqualTo(OWNER_COLOR.getGreen());
            assertThat(plan.color().getBlue()).isEqualTo(OWNER_COLOR.getBlue());
        }

        @Test
        void planLabelsStillPlansAtZeroOpacitySoTheRendererCulls() {
            // Zero opacity bakes a fully transparent colour but still plans the line; the
            // renderer culls an all-transparent frame rather than the builder dropping it.
            var anchors = List.of(acceptedAnchor(List.of("Persean League"), 100f, 200f,
                    new Segment(0f, 200f, 200f, 200f)));

            var plans = LabelsBuilder.planLabels(anchors, LINE_SPACING, 0.0);

            assertThat(plans).singleElement()
                    .satisfies(plan -> assertThat(plan.color().getAlpha()).isZero());
        }
    }

    // A placement that accepted a label line, hung at (anchorX, anchorY) with the given
    // accepted axis and wrapped lines at the shared font height - the input labels are
    // built from.
    private static ClusterAnchor acceptedAnchor(List<String> nameLines, float anchorX,
            float anchorY, Segment acceptedAxis) {
        var lineCount = Math.max(nameLines.size(), 1);
        return new ClusterAnchor(anchorX, anchorY, OWNER_COLOR, nameLines, FONT_HEIGHT,
                acceptedAxis, null, null,
                (float) (FONT_HEIGHT * ((lineCount - 1) * LINE_SPACING + 1)), lineCount);
    }

    // A collapsed placement: only the dot, no accepted line, so no name is drawn.
    private static ClusterAnchor collapsedAnchor(float anchorX, float anchorY) {
        return new ClusterAnchor(anchorX, anchorY, OWNER_COLOR, List.of(), 0f, null, null,
                null, 0f, 0);
    }
}
