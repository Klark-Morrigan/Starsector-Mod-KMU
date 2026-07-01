package kmu.politicalmap.render;

/**
 * The fixed visual style of the political map overlay, gathered as the single
 * place the map's look is decided: the border-channel width, the national-border
 * versus interior-seam line weights, and the core-faction fill/border opacities.
 * The seam color is not decided here - it is the owner faction's own dark UI
 * color, resolved with the rest of the owner palette in
 * {@link kmu.politicalmap.domain.SectorPolitics}.
 *
 * <p>These are the knobs that are fixed for every game. The independent,
 * decivilised, and uninhabited opacities are deliberately not here - they are
 * player-tunable under the LunaLib "Visuals customisation" tab and live in
 * {@link kmu.settings.KmuLunaSettings}. Mechanical constants that do not decide
 * the look (the GL vertex stride, the terrain render range and engine layers)
 * stay with the plugin that uses them.
 */
final class PoliticalMapStyle {
    // Inward inset applied to every national-border edge, so two neighbouring
    // blocs leave a uniform 2 * inset channel; a same-faction seam is left
    // un-inset so the two cells' fills fuse along it.
    static final double BORDER_INSET_DISTANCE = 150.0;

    // The bold national border versus the thin interior province line, so a
    // bloc's edge dominates and its internal seams recede.
    static final float BOUNDARY_LINE_WIDTH = 2.5f;
    static final float INTERIOR_LINE_WIDTH = 1f;

    // Fill and border opacities for a core-faction province. Fixed (not player-
    // tunable, unlike the independent/decivilised/uninhabited opacities): the fill
    // is high enough that the faction color reads at a glance yet low enough that
    // neighbouring fills do not muddy where they meet, and the border is fully
    // opaque so faction borders stay the crispest thing on the map.
    static final float FACTION_FILL_ALPHA = 0.4f;
    static final float FACTION_BORDER_ALPHA = 1f;

    // Constants only; never instantiated.
    private PoliticalMapStyle() {
    }
}
