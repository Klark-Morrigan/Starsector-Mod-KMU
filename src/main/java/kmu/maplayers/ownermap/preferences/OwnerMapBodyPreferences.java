package kmu.maplayers.ownermap.preferences;

import kmu.maplayers.ownermap.OwnerPaintedView;

/**
 * The per-save choices an owner-painted layer's body offers every one of its views: how cluster names
 * are spelled, whether uninhabited systems are outlined, and how the rest of the sector recedes behind
 * a spotlight.
 *
 * <p>One value a layer builds and hands over rather than three the tier finds for itself, because the
 * keys they are stored under are the layer's: two layers offering the same controls must store them
 * apart, and only the layer can name its own slots. The controls that write them and the rebuild that
 * reads them are both handed this one value, so a pick cannot be written to one layer's slot and read
 * from another's.
 *
 * <p>A view's own backdrop - one only some views recede - is not here: that is the view's to hold, and
 * the tier asks the painting view for it ({@link OwnerPaintedView#resolveViewRecedeAdjustment}).
 *
 * @param nameFormat         how cluster labels spell their holders' names
 * @param uninhabitedOutline whether never-settled space strokes its outline
 * @param filterRecede       how the rest of the sector recedes behind a spotlighted bloc
 */
public record OwnerMapBodyPreferences(
    NameFormatPreference nameFormat,
    UninhabitedOutlinePreference uninhabitedOutline,
    RecedePreferences filterRecede) {
}
