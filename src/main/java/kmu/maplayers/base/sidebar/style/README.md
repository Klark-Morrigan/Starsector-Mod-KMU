# The sidebar's look (`maplayers/base/sidebar/style`)

What the sidebar is painted in, and how loudly it answers the pointer. `SidebarStyles` composes a
look each frame from the running game's colours and the player's live settings; `SidebarPalettes`
maps the two colour choices a player makes onto shades. Neither holds anything between frames, and
neither needs a live GL context - a look is a value.

Part of [the map layer sidebar](../README.md); the passes that paint what is composed here are
[the sidebar's live screens](../runtime/README.md)'.

## Index

- [What a look is made of](#what-a-look-is-made-of)
- [How the panel sounds](#how-the-panel-sounds)
- [One file, two hosts](#one-file-two-hosts)
- [The body fill and the frame](#the-body-fill-and-the-frame)
- [The tab style and its box](#the-tab-style-and-its-box)
- [The strip](#the-strip)
- [The raised buttons](#the-raised-buttons)
- [What a pointer and a press do](#what-a-pointer-and-a-press-do)
- [The paces](#the-paces)
- [Faces, rings and hotkeys](#faces-rings-and-hotkeys)
- [The chevron](#the-chevron)
- [Label sharpness](#label-sharpness)
- [The settings behind the look](#the-settings-behind-the-look)

## What a look is made of

A host's `resolveWidgetStyle()` answers with the look each frame, built through
`style/SidebarStyles` from live values: a black body fill faded by the opacity setting, the frame
colour, the dark, base, and bright accents of the player's colour scheme, the wash the pointer lifts
a body control by and the light a press lifts it further by, the insignia body face, and
the host's own tab style. Nothing of it is held between frames - every shade reads the running game's
colours and the player's live settings - and nothing of it is persisted.

The opacity setting fades the body and its chrome, not the words. A translucent panel exists so the
map shows through the pane, not so the reading is half-composited with whatever happens to be behind
it, and the cost falls hardest on text carrying a colour of its own: a value in a relation's shade
gives up that shade toward the backdrop while the greys it is meant to be told apart from barely
move, so a faded reading does not merely dim, it flattens the distinctions the colour was for. The
words still leave with the panel - `PanelAlpha` keeps the look's translucency and the panel's arrival
apart, and the text takes the second alone. That is why the setting floors at 50%: the body is what
the words are read against, and one faded much past half leaves solid text standing on open map.

The hover wash is that same base accent at a lesser strength than the selected wash carries, so a
control under the pointer reads as lit without reading as picked, and a lit one under the pointer
lifts past both. It is a shade only: how fast a cell travels onto it is the panel's one input pace,
shared with the tabs and the handle, because a panel answering the pointer at two speeds reads as
two panels.

The press light is the bright step of that same accent, composed here for the same reason the wash is
and spent over it rather than in place of it. Both come from one resolved set, so the panel's answer
to a click is more of what the control already wears; why it is a second channel at all is the
library's own, stated where the treatment is named.

Which palette those accents come from is the player's `SidebarColourSchemeChoice`, and it is one
choice for all three of the panel's colour reads - the frame, the control accents, and the tab row's
chrome rule. They move together because a panel framed in one palette and ruled in another reads
worse than either of the looks it is made of, which is what per-colour knobs would offer as a
combination. The default scheme is the fixed UI palette (`buttonBgDark`, `buttonText`, and the
near-white tooltip title above it) rather than the player faction's own three: the sidebar is drawn
among vanilla chrome, every scrap of which answers to that palette whatever faction the player flies,
so a faction-coloured panel is the one thing on the screen that moves when nothing around it does. On
a stock install the two resolve to the same shades and the choice shows itself only once a faction
recolours - which is the case it exists for. `Chrome grey` is the third, dropping the accent hue
entirely; it is also the one scheme with no engine dark to take, since the fixed palette's dark role
is the button teal, so it steps its own grey down instead.

## How the panel sounds

The look also says how the panel *sounds*, and `SidebarStyles.buildSidebarSoundScheme` composes that:
the engine's own roles - a vanilla button's press and its mouseover, and the id vanilla scrolls its
own readouts with - at the levels the player set on the `Map - Sound` settings tab. The roles are
vanilla's because the panel is drawn among vanilla chrome; the levels are not, because what the engine
mixed its mouseover for is a screen carrying a handful of hit targets and this panel packs a column of
them. One level per kind of thing the pointer can reach - the panel's own furniture, a control with
one answer to give, one of many alike - pitched so a sweep crossing a listed column does not chatter.
A balance pulled to nothing everywhere composes a scheme naming no arrival role at all, silence being
something a look states rather than a cue played at zero.

The wheel moving the list carries a level of its own beside that balance rather than inside it, and
is silenced the same way by its own slider alone. It is not one of the kinds above because nobody
reached anything - the content moved instead - and it answers the movement whole: one turn of the
wheel, one sound, however many rows went past the cursor. The press is the one moment with no slider
behind it, keeping the engine's own level, being a single act the player asked for.

It is the one part of the look the panel's controller is handed directly rather than reading off the
widget style each frame - the moments it answers are pointer events, not paint passes. The controller
therefore takes a scheme composed at the moment it is built: the constructor's seed takes the
library's own balance, being built during class initialisation where reaching for the settings mod
would tie this mod's load order to it and being a controller nothing can be heard through, and
`restoreFoldFromSave` composes from the sliders for the controller a player actually reaches.

## One file, two hosts

Both hosts build through that one file rather than each spelling its look out, so the parts the two
screens share cannot drift into two spellings of them. Where they do differ, they choose between
named factories rather than passing the difference as an argument: `buildStripTabStyle` /
`buildRaisedButtonTabStyle` for the tab row, `buildAccentFramedStyle` / `buildChromeFramedStyle` for
the panel around it. The differences are vanilla conventions and not free choices - a strip's key is
underlined and a button's is bare - so naming the two ends is what keeps a host from composing a look
vanilla has no counterpart for. The file sits beside `SidebarPalettes` and out of the render pass for
the same reason: a look is a value, so building one needs no live GL context.

## The body fill and the frame

That fill is the body's alone. The tab row stands *on* the framed box rather than inside it, the way
a strip of tabs sits on the panel it selects, so nothing of the body reaches behind the tabs and a tab
whose body is empty is its row and nothing else - no frame, no handle. The row needs none, and each
chrome carries its own surface to do without one: the strip's fills are opaque in their own right,
and a raised button stands on a black backing at three-quarter alpha that its own chrome lays down.
Either way the row reads the same over the body, over the bare map, or over whatever the panel floats
on - which is the requirement, the two chromes meeting it differently being the point of their being
two.

The frame colour rides in `WidgetStyle`'s `BoxColours` beside the body fill, apart from the
`AccentColours` the controls wash and label with, so a host whose surrounding chrome is drawn in
another shade can match it without recolouring its controls. The map passes its scheme's base accent
for both, floating free with no neighbouring chrome to match; the intel panel frames itself in the
scheme's dark step, since its border abuts the visor's and the vanilla chrome there is framed in the
dark member of the three-colour set each of its controls is built from - two boxes sharing an edge in
two shades read as one dropped on the other. Its controls stay on the scheme's base either way -
which is the whole point of the frame being its own knob. The two framings therefore differ by which
step of one scheme they take, never by which palette.

## The tab style and its box

That one `TabStyle` carries a strip end to end - band height, `TabBox`, `TabPalette`, `HotkeyStyle`, and
the face - so the value the layout sized tabs against is the value the renderer paints them
from and a tab's width cannot part from the text drawn into it. That holds for the size as well
as the atlas: `TabsControlLayout.layoutHeaderControl` measures at the size its style names, keeping
`TAB_FONT_SIZE` as the baseline for a body tabs row, which carries no style.

The `TabBox` is what decides whether a label is measured at all. The strip stands its tabs in the sector
map's own box - 130 x 18, neighbours parted by a pixel, inside a band that reserves one more than the tab
is tall for the line the row sits on - so the row spans the same width whatever its tabs say and a renamed
tab moves nothing. The raised-button row stays `TabBox.SNAPPED`, its buttons being laid inside the tabs the
layout measured and taking their own channel from within them. A parted row rules no seams: a divider marks
where two tabs meet, and parted tabs never do. What it paints there instead is its own backing - vanilla's
row shows a dark panel through that pixel, and a strip floating over the map would show the map through it.
The palette holds that backing plus both flavours
of tab paint: an absolute `TabLook` per `TabLookState` (unselected, selected, hovered) and a relative
`TabWash` per `TabWashState` (clicked), the pulse lifting whichever look the tab has settled on.

A palette is the chrome's own, built by the factory named for it - `createMapTabPalette` for the strip,
`createRaisedButtonPalette` for the buttons. The two chromes cannot share one, because the engine's own
tab and its own button are painted from different colours by different rules: a tab from the button
roles in settings, taking no faction tint at all, and a button from the whole three-step accent and
nothing else. A single palette worn by both would have one of them copying shades its vanilla
counterpart never wears, which is exactly what the intel row did while it was built to a description
of that screen rather than to its source.

## The strip

The **strip's** three looks are not three shades but one at the engine's three glow amounts, worked out
by `VanillaTabFills` from the two settings colours a vanilla tab is painted with (`buttonBgDark` and
`buttonText`) rather than sampled off one - so a restyled install moves this strip exactly as it moves
the tabs above it, and the fills answer to settings and not to the player faction because vanilla's
own tabs take no faction colour. A tab rests unlit, the shown tab lights at `SELECTED_GLOW`, and the
tab under the pointer at the full `POINTED_GLOW`. That the pointer's is the brighter of the two lit
amounts is load-bearing: nothing else marks the shown tab, no bar capping it, so a pointed-at tab has
to outshine it rather than match it.

Its **labels** are not lit at all. The engine parts a resting tab's text from a lit tab's by *colour*,
switching between two of its own roles rather than brightening one: `buttonText` (170, 222, 255), the
blue every button's text is, while the tab is untouched, and `standardTextColor` (220, 220, 220), the
grey the rest of the interface reads in, once the tab is shown or pointed at. Both lit states take the
grey; their fills already stand at different glows, so that is what tells them apart.

That was arrived at by measurement rather than by reasoning, after two rules derived from the fill's
glow both came out wrong on screen. A lit vanilla tab's label samples at `#a0b1bc`, and solving for
the glyph's coverage over the fill beneath gives three irreconcilable answers against `buttonText`
(0.91 / 0.59 / 0.47) and one consistent answer against `standardTextColor` (0.62 / 0.60 / 0.65). A
computed label was the error both times: brightening a colour that already sits at 255 blue can only
push it sideways into cyan, and no amount tuned into that rule would have escaped it. Only the shown
tab's label is measured; the pointed-at one taking the same grey is the smaller claim, pinned equal in
KMLib so a divergence has to be deliberate.

A faction-tinted label, which is what this replaced first, would additionally have recoloured the
strip with the player's faction where the engine's own tabs take no faction colour at all.

## The raised buttons

The **buttons** invert that shape: theirs is a constant frame around a changing interior, where a tab is
a fill that moves whole. Every button carries the same two hairlines - the outer in the accent's dark
step, the inner in black - over the backing, whatever state it is in, and only the interior quad inside
them answers to the look. That is the engine's own arrangement, and mistaking it is what made the first
pass read as a foreign box: an outline that brightened with its fill was the one thing on the row moving
that the row it was drawn to match holds still.

Their two settled interiors come from `VanillaButtonFills`: the button being shown wears the dark step
composited onto the backing, and a resting one wears *nothing* - that same shade at zero alpha, so the
state is a value the palette states rather than a quad the paint pass learns to skip. Their labels take
the accent's base step untouched and its bright step once shown. There is no third interior, because
what the pointer does is not a shade a button settles on: it adds **the base accent** at `POINTED_GLOW`
(0.17) over whichever of the two the button is wearing, which is why it lives on the palette's
`TabHover` and not among its looks. The weight is fitted rather than read - the engine's own constant
sits behind an obfuscated widget - but fitted twice, from two pairs each sampled within one frame: an
unshown vanilla button over a flat map fill moves `#1b1d1b` to `#364144` (+27, +36, +41, so 0.161), and
the shown one moves `#17424f` to `#346a7c` (+29, +40, +45, so 0.176). Both sets of deltas carry the base
accent's own proportions rather than the equal channels white would add. The bound key's gold moves by
that identical +27, +36, +41, which says it is one light over the whole button rather than a fill rule.

The black under them is read the same way: over that flat `#505850` fill an unshown button's interior
samples `#1b1d1b`, which is the fill kept at 0.337 on every channel - untinted, so what vanilla lays
there is plain black at about two thirds and nothing else. `BACKING_ALPHA` takes that 0.665, and it is
why the map still shows through an unshown button rather than the row reading as a bar laid across the
screen.

That resting interior is why a look's alpha became load-bearing. Every fill on the strip is opaque, so a
fade between two looks had never had to carry alpha; `TabLook.computeBlendedLook` now interpolates all
four channels through `Colours.blendTowards`, which is a no-op on the strip only for as long as
`VanillaTabFills` keeps returning opaque shades.

## What a pointer and a press do

What a pointer does is the chrome's own rule, stated on the palette as a `TabHover`, and the two rules
reach the screen differently. A strip's tabs **converge**: both the resting and the shown tab land on one
named shade, which no fraction applied to each tab's own fill could produce, and which is what lets a row
marking selection by fill alone still light whatever the pointer is on. A button row's do not: the engine
**adds light** - its own base accent - over the finished button, so the shown button and an unshown one
light by the same amount from different places and stay as far apart as their settled shades left them.

Added over the top rather than mixed into the fill, because a button's interior is not always painted. A
shade blended into a surface arrives diluted by however much of that surface is actually there, so the
same amount would read at full strength on the shown button and at a fraction of it on an unshown one -
where the engine's own two move by the same step. Drawn additively, both do, and so does the label the
pass crosses. It stops at the interior: the two hairlines are the part of a button that never moves, and
light spilling onto them would shift the very edge the constant frame exists to hold still.

Which is why the palette answers on two channels and each rule uses one - a shade rule adds no light, a
light rule leaves the look alone - so a tab is never brightened twice. Either way it travels onto its lit
state rather than switching to it, paced by `PanelMotionPaces.DEFAULT_DURATIONS`.

A press rides the `clicked` wash up over whatever look the tab has settled on and **holds there until
the button comes up**, the way a vanilla tab does: a press is an act the player is still making, so
its length comes from the act rather than from a duration of ours, and only the rise and the fall are
paced. The release is unaimed - a press begun on a tab and let go over a neighbour, over the map, or
off the panel entirely still ends that tab's lift, because what it reported was the press. On the strip
that wash travels along the glow (the label colour half-way to white,
`VanillaTabFills.resolveGlowColour`) rather than toward white: the engine brightens a tab by adding its
own glow, so a lift aimed at white would be the one shade on the strip moving in a direction none of the
fills do, and most visible exactly when the player is looking at it.

On the intel screen that press **paints nothing**, by the same imitation: vanilla's tab headers hold a
lit shade while the button is down, its intel buttons take no press state at all and answer a click with
their sound alone. So the raised-button palette states its `clicked` lift at zero strength rather than
having the chrome opt out of the channel. The envelope still runs - the press sound is gated on there
having been a held lift to let go of, not on the wash having painted anything - so an invisible press is
still a press that sounds, and it comes out that way precisely because the controller cannot see the
palette and so cannot skip a pulse it would paint nothing with. The two screens therefore swap which
gesture carries the feedback: the map's click leads and its blink echoes, intel's click is silent and
its blink carries the press. Neither moves channel for it.

## The paces

`PanelMotionPaces.DEFAULT_DURATIONS` is a pair rather than one value, and the two halves are not equal: a tab
arrives at the shade it is heading for in half the time it takes to let go of one. A rise answers
something the player just did and has to land under the gesture that asked for it, while a fall
answers nothing and reads better unhurried - at equal paces the whole motion feels like the slower
half. Every *travel* the panel makes takes that pair, so the tabs, the notch, and the two ends of a
press lift cannot end up at different rhythms.

The press is the one motion whose *length* is not ours to set. Its rise and its fall take the pair
like everything else, but between them it waits on the button, so a press held for a second lasts a
second. That is the point of it: the lift reports an act the player is still making, and a duration
of ours would end it while they were still making it.

A bound key's blink takes no wash of its own: it carries its tab onto that same hovered shade and
back, so it rides the look channel with the hover and the two compose by the greater of them - which
is why a key pressed for the tab already under the pointer shows nothing, the blink reaching only
where the hover already stands. It is the one motion off that shared pair, running at
`TabPanelController.HOTKEY_BLINK_DURATIONS`: a blink is a strike rather than a travel, confirming a
key pressed away from the panel, so it lands and is gone however leisurely the rest of the panel
moves. Paced with the travels it reads as one more thing moving at the speed everything else moves
at, which is the opposite of what a keypress needs to say. All three animations are the controller's, which holds no colour: it
reports two fractions per tab - one look, one lift - and the paint pass binds them to the palette, so
it is handed a look already blended and a lift already scaled - so both tab chromes animate alike,
neither of them having any timing to compute. What the two screens set apart is the band height, the
chrome its shades are painted onto, and the four things that travel with that chrome because vanilla
keeps them together: the palette its shades come from, the face it is lettered in, the ring around
that face, and the hotkey convention. The timings, the
channels, and the order they resolve in are shared, which is why one animator serves either row. Every face is named through KMLib's `StarsectorFont` enum
rather than by atlas basename.

## Faces, rings and hotkeys

The strip is lettered in `VANILLA_ORBITRON_20AA` scaled to the tab size, the face the vanilla tabs it
sits beneath are set in; the buttons in `VANILLA_VICTOR_10` at that atlas's own size, the pixel face
the intel screen's map toggles are set in. A pixel face is crisp at one size only, so the button row
takes its native 9 - the line height its atlas states, not the 10 its name carries - rather than the
strip's 15; scaled, it would read as a blurred copy of the row it was drawn to match. Its capitals come with the atlas: every glyph sits on the same 5x5 cell with
lowercase included and no descenders, so a mixed-case label needs no upper-casing pass and the width
it is snapped to is the width it draws at.

The ring comes with the face. `victor10`'s atlas is hard pixels - every one of them fully on or fully
off, with no anti-aliased edge - so over a live visor its strokes have nothing but the map to read
against. The buttons take `TextHalo.createBlackHairline()`, a black copy laid a pixel out on each of
the four sides at half strength, and the strip takes `TextHalo.NONE`, a smooth face at size having
weight enough and reading muddier for a ring around it. A ring rather than a drop shadow: an offset
copy falls to one side, announcing a light source the flat chrome has none of, and leaves the opposite
edge as bare as it found it. `TabLabelRenderer` lays the whole group down once per side and then in
place, shifting the box the runs centre in rather than each draw site, so the key's underline travels
with the text it marks; the runs are separate single-colour drawables, which is what lets one resolve
serve every pass and what makes their colour a live draw-time knob rather than something baked into a
run. The ring's shade is set once for all four copies, and the key's underline reads its colour off the
key glyph it marks rather than off the style, so no copy can be laid down in two colours.

Where a tab says which key it answers to is `TabShortcutText`'s call, following the engine's rule: a
single-glyph key whose letter already stands in the label lights that letter where it is, and only a
key with nowhere to land is spelt out after it as `Label  [K]`. It answers in runs, and both passes
read that one answer - the layout measures the runs end to end, the paint pass walks them and colours
each - so a tab cannot be sized for one presentation and drawn in the other. Both of this mod's tabs
light in place (the N of "No Layer", the P of "Political Map"), so neither carries a bracketed key.

Each screen takes the `HotkeyStyle` its chrome's vanilla counterpart uses, which is why each tab
factory hands out the pair rather than taking the convention as an argument. The map's strip takes `createUnderlined()`: the vanilla Sector/System
tabs it sits below mark their key wherever it falls, so a key marked by colour alone reads as a
mismatch against the row above. The intel screen's buttons take `createPlain()`, its own map toggles
lighting their key by colour and nothing else. The line is a quad the style places under the key's
drawn box, not part of the measured display string, so drawing it or not moves no tab. The gold is
`buttonShortcut` under either, the role the engine's own buttons light their keys with, rather than
the prose-highlight `hColor` the stock install happens to give the same value.

## The chevron

`style/SidebarPalettes` maps both of the player's colour choices to shades: the
`SidebarColourSchemeChoice` to the panel's three accent steps, and the `NotchChevronColourChoice` to the
chevron's resting and lit shades. It is kept out of the renderer so the "which colour does this
choice mean" rules stay a pure lookup, with no live GL or screen needed. The handle
travels between those shades on the same fade the tabs use, so the gold choice - one colour passed
twice - answers a hover by its accent wash alone while the panel-accent choice brightens the glyph
with it; that choice resolves through whichever scheme is set, which is why the two rows sit together
on the settings screen.

## Label sharpness

Each chrome also carries how hard its labels read. Both rows are lettered in atlases that carry no
antialiasing of their own, which the font loader hands over interpolated, so each is drawn between the
two samplings and where between is a look rather than a number. It is one knob per row, not one for the
panel: each is read against the vanilla chrome it stands beside - the intel row against that screen's
raised buttons, the map row against the Sector/System tabs a tab-height above it - so a single value
would always be wrong for one of them. The amount rides on the `TabStyle` like the face and the ring do,
rather than being pushed into the draw pass, which is what keeps the two rows from sharing one.

## The settings behind the look

The border width, opacity, scrollbar thickness, collapse seconds, and both anchors' paddings are
LunaLib fields read through `kmu.settings.KmuMapSidebarSettings`, as are the colour scheme and the
chevron colour - those two in their own "Overlay sidebar - colours" section below the box's
dimensions. The three arrival levels and the list-scroll level are `kmu.settings.KmuMapSoundSettings`'
on the `Map - Sound` tab, which is where the volume half of the look is stated rather than beside the
shades. Every knob either class holds is a look; the dev hatches over the layers' controls are
`KmuMapControlSettings`' and named where the controls they govern are.

All of them are read per frame, so a slider moved on the settings screen shows on the next one with
no reopen, and both hosts read them through the one `buildChrome` - which is what keeps the map and
intel sidebars from drawing different boxes. The scrollbar thickness is the one whose effect reaches
past the chrome it names: a bar fatter than the gutter widens the panel rather than covering the
rows, so a player moving that slider sees the box grow. How much and why is KMLib's, stated on
`CappedStripLayout`.

The thickness is also clamped on this side, to the 1..12 the slider offers. A width of nothing is a
state KMLib supports and this panel never wants - it removes the bar outright, leaving nothing on
screen to say where the control went - so a hand-edited settings file cannot reach it either.
