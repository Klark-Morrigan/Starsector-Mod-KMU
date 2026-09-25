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
 * <p>Every one of these choices, and a view's own recede set too, is sidebar-only: driven solely by an
 * overlay control, never a settings-screen field, so it persists in sector memory - each save keeps
 * its own choice and it survives reload - rather than as a LunaLib field, which would render on a
 * settings tab duplicating the sidebar control, and whose key would not round-trip unregistered.
 * Supplementary tuning (how far Mute dims, the outline's opacity and width) stays a LunaLib knob, being
 * a screen control rather than the toggle itself.
 *
 * <p>For the same reason each writer repaints only on a real write: before the sector exists the write
 * no-ops and reports none, so nothing bumps a revision no overlay would read. The refresh it raises
 * stands in for the settings revision, which a choice that is not a LunaLib field never moves.
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
