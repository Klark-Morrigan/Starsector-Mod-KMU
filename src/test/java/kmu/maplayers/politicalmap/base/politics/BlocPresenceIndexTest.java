package kmu.maplayers.politicalmap.base.politics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the index answers on its own terms, apart from the walk that fills it: the lookup a
 * render pass takes per hover, and the seal that stops a sealed index moving under it.
 */
final class BlocPresenceIndexTest {

    @Nested
    class ReadPresentSystemIds {

        @Test
        void readPresentSystemIdsAnswersTheSystemsABlocLivesIn() {
            // The lookup the hover takes: one bloc's own systems, in the order they were recorded.
            var index = new BlocPresenceIndex(Map.of(
                "hegemony", new LinkedHashSet<>(Set.of("system-a"))));

            assertThat(index.readPresentSystemIds("hegemony"))
                .containsExactly("system-a");
        }

        @Test
        void readPresentSystemIdsAnswersEmptyForABlocTheWalkNeverSurfaced() {
            // A bloc living nowhere is absent from the index rather than present with nothing, and
            // the lookup answers for it without the caller testing membership first - which is what
            // lets a render pass ask about whichever bloc the pointer is on.
            assertThat(BlocPresenceIndex.EMPTY.readPresentSystemIds("hegemony"))
                .isEmpty();
        }

        @Test
        void readPresentSystemIdsAnswersEmptyForNoBlocAtAll() {
            // The pointer resting on no row at all reaches the lookup as a null id, so it answers
            // the same nothing rather than throwing at the top of a render pass.
            assertThat(BlocPresenceIndex.EMPTY.readPresentSystemIds(null))
                .isEmpty();
        }
    }

    @Nested
    class SystemIdsByBlocId {

        @Test
        void systemIdsByBlocIdKeepsTheOrderItWasBuiltIn() {
            // Walk order at both levels, so two reads of one sector answer alike and a lit set is
            // assembled in a stable order.
            var systemIdsByBlocId = new LinkedHashMap<String, Set<String>>();

            systemIdsByBlocId.put("tritachyon", new LinkedHashSet<>(Set.of("system-b")));
            systemIdsByBlocId.put("hegemony", new LinkedHashSet<>(Set.of("system-a")));

            assertThat(new BlocPresenceIndex(systemIdsByBlocId).systemIdsByBlocId().keySet())
                .containsExactly("tritachyon", "hegemony");
        }

        @Test
        void systemIdsByBlocIdIgnoresLaterWritesToTheMapItWasBuiltFrom() {
            // The walk hands over the map it accumulated into and goes on holding it, so an index
            // that did not copy would keep changing after the rebuild that sealed it.
            var systemIdsByBlocId = new LinkedHashMap<String, Set<String>>();
            var hegemonySystemIds = new LinkedHashSet<String>();

            hegemonySystemIds.add("system-a");
            systemIdsByBlocId.put("hegemony", hegemonySystemIds);

            var index = new BlocPresenceIndex(systemIdsByBlocId);

            hegemonySystemIds.add("system-b");
            systemIdsByBlocId.put("tritachyon", new LinkedHashSet<>(Set.of("system-c")));

            assertThat(index.systemIdsByBlocId())
                .containsExactly(Map.entry("hegemony", Set.of("system-a")));
        }
    }
}
