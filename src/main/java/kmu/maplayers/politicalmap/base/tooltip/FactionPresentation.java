package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionCrests;

/**
 * How a faction appears wherever a hovered system's breakdown names it: the title it is called by and
 * the mark it is shown under, resolved together from its id.
 *
 * <p>The pair travels as one value because it is read for several unrelated reasons - a ranked
 * standing, a claim, a decree - and a faction that presents differently between two lines of one box
 * reads as two factions. Resolving the title and the crest apart is what allows that: each read
 * decides on its own what an absent faction or an unauthored crest comes to, and the two answers only
 * happen to agree.
 *
 * <p>An id the sector no longer knows still presents: the title falls back to the id itself and the
 * crest to nothing, so a line names what it was asked to name rather than coming out blank.
 *
 * @param fullName        the faction's long display title, or its id when the faction will not
 *                        resolve
 * @param crestSpritePath the faction's crest sprite path, or null when it has no authored crest
 */
public record FactionPresentation(
    String fullName,
    String crestSpritePath) {

    /**
     * Resolves how a faction presents, reading the sector once for both halves of it.
     *
     * @param sector    the sector the faction is read from
     * @param factionId the id of the faction being presented
     * @return the title and crest that faction appears as
     */
    public static FactionPresentation resolvePresentation(SectorAPI sector, String factionId) {
        var faction = sector.getFaction(factionId);

        return new FactionPresentation(
            TooltipFactionNames.resolveLongName(faction, factionId),
            FactionCrests.resolveCrestPath(faction));
    }
}
