package kmu.settings;

/**
 * Which side of the sector's nebulae each of the political map's sub-layers paints on: dimmed
 * beneath the haze, or clear above it.
 *
 * <p>One field per visible sub-layer, because the sub-layers are what a player sees as separate
 * things. A treatment that cannot stand apart from one of them - the contested hatch, the hover
 * highlight, the diagnostics - follows the choice its own picture depends on rather than carrying
 * a knob nobody could answer independently.
 *
 * <p>The four answer only what the player picked. Which orders are actually drawable is a
 * constraint of the paint order itself - a fill laid over its own borders leaves a blank cell -
 * and is settled where the choices become passes, not here.
 */
public final class KmuPoliticalMapDrawOrderSettings {

    private static final String NEBULA_DRAW_ORDER_FILLS_FIELD =
        "kmu_map_politics_visuals_starscape_positionRelativeToNebulae_fills";
    private static final String NEBULA_DRAW_ORDER_BORDERS_FIELD =
        "kmu_map_politics_visuals_starscape_positionRelativeToNebulae_borders";
    private static final String NEBULA_DRAW_ORDER_RIBBONS_FIELD =
        "kmu_map_politics_visuals_starscape_positionRelativeToNebulae_presenceRibbons";
    private static final String NEBULA_DRAW_ORDER_LABELS_FIELD =
        "kmu_map_politics_visuals_starscape_positionRelativeToNebulae_labels";

    // The cell geometry and the bands laid on it sink beneath the nebulae, and only the names rise
    // clear of them - words being the one thing the haze decides whether a reader gets at all,
    // where a shape it dims is merely quieter.
    private static final NebulaDrawOrderChoice DEFAULT_NEBULA_DRAW_ORDER_FILLS =
        NebulaDrawOrderChoice.BELOW;
    private static final NebulaDrawOrderChoice DEFAULT_NEBULA_DRAW_ORDER_BORDERS =
        NebulaDrawOrderChoice.BELOW;
    private static final NebulaDrawOrderChoice DEFAULT_NEBULA_DRAW_ORDER_RIBBONS =
        NebulaDrawOrderChoice.BELOW;
    private static final NebulaDrawOrderChoice DEFAULT_NEBULA_DRAW_ORDER_LABELS =
        NebulaDrawOrderChoice.ABOVE;

    private KmuPoliticalMapDrawOrderSettings() {
    }

    /**
     * @return which side of the map's nebulae the territory fills paint on: BELOW dimmed by them
     *         (the default), ABOVE clear of them. The contested hatch is part of a cluster's fill
     *         and paints with it, and the hover highlight brightens a fill so it follows this too.
     *         Lifting the fills lifts the borders with them, since a fill painted over its own
     *         borders leaves a blank cell - a constraint resolved where the choices become a paint
     *         order, so this answers only what the player picked
     */
    public static NebulaDrawOrderChoice getPoliticalMapFillNebulaDrawOrder() {
        return KmuLunaSettings.readChoice(NEBULA_DRAW_ORDER_FILLS_FIELD, DEFAULT_NEBULA_DRAW_ORDER_FILLS);
    }

    /**
     * @return which side of the map's nebulae the cluster boundaries, province seams, and
     *         factionless outlines paint on: BELOW dimmed by them (the default), ABOVE clear of
     *         them. Borders may be lifted alone but never sink below the fills, so a BELOW here is
     *         honoured only while the fills are BELOW as well
     */
    public static NebulaDrawOrderChoice getPoliticalMapBorderNebulaDrawOrder() {
        return KmuLunaSettings.readChoice(NEBULA_DRAW_ORDER_BORDERS_FIELD, DEFAULT_NEBULA_DRAW_ORDER_BORDERS);
    }

    /**
     * @return which side of the map's nebulae the presence bands paint on: BELOW dimmed by them
     *         (the default), ABOVE clear of them
     */
    public static NebulaDrawOrderChoice getPoliticalMapRibbonNebulaDrawOrder() {
        return KmuLunaSettings.readChoice(NEBULA_DRAW_ORDER_RIBBONS_FIELD, DEFAULT_NEBULA_DRAW_ORDER_RIBBONS);
    }

    /**
     * @return which side of the map's nebulae the cluster names paint on: BELOW dimmed by them,
     *         ABOVE clear of them (the default, a haze over words costing legibility rather than
     *         strength of colour)
     */
    public static NebulaDrawOrderChoice getPoliticalMapLabelNebulaDrawOrder() {
        return KmuLunaSettings.readChoice(NEBULA_DRAW_ORDER_LABELS_FIELD, DEFAULT_NEBULA_DRAW_ORDER_LABELS);
    }
}
