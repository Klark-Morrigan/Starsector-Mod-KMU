package kmu.maplayers.politicalmap.base;

import kmlib.starsector.relation.PlayerStanding;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.SortDirection;
import kmlib.text.KmlibNumbers;

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
 * <p>An instance is built per picker read rather than published as a constant, which the metric modes
 * cannot do: it holds a reader bound to one sector and one grouping, and a constant would answer one
 * sector's ask off another's relations. That is safe only because the instance and the vocabulary it
 * is appended to are built together and never travel apart - a {@link
 * kmlib.starsector.ui.widgets.lists.ListSort} rejects a mode its own vocabulary does not hold, and
 * modes here are matched by identity. So a sort resolved against one read's vocabulary must not
 * outlive it: memoising a resolved sort across reads would fail on the next one, where re-resolving
 * against the read's own vocabulary always holds.
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

    // What the two ends of a key read as for a case that holds no range. Never compared against a
    // real reputation: only two keys level on the scale position reach the ends, and the two cases
    // filled this way each sit alone at their end of that scale.
    private static final int NO_REPUTATION = 0;

    // The whole ranking, friendliest first: the scale position, then the low end of the range, then
    // the high end - every key descending, so a higher position and a higher reputation both lead.
    // The low end leads the high because a bloc is only as friendly as its least friendly member: an
    // alliance holding one hostile is not ranked as though it were the ally inside it.
    private static final Comparator<StandingRankKey> FRIENDLIEST_FIRST_ORDER =
        Comparator.comparingInt(StandingRankKey::scalePosition)
            .thenComparingInt(StandingRankKey::lowestReputation)
            .thenComparingInt(StandingRankKey::highestReputation)
            .reversed();

    // What a case with no number to show draws: the picker leaves the row's value column empty.
    private static final List<TextSpan> NO_RUNS = List.of();

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
     * The drawn label for this mode's selector row, looked up against this mod's own strings.
     *
     * <p>It reads "Attitude" because that is the word the game itself puts in front of this exact
     * reading: the intel screen's faction panel draws the reputation level and number as
     * {@code "Attitude: Friendly (35 / 100)"}. Vanilla keeps "relationship" for the prose that
     * reports a change, so a column showing the number is an attitude and a line reporting a move in
     * it is a relationship. The code around this says "standing" throughout, which is the
     * mod-neutral noun for the value; the label is the player's word for it.
     *
     * @return the drawn label for this mode's selector row
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

    // A standing as the three numbers it ranks by. The sealed fold is the ranking's exhaustive gate:
    // a fourth case joining the set breaks here rather than quietly landing on some other position -
    // and folding once per standing is what keeps the comparison plain arithmetic over two keys,
    // instead of asking a second time which case each side turned out to be.
    private static StandingRankKey resolveRankKey(BlocStanding standing) {

        return standing.selectByCase(
            measured -> new StandingRankKey(
                MEASURED_POSITION,
                measured.lowest().reputation(),
                measured.highest().reputation()),
            () -> new StandingRankKey(PLAYERS_OWN_POSITION, NO_REPUTATION, NO_REPUTATION),
            () -> new StandingRankKey(UNREADABLE_POSITION, NO_REPUTATION, NO_REPUTATION));
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

    // One end of a range as its drawn run: the reputation in that relation's own colour, with its
    // sign shown so a friendly bloc's value reads as a positive standing rather than as a bare number
    // sitting beside negative ones. Neutral draws unsigned, this being a reading on a scale rather
    // than a movement along one.
    private static TextSpan resolveStandingRun(PlayerStanding standing) {

        return new TextSpan(
            KmlibNumbers.formatSignedNonZero(standing.reputation()),
            standing.colour());
    }

    // Two blocs by the standings they hold right now, read at comparison time for the reason the
    // class Javadoc states.
    private int compareBlocsFriendliestFirst(RankedBloc<S> leftBloc, RankedBloc<S> rightBloc) {

        return FRIENDLIEST_FIRST_ORDER.compare(
            resolveRankKey(standingReader.readBlocStanding(leftBloc.itemId())),
            resolveRankKey(standingReader.readBlocStanding(rightBloc.itemId())));
    }

    /**
     * One standing flattened to the numbers that rank it: where it sits on the scale the three cases
     * share, and the two ends of its range.
     *
     * <p>It exists so the sealed set is asked about once per standing rather than once per standing
     * per comparison - the ordering is then three plain integer keys, and there is no second place
     * that has to re-establish which case a side was.
     *
     * @param scalePosition     the case's position on the shared scale, ranked before either end
     * @param lowestReputation  the least friendly member's reputation, or a filler for a case with no
     *                          range - never compared against a real one, since the scale position
     *                          separates such a case from every measured bloc first
     * @param highestReputation the most friendly member's reputation, on the same terms
     */
    private record StandingRankKey(
        int scalePosition,
        int lowestReputation,
        int highestReputation) {
    }
}
