package kmu.maplayers.politicalmap.base.render.labels.anchor;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.colour.Colours;
import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.ui.font.LazyFontMeasurer;
import kmlib.starsector.ui.label.AspectLabelLengthEstimator;
import kmlib.starsector.ui.label.FontLabelLengthEstimator;
import kmlib.starsector.ui.label.LabelLengthEstimator;

import kmu.maplayers.base.labels.LabelFonts;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.render.style.BlocStyleDecision;
import kmu.maplayers.politicalmap.base.render.style.BlocStyleResolver;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;

import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Resolves a cluster label's non-geometric attributes - its colour and its name - the way
 * the fills resolve theirs, so a label never drifts from the territory it names. The
 * placement search owns where a label sits; this owns what shade it draws in and which
 * bloc's name it spells, and hands the search both as plain functions of a bloc id so the
 * search itself stays ignorant of blocs.
 *
 * <p>Both attributes follow the active view off filter and the filter rules under one. The
 * colour is baked from the same {@link MapPalettes} mapping the border uses,
 * off the shared {@link BlocStyleResolver#resolveBlocStyleDecision} the fills read - so a
 * desaturated bloc's name recolours with its fill. The name comes from the view, except that
 * a filter's synthetic spotlight key (which the view cannot name) resolves back to the
 * selected bloc. Both resolvers memoise per bloc id, since every cluster of a bloc shares one
 * decision, colour, and name.
 */
final class ClusterLabelStyling {

    // The stand-in name shape (length as a multiple of line height) sized against when
    // no real measurement exists - the label font failed to load, or a cluster's holder
    // resolves no display name. A plausible faction-name proportion, so the debug band
    // still shows a realistic footprint; no name is drawn from a stand-in fit.
    private static final double FALLBACK_NAME_ASPECT = 6.0;

    // Resolves only; never instantiated.
    private ClusterLabelStyling() {
    }

    // The colour a cluster's name (and its debug dot) draws in: the shade the holder's
    // national border resolves to, so the name inherits the border's own colour rather
    // than a fixed bright pick. A bloc drawn in the independent style carries the
    // independent outer-border choice, every other bloc the faction one, resolved against
    // this bloc's two shades - the holder's own palette, or the pass's shared desaturation
    // palette when the adjustment desaturates this bloc - by the same MapPalettes
    // mapping the border itself uses. A hidden border ("No color") still needs a legible
    // name, so it falls back to the resolved primary shade. The same faction-vs-independent
    // split then picks the group's name opacity, further scaled by the adjustment's opacity
    // multiplier, and fades the resolved colour by the product (the debug dot, sharing this
    // colour, dims and recolours with the name).
    static Color resolveLabelColour(
            DominantHolder holder,
            BlocNameStyles nameStyles,
            BlocStyleDecision styleDecision,
            FactionPalette desaturationPalette) {

        // One group pick drives both the name's colour choice and its opacity, so the two
        // can never be read from different groups.
        var nameStyle = styleDecision.usesIndependentStyle()
            ? nameStyles.independentNameStyle()
            : nameStyles.factionNameStyle();
        var choice = nameStyle.colour();

        // The name resolves against the same two shades the border does, off the one
        // "desaturate swaps the palette" decision MapPalettes owns - so the name can
        // never drift from the fill and border it labels.
        var palette = MapPalettes.resolveEffectivePalette(
            styleDecision.adjustment(),
            holder,
            desaturationPalette);

        var colour = MapPalettes.pickPaletteColour(
            choice,
            palette);

        var resolved = colour != null
            ? colour
            : palette.primaryColour();

        // The name mutes through the same one rule the fill and border do, so a receded name
        // dims in lockstep with the space it labels.
        var mutedOpacity = styleDecision.adjustment().muteOpacity(nameStyle.opacity());
        return Colours.scaleAlpha(resolved, mutedOpacity);
    }

    // The per-bloc label colours one rebuild draws in, as the plain colour-by-key function the
    // placement search takes. Each bloc's shade is resolved from any one of its holders (every
    // system of a bloc carries the same two palette shades, so the first one found speaks for
    // the whole bloc) under that bloc's shared style decision, and cached per bloc id since
    // every cluster of a bloc draws its name the same.
    static Function<String, Color> newLabelColourResolver(
            Map<String, DominantHolder> ownerBySystemId,
            BlocNameStyles nameStyles,
            Function<String, BlocStyleDecision> styleDecisionByBlocId,
            FactionPalette desaturationPalette) {

        var ownerByBlocId = mapHolderByBlocId(ownerBySystemId);
        return memoisePerBlocId(blocId -> resolveLabelColour(
            ownerByBlocId.get(blocId),
            nameStyles,
            styleDecisionByBlocId.apply(blocId),
            desaturationPalette));
    }

    // The per-bloc style decisions one rebuild applies: each bloc's independent-style and
    // adjustment call as the shared resolver makes it (the active view's off filter, the filter
    // rules under one), cached per bloc id like the name estimator resolver below, since every
    // cluster of a bloc shares one decision and the two label consumers both read it.
    static Function<String, BlocStyleDecision> newBlocStyleDecisionResolver(
            boolean isFiltering,
            PoliticalMapView view,
            HolderGrouping grouping,
            ElementStyleAdjustment recedeAdjustment) {

        return memoisePerBlocId(blocId -> BlocStyleResolver.resolveBlocStyleDecision(
            isFiltering,
            blocId,
            view,
            grouping,
            recedeAdjustment));
    }

    // The per-bloc name estimators one rebuild fits against: each bloc's display name
    // (a faction's under the faction view, an alliance's under the alliances view) as the
    // active view resolves it, measured with the label font, or the aspect stand-in when
    // the font or the name will not resolve. Cached per bloc id because every cluster of a
    // bloc shares one name, so its wrap is measured once per rebuild, not per cluster.
    static Function<String, LabelLengthEstimator> newNameEstimatorResolver(
            SectorAPI sector,
            PoliticalMapView view,
            HolderGrouping grouping,
            boolean isFiltering,
            String selectedBlocId) {

        var font = LabelFonts.loadMapLabelFont();

        // Read once per rebuild, like the font: every cluster of a bloc spells its name
        // the same way, so the full/short choice is resolved here rather than per bloc.
        var nameFormat = NameFormatPreference.getSelectedNameFormat();
        return memoisePerBlocId(blocId -> resolveNameEstimator(
            sector,
            view,
            grouping,
            font,
            nameFormat,
            resolveNameBlocId(isFiltering, blocId, selectedBlocId)));
    }

    // Caches a per-bloc resolution for the life of one rebuild. Every resolution here is
    // asked once per cluster but answers per bloc - a bloc with a homeland and three colonies
    // asks four times and gets one answer - so each is wrapped once rather than each growing
    // its own map and lookup. Single-threaded, like the rebuild that holds it.
    private static <T> Function<String, T> memoisePerBlocId(Function<String, T> resolver) {
        var valueByBlocId = new HashMap<String, T>();
        return blocId -> valueByBlocId.computeIfAbsent(blocId, resolver);
    }

    // One holder per bloc id, so a bloc's shade can be resolved from its id alone. Every system
    // of a bloc resolves to the same two palette shades, so which of them is kept is
    // immaterial; first seen wins.
    private static Map<String, DominantHolder> mapHolderByBlocId(
            Map<String, DominantHolder> ownerBySystemId) {

        var ownerByBlocId = new HashMap<String, DominantHolder>();
        for (var holder : ownerBySystemId.values()) {
            ownerByBlocId.putIfAbsent(holder.factionId(), holder);
        }
        return ownerByBlocId;
    }

    // The bloc id whose name a cluster's label reads: the selected bloc under the filter's synthetic
    // spotlight key (the view cannot name a synthetic id), the group key itself otherwise. The whole
    // spotlit footprint shares one key, so each disjoint spotlit cluster still carries its own
    // per-cluster label spelling the selected bloc's name.
    private static String resolveNameBlocId(
            boolean isFiltering,
            String blocId,
            String selectedBlocId) {

        return isFiltering && FilteredPolitics.isSpotlitBloc(blocId)
            ? selectedBlocId
            : blocId;
    }

    // One bloc's name estimator: font-measured when both the font and a non-blank name
    // resolved, the aspect stand-in otherwise (a stand-in fit still sizes the debug band;
    // it wraps no lines, so no label is minted from it). The active view resolves the name
    // for the bloc - a faction id is not always what the label reads (an alliance bloc id
    // is not a faction id), so the lookup goes through the view, not straight to the sector.
    private static LabelLengthEstimator resolveNameEstimator(
            SectorAPI sector,
            PoliticalMapView view,
            HolderGrouping grouping,
            LazyFont font,
            FactionNameFormatChoice nameFormat,
            String blocId) {
                
        if (font == null) {
            return new AspectLabelLengthEstimator(FALLBACK_NAME_ASPECT);
        }
        var name = view.resolveName(blocId, grouping, sector, nameFormat);
        if (name == null || name.isBlank()) {
            return new AspectLabelLengthEstimator(FALLBACK_NAME_ASPECT);
        }
        // LazyFontMeasurer (KMLib) reads the concrete font's calcWidth behind the
        // LineWidthMeasurer port, so the name-measuring estimator stays independent of
        // the font itself.
        return new FontLabelLengthEstimator(
            new LazyFontMeasurer(font),
            name);
    }
}
