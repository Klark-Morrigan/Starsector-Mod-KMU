package kmu.maplayers.politicalmap.base.render.model;

import kmu.settings.DesaturationProfileChoice;

/**
 * The sector-wide render style - the tier of the {@link RenderStyle} theme that does not
 * vary by category or territory: the contested-fill {@link HatchStyle}, the national-border
 * {@link BorderSmoothingStyle}, and the {@link DesaturationProfileChoice} a receded bloc
 * recolours toward. Holding these once (rather than reading each ad hoc where it is used)
 * gives the global tier a single home; the builders read it off the drawables the same way
 * they read the per-category styles, so an incremental re-shape smooths and hatches against
 * the exact settings the full build baked in.
 */
public record GlobalStyle(HatchStyle hatch, BorderSmoothingStyle borderSmoothing,
        DesaturationProfileChoice desaturationProfile) {
}
