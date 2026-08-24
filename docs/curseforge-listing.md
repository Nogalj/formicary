# CurseForge listing — copy/paste source

Everything the CurseForge project form asks for, filled in. This file is the source of
truth for the listing; if the store page and this disagree, fix this and re-paste.

Verified against CurseForge's own docs on 2026-08-24:
[creating a project](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project),
[file fields](https://support.curseforge.com/support/solutions/articles/9000197242-file-project-types-and-additional-fields).

---

## Project (General tab)

| Field | Value |
|---|---|
| **Name** | `Formicary` |
| **Project URL / slug** | `formicary` |
| **Class** | Mods |
| **Main category** | World Gen → Dimensions |
| **Extra categories** (up to 4) | Mobs · Adventure and RPG · Armor, Tools and Weapons |
| **Project License** | MIT |
| **Logo** | `docs/curseforge-logo.png` — 512×512 PNG (their minimum is 400×400, 1:1) |

### Summary

> Descend into an ant colony that remembers being robbed. A layered underground dimension of soil, amber and glowing fungal gardens — steal a larva, raise your own ants, and answer to the queen.

---

## Description

CurseForge's description editor has an HTML source mode. Paste the block below into that,
not into the rich-text view.

```html
<p><strong>An ant colony is not a dungeon you clear. It is a place you learn to live alongside — and it remembers being robbed.</strong></p>

<p>Throw an ender pearl at a savanna anthill and fall into the Formicary: a layered underground world of packed soil, amber and glowing fungal gardens, worked by four castes of ant and ruled by a queen who does not tolerate visitors.</p>

<h2>A dimension with a floor plan</h2>
<p>Three stacked soil tiers, each darker and less forgiving than the last, threaded with resin seams, amber pockets and fungal gardens that light themselves. Colonies generate with chambers, a larder and a royal depth.</p>

<h2>Ants that live there, not just spawn there</h2>
<p>Workers forage, soldiers respond to trouble, and ender ants blink around the deep tiers. The colony shares a temper: rob a comb in one chamber and soldiers elsewhere will take an interest in you. The deep tiers do not wait to be provoked.</p>

<h2>Raise one yourself</h2>
<p>Steal a larva, feed it, and it imprints on you.</p>
<ul>
<li><strong>Tamed workers</strong> harvest crops and carry them to a chest you bind them to — including pumpkins, melons, and crops added by other mods.</li>
<li><strong>Tamed soldiers</strong> fight beside you and hold a post where you put them.</li>
</ul>

<h2>Find your way back out</h2>
<p>The colony is easy to get lost in, so the <strong>Trail Pheromone</strong> lights the route you actually walked — a breadcrumb trail, not a pathfinder, so it is always a route that works.</p>

<h2>A boss worth the trip</h2>
<p>The queen fights in three phases, burrows, spits acid and calls her brood. She drops the two things worth having: the <strong>Royal Pheromone Gland</strong>, which becomes a horn that calls her own soldiers to <em>your</em> side, and the <strong>Queen's Crest</strong>, which arms the only two tools in the mod that sit above netherite.</p>

<h2>Things to build with</h2>
<p>Chitin armour, resin and amber blocks, brood comb, and three decorative families with full stairs-and-slab sets.</p>

<p><strong>7 entity types &middot; 26 blocks &middot; 41 items &middot; 26 recipes &middot; its own dimension and biomes</strong></p>

<h2>Getting started</h2>
<ol>
<li>Install <strong>NeoForge 21.0.167 or newer</strong> for <strong>Minecraft 1.21</strong>.</li>
<li>Drop the jar into your <code>mods</code> folder.</li>
<li>Find a savanna, find an anthill, throw an ender pearl at it.</li>
</ol>
<p>Works on client and dedicated server. No other mods required, and no config to set up.</p>

<h2>Notes</h2>
<p>Every texture, mob model, mob voice and the mod icon in this project is produced by a script — there are no hand-drawn assets in the repository. It is MIT licensed and built in public.</p>
<p>Source, issue tracker and the full recipe book: <a href="https://github.com/Nogalj/formicary">github.com/Nogalj/formicary</a></p>
<p>Found a bug? Please <a href="https://github.com/Nogalj/formicary/issues">open an issue</a> and say what you were doing, what you expected and what happened. A world seed helps for anything about generation.</p>
```

---

## File upload (Files tab → Add File)

| Field | Value |
|---|---|
| **File** | `build/libs/formicary-1.0.0.jar` |
| **Display Name** | `Formicary 1.0.0` |
| **Release Type** | **Release** — a project needs at least one approved Release file before it syncs to the CurseForge app |
| **Supported Version** | Minecraft `1.21` |
| **Supported Modloader** | **NeoForge** |
| **Related Projects** | none — the mod has no dependencies beyond NeoForge itself |

### Changelog for this file

```
First public release.

THE DIMENSION
- Entered by throwing an ender pearl at a savanna anthill.
- Three stacked soil tiers, generated colonies with chambers, a larder and a royal
  depth, plus resin seams, amber pockets and self-lighting fungal gardens.
- Crops can be planted and farmed inside the dimension.

ANTS
- Worker, soldier and ender ants, larvae, and the queen.
- Colony-wide anger: provoking the colony in one chamber brings soldiers elsewhere.
- The deep tiers are hostile without provocation.

TAMING
- Steal and raise a larva to imprint it on you.
- Tamed workers harvest crops -- including pumpkins, melons and modded crops -- and
  deliver them to a chest you bind them to.
- Tamed soldiers fight alongside you and hold a post.

THE QUEEN
- Three-phase boss with burrowing, an acid-spit ranged attack and a frenzy roar.
- Drops the Royal Pheromone Gland and the Queen's Crest.

ITEMS AND BLOCKS
- Pheromone Horn: calls two allied soldiers for a minute.
- Trail Pheromone: lights the route you actually walked.
- Chitin armour, plus the Pincer Sword and Mandible Pickaxe at Royal tier.
- Three colony foods, and building blocks with full stairs-and-slab sets.
- 26 recipes in total.

SOUND
- Every mob voice is a named sound event, so any of them can be replaced by a
  resource pack without touching the mod.
```

---

## After submitting

- The project goes to **moderation**. CurseForge's support docs say moderators make contact
  within **48–72 hours**.
- A common rejection cause is the logo: it must be **original art**, and specifically must
  not be a blank single-colour square or a generic Minecraft graphic. `curseforge-logo.png`
  is original, so this should pass.
- Once approved, the release page on GitHub and the CurseForge page should be kept in step —
  both are generated from `CHANGELOG.md`.
