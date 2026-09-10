# Map covers (`maplayers/base/hover/cover`)

Whether anything is drawn over the map where the cursor rests, which a layer asks before resolving a
hover at all. A hover reads a cell out of map geometry, which knows nothing of what is composited on
top, so without this it lights cells and floats boxes under whatever is covering them.

Part of [the hover package](../README.md), and through it of
[the map layers framework](../../../README.md).

## Index

- [The role and the reader](#the-role-and-the-reader)
- [The seven always in the set](#the-seven-always-in-the-set)
- [Two from optional mods](#two-from-optional-mods)
- [Order, and failing open](#order-and-failing-open)
- [The minimap exception](#the-minimap-exception)

## The role and the reader

`MapCover` is the role - one thing that can be over the cursor - and `MapCoverReader` holds the set
and stops at the first that answers.

## The seven always in the set

Seven are always in the set and live here: `HeldPointerMapCover` (a held left button, on which the
pointer is pressing rather than pointing - it stands in for vanilla's own marker menu, which no
geometry here can see; the class states why), `PauseMenuMapCover` (the campaign's pause menu, raised
over the screen without taking it down, so the map keeps drawing behind it),
`ArrangementDialogMapCover` (this mod's own bar-arranging dialog, read off its own state),
`CodexMapCover` (the codex - a panel over the middle of the screen, with a screen-spanning backdrop
taking the events over the rest of it, so the cover reads no geometry either), `ModalDialogMapCover`
(a confirmation prompt or picker a core screen raises in front of itself, which nothing the campaign
publishes reports), `SidebarMapCover` (any host's panel, through `SidebarHosts`), and
`VanillaChromeMapCover` (the map's own tab strip and control bar, stated as "outside the map surface"
since the chrome widgets are a fact about one game build).

The codex is its own cover rather than a case of the modal beside it, because it is raised outside
the core UI entirely - so the modal's walk answers no on exactly the frames the codex answers yes.
`CodexMapCover` states that, over KMLib's `CodexView`. The arranging dialog is its own for the
mirror-image reason: it is raised *inside* the core UI but descends from nothing of the game's, so the
marker that walk recognises a modal by is not on it.

Both sit ahead of the sidebar's deliberately: either stands the sidebar down, so the panel's own cover
cannot answer for them. `ModalDialogMapCover` and `CodexMapCover` state what follows for their
existence and `MapCoverReader` what follows for the order.

Those two, the pause menu's and the arranging dialog's are four names over one shape: each is a flag
read somewhere else, with no geometry and no cursor test, so `FlagMapCover` holds the delegation and
each of the four is a body of reasoning plus the reading it binds. They stay four classes because
what makes one of them its own is exactly the argument in its Javadoc - which walk cannot see it,
what silences it and what does not - and none of that is expressible as an entry in a list. What is
not four times over is the delegation itself, nor the test of it.

## Two from optional mods

Two more belong to optional mods, live with those mods' own integrations, and join the set only where
the mod is installed - presence being the one condition that cannot move within a run, so the factory
settles it once instead of asking a cover that could only ever answer no. `ConsoleMapCover`
(`kmu.mods.console`) is a text-entry console, which takes the whole screen and so reads
no geometry at all. `RandomAssortmentOfThingsMinimapCover` (`kmu.mods.rat`) is everywhere that
is *not* a docked minimap, on the frames the mode from `RandomAssortmentOfThingsCompatibilityMode` is
engaged and no vanilla map is showing; which surface that minimap is comes from
`SingleEmbeddedMapReader`, the one walk it shares with the rule that switches a parked surface off and
with the tooltip step-aside's search root, so the three cannot act on different answers.

## Order, and failing open

They are held in ascending cost - a polled mouse flag, then a published one-call read, then a settled
flag, then arithmetic over a box this mod laid out, then the two that walk the live widget tree - so
the order is the composition's and each cover states only its own reading. All but the last fail
open: what cannot be established is not covering, since a read taken to refine the hover must not be
able to switch it off. One set for every layer, not one per layer, because nothing about a cover is a
layer's own - a layer holding its own could be given a cover its neighbour was not, which is how a
console came to hide the sidebar while the map went on lighting cells behind it.

## The minimap exception

The minimap cover is the exception on both counts, and deliberately. It is the only one that *opens*
something up - it exists so a permission granted in game space confines to the one surface the player
is actually pointing at, rather than answering over the whole campaign view - and so it is the only
one that fails **closed**: on those frames nothing is pointable except that one box, so a walk that
comes back with nothing, or with two maps, has to cover or the leak returns by way of the read meant
to stop it. It reads a vanilla map's presence first and answers before touching a box, so a
travelling panel cannot reach the `M` map or the intel visor even in principle; the box itself is
read live every frame and never kept, since such a panel walks to its resting place over many frames.
Named for the mod because the switch is - nothing it reads names one, `EmbeddedMap` being found
structurally.
