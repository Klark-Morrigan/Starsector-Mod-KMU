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
final class BlocPresenceIndexTest {

    @Nested
    class ReadPresentSystemKeys {

        @Test
        void readPresentSystemKeysAnswersTheSystemsABlocLivesIn() {
            // The lookup the hover takes: one bloc's own systems, in the order they were recorded.
            var index = new BlocPresenceIndex(Map.of(
                "hegemony", new LinkedHashSet<>(Set.of(buildCellKey("system-a")))));

            assertThat(index.readPresentSystemKeys("hegemony"))
                .containsExactly(buildCellKey("system-a"));
        }

        @Test
        void readPresentSystemKeysAnswersEmptyForABlocTheWalkNeverSurfaced() {
            // A bloc living nowhere is absent from the index rather than present with nothing, and
            // the lookup answers for it without the caller testing membership first - which is what
            // lets a render pass ask about whichever bloc the pointer is on.
            assertThat(BlocPresenceIndex.EMPTY.readPresentSystemKeys("hegemony"))
                .isEmpty();
        }

        @Test
        void readPresentSystemKeysAnswersEmptyForNoBlocAtAll() {
            // The pointer resting on no row at all reaches the lookup as a null ID, so it answers
            // the same nothing rather than throwing at the top of a render pass.
            assertThat(BlocPresenceIndex.EMPTY.readPresentSystemKeys(null))
                .isEmpty();
        }
    }

    @Nested
    class SystemKeysByBlocId {

        @Test
        void systemKeysByBlocIdKeepsTheOrderItWasBuiltIn() {
            // Walk order at both levels, so two reads of one sector answer alike and a lit set is
            // assembled in a stable order.
            var systemKeysByBlocId = new LinkedHashMap<String, Set<SystemKey>>();

            systemKeysByBlocId.put("tritachyon", new LinkedHashSet<>(buildCellKeys("system-b")));
            systemKeysByBlocId.put("hegemony", new LinkedHashSet<>(buildCellKeys("system-a")));

            assertThat(new BlocPresenceIndex(systemKeysByBlocId).systemKeysByBlocId().keySet())
                .containsExactly("tritachyon", "hegemony");
        }

        @Test
        void systemKeysByBlocIdIgnoresLaterWritesToTheMapItWasBuiltFrom() {
            // The walk hands over the map it accumulated into and goes on holding it, so an index
            // that did not copy would keep changing after the rebuild that sealed it.
            var systemKeysByBlocId = new LinkedHashMap<String, Set<SystemKey>>();
            var hegemonySystemKeys = new LinkedHashSet<SystemKey>();

            hegemonySystemKeys.add(buildCellKey("system-a"));
            systemKeysByBlocId.put("hegemony", hegemonySystemKeys);

            var index = new BlocPresenceIndex(systemKeysByBlocId);

            hegemonySystemKeys.add(buildCellKey("system-b"));
            systemKeysByBlocId.put("tritachyon", new LinkedHashSet<>(buildCellKeys("system-c")));

            assertThat(index.systemKeysByBlocId())
                .containsExactly(Map.entry("hegemony", Set.of(buildCellKey("system-a"))));
        }
    }
}
