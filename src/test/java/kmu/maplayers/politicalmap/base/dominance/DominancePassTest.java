package kmu.maplayers.politicalmap.base.dominance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildPassOver;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildStabilityWeightedRules;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit coverage for {@link DominancePass}'s construction guard and for the cost claim its whole
 * arrangement rests on: that its several reads of one system share the walk that system was first
 * read by.
 *
 * <p>The guard is here because a pass carries its rule and its reading of the sector through a
 * whole sector walk and dereferences each per system, so it rejects a null of either at
 * construction to fail fast rather than deep in the walk under a less legible error.
 *
 * <p>What each per-system read <em>answers</em> - {@link DominancePass#readBlocFootprints},
 * {@link DominancePass#readBlocContributions}, {@link DominancePass#readKnownColonyFactionIds},
 * {@link DominancePass#readKnownColonyBlocIds}, and {@link DominancePass#tieBreakFor} - is covered
 * end to end by the
 * {@link kmu.maplayers.politicalmap.base.politics.SectorPolitics},
 * {@link kmu.maplayers.politicalmap.base.politics.FilteredPolitics}, the standings suites, and the
 * stats aggregations, which exercise the pass over a stubbed economy.
 */
class DominancePassTest {

    @Nested
    class Constructor {

        @Test
        void rejectsNullRules() {

            assertThatThrownBy(() ->
                    new DominancePass(
                        null,
                        HolderPass.over(null, false, HolderGrouping.identity())))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullHolding() {
            // A pass with no reading of the sector behind it would fault on the first system it
            // read rather than here, and a pass over a sector that cannot be reached is a
            // different thing entirely - a reading that answers an empty set, perfectly legal.
            assertThatThrownBy(() ->
                    new DominancePass(buildStabilityWeightedRules(), null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ReadColoniesIn {

        @Test
        void walksASystemOnceHoweverManyOfThePassesReadsAskAboutIt() {
            // The claim the whole arrangement rests on: a rebuild reads each system for several
            // things at once - who holds it, what its blocs contribute, what a hover accounts for -
            // and each of those walking the system itself is what made a rebuild cost two or three
            // traversals per system.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony),
                buildVisibleMarket(hegemony, 5));

            var system = buildOnlySystem(sector);
            var pass = buildPassOver(sector);

            pass.readColoniesIn(system);
            pass.readFootprintsByFaction(system);
            pass.readKnownColonyFactionIds(system);
            pass.readKnownColonyBlocIds(system);
            pass.readBlocFootprints(system);
            pass.readBlocContributions(system);
            pass.tieBreakFor(system);

            // Counted on the entity scan, which one walk of a system makes exactly once - the
            // economy read beside it is made twice by the walk itself, so counting that would pin
            // how the walk is written rather than how often it is made.
            verify(system, times(1))
                .getAllEntities();
        }
    }

    @Nested
    class ReadBlocFootprints {

        @Test
        void leavesOutAColonyWhoseOwnerCarriesNoId() {
            // A colony a mod hung on a faction with no id. The fold cannot name a bloc for it, so it
            // is left out - where before it was folded under a nameless key and the grouping faulted
            // on that key, taking the whole resolve down with it.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony),
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(buildFaction(null, HEGEMONY_BRIGHT), 5));

            var footprintByBlocId = buildPassOver(sector)
                .readBlocFootprints(buildOnlySystem(sector));

            assertThat(footprintByBlocId)
                .containsOnlyKeys("hegemony");
        }
    }
}
