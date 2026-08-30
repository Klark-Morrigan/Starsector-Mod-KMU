package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;

import kmlib.starsector.markets.DecivilisedMarkets;

import java.util.Set;

/**
 * The rule a colony set is shown under: what lifts the fog over a colony the player has not found
 * or read, and the gates that hold back the shapes the fog alone would leak.
 *
 * <p>The fog itself is not a knob and is not carried here. What is carried is everything that
 * <em>changes</em> that answer: a reveal that admits what has not been found, the survey the map
 * asks of a collapsed colony before it will name one, and - for the shapes a bare fog shows before
 * the player could plausibly have heard of them - a requirement that somebody have seen them where
 * they stand.
 *
 * <p>Passed as one value rather than as loose flags because all of it is read together wherever the
 * rule is applied. A surface handed one gate and not the other would show a derelict it had been
 * told to hold back, and a signature taking the parts in a row would say nothing about it either
 * way.
 *
 * <p>The three parts are three different types, which is what makes them un-transposable at a call
 * site: two adjacent booleans mean something quite different the wrong way round and still compile.
 * The gates are a set of names for the same reason, so which gate is which cannot be got wrong and
 * one added later needs no existing rule rewritten - {@link RevelationGate} says what each covers.
 *
 * <p>Each part reaches the one thing it names and no other. The reveal drops the discovery arm of
 * the fog and clears no gate beside it; the survey level decides one kind's fog and nothing else's;
 * a gate narrows and never widens, so leaving one out drops that shape back to the fog rather than
 * admitting anything the fog refuses. Which is what lets a player ask for exactly the thing they
 * meant and be shown nothing beside it.
 *
 * <p>The policy alone. What has actually been seen is the register beside it, and the two are
 * paired into {@link ColonyKnowledge} where a projection is taken - a rule that carried
 * observations would be a value nobody could state without a running save behind it.
 *
 * @param shouldIncludeUndiscoveredMarkets whether a colony on an entity the player has not found
 *                                         still counts - the "show all factions" reveal, which
 *                                         drops the discovery arm of the fog and no more
 * @param ungovernedColonySurveyLevel      how far a collapsed colony's world must have been
 *                                         surveyed before the map will name it; an unstated level
 *                                         reads as {@link DecivilisedMarkets#DEFAULT_SURVEY_LEVEL}
 * @param revelationGates                  the shapes that must have been revealed as well as
 *                                         found; a shape whose gate is absent is held to the fog
 *                                         alone
 */
public record ColonyVisibility(
    boolean shouldIncludeUndiscoveredMarkets,
    SurveyLevel ungovernedColonySurveyLevel,
    Set<RevelationGate> revelationGates) {

    /**
     * The fog alone: nothing admitted that has not been found, no collapsed colony named before it
     * has been encountered, and nothing held back beyond that. What a caller stating no rule of its
     * own is read as, since a gate nobody asked for must not appear out of an unstated argument.
     */
    public static final ColonyVisibility BASE_FOG = new ColonyVisibility(
        false,
        DecivilisedMarkets.DEFAULT_SURVEY_LEVEL,
        Set.of());

    /**
     * Reads an unstated survey level as the fog's own, and takes an immutable copy of the gates -
     * so a rule handed around a render pass cannot change under its readers, an unstated set cannot
     * hold anything back, and a missing level cannot widen what a collapsed colony shows.
     */
    public ColonyVisibility {
        ungovernedColonySurveyLevel = ungovernedColonySurveyLevel == null
            ? DecivilisedMarkets.DEFAULT_SURVEY_LEVEL
            : ungovernedColonySurveyLevel;
        revelationGates = revelationGates == null ? Set.of() : Set.copyOf(revelationGates);
    }
}
