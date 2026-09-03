package kmu.maplayers.base.visibility.observations;

import com.fs.starfarer.api.campaign.CampaignClockAPI;

import kmu.util.KmuStrings;

/**
 * How old the news on one axis is, put into words: how long ago it was observed, and on what date.
 *
 * <p>Said twice over because neither half answers for the other. The span is what a reader judges
 * the news by - a cycle-old reading is worth less than a morning-old one whatever the calendar says
 * - and the date is what they hold against anything else they know.
 *
 * <p>Stated once for every axis that dates itself rather than composed where it is drawn, because
 * two remarks disagreeing about how long a day is would be read as two different claims.
 *
 * <p>The lead-in is the caller's and the span is not. Which words introduce a date belong to the
 * axis, a direct sighting and a reading taken at a distance not being the same claim; the three span
 * words are the same wherever they are read. So the lead-in arrives as a key and the span is
 * composed here, which is how a further axis joins without this class learning what it is about.
 *
 * <p>Nothing here decides whether a date is due. It is handed a moment already known to want one.
 */
public final class ObservationNoteFormatter {

    // The two spans the wording turns on. The clock reports elapsed days as a fraction, so short of
    // one whole day the fact was observed today, and short of two it was observed the day before.
    private static final float A_DAY = 1.0f;
    private static final float TWO_DAYS = 2.0f;

    private ObservationNoteFormatter() {
    }

    /**
     * The remark stating when an axis was last observed.
     *
     * @param clock             the campaign clock the moment is read against - what turns a stamp
     *                          into a span and into a date
     * @param leadInKey         the axis's own words introducing the date, as a string id taking the
     *                          span and the date in that order
     * @param observedTimestamp when the axis was observed, on the clock's own scale
     * @return the composed remark
     */
    public static String formatObservationNote(
            CampaignClockAPI clock,
            String leadInKey,
            long observedTimestamp) {

        // The clock the date is read on is built from the stamp and discarded with the remark, this
        // being the only way the game turns a moment into a date.
        return KmuStrings.format(
            leadInKey,
            formatElapsedSpan(clock.getElapsedDaysSince(observedTimestamp)),
            clock.createClock(observedTimestamp).getDateString());
    }

    // How long ago, in whole days. A span short of a day is named rather than rounded to nought:
    // "0 days ago" reads as a fault in the surface, and the reader is being told the news is fresh.
    private static String formatElapsedSpan(float elapsedDays) {

        if (elapsedDays < A_DAY) {
            return KmuStrings.get(KmuStrings.OBSERVATION_SPAN_TODAY);
        }
        if (elapsedDays < TWO_DAYS) {
            return KmuStrings.get(KmuStrings.OBSERVATION_SPAN_A_DAY_AGO);
        }
        return KmuStrings.format(KmuStrings.OBSERVATION_SPAN_DAYS_AGO, (int) elapsedDays);
    }
}
