package kmu.politicalmap.render;

import kmlib.starsector.ui.font.LazyFontCache;

import kmu.settings.KmuLunaSettings;

import org.lazywizard.lazylib.ui.LazyFont;

/**
 * Resolves the political-map faction-label font from the player's "Faction name font"
 * setting. One home for the resolve so both consumers see the same face: the anchor
 * search ({@link ClusterAnchorsBuilder}) measures names with its glyph metrics, and the
 * label build ({@link FactionLabelsBuilder}) mints the drawn strings from it - the box
 * the search sizes is the box those glyphs fill only because both read the font here.
 *
 * <p>Only the setting-to-basename resolve is KMU's; the load-once / fail-once-logged
 * caching lives in KMLib's {@link LazyFontCache}, shared with any other mod that draws
 * cached text.
 */
final class LabelFonts {

    // Resolves only; never instantiated.
    private LabelFonts() {
    }

    // The face the "Faction name font" setting currently selects, loaded and cached by
    // KMLib, or null when that face cannot load (a missing or malformed .fnt).
    static LazyFont loadConfiguredFont() {
        return LazyFontCache.loadByBasename(KmuLunaSettings.getPoliticalMapFactionNameFont());
    }
}
