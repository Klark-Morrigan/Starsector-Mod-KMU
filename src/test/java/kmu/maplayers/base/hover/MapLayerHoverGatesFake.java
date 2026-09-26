package kmu.maplayers.base.hover;

/**
 * The drawn layer's cursor switches as a case sets them.
 *
 * <p>Which switches a layer offers and how it spells them is its own business, so a case states the
 * three answers outright rather than reaching for a layer's settings reader.
 */
public final class MapLayerHoverGatesFake implements MapLayerHoverGates {

    private boolean isHoverEffectsOn;
    private boolean isHoverTooltipOn;

    /**
     * Gates with both kinds of feedback open.
     *
     * @return the gates
     */
    public static MapLayerHoverGatesFake createAnswering() {

        var gatesFake = new MapLayerHoverGatesFake();

        gatesFake.isHoverEffectsOn = true;
        gatesFake.isHoverTooltipOn = true;

        return gatesFake;
    }

    /**
     * Gates with both kinds shut, which is a layer answering no cursor at all.
     *
     * @return the gates
     */
    public static MapLayerHoverGatesFake createSilent() {
        return new MapLayerHoverGatesFake();
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
