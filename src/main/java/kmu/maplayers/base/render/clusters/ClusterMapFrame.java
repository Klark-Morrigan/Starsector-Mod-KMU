package kmu.maplayers.base.render.clusters;

import kmu.maplayers.base.render.MapFrame;

/**
 * One frame of the cluster overlay: what there is to paint, and the frame it is painted in.
 *
 * <p>The two travel together through every pass of the overlay and neither varies within a frame,
 * so carrying them as one value leaves each pass's own signature to say what that pass actually
 * decides - the primitive it emits, the run it selects, the stroke it strokes with - rather than
 * restating the ambient pair in front of it.
 *
 * <p>The scale and fade half is {@link MapFrame}, shared with every other pass of the map; what is
 * local to this overlay is only its pairing with the draw lists.
 *
 * @param drawLists the already-baked runs this frame paints, opaque to the emission
 * @param mapFrame  the scale and fade this frame emits under
 */
record ClusterMapFrame(
    ClusterDrawLists drawLists,
    MapFrame mapFrame) {

    // The frame's two values read through, since nearly every emission below needs one of them and
    // this record is the single argument each of those passes takes. Read-through rather than a
    // second copy of the pair: there is one door to either value, so these cannot come to disagree
    // with the frame they are read from.

    float getFactor() {
        return mapFrame.factor();
    }

    float getAlphaMult() {
        return mapFrame.alphaMult();
    }
}
