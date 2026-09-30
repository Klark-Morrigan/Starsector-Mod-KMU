package kmu.maplayers.ownermap.owners;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;

import java.awt.Color;

/**
 * Everything the owner-map tier asks about an owner, answered by the layer that paints by it: the
 * owner's shades, its name, the crest its picker row carries, how far it recedes, which category
 * it draws in, and the two neutral shades the unowned and receded palettes are derived from.
 *
 * <p>The tier holds an owner key and nothing else about an owner. Without this seam it would have
 * to work those answers out for itself, which means reading them off whatever the key happens to
 * mean - and a key that names a faction on one layer names a relay's presence on another. So a
 * layer resolves one reading per rebuild and the tier asks it, never learning what an owner is.
 *
 * <p>One reading per rebuild, not per question: an answer may depend on a live source (which
 * factions stand together, say), and every answer the rebuild paints from has to come off one
 * sampling of it. A reading is therefore the layer's snapshot, and each method a lookup over it.
 *
 * <p>An owner ID reaching a method is one the layer's own owner source handed the tier, so a
 * reading need not answer for IDs it never produced.
 */
public interface OwnerReading {

    /**
     * The two shades an owner's cells may draw in.
     *
     * @param ownerId the owner to colour
     * @return the owner's shades, or null when it has none to give - an owner that is gone from the
     *         sector, which the surfaces asking drop rather than colour in a stand-in
     */
    OwnerPalette resolvePalette(String ownerId);

    /**
     * The label an owner reads by on the map and in the picker.
     *
     * @param ownerId    the owner to name
     * @param nameFormat whether the name reads in its short or full form; one of the drawn forms,
     *                   since the whole label build is skipped when the player's choice draws no
     *                   name at all
     * @return the owner's name, or null when none resolves, which the label fit treats as an
     *         unresolved name and sizes a stand-in band for instead
     */
    String resolveName(String ownerId, FactionNameFormatChoice nameFormat);

    /**
     * The crest an owner's picker row carries beside its name.
     *
     * @param ownerId the owner whose crest is drawn
     * @return the crest's sprite path, or null for an owner with no crest, whose row draws its name
     *         alone
     */
    String resolveCrestPath(String ownerId);

    /**
     * How an owner's fills, borders and name are dimmed or recoloured before the tier paints it,
     * applied wherever its category is read. A layer with no backdrop of its own answers
     * {@link ElementStyleAdjustment#NONE}; a layer setting some owners against a backdrop answers
     * the recede for the ones behind it. The tier applies what comes back without knowing why.
     *
     * @param ownerId       the owner to adjust
     * @param contentInputs the preferences this rebuild sampled, so a layer receding owners of its
     *                      own reads its recede out of the one sampling rather than off the stored
     *                      preference a second time
     * @return the adjustment the owner draws under; {@link ElementStyleAdjustment#NONE} to draw it
     *         exactly as categorised
     */
    ElementStyleAdjustment resolveStyleAdjustment(String ownerId, ContentInputs contentInputs);

    /**
     * Which of the layer's categories an owner draws in.
     *
     * <p>The owner's already-resolved {@code adjustment} is supplied so a layer that moves a
     * receded owner into a quieter category reads the one adjustment the rebuild actually paints
     * under - every reason to recede already folded in - rather than re-deriving it. A layer
     * answering from a narrower source than the palette resolves from would paint an owner in the
     * receded palette while leaving it in the full-strength category.
     *
     * @param ownerId    the owner to categorise
     * @param adjustment the dimming and recolouring this owner draws under, resolved ahead of this
     *                   so both read one decision
     * @return the category the owner draws in, one of those its layer's categories declare
     */
    MapStyleCategory resolveCategory(String ownerId, ElementStyleAdjustment adjustment);

    /**
     * The one shade a cell nobody owns paints in, in both slots, and the shade a spotlight lifts
     * such a cell from.
     *
     * @return the unowned shade
     */
    Color resolveUnownedColour();

    /**
     * The shades a receded owner sinks toward: the tier darkens them by the player's desaturation
     * strength into the one palette every desaturated owner recolours to, so the receded backdrop
     * reads as a single surface.
     *
     * @return the pair the receded palette is darkened from
     */
    OwnerPalette resolveRecedePalette();
}
