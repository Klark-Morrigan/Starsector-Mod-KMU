package kmu.maplayers.politicalmap.base;

import kmlib.starsector.relation.PlayerStanding;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.util.KmuStrings;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Ranks a political-map picker's blocs by where they stand with the player, and draws that standing
 * on each row.
 *
 * <p>A type of its own rather than another {@link BlocMetricSortMode} declaration, because the
 * standing is not a number either vocabulary's fold computes: it is a bloc-level fact read off the
 * sector, identical under every view, and a range rather than a single value. Declared as a metric it
 * would have to be summed onto both metrics records, asking two aggregators to carry something
 * neither aggregates and duplicating one fact into two payloads.
 *
 * <p>Being outside both vocabularies is also why it cannot fall through either one's numeric chain -
 * it has no chain of its own to name - so it breaks a level pair straight down
 * {@link BlocSortModeComposer#appendSharedTail}. The two ends of the range plus the name already
 * separate everything a player can tell apart.
 *
 * <p>The standing is read live at comparison and draw time rather than snapshotted when the picker's
 * rows were built: the picker read is memoised against settings and grouping, not reputation, so a
 * snapshot would hold the ranking still until some unrelated knob moved.
 *
 * @param <S> the calling vocabulary's metrics record, which this mode never reads - it ranks the same
 *            way whichever mechanic painted the map
 */
public final class BlocStandingSortMode<S extends BlocMetrics>
    implements ListSortMode<RankedBloc<S>> {

    // Frozen once shipped: renaming it silently resets every save that stored this ranking. One key
    // across all three views rather than one per vocabulary, so the choice survives a view switch the
    // way every other mode's does.
    private static final String PERSISTENCE_KEY = "player_standing";

    // Friendliest first, so the blocs a player deals with lead the list.
    private static final SortDirection DEFAULT_DIRECTION = SortDirection.DESCENDING;

    // The three cases as positions on one scale, which is what lets a single primary key rank them
    // together with the numbers. The player's own bloc is the point the scale is measured from, so it
    // sits above everything measured; a bloc nothing could be read for sits below everything measured,
    // since an unknown standing must not rank as though it were neutral.
    private static final int PLAYERS_OWN_POSITION = 2;
    private static final int MEASURED_POSITION = 1;
    private static final int UNREADABLE_POSITION = 0;

    // What a case with no number to show draws: the picker leaves the row's value column empty.
    private static final List<TextSpan> NO_RUNS = List.of();

    // How a positive reputation shows that it is one. Negatives already carry a minus, and neutral is
    // drawn plain - a signed nought would claim a direction the standing does not have.
    private static final String POSITIVE_SIGN = "+";

    private final BlocStandingReader standingReader;

    /**
     * @param standingReader where each listed bloc's standing is read from
     */
    public BlocStandingSortMode(BlocStandingReader standingReader) {
        this.standingReader = Objects.requireNonNull(standingReader, "standingReader");
    }

    /**
     * @return the one key every view stores this ranking under
     */
    @Override
    public String persistenceKey() {
        return PERSISTENCE_KEY;
    }

    /**
     * @return the drawn label for this mode's selector row, looked up against this mod's own strings
     */
    @Override
    public String resolveLabelText() {
        return KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_SORT_ATTITUDE);
    }

    /**
     * The standing drawn on a bloc's row: the signed reputation in the shade the game itself paints
     * that relation, so the value says which way the bloc leans before the number is read.
     *
     * <p>A bloc whose members disagree draws both ends of its range, each in its own colour, joined
     * rather than word-spaced so the pair reads as one value. A bloc whose ends coincide - a lone
     * faction, or an alliance that agrees - draws the single number they share. The two cases with no
     * standing on the scale draw nothing at all.
     *
     * @param bloc          the listed bloc
     * @param defaultColour the tone the rest of the row draws in, which the separator between the two
     *                      ends takes - it belongs to the value's punctuation rather than to either
     *                      relation
     * @return the standing's runs in reading order, or no runs where the bloc holds no number
     */
    @Override
    public List<TextSpan> resolveTrailingRuns(RankedBloc<S> bloc, Color defaultColour) {

        return standingReader.readBlocStanding(bloc.itemId()).selectByCase(
            measured -> resolveMeasuredRuns(measured, defaultColour),
            () -> NO_RUNS,
            () -> NO_RUNS);
    }

    /**
     * @return descending, so the friendliest bloc leads
     */
    @Override
    public SortDirection defaultDirection() {
        return DEFAULT_DIRECTION;
    }

    /**
     * The comparator that orders the picker's blocs under this mode in {@code direction}: the three
     * standing cases as one scale, then the low end of the range, then the high end, then the shared
     * tail.
     *
     * <p>The case ordering is part of the primary key, so a flipped direction reverses it along with
     * the numbers - the player's own bloc leads under the default and trails under the flip. Pinning
     * it to one end instead would make it the one row the direction control does not govern.
     *
     * @param direction the way the ranking runs - this mode's default, or the flipped opposite
     * @return the bloc comparator for this mode in the requested direction
     */
    @Override
    public Comparator<RankedBloc<S>> comparator(SortDirection direction) {

        Comparator<RankedBloc<S>> standingOrder = this::compareBlocsFriendliestFirst;

        if (direction != DEFAULT_DIRECTION) {
            standingOrder = standingOrder.reversed();
        }
        return BlocSortModeComposer.appendSharedTail(standingOrder);
    }

    // Where a standing sits on the one scale the three cases share. This is the ranking's exhaustive
    // gate: a fourth case joining the set breaks here rather than quietly landing on some other
    // position, and the number test below only ever sees pairs this placed together.
    private static int resolveScalePosition(BlocStanding standing) {

        return standing.selectByCase(
            measured -> MEASURED_POSITION,
            () -> PLAYERS_OWN_POSITION,
            () -> UNREADABLE_POSITION);
    }

    // Two standings, friendliest first: the case first, then the low end of the range, then the high
    // end. The low end leads because a bloc is only as friendly as its least friendly member - an
    // alliance holding one hostile is not ranked as though it were the ally inside it.
    private static int compareStandingsFriendliestFirst(BlocStanding left, BlocStanding right) {

        var casePosition = Integer.compare(
            resolveScalePosition(right),
            resolveScalePosition(left));

        if (casePosition != 0) {
            return casePosition;
        }
        // Only two measured standings have numbers to go on; the other two cases carry none, so a pair
        // sharing one of them is level here and falls to the shared tail.
        if (!(left instanceof BlocStanding.Measured leftRange)
            || !(right instanceof BlocStanding.Measured rightRange)) {
            return 0;
        }
        var lowEnd = Integer.compare(
            rightRange.lowest().reputation(),
            leftRange.lowest().reputation());

        return lowEnd != 0
            ? lowEnd
            : Integer.compare(rightRange.highest().reputation(), leftRange.highest().reputation());
    }

    // A measured range as the runs it draws: one number where the ends coincide, both ends around the
    // separator where they do not. Each end takes the colour its own relation resolved to, so the
    // range is painted in the shades the game itself uses for its two halves.
    private static List<TextSpan> resolveMeasuredRuns(
            BlocStanding.Measured range,
            Color defaultColour) {

        var lowestRun = resolveStandingRun(range.lowest());

        if (range.lowest().reputation() == range.highest().reputation()) {
            return List.of(lowestRun);
        }
        return List.of(
            lowestRun,
            new TextSpan(
                KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_SORT_ATTITUDE_RANGE_SEPARATOR),
                defaultColour).joinsPreviousRun(),
            resolveStandingRun(range.highest()).joinsPreviousRun());
    }

    // One end of a range as its drawn run: the signed reputation in that relation's own colour.
    private static TextSpan resolveStandingRun(PlayerStanding standing) {

        return new TextSpan(resolveSignedReputationText(standing.reputation()), standing.colour());
    }

    // The reputation with its sign shown, so a friendly bloc's value reads as a positive standing
    // rather than as a bare number sitting beside negative ones.
    private static String resolveSignedReputationText(int reputation) {

        return reputation > 0
            ? POSITIVE_SIGN + reputation
            : String.valueOf(reputation);
    }

    // Two blocs by the standings they hold right now, read at comparison time for the reason the
    // class Javadoc states.
    private int compareBlocsFriendliestFirst(RankedBloc<S> leftBloc, RankedBloc<S> rightBloc) {

        return compareStandingsFriendliestFirst(
            standingReader.readBlocStanding(leftBloc.itemId()),
            standingReader.readBlocStanding(rightBloc.itemId()));
    }
}
