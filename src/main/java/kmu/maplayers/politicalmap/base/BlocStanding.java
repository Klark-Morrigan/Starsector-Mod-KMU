package kmu.maplayers.politicalmap.base;

import kmlib.starsector.relation.PlayerStanding;

/**
 * Where a bloc stands with the player - the three positions a bloc can hold on that scale, named.
 *
 * <p>A bloc is one faction or several, so its standing is a range rather than a number: the lowest
 * and the highest of its members'. An alliance whose members disagree is then reported as
 * disagreeing, rather than averaged into a figure no member of it holds.
 *
 * <p>Sealed over three cases rather than stated as one record carrying a flag, because two of the
 * three have no number to carry and sit at opposite ends of the scale. The player's own bloc is the
 * point the scale is measured from, and a bloc nothing could be read for belongs past everything
 * that was read. One value standing for both would have to be asked a second question to learn
 * which end it belongs at, and nothing would keep the two answers agreeing.
 *
 * <p>Plain data over KMLib's per-faction reading, with no Starsector types of its own, so a rule
 * that ranks or draws a standing is exercised on hand-built values.
 */
public sealed interface BlocStanding {

    /**
     * The bloc the player's own faction belongs to. It is what every other standing is measured
     * from, so it holds the top of the scale and draws nothing: a number here would be the engine's
     * answer to a question nobody asked.
     */
    BlocStanding PLAYERS_OWN = new PlayersOwn();

    /**
     * A bloc none of whose members answered a standing at all - every id it names being one the
     * sector cannot look up. It draws nothing and holds the tail of the scale, since a bloc nothing
     * is known about is exactly what must not be ranked as though it were neutral.
     */
    BlocStanding UNREADABLE = new Unreadable();

    /**
     * A bloc whose members answered: the two ends of the range they hold, each carrying the colour
     * its own relation resolves to, so the ends are drawn in the shades the game itself paints them.
     *
     * <p>A bloc of one faction folds to a range whose ends coincide, which is what lets a lone
     * faction's value collapse to a single number without a case of its own.
     *
     * @param lowest  the least friendly member's standing
     * @param highest the most friendly member's standing
     */
    record Measured(
        PlayerStanding lowest,
        PlayerStanding highest) implements BlocStanding {
    }

    /** The shape behind {@link #PLAYERS_OWN}, carrying nothing beyond being that case. */
    record PlayersOwn() implements BlocStanding {
    }

    /** The shape behind {@link #UNREADABLE}, carrying nothing beyond being that case. */
    record Unreadable() implements BlocStanding {
    }
}
