package kmu.maplayers.base.labels.anchor;

import kmlib.starsector.ui.label.BandFitSpecification;
import kmlib.starsector.ui.label.NameFitSpecification;

import kmu.maplayers.base.labels.anchor.specifications.AnchorDiagnostics;
import kmu.maplayers.base.labels.anchor.specifications.AnchorSearch;
import kmu.maplayers.base.labels.anchor.specifications.LabelAnchorSpecification;
import kmu.maplayers.base.labels.anchor.specifications.LeanScoring;
import kmu.maplayers.base.render.clusters.ClusterBorderTrace;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what makes two passes of placements comparable. Everything a fingerprint is for rests
 * on its equality: a pass whose rules moved must not compare equal to one made before the
 * move, or placements sized under the old rules would read as still current.
 *
 * <p>The tuning is a tree of records assembled from three packages, so a knob is varied at
 * each depth rather than once. A component that stopped being a value type - swapped for a
 * class, or gaining an identity-based equality - would leave every knob under it invisible
 * here while the fingerprint still looked like it was doing its job, which is the failure
 * these cases exist to catch.
 */
final class AnchorFitFingerprintTest {

    // The revision two fits share where the case is about tuning alone.
    private static final int GEOMETRY_REVISION = 7;

    // The knobs held fixed while one of them is varied per case. Their values carry no
    // meaning here beyond being the same on both sides of a comparison.
    private static final double WELD_TOLERANCE = 1.0;
    private static final double MITER_LIMIT = 4.0;
    private static final int DIRECTION_COUNT = 8;
    private static final int OFFSET_COUNT = 14;
    private static final double ICON_CLEARANCE = 250.0;
    private static final double END_INSET = 30.0;
    private static final double FONT_HEIGHT_TOLERANCE = 1.0;
    private static final double VERTICAL_PENALTY_STRENGTH = 0.5;
    private static final double VERTICAL_PENALTY_EXPONENT = 2.0;
    private static final double MAX_SLANT_DEGREES = 30.0;
    private static final double MIN_FONT_HEIGHT = 200.0;
    private static final double MAX_FONT_HEIGHT = 1200.0;
    private static final int MAX_LINES = 3;
    private static final double LINE_SPACING = 1.1;

    private static final ClusterBorderTrace BORDER_TRACE =
        new ClusterBorderTrace(WELD_TOLERANCE, MITER_LIMIT);

    @Nested
    class Equality {

        @Test
        void equalityHoldsForTwoPassesMadeUnderTheSameRulesAndGeometry() {
            // The case the whole mechanism turns on: nothing moved between two rebuilds, so
            // the earlier pass's placements are still describable by the later pass's rules.
            var fittedEarlier = new AnchorFitFingerprint(
                buildBaselineSpecification(),
                GEOMETRY_REVISION);
            var fittedLater = new AnchorFitFingerprint(
                buildBaselineSpecification(),
                GEOMETRY_REVISION);

            assertThat(fittedEarlier)
                .isEqualTo(fittedLater);
            assertThat(fittedEarlier)
                .hasSameHashCodeAs(fittedLater);
        }

        @Test
        void equalitySeparatesPassesMadeUnderDifferentSearchKnobs() {
            // A sweep knob re-aims every candidate line, so no placement made under the old
            // width describes where the search would put it now.
            var fittedAtFourteenOffsets = new AnchorFitFingerprint(
                buildBaselineSpecification(),
                GEOMETRY_REVISION);
            var fittedAtOneOffset = new AnchorFitFingerprint(
                buildSpecificationWith(BORDER_TRACE, 1, FONT_HEIGHT_TOLERANCE),
                GEOMETRY_REVISION);

            assertThat(fittedAtFourteenOffsets)
                .isNotEqualTo(fittedAtOneOffset);
        }

        @Test
        void equalitySeparatesPassesMadeUnderDifferentBandFitKnobs() {
            // The band-fit half of the tuning is the library's own record, reached through
            // this one - a knob the search does not own still changes what it accepted.
            var fittedAtOneUnit = new AnchorFitFingerprint(
                buildBaselineSpecification(),
                GEOMETRY_REVISION);
            var fittedAtSixtyFourUnits = new AnchorFitFingerprint(
                buildSpecificationWith(BORDER_TRACE, OFFSET_COUNT, 64.0),
                GEOMETRY_REVISION);

            assertThat(fittedAtOneUnit)
                .isNotEqualTo(fittedAtSixtyFourUnits);
        }

        @Test
        void equalitySeparatesPassesMadeUnderADifferentBorderTrace() {
            // The deepest knob in the tree, and the one a placement is clipped against: a
            // looser weld traces different rings, so the boxes were sized inside a different
            // outline even where every other knob held.
            var fittedAgainstTightRings = new AnchorFitFingerprint(
                buildBaselineSpecification(),
                GEOMETRY_REVISION);
            var fittedAgainstLooseRings = new AnchorFitFingerprint(
                buildSpecificationWith(
                    new ClusterBorderTrace(100.0, MITER_LIMIT),
                    OFFSET_COUNT,
                    FONT_HEIGHT_TOLERANCE),
                GEOMETRY_REVISION);

            assertThat(fittedAgainstTightRings)
                .isNotEqualTo(fittedAgainstLooseRings);
        }

        @Test
        void equalitySeparatesPassesMadeAgainstDifferentGeometry() {
            // Recut cells move the borders the boxes were clipped inside and the keep-out
            // sites they were trimmed clear of, neither of which any tuning knob mentions.
            var fittedBeforeTheRecut = new AnchorFitFingerprint(
                buildBaselineSpecification(),
                GEOMETRY_REVISION);
            var fittedAfterTheRecut = new AnchorFitFingerprint(
                buildBaselineSpecification(),
                GEOMETRY_REVISION + 1);

            assertThat(fittedBeforeTheRecut)
                .isNotEqualTo(fittedAfterTheRecut);
        }
    }

    // The tuning a case compares against, with every knob at its held value.
    private static LabelAnchorSpecification buildBaselineSpecification() {
        return buildSpecificationWith(BORDER_TRACE, OFFSET_COUNT, FONT_HEIGHT_TOLERANCE);
    }

    // The same tuning with the three knobs a case varies opened up - one per depth of the
    // tree, so which nesting level a comparison failed at is named by the case that failed.
    private static LabelAnchorSpecification buildSpecificationWith(
            ClusterBorderTrace borderTrace,
            int offsetCount,
            double fontHeightTolerance) {

        return new LabelAnchorSpecification(
            new AnchorSearch(borderTrace, DIRECTION_COUNT, offsetCount),
            new LeanScoring(
                VERTICAL_PENALTY_STRENGTH,
                VERTICAL_PENALTY_EXPONENT,
                MAX_SLANT_DEGREES),
            new AnchorDiagnostics(false, false),
            new BandFitSpecification(ICON_CLEARANCE, END_INSET, fontHeightTolerance),
            new NameFitSpecification(
                MIN_FONT_HEIGHT,
                MAX_FONT_HEIGHT,
                MAX_LINES,
                LINE_SPACING));
    }
}
