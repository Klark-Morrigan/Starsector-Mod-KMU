package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.Global;

import kmlib.starsector.systems.SystemColoniesIndex;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.base.refresh.MapLayerStalenessSource;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.SectorColonySightings;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.starsector.nexerelin.NexerelinAlliances;

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
 * changed" is decided against this layer's own last read. The first poll only establishes them.
 *
 * <p>One further passenger writes rather than reads. A colony a revelation gate holds back is
 * observed by the people living around it, and that observation has to be recorded or the colony
 * drops off the map the day its last neighbour dies - yet nothing in the engine announces a
 * derelict arriving among witnesses. So the observation write rides this poll, which is already
 * the sector-wide sweep running on the cadence such an arrival deserves, in the way
 * {@link MovingSystems} already rides it. It stales nothing and no baseline turns on it.
 *
 * <p>A poll is a pass, and is read as one: every passenger is handed the same reading of the
 * sector rather than a sector it could walk again for itself. What that buys is stated where
 * the reading is opened.
 */
public class PoliticalMapStalenessSource implements MapLayerStalenessSource {
    private static final Logger LOG = Global.getLogger(PoliticalMapStalenessSource.class);

    // The installed machinery of the sector being polled, which holds the motion observations this
    // stages. Held from construction rather than resolved per poll because a source is built per
    // load, against the sector it was installed on - the sector its baselines are diffs of.
    private final MapLayerInstallation installation;

    // Last poll's state; 0 and an empty map are also the empty-sector values, so a
    // boolean guards the very first poll establishing every baseline.
    private boolean hasPolled;
    private int lastVisibilityFingerprint;
    private int lastAllianceFingerprint;
    private Map<String, String> lastHolderBySystemId = Map.of();

    /**
     * @param installation the machinery installed on the sector this polls
     */
    public PoliticalMapStalenessSource(MapLayerInstallation installation) {
        this.installation = installation;
    }

    // Re-reads the snapshot and stages on-map positions, then hands each axis its own
    // routing: a visibility move or an accepted system move rebuilds geometry, an holder-map
    // diff marks just the changed systems stale, an alliance-fingerprint move bumps the
    // alliance revision. The first poll only establishes the baselines.
    @Override
    public void markChangesSinceLastPoll() {
        var sector = Global.getSector();

        // The one reading of the sector every passenger below shares, opened here. It samples
        // the live rules before it walks anything, so a toggle flipped mid-poll cannot leave the
        // snapshot's drawn set and the motion walk's disagreeing - and so the motion walk
        // observes the revealed systems the geometry draws rather than only the normally visible
        // ones. Its colony index then bounds what the poll costs: each passenger asks every
        // system who lives there, so one selection per system serves all three.
        //
        // Discarded with the poll. A kept pass would answer the next poll off the sector this
        // one saw, which is the change a poll exists to notice.
        var pass = MapVisibilityPass.readFromLunaSettings(sector);
        var snapshot = PoliticalMapSectorSnapshot.scan(pass);

        // Observe positions every poll so a system that starts or stops moving is
        // taken out of, or returned to, the partition. Only a change to the moving set
        // stales the geometry; a system that keeps moving is already excluded, so it
        // reports no change and never churns the map.
        var hasMovingSetChanged = installation
            .resolveMovingSystems()
            .updateMovingSystems(pass);

        recordObservationsByInhabitants(pass.colonies());

        // One call per axis, each handed the same first-poll flag and each owning its own
        // baseline, so no axis can be read without seeing how it treats a baseline poll.
        var isFirstPoll = !hasPolled;

        markGeometryChange(snapshot, hasMovingSetChanged, isFirstPoll);
        markHolderChanges(snapshot.ownerBySystemId(), isFirstPoll);
        markAllianceSetChange(isFirstPoll);

        hasPolled = true;
    }

    // Writes down what each system's own inhabitants can see of the colonies a revelation gate
    // holds back. It rides this poll because a colony arriving among witnesses raises no event
    // to listen for, and this is already the sector-wide walk running on the cadence such an
    // arrival deserves. Nothing downstream waits on it: the gate keeps its own live reading of
    // the place, so an install where this never ran still shows what the player can plainly see
    // - what the write buys is that the reading survives the witnesses.
    //
    // The loop is here rather than inside the register because the register cannot be handed
    // the index: it lives in the colonies package the index reads, so taking one would make a
    // cycle of the layering. Owning the sweep here costs nothing, the cadence having been this
    // poll's decision in the first place.
    //
    // One knowledge for the whole sweep, on the same reasoning as the index above it: opening one
    // folds the sector's alliances, and a sweep opening one per place would refold them for every
    // system in the sector on every poll. It is the fog-only reading rather than the pass's,
    // because what a place's inhabitants can see is a fact about the place - a reveal reaching it
    // would write down observations nobody made.
    private static void recordObservationsByInhabitants(SystemColoniesIndex colonies) {

        var sector = colonies.getSector();
        var systems = sector == null ? null : sector.getStarSystems();

        if (systems == null) {
            return;
        }
        var observing = ColonyKnowledge.observingUnderTheFog();

        for (var system : systems) {
            SectorColonySightings.recordSightingsByInhabitants(
                sector,
                system,
                colonies.readColoniesIn(system),
                observing);
        }
    }

    // Requests a whole-map geometry rebuild when either trigger fired: the drawn set moved
    // (the visibility fingerprint differs) or a system started or stopped moving. One
    // request covers both, since the rebuild reads the fresh moving set and reachable set
    // whole. The fingerprint baseline advances even on the first poll, where there is
    // nothing to compare against and so nothing to request.
    private void markGeometryChange(
            PoliticalMapSectorSnapshot snapshot,
            boolean hasMovingSetChanged,
            boolean isFirstPoll) {

        var hasVisibilityChanged = isFirstPoll
            || snapshot.visibilityFingerprint() != lastVisibilityFingerprint;

        if (hasVisibilityChanged) {

            LOG.debug("Political map visibility fingerprint changed; old="
                + (isFirstPoll ? 0 : lastVisibilityFingerprint)
                + " new="
                + snapshot.visibilityFingerprint()
                + " firstPoll="
                + isFirstPoll);

            lastVisibilityFingerprint = snapshot.visibilityFingerprint();
        }
        if (isFirstPoll || !(hasVisibilityChanged || hasMovingSetChanged)) {
            return;
        }
        if (!hasVisibilityChanged) {
            LOG.debug("Political map moving set changed");
        }
        MapLayerRefresh.requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);
    }

    // Marks politics-stale every system whose holder differs from the last poll: a system
    // that gained an holder or changed hands (present now with a new id), and one that lost
    // its holder (dropped since). Each mark funnels into the same set the listeners raise,
    // so an overlapping change reshapes once and is traced by markSystemGroupingStale's own
    // log line. The baseline advances even on the first poll, which has no prior to diff.
    private void markHolderChanges(
            Map<String, String> currentHolderBySystemId,
            boolean isFirstPoll) {

        if (!isFirstPoll) {
            for (var entry : currentHolderBySystemId.entrySet()) {
                if (!entry.getValue().equals(lastHolderBySystemId.get(entry.getKey()))) {
                    MapLayerRefresh.markSystemGroupingStale(entry.getKey());
                }
            }
            for (var systemId : lastHolderBySystemId.keySet()) {
                if (!currentHolderBySystemId.containsKey(systemId)) {
                    MapLayerRefresh.markSystemGroupingStale(systemId);
                }
            }
        }
        lastHolderBySystemId = currentHolderBySystemId;
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

            MapLayerRefresh.requestRefresh(PoliticalMapRefreshSignal.ALLIANCES);
        }
        lastAllianceFingerprint = allianceFingerprint;
    }
}
