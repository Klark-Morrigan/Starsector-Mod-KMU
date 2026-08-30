package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.KnownColonyReader;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;

import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;

/**
 * The vanilla claim breakdown, read under whatever the player's visibility settings say at the
 * moment of the read.
 *
 * <p>Exists because the two halves keep different time. A hover box is one shared instance that
 * outlives every pass, while the colony rule is a live setting the player may move between
 * one hover and the next - so a reader built once around a rule sampled at start-up would go on
 * reporting a derelict as known long after the player asked for it to be hidden, and would do so
 * silently, since a claim breakdown reports knowledge as a flag on a market rather than by
 * leaving the market out.
 *
 * <p>Resolved per read rather than per box because that is the only moment both are in hand: the
 * settings read is a memory lookup beside a walk of the whole system, so paying it on every ask
 * costs nothing measurable and removes the one way the box and the map can disagree.
 *
 * <p>The reader beneath is minted per read for the same reason, and it is the pass-less one on
 * purpose: it holds no colony index, so there is no snapshot of the sector to go stale between
 * hovers. A reader that walks afresh is what a surface with no pass behind it must have.
 *
 * <p>Only the breakdown is resolved that way. The decree is a rule-free question - see below -
 * and the port promises it stays a single memory read, so nothing is put in front of it here.
 */
public final class LiveVisibilityClaimBreakdownReader implements ClaimBreakdownReader {

    // The decree read consults no colony knowledge: it answers off the system's own memory flag,
    // and reaches no colony for a projection to be applied to. So one reader serves every ask of
    // it, and it is opened naming nothing - the conservative statement, and the honest one for a
    // reader whose breakdowns nobody ever reads.
    //
    // Held rather than opened per ask because the port undertakes that this question stays a
    // single memory read - a caller heading a box with a decree is promised it does not pay for
    // the scoring - and a settings lookup and an allocation in front of it would quietly make
    // that untrue for every box drawn.
    private static final ClaimBreakdownReader DECREE_READER =
        new VanillaClaimBreakdownReader(KnownColonyReader.NOTHING_KNOWN);

    @Override
    public SystemClaimBreakdown readBreakdown(StarSystemAPI system) {
        return openReaderUnderLiveVisibility().readBreakdown(system);
    }

    @Override
    public String readCoreFactionId(StarSystemAPI system) {
        return DECREE_READER.readCoreFactionId(system);
    }

    // The underlying reader under the rule in force right now. Opened per ask rather than held,
    // so the rule can never be older than the answer given under it.
    private static ClaimBreakdownReader openReaderUnderLiveVisibility() {

        return new VanillaClaimBreakdownReader(ColonyKnowledge.over(
            Global.getSector(),
            MapVisibilityRules.readFromLunaSettings().colonyVisibility()));
    }
}
