package kmu.maplayers.ownermap.owners.holders.base.picker;

/**
 * The numbers of a vocabulary leading with the number its layer paints by, for a case proving one
 * shared sort mode binds into more than one vocabulary.
 *
 * <p>The same shape as {@link CountedBlocMetricsFake}; only the type tells the two apart. The shared
 * mode is generic over the metrics record, so binding it over two distinct records is what shows it
 * reads the one number they share and nothing either declares for itself.
 *
 * @param lead       the number this vocabulary leads with
 * @param marketSize the bloc's summed colony size, which every holder vocabulary carries
 */
public record LeadingBlocMetricsFake(
    int lead,
    int marketSize)
        implements SizedBlocMetrics {
}
