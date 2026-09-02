package kmu.maplayers.base.refresh;

/**
 * One coarse thing that can go stale under a map layer, and the key {@link MapLayerRefreshBoard}
 * holds its revision counter under. The board declares only that a layer raises <em>some</em>
 * named set: what the changes actually are - the cells moved, the styling moved, who is allied
 * with whom moved - is the vocabulary of the layer watching them, so a signal only one layer
 * could raise is declared alongside that layer rather than here. An enum may implement this,
 * which is what lets a layer keep a closed set the compiler checks its lookups against while the
 * board stays open to any set.
 *
 * <p>An id because every raise is logged, and a signal that cannot name itself leaves that line
 * unable to say which one moved - the one thing a reader chasing an overlay that did not repaint
 * is after.
 *
 * <p>An implementation owes {@code equals} and {@code hashCode} that agree across the producer
 * that raises a signal and the consumer that reads it, or the two reach different counters and
 * the raise is never seen. An enum constant is that for free.
 */
public interface MapLayerRefreshSignal {

    /**
     * @return this signal's name in the board's log lines
     */
    String getId();
}
