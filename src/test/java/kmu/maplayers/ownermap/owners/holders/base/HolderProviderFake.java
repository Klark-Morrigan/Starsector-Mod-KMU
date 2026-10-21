package kmu.maplayers.ownermap.owners.holders.base;

import kmu.maplayers.base.render.clusters.SystemFillStates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A holding source a tier case can hand over without reaching for a mechanic's own resolver.
 *
 * <p>The tier resolves holders through {@link HolderProvider} and never names what fills it, so its
 * own suites cannot name one either. What a case wants is a source that answers one stated
 * resolution every time, whatever it is passed.
 *
 * <p>Records every pass it was handed and the bloc each resolve was spotlit on, so a case can pin
 * what the caller opened and asked under.
 */
public final class HolderProviderFake implements HolderProvider {

    private final HolderResolution resolution;
    private final List<HolderPass> resolvedPasses = new ArrayList<>();
    private final List<String> selectedBlocIds = new ArrayList<>();

    private HolderProviderFake(HolderResolution resolution) {
        this.resolution = resolution;
    }

    /**
     * A source answering one stated resolution whatever it is passed.
     *
     * @param resolution the resolution every resolve answers with
     * @return a provider over that resolution
     */
    public static HolderProviderFake createAnswering(HolderResolution resolution) {
        return new HolderProviderFake(resolution);
    }

    /**
     * A source that resolves no holder at all - what a case wanting the pipeline driven over
     * nothing hands over.
     *
     * @return a provider answering empty holding for any pass
     */
    public static HolderProviderFake createHoldingNothing() {
        return createAnswering(new HolderResolution(Map.of(), SystemFillStates.ALL_SOLID));
    }

    /**
     * Every pass this source was asked to resolve, in the order it was asked.
     *
     * @return the recorded passes; empty where nothing resolved
     */
    public List<HolderPass> readResolvedPasses() {
        return List.copyOf(resolvedPasses);
    }

    /**
     * The bloc each resolve was spotlit on, in the order they were asked.
     *
     * @return the recorded bloc IDs, null where a resolve was asked with nothing spotlit
     */
    public List<String> readSelectedBlocIds() {
        return Collections.unmodifiableList(new ArrayList<>(selectedBlocIds));
    }

    @Override
    public HolderResolution resolveHolder(HolderPass pass, String selectedBlocId) {

        resolvedPasses.add(pass);
        selectedBlocIds.add(selectedBlocId);

        return resolution;
    }
}
