# Formicary

An ant-colony dimension for **Minecraft 1.21** on **NeoForge**.

Welcome to the colony...

Throw an ender pearl at a savanna anthill and you fall into the Formicary: a layered
underground world of packed soil, amber and glowing fungal gardens, worked by four castes
of ant and ruled by a queen who does not tolerate visitors.

---

## What's in it

**A dimension with a floor plan.** Three soil tiers stacked from the surface down, each
darker and less forgiving than the last, threaded with resin seams, amber pockets and
fungal gardens that light themselves. Colonies generate with chambers, a larder and a
royal depth.

**Ants that live there, not just spawn there.** Workers ferry and forage, soldiers respond
to trouble, ender ants blink around the deep tiers, and the queen sits at the bottom. The
colony has a shared temper: rob a comb in one chamber and soldiers elsewhere will take an
interest in you.

**Raise one yourself.** Steal a larva, feed it, and it imprints on you.

- **Tamed workers** harvest crops and carry them to a chest you bind them to — including
  pumpkins, melons, and crops added by other mods.
- **Tamed soldiers** fight beside you and stay where you post them.

**Find your way back out.** The colony is easy to get lost in, so the **Trail Pheromone**
lights the route you actually walked — a breadcrumb trail, not a pathfinder, so it is
always a route that works.

**A boss worth the trip.** The queen fights in three phases, burrows, spits acid and calls
her brood. She drops the two things worth having: the **Royal Pheromone Gland**, which
becomes a horn that calls her own soldiers to *your* side, and the **Queen's Crest**, which
arms the only two tools in the mod that sit above netherite.

**Things to build with.** Chitin armour, resin and amber blocks, brood comb, and three
decorative families with stairs-and-slab sets.

By the numbers: 7 entity types, 26 blocks, 41 items, 26 recipes, its own dimension and
biomes, and 100 headless GameTests that run in CI-style on every change.

---

## Installing

1. Install **NeoForge 21.0.167 or newer** for **Minecraft 1.21**.
2. Drop `formicary-1.0.0.jar` into your `mods/` folder.
3. Launch.

Works on both client and dedicated server. No other mods required, and no config to set up.

**To find the dimension:** find a savanna, find an anthill, throw an ender pearl at it.

---

## Building from source

Requires **JDK 21**.

```
git clone https://github.com/Nogalj/formicary.git
cd formicary
./gradlew build
```

The jar lands in `build/libs/`. Useful tasks:

| Task | What it does |
|---|---|
| `./gradlew build` | Compile and package |
| `./gradlew runClient` | Launch a dev client |
| `./gradlew runServer` | Launch a dev dedicated server |
| `./gradlew runData` | Regenerate all JSON (recipes, loot, tags, models, sounds) |
| `./gradlew runGameTestServer` | Run the headless test suite |

**Do not hand-edit anything under `src/generated/`** — it is datagen output and `runData`
will overwrite it.

### The art and audio are code too

There are no hand-drawn assets in this repository. Every texture, mob model, mob voice and
the mod icon is produced by a script in `assets-src/`, and the files under
`src/main/resources/assets/` are build output:

| Script | Generates |
|---|---|
| `assets-src/blocks.py` | Every block and item texture, plus armour layers |
| `assets-src/models.py` | Mob models and their textures |
| `assets-src/sounds.py` | Mob voices, cut from the raw recordings in `assets-src/audio-src/` |
| `assets-src/mod_icon.py` | The mod list logo |
| `assets-src/recipe_book.py` | `docs/recipe-book.html`, built from the datagen output |

Run them with `uv run --with pillow python assets-src/blocks.py` (the sound one also needs
`--with soundfile --with numpy`). Each carries its own guard — transparent item borders,
mono/unclipped audio, no recipe missing from the book — so a broken asset fails the script
rather than shipping.

---

## Documentation

| Document | For |
|---|---|
| [`docs/recipe-book.html`](docs/recipe-book.html) | Every recipe, in crafting-grid form |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Why the mod is built the way it is — every design decision and play-test revision, with the reasoning |
| [`docs/SOUNDS.md`](docs/SOUNDS.md) | Adding or replacing a mob voice |
| [`docs/dev-tools.md`](docs/dev-tools.md) | Headless probes and screenshot tooling |
| [`docs/gotchas/`](docs/gotchas/) | Version-verified NeoForge 1.21 traps hit while building this |

---

## Bugs

Please report them at [github.com/Nogalj/formicary/issues](https://github.com/Nogalj/formicary/issues).
Say what you were doing, what you expected, and what happened — a world seed helps for
anything about generation.

---

## Credits

Built by **Nogal**, in public, with Claude Code.

MIT licensed — see [LICENSE](LICENSE). Do what you like with it.
