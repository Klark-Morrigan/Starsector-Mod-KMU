package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;

import kmu.maplayers.base.visibility.MapVisibilityOverrides;

/**
 * The vanilla claim breakdown, read under whatever the player's visibility settings say at the
 * moment of the read.
 *
 * <p>Exists because the two halves keep different time. A hover box is one shared instance that
 * outlives every pass, while the visibility rule is a live setting the player may move between
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
 */
public final class LiveVisibilityClaimBreakdownReader implements ClaimBreakdownReader {

    @Override
    public SystemClaimBreakdown readBreakdown(StarSystemAPI system) {
        return openReader().readBreakdown(system);
    }

    @Override
    public String readCoreFactionId(StarSystemAPI system) {
        return openReader().readCoreFactionId(system);
    }

    // The underlying reader under the rule in force right now. Opened per ask rather than held,
    // so the rule can never be older than the answer given under it.
    private static ClaimBreakdownReader openReader() {

        return new VanillaClaimBreakdownReader(
            MapVisibilityOverrides.readFromLunaSettings().colonyVisibility());
    }
}
