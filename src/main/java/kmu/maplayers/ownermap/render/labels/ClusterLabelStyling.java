package kmu.maplayers.ownermap.render.labels;

import kmlib.colour.Colours;
import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.font.measure.LazyFontMeasurer;
import kmlib.starsector.ui.label.AspectLabelLengthEstimator;
import kmlib.starsector.ui.label.FontLabelLengthEstimator;
import kmlib.starsector.ui.label.LabelLengthEstimator;

import kmu.maplayers.base.labels.LabelFonts;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.owners.OwnerPalette;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.SpotlitBlocs;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;
import kmu.maplayers.ownermap.render.style.MapPalettes;
import kmu.maplayers.ownermap.render.style.OwnerCategories;
import kmu.maplayers.ownermap.render.style.OwnerStyleDecision;
import kmu.maplayers.ownermap.render.style.OwnerStyleResolver;

import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Resolves a cluster label's non-geometric attributes - its colour and its name - the way
 * the fills resolve theirs, so a label never drifts from the cluster group it names. The
 * placement search owns where a label sits; this owns what shade it draws in and which
 * owner's name it spells, and hands the search both as plain functions of an owner ID so the
 * search itself stays ignorant of owners.
 *
 * <p>Both attributes follow the owner reading off filter and the filter rules under one. The
 * colour is baked from the same {@link MapPalettes} mapping the border uses,
 * off the shared {@link OwnerStyleResolver#resolveBlocStyleDecision} the fills read - so a
 * desaturated owner's name recolours with its fill. The name comes from the reading, except that
 * a filter's synthetic spotlight key (which the reading cannot name) resolves back to the
 * selected owner. Both resolvers memoise per owner ID, since every cluster of an owner shares one
 * decision, colour, and name.
 */
final class ClusterLabelStyling {

    // The stand-in name shape (length as a multiple of line height) sized against when
    // no real measurement exists - the label font failed to load, or a cluster's owner
    // resolves no display name. A plausible faction-name proportion, so the debug band
    // still shows a realistic footprint; no name is drawn from a stand-in fit.
    private static final double FALLBACK_NAME_ASPECT = 6.0;

    // Resolves only; never instantiated.
    private ClusterLabelStyling() {
    }

    // The colour a cluster's name (and its debug dot) draws in: the colour choice of the name
    // style of the category the owner draws in - which, on the layers painting holders, is that
    // category's outer-border choice, so the name inherits the border's own colour rather than a
    // fixed bright pick - resolved against this owner's two shades: its own palette, or the pass's
    // shared desaturation palette when the adjustment desaturates this owner, by the same
    // MapPalettes mapping the border itself uses. A hidden choice ("No color") still needs a
    // legible name, so it falls back to the resolved primary shade. The same name style then picks
    // the name opacity, further scaled by the adjustment's opacity multiplier, and fades the
    // resolved colour by the product (the debug dot, sharing this colour, dims and recolours with
    // the name).
    static Color resolveLabelColour(
            SystemOwner owner,
            BlocNameStyles nameStyles,
            OwnerStyleDecision styleDecision,
            OwnerPalette desaturationPalette) {

        // One category pick drives both the name's colour choice and its opacity, so the two
        // can never be read from different categories.
        var nameStyle = nameStyles.resolveNameStyleOf(styleDecision.category());
        var choice = nameStyle.colour();

        // The name resolves against the same two shades the border does, off the one
        // "desaturate swaps the palette" decision MapPalettes owns - so the name can
        // never drift from the fill and border it labels.
        var palette = MapPalettes.resolveEffectivePalette(
            styleDecision.adjustment(),
            owner,
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

    // The per-owner label colours one rebuild draws in, as the plain colour-by-key function the
    // placement search takes. Each owner's shade is resolved from any one of its systems (every
    // system of an owner carries the same two palette shades, so the first one found speaks for
    // the whole owner) under that owner's shared style decision, and cached per owner ID since
    // every cluster of an owner draws its name the same.
    static Function<String, Color> newLabelColourResolver(
            Map<SystemKey, SystemOwner> ownerBySystemKey,
            BlocNameStyles nameStyles,
            Function<String, OwnerStyleDecision> styleDecisionByBlocId,
            OwnerPalette desaturationPalette) {

        var ownerByBlocId = mapHolderByBlocId(ownerBySystemKey);
        return memoisePerBlocId(blocId -> resolveLabelColour(
            ownerByBlocId.get(blocId),
            nameStyles,
            styleDecisionByBlocId.apply(blocId),
            desaturationPalette));
    }

    // The per-owner style decisions one rebuild applies: each owner's category and adjustment as
    // the shared resolver makes it (the reading's off filter, the filter rules under one), cached
    // per owner ID like the name estimator resolver below, since every cluster of an owner shares
    // one decision and the two label consumers both read it.
    static Function<String, OwnerStyleDecision> newBlocStyleDecisionResolver(
            OwnerReading reading,
            OwnerCategories categories,
            ContentInputs contentInputs) {

        return memoisePerBlocId(blocId -> OwnerStyleResolver.resolveBlocStyleDecision(
            blocId,
            reading,
            categories,
            contentInputs));
    }

    // The per-owner name estimators one rebuild fits against: each owner's name as the reading
    // resolves it, measured with the label font, or the aspect stand-in when the font or the name
    // will not resolve. Cached per owner ID because every cluster of an owner shares one name, so
    // its wrap is measured once per rebuild, not per cluster.
    //
    // The full/short choice comes off the rebuild's own sampling rather than being read here: it
    // decides the text every box is fitted around, so a name measured under a second reading of it
    // would size the boxes the fit accepted for a spelling the labels are not minted in.
    static Function<String, LabelLengthEstimator> newNameEstimatorResolver(
            OwnerReading reading,
            ContentInputs contentInputs) {

        var font = LabelFonts.loadMapLabelFont();
        var nameFormat = contentInputs.nameFormat();

        return memoisePerBlocId(blocId -> resolveNameEstimator(
            reading,
            font,
            nameFormat,
            resolveNameBlocId(
                contentInputs.isFiltering(),
                blocId,
                contentInputs.selectedBlocId())));
    }

    // Caches a per-owner resolution for the life of one rebuild. Every resolution here is
    // asked once per cluster but answers per owner - an owner with a homeland and three colonies
    // asks four times and gets one answer - so each is wrapped once rather than each growing
    // its own map and lookup. Single-threaded, like the rebuild that holds it.
    private static <T> Function<String, T> memoisePerBlocId(Function<String, T> resolver) {
        var valueByBlocId = new HashMap<String, T>();
        return blocId -> valueByBlocId.computeIfAbsent(blocId, resolver);
    }

    // One owner per owner ID, so an owner's shade can be resolved from its ID alone. Every system
    // of an owner resolves to the same two palette shades, so which of them is kept is
    // immaterial; first seen wins.
    private static Map<String, SystemOwner> mapHolderByBlocId(
            Map<SystemKey, SystemOwner> ownerBySystemKey) {

        var ownerByBlocId = new HashMap<String, SystemOwner>();
        for (var owner : ownerBySystemKey.values()) {
            ownerByBlocId.putIfAbsent(owner.ownerId(), owner);
        }
        return ownerByBlocId;
    }

    // The owner ID whose name a cluster's label reads: the selected owner under the filter's
    // synthetic spotlight key (the reading cannot name a synthetic ID), the key itself otherwise.
    // The whole spotlit footprint shares one key, so each disjoint spotlit cluster still carries its
    // own per-cluster label spelling the selected owner's name.
    private static String resolveNameBlocId(
            boolean isFiltering,
            String blocId,
            String selectedBlocId) {

        return isFiltering && SpotlitBlocs.isSpotlitBloc(blocId)
            ? selectedBlocId
            : blocId;
    }

    // One owner's name estimator: font-measured when both the font and a non-blank name
    // resolved, the aspect stand-in otherwise (a stand-in fit still sizes the debug band;
    // it wraps no lines, so no label is minted from it). The reading resolves the name for the
    // owner - an owner ID is not always what the label reads (a group's ID is not a faction ID),
    // so the lookup goes through the layer's answer rather than straight to the sector.
    private static LabelLengthEstimator resolveNameEstimator(
            OwnerReading reading,
            LazyFont font,
            FactionNameFormatChoice nameFormat,
            String blocId) {

        if (font == null) {
            return new AspectLabelLengthEstimator(FALLBACK_NAME_ASPECT);
        }
        var name = reading.resolveName(blocId, nameFormat);
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
