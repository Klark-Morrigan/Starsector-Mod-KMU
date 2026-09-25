package kmu.maplayers.ownermap.render.hover;

/**
 * The drawn layer's cursor switches as a tier case sets them.
 *
 * <p>Which switches a layer offers and how it spells them is its own business, so a tier case
 * states the three answers outright rather than reaching for a layer's settings reader.
 */
public final class OwnerMapHoverGatesFake implements OwnerMapHoverGates {

    private boolean isHoverEffectsOn;
    private boolean isHoverTooltipOn;

    /**
     * Gates with both kinds of feedback open.
     *
     * @return the gates
     */
    public static OwnerMapHoverGatesFake createAnswering() {
        var gatesFake = new OwnerMapHoverGatesFake();
        gatesFake.isHoverEffectsOn = true;
        gatesFake.isHoverTooltipOn = true;
        return gatesFake;
    }

    /**
     * Gates with both kinds shut, which is a layer answering no cursor at all.
     *
     * @return the gates
     */
    public static OwnerMapHoverGatesFake createSilent() {
        return new OwnerMapHoverGatesFake();
    }

    /**
     * Moves the painted-feedback switch.
     *
     * @param isOn whether the halo and cell wash may draw
     */
    public void setHoverEffectsOn(boolean isOn) {
        isHoverEffectsOn = isOn;
    }

    /**
     * Moves the box switch.
     *
     * @param isOn whether the hover box may be resolved
     */
    public void setHoverTooltipOn(boolean isOn) {
        isHoverTooltipOn = isOn;
    }

    @Override
    public boolean isHoverEffectsEnabled() {
        return isHoverEffectsOn;
    }

    @Override
    public boolean isHoverTooltipEnabled() {
        return isHoverTooltipOn;
    }
}
