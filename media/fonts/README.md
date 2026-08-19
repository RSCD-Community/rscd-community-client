# Pre-rendered interface fonts

The eight `.jf` files here were baked from **Liberation Sans**, whose advance
widths are identical to genuine Monotype Arial at every glyph and every size
the client uses. Arial is what Windows gave Jagex in 2001, so these carry the
metrics every panel was measured against. See *Which font, and how we know*
below — the earlier answer to that question was wrong.

## What the client does without any files here

RSC ships no font files, and never did. `GameImage.loadFont` asks AWT for
`Helvetica` at eight sizes, draws all 95 printable characters, and keeps the
pixels — so the game's entire layout is decided by whatever font the machine
running the client hands back. Every panel was measured to the pixel against
Helvetica in 2001; the options menu's longest line is 193px inside a 193px
panel, with nothing to spare.

Windows and macOS map Helvetica to Arial and it fits. Linux usually has
neither, and a plain JRE substitutes DejaVu Sans, which is about 27% wider —
vanilla's own text then runs off the side of the window. `GameImage`
compensates by resolving the first family it can actually find from
`Helvetica → Arial → Liberation Sans → Nimbus Sans → FreeSans → Nimbus Sans L`.
Liberation Sans and Nimbus Sans are metric-compatible clones of Arial and
Helvetica, so on any normal desktop the layout comes out right.

**So the client is not broken without these files.** That fallback is doing
its job.

## What files here buy you

Metric compatibility means the *advance widths* match, which is what keeps
panels from overflowing. It does not mean the glyphs are the same shape. Two
players on two operating systems still see slightly different letterforms, and
a machine with none of those six families falls back to Helvetica-by-name and
gets whatever the JRE decides — which is exactly the case the whole fallback
chain cannot rescue.

A `.jf` file is one font already rasterised: the finished pixels, not a
request for AWT to make some. Drop `h11p.jf` in here and every client that
reads it draws with those glyphs, on every platform, forever. Two things
follow:

- the desktop and web clients become pixel-identical, since the web client
  reads the same files rather than measuring text through a canvas;
- a player whose machine has none of the six families stops being at the mercy
  of whatever the JRE picks, which is the one case the fallback cannot rescue.

`GameWindow.loadFonts` tries `media/fonts/<name>.jf` for each of the eight
slots and silently falls through to AWT for any that is missing, so filling
this directory partially is fine, and emptying it again restores today's
behaviour exactly.

## Which font, and how we know

This directory sat empty for a while on the theory that a canonical bake needed
a machine with genuine Helvetica. That was wrong twice over, and the reasoning
is worth keeping because it is easy to fall back into.

`Helvetica` was never a request for the Helvetica typeface. In Java 1.1 it was
one of six *logical* font names, deprecated in that same release and aliased to
`SansSerif`. Windows resolved it to Arial and macOS to real Helvetica, so
"which one did Jagex mean" has an answer only in the sense of which one the
players had — and they were overwhelmingly on Windows, i.e. Arial.

With genuine Arial, genuine Helvetica and the clones all measured over the full
95-glyph charset at all eight slots (760 measurements per column):

| against genuine Arial | advance widths | line height | glyph pixels |
|---|---|---|---|
| Liberation Sans | **0/760 differ** | 0/760 | 655/760 |
| real Helvetica  | 241/760 differ | **760/760** | 646/760 |
| DejaVu Sans (what a bare Linux JRE picks) | 667/760 differ | — | — |

So real Helvetica would have been the *wrong* font to bake from: it disagrees
with Arial on a third of the advance widths and on the line height of every
single slot. The folklore that Arial and Helvetica are metrically identical
holds at print sizes and breaks under integer rounding at 11–24px, which is
exactly where this client lives.

Liberation Sans, meanwhile, is an exact metric match — not "compatible", but
identical at every glyph and every slot, which is what "metric-compatible
clone" turns out to mean when you actually measure it.

### Why not bake from Arial itself

Genuine Arial gives the same layout *and* the authentic glyph shapes, and
`FontBaker` can read it straight from a file. The 105 glyphs where Liberation
and Arial agree pixel-for-pixel are the minority: 16.2% of glyph pixels differ
on average, and Arial's bold at 12px is visibly the heavier of the two.

It is not baked from Arial because these files are rasterised glyph shapes and
the project ships publicly. Liberation Sans is SIL OFL 1.1 and redistributing
its pixels is nobody's problem. If you would rather have Arial's exact
letterforms and are content with that trade, it is one command — see below —
and the layout is unchanged either way, which is the point of the 0/760.

## Making them

```
javac FontBaker.java
java FontBaker . "Liberation Sans"           # what is committed here
java FontBaker . /path/to/fonts/Arial        # authentic glyphs, same metrics
```

The second form takes a **path prefix** and reads `<prefix>-Regular.ttf` and
`<prefix>-Bold.ttf`. Prefer it. A family name goes through fontconfig, which
substitutes silently when it cannot find what you asked for — that substitution
is how this directory ends up holding DejaVu metrics and nobody noticing. A
path cannot be substituted. Both routes were checked against each other on the
same typeface and produce byte-identical bakes, so this is only a question of
where the face comes from.

`FontBaker` is `GameImage.loadFont`/`drawLetter` transliterated onto
`BufferedImage` so it runs headless, with text antialiasing explicitly off —
which is what the original Windows client got from AWT, and what the glyph
cropping downstream assumes. It prints a warning if AWT substituted a
different family for the one you asked for; if you see that warning, the bake
is not canonical. Label it or throw it away.

If you bake from a substitute family deliberately, say so in the commit
message. A `.jf` file carries the family it was made from nowhere in its own
bytes, and the client cannot tell a canonical bake from a bad one.

`FontBaker` was written for the web client port and is byte-exact against the
desktop client's live AWT path — confirmed here on 2026-08-07 by rendering all
eight fonts both ways from the same family and comparing every byte.

## The format

Header, big-endian throughout:

| bytes | meaning                                        |
|-------|------------------------------------------------|
| 4     | magic `RSCF`                                   |
| 1     | version, currently 1                           |
| 1     | length of the font name                        |
| n     | font name in ASCII, e.g. `h11p`                |
| 1     | antialiased flag                               |
| 1     | reserved, zero                                 |
| 4     | payload length                                 |

The payload is exactly what `GameImage.fontData[slot]` holds after a load: 95
glyph headers of 9 bytes each (in charset order), then the cropped 8-bit
coverage bitmaps they point at. Per glyph header: bytes 0–2 are the bitmap's
offset packed base-128, 3 is its width, 4 its height, 5 the left bearing, 6 the
distance up from the baseline, 7 the advance width, 8 the line height.

The name in the header is checked against the slot being filled, so a
mis-copied file is rejected rather than quietly installing the wrong metrics.
