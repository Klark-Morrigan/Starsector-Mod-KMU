package kmu.maplayers.ownermap.render.rebuild;

import kmlib.colour.ColourPair;

import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.SpotlitOwners;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * The shades a standing build painted in, and each frame's question of whether the game still
 * answers them.
 *
 * <p>A build reads each owner's shades once and bakes them into the fills, borders, seams, names and
 * bands the frames then draw unchanged. Nothing else the ledger compares moves when a colour does,
 * so without this a recolour - the player picking their own colours, or a mod recolouring an owner
 * mid-game - stays off the map until something unrelated forces a rebuild.
 *
 * <p>Each owner's shades are held as the build's reading answered them, not as the owner carries
 * them. A rebuild then always ends with nothing moved, so a layer whose owners and reading disagree
 * about a shade costs no rebuild per frame.
 *
 * @param reading       the owner reading the build was painted by, asked again every frame
 * @param ownerPalettes each distinct owner the build painted, under the ID its reading answers to,
 *                      with the shades it answered then
 * @param unownedColour the shade the reading gave a cell nobody owns
 * @param recedePalette the shades the reading gave a receded owner to sink toward
 */
record PaintedPalettes(
    OwnerReading reading,
    List<OwnerPalette> ownerPalettes,
    Color unownedColour,
    ColourPair recedePalette) {

    /** What {@link #describeMovedPalette()} answers while every shade holds. */
    static final String NO_MOVED_PALETTE = "none";

    // How a line writes a shade or a pair the reading answered none for.
    private static final String ABSENT_SHADE = "nothing";

    /**
     * The shades one build painted in, read off its reading straight after the build.
     *
     * <p>The spotlit footprint is asked about under the picked owner's own ID, since its key names
     * no owner the reading knows.
     *
     * @param reading         the owner reading the build was painted by
     * @param owners          the owners the build painted, one per system it holds
     * @param selectedOwnerId the owner the build spotlit, as the sidebar picked it, or null where it
     *                        spotlit nobody
     * @return the shades, each distinct owner asked once
     */
    static PaintedPalettes sampleFrom(
            OwnerReading reading,
            Collection<SystemOwner> owners,
            String selectedOwnerId) {

        var askedOwnerIds = new LinkedHashSet<String>();

        for (var owner : owners) {

            askedOwnerIds.add(
                SpotlitOwners.isSpotlitOwner(owner.ownerId())
                    ? selectedOwnerId
                    : owner.ownerId());
        }

        var ownerPalettes = new ArrayList<OwnerPalette>();

        for (var ownerId : askedOwnerIds) {
            ownerPalettes.add(new OwnerPalette(ownerId, reading.resolvePalette(ownerId)));
        }

        return new PaintedPalettes(
            reading,
            List.copyOf(ownerPalettes),
            reading.resolveUnownedColour(),
            reading.resolveRecedePalette());
    }

    /**
     * Whether any shade the build painted in has moved since.
     *
     * <p>Asked every frame the map is open. Each owner is asked through
     * {@link OwnerReading#hasPalette}, so the walk builds no pair; the recede shades are the one
     * pair a frame builds.
     *
     * @return whether the reading now answers a shade other than the one painted
     */
    boolean hasAnyPaletteMoved() {

        if (!Objects.equals(reading.resolveUnownedColour(), unownedColour)
                || !Objects.equals(reading.resolveRecedePalette(), recedePalette)) {

            return true;
        }

        // Indexed rather than iterated, so the per-frame walk allocates no iterator.
        for (var index = 0; index < ownerPalettes.size(); index++) {

            var ownerPalette = ownerPalettes.get(index);
            if (!reading.hasPalette(ownerPalette.ownerId(), ownerPalette.palette())) {
                return true;
            }
        }

        return false;
    }

    /**
     * The first shade found moved, for the line a rebuild writes about why it ran.
     *
     * @return what moved and its two values, or {@link #NO_MOVED_PALETTE} where nothing has
     */
    String describeMovedPalette() {

        var liveUnownedColour = reading.resolveUnownedColour();
        if (!Objects.equals(liveUnownedColour, unownedColour)) {

            return "unowned from " + formatColour(unownedColour)
                + " to " + formatColour(liveUnownedColour);
        }

        var liveRecedePalette = reading.resolveRecedePalette();
        if (!Objects.equals(liveRecedePalette, recedePalette)) {

            return "recede from " + formatPalette(recedePalette)
                + " to " + formatPalette(liveRecedePalette);
        }

        for (var ownerPalette : ownerPalettes) {

            var ownerId = ownerPalette.ownerId();
            if (!reading.hasPalette(ownerId, ownerPalette.palette())) {

                return "owner " + ownerId
                    + " from " + formatPalette(ownerPalette.palette())
                    + " to " + formatPalette(reading.resolvePalette(ownerId));
            }
        }

        return NO_MOVED_PALETTE;
    }

    // A pair as its two shades, primary first.
    private static String formatPalette(ColourPair palette) {

        return palette == null
            ? ABSENT_SHADE
            : "(" + formatColour(palette.primaryColour()) + " / " + formatColour(palette.secondaryColour()) + ")";
    }

    // A shade as its four channels, red first.
    private static String formatColour(Color colour) {

        return colour == null
            ? ABSENT_SHADE
            : colour.getRed() + "," + colour.getGreen() + "," + colour.getBlue() + "," + colour.getAlpha();
    }

    /**
     * One owner's shades as the build's reading answered them.
     *
     * @param ownerId the ID the reading answers to - the picked owner's own for the spotlit footprint
     * @param palette the shades the reading answered, or null where it answered none
     */
    record OwnerPalette(
        String ownerId,
        ColourPair palette) {
    }
}
