package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.markets.colonies.Colonies;

import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.ColonySightings;
import kmu.maplayers.base.visibility.observations.ObservationAxis;
import kmu.maplayers.base.visibility.observations.ObservationNotes;
import kmu.maplayers.base.visibility.observations.ObservationRecency;
import kmu.maplayers.base.visibility.observations.ObservationRecency.RecalledObservation;
import kmu.util.KmuStrings;

import java.util.HashSet;
import java.util.List;
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
 * construction. Ranking the live reading above the recorded one is the shared triad's one rule
 * ({@link ObservationRecency#resolveRecency}); what is made here is only the two readings
 * themselves.
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
     * @param colonyId the colony's market ID, as the account listing it carries
     * @return the remark, or empty where the colony is being observed now, where nothing was ever
     *         observed of it, where what was observed carries no time, or where there is no clock
     *         to date it by
     */
    Optional<String> resolveLastSeenNote(String colonyId) {

        // The colony's own routes, posed in the shared triad's terms: whether anybody is observing
        // the colony now, and what the register recalls of it. Which of the two readings answers -
        // live over recalled over nothing - is the triad's rule, not a comparison made here.
        var recency = ObservationRecency.resolveRecency(
            isPlayerPresent || readColonyIdsObservedByInhabitants().contains(colonyId),
            readRecordedObservation(colonyId));

        // One axis, under the colony's own lead-in: what the box states of a colony is that
        // somebody had eyes on it, which is the only route a colony is ever observed by. The span
        // and the date are composed in the words every axis shares.
        return ObservationNotes.resolveNoteForAxes(
            clock,
            List.of(new ObservationAxis(KmuStrings.POLITICAL_MAP_TOOLTIP_LAST_SEEN, recency)));
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
     * @param colonyId the colony's market ID, as the account listing it carries
     * @return the line, remarked on where a remark is due and untouched where none is
     */
    CellTooltipEntryLine remarkOnColony(CellTooltipEntryLine line, String colonyId) {
        return resolveLastSeenNote(colonyId)
            .map(line::notedWith)
            .orElse(line);
    }

    // What the register recalls of one colony, in the triad's recalled shape. The place the
    // observation names is the visibility rule's to spend; a remark dates the news wherever it was
    // made, so only the moment travels.
    private Optional<RecalledObservation> readRecordedObservation(String colonyId) {

        return Optional
            .ofNullable(sightings.readObservation(colonyId))
            .map(observation -> new RecalledObservation(observation.observedTimestamp()));
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
}
