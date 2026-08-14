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
#   crest    (94,20)  22x7      mandible(94,27)  26x12
#   leg      (56,32)   8x15     antenna (64,32)   8x9
#
# Ground is y=24; her back sits at y=0, i.e. 24px = 1.5 blocks tall, under the
# 1.8-block hitbox. Feet land at 12 + 13*cos(0.6109) = 22.7, so the splay and
# the leg length are tied together -- change one and the feet leave the floor.
QUEEN_REST_LEG_Z = 0.6109       # 35 degrees off vertical
QUEEN_REST_LEG_Y_FRONT = -0.4363
QUEEN_REST_LEG_Y_HIND = 0.4363

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
        {"name": "mandible_r", "pose": (-3, 12, -18), "cubes": [
            {"off": (94, 27), "box": (-5, -2, -8, 5, 4, 8)},
        ]},
        {"name": "mandible_l", "pose": (3, 12, -18), "cubes": [
            {"off": (94, 27), "box": (0, -2, -8, 5, 4, 8)},
        ]},
        {"name": "antenna_r", "pose": (-3, 4, -16),
         "rot": (REST_ANT_X, 0, -REST_ANT_Z), "cubes": [
            {"off": (64, 32), "box": (-1, -7, -1, 2, 7, 2)},
        ]},
        {"name": "antenna_l", "pose": (3, 4, -16),
         "rot": (REST_ANT_X, 0, REST_ANT_Z), "cubes": [
            {"off": (64, 32), "box": (-1, -7, -1, 2, 7, 2)},
        ]},
        # --- gaster: the mass. Petiole waist, then the egg-swollen abdomen ------
        {"name": "gaster", "pose": (0, 4, 0), "cubes": [
            {"off": (72, 20), "box": (-3, 3, 5, 6, 6, 5)},              # petiole
            {"off": (0, 0), "box": (-9, -4, 9, 18, 14, 18)},            # gaster
        ]},
        # --- six long legs ------------------------------------------------------
        {"name": "leg_r1", "pose": (-7, 12, -5),
         "rot": (0, QUEEN_REST_LEG_Y_FRONT, QUEEN_REST_LEG_Z),
         "cubes": [{"off": (56, 32), "box": (-1, 0, -1, 2, 13, 2)}]},
        {"name": "leg_r2", "pose": (-7, 12, 0),
         "rot": (0, 0, QUEEN_REST_LEG_Z),
         "cubes": [{"off": (56, 32), "box": (-1, 0, -1, 2, 13, 2)}]},
        {"name": "leg_r3", "pose": (-7, 12, 5),
         "rot": (0, QUEEN_REST_LEG_Y_HIND, QUEEN_REST_LEG_Z),
         "cubes": [{"off": (56, 32), "box": (-1, 0, -1, 2, 13, 2)}]},
        {"name": "leg_l1", "pose": (7, 12, -5),
         "rot": (0, -QUEEN_REST_LEG_Y_FRONT, -QUEEN_REST_LEG_Z),
         "cubes": [{"off": (56, 32), "box": (-1, 0, -1, 2, 13, 2)}]},
        {"name": "leg_l2", "pose": (7, 12, 0),
         "rot": (0, 0, -QUEEN_REST_LEG_Z),
         "cubes": [{"off": (56, 32), "box": (-1, 0, -1, 2, 13, 2)}]},
        {"name": "leg_l3", "pose": (7, 12, 5),
         "rot": (0, -QUEEN_REST_LEG_Y_HIND, -QUEEN_REST_LEG_Z),
         "cubes": [{"off": (56, 32), "box": (-1, 0, -1, 2, 13, 2)}]},
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


# ------------------------------------------------------------- larva palette --
# Pale cream/ivory grub -- faint amber segment lines, tiny dark eye dots.

L_PALE = (252, 246, 232, 255)
L_LIGHT = (248, 238, 218, 255)
L_BASE = (238, 224, 196, 255)
L_SHADE = (222, 204, 174, 255)
L_DARK = (198, 178, 146, 255)

L_LINE = (222, 158, 70, 255)
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


def paint_worker_ant():
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
    r = rects(parts["antenna_r"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "antenna:" + f, LIMB_PAL, cell=1)
        hband(d, r[f], 0, ANTENNA_TIP)
        hband(d, r[f], 2, CHITIN_DARKEST)       # base joint
    fill(d, r["top"], ANTENNA_TIP)
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


def paint_soldier_ant():
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
    r = rects(parts["antenna_r"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "antenna:" + f, S_LIMB_PAL, cell=1, namespace=NS)
        hband(d, r[f], 0, S_ANTENNA_TIP)
        hband(d, r[f], 2, S_CHITIN_DARKEST)     # base joint
    fill(d, r["top"], S_ANTENNA_TIP)
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

    # ---- mid: the widest segment, pale cream with a faint seam each end ----
    r = rects(parts["mid"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "mid:" + f, L_BODY_PAL, namespace=NS, jitter=0.14, cell=1)
    noise_rect(d, r["top"], "mid:top", L_TOP_PAL, namespace=NS, jitter=0.14, cell=1)
    noise_rect(d, r["bottom"], "mid:bottom", L_BOTTOM_PAL, namespace=NS, jitter=0.14, cell=1)
    for f in ("west", "east"):
        vband(d, r[f], 0, L_LINE)               # seam against the head (front)
        vband(d, r[f], 2, L_LINE)               # seam against the tail (back)

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
        vband(d, r[f], 1, L_LINE)               # seam against mid

    # ---- tail: shorter and thinner, tapering down --------------------------
    r = rects(parts["tail"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "tail:" + f, L_BODY_PAL, namespace=NS, jitter=0.14, cell=1)
    noise_rect(d, r["top"], "tail:top", L_TOP_PAL, namespace=NS, jitter=0.14, cell=1)
    noise_rect(d, r["bottom"], "tail:bottom", L_BOTTOM_PAL, namespace=NS, jitter=0.14, cell=1)
    for f in ("west", "east"):
        vband(d, r[f], 0, L_LINE)               # seam against mid

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

    # ---- crest: the one piece that is gold rather than trimmed with it ------
    r = rects(crest)
    for f in ("west", "north", "east", "south", "top"):
        fill(d, r[f], Q_GOLD_DEEP)
    fill(d, r["bottom"], Q_PLUM_DARKEST)
    fill(d, r["top"], Q_GOLD)
    for f in ("west", "east"):
        hband(d, r[f], 0, Q_GOLD_BRIGHT)         # lit ridge along the top
    px(d, r["north"], 2, 0, Q_GOLD_BRIGHT)
    px(d, r["north"], 3, 0, Q_GOLD_BRIGHT)

    # ---- mandibles: plum base, gold biting tip -----------------------------
    r = rects(parts["mandible_r"]["cubes"][0])
    for f in ("west", "north", "east", "south", "top"):
        fill(d, r[f], Q_PLUM_BASE)
    fill(d, r["bottom"], Q_PLUM_DARKEST)
    fill(d, r["north"], Q_GOLD)                  # the -Z face is the biting tip
    for f in ("west", "east"):
        vband(d, r[f], 0, Q_GOLD)
        vband(d, r[f], 1, Q_GOLD_DEEP)
        vband(d, r[f], 7, Q_PLUM_DARKEST)
    px(d, r["top"], 1, 1, Q_GOLD_BRIGHT)
    px(d, r["top"], 3, 2, Q_GOLD_BRIGHT)

    # ---- antennae: dark shaft, gold tip (min-Y end is the tip) -------------
    r = rects(parts["antenna_r"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "antenna:" + f, Q_LIMB_PAL, cell=1, namespace=NS)
        hband(d, r[f], 0, Q_GOLD)
        hband(d, r[f], 1, Q_GOLD_DEEP)
        hband(d, r[f], 6, Q_PLUM_DARKEST)        # base joint
    fill(d, r["top"], Q_GOLD_BRIGHT)
    fill(d, r["bottom"], Q_PLUM_DARKEST)

    # ---- legs: dark chitin, two gold joint bands, near-black foot ---------
    r = rects(parts["leg_r1"]["cubes"][0])
    for f in ("west", "north", "east", "south"):
        noise_rect(d, r[f], "leg:" + f, Q_LIMB_PAL, cell=1, namespace=NS)
        hband(d, r[f], 4, Q_GOLD_DEEP)           # femur/tibia joint
        hband(d, r[f], 9, Q_GOLD_DEEP)           # tibia/tarsus joint
        hband(d, r[f], 12, Q_PLUM_DARKEST)       # foot
    fill(d, r["top"], Q_PLUM_DARK)
    fill(d, r["bottom"], Q_PLUM_DARKEST)

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
    """The 8 world-space corners plus the per-face (origin, +u, +v) triples."""
    ox, oy, oz = part["pose"]
    rot = part.get("rot", (0.0, 0.0, 0.0))
    bx, by, bz, w, h, d = cube["box"]
    x0, y0, z0 = bx, by, bz
    x1, y1, z1 = bx + w, by + h, bz + d

    def wp(x, y, z):
        rx, ry, rz = rotate_zyx((x, y, z), rot)
        return (ox + rx, oy + ry, oz + rz)

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


MODELS = [
    (WORKER_ANT, paint_worker_ant),
    (SOLDIER_ANT, paint_soldier_ant),
    (LARVA, paint_larva),
    (QUEEN_ANT, paint_queen_ant),
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
