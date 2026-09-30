package kmu.maplayers.base.labels;

import kmlib.starsector.ui.font.FontAtlas;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.installed.LazyFontCache;

import kmu.maplayers.base.faces.ProbedText;
import kmu.maplayers.base.faces.SettledFaces;

import org.lazywizard.lazylib.ui.LazyFont;

import java.util.Set;

/**
 * Resolves the map-label font. One home for the resolve so measuring and drawing see the
 * same face: a name is measured with its glyph metrics to size the box it will occupy, and
 * the drawn string is minted from it - the box the fit sizes is the box those glyphs fill
 * only because both read the font here, settled once per rebuild and handed to both.
 *
 * <p>The face asked for is fixed rather than a player choice. A name is stretched to span its
 * cluster, far past the face's glyph-atlas resolution, so only the highest-resolution
 * antialiased face the game ships ({@code insignia42LTaa}, a 42px atlas) stays clean
 * when magnified that far; a smaller atlas turns blocky. Where the installed atlas cannot draw
 * a faction's name - a localisation leaves that cut as vanilla ships it - the sector settles
 * the labels on the next cut down that can, blockier when stretched but readable. The load-once /
 * fail-once-logged caching lives in KMLib's {@link LazyFontCache}, shared with any
 * other mod that draws cached text.
 */
public final class LabelFonts {

    // The highest-resolution antialiased LazyFont face the game ships. Fixed because a
    // cluster-spanning name magnifies the atlas far past its native resolution, and only the
    // largest atlas survives that magnification cleanly.
    private static final StarsectorFont MAP_LABEL_FONT = StarsectorFont.VANILLA_INSIGNIA_42;

    // A label draws a faction's name and nothing else, so the face is held to faction names alone.
    private static final Set<ProbedText> MAP_LABEL_TEXTS = Set.of(ProbedText.FACTION_NAMES);

    // Resolves only; never instantiated.
    private LabelFonts() {
    }

    /**
     * @param settledFaces the faces settled on the sector the labels name
     * @return the face every label of that sector is measured and drawn in
     */
    public static FontAtlas settleMapLabelFace(SettledFaces settledFaces) {
        return settledFaces.settleFace(MAP_LABEL_FONT, MAP_LABEL_TEXTS);
    }

    /**
     * @param labelFace the face the labels were settled on
     * @return that face, loaded and cached by KMLib, or null when it cannot load (a missing or
     *         malformed .fnt)
     */
    public static LazyFont loadMapLabelFont(FontAtlas labelFace) {
        return LazyFontCache.loadByFace(labelFace);
    }
}
