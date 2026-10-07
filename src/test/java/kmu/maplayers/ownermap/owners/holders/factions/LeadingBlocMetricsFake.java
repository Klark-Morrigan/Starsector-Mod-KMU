package kmu.maplayers.ownermap.owners.holders.factions;

/**
 * One vocabulary's numbers for a case proving one shared sort mode binds into more than one: a
 * number it leads with, and the size every holder vocabulary carries.
 *
 * <p>Beside {@link CountedBlocMetricsFake} rather than in its place: a shared mode has to survive
 * being bound over vocabularies that agree on nothing but the number it is about, so a case needs
 * two of them.
 *
 * @param lead       the number this vocabulary leads with
 * @param marketSize the bloc's summed colony size
 */
public record LeadingBlocMetricsFake(
    int lead,
    int marketSize)
        implements SizedBlocMetrics {
}
