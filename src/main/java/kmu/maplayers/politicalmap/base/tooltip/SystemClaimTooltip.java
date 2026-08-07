package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;

import kmu.maplayers.base.tooltip.MapHoverTooltip;

import java.util.Optional;

/**
 * The claim contest a political-map layer shows for the hovered star system at a glance: who holds it,
 * who nearly took it, and who is merely present, each named with its crest and the standing the mechanic
 * weighed it at. The one {@link MapHoverTooltip} the claims view injects.
 *
 * <p>So this box adds nothing to the shape every claim box shares
 * ({@link SystemClaimContestTooltip}), and that is what it is: a faction listed as the line naming it
 * and nothing beneath, which is the shared default rather than an answer of its own. What it decides is
 * how much of the contest one hover states - who stands where, on the standing alone - and where the
 * rest of that answer is to be found: the colonies behind those standings are the counterpart box's
 * ({@link ExpandedSystemClaimTooltip}), which the framework draws in place of this one while the player
 * has asked for it, so the ordinary hover stays a glance and the detail is there for the asking rather
 * than always on screen.
 *
 * <p>Stateless past the reader it is built around, so one shared instance serves the layer.
 */
public final class SystemClaimTooltip extends SystemClaimContestTooltip {

    /**
     * The one shared instance, explaining vanilla claims - the same mechanic the layer's fills are
     * resolved through, so the box and the cell under it can never name different claimants.
     */
    public static final SystemClaimTooltip INSTANCE =
        new SystemClaimTooltip(VANILLA_CLAIM_BREAKDOWN_READER);

    // The counterpart drawn in this box's place while the player has asked for detail. Built here on
    // this box's own claim read rather than reached for as a shared instance, so the pair can never
    // answer a decree from two different readers - which is the whole point of the layer binding one.
    private final ExpandedSystemClaimTooltip expandedVariant;

    SystemClaimTooltip(ClaimBreakdownReader claimBreakdownReader) {
        super(claimBreakdownReader);
        this.expandedVariant = new ExpandedSystemClaimTooltip(claimBreakdownReader);
    }

    @Override
    public Optional<MapHoverTooltip> resolveExpandedVariant() {
        return Optional.of(expandedVariant);
    }
}
