package kmu.maplayers.ownermap.owners;

import kmlib.starsector.systems.SystemKey;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Pins the two adapters from an owner map onto the geometry: each carries every system's owner ID in
 * the order handed over, and a cell is owned through the system it draws as.
 */
final class SystemOwnerTests {

    // Two systems and the cell a void pocket beside the first draws as, keyed as the geometry keys
    // them.
    private static final SystemKey CORVUS_KEY = new SystemKey("corvus", null, "corvus_anchor");
    private static final SystemKey ASKONIA_KEY = new SystemKey("askonia", null, "askonia_anchor");
    private static final SystemKey POCKET_CELL_KEY = new SystemKey("pocket", null, "pocket_anchor");

    @Nested
    class MapOwnerIdBySystemKey {

        @Test
        void keepsEachSystemsOwnerIdInTheOrderHandedOver() {
            // The geometry fuses on these IDs and walks them in order, so the order is part of the
            // answer rather than an accident of the map type.
            assertThat(SystemOwner.mapOwnerIdBySystemKey(buildOwnerBySystemKey()))
                .containsExactly(
                    entry(ASKONIA_KEY, "sindrian_diktat"),
                    entry(CORVUS_KEY, "hegemony"));
        }

        @Test
        void answersNothingForNoOwnedSystem() {

            assertThat(SystemOwner.mapOwnerIdBySystemKey(Map.of()))
                .isEmpty();
        }
    }

    @Nested
    class MapCellGrouping {

        @Test
        void ownsACellByTheSystemItDrawsAs() {
            // A pocket cell has no star of its own; it is owned through the system it draws as, so
            // it clusters with that system's owner.
            var cellGrouping = SystemOwner.mapCellGrouping(
                Map.of(POCKET_CELL_KEY, CORVUS_KEY, ASKONIA_KEY, ASKONIA_KEY),
                buildOwnerBySystemKey());

            assertThat(cellGrouping.resolveOwnerOf(POCKET_CELL_KEY))
                .isEqualTo("hegemony");
            assertThat(cellGrouping.resolveOwnerOf(ASKONIA_KEY))
                .isEqualTo("sindrian_diktat");
        }

        @Test
        void leavesACellUnownedWhenItsSystemHasNoOwner() {

            var cellGrouping = SystemOwner.mapCellGrouping(
                Map.of(POCKET_CELL_KEY, CORVUS_KEY),
                Map.of());

            assertThat(cellGrouping.resolveOwnerOf(POCKET_CELL_KEY))
                .isNull();
        }
    }

    // Two owned systems in a set order - Askonia first - so an adapter reordering them shows.
    private static Map<SystemKey, SystemOwner> buildOwnerBySystemKey() {

        var ownerBySystemKey = new LinkedHashMap<SystemKey, SystemOwner>();

        ownerBySystemKey.put(
            ASKONIA_KEY,
            new SystemOwner("sindrian_diktat", new OwnerPalette(Color.GREEN, Color.BLACK)));
        ownerBySystemKey.put(
            CORVUS_KEY,
            new SystemOwner("hegemony", new OwnerPalette(Color.RED, Color.BLUE)));

        return ownerBySystemKey;
    }
}
