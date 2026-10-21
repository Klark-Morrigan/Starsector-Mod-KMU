package kmu.maplayers.ownermap.owners.holders.base.picker;

/**
 * The numbers of a vocabulary leading with a bare count, for a case proving one shared sort mode
 * binds into more than one vocabulary.
 *
 * <p>The same shape as {@link LeadingBlocMetricsFake}; only the type tells the two apart. The shared
 * mode is generic over the metrics record, so binding it over two distinct records is what shows it
 * reads the one number they share and nothing either declares for itself.
 *
 * @param count      the number this vocabulary leads with
 * @param marketSize the bloc's summed colony size, which every holder vocabulary carries
 */
public record CountedBlocMetricsFake(
    int count,
    int marketSize)
        implements SizedBlocMetrics {
}
