# Map covers (`maplayers/base/hover/cover`)

Whether anything is drawn over the map where the cursor rests,
which a layer asks before resolving a hover at all.
A hover reads a cell out of map geometry,
which knows nothing of what is composited on top,
so without this it lights cells and floats boxes under whatever is covering them.

Part of [the hover package](../README.md),
and through it of [the map layers framework](../../../README.md).

## Index

- [The role and the reader](#the-role-and-the-reader)
- [The five always in the set](#the-five-always-in-the-set)
- [Covers from optional mods](#covers-from-optional-mods)
- [Order, and failing open](#order-and-failing-open)
- [The minimap exception](#the-minimap-exception)

## The role and the reader

`MapCover` is the role -
one thing that can be over the cursor -
and `MapCoverReader` holds the set and stops at the first that answers.

## The five always in the set

Five are always in the set and live here:
`HeldPointerMapCover`
(a held left button, on which the pointer is pressing rather than pointing - it stands in for vanilla's own marker menu, which no geometry here can see; the class states why),
`PauseMenuMapCover`
(the campaign's pause menu, raised over the screen without taking it down, so the map keeps drawing behind it),
`ScreenClaimMapCover`
(anything that claims the screen from the sidebar too: a console, the codex, this mod's own bar-arranging dialog, or a modal a core screen raises in front of itself),
`SidebarMapCover`
(any host's panel, through `SidebarHosts`),
and `VanillaChromeMapCover`
(the map's own tab strip and control bar, stated as "outside the map surface" since the chrome widgets are a fact about one game build).

The claim cover reads the sidebar's own [`ScreenClaim`](../../sidebar/runtime/README.md),
since every claimant stands the panel down for the same reason it covers the map.
So a claimant is named,
read and gated in one place:
which walk cannot see the codex,
why the arranging dialog needs a reading of its own,
and why the console is asked only where its mod is installed,
are all the claim's to state.
It sits ahead of the sidebar's cover deliberately:
the panel's own cover cannot answer for a claimant that stood it down.

The pause menu's cover and the claim cover are two names over one shape:
each is a flag read somewhere else,
with no geometry and no cursor test,
so `FlagMapCover` holds the delegation and each is a body of reasoning plus the reading it binds.

## Covers from optional mods

A cover that belongs to an optional mod lives with that mod's own integration.
The composition root registers it with `MapCoverReader.registerOptionalModCovers` only where the mod is installed,
so this framework never names an adapter -
presence being the one condition that cannot move within a run,
it is settled once instead of asking a cover that could only ever answer no.
They are asked after every cover of the framework's own.

`RandomAssortmentOfThingsMinimapCover` (`kmu.mods.rat`) is the one today.
It covers everywhere that is *not* a docked minimap,
on the frames the mode from `RandomAssortmentOfThingsCompatibilityMode` is engaged
and no vanilla map is showing;
which surface that minimap is comes from `SingleEmbeddedMapReader`,
the one walk it shares with the rule that switches a parked surface off
and with the tooltip step-aside's search root,
so the three cannot act on different answers.

## Order, and failing open

They are held in ascending cost,
stated once on `MapCoverReader`'s list:

1. `HeldPointerMapCover` -
   a polled mouse flag;
2. `PauseMenuMapCover` -
   a published one-call read;
3. `ScreenClaimMapCover` -
   the console's settled flag,
   one hop off the app state for the codex,
   this mod's own dialog state,
   then a walk of the core UI's own children for a modal;
4. `SidebarMapCover` -
   arithmetic over a box this mod laid out;
5. `VanillaChromeMapCover` -
   a walk into the map tab's own layout;
6. the optional mods' covers,
   in the order they were registered -
   `RandomAssortmentOfThingsMinimapCover` walks the surfaces another mod put on screen,
   the dearest because it is rooted above every tab.

The claim cover sits ahead of the sidebar for a reason beyond cost,
given [above](#the-five-always-in-the-set).
So the order is the composition's and each cover states only its own reading.
All but the last fail open:
what cannot be established is not covering,
since a read taken to refine the hover must not be able to switch it off.
One set for every layer,
not one per layer,
because nothing about a cover is a layer's own -
a layer holding its own could be given a cover its neighbour was not -
a console hiding the sidebar while the map went on lighting cells behind it.

## The minimap exception

The minimap cover is the exception on both counts,
and deliberately.
It is the only one that *opens* something up -
it exists so a permission granted in game space confines to the one surface the player is actually pointing at,
rather than answering over the whole campaign view -
and so it is the only one that fails **closed**:
on those frames nothing is pointable except that one box,
so a walk that comes back with nothing,
or with two maps,
has to cover or the leak returns by way of the read meant to stop it.
It reads a vanilla map's presence first and answers before touching a box,
so a travelling panel cannot reach the `M` map or the intel visor even in principle;
the box itself is read live every frame and never kept,
since such a panel walks to its resting place over many frames.
Named for the mod because the switch is -
nothing it reads names one,
`EmbeddedMap` being found structurally.
