package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.Global;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerStalenessSource;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.mods.nexerelin.NexerelinAlliances;

import org.apache.log4j.Logger;

import java.util.Map;

/**
 * What the political map counts as a change the engine never announced - a gate activating,
 * a system being cut off, a dead colony surveyed, or an AI faction founding or capturing a
 * colony in a system already on the map - and which refresh each one earns.
 *
 * <p>Takes a cheap snapshot of the on-map systems ({@link PoliticalMapSectorSnapshot}) and
 * reacts to each half on its own axis. The visibility fingerprint tracks which systems are
 * drawn; when its scalar hash moves the geometry is stale, so this requests a whole-map
 * geometry rebuild - the Voronoi partition depends on the full set of sites, so there is
 * nothing finer to act on. The holder map tracks who holds each system; this diffs it against
 * the last poll and marks exactly the systems whose holder changed politics-stale, the same
 * targeted signal the event listeners raise. Feeding that shared set is what keeps this from
 * doubling a listener's work: a colony a listener already marked and one this diff
 * re-discovers collapse to a single reshape, and a change no listener saw is caught here and
 * reshaped just as narrowly.
 *
 * <p>A third axis handles systems that move. Each poll also observes on-map positions through
 * {@link MovingSystems}, which flags a system that rewrites its own hyperspace position so the
 * geometry can leave it out of the partition rather than chase it. When that moving set
 * changes - a system starts or stops moving - the geometry is stale through the same refresh a
 * visibility change uses, since the mover joins or leaves the cell layout. A system that merely
 * keeps moving changes nothing, since it is already excluded, so a steady drifter never churns
 * the map.
 *
 * <p>A fourth axis fingerprints the live alliance set (Nex-gated: on a Nex-free install the
 * fingerprint is a fixed value, so it never fires and no {@code exerelin} class loads). When
 * alliances form, dissolve, or change members the fingerprint moves, so this bumps the shared
 * alliance revision - which only the alliances view folds into its content token, so the
 * faction view never rebuilds for an alliance change. The fingerprint keys on who is allied and
 * which member leads (the bloc's colour), so a market-size reshuffle below the lead never churns
 * the map while a colour lead swap does.
 *
 * <p>All four baselines live here rather than in the framework's poll, so the whole of "what
 * changed" is decided against this layer's own last read. Each is the staleness of this layer's own
 * picture and belongs to the layer that draws it. The first poll only establishes them.
 *
 * <p>A poll is a pass, and is read as one: every passenger is handed the same reading of the
 * sector rather than a sector it could walk again for itself. What that buys is stated where
 * the reading is opened.
 */
public class PoliticalMapStalenessSource implements MapLayerStalenessSource {
    private static final Logger LOG = Global.getLogger(PoliticalMapStalenessSource.class);

    // The installed machinery of the sector being polled. The sector this walks, the tracker it
    // stages motion into and the board it raises on all come off this one handle, so a poll cannot
    // walk the running game's sector while marking another's cache stale. Held from construction
    // rather than resolved per poll because a source is built per load, against the sector it was
    // installed on - the sector its baselines are diffs of.
    private final SectorMapMachinery machinery;

    // Last poll's state; 0 and an empty map are also the empty-sector values, so a
    // boolean guards the very first poll establishing every baseline.
    private boolean hasPolled;
    private int lastVisibilityFingerprint;
    private int lastAllianceFingerprint;
    private Map<SystemKey, String> lastHolderBySystemKey = Map.of();

    /**
     * @param machinery the machinery installed on the sector this polls
     */
    public PoliticalMapStalenessSource(SectorMapMachinery machinery) {
        this.machinery = machinery;
    }

    // Re-reads the snapshot and stages on-map positions, then hands each axis its own
    // routing: a visibility move or an accepted system move rebuilds geometry, an holder-map
    // diff marks just the changed systems stale, an alliance-fingerprint move bumps the
    // alliance revision. The first poll only establishes the baselines.
    @Override
    public void markChangesSinceLastPoll() {

        // Null mid-load, before the sector stands up: every passenger below is then handed a reading
        // over nothing and answers emptily rather than faulting.
        var sector = machinery.resolveSector();

        // The one reading of the sector every passenger below shares, opened here. It samples
        // the live rules before it walks anything, so a toggle flipped mid-poll cannot leave the
        // snapshot's drawn set and the motion walk's disagreeing - and so the motion walk
        // observes the revealed systems the geometry draws rather than only the normally visible
        // ones. Its colony index then bounds what the poll costs: both passengers ask every
        // system who lives there, so one selection per system serves the pair.
        //
        // Discarded with the poll. A kept pass would answer the next poll off the sector this
        // one saw, which is the change a poll exists to notice.
        var pass = MapVisibilityPass.readFromLunaSettings(sector);
        var snapshot = PoliticalMapSectorSnapshot.scan(pass);

        // Observe positions every poll so a system that starts or stops moving is
        // taken out of, or returned to, the partition. Only a change to the moving set
        // stales the geometry; a system that keeps moving is already excluded, so it
        // reports no change and never churns the map.
        var hasMovingSetChanged = machinery
            .resolveMovingSystems()
            .updateMovingSystems(pass);

        // One call per axis, each handed the same first-poll flag and each owning its own
        // baseline, so no axis can be read without seeing how it treats a baseline poll.
        var isFirstPoll = !hasPolled;

        markGeometryChange(snapshot, hasMovingSetChanged, isFirstPoll);
        markHolderChanges(snapshot.ownerBySystemKey(), isFirstPoll);
        markAllianceSetChange(isFirstPoll);

        hasPolled = true;
    }

    // Requests a whole-map geometry rebuild when either trigger fired: the drawn set moved
    // (the visibility fingerprint differs) or a system started or stopped moving. One
    // request covers both, since the rebuild reads the fresh moving set and reachable set
    // whole.
    private void markGeometryChange(
            PoliticalMapSectorSnapshot snapshot,
            boolean hasMovingSetChanged,
            boolean isFirstPoll) {

        // A first poll has nothing to compare against, so nothing changed by definition - it only
        // establishes the baseline below. Kept out of the change itself so that neither the log
        // line nor the request has to subtract it back out.
        var hasVisibilityChanged = !isFirstPoll
            && snapshot.visibilityFingerprint() != lastVisibilityFingerprint;

        if (hasVisibilityChanged) {

            LOG.debug("Political map visibility fingerprint changed; old="
                + lastVisibilityFingerprint
                + " new="
                + snapshot.visibilityFingerprint());
        }
        lastVisibilityFingerprint = snapshot.visibilityFingerprint();

        // The first poll establishes both baselines and requests nothing, whatever the motion walk
        // answered. Stated here rather than left to that walk returning false on its own first
        // observation, so this axis's first-poll behaviour is readable without opening it.
        if (isFirstPoll) {
            return;
        }
        if (!hasVisibilityChanged && !hasMovingSetChanged) {
            return;
        }
        if (!hasVisibilityChanged) {
            LOG.debug("Political map moving set changed");
        }
        machinery
            .resolveRefreshBoard()
            .requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);
    }

    // Marks politics-stale every system whose holder differs from the last poll: a system
    // that gained an holder or changed hands (present now with a new holder), and one that lost
    // its holder (dropped since). Each mark funnels into the same set the listeners raise,
    // so an overlapping change reshapes once and is traced by markSystemGroupingStale's own
    // log line. The baseline advances even on the first poll, which has no prior to diff.
    //
    // Diffed and marked by key, which the snapshot's walk read off each system: two systems
    // sharing an ID are two baselines, so a flip in either marks that system and not both.
    private void markHolderChanges(
            Map<SystemKey, String> currentHolderBySystemKey,
            boolean isFirstPoll) {

        if (!isFirstPoll) {

            var board = machinery.resolveRefreshBoard();

            for (var entry : currentHolderBySystemKey.entrySet()) {
                if (!entry.getValue().equals(lastHolderBySystemKey.get(entry.getKey()))) {
                    board.markSystemGroupingStale(entry.getKey());
                }
            }
            for (var systemKey : lastHolderBySystemKey.keySet()) {
                if (!currentHolderBySystemKey.containsKey(systemKey)) {
                    board.markSystemGroupingStale(systemKey);
                }
            }
        }
        lastHolderBySystemKey = currentHolderBySystemKey;
    }

    // Fingerprints the live alliance set and bumps the shared alliance revision when it
    // moves against the last poll, so the alliances view repaints on a form/dissolve/
    // transfer. Nex-gated inside computeAllianceFingerprint (a fixed value without Nex), so
    // a Nex-free install polls a steady token and never bumps. The first poll only seeds the
    // baseline, matching the other axes.
    private void markAllianceSetChange(boolean isFirstPoll) {

        var allianceFingerprint = NexerelinAlliances.computeAllianceFingerprint();
        if (!isFirstPoll && allianceFingerprint != lastAllianceFingerprint) {

            LOG.debug("Political map alliance fingerprint changed; old="
                + lastAllianceFingerprint
                + " new="
                + allianceFingerprint);

            machinery
                .resolveRefreshBoard()
                .requestRefresh(PoliticalMapRefreshSignal.ALLIANCES);
        }
        lastAllianceFingerprint = allianceFingerprint;
    }
}
