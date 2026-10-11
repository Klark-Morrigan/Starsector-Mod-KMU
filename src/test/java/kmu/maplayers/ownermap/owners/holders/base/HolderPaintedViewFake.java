package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.picker.OwnerPickerRead;
import kmu.maplayers.ownermap.render.style.OwnerCategories;
import kmu.maplayers.ownermap.render.style.OwnerCategoriesFake;
import kmu.maplayers.ownermap.ribbon.RibbonPlan;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Test fixture: the smallest view painting holders. Answers every part with a value holding
 * nothing and names a bloc from a canned map, so a case about a shared holder default exercises
 * that default alone rather than whichever concrete view it borrowed to reach it.
 *
 * <p>The picker gate is supplied where a case is about which blocs survive it; it defaults to the
 * seam's own, which offers every bloc. The grouping is supplied where a case is about the one
 * sampling of it a rebuild takes; it defaults to the identity grouping.
 *
 * <p>It records how often its grouping is sampled, what its reading is built over, what its band
 * planner is built over, and what its picker is asked under. Its picker still answers with the
 * seam's own default, so a case about that default reads it through this fake unchanged.
 *
 * <p>The reading is supplied where a case is about what is read off it; it defaults to one naming
 * blocs from a canned map.
 */
public final class HolderPaintedViewFake
        implements HolderPaintedView {

    /**
     * The one planner this fake counts every band through, so a case can tell it from a planner the
     * source built for itself.
     */
    public static final SystemRibbonPlanner PLANNER = system -> RibbonPlan.NONE;

    private final HolderGrouping grouping;
    private final List<ColonyReadRules> pickerRules = new ArrayList<>();
    private final List<SectorAPI> pickerSectors = new ArrayList<>();
    private final List<RibbonPlanInputs> plannerInputs = new ArrayList<>();
    private final OwnerReadingFake readingFake;
    private final List<HolderGrouping> readingGroupings = new ArrayList<>();
    private final List<SectorAPI> readingSectors = new ArrayList<>();
    private final Predicate<String> selectableBlocGate;

    private int groupingSamples;

    public HolderPaintedViewFake(Map<String, String> nameByBlocId) {

        this(HolderGrouping.identity(), OwnerReadingFake.createNaming(nameByBlocId), null);
    }

    private HolderPaintedViewFake(
            HolderGrouping grouping,
            OwnerReadingFake readingFake,
            Predicate<String> selectableBlocGate) {

        this.grouping = grouping;
        this.readingFake = readingFake;
        this.selectableBlocGate = selectableBlocGate;
    }

    /**
     * A fake offering only the blocs a stated gate accepts, for a case about the gate rather than
     * about what surrounds it.
     *
     * @param nameByBlocId       the canned labels this fake names its blocs from
     * @param selectableBlocGate which of the walked blocs the fake's picker offers
     * @return the fake, gated
     */
    public static HolderPaintedViewFake createGatedFake(
            Map<String, String> nameByBlocId,
            Predicate<String> selectableBlocGate) {

        return new HolderPaintedViewFake(
            HolderGrouping.identity(),
            OwnerReadingFake.createNaming(nameByBlocId),
            selectableBlocGate);
    }

    /**
     * A fake folding its holders under a stated grouping, for a case about what is built over the
     * one sampling of it.
     *
     * @param grouping the grouping every sampling answers with
     * @return the fake, naming no bloc
     */
    public static HolderPaintedViewFake createGroupingFake(HolderGrouping grouping) {

        return new HolderPaintedViewFake(grouping, OwnerReadingFake.createAnsweringNothing(), null);
    }

    /**
     * A fake answering with a stated reading, for a case about what is read off the view's own
     * reading.
     *
     * @param readingFake the reading every resolve answers with
     * @return the fake, over the identity grouping
     */
    public static HolderPaintedViewFake createReadingFake(OwnerReadingFake readingFake) {

        return new HolderPaintedViewFake(HolderGrouping.identity(), readingFake, null);
    }

    /**
     * @return how many times the grouping has been sampled
     */
    public int countGroupingSamples() {
        return groupingSamples;
    }

    /**
     * @return the colony rules each picker read was asked under, in order
     */
    public List<ColonyReadRules> listPickerRules() {
        return pickerRules;
    }

    /**
     * @return the sector each picker read was asked about, in order
     */
    public List<SectorAPI> listPickerSectors() {
        return pickerSectors;
    }

    /**
     * @return the inputs each band planner was built over, in order
     */
    public List<RibbonPlanInputs> listPlannerInputs() {
        return plannerInputs;
    }

    /**
     * @return the grouping each reading was built over, in order
     */
    public List<HolderGrouping> listReadingGroupings() {
        return readingGroupings;
    }

    /**
     * @return the sector each reading was built over, in order
     */
    public List<SectorAPI> listReadingSectors() {
        return readingSectors;
    }

    @Override
    public HolderGrouping resolveGrouping() {

        groupingSamples++;
        return grouping;
    }

    @Override
    public OwnerAffiliation resolveContestAffiliation() {
        return OwnerAffiliation.NONE;
    }

    @Override
    public HolderProvider resolveHolderProvider() {
        return HolderProviderFake.createHoldingNothing();
    }

    @Override
    public SystemHolderResolveSource resolveSystemHolderResolveSource() {
        return new SystemHolderResolveSourceFake();
    }

    @Override
    public SystemRibbonPlanner resolveRibbonPlanner(RibbonPlanInputs inputs) {

        plannerInputs.add(inputs);
        return PLANNER;
    }

    @Override
    public OwnerReading resolveOwnerReading(SectorAPI sector, HolderGrouping grouping) {

        readingSectors.add(sector);
        readingGroupings.add(grouping);

        return readingFake;
    }

    // Recorded, then answered by the seam's own default, so the default is what a case reads back.
    @Override
    public OwnerPickerRead<?> resolveBlocPickerRead(SectorAPI sector, ColonyReadRules colonyReadRules) {

        pickerSectors.add(sector);
        pickerRules.add(colonyReadRules);

        return HolderPaintedView.super.resolveBlocPickerRead(sector, colonyReadRules);
    }

    // The supplied gate, or the seam's own default (offer everything) when a case did not name one.
    @Override
    public Predicate<String> resolveSelectableBlocGate(HolderGrouping grouping) {

        return selectableBlocGate == null
            ? HolderPaintedView.super.resolveSelectableBlocGate(grouping)
            : selectableBlocGate;
    }

    @Override
    public OwnerCategories resolveCategories() {
        return OwnerCategoriesFake.SHARED_FAKE;
    }

    @Override
    public String getId() {
        return "holder_fake";
    }

    @Override
    public String getSegmentLabelKey() {
        return "holder_fake_label";
    }

    @Override
    public int getContentRevision(MapLayerRefreshBoard board) {
        return 0;
    }
}
