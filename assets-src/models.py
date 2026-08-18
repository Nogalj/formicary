"""
Geometry + texture source of truth for Formicary's entity models.

Model space matches Minecraft entity models: +Y is DOWN (ground at y=24),
-Z is the creature's front, +X is the creature's left.

Box-UV face rects for a cube at texOffs(u,v) with dims (w,h,d), verified
against the decompiled 1.21 `ModelPart.Cube` constructor in
reference/net/minecraft/client/model/geom/ModelPart.java (lines 244-344).
Direction.DOWN is the min-Y face, which the -1/-1/+1 renderer flip puts at
the WORLD top, hence the naming here:

  world-top    (Direction.DOWN):  [u+d,     u+d+w]   x [v,     v+d]
  world-bottom (Direction.UP):    [u+d+w,   u+d+2w]  x [v,     v+d]
  west  (-X):                     [u,       u+d]     x [v+d,   v+d+h]
  north (-Z, front):              [u+d,     u+d+w]   x [v+d,   v+d+h]
  east  (+X):                     [u+d+w,   u+d+w+d] x [v+d,   v+d+h]
  south (+Z, back):               [u+d+w+d, u+2d+2w] x [v+d,   v+d+h]

Face-rect orientation (also read off the Cube vertex/uv pairing):
  * north: rect-x 0 = max X (the creature's LEFT); rect-y 0 = min Y (world top)
  * west / east / south: rect-y 0 = min Y (world top)
  * west: rect-x 0 = min Z (front), increasing toward the back
  * world-top rect: rect-y 0 = max Z (back) -> rect-y max = front

This file is the SINGLE SOURCE OF TRUTH for the numbers in
src/main/java/com/nogal/formicary/client/model/WorkerAntModel.java --
the LayerDefinition there is a hand translation of WORKER_ANT below.

Run with:  python assets-src\\models.py
Requires:  Pillow (PIL).

Outputs:
  src/main/resources/assets/formicary/textures/entity/worker_ant.png  (64x64)
  assets-src/previews/worker_ant_front.png / _side.png / _top.png
  assets-src/previews/worker_ant_sheet.png   (labelled QA contact sheet)
"""

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

TEX = 64
SOLDIER_TEX_W, SOLDIER_TEX_H = 64, 32
LARVA_TEX_W, LARVA_TEX_H = 32, 16
QUEEN_TEX_W, QUEEN_TEX_H = 128, 64
ACID_TEX_W, ACID_TEX_H = 32, 16
REPO_ROOT = Path(__file__).resolve().parent.parent
ENTITY_TEX_DIR = REPO_ROOT / "src/main/resources/assets/formicary/textures/entity"
PREVIEW_DIR = Path(__file__).resolve().parent / "previews"


# ---------------------------------------------------------------- geometry --
# part: {name, pose:(px,py,pz), rot:(xRot,yRot,zRot), cubes:[{off:(u,v), box:(x,y,z,w,h,d)}]}
#
# Poses are ABSOLUTE world-space (the Java hierarchy nests head/gaster/legs
# under `body`, but only leaf parts carry rotations, so absolute poses here
# project identically). Rotations are applied Rz * Ry * Rx, matching
# ModelPart.translateAndRotate -> Quaternionf.rotationZYX(zRot, yRot, xRot).
#
# One exception, and it is opt-in: a part may carry {"parent": <another part
# dict>}, in which case its pose and rot are relative TO THAT PARENT, exactly
# like a nested PartDefinition in Java. `cube_corners` walks the chain. Use it
# only where the flat form cannot express the shape -- a chain of two or more
# hinges, where "absolute" would mean hand-simulating the composition and the
# Java model has to nest anyway so that animating a link carries its children
# (the queen's antennae are the one case so far).

REST_LEG_Z = 0.8378        # leg splay away from vertical
REST_LEG_Y_FRONT = -0.5236  # front legs yaw forward (right side)
REST_LEG_Y_HIND = 0.5236    # hind legs yaw backward (right side)
REST_ANT_X = 0.5236         # antennae tilt forward
REST_ANT_Z = 0.2618         # antennae splay outward

WORKER_ANT = {
    "name": "worker_ant",
    "parts": [
        # --- thorax: the body root; every leg hangs off it ------------------
        {"name": "body", "pose": (0, 20.5, 0), "cubes": [
            {"off": (0, 0), "box": (-2, -1.5, -2, 4, 3, 4)},          # thorax
        ]},
        # --- head: skull + two mandibles ------------------------------------
        {"name": "head", "pose": (0, 20, -2), "cubes": [
            {"off": (0, 19), "box": (-2.5, -2, -4, 5, 4, 4)},         # skull
            {"off": (34, 0), "box": (-2, 0.5, -5, 1, 1, 2)},          # mandible_r
            {"off": (34, 0), "box": (1, 0.5, -5, 1, 1, 2)},           # mandible_l
        ]},
        {"name": "antenna_r", "pose": (-1.5, 18.5, -5.5),
         "rot": (REST_ANT_X, 0, -REST_ANT_Z), "cubes": [
            {"off": (42, 0), "box": (-0.5, -3, -0.5, 1, 3, 1)},
        ]},
        {"name": "antenna_l", "pose": (1.5, 18.5, -5.5),
         "rot": (REST_ANT_X, 0, REST_ANT_Z), "cubes": [
            {"off": (42, 0), "box": (-0.5, -3, -0.5, 1, 3, 1)},
        ]},
        # --- gaster: narrow petiole waist + the big rear mass ---------------
        {"name": "gaster", "pose": (0, 19.5, 0), "cubes": [
            {"off": (24, 0), "box": (-1, 0.5, 1.5, 2, 2, 2)},         # petiole
            {"off": (0, 8), "box": (-2.5, -1.5, 3.5, 5, 4, 6)},       # gaster
        ]},
        # --- six legs, splayed outward at rest ------------------------------
        {"name": "leg_r1", "pose": (-2, 21.9, -1.5),
         "rot": (0, REST_LEG_Y_FRONT, REST_LEG_Z),
         "cubes": [{"off": (48, 0), "box": (-0.5, 0, -0.5, 1, 3, 1)}]},
        {"name": "leg_r2", "pose": (-2, 21.9, 0),
         "rot": (0, 0, REST_LEG_Z),
         "cubes": [{"off": (48, 0), "box": (-0.5, 0, -0.5, 1, 3, 1)}]},
        {"name": "leg_r3", "pose": (-2, 21.9, 1.5),
         "rot": (0, REST_LEG_Y_HIND, REST_LEG_Z),
         "cubes": [{"off": (48, 0), "box": (-0.5, 0, -0.5, 1, 3, 1)}]},
        {"name": "leg_l1", "pose": (2, 21.9, -1.5),
         "rot": (0, -REST_LEG_Y_FRONT, -REST_LEG_Z),
         "cubes": [{"off": (48, 0), "box": (-0.5, 0, -0.5, 1, 3, 1)}]},
        {"name": "leg_l2", "pose": (2, 21.9, 0),
         "rot": (0, 0, -REST_LEG_Z),
         "cubes": [{"off": (48, 0), "box": (-0.5, 0, -0.5, 1, 3, 1)}]},
        {"name": "leg_l3", "pose": (2, 21.9, 1.5),
         "rot": (0, -REST_LEG_Y_HIND, -REST_LEG_Z),
         "cubes": [{"off": (48, 0), "box": (-0.5, 0, -0.5, 1, 3, 1)}]},
    ],
}


# Soldier: a visibly bulkier worker -- bigger armored head + crest, oversized
# mandibles, thicker thorax/gaster, slightly longer legs (so the foot still
# reaches near the ground despite the taller thorax). Mandibles are their own
# posed parts (not baked into the head's cube list like the worker's) because
# WorkerAntModel-style setupAnim needs to flex them independently; each pose
# sits at the mandible's inner-rear hinge corner, so the box below is that
# same absolute box re-expressed relative to the hinge instead of to head.
SOLDIER_ANT = {
    "name": "soldier_ant",
    "parts": [
        # --- thorax: bigger than the worker's (5x4x5 vs 4x3x4) ---------------
        {"name": "body", "pose": (0, 19.5, 0), "cubes": [
            {"off": (0, 12), "box": (-2.5, -2, -2.5, 5, 4, 5)},          # thorax
        ]},
        # --- head: armored skull + raised crest ------------------------------
        {"name": "head", "pose": (0, 19.0, -2.5), "cubes": [
            {"off": (20, 12), "box": (-3, -2, -5, 6, 4, 5)},             # skull
            {"off": (0, 26), "box": (-1.5, -3, -3, 3, 1, 2)},            # crest
        ]},
        {"name": "mandible_r", "pose": (-1, 20.0, -6.5), "cubes": [
            {"off": (0, 21), "box": (-2, -0.5, -3, 2, 1, 3)},
        ]},
        {"name": "mandible_l", "pose": (1, 20.0, -6.5), "cubes": [
            {"off": (0, 21), "box": (0, -0.5, -3, 2, 1, 3)},
        ]},
        {"name": "antenna_r", "pose": (-2.0, 17.5, -7.0),
         "rot": (REST_ANT_X, 0, -REST_ANT_Z), "cubes": [
            {"off": (10, 21), "box": (-0.5, -3, -0.5, 1, 3, 1)},
        ]},
        {"name": "antenna_l", "pose": (2.0, 17.5, -7.0),
         "rot": (REST_ANT_X, 0, REST_ANT_Z), "cubes": [
            {"off": (10, 21), "box": (-0.5, -3, -0.5, 1, 3, 1)},
        ]},
        # --- gaster: 6x5x7 vs the worker's 5x4x6 -----------------------------
        {"name": "gaster", "pose": (0, 18.5, 0), "cubes": [
            {"off": (14, 21), "box": (-1, 0.5, 2.0, 2, 2, 2)},           # petiole
            {"off": (0, 0), "box": (-3, -1.5, 4.0, 6, 5, 7)},            # gaster
        ]},
        # --- six legs, one unit longer than the worker's so the foot still ---
        # --- reaches near the ground under the taller thorax -----------------
        {"name": "leg_r1", "pose": (-2.5, 21.4, -2.0),
         "rot": (0, REST_LEG_Y_FRONT, REST_LEG_Z),
         "cubes": [{"off": (22, 21), "box": (-0.5, 0, -0.5, 1, 4, 1)}]},
        {"name": "leg_r2", "pose": (-2.5, 21.4, 0),
         "rot": (0, 0, REST_LEG_Z),
         "cubes": [{"off": (22, 21), "box": (-0.5, 0, -0.5, 1, 4, 1)}]},
        {"name": "leg_r3", "pose": (-2.5, 21.4, 2.0),
         "rot": (0, REST_LEG_Y_HIND, REST_LEG_Z),
         "cubes": [{"off": (22, 21), "box": (-0.5, 0, -0.5, 1, 4, 1)}]},
        {"name": "leg_l1", "pose": (2.5, 21.4, -2.0),
         "rot": (0, -REST_LEG_Y_FRONT, -REST_LEG_Z),
         "cubes": [{"off": (22, 21), "box": (-0.5, 0, -0.5, 1, 4, 1)}]},
        {"name": "leg_l2", "pose": (2.5, 21.4, 0),
         "rot": (0, 0, -REST_LEG_Z),
         "cubes": [{"off": (22, 21), "box": (-0.5, 0, -0.5, 1, 4, 1)}]},
        {"name": "leg_l3", "pose": (2.5, 21.4, 2.0),
         "rot": (0, -REST_LEG_Y_HIND, -REST_LEG_Z),
         "cubes": [{"off": (22, 21), "box": (-0.5, 0, -0.5, 1, 4, 1)}]},
    ],
}


# Ender ant (Ep2): the worker's body plan, one notch taller on the legs.
#
# Deliberately NOT a new silhouette. This is the same species as the colony's
# workers -- the spec's "ant body plan, near-black chitin + purple accents" --
# so the read at gameplay distance has to come from the palette, not from a
# shape nobody can resolve in a dark tunnel. Every part is the worker's, and
# the only geometry change is legs one unit longer (3 -> 4, the soldier's
# length), which raises the whole body 0.5 so the feet still reach the ground:
# every absolute pose here is the worker's minus 0.5 in Y, and the leg poses
# are the worker's minus 0.5 too, which leaves every parent-relative offset in
# the Java LayerDefinition IDENTICAL to WorkerAntModel's. The one atlas change
# that follows is the leg cube being 1x4x1, which grows its face rects from
# v 0..4 to v 0..5 -- still clear of every other cube on the 64x64 sheet.
ENDER_ANT = {
    "name": "ender_ant",
    "parts": [
        {"name": "body", "pose": (0, 20.0, 0), "cubes": [
            {"off": (0, 0), "box": (-2, -1.5, -2, 4, 3, 4)},          # thorax
        ]},
        {"name": "head", "pose": (0, 19.5, -2), "cubes": [
            {"off": (0, 19), "box": (-2.5, -2, -4, 5, 4, 4)},         # skull
            {"off": (34, 0), "box": (-2, 0.5, -5, 1, 1, 2)},          # mandible_r
            {"off": (34, 0), "box": (1, 0.5, -5, 1, 1, 2)},           # mandible_l
        ]},
        {"name": "antenna_r", "pose": (-1.5, 18.0, -5.5),
         "rot": (REST_ANT_X, 0, -REST_ANT_Z), "cubes": [
            {"off": (42, 0), "box": (-0.5, -3, -0.5, 1, 3, 1)},
        ]},
        {"name": "antenna_l", "pose": (1.5, 18.0, -5.5),
         "rot": (REST_ANT_X, 0, REST_ANT_Z), "cubes": [
            {"off": (42, 0), "box": (-0.5, -3, -0.5, 1, 3, 1)},
        ]},
        {"name": "gaster", "pose": (0, 19.0, 0), "cubes": [
            {"off": (24, 0), "box": (-1, 0.5, 1.5, 2, 2, 2)},         # petiole
            {"off": (0, 8), "box": (-2.5, -1.5, 3.5, 5, 4, 6)},       # gaster
        ]},
        # --- six legs, one unit longer than the worker's --------------------
        {"name": "leg_r1", "pose": (-2, 21.4, -1.5),
         "rot": (0, REST_LEG_Y_FRONT, REST_LEG_Z),
         "cubes": [{"off": (48, 0), "box": (-0.5, 0, -0.5, 1, 4, 1)}]},
        {"name": "leg_r2", "pose": (-2, 21.4, 0),
         "rot": (0, 0, REST_LEG_Z),
         "cubes": [{"off": (48, 0), "box": (-0.5, 0, -0.5, 1, 4, 1)}]},
        {"name": "leg_r3", "pose": (-2, 21.4, 1.5),
         "rot": (0, REST_LEG_Y_HIND, REST_LEG_Z),
         "cubes": [{"off": (48, 0), "box": (-0.5, 0, -0.5, 1, 4, 1)}]},
        {"name": "leg_l1", "pose": (2, 21.4, -1.5),
         "rot": (0, -REST_LEG_Y_FRONT, -REST_LEG_Z),
         "cubes": [{"off": (48, 0), "box": (-0.5, 0, -0.5, 1, 4, 1)}]},
        {"name": "leg_l2", "pose": (2, 21.4, 0),
         "rot": (0, 0, -REST_LEG_Z),
         "cubes": [{"off": (48, 0), "box": (-0.5, 0, -0.5, 1, 4, 1)}]},
        {"name": "leg_l3", "pose": (2, 21.4, 1.5),
         "rot": (0, -REST_LEG_Y_HIND, -REST_LEG_Z),
         "cubes": [{"off": (48, 0), "box": (-0.5, 0, -0.5, 1, 4, 1)}]},
    ],
}


# Larva: a tiny legless, antenna-less grub -- three tapering segments (head
# 3x3x2, mid 4x3x3, tail 3x2x3) resting flush on the ground. `mid` is the
# root; `head`/`tail` are its children so setupAnim can flex them
# independently for the wriggle.
LARVA = {
    "name": "larva",
    "parts": [
        {"name": "mid", "pose": (0, 22.5, 0), "cubes": [
            {"off": (0, 0), "box": (-2, -1.5, -1.5, 4, 3, 3)},
        ]},
        {"name": "head", "pose": (0, 22.5, -1.5), "cubes": [
            {"off": (0, 6), "box": (-1.5, -1.5, -2, 3, 3, 2)},
        ]},
        {"name": "tail", "pose": (0, 23.0, 1.5), "cubes": [
            {"off": (10, 6), "box": (-1.5, -1, 0, 3, 2, 3)},
        ]},
    ],
}


# Queen (M7): the boss. Everything about her reads "the colony's engine" -- the
# gaster is the visual mass (an egg-laying abdomen, 18x14x18 against the
# soldier's 6x5x7), the thorax and head carry the soldier's armour language at
# roughly double scale, and the legs are long enough to carry all of it.
#
# Atlas is 128x64. Box-UV packing, checked to fit:
#   gaster   (0,  0)  72x32     thorax  (0, 32)  56x24
#   head     (72, 0)  46x20     petiole (72,20)  22x11
#   crest    (94,20)  22x7      mandible_base (94,27) 16x7
#   mandible_tip (94,34) 12x6   leg      (56,32)   8x18
#   antenna_base (64,32) 12x9   antenna_mid   (76,32)  8x7
#   antenna_tip  (84,32)  8x6
#
# Ground is y=24; her back sits at y=0, i.e. 24px = 1.5 blocks tall, under the
# 1.8-block hitbox.
#
# Ep2 model pass, item 1: the legs used to hang off (+/-7, 12, z) -- x exactly on
# the thorax's side plane and y a full unit BELOW its underside (the thorax spans
# y 1..11), so every leg started in mid-air with a visible gap between it and the
# body. They are rooted now: x +/-6 puts the 2-wide leg cube two units inside the
# shell and y 9.5 puts its top 1.5 units inside it, so the joint is buried in the
# thorax's lower flank the way a real one is. Raising the root by 2.5 lifts the
# feet by the same amount, so the segment is 3 longer to compensate: feet land at
# 9.5 + 16*cos(0.6109) = 22.6, within a tenth of the old 12 + 13*cos(0.6109) =
# 22.7. Root height, leg length and splay are one number in three parts -- change
# any one alone and she floats or sinks.
QUEEN_REST_LEG_Z = 0.6109       # 35 degrees off vertical
QUEEN_REST_LEG_Y_FRONT = -0.4363
QUEEN_REST_LEG_Y_HIND = 0.4363
QUEEN_LEG_ROOT_X = 6
QUEEN_LEG_ROOT_Y = 9.5
QUEEN_LEG_LENGTH = 16

# Play-test round 1, spec item 2: the mandibles read "too chunky" -- reworked from one
# 5x4x8 block per side into two tapered segments (base 4x3x4, tip 2x2x4) so the jaw
# actually narrows toward the point instead of staying a uniform slab. The tip also
# curls toward the midline (yRot) for a pincer silhouette; sign is empirical -- verified
# by rendering the preview and checking the tips converge rather than splay. Both
# segments stay children of `head`, matching the flat single-level part list every other
# model here uses (see the module docstring): the base's own rest rotation is the
# identity, so nesting the tip under `head` directly instead of under the base produces
# an identical absolute pose while keeping this file's "poses are absolute" invariant
# intact -- no hierarchical composition to hand-simulate.
QUEEN_MANDIBLE_TIP_ANGLE = 0.3491   # ~20 degrees

# Ep2 model pass, item 2: the antennae were one straight 2x7x2 spike per side,
# which at her scale read as a stray leg glued to the skull rather than as a
# feeler. Rebuilt as the three real segments of an ant's antenna, each hinged off
# the last so the whole thing sweeps FORWARD over the jaws instead of standing up:
# a thick 3x3 scape out of the skull, a pedicel that leans in, and a flagellum
# that finishes horizontal, aimed at whatever she is looking at. The pitches add
# up (25 + 30 + 35 = 90 degrees) precisely so the last segment ends level, which
# is the whole silhouette -- something pointing at you rather than at the ceiling.
# The tip lands ~7.6 units past the mandibles' biting point, so the antennae are
# the first thing of her that reaches you.
#
# These are the only NESTED parts in this file (see the geometry comment at the
# top): a three-link chain has no honest flat form, and Java has to nest them
# anyway so that setupAnim's idle sway on the base carries the other two with it
# instead of letting the segments drift apart mid-animation.
QUEEN_ANTENNA_BASE_X = 0.4363   # 25 deg forward, out of the skull
QUEEN_ANTENNA_BASE_Z = 0.2618   # 15 deg outward
QUEEN_ANTENNA_MID_X = 0.5236    # +30 deg: the elbow leans forward
QUEEN_ANTENNA_MID_Z = 0.1745    # +10 deg further out
QUEEN_ANTENNA_TIP_X = 0.6109    # +35 deg -- 90 in total, so the tip runs level
QUEEN_ANTENNA_BASE_LEN = 6
QUEEN_ANTENNA_MID_LEN = 5
QUEEN_ANTENNA_TIP_LEN = 4


def queen_antenna(side):
    """One antenna as three nested segments. `side` is -1 right, +1 left.

    Mirrors QueenAntModel.createBodyLayer's nesting exactly: the base hangs off
    the head with an absolute-in-head pose, and mid/tip carry parent-relative
    poses of (0, -len_of_parent, 0) so each segment starts where the last ended.
    """
    base = {
        "name": f"antenna_{'l' if side > 0 else 'r'}_base",
        "pose": (3 * side, 4, -16),
        "rot": (QUEEN_ANTENNA_BASE_X, 0, QUEEN_ANTENNA_BASE_Z * side),
        "cubes": [{"off": (64, 32),
                   "box": (-1.5, -QUEEN_ANTENNA_BASE_LEN, -1.5, 3, QUEEN_ANTENNA_BASE_LEN, 3)}],
    }
    mid = {
        "name": f"antenna_{'l' if side > 0 else 'r'}_mid",
        "parent": base,
        "pose": (0, -QUEEN_ANTENNA_BASE_LEN, 0),
        "rot": (QUEEN_ANTENNA_MID_X, 0, QUEEN_ANTENNA_MID_Z * side),
        "cubes": [{"off": (76, 32),
                   "box": (-1, -QUEEN_ANTENNA_MID_LEN, -1, 2, QUEEN_ANTENNA_MID_LEN, 2)}],
    }
    tip = {
        "name": f"antenna_{'l' if side > 0 else 'r'}_tip",
        "parent": mid,
        "pose": (0, -QUEEN_ANTENNA_MID_LEN, 0),
        "rot": (QUEEN_ANTENNA_TIP_X, 0, 0),
        "cubes": [{"off": (84, 32),
                   "box": (-1, -QUEEN_ANTENNA_TIP_LEN, -1, 2, QUEEN_ANTENNA_TIP_LEN, 2)}],
    }
    return [base, mid, tip]


QUEEN_ANT = {
    "name": "queen_ant",
    "parts": [
        # --- thorax: the root; legs hang off it --------------------------------
        {"name": "body", "pose": (0, 6, 0), "cubes": [
            {"off": (0, 32), "box": (-7, -5, -7, 14, 10, 14)},
        ]},
        # --- head: broad armoured skull + a tall royal crest --------------------
        {"name": "head", "pose": (0, 6.5, -7), "cubes": [
            {"off": (72, 0), "box": (-6, -4.5, -11, 12, 9, 11)},        # skull
            {"off": (94, 20), "box": (-3, -6.5, -8, 6, 2, 5)},          # crest
        ]},
        {"name": "mandible_r_base", "pose": (-3, 12, -11), "cubes": [
            {"off": (94, 27), "box": (-4, -1.5, -4, 4, 3, 4)},
        ]},
        {"name": "mandible_r_tip", "pose": (-5, 12, -15),
         "rot": (0, -QUEEN_MANDIBLE_TIP_ANGLE, 0), "cubes": [
            {"off": (94, 34), "box": (-1, -1, -4, 2, 2, 4)},
        ]},
        {"name": "mandible_l_base", "pose": (3, 12, -11), "cubes": [
            {"off": (94, 27), "box": (0, -1.5, -4, 4, 3, 4)},
        ]},
        {"name": "mandible_l_tip", "pose": (5, 12, -15),
         "rot": (0, QUEEN_MANDIBLE_TIP_ANGLE, 0), "cubes": [
            {"off": (94, 34), "box": (-1, -1, -4, 2, 2, 4)},
        ]},
        # --- gaster: the mass. Petiole waist, then the egg-swollen abdomen ------
        {"name": "gaster", "pose": (0, 4, 0), "cubes": [
            {"off": (72, 20), "box": (-3, 3, 5, 6, 6, 5)},              # petiole
            {"off": (0, 0), "box": (-9, -4, 9, 18, 14, 18)},            # gaster
        ]},
        # --- six long legs, rooted in the thorax's lower flank -------------------
        {"name": "leg_r1", "pose": (-QUEEN_LEG_ROOT_X, QUEEN_LEG_ROOT_Y, -5),
         "rot": (0, QUEEN_REST_LEG_Y_FRONT, QUEEN_REST_LEG_Z),
         "cubes": [{"off": (56, 32), "box": (-1, 0, -1, 2, QUEEN_LEG_LENGTH, 2)}]},
        {"name": "leg_r2", "pose": (-QUEEN_LEG_ROOT_X, QUEEN_LEG_ROOT_Y, 0),
         "rot": (0, 0, QUEEN_REST_LEG_Z),
         "cubes": [{"off": (56, 32), "box": (-1, 0, -1, 2, QUEEN_LEG_LENGTH, 2)}]},
        {"name": "leg_r3", "pose": (-QUEEN_LEG_ROOT_X, QUEEN_LEG_ROOT_Y, 5),
         "rot": (0, QUEEN_REST_LEG_Y_HIND, QUEEN_REST_LEG_Z),
         "cubes": [{"off": (56, 32), "box": (-1, 0, -1, 2, QUEEN_LEG_LENGTH, 2)}]},
        {"name": "leg_l1", "pose": (QUEEN_LEG_ROOT_X, QUEEN_LEG_ROOT_Y, -5),
         "rot": (0, -QUEEN_REST_LEG_Y_FRONT, -QUEEN_REST_LEG_Z),
         "cubes": [{"off": (56, 32), "box": (-1, 0, -1, 2, QUEEN_LEG_LENGTH, 2)}]},
        {"name": "leg_l2", "pose": (QUEEN_LEG_ROOT_X, QUEEN_LEG_ROOT_Y, 0),
         "rot": (0, 0, -QUEEN_REST_LEG_Z),
         "cubes": [{"off": (56, 32), "box": (-1, 0, -1, 2, QUEEN_LEG_LENGTH, 2)}]},
        {"name": "leg_l3", "pose": (QUEEN_LEG_ROOT_X, QUEEN_LEG_ROOT_Y, 5),
         "rot": (0, -QUEEN_REST_LEG_Y_HIND, -QUEEN_REST_LEG_Z),
         "cubes": [{"off": (56, 32), "box": (-1, 0, -1, 2, QUEEN_LEG_LENGTH, 2)}]},
    ],
}

# Appended rather than written inline: the two chains carry `parent` references to
# dicts, which a list literal cannot express without naming them first.
QUEEN_ANT["parts"].extend(queen_antenna(-1) + queen_antenna(1))


# Acid spit (Ep2, task F2): the queen's ranged attack, a flying blob.
#
# Deliberately symmetric in Y, and that is a rendering fact rather than taste.
# LlamaSpitRenderer -- the reference this follows -- extends EntityRenderer, NOT
# LivingEntityRenderer, so it never applies the scale(-1, -1, 1) flip that makes
# +Y "down" for every mob in this file. A blob that is identical top and bottom
# renders the same either way, so the preview below and the game agree without
# the spec having to pick a convention it cannot honour.
#
# Shape is LlamaSpit's idea at a smaller part count: one 4-cube core with four
# 2-cubes budding off its sides, so it reads as a splattering glob rather than a
# tidy box. All four buds share one atlas rect -- they are the same cube.
#
# Atlas is 32x16:  core (0, 0) 16x8    bud (16, 0) 8x4
ACID_SPIT = {
    "name": "acid_spit",
    "parts": [
        {"name": "core", "pose": (0, 20, 0), "cubes": [
            {"off": (0, 0), "box": (-2, -2, -2, 4, 4, 4)},
        ]},
        {"name": "bud_west", "pose": (0, 20, 0), "cubes": [
            {"off": (16, 0), "box": (-4, -1, -1, 2, 2, 2)},
        ]},
        {"name": "bud_east", "pose": (0, 20, 0), "cubes": [
            {"off": (16, 0), "box": (2, -1, -1, 2, 2, 2)},
        ]},
        {"name": "bud_north", "pose": (0, 20, 0), "cubes": [
            {"off": (16, 0), "box": (-1, -1, -4, 2, 2, 2)},
        ]},
        {"name": "bud_south", "pose": (0, 20, 0), "cubes": [
            {"off": (16, 0), "box": (-1, -1, 2, 2, 2, 2)},
        ]},
    ],
}


# ------------------------------------------------------------------- faces --

def face_rects(u, v, w, h, d):
    """(x0, y0, x1, y1) atlas rect per face; x1/y1 exclusive."""
    w, h, d = int(w), int(h), int(d)
    return {
        "top":    (u + d,         v,     u + d + w,         v + d),
        "bottom": (u + d + w,     v,     u + d + 2 * w,     v + d),
        "west":   (u,             v + d, u + d,             v + d + h),
        "north":  (u + d,         v + d, u + d + w,         v + d + h),
        "east":   (u + d + w,     v + d, u + d + w + d,     v + d + h),
        "south":  (u + d + w + d, v + d, u + 2 * d + 2 * w, v + d + h),
    }


# ------------------------------------------------------------------ palette --
# Warm chitin red-browns, sitting next to the M1 block set's ambers.
# (M1 amber family: (232,160,64) light / (250,205,120) pale -- reused for the
#  eyes and antenna tips so the worker reads as part of the same world.)

CHITIN_DARKEST = (52, 22, 10, 255)
CHITIN_DARK = (84, 38, 17, 255)
CHITIN_BASE = (120, 60, 30, 255)
CHITIN_MID = (146, 76, 38, 255)
CHITIN_LIGHT = (172, 96, 50, 255)
CHITIN_PALE = (196, 120, 66, 255)

BELLY_DARK = (150, 98, 60, 255)
BELLY = (186, 128, 82, 255)
BELLY_LIGHT = (208, 152, 104, 255)

BAND = (72, 30, 13, 255)
SHEEN = (216, 152, 92, 255)

EYE = (250, 205, 120, 255)
EYE_HI = (255, 238, 190, 255)
EYE_DARK = (44, 20, 9, 255)

JAW = (170, 124, 68, 255)
JAW_DARK = (108, 70, 32, 255)

ANTENNA_TIP = (232, 160, 64, 255)

# Play-test round 1, spec item 3: "give tamed worker and tamed soldier their OWN texture
# files distinguished by a clearly visible colored antenna-TIP marker." Royal-jelly gold
# -- brighter and more yellow than either wild tip shade above and below (worker's warm
# amber ANTENNA_TIP, soldier's redder S_ANTENNA_TIP further down), so it reads as a
# distinct marker rather than a shade variant of the same hue at gameplay distance.
TAMED_ANTENNA_TIP = (255, 214, 64, 255)

BODY_PAL = [CHITIN_DARK, CHITIN_BASE, CHITIN_MID, CHITIN_LIGHT, CHITIN_PALE]
BACK_PAL = [CHITIN_DARKEST, CHITIN_DARK, CHITIN_BASE, CHITIN_MID, CHITIN_LIGHT]
BELLY_PAL = [BELLY_DARK, BELLY, BELLY, BELLY_LIGHT, BELLY_LIGHT]
LIMB_PAL = [CHITIN_DARKEST, CHITIN_DARK, CHITIN_DARK, CHITIN_BASE, CHITIN_MID]


# ----------------------------------------------------------- soldier palette --
# Darker, redder chitin than the worker -- deep maroon-browns with near-black
# head plates and lighter mandible tips, so it reads as armored rather than
# just a colour swap.

S_CHITIN_DARKEST = (26, 10, 8, 255)
S_CHITIN_DARK = (58, 18, 14, 255)
S_CHITIN_BASE = (92, 28, 20, 255)
S_CHITIN_MID = (118, 40, 28, 255)
S_CHITIN_LIGHT = (145, 55, 38, 255)
S_CHITIN_PALE = (168, 70, 48, 255)

S_BELLY_DARK = (110, 50, 36, 255)
S_BELLY = (140, 66, 46, 255)
S_BELLY_LIGHT = (164, 84, 58, 255)

S_BAND = (36, 12, 9, 255)
S_SHEEN = (172, 82, 56, 255)

S_HEAD_DARKEST = (16, 7, 6, 255)
S_HEAD_DARK = (30, 13, 11, 255)
S_HEAD_BASE = (46, 19, 15, 255)
S_HEAD_MID = (64, 27, 21, 255)
S_HEAD_LIGHT = (82, 36, 27, 255)

S_JAW = (150, 68, 44, 255)
S_JAW_TIP = (206, 122, 78, 255)
S_JAW_DARK = (94, 42, 26, 255)

S_ANTENNA_TIP = (196, 98, 52, 255)

S_BODY_PAL = [S_CHITIN_DARK, S_CHITIN_BASE, S_CHITIN_MID, S_CHITIN_LIGHT, S_CHITIN_PALE]
S_BACK_PAL = [S_CHITIN_DARKEST, S_CHITIN_DARK, S_CHITIN_BASE, S_CHITIN_MID, S_CHITIN_LIGHT]
S_BELLY_PAL = [S_BELLY_DARK, S_BELLY, S_BELLY, S_BELLY_LIGHT, S_BELLY_LIGHT]
S_LIMB_PAL = [S_CHITIN_DARKEST, S_CHITIN_DARK, S_CHITIN_DARK, S_CHITIN_BASE, S_CHITIN_MID]
S_HEAD_PAL = [S_HEAD_DARK, S_HEAD_BASE, S_HEAD_MID, S_HEAD_LIGHT, S_CHITIN_LIGHT]
S_HEAD_BACK_PAL = [S_HEAD_DARKEST, S_HEAD_DARK, S_HEAD_BASE, S_HEAD_MID, S_HEAD_LIGHT]


# -------------------------------------------------------- ender ant palette --
# Spec section 5: "near-black chitin + purple accents". The two named accents
# are exact -- E_ACCENT is #8A2BE2 and E_ACCENT_BRIGHT is #B26EE8 -- and the
# chitin family is built around #1A1420 as its BASE tone, with the lighter
# steps carrying a violet cast rather than going grey, so the shell reads as
# "black with something in it" instead of as an unlit worker.
#
# The accents are used sparingly and only on the things a player tracks in a
# dark tunnel: the eyes, the antenna tips, the gaster's segment sheen. A body
# painted mostly in #8A2BE2 would out-glow every emissive block in the
# dimension and stop reading as an ant at all.

E_CHITIN_DARKEST = (10, 8, 14, 255)
E_CHITIN_DARK = (18, 14, 22, 255)
E_CHITIN_BASE = (26, 20, 32, 255)       # #1A1420 -- the named shell colour
E_CHITIN_MID = (38, 28, 48, 255)
E_CHITIN_LIGHT = (52, 38, 66, 255)
E_CHITIN_PALE = (72, 52, 92, 255)

E_BELLY_DARK = (30, 22, 38, 255)
E_BELLY = (44, 32, 56, 255)
E_BELLY_LIGHT = (58, 42, 74, 255)

E_BAND = (12, 9, 16, 255)

E_ACCENT = (138, 43, 226, 255)          # #8A2BE2
E_ACCENT_BRIGHT = (178, 110, 232, 255)  # #B26EE8
E_ACCENT_DEEP = (72, 22, 118, 255)      # half-lit #8A2BE2, for accent shadows

E_EYE = E_ACCENT_BRIGHT
E_EYE_DARK = E_ACCENT_DEEP

E_JAW = (64, 40, 88, 255)
E_JAW_DARK = (34, 20, 48, 255)

E_ANTENNA_TIP = E_ACCENT

E_BODY_PAL = [E_CHITIN_DARK, E_CHITIN_BASE, E_CHITIN_MID, E_CHITIN_LIGHT, E_CHITIN_PALE]
E_BACK_PAL = [E_CHITIN_DARKEST, E_CHITIN_DARK, E_CHITIN_BASE, E_CHITIN_MID, E_CHITIN_LIGHT]
E_BELLY_PAL = [E_BELLY_DARK, E_BELLY, E_BELLY, E_BELLY_LIGHT, E_BELLY_LIGHT]
E_LIMB_PAL = [E_CHITIN_DARKEST, E_CHITIN_DARK, E_CHITIN_DARK, E_CHITIN_BASE, E_CHITIN_MID]


# ------------------------------------------------------------- larva palette --
# Pale cream/ivory grub -- faint amber segment lines, tiny dark eye dots.

L_PALE = (252, 246, 232, 255)
L_LIGHT = (248, 238, 218, 255)
L_BASE = (238, 224, 196, 255)
L_SHADE = (222, 204, 174, 255)
L_DARK = (198, 178, 146, 255)

L_LINE = (222, 158, 70, 255)
# Play-test round 1, spec item 4: "stray orange spots on the larva's SIDE faces." The
# west/east faces at the mid/head/tail joints are only 2-3 texels deep, so a full-
# strength L_LINE vband there covers 1/2 to 2/3 of the whole face -- nowhere near the
# "faint" this palette's own comment above promises, and reads as an orange blotch
# rather than a seam. L_LINE_FAINT is L_LINE blended 35% into L_BASE (the segment's own
# base tone) instead of painted at full saturation, so a segment boundary is still
# visible without dominating a face this small.
L_LINE_FAINT = (232, 201, 152, 255)
L_EYE = (60, 40, 24, 255)

L_BODY_PAL = [L_SHADE, L_BASE, L_LIGHT, L_LIGHT, L_PALE]
L_TOP_PAL = [L_DARK, L_SHADE, L_BASE, L_LIGHT, L_PALE]
L_BOTTOM_PAL = [L_DARK, L_DARK, L_SHADE, L_SHADE, L_BASE]


# ------------------------------------------------------------ queen palette --
# Regal rather than merely bigger: a near-black plum carapace for the head and
# thorax with gold banding, over a gaster that is pale and warm -- the eggs
# showing through the shell. That contrast (dark armour / lit belly) is what
# makes the abdomen read as the mass of her rather than just a large box.

Q_PLUM_DARKEST = (22, 10, 18, 255)
Q_PLUM_DARK = (44, 18, 34, 255)
Q_PLUM_BASE = (70, 28, 48, 255)
Q_PLUM_MID = (96, 40, 62, 255)
Q_PLUM_LIGHT = (124, 56, 78, 255)

Q_GOLD_DEEP = (146, 96, 26, 255)
Q_GOLD = (206, 152, 48, 255)
Q_GOLD_BRIGHT = (244, 202, 104, 255)

# Gaster: pale, egg-swollen. Its own ramp, warmer and much lighter than the plum.
Q_SAC_DARK = (170, 124, 84, 255)
Q_SAC_BASE = (214, 172, 118, 255)
Q_SAC_MID = (234, 198, 146, 255)
Q_SAC_LIGHT = (246, 220, 176, 255)
Q_SAC_PALE = (253, 240, 210, 255)

Q_EYE = (255, 226, 150, 255)
Q_EYE_DARK = (30, 12, 22, 255)


# ------------------------------------------------------------- acid palette --
# The queen's spit (Ep2). Bile green, deliberately the one thing in this mod that
# is NOT in the warm chitin/amber family: it has to read as "not part of her" at a
# glance, from across a chamber, in the dark. The pale top tone is what carries it
# at that distance; the dark rim is what keeps it from looking like a slime ball.

A_ACID_DARKEST = (24, 46, 12, 255)
A_ACID_DARK = (46, 84, 22, 255)
A_ACID_BASE = (86, 150, 36, 255)
A_ACID_MID = (124, 190, 52, 255)
A_ACID_LIGHT = (166, 220, 84, 255)
A_ACID_PALE = (214, 248, 148, 255)

A_ACID_PAL = [A_ACID_DARK, A_ACID_BASE, A_ACID_MID, A_ACID_LIGHT, A_ACID_PALE]
A_ACID_RIM_PAL = [A_ACID_DARKEST, A_ACID_DARK, A_ACID_DARK, A_ACID_BASE, A_ACID_MID]

# Ep2 model pass, item 3: the crest was six flat fills of Q_GOLD_DEEP -- a solid
# yellow slab on the one part of her that is supposed to read as a crown. Its own
# ramp instead, running plum -> gold, so the crest is chitin plating shot through
# with gold rather than a block of gold. noise_rect's default weights put most of
# the pixels on index 2, so keeping Q_GOLD_DEEP there holds the overall read at
# gameplay distance while the plum and bright-gold tones do the mottling.
Q_CREST_PAL = [Q_PLUM_DARKEST, Q_PLUM_BASE, Q_GOLD_DEEP, Q_GOLD, Q_GOLD_BRIGHT]
Q_CREST_TOP_PAL = [Q_PLUM_DARK, Q_GOLD_DEEP, Q_GOLD, Q_GOLD_BRIGHT, Q_GOLD_BRIGHT]

Q_SHELL_PAL = [Q_PLUM_DARK, Q_PLUM_BASE, Q_PLUM_MID, Q_PLUM_LIGHT, Q_GOLD_DEEP]
Q_SHELL_BACK_PAL = [Q_PLUM_DARKEST, Q_PLUM_DARK, Q_PLUM_BASE, Q_PLUM_MID, Q_PLUM_LIGHT]
Q_SAC_PAL = [Q_SAC_DARK, Q_SAC_BASE, Q_SAC_MID, Q_SAC_LIGHT, Q_SAC_PALE]
Q_SAC_TOP_PAL = [Q_SAC_DARK, Q_SAC_DARK, Q_SAC_BASE, Q_SAC_MID, Q_SAC_LIGHT]
Q_SAC_BELLY_PAL = [Q_SAC_MID, Q_SAC_LIGHT, Q_SAC_PALE, Q_SAC_PALE, Q_SAC_PALE]
Q_LIMB_PAL = [Q_PLUM_DARKEST, Q_PLUM_DARK, Q_PLUM_DARK, Q_PLUM_BASE, Q_PLUM_MID]


# ----------------------------------------------------------------- painting --

def noise_rect(draw, rect, seed, palette, weights=None, jitter=0.24, cell=2, namespace="worker_ant"):
    """Clumped multi-tone fill inside `rect` -- a coarse `cell`-sized tone grid
    with per-pixel +/-1 tone jitter. Same technique as blocks.py, so the ant
    shades in connected clumps rather than reading as flat colour or speckle.
    Deterministic: the same seed always paints the same pixels.
    `namespace` keys the RNG per model (worker/soldier/larva) so identical
    part-face seed strings across models don't paint identical noise."""
    x0, y0, x1, y1 = rect
    if x1 <= x0 or y1 <= y0:
        return
    r = random.Random(f"formicary:{namespace}:{seed}")
    n = len(palette)
    if weights is None:
        weights = [1, 3, 6, 3, 1][:n] if n == 5 else [1] * n
    gw = max(1, (x1 - x0 + cell - 1) // cell)
    gh = max(1, (y1 - y0 + cell - 1) // cell)
    grid = [[r.choices(range(n), weights)[0] for _ in range(gw)] for _ in range(gh)]
    for y in range(y0, y1):
        for x in range(x0, x1):
            t = grid[(y - y0) // cell][(x - x0) // cell]
            if r.random() < jitter:
                t = max(0, min(n - 1, t + r.choice((-1, 1))))
            draw.point((x, y), fill=palette[t])


def fill(draw, rect, color):
    x0, y0, x1, y1 = rect
    if x1 > x0 and y1 > y0:
        draw.rectangle([x0, y0, x1 - 1, y1 - 1], fill=color)


def px(draw, rect, x, y, color):
    """Paint one texel at rect-local (x, y)."""
    x0, y0, x1, y1 = rect
    if 0 <= x < x1 - x0 and 0 <= y < y1 - y0:
        draw.point((x0 + x, y0 + y), fill=color)


def hband(draw, rect, y, color):
    """Full-width 1px row at rect-local y."""
    x0, y0, x1, y1 = rect
    if 0 <= y < y1 - y0:
        draw.rectangle([x0, y0 + y, x1 - 1, y0 + y], fill=color)


def vband(draw, rect, x, color):
    """Full-height 1px column at rect-local x."""
    x0, y0, x1, y1 = rect
    if 0 <= x < x1 - x0:
        draw.rectangle([x0 + x, y0, x0 + x, y1 - 1], fill=color)


def paint_worker_ant(antenna_tip_color=ANTENNA_TIP):
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    parts = {p["name"]: p for p in WORKER_ANT["parts"]}

    def rects(cube):
        u, v = cube["off"]
        _, _, _, w, h, dd = cube["box"]
        return face_rects(u, v, w, h, dd)

    # ---- thorax: mid chitin, dark seam where the head and petiole join -----
    r = rects(parts["body"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "thorax:" + f, BODY_PAL)
    noise_rect(d, r["top"], "thorax:top", BACK_PAL)
    noise_rect(d, r["bottom"], "thorax:bottom", BELLY_PAL)
    hband(d, r["top"], 0, BAND)                 # seam against the gaster (back)
    hband(d, r["top"], 3, BAND)                 # seam against the head (front)
    for f in ("west", "east"):
        vband(d, r[f], 0, CHITIN_DARK)          # front-edge shadow
        vband(d, r[f], 3, BAND)                 # rear-edge shadow
    px(d, r["top"], 1, 1, SHEEN)
    px(d, r["top"], 2, 2, SHEEN)

    # ---- gaster: the big rear mass, banded like a real ant's metasoma -----
    petiole, gaster = parts["gaster"]["cubes"]
    r = rects(petiole)
    for f in ("west", "north", "east", "south", "top"):
        fill(d, r[f], CHITIN_DARK)
    fill(d, r["bottom"], BELLY_DARK)
    for f in ("west", "east"):
        hband(d, r[f], 0, CHITIN_BASE)          # lit upper edge of the waist

    r = rects(gaster)
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "gaster:" + f, BODY_PAL)
    noise_rect(d, r["top"], "gaster:top", BACK_PAL)
    noise_rect(d, r["bottom"], "gaster:bottom", BELLY_PAL)
    # segment banding: on the side faces rect-x runs front -> back
    for f in ("west", "east"):
        vband(d, r[f], 0, CHITIN_DARK)          # seam against the petiole
        for bx in (2, 4):
            vband(d, r[f], bx, BAND)
        hband(d, r[f], 0, CHITIN_DARK)          # dark upper rim
        px(d, r[f], 1, 2, CHITIN_LIGHT)
        px(d, r[f], 3, 1, SHEEN)
    # on the world-top face rect-y runs back -> front
    for by in (0, 2, 4):
        hband(d, r["top"], by, BAND)
    px(d, r["top"], 2, 3, SHEEN)
    px(d, r["top"], 3, 1, SHEEN)
    hband(d, r["north"], 0, CHITIN_DARK)        # shaded where it meets the waist
    fill(d, (r["south"][0], r["south"][1], r["south"][2], r["south"][1] + 1), CHITIN_DARK)
    px(d, r["south"], 2, 2, CHITIN_DARKEST)     # tail tip dimple

    # ---- head: eyes, brow, mouth ------------------------------------------
    skull, mandible = parts["head"]["cubes"][0], parts["head"]["cubes"][1]
    r = rects(skull)
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "skull:" + f, BODY_PAL)
    noise_rect(d, r["top"], "skull:top", BACK_PAL)
    noise_rect(d, r["bottom"], "skull:bottom", BELLY_PAL)
    n = r["north"]                              # 5 wide x 4 tall front face
    hband(d, n, 0, CHITIN_DARK)                 # brow ridge
    for ex in (0, 4):                           # small pale amber eye spots
        px(d, n, ex, 0, CHITIN_DARKEST)         # socket shadow above the eye
        px(d, n, ex, 1, EYE)
        px(d, n, ex, 2, EYE_DARK)               # dark lower rim / pupil
    px(d, n, 2, 0, CHITIN_BASE)                 # frontal ocellus notch
    d.rectangle([n[0] + 1, n[1] + 3, n[0] + 3, n[1] + 3], fill=BAND)  # mouth line
    # the compound eyes wrap onto the sides of the head
    for f in ("west", "east"):
        px(d, r[f], 0, 1, EYE)
        px(d, r[f], 0, 2, EYE_DARK)
    hband(d, r["top"], 3, CHITIN_DARK)          # shadow under the brow
    px(d, r["top"], 2, 1, SHEEN)

    r = rects(mandible)
    for f in ("west", "north", "east", "south", "top"):
        fill(d, r[f], JAW)
    fill(d, r["bottom"], JAW_DARK)
    fill(d, r["north"], JAW_DARK)               # the -Z face is the biting tip
    for f in ("west", "east"):
        vband(d, r[f], 0, JAW_DARK)             # darkened toward the tip
    px(d, r["top"], 0, 1, JAW_DARK)

    # ---- antennae: dark shaft, amber tip (min-Y end is the tip) -----------
    # `antenna_tip_color` is the tamed-caste hook (spec item 3) -- wild callers don't
    # pass it and get the amber above.
    r = rects(parts["antenna_r"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "antenna:" + f, LIMB_PAL, cell=1)
        hband(d, r[f], 0, antenna_tip_color)
        hband(d, r[f], 2, CHITIN_DARKEST)       # base joint
    fill(d, r["top"], antenna_tip_color)
    fill(d, r["bottom"], CHITIN_DARKEST)

    # ---- legs: dark chitin, lit joint band, near-black foot ---------------
    r = rects(parts["leg_r1"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "leg:" + f, LIMB_PAL, cell=1)
        hband(d, r[f], 1, CHITIN_MID)           # knee joint catches the light
        hband(d, r[f], 2, CHITIN_DARKEST)       # tarsus / foot
    fill(d, r["top"], CHITIN_DARK)
    fill(d, r["bottom"], CHITIN_DARKEST)

    return img


def paint_ender_ant():
    """The worker's painter re-cut in the ender palette.

    Deliberately a sibling of `paint_worker_ant` rather than a parameterised
    call into it: the accents do not land in the same places the worker's
    ambers do (the gaster gets a lit segment rim it has no equivalent of, the
    eyes are twice the size, the mouth line is an accent rather than a shadow),
    so threading five palettes plus three placement flags through the worker
    would have made both harder to read than one honest copy. The `namespace`
    argument to `noise_rect` keys the RNG per model, so this paints its own
    clumps rather than a recoloured copy of the worker's.
    """
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    parts = {p["name"]: p for p in ENDER_ANT["parts"]}
    NS = "ender_ant"

    def rects(cube):
        u, v = cube["off"]
        _, _, _, w, h, dd = cube["box"]
        return face_rects(u, v, w, h, dd)

    # ---- thorax: near-black shell, one accent spark on the back ------------
    r = rects(parts["body"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "thorax:" + f, E_BODY_PAL, namespace=NS)
    noise_rect(d, r["top"], "thorax:top", E_BACK_PAL, namespace=NS)
    noise_rect(d, r["bottom"], "thorax:bottom", E_BELLY_PAL, namespace=NS)
    hband(d, r["top"], 0, E_BAND)               # seam against the gaster (back)
    hband(d, r["top"], 3, E_BAND)               # seam against the head (front)
    for f in ("west", "east"):
        vband(d, r[f], 0, E_CHITIN_DARK)        # front-edge shadow
        vband(d, r[f], 3, E_BAND)               # rear-edge shadow
    px(d, r["top"], 1, 1, E_ACCENT_DEEP)
    px(d, r["top"], 2, 2, E_ACCENT)

    # ---- gaster: banded, with the accent riding the segment rims -----------
    petiole, gaster = parts["gaster"]["cubes"]
    r = rects(petiole)
    for f in ("west", "north", "east", "south", "top"):
        fill(d, r[f], E_CHITIN_DARKEST)
    fill(d, r["bottom"], E_BELLY_DARK)
    for f in ("west", "east"):
        hband(d, r[f], 0, E_ACCENT_DEEP)        # lit upper edge of the waist

    r = rects(gaster)
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "gaster:" + f, E_BODY_PAL, namespace=NS)
    noise_rect(d, r["top"], "gaster:top", E_BACK_PAL, namespace=NS)
    noise_rect(d, r["bottom"], "gaster:bottom", E_BELLY_PAL, namespace=NS)
    # segment banding: on the side faces rect-x runs front -> back
    for f in ("west", "east"):
        vband(d, r[f], 0, E_CHITIN_DARKEST)     # seam against the petiole
        for bx in (2, 4):
            vband(d, r[f], bx, E_BAND)
        px(d, r[f], 3, 1, E_ACCENT)             # one spark per segment gap
        px(d, r[f], 5, 2, E_ACCENT_DEEP)
        hband(d, r[f], 0, E_CHITIN_DARKEST)     # dark upper rim
    # on the world-top face rect-y runs back -> front
    for by in (0, 2, 4):
        hband(d, r["top"], by, E_BAND)
    px(d, r["top"], 2, 3, E_ACCENT)
    px(d, r["top"], 3, 1, E_ACCENT_DEEP)
    hband(d, r["north"], 0, E_CHITIN_DARKEST)   # shaded where it meets the waist
    fill(d, (r["south"][0], r["south"][1], r["south"][2], r["south"][1] + 1), E_CHITIN_DARKEST)
    px(d, r["south"], 2, 2, E_ACCENT)           # tail tip glows

    # ---- head: big accent eyes, accent mouth line -------------------------
    skull, mandible = parts["head"]["cubes"][0], parts["head"]["cubes"][1]
    r = rects(skull)
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "skull:" + f, E_BODY_PAL, namespace=NS)
    noise_rect(d, r["top"], "skull:top", E_BACK_PAL, namespace=NS)
    noise_rect(d, r["bottom"], "skull:bottom", E_BELLY_PAL, namespace=NS)
    n = r["north"]                              # 5 wide x 4 tall front face
    hband(d, n, 0, E_CHITIN_DARKEST)            # brow ridge
    for ex in (0, 4):                           # two-texel eyes, not the worker's one
        px(d, n, ex, 1, E_EYE)
        px(d, n, ex, 2, E_EYE_DARK)
    px(d, n, 1, 1, E_ACCENT_DEEP)               # inner corner of each eye
    px(d, n, 3, 1, E_ACCENT_DEEP)
    px(d, n, 2, 0, E_CHITIN_MID)                # frontal ocellus notch
    d.rectangle([n[0] + 1, n[1] + 3, n[0] + 3, n[1] + 3], fill=E_ACCENT_DEEP)  # mouth line
    # the compound eyes wrap onto the sides of the head
    for f in ("west", "east"):
        px(d, r[f], 0, 1, E_EYE)
        px(d, r[f], 0, 2, E_EYE_DARK)
    hband(d, r["top"], 3, E_CHITIN_DARKEST)     # shadow under the brow
    px(d, r["top"], 2, 1, E_ACCENT_DEEP)

    r = rects(mandible)
    for f in ("west", "north", "east", "south", "top"):
        fill(d, r[f], E_JAW)
    fill(d, r["bottom"], E_JAW_DARK)
    fill(d, r["north"], E_ACCENT_DEEP)          # the -Z face is the biting tip
    for f in ("west", "east"):
        vband(d, r[f], 0, E_JAW_DARK)           # darkened toward the tip
    px(d, r["top"], 0, 1, E_JAW_DARK)

    # ---- antennae: black shaft, accent tip (min-Y end is the tip) ---------
    r = rects(parts["antenna_r"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "antenna:" + f, E_LIMB_PAL, cell=1, namespace=NS)
        hband(d, r[f], 0, E_ANTENNA_TIP)
        hband(d, r[f], 2, E_CHITIN_DARKEST)     # base joint
    fill(d, r["top"], E_ANTENNA_TIP)
    fill(d, r["bottom"], E_CHITIN_DARKEST)

    # ---- legs: 4 long, so one more banded row than the worker's ----------
    r = rects(parts["leg_r1"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "leg:" + f, E_LIMB_PAL, cell=1, namespace=NS)
        hband(d, r[f], 1, E_ACCENT_DEEP)        # knee joint carries the accent
        hband(d, r[f], 3, E_CHITIN_DARKEST)     # tarsus / foot
    fill(d, r["top"], E_CHITIN_DARK)
    fill(d, r["bottom"], E_CHITIN_DARKEST)

    return img


def paint_soldier_ant(antenna_tip_color=S_ANTENNA_TIP):
    img = Image.new("RGBA", (SOLDIER_TEX_W, SOLDIER_TEX_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    parts = {p["name"]: p for p in SOLDIER_ANT["parts"]}
    NS = "soldier_ant"

    def rects(cube):
        u, v = cube["off"]
        _, _, _, w, h, dd = cube["box"]
        return face_rects(u, v, w, h, dd)

    # ---- thorax: bulkier, maroon chitin ------------------------------------
    r = rects(parts["body"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "thorax:" + f, S_BODY_PAL, namespace=NS)
    noise_rect(d, r["top"], "thorax:top", S_BACK_PAL, namespace=NS)
    noise_rect(d, r["bottom"], "thorax:bottom", S_BELLY_PAL, namespace=NS)
    hband(d, r["top"], 0, S_BAND)                # seam against the gaster (back)
    hband(d, r["top"], 4, S_BAND)                # seam against the head (front)
    for f in ("west", "east"):
        vband(d, r[f], 0, S_CHITIN_DARK)
        vband(d, r[f], 4, S_BAND)
    px(d, r["top"], 2, 2, S_SHEEN)

    # ---- gaster: petiole waist + the big rear mass -------------------------
    petiole, gaster = parts["gaster"]["cubes"]
    r = rects(petiole)
    for f in ("west", "north", "east", "south", "top"):
        fill(d, r[f], S_CHITIN_DARK)
    fill(d, r["bottom"], S_BELLY_DARK)
    for f in ("west", "east"):
        hband(d, r[f], 0, S_CHITIN_BASE)

    r = rects(gaster)
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "gaster:" + f, S_BODY_PAL, namespace=NS)
    noise_rect(d, r["top"], "gaster:top", S_BACK_PAL, namespace=NS)
    noise_rect(d, r["bottom"], "gaster:bottom", S_BELLY_PAL, namespace=NS)
    for f in ("west", "east"):
        vband(d, r[f], 0, S_CHITIN_DARK)
        for bx in (2, 5):
            vband(d, r[f], bx, S_BAND)
        hband(d, r[f], 0, S_CHITIN_DARK)
        px(d, r[f], 1, 2, S_CHITIN_LIGHT)
    for by in (0, 3, 6):
        hband(d, r["top"], by, S_BAND)
    hband(d, r["north"], 0, S_CHITIN_DARK)
    px(d, r["south"], 3, 2, S_CHITIN_DARKEST)

    # ---- head: near-black armored plates, amber eyes, brow ridge ----------
    skull, crest = parts["head"]["cubes"][0], parts["head"]["cubes"][1]
    r = rects(skull)
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "skull:" + f, S_HEAD_PAL, namespace=NS)
    noise_rect(d, r["top"], "skull:top", S_HEAD_BACK_PAL, namespace=NS)
    noise_rect(d, r["bottom"], "skull:bottom", S_BELLY_PAL, namespace=NS)
    n = r["north"]                              # 6 wide x 4 tall front face
    hband(d, n, 0, S_HEAD_DARKEST)              # brow ridge
    for ex in (0, 5):                           # amber eye spots near the edges
        px(d, n, ex, 0, S_HEAD_DARKEST)
        px(d, n, ex, 1, EYE)
        px(d, n, ex, 2, EYE_DARK)
    px(d, n, 2, 0, S_HEAD_BASE)
    px(d, n, 3, 0, S_HEAD_BASE)
    d.rectangle([n[0] + 1, n[1] + 3, n[0] + 4, n[1] + 3], fill=S_BAND)  # mouth line
    for f in ("west", "east"):
        px(d, r[f], 0, 1, EYE)
        px(d, r[f], 0, 2, EYE_DARK)
    hband(d, r["top"], 4, S_HEAD_DARKEST)       # shadow where the crest sits

    r = rects(crest)
    for f in ("west", "north", "east", "south", "top"):
        fill(d, r[f], S_HEAD_DARKEST)
    fill(d, r["bottom"], S_HEAD_DARK)
    for f in ("west", "east"):
        hband(d, r[f], 0, S_CHITIN_MID)         # thin lit ridge along the top

    # ---- mandibles: dark base, lighter biting tip --------------------------
    r = rects(parts["mandible_r"]["cubes"][0])
    for f in ("west", "north", "east", "south", "top"):
        fill(d, r[f], S_JAW)
    fill(d, r["bottom"], S_JAW_DARK)
    fill(d, r["north"], S_JAW_TIP)               # the -Z face is the biting tip
    for f in ("west", "east"):
        vband(d, r[f], 0, S_JAW_TIP)
        vband(d, r[f], 2, S_JAW_DARK)
    px(d, r["top"], 0, 1, S_JAW_DARK)

    # ---- antennae: dark shaft, redder tip (min-Y end is the tip) -----------
    # `antenna_tip_color` is the tamed-caste hook (spec item 3) -- wild callers don't
    # pass it and get the maroon-red S_ANTENNA_TIP above.
    r = rects(parts["antenna_r"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "antenna:" + f, S_LIMB_PAL, cell=1, namespace=NS)
        hband(d, r[f], 0, antenna_tip_color)
        hband(d, r[f], 2, S_CHITIN_DARKEST)     # base joint
    fill(d, r["top"], antenna_tip_color)
    fill(d, r["bottom"], S_CHITIN_DARKEST)

    # ---- legs: dark chitin, lit joint band, near-black foot ---------------
    r = rects(parts["leg_r1"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "leg:" + f, S_LIMB_PAL, cell=1, namespace=NS)
        hband(d, r[f], 1, S_CHITIN_MID)         # knee joint catches the light
        hband(d, r[f], 3, S_CHITIN_DARKEST)     # tarsus / foot
    fill(d, r["top"], S_CHITIN_DARK)
    fill(d, r["bottom"], S_CHITIN_DARKEST)

    return img


def paint_larva():
    img = Image.new("RGBA", (LARVA_TEX_W, LARVA_TEX_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    parts = {p["name"]: p for p in LARVA["parts"]}
    NS = "larva"

    def rects(cube):
        u, v = cube["off"]
        _, _, _, w, h, dd = cube["box"]
        return face_rects(u, v, w, h, dd)

    # ---- mid: the widest segment, pale cream -------------------------------
    # Play-test round 1, spec item 4: mid used to paint its OWN seam at both its front
    # (against head) and back (against tail) edge, on top of head's and tail's each
    # painting their own seam at that identical boundary -- every junction got marked
    # twice. On a face this thin (3 texels wide) two full-brightness L_LINE columns left
    # only the single middle column unpainted, which is what read as "stray orange
    # spots" rather than a seam. Mid no longer paints a seam of its own; the boundary is
    # still marked once, by head/tail below, in the softened L_LINE_FAINT.
    r = rects(parts["mid"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "mid:" + f, L_BODY_PAL, namespace=NS, jitter=0.14, cell=1)
    noise_rect(d, r["top"], "mid:top", L_TOP_PAL, namespace=NS, jitter=0.14, cell=1)
    noise_rect(d, r["bottom"], "mid:bottom", L_BOTTOM_PAL, namespace=NS, jitter=0.14, cell=1)

    # ---- head: tapered, carries the two eye dots ---------------------------
    r = rects(parts["head"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "head:" + f, L_BODY_PAL, namespace=NS, jitter=0.14, cell=1)
    noise_rect(d, r["top"], "head:top", L_TOP_PAL, namespace=NS, jitter=0.14, cell=1)
    noise_rect(d, r["bottom"], "head:bottom", L_BOTTOM_PAL, namespace=NS, jitter=0.14, cell=1)
    n = r["north"]                               # 3 wide x 3 tall front face
    px(d, n, 0, 1, L_EYE)
    px(d, n, 2, 1, L_EYE)
    for f in ("west", "east"):
        vband(d, r[f], 1, L_LINE_FAINT)         # seam against mid

    # ---- tail: shorter and thinner, tapering down --------------------------
    r = rects(parts["tail"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "tail:" + f, L_BODY_PAL, namespace=NS, jitter=0.14, cell=1)
    noise_rect(d, r["top"], "tail:top", L_TOP_PAL, namespace=NS, jitter=0.14, cell=1)
    noise_rect(d, r["bottom"], "tail:bottom", L_BOTTOM_PAL, namespace=NS, jitter=0.14, cell=1)
    for f in ("west", "east"):
        vband(d, r[f], 0, L_LINE_FAINT)         # seam against mid

    return img


def paint_queen_ant():
    img = Image.new("RGBA", (QUEEN_TEX_W, QUEEN_TEX_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    parts = {p["name"]: p for p in QUEEN_ANT["parts"]}
    NS = "queen_ant"

    def rects(cube):
        u, v = cube["off"]
        _, _, _, w, h, dd = cube["box"]
        return face_rects(u, v, w, h, dd)

    # ---- thorax: plum plate armour with a gold seam front and back ----------
    r = rects(parts["body"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "thorax:" + f, Q_SHELL_PAL, namespace=NS, cell=3)
    noise_rect(d, r["top"], "thorax:top", Q_SHELL_BACK_PAL, namespace=NS, cell=3)
    noise_rect(d, r["bottom"], "thorax:bottom", Q_LIMB_PAL, namespace=NS, cell=3)
    hband(d, r["top"], 0, Q_GOLD_DEEP)           # seam against the gaster (back)
    hband(d, r["top"], 13, Q_GOLD_DEEP)          # seam against the head (front)
    for f in ("west", "east"):
        vband(d, r[f], 0, Q_GOLD_DEEP)
        vband(d, r[f], 13, Q_PLUM_DARKEST)
        hband(d, r[f], 0, Q_PLUM_DARKEST)        # dark upper rim
        px(d, r[f], 4, 3, Q_GOLD)                # a lit stud on each flank
        px(d, r[f], 9, 3, Q_GOLD)
    for gx in (3, 10):
        vband(d, r["top"], gx, Q_PLUM_DARKEST)   # two ridges running the length
    px(d, r["top"], 6, 6, Q_GOLD_BRIGHT)
    px(d, r["top"], 7, 7, Q_GOLD_BRIGHT)

    # ---- gaster: petiole waist, then the pale egg-swollen mass --------------
    petiole, gaster = parts["gaster"]["cubes"]
    r = rects(petiole)
    for f in ("west", "north", "east", "south", "top"):
        fill(d, r[f], Q_PLUM_DARK)
    fill(d, r["bottom"], Q_PLUM_DARKEST)
    for f in ("west", "east"):
        hband(d, r[f], 0, Q_GOLD_DEEP)           # lit upper edge of the waist

    r = rects(gaster)
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "gaster:" + f, Q_SAC_PAL, namespace=NS, cell=3, jitter=0.18)
    noise_rect(d, r["top"], "gaster:top", Q_SAC_TOP_PAL, namespace=NS, cell=3, jitter=0.18)
    noise_rect(d, r["bottom"], "gaster:bottom", Q_SAC_BELLY_PAL, namespace=NS, cell=3, jitter=0.18)
    # Segment banding. On the side faces rect-x runs front -> back; the bands are
    # plum, so the abdomen reads as pale skin stretched between dark chitin rings.
    for f in ("west", "east"):
        vband(d, r[f], 0, Q_PLUM_DARK)           # seam against the petiole
        for bx in (4, 9, 14):
            vband(d, r[f], bx, Q_PLUM_BASE)
        hband(d, r[f], 0, Q_SAC_DARK)
        px(d, r[f], 2, 4, Q_SAC_PALE)            # sheen high on the flank
        px(d, r[f], 6, 3, Q_SAC_PALE)
    for by in (0, 5, 10, 15):                    # world-top rect-y runs back -> front
        hband(d, r["top"], by, Q_PLUM_BASE)
    px(d, r["top"], 8, 7, Q_SAC_PALE)
    px(d, r["top"], 9, 8, Q_SAC_PALE)
    hband(d, r["north"], 0, Q_PLUM_DARK)         # shaded where it meets the waist
    fill(d, (r["south"][0], r["south"][1], r["south"][2], r["south"][1] + 1), Q_PLUM_DARK)
    px(d, r["south"], 8, 10, Q_GOLD_DEEP)        # ovipositor tip
    px(d, r["south"], 9, 10, Q_GOLD_DEEP)

    # ---- head: near-black plates, gold brow, big amber eyes ----------------
    skull, crest = parts["head"]["cubes"][0], parts["head"]["cubes"][1]
    r = rects(skull)
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "skull:" + f, Q_SHELL_BACK_PAL, namespace=NS, cell=3)
    noise_rect(d, r["top"], "skull:top", Q_SHELL_BACK_PAL, namespace=NS, cell=3)
    noise_rect(d, r["bottom"], "skull:bottom", Q_LIMB_PAL, namespace=NS, cell=3)
    n = r["north"]                               # 12 wide x 9 tall front face
    hband(d, n, 0, Q_PLUM_DARKEST)
    hband(d, n, 1, Q_GOLD_DEEP)                  # gold brow band
    for ex in (1, 2, 9, 10):                     # two 2px amber eyes
        px(d, n, ex, 3, Q_EYE)
        px(d, n, ex, 4, Q_EYE_DARK)
    px(d, n, 1, 3, Q_GOLD_BRIGHT)
    px(d, n, 10, 3, Q_GOLD_BRIGHT)
    d.rectangle([n[0] + 3, n[1] + 7, n[0] + 8, n[1] + 7], fill=Q_PLUM_DARKEST)  # mouth line
    for f in ("west", "east"):
        px(d, r[f], 0, 3, Q_EYE)                 # the eyes wrap onto the sides
        px(d, r[f], 0, 4, Q_EYE_DARK)
        hband(d, r[f], 1, Q_GOLD_DEEP)
    hband(d, r["top"], 10, Q_PLUM_DARKEST)       # shadow where the crest sits

    # ---- crest: gold-veined chitin plating, not a slab of gold ---------------
    # cell=1: the crest's tallest face is 2 texels, so the 3-texel clumping the
    # rest of her uses would paint each face a single flat tone -- exactly the
    # thing this replaces.
    r = rects(crest)
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "crest:" + f, Q_CREST_PAL, namespace=NS, cell=1, jitter=0.35)
    noise_rect(d, r["top"], "crest:top", Q_CREST_TOP_PAL, namespace=NS, cell=1, jitter=0.35)
    fill(d, r["bottom"], Q_PLUM_DARKEST)         # underside, never seen lit
    for f in ("west", "east"):
        hband(d, r[f], 0, Q_GOLD_BRIGHT)         # lit ridge along the top
    for gx in (1, 4):                            # two plate seams down the crown
        vband(d, r["top"], gx, Q_PLUM_DARKEST)
    px(d, r["north"], 2, 0, Q_GOLD_BRIGHT)
    px(d, r["north"], 3, 0, Q_GOLD_BRIGHT)
    hband(d, r["north"], 1, Q_PLUM_DARKEST)      # shadow where it meets the skull

    # ---- mandibles: plum base with a gold joint accent, then a slimmer gold ------
    # ---- tip that carries the biting point (play-test round 1, spec item 2) ------
    r = rects(parts["mandible_r_base"]["cubes"][0])
    for f in ("west", "north", "east", "south", "top"):
        fill(d, r[f], Q_PLUM_BASE)
    fill(d, r["bottom"], Q_PLUM_DARKEST)
    for f in ("west", "east"):
        hband(d, r[f], 0, Q_GOLD_DEEP)            # joint accent, same motif as the legs
    px(d, r["top"], 1, 1, Q_GOLD_BRIGHT)

    r = rects(parts["mandible_r_tip"]["cubes"][0])
    for f in ("west", "north", "east", "south", "top"):
        fill(d, r[f], Q_GOLD)
    fill(d, r["bottom"], Q_PLUM_DARKEST)
    fill(d, r["north"], Q_GOLD_BRIGHT)            # the -Z face is the true biting point
    for f in ("west", "east"):
        vband(d, r[f], 0, Q_GOLD_BRIGHT)          # brightest right at the point
        vband(d, r[f], 3, Q_PLUM_DARK)            # dims back toward the base joint

    # ---- antennae: three segments, darkest at the skull and gold at the point --
    # Each rect's y=0 is its min-Y end, i.e. the end AWAY from the head, so the
    # gradient across the chain is written the same way in each: light at row 0,
    # dark at the last row. The scape is plum shading to a gold joint collar, the
    # pedicel is the transition, and the flagellum is gold outright -- so the eye
    # is pulled along the sweep to the thing pointing at the player.
    r = rects(parts["antenna_r_base"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "antenna_base:" + f, Q_LIMB_PAL, cell=1, namespace=NS)
        hband(d, r[f], 0, Q_GOLD_DEEP)           # collar the pedicel hinges on
        hband(d, r[f], QUEEN_ANTENNA_BASE_LEN - 1, Q_PLUM_DARKEST)   # buried in the skull
    fill(d, r["top"], Q_GOLD_DEEP)
    fill(d, r["bottom"], Q_PLUM_DARKEST)

    r = rects(parts["antenna_r_mid"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "antenna_mid:" + f, Q_LIMB_PAL, cell=1, namespace=NS)
        hband(d, r[f], 0, Q_GOLD)
        hband(d, r[f], QUEEN_ANTENNA_MID_LEN - 1, Q_PLUM_DARKEST)    # elbow joint
    fill(d, r["top"], Q_GOLD)
    fill(d, r["bottom"], Q_PLUM_DARKEST)

    r = rects(parts["antenna_r_tip"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        fill(d, r[f], Q_GOLD)
        hband(d, r[f], 0, Q_GOLD_BRIGHT)         # the point itself
        hband(d, r[f], QUEEN_ANTENNA_TIP_LEN - 1, Q_GOLD_DEEP)
    fill(d, r["top"], Q_GOLD_BRIGHT)
    fill(d, r["bottom"], Q_GOLD_DEEP)

    # ---- legs: dark chitin, two gold joint bands, near-black foot ---------
    r = rects(parts["leg_r1"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "leg:" + f, Q_LIMB_PAL, cell=1, namespace=NS)
        hband(d, r[f], 5, Q_GOLD_DEEP)           # femur/tibia joint
        hband(d, r[f], 11, Q_GOLD_DEEP)          # tibia/tarsus joint
        hband(d, r[f], QUEEN_LEG_LENGTH - 1, Q_PLUM_DARKEST)         # foot
    fill(d, r["top"], Q_PLUM_DARK)
    fill(d, r["bottom"], Q_PLUM_DARKEST)

    return img


def paint_acid_spit():
    img = Image.new("RGBA", (ACID_TEX_W, ACID_TEX_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    parts = {p["name"]: p for p in ACID_SPIT["parts"]}
    NS = "acid_spit"

    def rects(cube):
        u, v = cube["off"]
        _, _, _, w, h, dd = cube["box"]
        return face_rects(u, v, w, h, dd)

    # ---- core: a lit blob -- pale on top, dark underneath ------------------
    # cell=1 on a 4-texel face: anything coarser paints the whole thing one tone.
    r = rects(parts["core"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "core:" + f, A_ACID_PAL, namespace=NS, cell=1, jitter=0.4)
        hband(d, r[f], 0, A_ACID_LIGHT)          # lit crown
        hband(d, r[f], 3, A_ACID_DARK)           # shaded underside
    noise_rect(d, r["top"], "core:top", A_ACID_PAL, namespace=NS, cell=1, jitter=0.4)
    noise_rect(d, r["bottom"], "core:bottom", A_ACID_RIM_PAL, namespace=NS, cell=1, jitter=0.4)
    px(d, r["top"], 1, 1, A_ACID_PALE)           # one specular texel, top and front
    px(d, r["north"], 1, 1, A_ACID_PALE)

    # ---- bud: one rect shared by all four, so it reads the same from any side --
    r = rects(parts["bud_west"]["cubes"][0])
    for f in ("top", "west", "north", "east", "south"):
        fill(d, r[f], A_ACID_MID)
    fill(d, r["bottom"], A_ACID_DARK)
    for f in ("west", "north", "east", "south"):
        hband(d, r[f], 0, A_ACID_LIGHT)
    px(d, r["top"], 0, 0, A_ACID_PALE)

    return img


# ----------------------------------------------------------------- preview --

def rotate_zyx(p, rot):
    """Rx then Ry then Rz -- matches Quaternionf.rotationZYX(zRot, yRot, xRot)
    used by ModelPart.translateAndRotate."""
    x, y, z = p
    rx, ry, rz = rot
    # Rx
    cy_, sy_ = math.cos(rx), math.sin(rx)
    y, z = y * cy_ - z * sy_, y * sy_ + z * cy_
    # Ry
    c, s = math.cos(ry), math.sin(ry)
    x, z = x * c + z * s, -x * s + z * c
    # Rz
    c, s = math.cos(rz), math.sin(rz)
    x, y = x * c - y * s, x * s + y * c
    return (x, y, z)


def cube_corners(part, cube):
    """The 8 world-space corners plus the per-face (origin, +u, +v) triples.

    Walks the optional `parent` chain outward, applying each node's own
    rotation before its offset -- the same order ModelPart.translateAndRotate
    uses, where a child's offset is expressed in its PARENT's frame. A part
    with no `parent` (every part but the queen's antenna segments) takes
    exactly one pass, which is the old absolute-pose behaviour unchanged."""
    bx, by, bz, w, h, d = cube["box"]
    x0, y0, z0 = bx, by, bz
    x1, y1, z1 = bx + w, by + h, bz + d

    def wp(x, y, z):
        p = (x, y, z)
        node = part
        while node is not None:
            rx, ry, rz = rotate_zyx(p, node.get("rot", (0.0, 0.0, 0.0)))
            ox, oy, oz = node["pose"]
            p = (ox + rx, oy + ry, oz + rz)
            node = node.get("parent")
        return p

    # (src-origin, src +x edge end, src +y edge end) per face, in world space.
    return {
        "top":    (wp(x1, y0, z1), wp(x0, y0, z1), wp(x1, y0, z0)),
        "bottom": (wp(x1, y1, z1), wp(x0, y1, z1), wp(x1, y1, z0)),
        "west":   (wp(x0, y0, z0), wp(x0, y0, z1), wp(x0, y1, z0)),
        "north":  (wp(x1, y0, z0), wp(x0, y0, z0), wp(x1, y1, z0)),
        "east":   (wp(x1, y0, z1), wp(x1, y0, z0), wp(x1, y1, z1)),
        "south":  (wp(x0, y0, z1), wp(x1, y0, z1), wp(x0, y1, z1)),
    }


def face_centre(part, cube, face):
    """World-space centre of a face, used for painter's-algorithm sorting."""
    p0, pu, pv = cube_corners(part, cube)[face]
    return tuple((pu[i] + pv[i]) / 2.0 for i in range(3))


# The orthographic window each model is rendered into: `units` world units across,
# `scale` screen pixels per unit, with (x0, y0, z0) the world coord at the screen
# origin. A model that pokes outside the window is silently CLIPPED, not scaled to
# fit, so a new model whose reach differs much from the worker's needs its own box --
# the queen is 60 units nose to abdomen tip against the worker's 16.
DEFAULT_VIEW = {"scale": 12, "units": 24, "x0": -12.0, "y0": 8.0, "z0": -12.0}
QUEEN_VIEW = {"scale": 7, "units": 60, "x0": -30.0, "y0": -6.0, "z0": -30.0}
QUEEN_ANT["view"] = QUEEN_VIEW


def make_views(box):
    """{name: (project(worldpoint) -> (screen_x, screen_y), depth axis index)}."""
    scale, x0, y0, z0 = box["scale"], box["x0"], box["y0"], box["z0"]
    return {
        "front": (lambda p: ((p[0] - x0) * scale, (p[1] - y0) * scale), 2),
        "side":  (lambda p: ((p[2] - z0) * scale, (p[1] - y0) * scale), 0),
        "top":   (lambda p: ((p[0] - x0) * scale, (p[2] - z0) * scale), 1),
    }


def paste_face(canvas, tex, rect, p0, pu, pv, view_px):
    """Paste the atlas rect onto the canvas as the parallelogram p0/pu/pv
    (orthographic projection of a rotated quad is always affine)."""
    x0, y0, x1, y1 = rect
    sw, sh = x1 - x0, y1 - y0
    if sw <= 0 or sh <= 0:
        return
    ax, ay = (pu[0] - p0[0]) / sw, (pu[1] - p0[1]) / sw
    bx, by = (pv[0] - p0[0]) / sh, (pv[1] - p0[1]) / sh
    det = ax * by - bx * ay
    if abs(det) < 1e-6:      # face seen exactly edge-on
        return
    ia, ib = by / det, -bx / det
    ic, id_ = -ay / det, ax / det
    coeffs = (ia, ib, -(ia * p0[0] + ib * p0[1]),
              ic, id_, -(ic * p0[0] + id_ * p0[1]))
    face = tex.crop(rect)
    warped = face.transform((view_px, view_px), Image.AFFINE, coeffs,
                            resample=Image.NEAREST)
    canvas.alpha_composite(warped)


def render_view(model, tex, view):
    box = model.get("view", DEFAULT_VIEW)
    view_px = box["scale"] * box["units"]
    project, depth_axis = make_views(box)[view]
    canvas = Image.new("RGBA", (view_px, view_px), (0, 0, 0, 0))
    faces = []
    for part in model["parts"]:
        for cube in part["cubes"]:
            u, v = cube["off"]
            _, _, _, w, h, dd = cube["box"]
            fr = face_rects(u, v, w, h, dd)
            corners = cube_corners(part, cube)
            for fname, tri in corners.items():
                depth = face_centre(part, cube, fname)[depth_axis]
                faces.append((depth, fr[fname], tri))
    # painter's algorithm: the camera sits on the negative side of each axis,
    # so larger coordinate = further away = drawn first.
    faces.sort(key=lambda f: -f[0])
    for _, rect, (p0, pu, pv) in faces:
        paste_face(canvas, tex, rect, project(p0), project(pu), project(pv), view_px)
    return canvas


def crop_to_content(img, pad=8):
    bbox = img.getbbox()
    if bbox is None:
        return img
    x0, y0, x1, y1 = bbox
    x0, y0 = max(0, x0 - pad), max(0, y0 - pad)
    x1, y1 = min(img.width, x1 + pad), min(img.height, y1 + pad)
    return img.crop((x0, y0, x1, y1))


def contact_sheet(views, tex, atlas_scale=4):
    """Labelled QA sheet: the three orthographic views plus the raw atlas."""
    panels = [(name, crop_to_content(img)) for name, img in views]
    atlas = tex.resize((tex.width * atlas_scale, tex.height * atlas_scale), Image.NEAREST)
    panels.append((f"atlas {tex.width}x{tex.height} ({atlas_scale}x)", atlas))

    label_h = 16
    pad = 10
    ph = max(p.height for _, p in panels)
    total_w = sum(p.width for _, p in panels) + pad * (len(panels) + 1)
    total_h = ph + label_h + pad * 2
    sheet = Image.new("RGBA", (total_w, total_h), (30, 26, 24, 255))
    draw = ImageDraw.Draw(sheet)
    x = pad
    for name, panel in panels:
        # checkerboard backing so alpha reads correctly
        for cy in range(0, ph, 16):
            for cx in range(0, panel.width, 16):
                shade = 64 if ((cx // 16 + cy // 16) % 2 == 0) else 50
                draw.rectangle([x + cx, pad + cy,
                                min(x + cx + 15, x + panel.width - 1),
                                min(pad + cy + 15, pad + ph - 1)],
                               fill=(shade, shade, shade, 255))
        sheet.alpha_composite(panel, (x, pad + (ph - panel.height) // 2))
        draw.text((x + 2, pad + ph + 2), name, fill=(226, 214, 200, 255))
        x += panel.width + pad
    return sheet


# Play-test round 1, spec item 3: tamed worker/soldier get their own atlas -- same
# geometry (a shallow copy of the wild spec with just `name` overridden, so the
# preview/atlas-writing code below keys off the right filename), repainted with
# TAMED_ANTENNA_TIP instead of each caste's wild tip colour.
TAMED_WORKER_ANT = dict(WORKER_ANT, name="tamed_worker_ant")
TAMED_SOLDIER_ANT = dict(SOLDIER_ANT, name="tamed_soldier_ant")


def paint_tamed_worker_ant():
    return paint_worker_ant(antenna_tip_color=TAMED_ANTENNA_TIP)


def paint_tamed_soldier_ant():
    return paint_soldier_ant(antenna_tip_color=TAMED_ANTENNA_TIP)


MODELS = [
    (WORKER_ANT, paint_worker_ant),
    (TAMED_WORKER_ANT, paint_tamed_worker_ant),
    (SOLDIER_ANT, paint_soldier_ant),
    (TAMED_SOLDIER_ANT, paint_tamed_soldier_ant),
    (LARVA, paint_larva),
    (QUEEN_ANT, paint_queen_ant),
    (ENDER_ANT, paint_ender_ant),
    (ACID_SPIT, paint_acid_spit),
]


def main():
    ENTITY_TEX_DIR.mkdir(parents=True, exist_ok=True)
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)

    for spec, paint in MODELS:
        tex = paint()
        out = ENTITY_TEX_DIR / f"{spec['name']}.png"
        tex.save(out)
        print(f"wrote {out.relative_to(REPO_ROOT)}")

        views = []
        for view in ("front", "side", "top"):
            img = render_view(spec, tex, view)
            views.append((view, img))
            p = PREVIEW_DIR / f"{spec['name']}_{view}.png"
            crop_to_content(img).save(p)
            print(f"wrote {p.relative_to(REPO_ROOT)}")

        sheet = contact_sheet(views, tex)
        p = PREVIEW_DIR / f"{spec['name']}_sheet.png"
        sheet.save(p)
        print(f"wrote {p.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    main()
