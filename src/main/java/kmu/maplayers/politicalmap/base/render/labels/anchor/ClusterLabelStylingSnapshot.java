package kmu.maplayers.politicalmap.base.render.labels.anchor;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.ContentInputs;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;

import java.util.Map;

/**
 * Everything one label rebuild resolves a cluster's colour and name from: who holds each system,
 * the shades a desaturated bloc recolours to, the view and grouping the holding was classified
 * under, and the picks the pass was baked under - the spotlight the rest of the sector recedes by,
 * and the format a name is spelled in.
 *
 * <p>Held as one value because the four have to describe the same moment. A rebuild that named
 * blocs under this pass's view while receding them by the last pass's filter would produce
 * labels that disagree with the fills beneath them, and the disagreement would show as a colour,
 * not as an error. Passing them one by one is what makes that possible; passing the snapshot
 * makes it unrepresentable.
 *
 * <p>The two paths that produce one differ in where the holding comes from - the production
 * build hands over the territories it just built, the border-tracing diagnostic resolves holders
 * straight from the sector and spotlights nothing - and agree on everything downstream, which is
 * the point of them meeting in one type.
 *
 * @param holderBySystemKey   the dominant holder of each drawn system, keyed by {@link SystemKey}
 * @param desaturationPalette the shared shades a desaturated bloc's name recolours to
 * @param viewGrouping        the view painted and the grouping snapshot holding was resolved under
 * @param contentInputs       the picks the pass was baked under: the spotlight names and shades
 *                            recede by, and the name format they are spelled and fitted in
 */
public record ClusterLabelStylingSnapshot(
    Map<SystemKey, DominantHolder> holderBySystemKey,
    FactionPalette desaturationPalette,
    ViewGrouping viewGrouping,
    ContentInputs contentInputs) {

    /**
     * The snapshot a production rebuild styles by: the territories' own retained state, read off
     * the one build whose fills and borders the labels must match.
     *
     * @param territories the territories built this pass
     * @return the styling snapshot those territories were built under
     */
    public static ClusterLabelStylingSnapshot resolveFrom(PoliticalMapTerritories territories) {
        return new ClusterLabelStylingSnapshot(
            territories.getHolderBySystemKey(),
            territories.getDesaturationPalette(),
            territories.getViewGrouping(),
            territories.getContentInputs());
    }
}
