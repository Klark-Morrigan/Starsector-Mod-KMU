package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.highlight.Highlight;
import kmlib.starsector.ui.highlight.HighlightedParagraph;
import kmlib.starsector.ui.map.controls.MapFilterToggle;

import kmu.util.KmuStringKeys;

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

    // What separates one paragraph from the next. Wider than the pad above the first, because that
    // one only lifts the text off the tooltip's edge while this one has to read as a break: at the
    // same three units the three paragraphs run together as one block of prose, and the reader loses
    // the only cue that the middle one is a different kind of statement from the first.
    private static final float PARAGRAPH_GAP = 10f;

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

        // Its own paragraph rather than a third sentence: the first says what the box does, this
        // says who is answerable for it and how to be rid of it, and a player looking for the second
        // should not have to read the first to find it.
        new HighlightedParagraph(parts.composeUninstallNote(), parts.listUninstallHighlights())
            .addTo(tooltip, PARAGRAPH_GAP);

        // The consequence of doing it the other way round. Its opening word is the only run in the
        // shade the engine keeps for bad news, so the paragraph is flagged before it is read; the
        // rest takes the same two roles the paragraph above uses, names in one shade and the step
        // in the other, so the three paragraphs read as one vocabulary.
        new HighlightedParagraph(parts.composeUninstallWarning(), parts.listUninstallWarningHighlights())
            .addTo(tooltip, PARAGRAPH_GAP);
    }

    // The pieces the sentence is made of, read once. The same four settle both what the text says
    // and which of its runs are tinted, so they travel as one value rather than being handed
    // separately to two calls that must agree about them.
    private SentenceParts readSentenceParts() {

        return new SentenceParts(
            KmuStringKeys.get(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_LAYERS),
            KmuStringKeys.get(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_POLITICAL_MAP),
            listOfferedViews());
    }

    // The views the political map draws, in the order its own selector offers them. Held as
    // literals rather than read off the roster, which is what keeps this a lookup a per-frame
    // rebuild can afford; the day the roster is something a tooltip can name, this list goes.
    private List<ViewMention> listOfferedViews() {

        var views = new ArrayList<ViewMention>();

        views.add(ViewMention.named(
            KmuStringKeys.get(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_FACTIONS)));

        if (isAllianceViewOffered.getAsBoolean()) {

            views.add(ViewMention.named(
                KmuStringKeys.get(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_ALLIANCES)));
        }

        // The one view whose name needs a word in front of it to read as English, and that word is
        // not part of the name - so the phrase and the run tinted inside it part company here.
        var claimsName = KmuStringKeys.get(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_CLAIMS);

        views.add(new ViewMention(
            KmuStringKeys.format(
                KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_CLAIMS_QUALIFIED,
                claimsName),
            claimsName));

        return views;
    }

    /**
     * One view as the sentence mentions it: the words it takes up, and the run of them that is the
     * view's own name.
     *
     * <p>The two are apart because a name is not always the whole mention - a view the sentence has
     * to qualify carries a word the name does not own, and tinting that word would say the
     * qualifier were part of what the view is called.
     *
     * @param phrase        what the sentence reads at this point in its list
     * @param highlightedName the view's name inside that phrase, which is what is tinted
     */
    private record ViewMention(String phrase, String highlightedName) {

        /** A view whose whole mention is its name, which is every view but the qualified one. */
        static ViewMention named(String name) {
            return new ViewMention(name, name);
        }
    }

    /**
     * The wording the three paragraphs are built from, and the source of every run tinted inside
     * them.
     *
     * @param layersName what the feature is called, which every paragraph names
     * @param mapName    what the layers draw
     * @param views      the views that map offers, in the order its own selector offers them
     */
    private record SentenceParts(String layersName, String mapName, List<ViewMention> views) {

        /** @return the whole sentence, with the views joined into its closing list */
        String composeSentence() {

            return KmuStringKeys.format(
                KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE,
                layersName,
                mapName,
                String.join(
                    KmuStringKeys.get(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_VIEW_SEPARATOR),
                    views.stream().map(ViewMention::phrase).toList()));
        }

        /** @return the second paragraph: who is answerable for the feature, and nothing else */
        String composeUninstallNote() {

            return KmuStringKeys.format(
                KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL,
                KmuStringKeys.get(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_SUPPLIER));
        }

        /**
         * @return the third paragraph: what happens to a save the feature was pulled out from under,
         *         opened by the one word that says which kind of paragraph this is, and closed by the
         *         steps that avoid it. The procedure sits here rather than beside the attribution
         *         because it is only worth reading once the reader knows what it is for - "to do so"
         *         answers a question the sentence before it has just raised
         */
        String composeUninstallWarning() {

            return KmuStringKeys.format(
                KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING,
                KmuStringKeys.get(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING_LABEL),
                KmuStringKeys.get(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_SUPPLIER),
                KmuStringKeys.get(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING_ACTION),
                layersName,
                KmuStringKeys.get(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_DISABLE),
                KmuStringKeys.get(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_SETTINGS_MOD),
                KmuStringKeys.get(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_SAVE));
        }

        /**
         * @return the third paragraph's runs, in reading order: the flag in the shade for bad news,
         *         then names in one shade and steps in the other, alternating through the warning and
         *         on into the procedure that closes it
         */
        Highlight[] listUninstallWarningHighlights() {

            return new Highlight[] {
                flagRun(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING_LABEL),
                nameRun(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_SUPPLIER),
                stepRun(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_WARNING_ACTION),
                nameWord(layersName),
                stepRun(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_DISABLE),
                nameRun(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_SETTINGS_MOD),
                stepRun(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_UNINSTALL_SAVE),
            };
        }

        /** @return each run bound to the colour it takes, in the order the sentence reads them */
        Highlight[] listRunHighlights() {

            var highlights = new ArrayList<Highlight>();

            highlights.add(nameWord(layersName));
            highlights.add(nameWord(mapName));

            for (var view : views) {
                highlights.add(nameWord(view.highlightedName()));
            }

            return highlights.toArray(new Highlight[0]);
        }

        /**
         * @return the second paragraph's one run: the mod that answers for the feature, in the shade
         *         the other paragraphs give a name
         */
        Highlight[] listUninstallHighlights() {

            return new Highlight[] {
                nameRun(KmuStringKeys.MAP_LAYER_TOOLTIP_FILTER_ROW_TOGGLE_SUPPLIER),
            };
        }

        // The three roles the paragraphs tint by, each said once. Named for what a run is rather
        // than for the shade it takes: which colour marks a name is one decision, and spelling it
        // out at every run is where two paragraphs drift into disagreeing about it.
        private static Highlight flagRun(String stringId) {
            return Highlight.of(
                KmuStringKeys.get(stringId),
                StarsectorUiColour.VANILLA_HIGHLIGHT_RED.resolve());
        }

        private static Highlight nameRun(String stringId) {
            return nameWord(KmuStringKeys.get(stringId));
        }

        // A name already in hand rather than one to look up - the feature's own, which the parts
        // carry because every paragraph says it.
        private static Highlight nameWord(String name) {
            return Highlight.of(name, StarsectorUiColour.VANILLA_HIGHLIGHT_GOLD.resolve());
        }

        private static Highlight stepRun(String stringId) {
            return Highlight.of(
                KmuStringKeys.get(stringId),
                StarsectorUiColour.VANILLA_HIGHLIGHT_GREEN.resolve());
        }
    }
}
