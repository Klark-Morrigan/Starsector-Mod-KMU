package kmu.maplayers.politicalmap.tooltip;

import kmlib.starsector.systems.StarSystems;
import kmlib.starsector.systems.claims.ClaimDecreeReader;

import kmu.maplayers.base.visibility.colonies.FactionAllianceSource;
import kmu.mods.nexerelin.NexerelinAlliances;
import kmu.mods.nexerelin.NexerelinContestWording;

import java.util.Objects;

/**
 * The live reads every political-map box is built around: the decree it heads with, the alliances
 * its contest's sides are read under, and the wording of its rival block.
 *
 * <p>Travelling as one value, with one binding a running game uses, is what stops two boxes running
 * on different reads. A decree, an alliance set or a wording bound per box would answer one hover two
 * ways a keystroke apart, with nothing on screen to say which was right.
 *
 * @param claimDecreeReader     the decree read the box heads itself with. The decree answers off the
 *                              system's own memory flag and reaches no colony, so it is the narrow port
 *                              alone rather than a claim reader
 * @param factionAllianceSource where the alliance set behind an allied block is taken from. A source
 *                              rather than a set, because a box lives for the whole session while
 *                              alliances form and dissolve inside it
 * @param contestWordingSource  where the wording of the block beneath the friendly one is taken from.
 *                              A source rather than a wording, because these boxes stand in static
 *                              fields and construction can fall before the game has a mod set to read
 */
public record PoliticalBoxSeams(
    ClaimDecreeReader claimDecreeReader,
    FactionAllianceSource factionAllianceSource,
    ContestWordingSource contestWordingSource) {

    /**
     * The reads a running game's boxes use. The alliance set and the wording come off the one mod
     * that supplies them; both are gates rather than branches, answering with the plain reading
     * wherever that mod is absent.
     */
    public static final PoliticalBoxSeams LIVE = new PoliticalBoxSeams(
        StarSystems::readDecreedFactionId,
        NexerelinAlliances::resolveFactionAlliancesIn,
        NexerelinContestWording::resolveWording);

    public PoliticalBoxSeams {

        Objects.requireNonNull(claimDecreeReader, "claimDecreeReader");
        Objects.requireNonNull(factionAllianceSource, "factionAllianceSource");
        Objects.requireNonNull(contestWordingSource, "contestWordingSource");
    }
}
