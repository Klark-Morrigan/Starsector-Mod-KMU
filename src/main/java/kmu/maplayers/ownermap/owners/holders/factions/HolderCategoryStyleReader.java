package kmu.maplayers.ownermap.owners.holders.factions;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.ownermap.render.style.OwnerPaletteSlot;
import kmu.settings.KmuOwnerMapStyleSettings;
import kmu.settings.OwnerPaletteChoice;

/**
 * Reads the four {@link OwnerMapCategory} styles out of the player's LunaLib settings - the bundles
 * {@link HolderCategories} hands the tier.
 *
 * <p>The two owned categories - core factions and independent space - carry a full
 * fill/outer/inner style. The two factionless categories - decivilised and uninhabited - have no
 * faction palette, so both paint in the shared neutral colour and neither carries a colour choice:
 * a decivilised cell draws a fill and an outline, an uninhabited cell an outline alone, and both set
 * their inner seam to "No color" since factionless cells never fuse into clusters.
 */
final class HolderCategoryStyleReader {

    // Reads only settings; never instantiated.
    private HolderCategoryStyleReader() {
    }

    // Reads each owned category's eight style settings into one bundle, so the build
    // loop applies them per cluster without eight lookups each.
    //
    // The two owned categories are the same eight knobs read twice over, and stay two methods
    // rather than one parameterised by group. The field IDs cannot be composed from a group name:
    // each is held whole in a constant so it traces to its row in the shipped settings table, and
    // an ID built from parts would trace to none and be free to drift from it. What the two share
    // is the crossing below, which is named once for that reason.
    static CategoryStyle readFactionStyle() {

        return new CategoryStyle(
            resolvePaintedElement(
                KmuOwnerMapStyleSettings.getFactionFillColour(),
                KmuOwnerMapStyleSettings.getFactionFillOpacity()),
            resolvePaintedElement(
                KmuOwnerMapStyleSettings.getFactionOuterBorderColour(),
                KmuOwnerMapStyleSettings.getFactionOuterBorderOpacity()),
            KmuOwnerMapStyleSettings.getFactionOuterBorderWidth(),
            resolvePaintedElement(
                KmuOwnerMapStyleSettings.getFactionInnerBorderColour(),
                KmuOwnerMapStyleSettings.getFactionInnerBorderOpacity()),
            KmuOwnerMapStyleSettings.getFactionInnerBorderWidth());
    }

    static CategoryStyle readIndependentStyle() {

        return new CategoryStyle(
            resolvePaintedElement(
                KmuOwnerMapStyleSettings.getIndependentFillColour(),
                KmuOwnerMapStyleSettings.getIndependentFillOpacity()),
            resolvePaintedElement(
                KmuOwnerMapStyleSettings.getIndependentOuterBorderColour(),
                KmuOwnerMapStyleSettings.getIndependentOuterBorderOpacity()),
            KmuOwnerMapStyleSettings.getIndependentOuterBorderWidth(),
            resolvePaintedElement(
                KmuOwnerMapStyleSettings.getIndependentInnerBorderColour(),
                KmuOwnerMapStyleSettings.getIndependentInnerBorderOpacity()),
            KmuOwnerMapStyleSettings.getIndependentInnerBorderWidth());
    }

    // Decivilised colonies keep both a neutral fill and a neutral outline: the system was settled
    // once, so it reads as occupied space rather than a bare ring around nothing. Neither
    // element has a colour choice - a factionless cell has no palette to pick from - so the
    // outline is unconditionally drawn and each opacity is its element's own on/off.
    static CategoryStyle readDecivilisedStyle() {

        return buildNeutralStyle(
            resolvePaintedElement(
                OwnerPaletteChoice.PRIMARY,
                KmuOwnerMapStyleSettings.getDecivilisedFillOpacity()),
            true, // Outline is drawn.
            KmuOwnerMapStyleSettings.getDecivilisedBorderOpacity(),
            KmuOwnerMapStyleSettings.getDecivilisedBorderWidth());
    }

    // Never-settled space stays outline-only: filling it would wash the whole sector, since
    // uninhabited cells cover everything no faction and no decivilised colony holds. Its outline's
    // on/off is the one style input that is not a LunaLib field - the sidebar's
    // uninhabited-systems checkbox, a per-save preference the rebuild samples and hands over -
    // while the opacity and width it strokes at stay settings-screen knobs read here.
    static CategoryStyle readUninhabitedStyle(boolean isUninhabitedOutlineDrawn) {

        return buildNeutralStyle(
            ElementStyle.NOT_DRAWN,
            isUninhabitedOutlineDrawn,
            KmuOwnerMapStyleSettings.getUninhabitedBorderOpacity(),
            KmuOwnerMapStyleSettings.getUninhabitedBorderWidth());
    }

    // One element's paint as the player authored it: a palette choice crossed into the selection
    // the draw pass resolves, paired with the opacity it paints at.
    //
    // Named because the crossing is the same step for all seven elements read here, and an inline
    // copy of it is what lets one element end up pointed at a slot its opacity was never chosen
    // against - a drift neither half shows on its own, both halves being right.
    private static ElementStyle resolvePaintedElement(OwnerPaletteChoice colour, double opacity) {

        return new ElementStyle(OwnerPaletteSlot.resolvePaintSelectionOf(colour), opacity);
    }

    // Assembles a factionless category's style: the given fill, its outline as the outer
    // border (pointed at the PRIMARY slot, or at nothing at all to hide it), and no inner seam -
    // factionless cells do not fuse into clusters, so they have no interior seams to stroke.
    // Both palette slots hold the neutral colour at draw time, so PRIMARY and SECONDARY would
    // paint identically; PRIMARY is the drawn arm throughout.
    //
    // The hidden arm is no selection rather than a no-colour one: this style is assembled here
    // rather than read from a player choice, so it can state the off case the way a style holds
    // it directly instead of routing a settings value through the crossing.
    private static CategoryStyle buildNeutralStyle(
            ElementStyle fill,
            boolean isOutlineDrawn,
            double outlineOpacity,
            double outlineWidth) {

        var outerPaint = isOutlineDrawn
            ? OwnerPaletteSlot.PRIMARY
            : null;

        return new CategoryStyle(
            fill,
            new ElementStyle(outerPaint, outlineOpacity),
            outlineWidth,
            ElementStyle.NOT_DRAWN,
            0);
    }
}
