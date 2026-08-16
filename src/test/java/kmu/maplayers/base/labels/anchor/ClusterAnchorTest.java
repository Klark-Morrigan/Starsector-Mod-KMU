package kmu.maplayers.base.labels.anchor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the filing every cross-pass question about a placement is asked through.
 *
 * <p>A fit reports its placements in its own partition's order, so two passes' lists cannot be
 * walked side by side and the cluster each one names is the only thing that survives between them.
 * More than one reader asks that question - whether a placement may be carried over, and whether it
 * moved - so the filing is stated once and both read the same answer.
 */
final class ClusterAnchorTest {

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    @Nested
    class MapAnchorsByIdentity {

        @Test
        void mapAnchorsByIdentityFilesEachPlacementUnderTheClusterItNames() {

            var hegemonyAnchor = buildAnchorOf(HEGEMONY);
            var tritachyonAnchor = buildAnchorOf(TRITACHYON);

            assertThat(ClusterAnchor.mapAnchorsByIdentity(
                    List.of(hegemonyAnchor, tritachyonAnchor)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                    hegemonyAnchor.identity(),
                    hegemonyAnchor,
                    tritachyonAnchor.identity(),
                    tritachyonAnchor));
        }

        @Test
        void mapAnchorsByIdentityFilesNothingForAPassThatFittedNoPlacement() {
            // The session's first rebuild, and every one taken while the names and the overlay are
            // both off: nothing stands to be looked up, which is a filing rather than a refusal.
            assertThat(ClusterAnchor.mapAnchorsByIdentity(List.of()))
                .isEmpty();
        }
    }

    // One placement of one cluster. Only its identity is read here, so every fitted component is
    // inert.
    private static ClusterAnchor buildAnchorOf(String ownerKey) {

        return new ClusterAnchor(
            new ClusterIdentity(ownerKey, Set.of(ownerKey)),
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
}
