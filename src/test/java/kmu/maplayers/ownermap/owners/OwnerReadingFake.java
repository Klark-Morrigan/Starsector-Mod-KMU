package kmu.maplayers.ownermap.owners;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;
import kmu.maplayers.ownermap.render.style.OwnerMapCategory;

import java.awt.Color;
import java.util.Map;

/**
 * Test fixture: an owner reading answering from canned values rather than from a sector, so a case
 * about what the tier does with a layer's answers states those answers outright.
 *
 * <p>Every owner draws in one category under one adjustment - the answer most cases want, since
 * the per-owner rule is the layer's and not the tier's. A case whose subject is an owner-dependent
 * answer stubs a mock of the seam instead. Names, shades and crests are per owner, and an owner the
 * maps do not hold answers none, which is the unresolved case the seam allows.
 *
 * @param category         the category every owner draws in
 * @param adjustment       the adjustment every owner draws under
 * @param nameByOwnerId    each owner's name, whatever form is asked for
 * @param paletteByOwnerId each owner's shades
 * @param crestByOwnerId   each owner's crest sprite path
 * @param unownedColour    the shade a cell nobody owns paints in
 * @param recedePalette    the shades a receded owner sinks toward
 */
public record OwnerReadingFake(
    MapStyleCategory category,
    ElementStyleAdjustment adjustment,
    Map<String, String> nameByOwnerId,
    Map<String, OwnerPalette> paletteByOwnerId,
    Map<String, String> crestByOwnerId,
    Color unownedColour,
    OwnerPalette recedePalette) implements OwnerReading {

    /** The shade a reading built by the factories below paints unowned cells in. */
    public static final Color UNOWNED_COLOUR = Color.GRAY;

    /** The shades a reading built by the factories below sinks a receded owner toward. */
    public static final OwnerPalette RECEDE_PALETTE =
        new OwnerPalette(Color.LIGHT_GRAY, Color.DARK_GRAY);

    /**
     * A reading placing every owner at full strength, adjusting nothing and naming, colouring and
     * badging no owner - the reading a case about something other than the answers carries.
     *
     * @return the reading
     */
    public static OwnerReadingFake createAnsweringNothing() {
        return createNaming(Map.of());
    }

    /**
     * A reading naming owners from a canned map and answering nothing else of note.
     *
     * @param nameByOwnerId each owner's name
     * @return the reading
     */
    public static OwnerReadingFake createNaming(Map<String, String> nameByOwnerId) {

        return new OwnerReadingFake(
            OwnerMapCategory.FACTION,
            ElementStyleAdjustment.NONE,
            nameByOwnerId,
            Map.of(),
            Map.of(),
            UNOWNED_COLOUR,
            RECEDE_PALETTE);
    }

    /**
     * This reading with every owner in another category under another adjustment.
     *
     * @param newCategory   the category every owner draws in
     * @param newAdjustment the adjustment every owner draws under
     * @return the reading, otherwise as it was
     */
    public OwnerReadingFake withStyle(
            MapStyleCategory newCategory,
            ElementStyleAdjustment newAdjustment) {

        return new OwnerReadingFake(
            newCategory,
            newAdjustment,
            nameByOwnerId,
            paletteByOwnerId,
            crestByOwnerId,
            unownedColour,
            recedePalette);
    }

    /**
     * This reading with its owners' shades and crests stated.
     *
     * @param newPaletteByOwnerId each owner's shades
     * @param newCrestByOwnerId   each owner's crest sprite path
     * @return the reading, otherwise as it was
     */
    public OwnerReadingFake withLooks(
            Map<String, OwnerPalette> newPaletteByOwnerId,
            Map<String, String> newCrestByOwnerId) {

        return new OwnerReadingFake(
            category,
            adjustment,
            nameByOwnerId,
            newPaletteByOwnerId,
            newCrestByOwnerId,
            unownedColour,
            recedePalette);
    }

    @Override
    public OwnerPalette resolvePalette(String ownerId) {
        return paletteByOwnerId.get(ownerId);
    }

    @Override
    public String resolveName(String ownerId, FactionNameFormatChoice nameFormat) {
        return nameByOwnerId.get(ownerId);
    }

    @Override
    public String resolveCrestPath(String ownerId) {
        return crestByOwnerId.get(ownerId);
    }

    @Override
    public ElementStyleAdjustment resolveStyleAdjustment(
            String ownerId,
            ContentInputs contentInputs) {
        return adjustment;
    }

    @Override
    public MapStyleCategory resolveCategory(String ownerId, ElementStyleAdjustment ownerAdjustment) {
        return category;
    }

    @Override
    public Color resolveUnownedColour() {
        return unownedColour;
    }

    @Override
    public OwnerPalette resolveRecedePalette() {
        return recedePalette;
    }
}
