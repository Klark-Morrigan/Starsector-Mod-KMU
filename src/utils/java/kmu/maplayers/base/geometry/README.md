# Sector geometry viewer (`utils`: `kmu.maplayers.base.geometry`)

Developer tooling for the map's cell geometry,
run outside the game.
It builds a real sector's geometry from a fixture,
draws it in a window with live knobs,
and writes the same shapes to file.
The void constructions that may later reach the shipped map are prototyped here first.

Nothing here ships.
The `utils` source set keeps it out of the mod's jar,
while the tests share its sector fixture.
The shipped geometry it builds on is [cell geometry](../../../../../../main/java/kmu/maplayers/base/geometry/README.md).

Part of Klark Morrigan's Utilities (KMU);
see the [mod README](../../../../../../../README.md) for project context.

## Index

- [The three tasks](#the-three-tasks)
- [The packages](#the-packages)
- [Outside the geometry tree](#outside-the-geometry-tree)
- [How the packages may depend](#how-the-packages-may-depend)

## The three tasks

| Task | Main class | What it does |
| --- | --- | --- |
| `viewSectorGeometry` | `ui.SectorGeometryViewer` | opens the viewer, rebuilding the geometry live as its knobs move |
| `writeSectorSvg` | `output.SectorSvgDump` | writes every sector fixture to SVG, frontier off and on, into `build/reports` |
| `writeVoidRegions` | `output.VoidRegionsDump` | traces the void between cells exactly and reports how it divides |

The window needs someone sitting at it;
the two writers exist for a shape that has to be looked at when nobody is -
attached to a plan,
diffed across two runs of a change,
or handed to someone else.

All three run with LunaLib on the `utils` runtime classpath.
`SectorGeometryParameters` opens on the mod's own settings accessors,
which answer their shipped fallbacks outside the game.

## The packages

| Package | What is in it |
| --- | --- |
| *(root)* | the fixture and what every construction shares: a real sector's sites and owners (`SectorFixture`), its geometry at a set of knobs (`SectorGeometry`, `SectorGeometryParameters`), the partition before the reach clip (`UnboundedCells`), the union of reach discs whose complement is the void (`DiscUnion`), the values the constructions trade in (`CellGap`, `Chord`, `CellEdges`, `VoidHole`, `VoidKeys`, `NamedRegion`), the one test of whether two straight runs cross (`LineCrossings`), and the key a flood files its squares under (`SquareKey`) |
| `walls` | the boundary of the disc union as closed arcs: the cycle walk (`DiscUnionBoundary`) over the walls the greedy laying lets down (`WallLaying`) and the arcs the cover sweep leaves (`CoverSweep`), joined by the names in `BoundaryTerminals`; why each offered wall was or was not laid (`ChordRefusals`); the same trace with nothing laid across it (`BareVoidBoundary`); and the walls laid across the void (`Walls`, `WallMouths`) |
| `v3` | the first void construction: the void walked once per kind of thing sought - pockets, coasts, bridges, sections - and the answers reconciled afterwards; `v3.ui` draws it |
| [`v4`](v4/README.md) | the void as one planar division, every line laid into a single walk and the pieces read off it; `v4.ui` makes the pieces drawable |
| `render` | the painting every surface shares: what the map is painted with (`MapLook`), how one fill looks (`FillLook`, `FillSheet`), and the Java2D moves (`MapPainting`) |
| `settings` | what the viewer is set to draw with (`ViewerSettings`), and nothing else |
| `ui` | the window and what it draws in which order (`SectorGeometryViewer`), the view onto the sector and its pointer (`MapCanvas`, `CursorReadout`), and `LakeReaches`, which hands v3's traced lakes to v4's lake tier |
| `ui.overlays` | the cells, the named void sections and the names on the map |
| `ui.settings` | the knob panel: one section per construction, one descriptor per setting (`SliderKnob`, `SwitchKnob`, `ColourKnob`, `ColourPairKnob`, `RadioKnob`), the row shapes bound to one refresh each (`SettingRows`), and the settings the viewer runs read through the same knobs without a window (`SavedViewerSettings`) |
| `output` | the two writers, and `SectorSvgWriter`, which draws the geometry to an SVG file |
| `output.v3` | the reports the void dump writes over v3's construction - coasts, walls, sections, refusals and a picked point's check - and the figures they share (`ReportFigures`) |

## Outside the geometry tree

Two packages in the same source set hold what is not about geometry at all:

- `kmu.desktop.ui` -
  what a window remembers between runs (`SavedValues`),
  one file under keys that are identities rather than labels,
  and the one way a control keeps a value in it (`RememberedValue`, `SavedValueFormat`);
  its `swing` subpackage is the generic toolkit the knob panel is built from:
  sliders,
  colour swatches,
  toggle trees,
  foldable sections and window layout.
- `kmu.svg` -
  an SVG document built shape by shape,
  and a rasteriser that turns one back into a PNG.

## How the packages may depend

`gradle/package-layering.gradle` holds the edges.
The root reaches none of `output`,
`render`,
`settings` or `ui`,
so a construction is asked its question under a rule record rather than under the viewer's state.
Nor does it reach `walls`,
`v3` or `v4`:
it is what they all build on.
v3 and v4 do not import each other:
v4 is a construction beside v3 rather than a layer on top of it,
and what crosses between them crosses as root values,
handed over by the viewer.
`walls` reaches neither version,
and nothing in `render` or `output` reaches `ui`.
`render` and `settings` reach neither version's overlays (`v3.ui`, `v4.ui`);
`settings` does read v3 itself,
taking its defaults from v3's own rules.
`kmu.svg` and `kmu.desktop` reach nothing under `kmu.maplayers`.
