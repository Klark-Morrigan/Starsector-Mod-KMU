package kmu.maplayers.base.geometry.ui.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * A stand-in for the viewer that writes down every refresh it is asked for, in order.
 */
final class RecordingViewerRefreshes
        implements ViewerRefreshes {

    static final String REBUILD = "rebuild";
    static final String COASTS = "coasts";
    static final String VOID_V4 = "voidV4";
    static final String UNBOUNDED = "unbounded";
    static final String REPAINT = "repaint";

    private final List<String> asked = new ArrayList<>();

    @Override
    public void rebuildGeometry() {
        asked.add(REBUILD);
    }

    @Override
    public void refreshCoastlines() {
        asked.add(COASTS);
    }

    @Override
    public void refreshVoidV4() {
        asked.add(VOID_V4);
    }

    @Override
    public void refreshUnboundedCells() {
        asked.add(UNBOUNDED);
    }

    @Override
    public void repaintMap() {
        asked.add(REPAINT);
    }

    /**
     * @return every refresh asked for so far, in order
     */
    List<String> listAsked() {
        return asked;
    }
}
