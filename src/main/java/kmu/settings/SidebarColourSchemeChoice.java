package kmu.settings;

import kmlib.settings.LabeledChoice;

/**
 * Which palette the overlay sidebar is coloured from - the player's choice behind the "Colour scheme"
 * Radio.
 *
 * <p>The sidebar is a piece of the game's UI drawn among vanilla chrome, and every scrap of that chrome
 * answers to the fixed UI palette whatever faction the player flies. Colouring the panel from the player
 * faction instead - which is what it did before this choice existed - leaves a Nex or modded player
 * faction repainting it to a shade nothing around it takes. So the fixed palette is the default and the
 * faction one is kept as a taste, rather than the other way round.
 *
 * <p>One choice rather than one knob per colour because the panel's three colour reads - the frame, the
 * controls' accent steps, and the tab row's chrome rule - have to move together: a panel framed in one
 * palette and ruled in another reads worse than either of the two looks it is made of. Per-colour knobs
 * would offer exactly that as a combination.
 *
 * <p>Its labels are the Radio row's options, spelt identically - see {@link LabeledChoice} for why
 * they never change.
 */
public enum SidebarColourSchemeChoice implements LabeledChoice {

    /**
     * The engine's own button roles: {@code buttonBgDark} for the frames and surfaces that recede,
     * {@code buttonText} for the chrome, and the near-white tooltip-title shade above it. The stock
     * install gives these three the same values the player faction's own three default to, so on an
     * unmodded game this is the look the panel has always had - what it stops doing is following a
     * faction that recolours those.
     */
    UI_PALETTE("UI palette"),

    /**
     * The neutral greys the engine writes its plain text in, over a dark step sunk from the first of
     * them - this being the one scheme with no engine dark to take, the fixed palette's own being the
     * button teal and a tint being what this choice exists to drop. The strongest reading of the panel
     * as part of the game's UI: no accent hue at all, so the sidebar recedes into the chrome around it.
     */
    CHROME_GREY("Chrome grey"),

    /**
     * The player faction's own dark, base, and bright shades, which is what the panel wore before the
     * scheme was a choice. Kept because a player flying a faction whose colours they picked may well
     * want the panel to carry them - and it is the one scheme whose three steps arrive together from a
     * single source, a faction declaring all three.
     */
    PLAYER_FACTION("Player faction");

    private final String label;

    SidebarColourSchemeChoice(String label) {
        this.label = label;
    }

    /**
     * @return the LunaLib Radio option label for this choice, used both as a
     *         read fallback and as the CSV default value
     */
    @Override
    public String getLabel() {
        return label;
    }
}
