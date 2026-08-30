package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.markets.Markets;

/**
 * What kind of place a colony is, as a map layer has to tell them apart: somewhere a faction runs,
 * a station somebody keeps, a derelict nobody ever lived on, or a colony whose government has
 * collapsed.
 *
 * <p>A classification drawn for the map rather than a shape the sector holds. Vanilla has one
 * derelict shape - a market carrying the abandoned-station condition - and the split into
 * {@link #OUTPOST} and {@link #SPACE_DERELICT} is made here because a cell must not paint as
 * settled for a hulk. {@link #isSettlingLocation} is the same story stated outright: nothing in
 * the game answers it, and it exists so a place can be judged settled for the purpose of revealing
 * whatever else is standing there. Which is why the kind lives with the layer that spends it,
 * over a colony set the library states without it.
 *
 * <p>The sector holds the first three under one market shape. A market carrying the derelict
 * condition is owned by some faction, is not condition-only, and is registered like any other, so
 * every ownership read admits it as a colony - while nobody is aboard the abandoned ones, they
 * support nothing, and a place holding only those is empty space with hulks in it. A reader that
 * cannot tell them apart says something false about the sector rather than merely drawing it oddly.
 *
 * <p>The fourth wears the opposite disguise. A colony that decivilises is stripped of its owner,
 * its industries and its economy listing, so every ownership read refuses it - and a place drawn as
 * empty because the people still on it are invisible to the selection says something equally
 * false. It is admitted on the one condition that marks it, and named here so nothing downstream
 * has to ask again what a collapsed colony is.
 *
 * <p>A kind rather than a boolean because the distinction was already known not to be binary, and
 * the fourth arrived exactly as expected. An enum admits a fifth without any reader changing shape,
 * where a boolean would have to be replaced.
 *
 * <p>Resolved once per pass, where the colony set is memoised, and read back off
 * {@link ColonyKnowledge} from there. Every reader downstream routes on it, and each re-deriving
 * it from the market would be that many independent statements of what a derelict is.
 */
public enum ColonyKind {

    /**
     * The general case for most held markets.
     */
    COLONY,

    /**
     * An abandoned station somebody is at: one a faction holds, or one the economy lists.
     */
    OUTPOST,

    /**
     * A colony whose government has collapsed: decivilised, with no stable ruling polity, and
     * still populated - survivors, bandits and looters, in vanilla's own account of the condition.
     *
     * <p>People are there, which is what parts it from a derelict and is why it inhabits its
     * place. Nobody there speaks for it, which is what parts it from a colony and is why it takes
     * no part in the political landscape: it holds nothing, claims nothing, weighs nothing, and
     * vouches for nothing else standing in the same place.
     */
    UNGOVERNED_COLONY,

    /**
     * An abandoned station nobody is at: held by neutral (or nobody), and unlisted.
     */
    SPACE_DERELICT;

    /**
     * Reads which kind of place a market stands for.
     *
     * <p>Positive identification only: a market wears the derelict shape because it says so
     * ({@link Markets#isAbandonedStation}), and everything else - including a market that reads as
     * nothing in particular, and a null one - is an ordinary colony.
     *
     * <p>The collapsed colony is tested first, and the order is what settles a market wearing both
     * marks. It is condition-only where a derelict is pointedly not, so vanilla builds neither
     * shape into the other; where a mod hangs the derelict condition on a condition-only shell,
     * being somewhere people still are is the more particular thing to say about it, and the one
     * that keeps those people on the map.
     *
     * <p>Which of the two derelict-shaped kinds it is then turns on whether anybody is there, and
     * two independent facts say so. A real owner is one, asked through
     * {@link Markets#isSettledColony} so that "is the owner somebody" is answered here exactly as
     * it is answered everywhere else. Registration with the economy is the other, and it is a
     * deliberate act: the routine that builds a derelict pointedly does not register one, so a
     * market wearing the condition and trading anyway was made economically real on purpose.
     * Either alone is enough, since they are two ways of saying the same thing rather than two
     * requirements - and reading only the first left a registered hulk taking a dominance weight
     * while counting toward nobody living there.
     *
     * <p>The listing arrives as an argument rather than being read off the market, because which
     * markets the economy lists is decided by the walk that selected this one - by identity
     * against the economy's own set - and asking the market itself would be a second answer free
     * to disagree with the one the colony carries.
     *
     * <p>The colony default is the safe direction rather than the tidy one. Misfiling a derelict
     * as a colony overstates a place by one hulk; misfiling a colony as a derelict erases a
     * settlement that is really there, taking its people with it.
     *
     * @param market            the market to classify; null yields {@link #COLONY}
     * @param isListedByEconomy whether the economy's own listing holds this very market, as the
     *                          walk that selected it decided
     * @return the kind of place the market stands for
     */
    public static ColonyKind resolveKind(MarketAPI market, boolean isListedByEconomy) {

        if (DecivilisedMarkets.isDecivilisedWorld(market)) {
            return UNGOVERNED_COLONY;
        }
        if (!Markets.isAbandonedStation(market)) {
            return COLONY;
        }
        return Markets.isSettledColony(market) || isListedByEconomy
            ? OUTPOST
            : SPACE_DERELICT;
    }

    /**
     * Whether a place of this kind settles its location - whether somebody there would both see
     * whatever else stands in it and have word of that reach the player.
     *
     * <p>Two conditions, and a collapsed colony fails the second while passing the first. People
     * are still on it, and they see what is in orbit; there is no polity, no comm directory and no
     * economy for what they see to travel through. Being populated is not the same as being heard
     * from, which is the whole reason this is asked of the kind rather than of habitation.
     *
     * <p>Asked of the kind rather than tested against a constant at the rule that uses it, so a
     * kind added later declares for itself whether its people can vouch for a neighbour instead of
     * being silently left out of a comparison written before it existed.
     *
     * @return true when somebody is there whose word about the location would reach the player
     */
    public boolean isSettlingLocation() {
        return this == COLONY || this == OUTPOST;
    }
}
