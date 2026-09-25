package kmu.maplayers.politicalmap.dominance.standings;

/**
 * How far the heading over a listed row applies to the thing on it: a count out of a total.
 *
 * <p>A heading placed by disposition can be true of part of a bloc and false of the rest, and a row
 * drawn under it says so by stating how much of itself the heading took. That is what lets one bloc
 * be listed under two headings without either of them overreaching.
 *
 * <p>The one rule about when a fraction is worth drawing lives here: a count of nought and a count of
 * the whole both state exactly what the heading above already said, so neither is stated. Held as a
 * value rather than as two ints beside each other because both ends of the range are omissions, and
 * a rule restated at each row that draws one is a rule some of them can drift from.
 *
 * <p>Plain arithmetic with no Starsector types and nothing about how the count was arrived at - who
 * is at odds with whom is {@link kmu.maplayers.ownermap.holding.BlocFriendliness}'s answer, and what
 * a row counts out of is its caller's.
 *
 * @param count what the heading is true of
 * @param total what it is counted out of - a bloc's own membership on a bloc's row, the holder's on a
 *              faction's
 */
public record StandingFraction(
    int count,
    int total) {

    /**
     * What a row carries where there is no fraction to state at all - every block placed by
     * membership rather than by disposition. Nothing counted out of nothing, so the draw rule below
     * omits it without a second reading of what an absent fraction means.
     */
    public static final StandingFraction NOTHING_TO_STATE = new StandingFraction(0, 0);

    /**
     * Rejects a count that is not part of a whole, at construction, where the caller that worked it
     * out is still on the stack. A fraction says how far a heading reaches, and one outside its own
     * range says nothing a reader could act on.
     */
    public StandingFraction {

        if (count < 0 || total < 0 || count > total) {
            throw new IllegalArgumentException("a fraction counts part of a whole");
        }
    }

    /**
     * Whether the fraction is worth stating on the row - true only between the ends of its range.
     *
     * @return true where the heading is true of some of the row but not all of it
     */
    public boolean isStated() {
        return count > 0 && count < total;
    }
}
