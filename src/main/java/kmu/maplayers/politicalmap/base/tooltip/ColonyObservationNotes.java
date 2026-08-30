package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.Colonies;

import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.ColonySightings;
import kmu.util.KmuStrings;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * How old the box's news of each colony in one system is, as the remark a line carries.
 *
 * <p>A colony the player is shown says nothing about when they last had eyes on it. One observed
 * four cycles ago is listed exactly as one observed this morning, and a box that lists them alike
 * is quietly claiming they are equally current - which matters most for the colonies that reached
 * the list through an observation in the first place, a derelict or a concealed base being shown
 * on the strength of somebody having seen it rather than of anything the player can check.
 *
 * <p>The remark is due only where nobody is observing the colony now. A colony in plain sight
 * needs no date beside it, and a date beside a thing the player is looking at would be stale by
 * construction - so the live reading is asked first and the recorded one is reached for only when
 * it fails.
 *
 * <p>Two things count as looking at it, being the two routes an observation is ever made by: the
 * player's fleet is in the system, or the system's own inhabitants can see the colony - the
 * owner-aware reading the visibility rule itself uses, so a faction's own concealed base is not
 * credited to that faction's neighbours.
 *
 * <p>No visibility rule reads any of this. What the time decides is the remark and nothing else:
 * the moment being shown turned on how recent an observation was, a colony would blink out of a
 * box the player had been reading it in.
 */
public final class ColonyObservationNotes {

    // What the player is being shown right now needs no date beside it, whichever route is doing
    // the observing.
    private static final Optional<String> NO_NOTE = Optional.empty();

    // The two spans the wording turns on. The clock reports elapsed days as a fraction, so short
    // of one whole day the colony was seen today, and short of two it was seen the day before.
    private static final float A_DAY = 1.0f;
    private static final float TWO_DAYS = 2.0f;

    // What a box with no world to read answers about every colony: nobody standing anywhere, and
    // no clock, so nothing can be said about when anything was last seen.
    private static final boolean NOBODY_IS_LOOKING = false;
    private static final CampaignClockAPI NO_CLOCK = null;
    private static final Colonies NO_COLONIES = null;

    /**
     * A box with no world to read: nobody is looking at anything and nothing can be dated, so no
     * colony carries a remark. What a caller with no system in hand states, rather than each
     * inventing an empty one of its own.
     */
    public static final ColonyObservationNotes NONE = new ColonyObservationNotes(
        NO_CLOCK,
        NOBODY_IS_LOOKING,
        NO_COLONIES,
        ColonySightings.NONE);

    private final CampaignClockAPI clock;
    private final boolean isPlayerPresent;
    private final Colonies colonies;
    private final ColonySightings sightings;

    // Which colonies the system's own people can see, folded on the first line that asks and kept
    // for the rest of the box.
    //
    // Deferred rather than folded on the read, because the box that takes these notes is not
    // always the box that lists a colony: the ordinary claims box hangs nothing beneath a faction,
    // so an eager fold would run on every hover of every system to answer nothing. A memo on a
    // per-box value, like the one the pass's own classification keeps.
    private Set<String> colonyIdsObservedByInhabitants;

    private ColonyObservationNotes(
            CampaignClockAPI clock,
            boolean isPlayerPresent,
            Colonies colonies,
            ColonySightings sightings) {

        this.clock = clock;
        this.isPlayerPresent = isPlayerPresent;
        this.colonies = colonies;
        this.sightings = sightings;
    }

    /**
     * Reads what one system's colonies are worth remarking on, off the very walk the box is
     * listing them from.
     *
     * <p>The live reading is settled once here rather than per line: whether the player is in the
     * system is one fact about the box, and which colonies the inhabitants can see is one fold
     * over the set the lines come from.
     *
     * @param sector    the sector the system stands in - where the player's fleet is, and what
     *                  clock the dates are read on; null yields notes for nothing
     * @param system    the hovered system; null yields notes for nothing
     * @param colonies  that system's colony set, as the box's own pass walked it; null yields
     *                  notes for nothing
     * @param sightings what has been observed of those colonies and where, as the box's own pass
     *                  read the register - handed in rather than opened here, so the dates stated
     *                  come off the very observations the pass resolved its projection against;
     *                  null reads as nothing observed
     * @return the remarks due on that system's colonies; never null
     */
    public static ColonyObservationNotes readNotesFor(
            SectorAPI sector,
            StarSystemAPI system,
            Colonies colonies,
            ColonySightings sightings) {

        if (sector == null || system == null || colonies == null) {
            return NONE;
        }
        return new ColonyObservationNotes(
            sector.getClock(),
            Objects.equals(sector.getCurrentLocation(), system),
            colonies,
            sightings == null ? ColonySightings.NONE : sightings);
    }

    /**
     * The remark one colony's line carries about how current the box's news of it is.
     *
     * @param colonyId the colony's market id, as the account listing it carries
     * @return the remark, or empty where the colony is being observed now, where nothing was ever
     *         observed of it, or where what was observed carries no time
     */
    public Optional<String> resolveLastSeenNote(String colonyId) {

        if (isPlayerPresent || readColonyIdsObservedByInhabitants().contains(colonyId)) {
            return NO_NOTE;
        }
        var observation = sightings.readObservation(colonyId);

        if (observation == null || clock == null) {
            return NO_NOTE;
        }
        return observation
            .observedTimestamp()
            .map(this::formatLastSeenNote);
    }

    /**
     * Runs a colony's line on into when it was last seen, where nobody is looking at it now.
     *
     * <p>Stated here rather than at each account that draws one, because how a remark reaches a
     * line is the same wherever the line came from - and the accounts that draw them have nothing
     * else in common to have arrived at one rule by.
     *
     * <p>Only the line naming the colony takes one. Anything hanging beneath it is arithmetic over
     * that colony's own number, so a date there would answer for the line above it twice.
     *
     * <p>Reached only through the reading that gathers these notes
     * ({@link SystemColonyReading#describeColony}), which is what keeps a date from being laid on a
     * line without the findings that are due beside it.
     *
     * @param line     the colony's own line, as its account built it
     * @param colonyId the colony's market id, as the account listing it carries
     * @return the line, remarked on where a remark is due and untouched where none is
     */
    CellTooltipEntryLine remarkOnColony(CellTooltipEntryLine line, String colonyId) {
        return resolveLastSeenNote(colonyId)
            .map(line::notedWith)
            .orElse(line);
    }

    // The gated colonies the system's own people can see, folded once and kept.
    private Set<String> readColonyIdsObservedByInhabitants() {

        if (colonyIdsObservedByInhabitants == null) {
            colonyIdsObservedByInhabitants = foldColonyIdsObservedByInhabitants(colonies);
        }
        return colonyIdsObservedByInhabitants;
    }

    // The gated colonies the system's own people can see, by id. Asked of the visibility rule's own
    // reading rather than re-derived here, so the box's account of who can see what is the same one
    // the map is drawn under.
    //
    // Under the fog alone, as every observation reading is: who can see a colony is a fact about the
    // place, and a reveal that reached it would date a colony the player was never told about. That
    // is why the knowledge is opened here rather than taken from the box's own pass, whose rule
    // carries whatever the player has revealed - and why the kinds it resolves cannot be shared
    // with the ones the box folded beside these notes.
    private static Set<String> foldColonyIdsObservedByInhabitants(Colonies colonies) {

        var colonyIds = new HashSet<String>();

        if (colonies == null) {
            return colonyIds;
        }

        for (var colony : ColonyKnowledge
                .observingUnderTheFog()
                .readColoniesObservedByInhabitants(colonies)) {

            colonyIds.add(colony.market().getId());
        }
        return colonyIds;
    }

    // When the colony was last seen, said twice over: how long ago, and on what date. The span is
    // what a reader judges the news by, and the date is what they can hold against anything else
    // they know - neither answers for the other.
    //
    // The clock the date is read on is built from the stamp and discarded with the line, this
    // being the only way the game turns a moment into a date.
    private String formatLastSeenNote(long observedTimestamp) {

        return KmuStrings.format(
            KmuStrings.POLITICAL_MAP_TOOLTIP_LAST_SEEN,
            formatElapsedSpan(clock.getElapsedDaysSince(observedTimestamp)),
            clock.createClock(observedTimestamp).getDateString());
    }

    // How long ago, in whole days. A span short of a day is named rather than rounded to nought:
    // "0 days ago" reads as a fault in the box, and the reader is being told the news is fresh.
    private static String formatElapsedSpan(float elapsedDays) {

        if (elapsedDays < A_DAY) {
            return KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_LAST_SEEN_TODAY);
        }
        if (elapsedDays < TWO_DAYS) {
            return KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_LAST_SEEN_A_DAY_AGO);
        }
        return KmuStrings.format(
            KmuStrings.POLITICAL_MAP_TOOLTIP_LAST_SEEN_DAYS_AGO,
            (int) elapsedDays);
    }
}
