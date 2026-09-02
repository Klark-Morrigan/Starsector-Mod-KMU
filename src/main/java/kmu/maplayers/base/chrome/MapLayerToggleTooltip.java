package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.highlight.Highlight;
import kmlib.starsector.ui.highlight.HighlightedParagraph;
import kmlib.starsector.ui.map.controls.MapFilterToggle;

import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * What the tick box on the game's filter row says when the cursor rests on it.
 *
 * <p>Six of the eight buttons already on that row carry a hover tooltip. A seventh standing among
 * them with nothing to say is the one thing on the strip that does not behave like the rest, on the
 * one screen where behaving like the rest is the whole point of putting it there.
 *
 * <p>It is also the only surface on which the box can say who put it there. The control is dressed
 * as the game's own furniture and stands among the game's own buttons, so a player wondering where
 * it came from has nowhere else to look - hence the aside naming the mod, and hence the aside being
 * the one run that recedes rather than highlights: it answers a question without asking to be read
 * as part of the sentence.
 *
 * <p>The sentence is assembled from the same runs it highlights rather than restating them, so a
 * highlighted run is a substring of the text by construction. A run that merely happened to match
 * would fall silent the moment either half was reworded, and the substrate gives no sign of it -
 * an unmatched highlight is simply not drawn.
 *
 * <p>Which views the sentence lists is asked afresh on every hover. The engine rebuilds a tooltip's
 * body while it is up, so the body must stay a lookup and an array - nothing that walks the sector -
 * and in return everything it reads is live at no cost.
 */
final class MapLayerToggleTooltip {

    // What the game's own six use, so the box's hover is the width of its neighbours'. Private
    // because the width is part of what this says rather than something a caller chooses: the
    // hanging happens here too, so nothing outside has to hold a number it cannot judge.
    private static final float TOOLTIP_WIDTH = 300f;

    // The gap vanilla leaves above a tooltip's first paragraph, matched so the one box that is not
    // the game's does not sit its text differently from the six that are.
    private static final float PARAGRAPH_PAD = 3f;

    // Whether the political map offers an alliances view at all. Asked rather than stated, the view
    // itself being registered only where the mod that keeps alliances is installed - so an install
    // without it is not told about a view it does not have.
    private final BooleanSupplier isAllianceViewOffered;

    /**
     * @param isAllianceViewOffered whether the alliances view is among the ones the political map
     *                              registered, which is what decides whether the sentence lists it
     */
    MapLayerToggleTooltip(BooleanSupplier isAllianceViewOffered) {
        this.isAllianceViewOffered = isAllianceViewOffered;
    }

    /**
     * Hangs this on a box standing on the game's filter row.
     *
     * @param toggle the box to give a hover to
     */
    void attachTo(MapFilterToggle toggle) {

        toggle.attachTooltip(TOOLTIP_WIDTH, this::describeToggle);
    }

    /**
     * Writes the box's one paragraph into a tooltip the engine has opened.
     *
     * @param tooltip the surface to paint into, supplied afresh on every hover
     */
    void describeToggle(TooltipMakerAPI tooltip) {

        var parts = readSentenceParts();

        new HighlightedParagraph(parts.composeSentence(), parts.listRunHighlights())
            .addTo(tooltip, PARAGRAPH_PAD);
    }

    // The pieces the sentence is made of, read once. The same four settle both what the text says
    // and which of its runs are tinted, so they travel as one value rather than being handed
    // separately to two calls that must agree about them.
    private SentenceParts readSentenceParts() {

        return new SentenceParts(
            KmuStrings.get(KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_LAYERS),
            KmuStrings.get(KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_SUPPLIER),
            KmuStrings.get(KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_POLITICAL_MAP),
            listOfferedViewNames());
    }

    // The views the political map draws, in the order its own selector offers them. Held as
    // literals rather than read off the roster, which is what keeps this a lookup a per-frame
    // rebuild can afford; the day the roster is something a tooltip can name, this list goes.
    private List<String> listOfferedViewNames() {

        var viewNames = new ArrayList<String>();

        viewNames.add(KmuStrings.get(KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_FACTIONS));

        if (isAllianceViewOffered.getAsBoolean()) {

            viewNames.add(
                KmuStrings.get(KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_ALLIANCES));
        }

        viewNames.add(KmuStrings.get(KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_CLAIMS));

        return viewNames;
    }

    /**
     * The wording the sentence is built from, and the source of every run tinted inside it.
     *
     * @param layersName    what the feature is called
     * @param supplierAside the parenthetical naming the mod that supplies it
     * @param mapName       what the layers draw
     * @param viewNames     the views that map offers, in the order its own selector offers them
     */
    private record SentenceParts(
        String layersName,
        String supplierAside,
        String mapName,
        List<String> viewNames) {

        /** @return the whole sentence, with the views joined into its closing list */
        String composeSentence() {

            return KmuStrings.format(
                KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE,
                layersName,
                supplierAside,
                mapName,
                String.join(
                    KmuStrings.get(KmuStrings.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_SEPARATOR),
                    viewNames));
        }

        /** @return each run bound to the colour it takes, in the order the sentence reads them */
        Highlight[] listRunHighlights() {

            var highlightColour = StarsectorUiColour.VANILLA_HIGHLIGHT_GOLD.resolve();
            var highlights = new ArrayList<Highlight>();

            highlights.add(Highlight.of(layersName, highlightColour));

            // The one run that recedes: naming the mod is what the aside is for, and a mod's name
            // said in the same colour as the feature would read as part of it.
            highlights.add(Highlight.of(supplierAside, StarsectorUiColour.VANILLA_GRAY.resolve()));
            highlights.add(Highlight.of(mapName, highlightColour));

            for (var viewName : viewNames) {
                highlights.add(Highlight.of(viewName, highlightColour));
            }

            return highlights.toArray(new Highlight[0]);
        }
    }
}
