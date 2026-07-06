package kmu.politicalmap.layer;

/**
 * What a layer's tab reveals in its body once selected. The tab acts as a panel - picking it
 * opens the body this names - so each layer declares which body it carries rather than the bar
 * hard-coding a body per layer. Keeping the choice on the layer lets a further view pick its
 * own body without the bar changing.
 */
public enum SidebarBodyKind {
    /** The tab opens no body: selecting the layer only switches the map paint, or clears it. */
    NONE,

    /**
     * The tab opens the political-map control panel - the uninhabited-systems checkbox, the
     * Short/Full name-format radio, and the faction-overlay toggle.
     */
    POLITICAL_MAP_CONTROLS
}
