package kmu.maplayers.base.tooltip;

import java.util.Optional;

/**
 * A stand-in hover box that does define a richer counterpart, so a selection test has both sides of
 * the choice in hand: the box the expanded mode is meant to pass over, and the one it is meant to
 * reach instead.
 *
 * <p>Built over the plain stand-in rather than beside it, so the two differ in exactly the one thing
 * selection reads - whether a counterpart is offered - and nothing else can explain a difference in
 * what a test observes.
 */
final class ExpandedVariantMapHoverTooltipFake extends MapHoverTooltipFake {

    // The richer box this stand-in offers. Handed in rather than built here, so a test can assert
    // that the very instance it supplied is the one selection reached.
    private final MapHoverTooltip expandedVariantFake;

    ExpandedVariantMapHoverTooltipFake(MapHoverTooltip expandedVariantFake) {
        this.expandedVariantFake = expandedVariantFake;
    }

    @Override
    public Optional<MapHoverTooltip> resolveExpandedVariant() {
        return Optional.of(expandedVariantFake);
    }
}
