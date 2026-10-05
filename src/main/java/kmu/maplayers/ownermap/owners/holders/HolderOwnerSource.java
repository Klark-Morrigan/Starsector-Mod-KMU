package kmu.maplayers.ownermap.owners.holders;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileSection;
import kmlib.profiling.Profiler;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.profiling.RebuildStepTerms;
import kmu.maplayers.ownermap.holding.BlocAffiliation;
import kmu.maplayers.ownermap.holding.ColonyReadRules;
import kmu.maplayers.ownermap.holding.DecivilisedColonyHabitation;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.holding.OwnerMapInhabitation;
import kmu.maplayers.ownermap.owners.OwnerSource;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.owners.SpotlitBlocs;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwnerResolve;
import kmu.maplayers.ownermap.ribbon.RibbonPlanInputs;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The owner source of a layer painting holders: it opens a {@link HolderPass} over the walk the
 * tier hands it, folded by the grouping it was built under, and answers every question the tier
 * asks about owners off that one pass - who holds each system through the layer's own
 * {@link HolderProvider}, who lives where through the pass's habitation, where the spotlit bloc
 * lives among the unheld, and how a cell's band is counted.
 *
 * <p>What the layer states is the three rules that are its own - the whole-sector holding, the
 * per-system holding and the band planner - beside the two groupings every one of them is read
 * under. What this adds is the colony reading none of them should open for itself: one pass per
 * walk, so the holder scan, the habitation scan and the band count read each system once between
 * them, and cannot disagree about a colony the habitation rule admits.
 *
 * <p>Built per rebuild, over one sampling of the groupings: a view that reads its grouping live
 * makes a fresh source each rebuild, and the build retains it, so a batch that follows re-derives
 * a marked system under the very fold its neighbours were painted by. The pass is kept by the walk
 * it was opened over, for the same reason it is opened at all - a bake in the same frame as the
 * build reads the build's pass rather than paying a second habitation fold over the walk it already
 * shares - so the source itself holds nothing from one walk to the next.
 */
public final class HolderOwnerSource implements OwnerSource {

    // The three scans a holding resolve makes, each writing a log line of its own on every call:
    // a rebuild happens when something changed rather than on a clock, so the trace a reader
    // follows through the log wants every one of them, fast ones included.
    private static final ProfileSection RESOLVE_HOLDERS_SECTION = ProfileSection.registerSection(
        "ownerMap.resolveHolders", RebuildStepTerms.LOGGED_EVERY_CALL);

    private static final ProfileSection FIND_INHABITED_SECTION = ProfileSection.registerSection(
        "ownerMap.findInhabited", RebuildStepTerms.LOGGED_EVERY_CALL);

    private static final ProfileSection FIND_SPOTLIT_PRESENCE_SECTION =
        ProfileSection.registerSection(
            "ownerMap.findSpotlitPresence", RebuildStepTerms.LOGGED_EVERY_CALL);

    private final HolderGrouping grouping;
    private final HolderGrouping contestGrouping;
    private final HolderProvider holderProvider;
    private final SystemHolderResolveSource holderResolveSource;
    private final Function<RibbonPlanInputs, SystemRibbonPlanner> ribbonPlannerSource;

    /**
     * @param grouping            the grouping this source folds factions into blocs under, sampled
     *                            once by the view for the rebuild this source serves
     * @param contestGrouping     who among the blocs stands together in a contest a band judges,
     *                            sampled once beside it; the identity grouping where nobody does
     * @param holderProvider      the layer's whole-sector holding rule
     * @param holderResolveSource where the layer's per-system holding rule is opened from, per batch
     * @param ribbonPlannerSource the layer's band counting, built over one bake's inputs
     */
    public HolderOwnerSource(
            HolderGrouping grouping,
            HolderGrouping contestGrouping,
            HolderProvider holderProvider,
            SystemHolderResolveSource holderResolveSource,
            Function<RibbonPlanInputs, SystemRibbonPlanner> ribbonPlannerSource) {

        this.grouping = Objects.requireNonNull(grouping, "grouping");
        this.contestGrouping = Objects.requireNonNull(contestGrouping, "contestGrouping");
        this.holderProvider = Objects.requireNonNull(holderProvider, "holderProvider");
        this.holderResolveSource = Objects.requireNonNull(holderResolveSource, "holderResolveSource");
        this.ribbonPlannerSource = Objects.requireNonNull(ribbonPlannerSource, "ribbonPlannerSource");
    }

    /**
     * @return the grouping this source folds factions into blocs under
     */
    public HolderGrouping grouping() {
        return grouping;
    }

    /**
     * Resolves the holding, then reads what stands in each system and where the spotlit bloc lives
     * among the settled systems nobody holds - in that order, because each is asked of what the one
     * before left, and all off the one pass so the three cannot disagree about a colony the rules
     * admit.
     */
    @Override
    public ResolvedOwners resolveOwners(SectorWalk walk, String spotlitOwnerId) {

        var profiler = ActiveProfiler.resolveProfiler();
        var pass = openPassOver(walk);

        var resolution = resolveHolders(profiler, pass, spotlitOwnerId);

        // What stands in each system, read once for the whole pass. Independent of the holding
        // and deliberately so: the holding answers who this layer gives a system to, and a rule
        // that admits only some markets leaves inhabited systems with no holder, for reasons the
        // layer's own holding rule sets. Only this read tells those apart from empty space.
        var inhabitedSystemKeys = measureSystemScan(
            profiler,
            FIND_INHABITED_SECTION,
            () -> OwnerMapInhabitation.readInhabitedSystemKeys(pass));

        // Where the spotlit bloc is living outside anything this holding attributed to it, so
        // the factionless cells over its own colonies are spared the recede. Asked only of the
        // inhabited systems the holding left out: a rule that leaves settled systems unheld can
        // leave the bloc's own colonies there, and this read is what spares their cells. A rule
        // that resolves holding through a filtered resolve already keys every system the bloc is
        // present in, so its leftovers are systems the bloc is absent from and the read comes back
        // empty for the cost of the set arithmetic.
        //
        // Asked of the same habitation the scan above classified by, so a cell spared here is
        // never one that scan called empty space.
        var spotlitPresenceSystemKeys = measureSystemScan(
            profiler,
            FIND_SPOTLIT_PRESENCE_SECTION,
            () -> SpotlitBlocs.findPresentSystemKeys(
                pass,
                spotlitOwnerId,
                SystemOwner.selectUnownedSystemKeysAmong(
                    resolution.ownerBySystemKey(),
                    inhabitedSystemKeys)));

        return new ResolvedOwners(
            resolution.ownerBySystemKey(),
            resolution.contestedSystemKeys(),
            resolution.unfilledSystemKeys(),
            inhabitedSystemKeys,
            spotlitPresenceSystemKeys);
    }

    @Override
    public SystemOwnerResolve openSystemResolve(SectorWalk walk, String spotlitOwnerId) {

        var pass = openPassOver(walk);

        return new HolderSystemOwnerResolve(
            pass,
            holderResolveSource.openResolveOver(pass),
            spotlitOwnerId);
    }

    @Override
    public SystemRibbonPlanner resolveRibbonPlanner(SectorWalk walk, RibbonPlanRules rules) {

        // The colour source, who stands together and the laying rules are sampled here, once, and
        // handed to whatever planner the layer resolves - so every mechanic counting one map reads
        // a bloc's shades through one object, judges a contest against one grouping, and lays its
        // cells by one rule.
        return ribbonPlannerSource.apply(
            RibbonPlanInputs.createForPass(
                openPassOver(walk),
                new BlocAffiliation(contestGrouping),
                rules));
    }

    // The pass this source reads one walk through, opened on the first ask about that walk and kept
    // by the walk for the rest of them, so the source holds nothing between walks. The visibility
    // half of the colony rule is the walk's - the rule the cells were cut under - and the habitation
    // half is this layer's own knob, read where the pass opens, once for the walk.
    private HolderPass openPassOver(SectorWalk walk) {

        return walk.readReadingOpenedBy(this, HolderPass.class, () -> new HolderPass(
            walk.sectorIndex(),
            new ColonyReadRules(
                walk.visibilityRules().colonyVisibility(),
                DecivilisedColonyHabitation.readFromLunaSettings()),
            grouping));
    }

    // Who holds each system under this layer's rule, and - under a filter - which of the spotlit
    // systems are contested. The holder scan walks the whole economy, the priciest content step,
    // so it is profiled on its own and names what it resolved on the call, which is the reading
    // its duration has to be judged against.
    private HolderResolution resolveHolders(
            Profiler profiler,
            HolderPass pass,
            String spotlitOwnerId) {

        try (var holdersScope = profiler.open(RESOLVE_HOLDERS_SECTION)) {

            var resolution = holderProvider.resolveHolder(pass, spotlitOwnerId);

            holdersScope.tagCall("owned=" + resolution.ownerBySystemKey().size()
                + " filtering=" + (spotlitOwnerId != null)
                + " contested=" + resolution.contestedSystemKeys().size()
                + " unfilled=" + resolution.unfilledSystemKeys().size());

            return resolution;
        }
    }

    // One profiled sector scan yielding a set of system keys, naming what it selected on the call.
    // The two such scans report identically rather than each spelling out a section and a reading
    // of its own - two chances for one of them to state its cost differently from the other.
    private static Set<SystemKey> measureSystemScan(
            Profiler profiler,
            ProfileSection section,
            Supplier<Set<SystemKey>> scan) {

        try (var scanScope = profiler.open(section)) {

            var systemKeys = scan.get();

            // What it selected rather than what it examined: the systems it went over are counted
            // by the readers it scans through, and appear on this row by the roll-up alone.
            scanScope.tagCall("systems=" + systemKeys.size());

            return systemKeys;
        }
    }
}
