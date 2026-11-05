package kmu.maplayers.politicalmap.dominance;

import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.politicalmap.dominance.weighting.MarketFootprint;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.HEGEMONY_FACTION_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.NEUTRAL_BASE;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.TRITACHYON_FACTION_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildOnlySystem;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildSectorWith;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.dominance.DominancePassFixtures.buildPassOver;
import static kmu.maplayers.politicalmap.dominance.DominancePassFixtures.buildStabilityWeightedRules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit coverage for {@link DominancePass}'s construction guard, for the cost claim its whole
 * arrangement rests on - that its several reads of one system share the walk that system was first
 * read by - and for the one place a system's winner is settled, which every surface naming a holder
 * takes its answer from and so must apply the pass's own candidacy bar.
 *
 * <p>The guard is here because a pass carries its rule and its reading of the sector through a
 * whole sector walk and dereferences each per system, so it rejects a null of either at
 * construction to fail fast rather than deep in the walk under a less legible error.
 *
 * <p>The regroup of footprints a caller already holds is here too, being the fold the alliances
 * view's holder and its box both rank by, and pure enough to pin over hand-built footprints.
 *
 * <p>What each per-system read <em>answers</em> - {@link DominancePass#readBlocFootprints},
 * {@link DominancePass#readKnownColonyFactionIds}, {@link DominancePass#readHabitationIn},
 * {@link DominancePass#resolveRankingRulesFor} - is covered
 * end to end by the
 * {@link SectorPolitics},
 * {@link FilteredPolitics}, the standings suites, and the
 * stats aggregations, which exercise the pass over a stubbed economy.
 */
class DominancePassTests {

    // Two factions folded into one bloc, with pirates left standing alone, so a case can tell a
    // member-summing regroup from one that keeps every faction its own bloc.
    private static final HolderGrouping ALLIANCE_GROUPING = new HolderGrouping(
        Map.of("hegemony", "alliance-1", "tritachyon", "alliance-1"),
        Map.of("alliance-1", "hegemony"),
        Map.of("alliance-1", "Allied Powers"));

    @Nested
    class Constructor {

        @Test
        void rejectsNullRules() {

            // ARRANGE
            var holding = HolderPass.createOver(null, UNDER_THE_FOG, HolderGrouping.identity());

            // ACT
            var actualFailure = assertThatThrownBy(() -> new DominancePass(null, holding));

            // ASSERT
            actualFailure
                .isInstanceOf(NullPointerException.class);
        }

        // A pass with no reading of the sector behind it would fault on the first system it read
        // rather than here, and a pass over a sector that cannot be reached is a different thing
        // entirely - a reading that answers an empty set, perfectly legal.
        @Test
        void rejectsNullHolding() {

            // ARRANGE
            var rules = buildStabilityWeightedRules();

            // ACT
            var actualFailure = assertThatThrownBy(() -> new DominancePass(rules, null));

            // ASSERT
            actualFailure
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ReadColoniesIn {

        // The claim the whole arrangement rests on: a rebuild reads each system for several things at
        // once - who holds it, what its blocs contribute, what a hover accounts for - and each of those
        // walking the system itself is what made a rebuild cost two or three traversals per system.
        @Test
        void walksASystemOnceHoweverManyOfThePassesReadsAskAboutIt() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony),
                buildVisibleMarket(hegemony, 5));
            var system = buildOnlySystem(sector);
            var pass = buildPassOver(sector);

            // ACT
            pass.readColoniesIn(system);
            pass.readFootprintsByFaction(system);
            pass.readWeightBreakdownsByFaction(system);
            pass.readUnweighedColoniesByFaction(system);
            pass.readKnownColonyFactionIds(system);
            pass.readHabitationIn(system);
            pass.readBlocFootprints(system);
            pass.resolveRankingRulesFor(system);

            // ASSERT
            // Counted on the entity scan, which one walk of a system makes exactly once - the economy
            // read beside it is made twice by the walk itself, so counting that would pin how the
            // walk is written rather than how often it is made.
            verify(system, times(1))
                .getAllEntities();
        }
    }

    @Nested
    class ReadBlocFootprints {

        // A colony a mod hung on a faction with no ID. The fold cannot name a bloc for it, so it is
        // left out rather than folded under a nameless key the grouping would fault on, taking the
        // whole resolve down with it.
        @Test
        void leavesOutAColonyWhoseOwnerCarriesNoId() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony),
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(buildFaction(null, HEGEMONY_FACTION_BRIGHT), 5));

            // ACT
            var actualFootprintByBlocId = buildPassOver(sector)
                .readBlocFootprints(buildOnlySystem(sector));

            // ASSERT
            assertThat(actualFootprintByBlocId)
                .containsOnlyKeys("hegemony");
        }
    }

    @Nested
    class RegroupFootprintsByBloc {

        // The alliances view's shape: two members fold into their bloc, which ranks on what they come
        // to between them - their markets counted together, their weights summed, and the heavier of
        // their two largest markets as the bloc's largest. A faction outside the alliance stays its
        // own bloc.
        @Test
        void sumsTheMembersOfABlocIntoOneFootprint() {

            // ARRANGE
            var pass = DominancePass.createOver(null, buildStabilityWeightedRules(), UNDER_THE_FOG, ALLIANCE_GROUPING);
            var footprintByFactionId = Map.of(
                "hegemony", new MarketFootprint(1, 5000, 5000, 5000),
                "tritachyon", new MarketFootprint(1, 3000, 3000, 0),
                "pirates", new MarketFootprint(1, 2000, 2000, 2000));

            var expectedFootprintByBlocId = Map.of(
                "alliance-1", new MarketFootprint(2, 8000, 5000, 5000),
                "pirates", new MarketFootprint(1, 2000, 2000, 2000));

            // ACT
            var actualFootprintByBlocId = pass.regroupFootprintsByBloc(footprintByFactionId);

            // ASSERT
            assertThat(actualFootprintByBlocId)
                .isEqualTo(expectedFootprintByBlocId);
        }

        // The faction view's shape: every faction is its own bloc, so the footprints come out exactly
        // as they went in.
        @Test
        void keepsEveryFactionItsOwnBlocUnderTheIdentityGrouping() {

            // ARRANGE
            var pass = DominancePass.createOver(
                null,
                buildStabilityWeightedRules(),
                UNDER_THE_FOG,
                HolderGrouping.identity());
            var footprintByFactionId = Map.of(
                "hegemony", new MarketFootprint(1, 5000, 5000, 5000),
                "tritachyon", new MarketFootprint(1, 3000, 3000, 0));

            var expectedFootprintByBlocId = Map.of(
                "hegemony", new MarketFootprint(1, 5000, 5000, 5000),
                "tritachyon", new MarketFootprint(1, 3000, 3000, 0));

            // ACT
            var actualFootprintByBlocId = pass.regroupFootprintsByBloc(footprintByFactionId);

            // ASSERT
            assertThat(actualFootprintByBlocId)
                .isEqualTo(expectedFootprintByBlocId);
        }

        @Test
        void answersNothingForASystemNobodyWasWeighedIn() {

            // ARRANGE
            var pass = DominancePass.createOver(null, buildStabilityWeightedRules(), UNDER_THE_FOG, ALLIANCE_GROUPING);

            // ACT
            var actualFootprintByBlocId = pass.regroupFootprintsByBloc(Map.of());

            // ASSERT
            assertThat(actualFootprintByBlocId)
                .isEmpty();
        }
    }

    @Nested
    class ResolveDominantBlocId {

        @Test
        void namesTheHeavierBloc() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony, tritachyon),
                buildVisibleMarket(hegemony, 3),
                buildVisibleMarket(tritachyon, 5));
            var system = buildOnlySystem(sector);
            var pass = buildPassOver(sector);

            // ACT
            var actualDominantBlocId = pass.resolveDominantBlocId(system, pass.readBlocFootprints(system));

            // ASSERT
            assertThat(actualDominantBlocId)
                .isEqualTo("tritachyon");
        }

        // The pass's own candidacy bar rides in with the ranking, so the neutral placeholder's heavier
        // footprint buys it nothing - the one rule every surface naming a winner shares.
        @Test
        void passesOverAHeavierNeutralBloc() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var neutral = buildFaction(Factions.NEUTRAL, NEUTRAL_BASE);
            var sector = buildSectorWith(
                "salvage-system",
                List.of(neutral, hegemony),
                buildVisibleMarket(neutral, 6),
                buildVisibleMarket(hegemony, 3));
            var system = buildOnlySystem(sector);
            var pass = buildPassOver(sector);

            // ACT
            var actualDominantBlocId = pass.resolveDominantBlocId(system, pass.readBlocFootprints(system));

            // ASSERT
            assertThat(actualDominantBlocId)
                .isEqualTo("hegemony");
        }

        @Test
        void isNullWhereNothingWasWeighed() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony),
                buildVisibleMarket(hegemony, 5));

            // ACT
            var actualDominantBlocId = buildPassOver(sector)
                .resolveDominantBlocId(buildOnlySystem(sector), Map.of());

            // ASSERT
            assertThat(actualDominantBlocId)
                .isNull();
        }
    }
}
