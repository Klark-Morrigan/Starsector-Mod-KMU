# Settled faces (`base.faces`)

The face each KMU text draws in on one sector,
settled against what the install's fonts can draw.

- [Why faces are settled](#why-faces-are-settled)
- [What a face is held to](#what-a-face-is-held-to)
- [When a face is settled](#when-a-face-is-settled)

## Why faces are settled

A localisation laid over `starsector-core` replaces the game's font atlases with its own,
and not every atlas it replaces holds its script -
the Chinese localisation leaves `insignia42LTaa` as vanilla ships it,
so a text asking for it on that install would draw its Chinese characters as `?`.
KMLib's `FaceResolver`
walks a face down its family and on to the game's default until a face holds the text,
and KMLib's `SettledFaceMemo` holds its answers;
this package is where KMU keeps one memo per sector
and says what each kind of text reads.

The face a text asks for stays with that text -
`LabelFonts`, `SidebarStyles` and `CellTooltipLook` keep their own constants and the reasons for them -
and [`SettledFaces`](SettledFaces.java) answers what that face becomes on this sector.
On a localised install the map labels are the text that moves:
they ask for `insignia42LTaa`, which the localisation leaves without its script,
and settle on the next cut down.
A face that moves is logged once per sector,
which is the first thing a report of text drawn wrongly is read against.

## What a face is held to

A text is held to the [kinds of text](ProbedText.java) it is made of,
each read through KMLib's sector and string readers:

- **Faction names**, short and long both,
  which the map labels draw and the hover box and the sidebar body list.
- **Place names**, every star system's and colony's,
  which the hover box titles and the sidebar body lists.
- **KMU's own strings**, in the running build's locale,
  which every text carries.

Kinds rather than one pool,
because a face held to text it never draws would move for no reason:
a tab carries a layer's name and nothing else,
so it is held to KMU's strings alone,
while a body listing all three is held to `ProbedText.EVERY_KIND`.
Every name the sector holds is read, discovered or not,
since a name the player has yet to see is one the face will draw later.

## When a face is settled

On first asking, and then held for the sector's life
as one of the sector's [installed machinery](../machinery/README.md),
discarded with it on load.
Held rather than resolved per paint,
because settling reads every glyph of every name the sector holds;
two texts asking for one face against the same kinds share one answer,
which keeps a row measured in one face from being painted in another.
The map labels are settled by the owner-map cache off its own sector's machinery
and handed down to the fit, the mint and the band bake,
so a name is fitted, drawn and kept clear of in the one face.

With no game loaded the detached machinery answers,
and every text keeps the face it asks for:
there is no name to hold a face to.
A faction renamed mid-session into another script
keeps the face settled before it until the next load.
