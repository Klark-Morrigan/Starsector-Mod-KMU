package kmu.maplayers.politicalmap.base.render.labels;

import kmlib.starsector.ui.font.LazyFontCache;
import kmlib.starsector.ui.font.StarsectorFont;

import org.lazywizard.lazylib.ui.LazyFont;

/**
 * Resolves the political-map label font. One home for the resolve so both consumers
 * see the same face: the anchor search ({@link ClusterAnchorsBuilder}) measures names
 * with its glyph metrics, and the label build ({@link LabelsBuilder}) mints the drawn
 * strings from it - the box the search sizes is the box those glyphs fill only because
 * both read the font here.
 *
 * <p>The face is fixed rather than a player choice. A name is stretched to span its
 * cluster, far past the face's glyph-atlas resolution, so only the highest-resolution
 * antialiased face the game ships ({@code insignia42LTaa}, a 42px atlas) stays clean
 * when magnified that far; a smaller atlas turns blocky. The load-once /
 * fail-once-logged caching lives in KMLib's {@link LazyFontCache}, shared with any
 * other mod that draws cached text.
 */
public final class LabelFonts {

    // The highest-resolution antialiased LazyFont face the game ships. Fixed because a
    // cluster-spanning name magnifies the atlas far past its native resolution, and only the
    // largest atlas survives that magnification cleanly.
    private static final StarsectorFont MAP_LABEL_FONT = StarsectorFont.VANILLA_INSIGNIA_42;

    // Resolves only; never instantiated.
    private LabelFonts() {
    }

    // The map-label face, loaded and cached by KMLib, or null when it cannot load
    // (a missing or malformed .fnt).
    public static LazyFont loadMapLabelFont() {
        return LazyFontCache.loadByFace(MAP_LABEL_FONT);
    }
}
