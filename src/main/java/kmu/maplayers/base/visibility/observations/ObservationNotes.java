package kmu.maplayers.base.visibility.observations;

import com.fs.starfarer.api.campaign.CampaignClockAPI;

import java.util.List;
import java.util.Optional;

/**
 * The one remark a row states about how current its news is, chosen among the several concealed
 * facts the row carries.
 *
 * <p>One rule, and it is the whole of the shared half's opinion about dates: every recalled fact
 * carrying a moment contributes it, the most recent contribution dates the row, and the row states
 * that contribution in its own fact's words. Where nothing contributes, the row carries no date.
 *
 * <p>The other two states contribute nothing, and for different reasons. A fact being revealed now
 * has no date to give - what is being looked at is current by construction - and one nobody has ever
 * established has none either, being absent rather than old. So a row is dated by what it is
 * recalling and by nothing else.
 *
 * <p>Several readings fall out of that rather than being written. A row whose one unknown fact
 * states a placeholder is still dated by whichever fact is recalled, since the unknown one
 * contributes nothing to argue with. A row carrying one current fact beside one recalled fact is
 * dated by the recalled one, rather than being left undated by the current one. Neither needs a rule
 * of its own, and neither can be broken by a later hand without breaking the one rule above.
 *
 * <p>Stated once for every family rather than per row, because two rows disagreeing about which of
 * their facts dates them - or about how long a day is - would be read as two different claims about
 * the world instead of as one surface phrasing itself two ways.
 */
public final class ObservationNotes {

    // A row with nothing to date says nothing, rather than spending a line on an empty remark.
    private static final Optional<String> NO_NOTE = Optional.empty();

    private ObservationNotes() {
    }

    /**
     * The remark a row states, given every concealed fact on it.
     *
     * @param clock the campaign clock the winning moment is read against - what turns a stamp into
     *              a span and into a date; absent where the caller has no world to read, in which
     *              case nothing can be dated and no remark is due
     * @param axes  the row's concealed facts, in the order a tie between two equally recent ones is
     *              to be settled by; null or empty yields no remark
     * @return the remark, or empty where no fact on the row contributes a moment
     */
    public static Optional<String> resolveNoteForAxes(
            CampaignClockAPI clock,
            List<ObservationAxis> axes) {

        if (clock == null || axes == null) {
            return NO_NOTE;
        }
        ObservationAxis datingAxis = null;
        long datingTimestamp = 0L;

        for (var axis : axes) {
            var contributedTimestamp = readContributedTimestamp(axis);

            // Strictly later, so the first axis given holds a tie. The caller's order settles it,
            // which is what keeps a row hovered twice from stating two different wordings.
            if (contributedTimestamp.isPresent()
                    && (datingAxis == null || contributedTimestamp.get() > datingTimestamp)) {

                datingAxis = axis;
                datingTimestamp = contributedTimestamp.get();
            }
        }
        if (datingAxis == null) {
            return NO_NOTE;
        }
        // The winner's own words, not the row's: which claim is being dated is decided by which
        // fact won, and the words are what say which claim that was.
        return Optional.of(ObservationNoteFormatter.formatObservationNote(
            clock,
            datingAxis.leadInKey(),
            datingTimestamp));
    }

    // The moment one fact contributes to the row's date. Only a recalled fact has one to give, and
    // a recalled fact written down before observations were timed gives none either - it is news
    // somebody wrote down without saying when, which is recalled all the same and simply undatable.
    private static Optional<Long> readContributedTimestamp(ObservationAxis axis) {

        return axis.recency().selectByCase(
            Optional::empty,
            ObservationRecency.RecalledObservation::observedTimestamp,
            Optional::empty);
    }
}
