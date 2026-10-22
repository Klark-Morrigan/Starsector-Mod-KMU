package kmu.maplayers.base.ribbon;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

/**
 * A finished builder's runs, which a plan takes over as they stand rather than copying.
 *
 * <p>Safe to take over because nothing else can reach the list beneath: the builder that filled it
 * hands it over once and refuses every append after, and this view lends it out read-only. So the
 * one guarantee a copy buys - that no caller can lengthen a ribbon already planned - holds without
 * one, and a plan laid run by run is held once rather than twice.
 *
 * <p>Package-private, because the guarantee is only as good as the builder's: a list wrapped here
 * by anything else could still be written through by whoever holds it.
 */
final class AdoptedRibbonSegments
        extends AbstractList<RibbonSegment>
        implements RandomAccess {

    private final List<RibbonSegment> segments;

    /**
     * @param segments the finished builder's own runs, which no caller holds once handed over
     */
    AdoptedRibbonSegments(List<RibbonSegment> segments) {
        this.segments = segments;
    }

    @Override
    public RibbonSegment get(int index) {
        return segments.get(index);
    }

    @Override
    public int size() {
        return segments.size();
    }
}
