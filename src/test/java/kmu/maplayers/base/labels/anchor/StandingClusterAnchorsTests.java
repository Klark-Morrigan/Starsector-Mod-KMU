package kmu.maplayers.base.labels.anchor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the one thing this holder exists to guarantee: the placements and the rules recorded
 * for them move together and cannot be moved apart. Everything the carry-over rests on reads
 * the two as describing each other, so a list that outran its label - or a label that outran
 * its list - offers a later rebuild placements it has no business reusing.
 *
 * <p>The tuning inside a fingerprint is opaque here; the cases only need two readings that are
 * not equal, so the specification rides along as null and the revision separates them.
 */
final class StandingClusterAnchorsTests {

    // Two readings of what a pass ran under, differing only in the revision, so a case can say
    // which of them a pair ends up labelled with.
    private static final AnchorFitFingerprint EARLIER_FIT = fitAtRevision(1);
    private static final AnchorFitFingerprint LATER_FIT = fitAtRevision(2);

    @Nested
    class GetAnchors {

        @Test
        void startsEmptyWithNothingRecordedAgainstIt() {
            // The reading a session starts at and a discard returns to: no placements, and no
            // claim about what any were fitted under. It is what makes a first rebuild total,
            // since nothing can match a fingerprint that is not there.
            var standingAnchors = new StandingClusterAnchors();

            assertThat(standingAnchors.getAnchors())
                .isEmpty();
            assertThat(standingAnchors.getFitFingerprint())
                .isNull();
        }

        @Test
        void refusesEditsThroughTheListItHandsBack() {
            // The pair moves through replaceAnchors alone. A caller that could append to the
            // list it was handed would leave a placement standing under a label that never
            // described it - the one state the whole carry-over assumes cannot happen.
            var standingAnchors = new StandingClusterAnchors();

            assertThatThrownBy(() -> standingAnchors.getAnchors().add(buildAnchorFor("hegemony")))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        // What lets work packed from one pass, such as the anchor overlay's runs, tell by identity
        // whether its pass still stands. A pass with the same placements as the last is still a
        // new pass, since what it was fitted under may have moved.
        @Test
        void handsBackADifferentListAfterEveryPass() {

            // ARRANGE
            var standingAnchors = new StandingClusterAnchors();

            standingAnchors.replaceAnchors(List.of(buildAnchorFor("hegemony")), EARLIER_FIT);

            var earlierAnchors = standingAnchors.getAnchors();

            standingAnchors.replaceAnchors(List.of(buildAnchorFor("hegemony")), LATER_FIT);

            // ACT
            var actualAnchors = standingAnchors.getAnchors();

            // ASSERT
            assertThat(actualAnchors)
                .isNotSameAs(earlierAnchors);
        }
    }

    @Nested
    class ComputeLineBoxesIfAbsent {

        // Every bake asks, and the bakes between two passes keep clear of the same names.
        @Test
        void measuresOncePerPass() {

            // ARRANGE
            var standingAnchors = new StandingClusterAnchors();
            var measurementCount = new AtomicInteger();

            standingAnchors.replaceAnchors(List.of(buildAnchorFor("hegemony")), EARLIER_FIT);

            // ACT
            standingAnchors.computeLineBoxesIfAbsent(() -> measureCounted(measurementCount));
            standingAnchors.computeLineBoxesIfAbsent(() -> measureCounted(measurementCount));

            // ASSERT
            assertThat(measurementCount)
                .hasValue(1);
        }

        // The boxes were measured off the placements a pass replaced, so they go with them.
        @Test
        void measuresAgainAfterAPassReplacesThePlacements() {

            // ARRANGE
            var standingAnchors = new StandingClusterAnchors();
            var measurementCount = new AtomicInteger();

            standingAnchors.replaceAnchors(List.of(buildAnchorFor("hegemony")), EARLIER_FIT);
            standingAnchors.computeLineBoxesIfAbsent(() -> measureCounted(measurementCount));
            standingAnchors.replaceAnchors(List.of(buildAnchorFor("tritachyon")), LATER_FIT);

            // ACT
            standingAnchors.computeLineBoxesIfAbsent(() -> measureCounted(measurementCount));

            // ASSERT
            assertThat(measurementCount)
                .hasValue(2);
        }

        @Test
        void measuresAgainAfterADiscard() {

            // ARRANGE
            var standingAnchors = new StandingClusterAnchors();
            var measurementCount = new AtomicInteger();

            standingAnchors.replaceAnchors(List.of(buildAnchorFor("hegemony")), EARLIER_FIT);
            standingAnchors.computeLineBoxesIfAbsent(() -> measureCounted(measurementCount));
            standingAnchors.discardAnchors();

            // ACT
            standingAnchors.computeLineBoxesIfAbsent(() -> measureCounted(measurementCount));

            // ASSERT
            assertThat(measurementCount)
                .hasValue(2);
        }
    }

    @Nested
    class ReplaceAnchors {

        @Test
        void takesThePlacementsAndTheirRulesTogether() {

            var standingAnchors = new StandingClusterAnchors();

            standingAnchors.replaceAnchors(List.of(buildAnchorFor("hegemony")), EARLIER_FIT);

            assertThat(standingAnchors.getAnchors())
                .hasSize(1);
            assertThat(standingAnchors.getFitFingerprint())
                .isEqualTo(EARLIER_FIT);
        }

        @Test
        void dropsThePreviousPassRatherThanAddingToIt() {
            // A pass states the whole of what stands, so the placements it did not produce are
            // gone: a rebuild that fitted one cluster where two stood must not leave the second
            // one's box on the map under this pass's label.
            var standingAnchors = new StandingClusterAnchors();

            standingAnchors.replaceAnchors(List.of(buildAnchorFor("hegemony")), EARLIER_FIT);
            standingAnchors.replaceAnchors(List.of(buildAnchorFor("tritachyon")), LATER_FIT);

            assertThat(standingAnchors.getAnchors())
                .hasSize(1);
            assertThat(standingAnchors.getFitFingerprint())
                .isEqualTo(LATER_FIT);
        }

        @Test
        void copiesThePassesListSoALaterChangeCannotMoveWhatStands() {
            // The list a fit hands over is the fit's own working collection. Holding it by
            // reference would let whatever produced it keep editing what the map draws, and
            // under a label that stopped describing it at the moment of the edit.
            var standingAnchors = new StandingClusterAnchors();
            var fittedAnchors = new ArrayList<ClusterAnchor>();

            fittedAnchors.add(buildAnchorFor("hegemony"));
            standingAnchors.replaceAnchors(fittedAnchors, EARLIER_FIT);
            fittedAnchors.add(buildAnchorFor("tritachyon"));

            assertThat(standingAnchors.getAnchors())
                .hasSize(1);
        }
    }

    @Nested
    class DiscardAnchors {

        @Test
        void dropsThePlacementsAndTheirRulesTogether() {
            // Called when the sector behind the placements is gone. Leaving the rules standing
            // would let the next sector's first rebuild match this one's fingerprint and carry
            // over placements fitted inside a partition that no longer exists.
            var standingAnchors = new StandingClusterAnchors();

            standingAnchors.replaceAnchors(List.of(buildAnchorFor("hegemony")), EARLIER_FIT);
            standingAnchors.discardAnchors();

            assertThat(standingAnchors.getAnchors())
                .isEmpty();
            assertThat(standingAnchors.getFitFingerprint())
                .isNull();
        }
    }

    // One placement for the given owner. Nothing here reads a placement's geometry, so every
    // fitted component is inert and only the identity distinguishes one from another.
    private static ClusterAnchor buildAnchorFor(String ownerKey) {

        return new ClusterAnchor(
            new ClusterIdentity(ownerKey, Set.of(buildCellKey(ownerKey))),
            0f,
            0f,
            Color.WHITE,
            List.of(),
            0f,
            null,
            null,
            null,
            0f,
            0);
    }

    // A measurement that counts how often it ran and measures no room at all.
    private static List<List<double[]>> measureCounted(AtomicInteger measurementCount) {

        measurementCount.incrementAndGet();
        return List.of();
    }

    // A fingerprint separated from its neighbours by the geometry revision alone: neither the tuning
    // nor the face is read here, and nulls keep the fixture from restating the whole search surface.
    private static AnchorFitFingerprint fitAtRevision(int geometryRevision) {
        return new AnchorFitFingerprint(null, geometryRevision, null);
    }
}
