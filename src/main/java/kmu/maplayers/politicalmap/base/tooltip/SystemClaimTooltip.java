package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;

import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.dominance.HolderGroupingSource;
import kmu.starsector.nexerelin.NexerelinAlliances;

import java.util.Optional;

/**
 * The claim contest a political-map layer shows for the hovered star system at a glance: who holds it,
 * who stands with the holder, who nearly took it, and who is merely present, each named with its crest
 * and the standing the mechanic weighed it at. The one {@link MapHoverTooltip} the claims view injects.
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
 * <p>Stateless past the seams it is built around, so one shared instance serves the layer.
 */
public final class SystemClaimTooltip extends SystemClaimContestTooltip {

    /**
     * The one shared instance, explaining vanilla claims - the same mechanic the layer's fills are
     * resolved through, so the box and the cell under it can never name different claimants.
     *
     * <p>This is where the alliance set behind the allied block is bound, and the only place either
     * claim box names where alliances come from. The gate answers with the identity grouping wherever
     * the mod supplying them is absent, so the box needs no branch of its own and reads as its two
     * eligibility blocks alone on such an install.
     *
     * <p>Deliberately not the claims view's own grouping, which pins itself to identity so the fills
     * stay per claiming faction: that decision is about what the map paints, while the block needs the
     * live alliance set, and reusing the view's would leave it permanently empty on the one layer that
     * draws it.
     */
    public static final SystemClaimTooltip INSTANCE = new SystemClaimTooltip(
        VANILLA_CLAIM_BREAKDOWN_READER,
        NexerelinAlliances::resolveGrouping);

    // The counterpart drawn in this box's place while the player has asked for detail. Built here on
    // this box's own claim read and alliance seam rather than reached for as a shared instance, so the
    // pair can never answer a decree - or place an ally - from two different sources.
    private final ExpandedSystemClaimTooltip expandedVariant;

    SystemClaimTooltip(
            ClaimBreakdownReader claimBreakdownReader,
            HolderGroupingSource holderGroupingSource) {

        super(claimBreakdownReader, holderGroupingSource);
        this.expandedVariant = new ExpandedSystemClaimTooltip(
            claimBreakdownReader,
            holderGroupingSource);
    }

    @Override
    public Optional<MapHoverTooltip> resolveExpandedVariant() {
        return Optional.of(expandedVariant);
    }
}
