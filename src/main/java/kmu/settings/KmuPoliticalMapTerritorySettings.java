package kmu.settings;

/**
 * How each kind of territory paints: the fill, the two borders and the name of a faction-held
 * or independent-held cluster, the two factionless kinds that have no palette to pick from, and
 * the two strengths that separate a spotlight's subject from its backdrop.
 *
 * <p>The four kinds sit together because they are one decision taken four times - a category's
 * bundle is read as a whole, and setting one kind's fill against another's is the only way to
 * judge either. The recede and spare strengths join them for the same reason: they move the
 * shades these knobs authored.
 *
 * <p>The decivilised kind carries one knob that is not paint: whether such a system is territory
 * to paint at all. It is here because it is read against the shades below it - a player reaching
 * for the decivilised fill because those systems say more than they should is reaching for this
 * switch - and because there is no shade that stands in for it. Zeroing the fill and the border
 * blanks the kind on every view at once, this bundle serving all three, and leaves the picker,
 * the ribbon and the stats counting what nothing on screen draws.
 */
public final class KmuPoliticalMapTerritorySettings {

    private static final String FACTION_OUTER_BORDER_COLOR_FIELD =
        "kmu_map_politics_visuals_faction_outerBorder_colour";
    private static final String FACTION_OUTER_BORDER_OPACITY_FIELD =
        "kmu_map_politics_visuals_faction_outerBorder_opacity";
    private static final String FACTION_OUTER_BORDER_WIDTH_FIELD =
        "kmu_map_politics_visuals_faction_outerBorder_width";
    private static final String FACTION_INNER_BORDER_COLOR_FIELD =
        "kmu_map_politics_visuals_faction_innerBorder_colour";
    private static final String FACTION_INNER_BORDER_OPACITY_FIELD =
        "kmu_map_politics_visuals_faction_innerBorder_opacity";
    private static final String FACTION_INNER_BORDER_WIDTH_FIELD =
        "kmu_map_politics_visuals_faction_innerBorder_width";
    private static final String FACTION_FILL_COLOR_FIELD = "kmu_map_politics_visuals_faction_fill_colour";
    private static final String FACTION_FILL_OPACITY_FIELD = "kmu_map_politics_visuals_faction_fill_opacity";
    private static final String FACTION_NAME_OPACITY_FIELD = "kmu_map_politics_visuals_faction_name_opacity";

    private static final String INDEPENDENT_OUTER_BORDER_COLOR_FIELD =
        "kmu_map_politics_visuals_independent_outerBorder_colour";
    private static final String INDEPENDENT_OUTER_BORDER_OPACITY_FIELD =
        "kmu_map_politics_visuals_independent_outerBorder_opacity";
    private static final String INDEPENDENT_OUTER_BORDER_WIDTH_FIELD =
        "kmu_map_politics_visuals_independent_outerBorder_width";
    private static final String INDEPENDENT_INNER_BORDER_COLOR_FIELD =
        "kmu_map_politics_visuals_independent_innerBorder_colour";
    private static final String INDEPENDENT_INNER_BORDER_OPACITY_FIELD =
        "kmu_map_politics_visuals_independent_innerBorder_opacity";
    private static final String INDEPENDENT_INNER_BORDER_WIDTH_FIELD =
        "kmu_map_politics_visuals_independent_innerBorder_width";
    private static final String INDEPENDENT_FILL_COLOR_FIELD =
        "kmu_map_politics_visuals_independent_fill_colour";
    private static final String INDEPENDENT_FILL_OPACITY_FIELD =
        "kmu_map_politics_visuals_independent_fill_opacity";
    private static final String INDEPENDENT_NAME_OPACITY_FIELD =
        "kmu_map_politics_visuals_independent_name_opacity";

    // Ahead of the decivilised shades because it decides whether there is anything for them to
    // paint: switched off, such a system is not of this category at all and draws as uninhabited.
    private static final String DECIVILISED_SHOULD_DRAW_TERRITORY_FIELD =
        "kmu_map_politics_visuals_decivilised_shouldDrawTerritory";

    // A factionless category carries no colour field: it has no faction palette to pick from, so
    // it always paints in the shared neutral colour and the opacity knobs alone decide what shows.
    private static final String DECIVILISED_BORDER_OPACITY_FIELD =
        "kmu_map_politics_visuals_decivilised_border_opacity";
    private static final String DECIVILISED_BORDER_WIDTH_FIELD =
        "kmu_map_politics_visuals_decivilised_border_width";
    private static final String DECIVILISED_FILL_OPACITY_FIELD =
        "kmu_map_politics_visuals_decivilised_fill_opacity";
    private static final String UNINHABITED_BORDER_OPACITY_FIELD =
        "kmu_map_politics_visuals_uninhabited_border_opacity";
    private static final String UNINHABITED_BORDER_WIDTH_FIELD =
        "kmu_map_politics_visuals_uninhabited_border_width";

    // Only the depth of each recede treatment is a field. The toggles that switch them on are
    // sidebar-only per-save choices held in sector memory, because every LunaLib field renders on
    // a settings tab and a per-save choice cannot.
    private static final String ALLIANCE_MUTED_OPACITY_MODIFIER_FIELD =
        "kmu_map_politics_visuals_styleMutators_mutedOpacityModifier";
    private static final String DESATURATION_DARKENING_FIELD =
        "kmu_map_politics_visuals_styleMutators_desaturationDarkening";
    private static final String PRESENCE_LIGHTENING_FIELD =
        "kmu_map_politics_visuals_styleMutators_presenceLightening";

    private static final FactionPaletteChoice DEFAULT_FACTION_OUTER_BORDER_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_FACTION_OUTER_BORDER_OPACITY = 1.0;
    private static final double DEFAULT_FACTION_OUTER_BORDER_WIDTH = 3.0;
    private static final FactionPaletteChoice DEFAULT_FACTION_INNER_BORDER_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_FACTION_INNER_BORDER_OPACITY = 0.1;
    private static final double DEFAULT_FACTION_INNER_BORDER_WIDTH = 10.0;
    private static final FactionPaletteChoice DEFAULT_FACTION_FILL_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_FACTION_FILL_OPACITY = 0.4;
    private static final double DEFAULT_FACTION_NAME_OPACITY = 1.0;
    private static final FactionPaletteChoice DEFAULT_INDEPENDENT_OUTER_BORDER_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_INDEPENDENT_OUTER_BORDER_OPACITY = 0.5;
    private static final double DEFAULT_INDEPENDENT_OUTER_BORDER_WIDTH = 3.0;
    private static final FactionPaletteChoice DEFAULT_INDEPENDENT_INNER_BORDER_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_INDEPENDENT_INNER_BORDER_OPACITY = 0.1;
    private static final double DEFAULT_INDEPENDENT_INNER_BORDER_WIDTH = 10.0;
    private static final FactionPaletteChoice DEFAULT_INDEPENDENT_FILL_COLOUR =
        FactionPaletteChoice.PRIMARY;
    private static final double DEFAULT_INDEPENDENT_FILL_OPACITY = 0.2;
    private static final double DEFAULT_INDEPENDENT_NAME_OPACITY = 1.0;
    // On, so the shipped map draws what it drew before the switch existed. A decivilised world is
    // a settled place nobody speaks for, and a map of polities can honestly follow either half of
    // that - which is the player's call rather than the mod's, and the half already on screen is
    // the one a switch should have to be thrown to leave.
    private static final boolean DEFAULT_DECIVILISED_SHOULD_DRAW_TERRITORY = true;

    private static final double DEFAULT_DECIVILISED_BORDER_OPACITY = 0.45;
    private static final double DEFAULT_DECIVILISED_BORDER_WIDTH = 3.0;

    // Set against the two fills it has to stay behind: a living faction's 0.4 and independent
    // space's 0.2, a decivilised colony having really been settled but held by nobody.
    private static final double DEFAULT_DECIVILISED_FILL_OPACITY = 0.15;
    private static final double DEFAULT_UNINHABITED_BORDER_OPACITY = 0.15;
    private static final double DEFAULT_UNINHABITED_BORDER_WIDTH = 3.0;
    private static final double DEFAULT_ALLIANCE_MUTED_OPACITY_MODIFIER = 0.5;
    private static final double DEFAULT_DESATURATION_DARKENING = 0.5;

    // All the way to white, because the neutral and the Independent grey the backdrop sinks from
    // are the same shade: a partial lift would leave a spared cell reading against the value the
    // receded fills started at, where the point of sparing it is to be unmistakable at a glance.
    private static final double DEFAULT_PRESENCE_LIGHTENING = 1.0;

    private KmuPoliticalMapTerritorySettings() {
    }

    /**
     * @return which faction palette colour the outer (national) border draws in, or NONE to hide
     *         it; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getFactionOuterBorderColour() {
        return KmuLunaSettings.readChoice(
            FACTION_OUTER_BORDER_COLOR_FIELD,
            DEFAULT_FACTION_OUTER_BORDER_COLOUR);
    }

    /** @return the outer (national) border opacity for faction systems, 0..1; 1.0 by default */
    public static double getFactionOuterBorderOpacity() {
        return KmuLunaSettings.readDouble(
            FACTION_OUTER_BORDER_OPACITY_FIELD,
            DEFAULT_FACTION_OUTER_BORDER_OPACITY);
    }

    /** @return the outer (national) border line width for faction systems, pixels; 3.0 by default */
    public static double getFactionOuterBorderWidth() {
        return KmuLunaSettings.readDouble(
            FACTION_OUTER_BORDER_WIDTH_FIELD,
            DEFAULT_FACTION_OUTER_BORDER_WIDTH);
    }

    /**
     * @return which faction palette colour the inner (province seam) borders draw in, or NONE to
     *         hide them; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getFactionInnerBorderColour() {
        return KmuLunaSettings.readChoice(
            FACTION_INNER_BORDER_COLOR_FIELD,
            DEFAULT_FACTION_INNER_BORDER_COLOUR);
    }

    /** @return the inner (province seam) border opacity for faction systems, 0..1; 0.1 by default */
    public static double getFactionInnerBorderOpacity() {
        return KmuLunaSettings.readDouble(
            FACTION_INNER_BORDER_OPACITY_FIELD,
            DEFAULT_FACTION_INNER_BORDER_OPACITY);
    }

    /**
     * @return the inner (province seam) border line width for faction systems, pixels; 10.0 by
     *         default
     */
    public static double getFactionInnerBorderWidth() {
        return KmuLunaSettings.readDouble(
            FACTION_INNER_BORDER_WIDTH_FIELD,
            DEFAULT_FACTION_INNER_BORDER_WIDTH);
    }

    /**
     * @return which faction palette colour the territory fill draws in, or NONE to leave it
     *         unfilled; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getFactionFillColour() {
        return KmuLunaSettings.readChoice(FACTION_FILL_COLOR_FIELD, DEFAULT_FACTION_FILL_COLOUR);
    }

    /** @return the fill opacity for faction systems, 0..1; 0.4 by default */
    public static double getFactionFillOpacity() {
        return KmuLunaSettings.readDouble(FACTION_FILL_OPACITY_FIELD, DEFAULT_FACTION_FILL_OPACITY);
    }

    /**
     * @return the opacity a faction cluster's name draws at, 0..1 of its owner's colour; 1.0 by
     *         default. The per-frame map-zoom fade the renderer applies composes on top of this
     */
    public static double getFactionNameOpacity() {
        return KmuLunaSettings.readDouble(FACTION_NAME_OPACITY_FIELD, DEFAULT_FACTION_NAME_OPACITY);
    }

    /**
     * @return which independent palette colour the outer (national) border draws in, or NONE to
     *         hide it; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getIndependentOuterBorderColour() {
        return KmuLunaSettings.readChoice(
            INDEPENDENT_OUTER_BORDER_COLOR_FIELD,
            DEFAULT_INDEPENDENT_OUTER_BORDER_COLOUR);
    }

    /**
     * @return the outer (national) border opacity for independent-held systems, 0..1; 0.5 by
     *         default
     */
    public static double getIndependentOuterBorderOpacity() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_OUTER_BORDER_OPACITY_FIELD,
            DEFAULT_INDEPENDENT_OUTER_BORDER_OPACITY);
    }

    /**
     * @return the outer (national) border line width for independent-held systems, pixels; 3.0 by
     *         default
     */
    public static double getIndependentOuterBorderWidth() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_OUTER_BORDER_WIDTH_FIELD,
            DEFAULT_INDEPENDENT_OUTER_BORDER_WIDTH);
    }

    /**
     * @return which independent palette colour the inner (province seam) borders draw in, or NONE
     *         to hide them; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getIndependentInnerBorderColour() {
        return KmuLunaSettings.readChoice(
            INDEPENDENT_INNER_BORDER_COLOR_FIELD,
            DEFAULT_INDEPENDENT_INNER_BORDER_COLOUR);
    }

    /**
     * @return the inner (province seam) border opacity for independent-held systems, 0..1; 0.1 by
     *         default
     */
    public static double getIndependentInnerBorderOpacity() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_INNER_BORDER_OPACITY_FIELD,
            DEFAULT_INDEPENDENT_INNER_BORDER_OPACITY);
    }

    /**
     * @return the inner (province seam) border line width for independent-held systems, pixels;
     *         10.0 by default
     */
    public static double getIndependentInnerBorderWidth() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_INNER_BORDER_WIDTH_FIELD,
            DEFAULT_INDEPENDENT_INNER_BORDER_WIDTH);
    }

    /**
     * @return which independent palette colour the territory fill draws in, or NONE to leave it
     *         unfilled; the primary (bright) colour by default
     */
    public static FactionPaletteChoice getIndependentFillColour() {
        return KmuLunaSettings.readChoice(
            INDEPENDENT_FILL_COLOR_FIELD,
            DEFAULT_INDEPENDENT_FILL_COLOUR);
    }

    /** @return the fill opacity for independent-held systems, 0..1; 0.2 by default */
    public static double getIndependentFillOpacity() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_FILL_OPACITY_FIELD,
            DEFAULT_INDEPENDENT_FILL_OPACITY);
    }

    /**
     * @return the opacity an independent-held cluster's name draws at, 0..1 of its owner's colour;
     *         1.0 by default. The per-frame map-zoom fade the renderer applies composes on top of
     *         this
     */
    public static double getIndependentNameOpacity() {
        return KmuLunaSettings.readDouble(
            INDEPENDENT_NAME_OPACITY_FIELD,
            DEFAULT_INDEPENDENT_NAME_OPACITY);
    }

    /**
     * @return whether a decivilised world counts as somebody living in its system, so the layer
     *         draws that system as territory, offers its owner in the bloc picker, bands it in the
     *         presence ribbon and folds its size into the stats; on by default. Off moves only that
     *         projection - the world is still found, still listed, and still named in the box over
     *         its cell - and the system falls to the uninhabited kind. It keeps a cell, but that
     *         kind carries no fill and its outline is the sidebar's uninhabited-systems checkbox
     *         rather than a knob here, so with that box unticked the cell draws nothing at all
     */
    public static boolean shouldDecivilisedSystemsDrawTerritory() {
        return KmuLunaSettings.readBoolean(
            DECIVILISED_SHOULD_DRAW_TERRITORY_FIELD,
            DEFAULT_DECIVILISED_SHOULD_DRAW_TERRITORY);
    }

    /**
     * @return the outline opacity for decivilised systems, 0..1; 0.45 by default, 0 hiding the
     *         outline, the neutral colour being the only shade a factionless cell has
     */
    public static double getDecivilisedBorderOpacity() {
        return KmuLunaSettings.readDouble(
            DECIVILISED_BORDER_OPACITY_FIELD,
            DEFAULT_DECIVILISED_BORDER_OPACITY);
    }

    /** @return the outline line width for decivilised systems, pixels; 3.0 by default */
    public static double getDecivilisedBorderWidth() {
        return KmuLunaSettings.readDouble(
            DECIVILISED_BORDER_WIDTH_FIELD,
            DEFAULT_DECIVILISED_BORDER_WIDTH);
    }

    /**
     * @return the neutral-colour fill opacity for decivilised systems, 0..1; 0.15 by default, 0
     *         leaving them unfilled so only the outline draws
     */
    public static double getDecivilisedFillOpacity() {
        return KmuLunaSettings.readDouble(
            DECIVILISED_FILL_OPACITY_FIELD,
            DEFAULT_DECIVILISED_FILL_OPACITY);
    }

    /** @return the outline opacity for uninhabited systems, 0..1; 0.15 by default */
    public static double getUninhabitedBorderOpacity() {
        return KmuLunaSettings.readDouble(
            UNINHABITED_BORDER_OPACITY_FIELD,
            DEFAULT_UNINHABITED_BORDER_OPACITY);
    }

    /** @return the outline line width for uninhabited systems, pixels; 3.0 by default */
    public static double getUninhabitedBorderWidth() {
        return KmuLunaSettings.readDouble(
            UNINHABITED_BORDER_WIDTH_FIELD,
            DEFAULT_UNINHABITED_BORDER_WIDTH);
    }

    /**
     * @return the fraction of its normal opacity a non-allied bloc's borders, fills, and name draw
     *         at while the alliances view mutes it, 0..1; 0.5 by default. Unread while the sidebar
     *         Mute toggle is off
     */
    public static double getPoliticalMapAllianceMutedOpacityModifier() {
        return KmuLunaSettings.readDouble(
            ALLIANCE_MUTED_OPACITY_MODIFIER_FIELD,
            DEFAULT_ALLIANCE_MUTED_OPACITY_MODIFIER);
    }

    /**
     * @return how far a desaturated bloc's uniform Independent-based grey is sunk toward black, as
     *         the fraction of brightness removed, 0..1; 0.5 by default. The render pipeline
     *         resolves the palette once per pass, the view flagging only whether a bloc
     *         desaturates and never how dark
     */
    public static double getPoliticalMapDesaturationDarkening() {
        return KmuLunaSettings.readDouble(
            DESATURATION_DARKENING_FIELD,
            DEFAULT_DESATURATION_DARKENING);
    }

    /**
     * @return how far a factionless cell the spotlight spares is washed toward white, 0..1; 1.0 by
     *         default. Applied to RGB alone, so a grey stays the same grey and the cell cannot
     *         drift into reading as a faction colour
     */
    public static double getPoliticalMapPresenceLightening() {
        return KmuLunaSettings.readDouble(
            PRESENCE_LIGHTENING_FIELD,
            DEFAULT_PRESENCE_LIGHTENING);
    }
}
