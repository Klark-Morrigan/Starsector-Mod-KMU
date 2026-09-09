package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Every piece of water the construction fills, layer by layer.
 *
 * <p>One answer to "what does this map paint over the void". A drawing needs the layers apart,
 * because each is behind a switch of its own; a report asking what the picture failed to cover
 * needs them together. Both are the same list read two ways, and each arriving at its own would
 * let a report call a patch bare that the map is plainly painting.
 *
 * <p>Opened against one colouring and one shaping, since both change the answer. A pocket ringed
 * entirely by one owner is pushed out to meet that owner's fills instead of keeping the channel,
 * so the shapes move with who holds the cells around them - which is why a report about the
 * SHAPES asks with every site unowned. And the channel itself is what the shaping decides: at
 * the void's true extent nothing is given up anywhere, which is a different map from the one a
 * reader is looking at.
 *
 * <p>Each layer is found on the first ask and kept, the way the walls it stands over are. A
 * window fills two or three of these at a time and a report wants all of them, so neither an
 * eager pass nor a search per call is right.
 */
public final class FilledWater {

    private final BridgedContinents laid;
    private final List<String> ownerBySite;
    private final VoidPockets.PocketShaping shaping;

    // Null until asked for, which is what tells "not yet found" from "found nothing" - an empty
    // layer is a real answer, and a sector with no lakes would otherwise pay for the lake walk
    // on every frame that mentions it.
    private List<List<double[]>> shoreWater;
    private List<List<double[]>> inletWater;
    private List<List<double[]>> lakeWater;
    private List<List<double[]>> puddleWater;
    private List<List<double[]>> linkWater;
    private List<LakeMargin> lakeMargins;

    FilledWater(
            BridgedContinents laid,
            List<String> ownerBySite,
            VoidPockets.PocketShaping shaping) {

        this.laid = laid;
        this.ownerBySite = ownerBySite;
        this.shaping = shaping;
    }

    /**
     * One lake's margin: the water between the shore the map draws and the cells' own edge.
     *
     * <p>Two rings rather than one because the middle is deliberately not filled. What the
     * margin shows is what the drawn line conceded against the true edge; the open water inside
     * the shore is left to whatever else fills it, or to the backdrop where nothing does.
     *
     * <p>Against the ROUNDED shore, which is the one place water is measured from a rounded line
     * rather than from a border: the margin exists to meet the stroke on screen, so a band ending
     * at the border would leave a sliver bare wherever the rounding stepped inside it.
     *
     * @param waterEdge the cells' own arcs around the lake, which the margin runs out to
     * @param drawnShore the shore as the map strokes it, which the margin stops at
     */
    public record LakeMargin(
        List<double[]> waterEdge,
        List<double[]> drawnShore) {
    }

    /**
     * The void the outer shores shut in behind them.
     *
     * @return one ring per outline, in the order the pockets were found
     */
    public List<List<double[]>> collectShoreWater() {

        if (shoreWater == null) {

            var rings = new ArrayList<List<double[]>>();

            for (var walled : CoastPockets.findCoastPockets(
                    laid.traceCoasts(),
                    ownerBySite,
                    new VoidPockets.PocketRules(laid.parameters(), shaping))) {

                rings.addAll(walled.pocket().outlines());
            }
            shoreWater = List.copyOf(rings);
        }
        return shoreWater;
    }

    /**
     * The bays the inlet spans hold, on the side of a continent that faces the open void.
     *
     * @return one ring per pocket
     */
    public List<List<double[]>> collectInletWater() {

        if (inletWater == null) {
            inletWater = fillBehindSpans(laid.layInletSpans());
        }
        return inletWater;
    }

    /**
     * The water the lake spans hold: a crossed lake cut into the finer pockets its spans make.
     *
     * @return one ring per pocket
     */
    public List<List<double[]>> collectLakeWater() {

        if (lakeWater == null) {
            lakeWater = fillBehindSpans(laid.layLakeSpans());
        }
        return lakeWater;
    }

    /**
     * The puddles, each as its whole water.
     *
     * <p>The whole of it, where a lake gives up its middle, because the two say different
     * things. A puddle has no shore to concede anything against, and water left to the backdrop
     * reads as open void - which is exactly what a puddle is not.
     *
     * @return one ring per puddle
     */
    public List<List<double[]>> collectPuddleWater() {

        if (puddleWater == null) {

            puddleWater = laid.traceCoasts().puddles().stream()
                .map(Coastlines.Puddle::waterEdge)
                .toList();
        }
        return puddleWater;
    }

    /**
     * The sea a run of links shut in between two continents.
     *
     * <p>Walled by the inlet spans as well as by the links, since those are the remaining lines
     * such a sea can come to rest against.
     *
     * @return one ring per pocket
     */
    public List<List<double[]>> collectLinkWater() {

        if (linkWater == null) {

            linkWater = IntercontinentalPockets.findLinkWalledPockets(
                laid.traceCoasts(),
                laid.layLinks(),
                laid.layInletSpans(),
                new VoidPockets.PocketRules(laid.parameters(), shaping));
        }
        return linkWater;
    }

    /**
     * Each lake's margin, paired with the shore the map draws for it.
     *
     * @return one margin per lake, in the order the lakes were traced
     */
    public List<LakeMargin> collectLakeMargins() {

        if (lakeMargins == null) {

            var lakes = laid.traceCoasts().lakes();
            var shores = laid.roundCoasts().lakes();
            var margins = new ArrayList<LakeMargin>(lakes.size());

            for (var index = 0; index < lakes.size(); index++) {
                margins.add(new LakeMargin(lakes.get(index).waterEdge(), shores.get(index)));
            }
            lakeMargins = List.copyOf(margins);
        }
        return lakeMargins;
    }

    /**
     * Which map this water was opened against: the one as drawn, or the void's true extent.
     *
     * @return the shaping every layer here is found at
     */
    public VoidPockets.PocketShaping shaping() {
        return shaping;
    }

    // What one set of spans shut in, with those spans as the only walls. Only what a span
    // actually walled: the walk finds the water the cells closed unaided as well, and here that
    // water is a lake or a puddle with a layer of its own - drawn from this list too it would be
    // painted twice and go on being painted with its own switch off.
    private List<List<double[]>> fillBehindSpans(List<CellGap> spans) {

        return spans.isEmpty()
            ? List.of()
            : VoidBridgePockets.findBridgeWalledPockets(
                laid.traceCoasts().union().sites(), spans, laid.parameters(), shaping);
    }
}
