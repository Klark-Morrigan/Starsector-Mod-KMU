package kmu.maplayers.ownermap;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.controls.specs.ControlSpec;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.sidebar.BodyControlTarget;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.OwnerSource;
import kmu.maplayers.ownermap.picker.OwnerPickerRead;
import kmu.maplayers.ownermap.render.style.OwnerCategories;

import java.util.List;
import java.util.Optional;

/**
 * One owner-painted view's rules, read by the shared owner-map pipeline: where its owners come
 * from and how each one looks, the categories they draw in, and what the view adds to the picker,
 * the sidebar body, and the hover box. Gathering them behind one seam makes a new view an added
 * rules object rather than a fork of the render pipeline.
 *
 * <p>The core asks a view two things about its owners - which systems each one holds, and how
 * each one looks - and nothing else in the core knows what an owner is. Both are answered once
 * per rebuild, in one call, and carried through every stage, so styling, naming and the owner map
 * itself are pure lookups over one snapshot rather than re-reads of a live source per cell.
 */
public interface OwnerPaintedView {

    /**
     * This view's stable ID - the string the active-view selection serialises into the save and
     * the view registry resolves a stored pick back to. Frozen once shipped, since renaming it
     * silently resets a save that selected this view to the default.
     *
     * @return the view's save-stable ID
     */
    String getId();

    /**
     * The localisation key for this view's label on the view-selector radio - the segment the
     * player clicks to activate it. A key rather than the resolved string so the segment follows
     * the player's language and the resolution stays with the view radio that draws it.
     *
     * @return the {@code KmuStringKeys} key for this view's radio-segment label
     */
    String getSegmentLabelKey();

    /**
     * A revision fingerprint folding every live input this view samples, folded into the
     * drawables' content token so a change to any of them forces a rebuild even when no
     * setting moved. A view composes it from its own sources ({@link
     * kmlib.math.hashing.Fingerprints#compute}), so its number of live inputs can grow
     * without widening this contract. A view sampling nothing live folds no sources and returns a
     * constant, never triggering a rebuild on its own. This is what lets the shared renderer
     * invalidate on a view's live data without naming any concrete view - each view declares its
     * own fingerprint.
     *
     * <p>The player's own preferences are not folded here: the bake samples those with every other
     * setting and folds them in as values, so a flip that leaves a preference where it was rebuilds
     * nothing.
     *
     * <p>The board arrives as an argument because a view is a stateless strategy every sector's
     * machinery shares, while the counters it folds are one sector's: a view resolving a board of
     * its own would answer one sector's ask off another sector's revisions, which is a wrong number
     * rather than a stale one - the drawing stands as it was and no signal can move the token that
     * would rebuild it.
     *
     * @param board the refresh board of the sector this ask is about, whose revisions the view
     *              folds
     * @return this view's content fingerprint over {@code board}; a constant for a view with no
     *         live inputs
     */
    int getContentRevision(MapLayerRefreshBoard board);

    /**
     * This view's two answers about its owners for one rebuild: the {@link OwnerReading} every
     * shade, name, crest, recede and category is looked up in, and the {@link OwnerSource} that
     * says which systems each owner holds - resolved together, under one sampling.
     *
     * <p>One call rather than two, so the two answers are resolved under one sampling of whatever
     * the view reads live and cannot describe two different folds.
     *
     * <p>The tier retains what comes back for every stage after - the label fit and the incremental
     * refresh included - which is also what keeps the tier from working any of it out for itself
     * off what an owner key happens to mean.
     *
     * <p>Carries no default, deliberately: a default would name one kind of owner's answers in
     * front of every view, including the views that kind does not paint. Views sharing a kind
     * answer it once between them where that kind's own seam is declared.
     *
     * @param sector the sector the rebuild reads; null for a rebuild over no sector, which the
     *               answers treat as holding nothing
     * @return this view, its reading and its source for the rebuild
     */
    ViewReading resolveViewReading(SectorAPI sector);

    /**
     * The categories this view's cells divide into: which exist and how each is styled, which one
     * an owner at full strength draws in, and which one a cell nobody owns falls to.
     *
     * <p>Carries no default: how a map divides is the vocabulary of whoever paints it, and a
     * default would name one kind of owner's division in front of every view.
     *
     * @return this view's categories
     */
    OwnerCategories resolveCategories();

    /**
     * How the owners this view recedes of its own accord draw on one screen - a backdrop the view
     * keeps apart from the spotlight's, such as every owner outside a group. Read once per rebuild into
     * {@link ContentInputs}, so the owner reading decides which owners recede while this decides how
     * far, off one sampling.
     *
     * <p>Asked of the view rather than held by the tier, because the backdrop and the toggles behind it
     * are the view's own: the tier learns only that a view recedes something, never what. Defaults to
     * receding nothing, which is what a view without such a backdrop answers.
     *
     * @param memoryScope the screen being painted for, whose panel holds the view's toggles
     * @return the adjustment this view's own backdrop draws under;
     *         {@link ElementStyleAdjustment#NONE} for a view that recedes nothing of its own
     */
    default ElementStyleAdjustment resolveViewRecedeAdjustment(ScreenMemoryScope memoryScope) {
        return ElementStyleAdjustment.NONE;
    }

    /**
     * The spotlight picker this view offers: the owners it lists, together with the sort vocabulary
     * that ranks them. Each owner carries the ID the filter stores, its picker label, and its crest.
     * The list is what the picker draws and what {@link kmu.maplayers.base.sidebar.FilterSelection}
     * heals a stale saved selection against, so an owner that is no longer here is no longer
     * spotlightable.
     *
     * <p>Answered as an {@link OwnerPickerRead}, so the same walk hands back where each owner was found
     * beside the rows it was found for. That is what lets a surface light an owner's systems without a
     * second read of the sector, and without the two readings being able to disagree.
     *
     * <p>The list and the vocabulary are answered together because which owners a view offers, what
     * numbers those owners carry, and which metrics rank them are one decision: a view must never be
     * handed a vocabulary reading numbers its owners do not carry. That is why the return type is
     * wildcarded - the metrics a view's owners carry are its own, so the layer above passes the
     * picker on without naming them.
     *
     * <p>Who is listed, and off what reading of the sector, is the view's own. The sidebar and the
     * stale-selection heal call this without a pass of their own, so a view samples whatever live
     * settings its walk needs here.
     *
     * <p>The spotlight is optional: the default offers an empty read, so a view with no list to
     * spotlight inherits one. A view opts into the spotlight by overriding this, the same way it
     * opts into its own body controls.
     *
     * @param sector the sector whose owners decide who is listed; null yields an empty read
     * @return this view's picker - its owners in the order its walk surfaces them, and the vocabulary
     *         ranking them - beside where that walk found each owner; empty when no owner qualifies,
     *         and empty by default for a view with no spotlight
     */
    default OwnerPickerRead<?> resolveOwnerPickerRead(SectorAPI sector) {
        return OwnerPickerRead.empty();
    }

    /**
     * The body controls this view contributes to its layer's tab, appended beneath the shared
     * sub-options and the view selector while this view is the selected one. A view that adds no
     * controls of its own returns an empty list (the default), so the body carries only the shared
     * rows; a view with a backdrop of its own returns the toggles behind it, so those show solely
     * under that view.
     * The sidebar body grows downward to fit whatever rows a view adds, so a view opts into its own
     * controls without any layout knowing which view asked.
     *
     * <p>The panel arrives as an argument for the reason the board does on {@link #getContentRevision}:
     * a view is a stateless strategy every sector's machinery shares, so a control it builds has no
     * sector of its own to raise on and no screen of its own to write under.
     *
     * @param target the panel this body was opened on, carried into whatever controls the view
     *               contributes so their writes repaint that sector and are filed as that screen's own
     * @return this view's own body controls, top to bottom; empty when the view adds none
     */
    default List<ControlSpec> getViewBodyControls(BodyControlTarget target) {
        return List.of();
    }

    /**
     * The hover tooltip this view shows for the star system under the cursor, or empty when it shows
     * none. The layer renderer answers the framework's tooltip seam with whatever the active view
     * supplies here, and the shared dispatcher draws it - nothing when it supplies nothing - so a view
     * opts into a tooltip by injecting one rather than flipping a flag. A view injects the box that
     * explains the mechanic its own fills were painted by, since a box read off another mechanic
     * would describe owners the cells do not show. Defaulting to empty makes "no tooltip" the base
     * case, the same shape as {@link #resolveOwnerPickerRead} defaulting to no spotlight, so a new
     * view opts in only when it has a tooltip to show.
     *
     * @return this view's hover tooltip, or empty for a view that shows none
     */
    default Optional<MapHoverTooltip> resolveHoverTooltip() {
        return Optional.empty();
    }
}
