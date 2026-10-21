package kmu.maplayers.ownermap.picker;

import kmlib.starsector.systems.SystemKey;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the index answers on its own terms, apart from the walk that fills it: the lookup a
 * render pass takes per hover, and the seal that stops a sealed index moving under it.
 */
final class OwnerPresenceIndexTests {

    @Nested
    class ReadPresentSystemKeys {

        @Test
        void answersTheSystemsAnOwnerLivesIn() {
            // The lookup the hover takes: one owner's own systems, in the order they were recorded.
            var index = new OwnerPresenceIndex(Map.of(
                "hegemony", new LinkedHashSet<>(Set.of(buildCellKey("system-a")))));

            assertThat(index.readPresentSystemKeys("hegemony"))
                .containsExactly(buildCellKey("system-a"));
        }

        @Test
        void answersEmptyForAnOwnerTheWalkNeverSurfaced() {
            // An owner living nowhere is absent from the index rather than present with nothing, and
            // the lookup answers for it without the caller testing membership first - which is what
            // lets a render pass ask about whichever owner the pointer is on.
            assertThat(OwnerPresenceIndex.EMPTY.readPresentSystemKeys("hegemony"))
                .isEmpty();
        }

        @Test
        void answersEmptyForNoOwnerAtAll() {
            // The pointer resting on no row at all reaches the lookup as a null ID, so it answers
            // the same nothing rather than throwing at the top of a render pass.
            assertThat(OwnerPresenceIndex.EMPTY.readPresentSystemKeys(null))
                .isEmpty();
        }
    }

    @Nested
    class SystemKeysByOwnerId {

        @Test
        void keepsTheOrderItWasBuiltIn() {
            // Walk order at both levels, so two reads of one sector answer alike and a lit set is
            // assembled in a stable order.
            var systemKeysByOwnerId = new LinkedHashMap<String, Set<SystemKey>>();

            systemKeysByOwnerId.put("tritachyon", new LinkedHashSet<>(buildCellKeys("system-b")));
            systemKeysByOwnerId.put("hegemony", new LinkedHashSet<>(buildCellKeys("system-a")));

            assertThat(new OwnerPresenceIndex(systemKeysByOwnerId).systemKeysByOwnerId().keySet())
                .containsExactly("tritachyon", "hegemony");
        }

        @Test
        void ignoresLaterWritesToTheMapItWasBuiltFrom() {
            // The walk hands over the map it accumulated into and goes on holding it, so an index
            // that did not copy would keep changing after the rebuild that sealed it.
            var systemKeysByOwnerId = new LinkedHashMap<String, Set<SystemKey>>();
            var hegemonySystemKeys = new LinkedHashSet<SystemKey>();

            hegemonySystemKeys.add(buildCellKey("system-a"));
            systemKeysByOwnerId.put("hegemony", hegemonySystemKeys);

            var index = new OwnerPresenceIndex(systemKeysByOwnerId);

            hegemonySystemKeys.add(buildCellKey("system-b"));
            systemKeysByOwnerId.put("tritachyon", new LinkedHashSet<>(buildCellKeys("system-c")));

            assertThat(index.systemKeysByOwnerId())
                .containsExactly(Map.entry("hegemony", Set.of(buildCellKey("system-a"))));
        }
    }
}
