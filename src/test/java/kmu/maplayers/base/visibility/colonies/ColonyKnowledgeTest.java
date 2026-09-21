package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.factions.alliances.FactionAlliances;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.markets.colonies.Colony;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static kmu.maplayers.base.visibility.colonies.FactionAllianceFixture.buildAllianceOf;
import static kmu.maplayers.base.visibility.colonies.FactionAllianceFixture.registerAllianceOf;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.ACADEMY_ENTITY_ID;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.clearRegistrations;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.registerTheAcademy;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.standOnEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the projections {@link ColonyKnowledge} states over a colony set - the known listing and the
 * habitation reading, each beside the emptiness question asked of it - the two observation
 * reads the register is written from, and the sector-scoped facts a pass folds when it opens.
 * Each method's cases live in a {@link Nested} group so the suite reports as a per-method tree;
 * the world they are posed against is {@link ColonyKnowledgeFixture}.
 * The known projection's group is sub-grouped once more, by the arm of the rule each case
 * exercises - the only group large enough to need it.
 *
 * <p>How a set is selected in the first place is the library's, and is pinned there. What this adds
 * is the map's own framing over it: which kind of place each colony is, and what may be drawn from
 * that.
 */
final class ColonyKnowledgeTest {

    // Every gate this rule can hold, which is how a case poses the shipped state - both leaking
    // shapes held back until somebody has seen them.
    private static final Set<RevelationGate> EVERY_GATE =
        Set.of(RevelationGate.SPACE_DERELICTS, RevelationGate.HIDDEN_COLONIES);

    // Nothing widened, which is the ordinary state: nothing admitted that the player has not
    // found, and no collapsed colony named on a world nobody has looked at.
    private static final boolean NOTHING_REVEALED = false;
    private static final boolean UNDISCOVERED_REVEALED = true;

    // The survey the fog itself asks for, which every case but the survey ones poses.
    private static final SurveyLevel FOGGED_SURVEY_LEVEL = DecivilisedMarkets.DEFAULT_SURVEY_LEVEL;

    // The rule as it ships.
    private static final ColonyVisibility BOTH_GATES_ON =
        new ColonyVisibility(NOTHING_REVEALED, FOGGED_SURVEY_LEVEL, EVERY_GATE);

    // One gate apiece, which is how a case shows the two are independent of each other.
    private static final ColonyVisibility ONLY_STATIONS_GATED = new ColonyVisibility(
        NOTHING_REVEALED,
        FOGGED_SURVEY_LEVEL,
        Set.of(RevelationGate.SPACE_DERELICTS));

    private static final ColonyVisibility ONLY_HIDDEN_GATED = new ColonyVisibility(
        NOTHING_REVEALED,
        FOGGED_SURVEY_LEVEL,
        Set.of(RevelationGate.HIDDEN_COLONIES));

    // The discovery reveal with nothing gated, which is what a case about the fog alone poses: the
    // reveal drops the arm it names and no gate, so stating it beside a gate would pose two things
    // at once.
    private static final ColonyVisibility REVEAL_UNDISCOVERED = new ColonyVisibility(
        UNDISCOVERED_REVEALED,
        FOGGED_SURVEY_LEVEL,
        Set.of());

    // The same reveal with every gate still in force - the pairing that shows a reveal reaching
    // the fog and stopping there.
    private static final ColonyVisibility REVEAL_UNDISCOVERED_UNDER_EVERY_GATE =
        new ColonyVisibility(UNDISCOVERED_REVEALED, FOGGED_SURVEY_LEVEL, EVERY_GATE);

    // The survey bar dropped to nothing, which is the only thing that shows a collapsed colony on
    // a world nobody has looked at.
    private static final ColonyVisibility ASKING_NO_SURVEY = new ColonyVisibility(
        NOTHING_REVEALED,
        SurveyLevel.NONE,
        Set.of());

    // The survey bar raised past what any case here poses, for the colony a stricter player would
    // rather not be told about yet.
    private static final ColonyVisibility ASKING_A_FULL_SURVEY = new ColonyVisibility(
        NOTHING_REVEALED,
        SurveyLevel.FULL,
        Set.of());

    // One notch above what a sighting is worth, which is the boundary the player's own route into
    // a collapsed world turns on rather than a bar picked for being high: at this level the player
    // has asked for readings off the world itself, and a fly-past produces none. Every gate is
    // left in force so that a case pairing this with the shipped rule moves the bar and nothing
    // beside it.
    private static final ColonyVisibility ASKING_A_PRELIMINARY_SURVEY = new ColonyVisibility(
        NOTHING_REVEALED,
        SurveyLevel.PRELIMINARY,
        EVERY_GATE);

    // Both fog arms open at once, for the world neither knob alone reaches.
    private static final ColonyVisibility ASKING_NEITHER_FOG_ARM = new ColonyVisibility(
        UNDISCOVERED_REVEALED,
        SurveyLevel.NONE,
        Set.of());

    @AfterEach
    void clearOpenlyKnownColonies() {
        clearRegistrations();
    }

    @AfterEach
    void clearRegisteredAlliances() {
        FactionAllianceFixture.clearRegistrations();
    }

    @Nested
    class ReadKnownColonies {

        // Sub-grouped by the arm of the rule each case exercises rather than by subject matter, the
        // rule being written as one predicate over exactly these: the fog and the reveal that lifts
        // it, the report that stands in for the fog where it has nothing to read, the player's own
        // sighting, the place's settling owners, and the kinds no gate covers.
        // An arm is somewhere a case unambiguously belongs; a theme is a judgement call made again
        // every time one is added.

        // The fog and the reveals that lift an arm of it - what a colony has to pass before any
        // gate is consulted.
        @Nested
        class BaseFog {

            @Test
            void excludesAColonyThePlayerHasNotFound() {
                // Concealed and on an undiscovered entity: the shape the fog has to keep back on both
                // counts - naming its owner in a box would tell the player exactly what is hiding out
                // there.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var base = fixture.buildUndiscoveredConcealedColony("pirates");

                assertThat(knowing(fixture, ColonyVisibility.BASE_FOG).readKnownColonies(buildColoniesOf(buildColony(base))))
                    .isEmpty();
            }

            @Test
            void excludesAnOpenColonyOnAnEntityThePlayerHasNotFound() {
                // A derelict station: the sector's most common undiscovered colony, and the one shape
                // whose concealment and discovery disagree. Nothing hides it, so a projection reading
                // concealment would paint its system as settled from the first frame of a campaign,
                // for a place no fleet has been near.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var derelict = fixture.buildUndiscoveredOpenColony(Factions.NEUTRAL);

                assertThat(knowing(fixture, ColonyVisibility.BASE_FOG).readKnownColonies(buildColoniesOf(buildColony(derelict))))
                    .isEmpty();
            }

            @Test
            void restoresAColonyThePlayerHasNotFoundUnderTheReveal() {

                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var base = fixture.buildUndiscoveredConcealedColony("pirates");

                assertThat(knowing(fixture, REVEAL_UNDISCOVERED).readKnownColonies(buildColoniesOf(buildColony(base))))
                    .containsExactly(buildColony(base));
            }

            @Test
            void withholdsAnUndiscoveredConcealedColonyUnderTheDiscoveryRevealAlone() {
                // The rule each toggle is written to: a reveal drops the arm it names and clears no
                // gate beside it. This colony is held back twice over - undiscovered, and concealed in a
                // place nobody has seen it - so lifting the fog leaves the second reason standing.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var base = fixture.buildUndiscoveredConcealedColony("pirates");

                fixture.placeColoniesInSystem(base);

                assertThat(knowing(fixture, REVEAL_UNDISCOVERED_UNDER_EVERY_GATE).readKnownColonies(buildColoniesOf(buildColony(base))))
                    .isEmpty();
            }

            @Test
            void keepsAConcealedColonyThePlayerHasFound() {
                // A raided pirate base stays permanently hidden while being perfectly well known, so
                // concealment alone must not fog it out where no gate asks it to.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var base = fixture.buildFoundConcealedColony("pirates");

                assertThat(knowing(fixture, ColonyVisibility.BASE_FOG).readKnownColonies(buildColoniesOf(buildColony(base))))
                    .containsExactly(buildColony(base));
            }

            @Test
            void keepsTheSetSOwnOrder() {

                var fixture = new ColonyKnowledgeFixture("corvus");
                var first = fixture.buildVisibleColony("hegemony");
                var second = fixture.buildVisibleColony("tritachyon");

                assertThat(knowing(fixture, ColonyVisibility.BASE_FOG).readKnownColonies(buildColoniesOf(buildColony(first), buildColony(second))))
                    .containsExactly(buildColony(first), buildColony(second));
            }

            @Test
            void readsAnUnstatedRuleAsTheFogAlone() {
                // A gate nobody asked for must not appear out of a missing argument, and neither
                // must a reveal: a derelict the player has found reads known, and one they have not
                // does not.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var derelict = fixture.buildDerelictStation();

                assertThat(knowing(fixture, null).readKnownColonies(buildColoniesOf(buildDerelict(derelict))))
                    .containsExactly(buildDerelict(derelict));
            }

            @ParameterizedTest
            @MethodSource("kmu.maplayers.base.visibility.colonies.ColonyKnowledgeTest#buildEveryGateCombination")
            void excludesAnUndiscoveredColonyInASettledSystem(ColonyVisibility rule) {
                // The conjunction's own case. Revelation is a second condition on top of the fog and
                // never an alternative to it, so a system full of witnesses - entered by the player,
                // and holding a colony that settles it - still shows nothing the player has not
                // found, whichever way the gates are set.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var undiscoveredBase = fixture.buildUndiscoveredConcealedColony("pirates");
                var colony = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(undiscoveredBase, colony);
                fixture.markColoniesAsSighted(undiscoveredBase, colony);

                assertThat(knowing(fixture, rule).readKnownColonies(buildColoniesOf(buildColony(undiscoveredBase), buildColony(colony))))
                    .containsExactly(buildColony(colony));
            }

            @Test
            void withholdsAGatedColonyUnderTheRevealWhereNobodyHasSeenIt() {
                // The same rule from the derelict's side: the reveal says it may be shown
                // though nobody found it, which is no answer at all to whether anybody has seen it
                // standing here.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var derelict = fixture.buildDerelictStation();

                fixture.placeColoniesInSystem(derelict);

                assertThat(knowing(fixture, REVEAL_UNDISCOVERED_UNDER_EVERY_GATE).readKnownColonies(buildColoniesOf(buildDerelict(derelict))))
                    .isEmpty();
            }

            @Test
            void admitsASurveyedDecivilisedWorld() {
                // A collapse the player has read: found by the survey arm, gated by nothing, and
                // condition-only - which is what every other colony read refuses it for.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var decivilisedWorld = fixture.buildDecivilisedWorld();

                fixture.placeColoniesInSystem(decivilisedWorld);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildUngovernedColony(decivilisedWorld))))
                    .containsExactly(buildUngovernedColony(decivilisedWorld));
            }

            @Test
            void withholdsADecivilisedWorldNobodyHasSurveyed() {
                // The collapse has happened and the player has no way of knowing it, so the map may
                // not
                // say so - and the planet being found is beside the point, the two arms being
                // independent.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();

                fixture.placeColoniesInSystem(decivilisedWorld);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildUngovernedColony(decivilisedWorld))))
                    .isEmpty();
            }

            @Test
            void withholdsASeenDecivilisedWorldWhereAFullSurveyIsAskedFor() {
                // The bar moves both ways, which is what makes it a level rather than a reveal: a
                // world vanilla would show is withheld where the map has been asked for more than
                // vanilla asks.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var decivilisedWorld = fixture.buildSeenDecivilisedWorld();

                fixture.placeColoniesInSystem(decivilisedWorld);

                assertThat(knowing(fixture, ASKING_A_FULL_SURVEY).readKnownColonies(buildColoniesOf(buildUngovernedColony(decivilisedWorld))))
                    .isEmpty();
            }

            @Test
            void admitsAnUnsurveyedDecivilisedWorldWhereNoSurveyIsAskedFor() {

                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();

                fixture.placeColoniesInSystem(decivilisedWorld);

                assertThat(knowing(fixture, ASKING_NO_SURVEY).readKnownColonies(buildColoniesOf(buildUngovernedColony(decivilisedWorld))))
                    .containsExactly(buildUngovernedColony(decivilisedWorld));
            }

            @Test
            void withholdsAnUndiscoveredUnsurveyedDecivilisedWorldUnderEitherKnobAlone() {
                // The kind's own case of the rule every knob on the tab is written to: two arms
                // hold this world back, each knob reaches one, and neither reaches the other's.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var decivilisedWorld = fixture.buildUndiscoveredUnsurveyedDecivilisedWorld();

                fixture.placeColoniesInSystem(decivilisedWorld);

                var colonies = buildColoniesOf(buildUngovernedColony(decivilisedWorld));

                assertThat(knowing(fixture, ASKING_NO_SURVEY).readKnownColonies(colonies))
                    .isEmpty();
                assertThat(knowing(fixture, REVEAL_UNDISCOVERED).readKnownColonies(colonies))
                    .isEmpty();
                assertThat(knowing(fixture, ASKING_NEITHER_FOG_ARM).readKnownColonies(colonies))
                    .containsExactly(buildUngovernedColony(decivilisedWorld));
            }
        }

        // The player's own route: a sighting naming the place the colony stands in now. The case a
        // place nothing has settled opens the group, both routes having to fail for it to answer.
        @Nested
        class SightingRoute {

            @Test
            void excludesADerelictAloneInASystemNobodyHasSeen() {
                // The Sentinel Gantries reading: found by the fog because nothing hides it, and
                // nothing whatever about it has reached the player.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var derelict = fixture.buildDerelictStation();

                fixture.placeColoniesInSystem(derelict);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildDerelict(derelict))))
                    .isEmpty();
            }

            @Test
            void keepsADerelictThePlayerHasSeenWhereItStands() {

                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var derelict = fixture.buildDerelictStation();

                fixture.placeColoniesInSystem(derelict);
                fixture.markColoniesAsSighted(derelict);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildDerelict(derelict))))
                    .containsExactly(buildDerelict(derelict));
            }

            @Test
            void excludesAColonyThePlayerSawInASystemItHasSinceLeft() {
                // The mover: an exoship warping about the fringes carries a sighting naming wherever
                // it was met, and standing somewhere else is being unseen again. A gate reading only
                // that the player had once been here would show it from the frame it arrived in.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var mover = fixture.buildFoundConcealedColony("rat_exotech");

                fixture.placeColoniesInSystem(mover);
                fixture.markColoniesAsSightedElsewhere("corvus", mover);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildColony(mover))))
                    .isEmpty();
            }

            @Test
            void keepsAMoverOnceThePlayerMeetsItWhereItHasGone() {
                // The other half of the case above, and what keeps the rule from being a one-way
                // door: the sighting is refreshed by the meeting, not by the first one ever made.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var mover = fixture.buildFoundConcealedColony("rat_exotech");

                fixture.placeColoniesInSystem(mover);
                fixture.markColoniesAsSightedElsewhere("corvus", mover);
                fixture.markColoniesAsSighted(mover);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildColony(mover))))
                    .containsExactly(buildColony(mover));
            }

            @Test
            void excludesAColonyFoundedWhereThePlayerHasAlreadyBeen() {
                // The founding case, which is the mover seen from the other end: a base built in a
                // system the player cleared years ago was never there to be seen, and no amount of
                // time passing changes that - nothing in the rule reads a clock at all.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var neighbour = fixture.buildFoundConcealedColony("pirates");
                var foundedSince = fixture.buildFoundConcealedColony("luddic_path");

                fixture.placeColoniesInSystem(neighbour, foundedSince);
                fixture.markColoniesAsSighted(neighbour);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildColony(neighbour), buildColony(foundedSince))))
                    .containsExactly(buildColony(neighbour));
            }

            @Test
            void keepsAConcealedColonyItsOwnFactionSheltersOnceThePlayerHasSeenIt() {
                // Owner-awareness narrows the route that runs through the neighbours and leaves the
                // player's own untouched: having been there is knowing, whoever else holds the place.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var base = fixture.buildFoundConcealedColony("pirates");
                var ownColony = fixture.buildVisibleColony("pirates");

                fixture.placeColoniesInSystem(base, ownColony);
                fixture.markColoniesAsSighted(base);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildColony(base), buildColony(ownColony))))
                    .containsExactly(buildColony(base), buildColony(ownColony));
            }

            @Test
            void keepsAGatedColonyStandingInNoStarSystem() {
                // Hyperspace, where mods put a few. There is no system to have been in and none to be
                // settled, so a gate answering otherwise would withhold it for the whole campaign.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var derelict = fixture.buildDerelictStation();

                placeColonyOutsideAnySystem(derelict);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildDerelict(derelict))))
                    .containsExactly(buildDerelict(derelict));
            }
        }

        // The route through the place itself: who else stands here, and whether any of them is
        // somebody other than the colony's own owner.
        @Nested
        class SettledRoute {

            @Test
            void keepsAnUnseenMoverAmongInhabitantsAndDropsItWhereThereAreNone() {
                // The settled route is untouched by any of the above: it reads the place as it stands,
                // so a colony arriving among people who can see it is revealed by the arrival itself,
                // needs no sighting of its own, and loses that revelation the moment it warps out to
                // somewhere nobody is watching.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var mover = fixture.buildFoundConcealedColony("rat_exotech");
                var colony = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(mover, colony);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildColony(mover), buildColony(colony))))
                    .containsExactly(buildColony(mover), buildColony(colony));

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildColony(mover))))
                    .isEmpty();
            }

            @Test
            void keepsADerelictOnceAColonyIsFoundedBesideIt() {
                // The second route to revelation: a derelict in orbit over an inhabited world is
                // common
                // knowledge there, whether or not the player has ever been.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var derelict = fixture.buildDerelictStation();
                var colony = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(derelict, colony);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildDerelict(derelict), buildColony(colony))))
                    .containsExactly(buildDerelict(derelict), buildColony(colony));
            }

            @Test
            void excludesADerelictBesideAColonyThePlayerHasNotFound() {
                // A system counts as settled by what the player is shown, not by what is there: an
                // undiscovered colony is no grapevine the player is party to, so the derelict beside
                // it stays unmentioned rather than being vouched for by a place nobody has seen.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var derelict = fixture.buildDerelictStation();
                var undiscoveredColony = fixture.buildUndiscoveredOpenColony("hegemony");

                fixture.placeColoniesInSystem(derelict, undiscoveredColony);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildDerelict(derelict), buildColony(undiscoveredColony))))
                    .isEmpty();
            }

            @Test
            void excludesADerelictVouchedForOnlyByAnotherDerelict() {
                // A derelict cannot settle anything, having never had anybody aboard, so a place
                // holding nothing but derelicts reveals none of them.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var first = fixture.buildDerelictStation();
                var second = fixture.buildDerelictStation();

                fixture.placeColoniesInSystem(first, second);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildDerelict(first), buildDerelict(second))))
                    .isEmpty();
            }

            @Test
            void excludesAConcealedColonyInASystemNobodyHasSeen() {
                // The Daybreak reading: a colony that hides itself, on an entity that was never
                // discoverable, so the fog admits it and only revelation can hold it back.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var exoship = fixture.buildFoundConcealedColony("rat_exotech");

                fixture.placeColoniesInSystem(exoship);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildColony(exoship))))
                    .isEmpty();
            }

            @Test
            void keepsAConcealedColonyASystemSOwnInhabitantsCanSee() {
                // The Galatia Academy reading, and the case that makes the settled route necessary
                // rather than tidy: nothing on the market tells it from the exoship above, and only
                // the Hegemony world in the same system does.
                var fixture = new ColonyKnowledgeFixture("galatia");
                var academy = fixture.buildFoundConcealedColony("independent");
                var ancyra = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(academy, ancyra);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildColony(academy), buildColony(ancyra))))
                    .containsExactly(buildColony(academy), buildColony(ancyra));
            }

            @Test
            void excludesAColonyTheSectorOpenlyPointsAtWhereNothingSettlesItsSystem() {
                // The same Academy, with the sector's own vouching for it registered. That excuses
                // one word on a hover box and says nothing whatever about what may be shown: alone
                // in its system it is withheld exactly as the exoship above is. One flag serving
                // both readings is the obvious-looking simplification, and this is where it shows.
                var fixture = new ColonyKnowledgeFixture("galatia");
                var academy = registerAsOpenlyKnown(fixture.buildFoundConcealedColony("independent"));

                fixture.placeColoniesInSystem(academy);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildColony(academy))))
                    .isEmpty();
            }

            @Test
            void keepsAColonyTheSectorOpenlyPointsAtWhereItsNeighboursCanSeeIt() {
                // The other half of that pair: the route in is the settling neighbour, exactly as it
                // is for the base beside it, and the registered vouching neither adds nor removes
                // one.
                var fixture = new ColonyKnowledgeFixture("galatia");
                var academy = registerAsOpenlyKnown(fixture.buildFoundConcealedColony("independent"));
                var ancyra = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(academy, ancyra);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildColony(academy), buildColony(ancyra))))
                    .containsExactly(buildColony(academy), buildColony(ancyra));
            }

            @Test
            void excludesAConcealedColonyOnlyItsOwnFactionCouldVouchFor() {
                // A pirate base in a system the pirates openly hold. The settled route rests on
                // somebody saying what they can see, and the party keeping the secret is the one that
                // will not - so the base falls back to the player's own sighting, which nothing here
                // has made.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var base = fixture.buildFoundConcealedColony("pirates");
                var ownColony = fixture.buildVisibleColony("pirates");

                fixture.placeColoniesInSystem(base, ownColony);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildColony(base), buildColony(ownColony))))
                    .containsExactly(buildColony(ownColony));
            }

            @Test
            void keepsAConcealedColonyARivalSColonyCanSee() {
                // The other half of the pair, posed on one base and one neighbour so the owners are
                // the only thing that has moved: a faction that is not keeping the secret has every
                // reason to mention what is sitting in the system with it.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var base = fixture.buildFoundConcealedColony("pirates");
                var rivalColony = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(base, rivalColony);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildColony(base), buildColony(rivalColony))))
                    .containsExactly(buildColony(base), buildColony(rivalColony));
            }

            @Test
            void keepsAConcealedColonyARivalCanSeeAmongItsOwnFactionSNeighbours() {
                // Several owners settle this place, and the question is asked of the set rather than
                // of whichever colony was reached first: one rival among the base's own countrymen is
                // enough, and a fold that had collapsed to "somebody is here" or to "the first owner
                // found" would answer differently.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var base = fixture.buildFoundConcealedColony("pirates");
                var ownColony = fixture.buildVisibleColony("pirates");
                var rivalColony = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(base, ownColony, rivalColony);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf( buildColony(base), buildColony(ownColony), buildColony(rivalColony))))
                    .containsExactly(
                        buildColony(base), buildColony(ownColony), buildColony(rivalColony));
            }

            @Test
            void excludesAConcealedColonyOnlyAnAlliedFactionCouldVouchFor() {
                // The same silence as the base's own faction, for the same reason: an alliance is a
                // standing arrangement to act as one, and handing a partner's concealed base to a
                // third party is the thing it forbids. Posed on the pair that talks with no
                // alliance registered, so the membership is the only thing that has moved.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var base = fixture.buildFoundConcealedColony("pirates");
                var partnerColony = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(base, partnerColony);

                assertThat(knowing(fixture, BOTH_GATES_ON, buildAllianceOf("pirates", "hegemony"))
                        .readKnownColonies(
                            buildColoniesOf(buildColony(base), buildColony(partnerColony))))
                    .containsExactly(buildColony(partnerColony));
            }

            @Test
            void excludesAConcealedColonyItsOwnFactionSheltersAmongAllies() {
                // The rule the alliance widened, unchanged underneath it: an owner is passed over
                // for being the owner, not for being allied with itself. Posed with an alliance
                // standing so a widening that had swallowed the identity test would show here.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var base = fixture.buildFoundConcealedColony("pirates");
                var ownColony = fixture.buildVisibleColony("pirates");

                fixture.placeColoniesInSystem(base, ownColony);

                assertThat(knowing(fixture, BOTH_GATES_ON, buildAllianceOf("pirates", "hegemony"))
                        .readKnownColonies(
                            buildColoniesOf(buildColony(base), buildColony(ownColony))))
                    .containsExactly(buildColony(ownColony));
            }

            @Test
            void keepsAConcealedColonyAFactionOutsideItsAllianceCanSee() {
                // The other half of the pair: an alliance the base's owner is not in silences
                // nobody, so the rule answers exactly as it does with no alliances at all.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var base = fixture.buildFoundConcealedColony("pirates");
                var rivalColony = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(base, rivalColony);

                assertThat(knowing(
                        fixture,
                        BOTH_GATES_ON,
                        buildAllianceOf("hegemony", "persean_league"))
                        .readKnownColonies(
                            buildColoniesOf(buildColony(base), buildColony(rivalColony))))
                    .containsExactly(buildColony(base), buildColony(rivalColony));
            }

            @Test
            void keepsAConcealedColonyOneUnalliedSettlerCanSeeAmongItsPartners() {
                // Asked of the whole set rather than of whichever colony was reached first: one
                // faction outside the alliance is enough, however many partners stand around it.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var base = fixture.buildFoundConcealedColony("pirates");
                var partnerColony = fixture.buildVisibleColony("hegemony");
                var rivalColony = fixture.buildVisibleColony("tritachyon");

                fixture.placeColoniesInSystem(base, partnerColony, rivalColony);

                assertThat(knowing(fixture, BOTH_GATES_ON, buildAllianceOf("pirates", "hegemony"))
                        .readKnownColonies(buildColoniesOf(
                            buildColony(base),
                            buildColony(partnerColony),
                            buildColony(rivalColony))))
                    .containsExactly(
                        buildColony(base), buildColony(partnerColony), buildColony(rivalColony));
            }

            @Test
            void keepsAConcealedColonyThePlayerHasSeenWhateverTheAllianceSays() {
                // An alliance says who would speak, and nothing about what the player has been to
                // look at. So the sighting route is untouched by it, which is what keeps a partner's
                // silence from taking back knowledge the player earned.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var base = fixture.buildFoundConcealedColony("pirates");
                var partnerColony = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(base, partnerColony);
                fixture.markColoniesAsSighted(base);

                assertThat(knowing(fixture, BOTH_GATES_ON, buildAllianceOf("pirates", "hegemony"))
                        .readKnownColonies(
                            buildColoniesOf(buildColony(base), buildColony(partnerColony))))
                    .containsExactly(buildColony(base), buildColony(partnerColony));
            }

            @Test
            void keepsADerelictWhicheverAllianceSettlesTheSystemAroundIt() {
                // Nobody holds a derelict and nobody joins an alliance, so the widened comparison
                // reaches a derelict exactly as the bare owner one did - it is vouched for by
                // whoever is there, allied or not.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var derelict = fixture.buildDerelictStation();
                var alliedColony = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(derelict, alliedColony);

                assertThat(knowing(fixture, BOTH_GATES_ON, buildAllianceOf("hegemony", "pirates"))
                        .readKnownColonies(
                            buildColoniesOf(buildDerelict(derelict), buildColony(alliedColony))))
                    .containsExactly(buildDerelict(derelict), buildColony(alliedColony));
            }

            @Test
            void keepsADerelictWhicheverFactionSettlesTheSystemAroundIt() {
                // A derelict is held by nobody, and nobody never settles a place - so the owner
                // comparison can never find its own owner among the settling ones, and the route
                // answers for a derelict exactly as it did before it read owners at all.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var derelict = fixture.buildDerelictStation();
                var pirateColony = fixture.buildVisibleColony("pirates");

                fixture.placeColoniesInSystem(derelict, pirateColony);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildDerelict(derelict), buildColony(pirateColony))))
                    .containsExactly(buildDerelict(derelict), buildColony(pirateColony));
            }

            @Test
            void excludesADerelictVouchedForOnlyByAnUnheldStation() {
                // The one arrangement in which the owner comparison reaches a derelict: a station no
                // faction holds that the economy lists anyway is read as kept, so it settles its
                // place - while falling to the same nobody the wreck beside it does. It vouches for
                // everything else here and not for that wreck, which is a derelict on the books
                // being no witness to the one drifting next to it.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var derelict = fixture.buildDerelictStation();
                var listedStation = fixture.buildOutpost(Factions.NEUTRAL);

                fixture.placeColoniesInSystem(derelict, listedStation);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildDerelict(derelict), buildOutpost(listedStation))))
                    .containsExactly(buildOutpost(listedStation));
            }

        }

        // The two routes that stand in for the fog rather than qualifying it, for the one kind
        // that has them: a collapsed colony the player has not surveyed, found on the word of
        // whoever else is in the system, or on the player having laid eyes on it. Every case here
        // poses a world the fog refuses outright, so nothing in the group could have passed by any
        // other arm - and the two part company at the survey bar, which reaches the player's own
        // route alone.
        @Nested
        class ReportRoute {

            @Test
            void keepsAnUnsurveyedDecivilisedWorldARivalColonyCanSee() {
                // The asymmetry the route exists to close: the Hegemony's colony is drawn in a
                // system the player has never entered, and the collapsed world in the next orbit -
                // which
                // everyone living there can see - was not.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();
                var neighbour = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(decivilisedWorld, neighbour);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildUngovernedColony(decivilisedWorld), buildColony(neighbour))))
                    .containsExactly(buildUngovernedColony(decivilisedWorld), buildColony(neighbour));
            }

            @ParameterizedTest
            @EnumSource(value = SurveyLevel.class, names = {"SEEN", "PRELIMINARY", "FULL"})
            void keepsAnUnsurveyedDecivilisedWorldARivalColonyCanSeeAtEveryBar(SurveyLevel bar) {
                // The claim the split is for: the bar is what the player asks of their own
                // instruments, and a neighbour's word is not a reading anybody's instruments took -
                // so raising it may not silence the people living in the system.
                // NONE is left out because the fog admits the world outright there, which would
                // pose nothing about this route at all.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();
                var neighbour = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(decivilisedWorld, neighbour);

                assertThat(knowing(fixture, askingForASurveyOf(bar)).readKnownColonies(buildColoniesOf(buildUngovernedColony(decivilisedWorld), buildColony(neighbour))))
                    .containsExactly(buildUngovernedColony(decivilisedWorld), buildColony(neighbour));
            }

            @Test
            void withholdsAnUnsurveyedDecivilisedWorldAloneInItsSystem() {
                // Nobody is there to have seen it, so the route has nothing to report and the world
                // falls back to the survey the player has not made.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();

                fixture.placeColoniesInSystem(decivilisedWorld);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildUngovernedColony(decivilisedWorld))))
                    .isEmpty();
            }

            @ParameterizedTest
            @EnumSource(value = SurveyLevel.class, names = {"SEEN", "PRELIMINARY", "FULL"})
            void withholdsAnUnsurveyedDecivilisedWorldVouchedForOnlyByAnUnheldStation(SurveyLevel bar) {
                // Owner-awareness reaching the route without a line of its own, and what ungating
                // it must not have cost. A collapsed colony falls to neutral as it dies, and so
                // does a station no faction holds that the economy lists anyway - so the only
                // settler here shares the world's own owner and is passed over, exactly as a
                // faction's own colony is beside its concealed base. Posed at every bar, a route
                // that no longer reads one having no bar left to be narrowed by instead.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();
                var listedStation = fixture.buildOutpost(Factions.NEUTRAL);

                fixture.placeColoniesInSystem(decivilisedWorld, listedStation);

                assertThat(knowing(fixture, askingForASurveyOf(bar)).readKnownColonies(buildColoniesOf(buildUngovernedColony(decivilisedWorld), buildOutpost(listedStation))))
                    .containsExactly(buildOutpost(listedStation));
            }

            @Test
            void withholdsAnUnsurveyedDecivilisedWorldOnlyAnAlliedSettlerCouldVouchFor() {
                // The alliance rule reaching this route too, posed at a bar the player's own
                // sighting could not clear so the neighbour is the only thing being asked. A
                // partner of the world's own owner is passed over exactly as the owner is, which
                // is the widened comparison and not a second rule written for this route.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();
                var partnerColony = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(decivilisedWorld, partnerColony);

                assertThat(knowing(
                        fixture,
                        ASKING_A_PRELIMINARY_SURVEY,
                        buildAllianceOf(Factions.NEUTRAL, "hegemony"))
                        .readKnownColonies(buildColoniesOf(
                            buildUngovernedColony(decivilisedWorld), buildColony(partnerColony))))
                    .containsExactly(buildColony(partnerColony));
            }

            @Test
            void keepsAnUnsurveyedDecivilisedWorldSeenWhereItStands() {
                // The recorded half of the same route, and what keeps the world on the map after
                // the neighbour that reported it has itself collapsed: an observation naming this
                // system finds the world with nobody left in it.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();

                fixture.placeColoniesInSystem(decivilisedWorld);
                fixture.markColoniesAsSighted(decivilisedWorld);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildUngovernedColony(decivilisedWorld))))
                    .containsExactly(buildUngovernedColony(decivilisedWorld));
            }

            @ParameterizedTest
            @EnumSource(value = SurveyLevel.class, names = {"PRELIMINARY", "FULL"})
            void withholdsADecivilisedWorldSeenFromAFlyPastWhereMoreThanASightingIsAskedFor(
                    SurveyLevel bar) {

                // The half the bar does decide, and the pair to the case above: the same world,
                // seen and nothing more, is withheld the moment the player asks for readings off
                // it. Nobody is in the system, so the neighbour route has nothing to say and the
                // player's own sighting is the whole of what is on offer.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();

                fixture.placeColoniesInSystem(decivilisedWorld);
                fixture.markColoniesAsSighted(decivilisedWorld);

                assertThat(knowing(fixture, askingForASurveyOf(bar)).readKnownColonies(buildColoniesOf(buildUngovernedColony(decivilisedWorld))))
                    .isEmpty();
            }

            @Test
            void keepsASurveyedDecivilisedWorldWhereMoreThanASightingIsAskedFor() {
                // The fog arm is untouched by any of this: a world the player has actually surveyed
                // that far is admitted at a bar no report could ever reach.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var decivilisedWorld = fixture.buildDecivilisedWorld();

                fixture.placeColoniesInSystem(decivilisedWorld);

                assertThat(knowing(fixture, ASKING_A_FULL_SURVEY).readKnownColonies(buildColoniesOf(buildUngovernedColony(decivilisedWorld))))
                    .containsExactly(buildUngovernedColony(decivilisedWorld));
            }

            @Test
            void letsADecivilisedWorldThisRouteFoundSettleNothing() {
                // The route finds a world and grants it no voice, which is what keeps the two
                // passes an ordering rather than a cycle: the collapsed colony is on the map and
                // the derelict drifting beside it stays held back, having nobody there to report it.
                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();
                var derelict = fixture.buildDerelictStation();

                fixture.placeColoniesInSystem(decivilisedWorld, derelict);
                fixture.markColoniesAsSighted(decivilisedWorld);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildUngovernedColony(decivilisedWorld), buildDerelict(derelict))))
                    .containsExactly(buildUngovernedColony(decivilisedWorld));
            }
        }

        // The colonies no gate is about: a kind the rule's gates do not cover, and a gate the rule
        // was never given. Neither needs revealing, so neither route is consulted at all.
        @Nested
        class UngatedColonies {

            @Test
            void keepsADerelictUnderItsOwnGateOff() {

                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var derelict = fixture.buildDerelictStation();

                fixture.placeColoniesInSystem(derelict);

                assertThat(knowing(fixture, ONLY_HIDDEN_GATED).readKnownColonies(buildColoniesOf(buildDerelict(derelict))))
                    .containsExactly(buildDerelict(derelict));
            }

            @Test
            void keepsAConcealedColonyUnderItsOwnGateOff() {

                var fixture = new ColonyKnowledgeFixture("kumari_kandam");
                var exoship = fixture.buildFoundConcealedColony("rat_exotech");

                fixture.placeColoniesInSystem(exoship);

                assertThat(knowing(fixture, ONLY_STATIONS_GATED).readKnownColonies(buildColoniesOf(buildColony(exoship))))
                    .containsExactly(buildColony(exoship));
            }

            @Test
            void keepsAnOrdinaryColonyUnderBothGates() {
                // Neither gate is about an open colony somebody lives on, so the gates that hold the
                // other two kinds back must leave this one exactly where the fog put it.
                var fixture = new ColonyKnowledgeFixture("corvus");
                var colony = fixture.buildVisibleColony("hegemony");

                fixture.placeColoniesInSystem(colony);

                assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(buildColoniesOf(buildColony(colony))))
                    .containsExactly(buildColony(colony));
            }
        }
    }

    @Nested
    class HasKnownColony {

        @Test
        void answersFalseForASystemHoldingNothing() {
            assertThat(observing().hasKnownColony(Colonies.NONE))
                .isFalse();
        }

        @Test
        void answersTrueForAnOrdinaryColony() {

            var fixture = new ColonyKnowledgeFixture("corvus");

            assertThat(knowing(fixture, ColonyVisibility.BASE_FOG).hasKnownColony(buildColoniesOf(buildColony(fixture.buildVisibleColony("hegemony")))))
                .isTrue();
        }

        @Test
        void answersTrueForAConcealedColonyThePlayerHasFound() {
            // A raided base is concealed for good and plainly known, so an emptiness read gated on
            // concealment would call its system empty while the player is standing in it.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var base = fixture.buildFoundConcealedColony("pirates");

            assertThat(knowing(fixture, ColonyVisibility.BASE_FOG).hasKnownColony(buildColoniesOf(buildColony(base))))
                .isTrue();
        }

        @Test
        void answersFalseForAColonyThePlayerHasNotFound() {
            // Reporting its system as occupied is itself the tell that something is hiding there.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var base = fixture.buildUndiscoveredConcealedColony("pirates");

            assertThat(knowing(fixture, ColonyVisibility.BASE_FOG).hasKnownColony(buildColoniesOf(buildColony(base))))
                .isFalse();
        }

        @Test
        void answersFalseForAnOpenColonyOnAnEntityThePlayerHasNotFound() {
            // The derelict-station shape again, asked of the emptiness read. A system holding
            // nothing but an undiscovered derelict reads as empty, which is what the player has
            // any means of knowing about it.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildUndiscoveredOpenColony(Factions.NEUTRAL);

            assertThat(knowing(fixture, ColonyVisibility.BASE_FOG).hasKnownColony(buildColoniesOf(buildColony(derelict))))
                .isFalse();
        }

        @Test
        void answersTrueForAColonyThePlayerHasNotFoundUnderTheReveal() {

            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var base = fixture.buildUndiscoveredConcealedColony("pirates");

            assertThat(knowing(fixture, REVEAL_UNDISCOVERED).hasKnownColony(buildColoniesOf(buildColony(base))))
                .isTrue();
        }

        @Test
        void agreesWithTheProjectionItAsksTheEmptinessOf() {
            // The claim the second read rests on: not materialising the list must not change the
            // answer, and a set mixing a fogged colony with a visible one is where a filter that
            // had drifted between the two reads would show it.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var fogged = fixture.buildUndiscoveredConcealedColony("pirates");
            var visible = fixture.buildVisibleColony("independent");

            var foggedOnly = buildColoniesOf(buildColony(fogged));
            var mixed = buildColoniesOf(buildColony(fogged), buildColony(visible));

            assertThat(knowing(fixture, ColonyVisibility.BASE_FOG).readKnownColonies(foggedOnly))
                .isEmpty();
            assertThat(knowing(fixture, ColonyVisibility.BASE_FOG).hasKnownColony(foggedOnly))
                .isFalse();

            assertThat(knowing(fixture, ColonyVisibility.BASE_FOG).readKnownColonies(mixed))
                .containsExactly(buildColony(visible));
            assertThat(knowing(fixture, ColonyVisibility.BASE_FOG).hasKnownColony(mixed))
                .isTrue();
        }

        @Test
        void agreesWithTheProjectionOverASetHoldingOnlyDerelicts() {
            // Nothing here settles the place, so both reads have to reach their answer through
            // the gate itself rather than through anything an ordinary colony vouched for.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildDerelictStation();

            fixture.placeColoniesInSystem(derelict);

            var colonies = buildColoniesOf(buildDerelict(derelict));

            assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(colonies))
                .isEmpty();
            assertThat(knowing(fixture, BOTH_GATES_ON).hasKnownColony(colonies))
                .isFalse();

            assertThat(knowing(fixture, ONLY_HIDDEN_GATED).readKnownColonies(colonies))
                .containsExactly(buildDerelict(derelict));
            assertThat(knowing(fixture, ONLY_HIDDEN_GATED).hasKnownColony(colonies))
                .isTrue();
        }

        @Test
        void agreesWithTheProjectionOverADerelictAnOutpostVouchesFor() {
            // A kept station has people on it, so it settles its place exactly as a colony does -
            // and what it vouches for is the derelict drifting beside it, which no gate would
            // otherwise admit here.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildDerelictStation();
            var outpost = fixture.buildOutpost("hegemony");

            fixture.placeColoniesInSystem(derelict, outpost);

            var colonies = buildColoniesOf(buildDerelict(derelict), buildOutpost(outpost));

            assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(colonies))
                .containsExactly(buildDerelict(derelict), buildOutpost(outpost));
            assertThat(knowing(fixture, BOTH_GATES_ON).hasKnownColony(colonies))
                .isTrue();
        }

        @Test
        void agreesWithTheProjectionOverADerelictAColonyVouchesFor() {
            // A settled place, where the derelict is admitted only by the colony beside it. Both
            // reads must take that settled reading before judging the gate, or they diverge.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildDerelictStation();
            var colony = fixture.buildVisibleColony("hegemony");

            fixture.placeColoniesInSystem(derelict, colony);

            var colonies = buildColoniesOf(buildDerelict(derelict), buildColony(colony));

            assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(colonies))
                .containsExactly(buildDerelict(derelict), buildColony(colony));
            assertThat(knowing(fixture, BOTH_GATES_ON).hasKnownColony(colonies))
                .isTrue();
        }
    }

    @Nested
    class ReadInhabitingColonies {

        @Test
        void keepsAStationAFactionKeeps() {
            // A kept station wears the derelict condition and is somebody's, so it is read as a
            // colony throughout: habitation counts it, where the derelict beside it in the case above
            // is counted by neither projection.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var outpost = fixture.buildOutpost("hegemony");

            fixture.placeColoniesInSystem(outpost);

            var colonies = buildColoniesOf(buildOutpost(outpost));

            assertThat(knowing(fixture, BOTH_GATES_ON).readInhabitingColonies(colonies))
                .containsExactly(buildOutpost(outpost));
        }

        @Test
        void excludesADerelictThePlayerHasSeen() {
            // The whole of the projection's reason for existing: the listing may name a derelict the
            // player has been past, and the place it orbits is still nobody's home.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildDerelictStation();

            fixture.placeColoniesInSystem(derelict);
            fixture.markColoniesAsSighted(derelict);

            var colonies = buildColoniesOf(buildDerelict(derelict));

            assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(colonies))
                .containsExactly(buildDerelict(derelict));
            assertThat(knowing(fixture, BOTH_GATES_ON).readInhabitingColonies(colonies))
                .isEmpty();
        }

        @Test
        void keepsAColonyTheDerelictBesideItDoesNotJoin() {
            // Both projections speak about one system and say different things: the listing names
            // the derelict the colony's own inhabitants can see, and habitation counts only the
            // colony.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var colony = fixture.buildVisibleColony("hegemony");
            var derelict = fixture.buildDerelictStation();

            fixture.placeColoniesInSystem(colony, derelict);

            var colonies = buildColoniesOf(buildColony(colony), buildDerelict(derelict));

            assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(colonies))
                .containsExactly(buildColony(colony), buildDerelict(derelict));
            assertThat(knowing(fixture, BOTH_GATES_ON).readInhabitingColonies(colonies))
                .containsExactly(buildColony(colony));
        }

        @Test
        void keepsEveryKnownColonyWhereNoDerelictIsPresent() {
            // Nothing is removed where nothing was ever a derelict, so a system of ordinary colonies
            // reads alike under either projection - in the set's own order.
            var fixture = new ColonyKnowledgeFixture("galatia");
            var ancyra = fixture.buildVisibleColony("hegemony");
            var academy = fixture.buildFoundConcealedColony("independent");

            fixture.placeColoniesInSystem(ancyra, academy);

            assertThat(knowing(fixture, BOTH_GATES_ON).readInhabitingColonies(buildColoniesOf(buildColony(ancyra), buildColony(academy))))
                .containsExactly(buildColony(ancyra), buildColony(academy));
        }

        @Test
        void keepsADecivilisedWorldAndLetsItSettleNothing() {
            // The world inhabits its place - somewhere people were is not empty space - while
            // vouching for nothing else standing there, nobody being left to speak. So the derelict
            // beside it stays unmentioned: a place is settled by the living, and the listing shows
            // exactly the collapsed world and not the wreck.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var decivilisedWorld = fixture.buildDecivilisedWorld();
            var derelict = fixture.buildDerelictStation();

            fixture.placeColoniesInSystem(decivilisedWorld, derelict);

            var colonies = buildColoniesOf(buildUngovernedColony(decivilisedWorld), buildDerelict(derelict));

            assertThat(knowing(fixture, BOTH_GATES_ON).readInhabitingColonies(colonies))
                .containsExactly(buildUngovernedColony(decivilisedWorld));
            assertThat(knowing(fixture, BOTH_GATES_ON).readKnownColonies(colonies))
                .containsExactly(buildUngovernedColony(decivilisedWorld));
        }

        @Test
        void keepsADecivilisedWorldANeighbourReported() {
            // Habitation reads the same found-test the listing does, so a world admitted on the
            // Hegemony's word counts as people living there exactly as a surveyed one does. Which
            // it should: the survivors on it are no less present for the player not having looked.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();
            var neighbour = fixture.buildVisibleColony("hegemony");

            fixture.placeColoniesInSystem(decivilisedWorld, neighbour);

            assertThat(knowing(fixture, BOTH_GATES_ON).readInhabitingColonies(buildColoniesOf(buildUngovernedColony(decivilisedWorld), buildColony(neighbour))))
                .containsExactly(buildUngovernedColony(decivilisedWorld), buildColony(neighbour));
        }

        @Test
        void excludesADerelictItsOwnGateHasLetThrough() {
            // Kind and gate answer separate questions. Turning the station gate off says the
            // player may be told about a derelict they have found; it does not put anybody aboard it.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildDerelictStation();

            fixture.placeColoniesInSystem(derelict);

            var colonies = buildColoniesOf(buildDerelict(derelict));

            assertThat(knowing(fixture, ONLY_HIDDEN_GATED).readKnownColonies(colonies))
                .containsExactly(buildDerelict(derelict));
            assertThat(knowing(fixture, ONLY_HIDDEN_GATED).readInhabitingColonies(colonies))
                .isEmpty();
        }

        @Test
        void excludesADerelictTheRevealHasLetThrough() {
            // The reveal is about the fog, not about who is aboard. Posed on an entity the player
            // has not found, so it is the reveal alone putting the derelict in the listing - and
            // habitation still declines it.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildUndiscoveredDerelictStation();

            fixture.placeColoniesInSystem(derelict);

            var colonies = buildColoniesOf(buildDerelict(derelict));

            assertThat(knowing(fixture, REVEAL_UNDISCOVERED).readKnownColonies(colonies))
                .containsExactly(buildDerelict(derelict));
            assertThat(knowing(fixture, REVEAL_UNDISCOVERED).readInhabitingColonies(colonies))
                .isEmpty();
        }

        @Test
        void followsTheGateThatHoldsAConcealedColonyBack() {
            // Habitation is the known set with a kind removed and no second reading of the rule,
            // so a colony the hidden gate withholds is absent from both and present in both once
            // the gate is off.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var exoship = fixture.buildFoundConcealedColony("rat_exotech");

            fixture.placeColoniesInSystem(exoship);

            var colonies = buildColoniesOf(buildColony(exoship));

            assertThat(knowing(fixture, BOTH_GATES_ON).readInhabitingColonies(colonies))
                .isEmpty();
            assertThat(knowing(fixture, ONLY_STATIONS_GATED).readInhabitingColonies(colonies))
                .containsExactly(buildColony(exoship));
        }

        @Test
        void readsAnUnstatedRuleAsTheFogAlone() {

            var fixture = new ColonyKnowledgeFixture("corvus");
            var colony = fixture.buildVisibleColony("hegemony");
            var undiscoveredColony = fixture.buildUndiscoveredOpenColony("tritachyon");

            fixture.placeColoniesInSystem(colony, undiscoveredColony);

            assertThat(knowing(fixture, null).readInhabitingColonies(buildColoniesOf(buildColony(colony), buildColony(undiscoveredColony))))
                .containsExactly(buildColony(colony));
        }
    }

    @Nested
    class HasInhabitingColony {

        @Test
        void answersFalseForASystemHoldingNothing() {
            assertThat(observing().hasInhabitingColony(Colonies.NONE))
                .isFalse();
        }

        @Test
        void answersTrueForAnOrdinaryColony() {

            var fixture = new ColonyKnowledgeFixture("corvus");

            assertThat(knowing(fixture, ColonyVisibility.BASE_FOG).hasInhabitingColony(buildColoniesOf(buildColony(fixture.buildVisibleColony("hegemony")))))
                .isTrue();
        }

        @Test
        void answersFalseForASystemHoldingOnlyADerelictThePlayerHasSeen() {
            // The reading the map turns on: a system the listing has something to say about, and
            // which is still empty space with a derelict in it.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildDerelictStation();

            fixture.placeColoniesInSystem(derelict);
            fixture.markColoniesAsSighted(derelict);

            var colonies = buildColoniesOf(buildDerelict(derelict));

            assertThat(knowing(fixture, BOTH_GATES_ON).hasKnownColony(colonies))
                .isTrue();
            assertThat(knowing(fixture, BOTH_GATES_ON).hasInhabitingColony(colonies))
                .isFalse();
        }

        @Test
        void answersTrueForASystemADerelictSharesWithAColony() {
            // A settled place, where the derelict must not be what carries the answer - the
            // colony beside it is.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildDerelictStation();
            var colony = fixture.buildVisibleColony("hegemony");

            fixture.placeColoniesInSystem(derelict, colony);

            assertThat(knowing(fixture, BOTH_GATES_ON).hasInhabitingColony(buildColoniesOf(buildDerelict(derelict), buildColony(colony))))
                .isTrue();
        }

        @Test
        void answersTrueForARememberedDecivilisedWorldWhoseNeighboursAreGone() {
            // What the register buys this reading: a system whose colonies have all since
            // collapsed still reads as somewhere people are, on an observation of that world made
            // while somebody was there to make it. The world is unsurveyed throughout, so the fog
            // is not what answers - and raising the bar past a sighting takes the answer away
            // again, the survey level reaching habitation exactly as it reaches the listing.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();

            fixture.placeColoniesInSystem(decivilisedWorld);
            fixture.markColoniesAsSighted(decivilisedWorld);

            var colonies = buildColoniesOf(buildUngovernedColony(decivilisedWorld));

            assertThat(knowing(fixture, BOTH_GATES_ON).hasInhabitingColony(colonies))
                .isTrue();
            assertThat(knowing(fixture, ASKING_A_PRELIMINARY_SURVEY).hasInhabitingColony(colonies))
                .isFalse();
        }

        @Test
        void answersFalseForADerelictTheRevealHasLetThrough() {
            // The emptiness question asked of the same case: the reveal admits the derelict to the
            // listing without making its place anybody's home.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildUndiscoveredDerelictStation();

            fixture.placeColoniesInSystem(derelict);

            var colonies = buildColoniesOf(buildDerelict(derelict));

            assertThat(knowing(fixture, REVEAL_UNDISCOVERED).hasKnownColony(colonies))
                .isTrue();
            assertThat(knowing(fixture, REVEAL_UNDISCOVERED).hasInhabitingColony(colonies))
                .isFalse();
        }

        @Test
        void readsAnUnstatedRuleAsTheFogAlone() {
            // No reveal appears out of a missing argument: a colony the player has not found does
            // not inhabit its place for the rule having gone unstated.
            var fixture = new ColonyKnowledgeFixture("corvus");
            var undiscoveredColony = fixture.buildUndiscoveredOpenColony("tritachyon");

            fixture.placeColoniesInSystem(undiscoveredColony);

            assertThat(knowing(fixture, null).hasInhabitingColony(buildColoniesOf(buildColony(undiscoveredColony))))
                .isFalse();
        }

        @Test
        void followsTheGateThatHoldsAConcealedColonyBack() {
            // A concealed colony settles nothing, so the gate is the whole of the answer here -
            // and the emptiness question has to consult it exactly as the listing does.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var exoship = fixture.buildFoundConcealedColony("rat_exotech");

            fixture.placeColoniesInSystem(exoship);

            var colonies = buildColoniesOf(buildColony(exoship));

            assertThat(knowing(fixture, BOTH_GATES_ON).hasInhabitingColony(colonies))
                .isFalse();
            assertThat(knowing(fixture, ONLY_STATIONS_GATED).hasInhabitingColony(colonies))
                .isTrue();
        }

        @Test
        void agreesWithTheProjectionItAsksTheEmptinessOf() {
            // The claim the short-circuit rests on: skipping the list must not change the answer,
            // and a set mixing a derelict with a colony the fog withholds is where a short-circuit
            // that had drifted from the projection would show it.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildDerelictStation();
            var undiscoveredColony = fixture.buildUndiscoveredOpenColony("hegemony");

            fixture.placeColoniesInSystem(derelict, undiscoveredColony);
            fixture.markColoniesAsSighted(derelict, undiscoveredColony);

            var derelictOnly = buildColoniesOf(buildDerelict(derelict));
            var mixed = buildColoniesOf(
                buildDerelict(derelict),
                buildColony(undiscoveredColony));

            assertThat(knowing(fixture, BOTH_GATES_ON).readInhabitingColonies(derelictOnly))
                .isEmpty();
            assertThat(knowing(fixture, BOTH_GATES_ON).hasInhabitingColony(derelictOnly))
                .isFalse();

            assertThat(knowing(fixture, BOTH_GATES_ON).readInhabitingColonies(mixed))
                .isEmpty();
            assertThat(knowing(fixture, BOTH_GATES_ON).hasInhabitingColony(mixed))
                .isFalse();
        }
    }

    @Nested
    class ReadGatedColonies {

        @Test
        void yieldsTheShapesAGateHoldsBack() {

            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildDerelictStation();
            var base = fixture.buildFoundConcealedColony("pirates");

            fixture.placeColoniesInSystem(derelict, base);

            assertThat(observing().readGatedColonies(buildColoniesOf(buildDerelict(derelict), buildColony(base))))
                .containsExactly(buildDerelict(derelict), buildColony(base));
        }

        @Test
        void passesOverAColonyHeldInTheOpen() {
            // What keeps the register from holding an entry per colony in the sector. An open
            // colony the economy lists is permanently in the sector's own sight, so no gate ever
            // asks about it and an observation of one would answer nothing.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var colony = fixture.buildVisibleColony("hegemony");

            fixture.placeColoniesInSystem(colony);

            assertThat(observing().readGatedColonies(buildColoniesOf(buildColony(colony))))
                .isEmpty();
        }

        @Test
        void yieldsADerelictThePlayerHasNotFound() {
            // Being somewhere is seeing what is in it, so this read consults no fog. The colony
            // still needs finding before it is shown, the gate being a condition on top of the fog
            // rather than an alternative to it.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildUndiscoveredDerelictStation();

            fixture.placeColoniesInSystem(derelict);

            assertThat(observing().readGatedColonies(buildColoniesOf(buildDerelict(derelict))))
                .containsExactly(buildDerelict(derelict));
        }

        @Test
        void passesOverADecivilisedWorldTheInhabitantsRouteWouldRecord() {
            // The deliberate gap between the two observation reads. Arriving here is the very act
            // vanilla stamps a permanent survey level for, so an entry would restate what the fog
            // already answers - at the cost of one per collapsed world in every system entered.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();

            fixture.placeColoniesInSystem(decivilisedWorld);

            assertThat(observing().readGatedColonies(buildColoniesOf(buildUngovernedColony(decivilisedWorld))))
                .isEmpty();
        }
    }

    @Nested
    class ReadColoniesObservedByInhabitants {

        @Test
        void yieldsADerelictStandingBesideAnotherFactionsColony() {
            // The route's own case: a derelict in orbit over an inhabited world is common knowledge
            // there, whether or not the player has ever been near it.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildDerelictStation();
            var neighbour = fixture.buildVisibleColony("hegemony");

            fixture.placeColoniesInSystem(derelict, neighbour);

            assertThat(observing().readColoniesObservedByInhabitants(buildColoniesOf(buildDerelict(derelict), buildColony(neighbour))))
                .containsExactly(buildDerelict(derelict));
        }

        @Test
        void yieldsNothingForADerelictAloneInItsSystem() {

            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildDerelictStation();

            fixture.placeColoniesInSystem(derelict);

            assertThat(observing().readColoniesObservedByInhabitants(buildColoniesOf(buildDerelict(derelict))))
                .isEmpty();
        }

        @Test
        void yieldsNothingForAConcealedBaseItsOwnFactionShelters() {
            // Owner-awareness, carried into the write: the pirates do not announce their own base,
            // so nothing is recorded that the rule would decline to credit.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var base = fixture.buildFoundConcealedColony("pirates");
            var ownColony = fixture.buildVisibleColony("pirates");

            fixture.placeColoniesInSystem(base, ownColony);

            assertThat(observing().readColoniesObservedByInhabitants(buildColoniesOf(buildColony(base), buildColony(ownColony))))
                .isEmpty();
        }

        @Test
        void yieldsNothingForAColonyNoGateIsAbout() {
            // Two open colonies watching each other. Neither is gated, so neither is worth an
            // entry however plainly the other can see it.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var colony = fixture.buildVisibleColony("hegemony");
            var neighbour = fixture.buildVisibleColony("tritachyon");

            fixture.placeColoniesInSystem(colony, neighbour);

            assertThat(observing().readColoniesObservedByInhabitants(buildColoniesOf(buildColony(colony), buildColony(neighbour))))
                .isEmpty();
        }

        @Test
        void yieldsNothingForAConcealedBaseAnAlliedFactionWouldNotAnnounce() {
            // The rule's own silence, carried into the write: an observation an ally alone would
            // have made is one the rule declines to credit, so recording it would put an
            // announcement nobody made permanently into a save.
            registerAllianceOf("pirates", "hegemony");

            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var base = fixture.buildFoundConcealedColony("pirates");
            var partnerColony = fixture.buildVisibleColony("hegemony");

            fixture.placeColoniesInSystem(base, partnerColony);

            assertThat(observing().readColoniesObservedByInhabitants(
                    buildColoniesOf(buildColony(base), buildColony(partnerColony))))
                .isEmpty();
        }

        @Test
        void yieldsAConcealedBaseAFactionOutsideItsAllianceAnnounces() {
            // The other half of that pair, on the same shape: an alliance the base's owner is not
            // in silences nobody, so the observation is recorded as it always was.
            registerAllianceOf("hegemony", "persean_league");

            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var base = fixture.buildFoundConcealedColony("pirates");
            var rivalColony = fixture.buildVisibleColony("hegemony");

            fixture.placeColoniesInSystem(base, rivalColony);

            assertThat(observing().readColoniesObservedByInhabitants(
                    buildColoniesOf(buildColony(base), buildColony(rivalColony))))
                .containsExactly(buildColony(base));
        }

        @Test
        void yieldsNothingWhereTheOnlySettlerIsAColonyThePlayerHasNotFound() {
            // The fog is read here as it is read by the rule, and no reveal can reach it: a
            // written observation outlives the setting that let it be made, so one made under a
            // reveal could not be taken back by turning the reveal off again.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var derelict = fixture.buildDerelictStation();
            var undiscoveredNeighbour = fixture.buildUndiscoveredOpenColony("hegemony");

            fixture.placeColoniesInSystem(derelict, undiscoveredNeighbour);

            assertThat(observing().readColoniesObservedByInhabitants(buildColoniesOf(buildDerelict(derelict), buildColony(undiscoveredNeighbour))))
                .isEmpty();
        }

        @Test
        void yieldsADecivilisedWorldARivalColonyCanSee() {
            // The report route's own entry, and the reason this read is not the gated one. No gate
            // is about a collapsed world; what the neighbours can see of it is nonetheless the whole
            // of why the map shows it, so the observation has to be written down.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();
            var neighbour = fixture.buildVisibleColony("hegemony");

            fixture.placeColoniesInSystem(decivilisedWorld, neighbour);

            assertThat(observing().readColoniesObservedByInhabitants(
                    buildColoniesOf(buildUngovernedColony(decivilisedWorld), buildColony(neighbour))))
                .containsExactly(buildUngovernedColony(decivilisedWorld));
        }

        @Test
        void yieldsNothingForADecivilisedWorldAloneInItsSystem() {

            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();

            fixture.placeColoniesInSystem(decivilisedWorld);

            assertThat(observing().readColoniesObservedByInhabitants(
                    buildColoniesOf(buildUngovernedColony(decivilisedWorld))))
                .isEmpty();
        }

        @Test
        void yieldsADecivilisedWorldThoughTheRuleAsksForMoreThanASighting() {
            // What is written down is keyed on the kind and never on the knob. A player who ran at
            // a stricter bar for a hundred cycles must not come back down to a hole in the register
            // for those years - a setting says what may be shown and never what was seen.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var decivilisedWorld = fixture.buildUnsurveyedDecivilisedWorld();
            var neighbour = fixture.buildVisibleColony("hegemony");

            fixture.placeColoniesInSystem(decivilisedWorld, neighbour);

            assertThat(knowing(fixture, ASKING_A_PRELIMINARY_SURVEY).readColoniesObservedByInhabitants(
                    buildColoniesOf(buildUngovernedColony(decivilisedWorld), buildColony(neighbour))))
                .containsExactly(buildUngovernedColony(decivilisedWorld));
        }
    }

    @Nested
    class Over {

        @Test
        void readsTheAlliancesStandingWhenEachPassOpens() {
            // Alliances form and dissolve while a campaign runs, and a pass folds them where it
            // opens the register - so a partnership that ends between two passes stops silencing
            // its witness at the second, rather than at whatever point a snapshot was taken.
            registerAllianceOf("pirates", "hegemony");

            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var base = fixture.buildFoundConcealedColony("pirates");
            var neighbourColony = fixture.buildVisibleColony("hegemony");
            var colonies = buildColoniesOf(buildColony(base), buildColony(neighbourColony));

            fixture.placeColoniesInSystem(base, neighbourColony);

            assertThat(ColonyKnowledge.over(null, BOTH_GATES_ON).readKnownColonies(colonies))
                .containsExactly(buildColony(neighbourColony));

            registerAllianceOf("hegemony", "persean_league");

            assertThat(ColonyKnowledge.over(null, BOTH_GATES_ON).readKnownColonies(colonies))
                .containsExactly(buildColony(base), buildColony(neighbourColony));
        }

        @Test
        void readsNobodyAsAlliedWhereNothingIsRegistered() {
            // What an install without the mod that keeps alliances answers: every other faction
            // present speaks, which is the rule exactly as it stood before alliances were read.
            var fixture = new ColonyKnowledgeFixture("kumari_kandam");
            var base = fixture.buildFoundConcealedColony("pirates");
            var neighbourColony = fixture.buildVisibleColony("hegemony");

            fixture.placeColoniesInSystem(base, neighbourColony);

            assertThat(ColonyKnowledge.over(null, BOTH_GATES_ON).readKnownColonies(
                    buildColoniesOf(buildColony(base), buildColony(neighbourColony))))
                .containsExactly(buildColony(base), buildColony(neighbourColony));
        }
    }

    // Every setting of the two gates, the reveal left off throughout. What a case poses when it
    // is claiming the answer does not depend on the gates at all - which is the shape of the
    // conjunction, where the fog refuses before a gate is ever consulted.
    static Stream<ColonyVisibility> buildEveryGateCombination() {
        return Stream.of(
            BOTH_GATES_ON,
            ONLY_STATIONS_GATED,
            ONLY_HIDDEN_GATED,
            ColonyVisibility.BASE_FOG);
    }

    // The shipped rule with the survey bar moved and nothing else, for a case claiming which of
    // the routes into a collapsed world the bar reaches. Both gates stay in force so that a bar
    // the case did not mean to pose cannot arrive with a gate lifted beside it.
    private static ColonyVisibility askingForASurveyOf(SurveyLevel bar) {
        return new ColonyVisibility(NOTHING_REVEALED, bar, EVERY_GATE);
    }

    // A colony set built straight from colonies, for a case about the projection rather than
    // about the walk that gathers the set. Nothing in it has been seen, which is what a colony
    // standing in a system reads as until a case says otherwise.
    private static Colonies buildColoniesOf(Colony... colonies) {
        return new Colonies(List.of(colonies));
    }

    // A colony standing where no star system is - hyperspace, which mods put a few in. Stated here
    // rather than through the world fixture, whose whole subject is a system.
    private static void placeColonyOutsideAnySystem(MarketAPI colony) {

        when(colony.getContainingLocation())
            .thenReturn(mock(LocationAPI.class));
    }

    // The knowledge a case reads its projection under: the rule it poses, against the world's own
    // record of what has been observed. Paired here so a case cannot read one world's colonies
    // against another's observations.
    private static ColonyKnowledge knowing(
            ColonyKnowledgeFixture fixture,
            ColonyVisibility rule) {

        return new ColonyKnowledge(rule, fixture.getSightings());
    }

    // The same, among factions that stand together. Stated only where an alliance is what the case
    // is about, so every other case poses the sector's ordinary shape - nobody allied with anybody.
    private static ColonyKnowledge knowing(
            ColonyKnowledgeFixture fixture,
            ColonyVisibility rule,
            FactionAlliances alliances) {

        return new ColonyKnowledge(rule, fixture.getSightings(), alliances);
    }

    // The knowledge the observation reads are taken under - the fog alone, and no register. Being
    // somewhere is seeing what is in it, so neither read consults a rule or an observation.
    private static ColonyKnowledge observing() {
        return ColonyKnowledge.observingUnderTheFog();
    }

    // The market stood on the entity a composition root vouches for, which is the only thing that
    // parts a landmark from an identical concealed market. Vouched for here rather than in the case
    // so a case reads as posing a colony, and cleared after every test by the class's own teardown.
    private static MarketAPI registerAsOpenlyKnown(MarketAPI market) {

        registerTheAcademy();

        return standOnEntity(market, ACADEMY_ENTITY_ID);
    }

    // Somewhere people live, listed by the economy - the kind neither gate is about.
    private static Colony buildColony(MarketAPI market) {
        return new Colony(market, true);
    }

    // A derelict nobody has ever lived on: unlisted, as the routine that builds one leaves it -
    // which is half of what the kind read parts a derelict from a station somebody keeps on.
    private static Colony buildDerelict(MarketAPI market) {
        return new Colony(market, false);
    }

    // A world whose government has collapsed. Unlisted, the economy dropping a colony as it falls -
    // which is what such a world always reaches a reader as.
    private static Colony buildUngovernedColony(MarketAPI market) {
        return new Colony(market, false);
    }

    // A station a faction keeps, listed like the colony it is read as.
    private static Colony buildOutpost(MarketAPI market) {
        return new Colony(market, true);
    }
}
