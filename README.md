# RSCD Community Client

**The RuneScape Classic community client.** A desktop program for Windows, macOS
and Linux — downloaded and run like any other, no applet or browser plugin
involved.
[`rscd-www`](https://github.com/RSCD-Community/rscd-www) also serves the same
game straight in a browser, nothing to install at all — what this repository
adds on top is real scripting, which the browser build does not carry.

Jagex closed RuneScape Classic permanently in August 2018. This client connects
to the community worlds that keep it playable: **one client, many independently
run servers**, and you choose which one.

---

## Play

You need **[git](https://git-scm.com/downloads)** and **any JDK 8 or newer**.
Nothing else — there are no third-party libraries to fetch and nothing to
install afterwards.

```sh
git clone https://github.com/RSCD-Community/rscd-community-client.git
cd rscd-community-client
./run.sh          # Windows: run.bat
```

That is the whole thing: the first `run.sh` notices there is no jar yet and
builds it, then every later launch just starts the game. Every script checks
its own prerequisites first — no Java, wrong Java, no compiler — and tells you
in plain words what is missing and where to get it, so a failed start is never
a mystery. (`./build.sh` / `build.bat` rebuild by hand after you change the
source or pull a newer version.)

### Put it in your menus — and make Join links work

```sh
./install.sh          # Linux
./install-mac.sh      # macOS
install.bat           # Windows
```

The installer adds the client to your application menu / Start Menu and — the
real point — registers the **`rscd://` link type**, so the **Join Now** buttons
on a community website launch the game straight into the world you clicked. It
is per-user (no admin rights), it never moves this folder, and `--uninstall`
undoes exactly what it did. Details of what a join link can and cannot do:
[`install/join-links.md`](install/join-links.md).

### First run

No server is preselected, so the client opens on the **Worlds** screen — the
live list of community worlds. Pick one and press **Join Now**, or type an address
in yourself. That is the whole setup.

You can also join straight from the command line:

```sh
./run.sh rscd://your.server.example:43594
```

Nothing is downloaded to your machine ahead of time. Each world publishes its
own artwork, maps and definitions, and the client fetches them into memory when
you connect.

---

## What it does that the original did not

- **A community world list.** You pick the server, and you can point the client
  at a different registry entirely — `api_url` in `settings.ini` is a setting,
  not a constant. Nobody has to ask our permission to be listed or to host.
- **Scripting as a supported feature, in three tiers**: full Java scripts,
  simple labelled `.txt` scripts, and an APOS-compatible layer that runs scripts
  written for the original bot. Each flavour has its own guide in
  [`docs/scripts/`](docs/scripts/).
- **An in-client world map**, a movie recorder, and stat panel tabs.
- **Readable source.** The tree began as a decompile of Jagex's obfuscated
  applet; every machine-generated name (`anInt403`, `method276`...) has been
  renamed to what the code actually does.
  [`docs/deobfuscation-map.tsv`](docs/deobfuscation-map.tsv) records every
  old → new mapping, for anyone diffing against other decompiles.
- **It is not an applet.** It resizes, it survives a modern JDK, and it draws
  its own menus instead of borrowing the browser's.

**[`CLIENT.md`](CLIENT.md) is the real documentation** — what the original
applet did, what changed and why, the asset pipeline, the font problem on Linux,
and the full scripting reference. Most of what looks arbitrary in this tree is a
decision a browser made for it twenty years ago, and that file is the record of
it.

## Settings

`settings.ini` holds everything the client remembers: which registry to ask for
worlds, the last server joined, favourites, and the asset cache. It ships with
no server preselected — an empty `default_server` is what sends a fresh install
to the Worlds screen instead of a sign-in box.

The file is not in the repository and you do not need to create it: a client
with no `settings.ini` starts with sensible defaults, and
[`settings.ini.example`](settings.ini.example) documents every key for when you
want to set one by hand.

## Where the game assets are

**Not here.** This repository is the program; the artwork, maps, definitions and
sounds belong to the world you join. Every server publishes its own
`cache_data/` over HTTP and advertises it as `cache_url`; the client downloads
all of it into memory at startup and writes nothing to disk.

That is what lets one client play on many servers, and it means a world can
change an item or redraw the map without shipping anyone a new client.

To point a development client at a local copy:

```sh
JAVA_OPTS=-Drscd.cacheurl=http://localhost/cache_data ./run.sh
```

## Two things to know before you change assets

- **Spell ids are wire-level.** The client sends the *index* of a spell in its
  own `SpellDef` table and the server looks that index up in its own. Both
  tables live with the world — `cache_data/SpellDef.xml.data` and
  `conf/server/defs/SpellDef.xml.gz` in
  [`rscd-server`](https://github.com/RSCD-Community/rscd-server) — and changing
  one without the other makes every spell in the game cast as a different spell.
  Nothing throws.
- **Never run a pre-built client jar you did not build.** Build only from
  audited source, and diff outbound network calls before distributing. The
  scripting lineage this client inherits from once shipped a jar with
  credential-stealing code in it. That is also why this repository exists in the
  open: so you can read it before you run it.

## The splash

`Loading.xml.data`, in that same asset set, is the loading splash — listed first
in `mudclient.gamefiles` so it can go up the moment it lands rather than after
the whole 4.7 MB. Despite the extension it is a plain image, loaded through
`Toolkit.getImage()`, which sniffs the format.

It used to have to be authored at exactly the applet size: `GameWindow` blitted
it unscaled at a fixed `(5, 0)` with a fixed 277x20 progress bar. `GameWindow`
now scales it to whatever the window is and positions the bar as a fraction of
the image, so the master asset stays at full resolution. To replace it, drop the
new file in, re-measure the progress-bar recess, and update the four `BAR_*`
constants at the top of `GameWindow.java`. `BAR_COLOUR` is sampled from the
artwork.

## The rest of the project

| Repository | What it is |
|---|---|
| [`rscd-server`](https://github.com/RSCD-Community/rscd-server) | The game and login daemons, and the assets a world serves. Run your own world. |
| [`rscd-toolkit`](https://github.com/RSCD-Community/rscd-toolkit) | The world editor — edits a server checkout's items, npcs, spawns, sprites and landscape. |
| [`rscd-www`](https://github.com/RSCD-Community/rscd-www) | [rscd-community.org](https://rscd-community.org) — account manager, forums, hiscores, beastiary, the 2003 manual, and browser play. |

Bug reports, corrections and recovered records are welcome on the
[forums](https://rscd-community.org/forums/).

## Licence

**Apache-2.0.** Full text in [`LICENSE`](LICENSE), attribution and lineage in
[`NOTICE`](NOTICE) — read `NOTICE` before forking. The scripting API here is a
reimplementation of a documented contract (SBoT → SkullTorchaScriptable), not a
copy of anyone's code.

RuneScape is a trademark of Jagex Ltd. This project is not affiliated with or
endorsed by Jagex. See
[what this project claims and does not](https://rscd-community.org/about/).
