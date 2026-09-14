package kmu.maplayers.base.visibility.systems;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import kmu.maplayers.DecivilisedPlanetFixtures;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.visibility.systems.MapSectorFixture.buildUnroutedSectorOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what a pass adds over the rule it answers: the parts it must be handed, and that a system
 * asked about twice is read once.
 *
 * <p>What the rule itself admits is exercised next door, over this same pass. Only the memo is
 * here, because it is the one thing no case about the rule can show - a pass that forgot between
 * asks would go on giving every one of those cases the right answer, and would quietly walk every
 * planet in the sector again on each of the reads a poll makes.
 *
 * <p>Two counts are taken, one per thing the pass remembers. The ruin walk is the first: the colony
 * half is memoised in the index beneath, so it was never what repeated, and the ruin walk had no
 * memo of its own until the pass gave it one. The drawn answer is the second, and it repeats for a
 * different reason - the access half of the rule reads the sector directly, the system's gates
 * among it, where no index stands between.
 */
class MapVisibilityPassTest {

    // What one poll's reads of a system amount to once the memo holds: the fingerprint scan asks
    // for the salt and again through membership, and the motion walk asks a third time.
    private static final int ONE_READ = 1;

    // The override on, so a case about what the map would show of a system is posed over a pass
    // that is showing it either way - which is the only arrangement in which the two answers
    // can be told apart.
    private static final MapVisibilityRules SHOWING_HIDDEN_SYSTEMS =
        new MapVisibilityRules(ColonyVisibility.BASE_FOG, true);

    @Nested
    class Constructor {

        @Test
        void rejectsNullColonies() {
            // A pass with no walk behind it would fault on the first system it read rather than
            // here, and a pass over a sector that cannot be reached is a different thing entirely -
            // an index over a null sector, which answers an empty set and is perfectly legal.
            assertThatThrownBy(() ->
                    new MapVisibilityPass(null, VisibleStars.scan(null), MapVisibilityRules.BASE))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullVisibleStars() {
            // Standing an empty scan in would draw no system on the access path, so a sector's
            // whole core would vanish from the map with nothing on screen saying why.
            assertThatThrownBy(() ->
                    new MapVisibilityPass(
                        new SectorPassIndex(null),
                        null,
                        MapVisibilityRules.BASE))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullRules() {
            // Required on the same terms as the other two: the rules are sampled once where the
            // tick begins, so a null here is that one read having gone wrong.
            assertThatThrownBy(() ->
                    new MapVisibilityPass(
                        new SectorPassIndex(null),
                        VisibleStars.scan(null),
                        null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Rules {

        @Test
        void answersTheRulesThePassWasOpenedUnder() {
            // A reader taking a walk of its own off this pass judges by the rules the tick began
            // under, which is only possible if the pass hands back the very ones it was given -
            // re-reading the settings would let two walks in one tick disagree.
            var rules = MapVisibilityRules.BASE;
            var pass = MapVisibilityPass.over(buildUnroutedSectorOf(buildSystem("a")), rules);

            assertThat(pass.rules())
                .isSameAs(rules);
        }
    }

    @Nested
    class IsDrawn {

        @Test
        void readsOneSystemsAccessOnceHoweverOftenTheDrawnAnswerIsAsked() {
            // The gate walk stands for the whole access half: it is the first thing the rule asks
            // of the system and the only one a mock counts cleanly. Several walks share a pass and
            // each asks this of every system, so a pass that forgot between asks would multiply
            // the sector traversal a rebuild is budgeted one of.
            var system = buildSystem("a");
            var pass = MapVisibilityPass.over(buildUnroutedSectorOf(system), MapVisibilityRules.BASE);

            pass.isDrawn(system);
            pass.isDrawn(system);
            pass.isDrawn(system);

            verify(system, times(ONE_READ)).getEntitiesWithTag(Tags.GATE);
        }

        @Test
        void asksTheSystemForItsGatesOncePerReading() {
            // The arms of reachability are taken singly rather than through the fold that sums
            // them, so one reading costs one gate walk. Asking the fold as well would walk them
            // again to be told what this has already established.
            var system = buildSystem("a");
            var pass = MapVisibilityPass.over(buildUnroutedSectorOf(system), MapVisibilityRules.BASE);

            pass.isDrawn(system);

            verify(system, times(ONE_READ)).getEntitiesWithTag(Tags.GATE);
        }

        @Test
        void answersForNoSystemWithoutTryingToRememberIt() {
            // A system there is nothing of has no key to file an answer under, so the ask goes
            // straight to the rule. What the rule says of nothing is its own business, and under
            // the base rules it says the map draws nothing.
            var pass = MapVisibilityPass.over(
                buildUnroutedSectorOf(buildSystem("a")),
                MapVisibilityRules.BASE);

            assertThat(pass.isDrawn(null))
                .isFalse();
        }
    }

    @Nested
    class IsHiddenSystem {

        @Test
        void callsASystemWithNoAccessAndNobodyInItHiddenEvenWhereThePassShowsIt() {
            // The question is what the map would show of the system, not what this pass is showing:
            // asked under the override that puts every system on the map, it still has to answer
            // that this one is only there because of it.
            var system = buildSystem("empty");
            var pass = MapVisibilityPass.over(buildUnroutedSectorOf(system), SHOWING_HIDDEN_SYSTEMS);

            assertThat(pass.isHiddenSystem(system))
                .isTrue();
        }

        @Test
        void callsAnInhabitedSystemShownInItsOwnRight() {
            // Somebody living there is a place on the map the override has nothing to do with, so
            // the same forcing pass has to tell this system from the one above.
            var system = buildSystem("ruined");

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            var pass = MapVisibilityPass.over(buildUnroutedSectorOf(system), SHOWING_HIDDEN_SYSTEMS);

            assertThat(pass.isHiddenSystem(system))
                .isFalse();
        }
    }

    @Nested
    class IsRevealedDecivilised {

        @Test
        void readsOneSystemsColoniesOnceHoweverOftenTheRuinIsAsked() {
            // Every public read reaches the pass's one walk of the system, so the count covers a
            // poll's whole use of it: the salt asks the ruin outright, and membership asks it
            // through inhabitation.
            var system = buildSystem("a");
            var pass = MapVisibilityPass.over(buildUnroutedSectorOf(system), MapVisibilityRules.BASE);

            pass.isRevealedDecivilised(system);
            pass.isSystemInhabited(system);
            pass.isDrawn(system);

            verify(system, times(ONE_READ)).getAllEntities();
        }

        @Test
        void answersOffTheColonySetRatherThanOffTheSystemsPlanets() {
            // A ruin is a kind of colony, so the pass reads it where it reads everything else -
            // which is what keeps the salt, the cell and the box over it from ever parting on
            // whether a system is drawn as ruins.
            var ruinedSystem = buildSystem("ruined");
            var emptySystem = buildSystem("empty");

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(ruinedSystem);

            var pass = MapVisibilityPass.over(
                buildUnroutedSectorOf(ruinedSystem, emptySystem),
                MapVisibilityRules.BASE);

            assertThat(pass.isRevealedDecivilised(ruinedSystem))
                .isTrue();
            assertThat(pass.isRevealedDecivilised(emptySystem))
                .isFalse();
        }
    }

    // A system holding nothing at all, which each case then furnishes. Its planets are stubbed
    // empty rather than left to the mock default, so the ruin walk really runs and the count
    // above is of a read that happened.
    private static StarSystemAPI buildSystem(String id) {

        var systemMock = StarSystemFixture.buildSystem(id);

        when(systemMock.getPlanets())
            .thenReturn(List.of());

        return systemMock;
    }
}
