package kmu.maplayers.ownermap.render.rebuild;

import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The base view an owner map stands on: the production draw lists, the debug border-tracing overlay
 * that replaces them, or the placeholder standing while neither has been built.
 *
 * <p>Sealed over three cases rather than held as two nullable fields, because the states no frame
 * may draw are exactly the ones two fields can spell: both views standing at once, or a placeholder
 * carrying draw lists over a reading that was never resolved. Here the placeholder carries nothing,
 * so a fold, a hover or a band has nothing to reach in it.
 *
 * <p>The placeholder stands until the first build completes, and again once the drawables are
 * released. It draws nothing. A build that throws leaves whichever view stood before it, so a map
 * that drew once keeps drawing its last good view while the next frame retries.
 */
public sealed interface BuiltView {

    /** The view standing while nothing has been built: it draws nothing. */
    BuiltView PLACEHOLDER = new Placeholder();

    /**
     * Hands this view to the case written for it, for a caller acting on the view rather than
     * producing a value.
     *
     * @param productionCase  what the production draw lists do
     * @param debugCase       what the debug border-tracing overlay does
     * @param placeholderCase what the placeholder does
     */
    default void runByCase(
            Consumer<OwnerMapClusters> productionCase,
            Consumer<ClusterBorderStageOverlay> debugCase,
            Runnable placeholderCase) {

        selectByCase(
            clusters -> {

                productionCase.accept(clusters);
                return null;
            },
            borderStageOverlay -> {

                debugCase.accept(borderStageOverlay);
                return null;
            },
            () -> {

                placeholderCase.run();
                return null;
            });
    }

    /**
     * Hands this view to the case written for it, and answers what that case produced.
     *
     * <p>The fold is how the sealed set is spent: a caller states all three cases or does not
     * compile, and a fourth case joining the set breaks every caller at once. The language's own
     * exhaustive switch would say the same thing, but only from Java 21 - the mod targets 17, which
     * is the runtime the game supplies.
     *
     * @param <R>             what the caller produces for a view
     * @param productionCase  what the production draw lists produce
     * @param debugCase       what the debug border-tracing overlay produces
     * @param placeholderCase what the placeholder produces
     * @return the answer of the case this view is
     */
    <R> R selectByCase(
        Function<OwnerMapClusters, R> productionCase,
        Function<ClusterBorderStageOverlay, R> debugCase,
        Supplier<R> placeholderCase);

    /**
     * The production draw lists, which a batch folds into and the cursor reads.
     *
     * @param clusters the built draw lists
     */
    record Production(
        OwnerMapClusters clusters)
            implements BuiltView {

        @Override
        public <R> R selectByCase(
                Function<OwnerMapClusters, R> productionCase,
                Function<ClusterBorderStageOverlay, R> debugCase,
                Supplier<R> placeholderCase) {

            return productionCase.apply(clusters);
        }
    }

    /**
     * The debug border-tracing overlay, built in place of the production draw lists while the
     * "debug border tracing" dev toggle is on. It replaces them outright rather than drawing over
     * them, so nothing folds into it and nothing bands it.
     *
     * @param borderStageOverlay the traced border stages
     */
    record Debug(
        ClusterBorderStageOverlay borderStageOverlay)
            implements BuiltView {

        @Override
        public <R> R selectByCase(
                Function<OwnerMapClusters, R> productionCase,
                Function<ClusterBorderStageOverlay, R> debugCase,
                Supplier<R> placeholderCase) {

            return debugCase.apply(borderStageOverlay);
        }
    }

    /** The shape behind {@link #PLACEHOLDER}, carrying nothing beyond being that case. */
    record Placeholder()
            implements BuiltView {

        @Override
        public <R> R selectByCase(
                Function<OwnerMapClusters, R> productionCase,
                Function<ClusterBorderStageOverlay, R> debugCase,
                Supplier<R> placeholderCase) {

            return placeholderCase.get();
        }
    }
}
