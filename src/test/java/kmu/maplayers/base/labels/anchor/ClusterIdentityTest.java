package kmu.maplayers.base.labels.anchor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what makes two placements the same cluster. Everything an identity is for rests on
 * its equality, so the cases here are the four ways that answer can be wrong: reading a
 * re-ordered walk as a different cluster, reading a split or a merge as the same one,
 * ignoring the owner, and letting the collection an identity was built from change it
 * afterwards.
 */
final class ClusterIdentityTest {

    // The owner two identities share where the case is about membership alone.
    private static final String OWNER = "persean_league";

    @Nested
    class Constructor {

        @Test
        void constructorIgnoresTheOrderTheMembersArrivedIn() {
            // Which order a cluster's members were walked in is an accident of the
            // traversal, so an identity built from either order names the same cluster.
            var walkedOneWay = new ClusterIdentity(
                OWNER,
                Set.copyOf(buildCellKeys("alpha", "beta")));

            var walkedTheOther = new ClusterIdentity(
                OWNER,
                Set.copyOf(buildCellKeys("beta", "alpha")));

            assertThat(walkedOneWay)
                .isEqualTo(walkedTheOther);
            assertThat(walkedOneWay)
                .hasSameHashCodeAs(walkedTheOther);
        }

        @Test
        void constructorCopiesTheMembersSoALaterChangeCannotMoveTheIdentity() {
            // An identity is used as a map key: a member set that shifted underneath would
            // quietly lose whatever was filed under it rather than fail, so the members are
            // copied out of the caller's collection.
            var members = new HashSet<>(buildCellKeys("alpha", "beta"));
            var identity = new ClusterIdentity(OWNER, members);

            members.add(buildCellKey("gamma"));

            assertThat(identity.memberSystemKeys())
                .containsExactlyInAnyOrderElementsOf(buildCellKeys("alpha", "beta"));
        }
    }

    @Nested
    class Equality {

        @Test
        void equalitySeparatesAClusterFromOneItSplitInto() {
            // A split leaves member sets that match nothing, which is what makes the two
            // halves re-fit without anyone having to spot the split.
            var whole = new ClusterIdentity(
                OWNER,
                Set.of(buildCellKey("alpha"), buildCellKey("beta")));
            var half = new ClusterIdentity(OWNER, Set.of(buildCellKey("alpha")));

            assertThat(whole)
                .isNotEqualTo(half);
        }

        @Test
        void equalitySeparatesAClusterFromOneItMergedInto() {
            // The mirror case: a merged cluster has gained a member, so it matches neither
            // of the clusters it swallowed.
            var beforeMerge = new ClusterIdentity(
                OWNER,
                Set.of(buildCellKey("alpha"), buildCellKey("beta")));

            var afterMerge = new ClusterIdentity(
                OWNER,
                Set.of(buildCellKey("alpha"), buildCellKey("beta"), buildCellKey("gamma")));

            assertThat(beforeMerge)
                .isNotEqualTo(afterMerge);
        }

        @Test
        void equalitySeparatesTheSameSystemsUnderDifferentOwners() {
            // The same systems held by someone else is a different cluster - the name and
            // the shade a placement was fitted for both come off the owner.
            var heldByOne = new ClusterIdentity(
                OWNER,
                Set.of(buildCellKey("alpha"), buildCellKey("beta")));

            var heldByAnother = new ClusterIdentity(
                "hegemony",
                Set.of(buildCellKey("alpha"), buildCellKey("beta")));

            assertThat(heldByOne)
                .isNotEqualTo(heldByAnother);
        }
    }
}
