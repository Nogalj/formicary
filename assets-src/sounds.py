"""Builds the mod's shipped mob voices from Logan's raw recordings.

Same contract as blocks.py: the files under `audio-src/` are the source of
truth, everything under
`src/main/resources/assets/formicary/sounds/` is GENERATED, and re-running
this script reproduces it byte-for-byte from the same inputs. Never hand-edit
a file in the resources tree -- tune the table below and re-run.

    uv run --with soundfile --with numpy python assets-src/sounds.py

Why a script and not an audio editor: play-test rounds keep asking for the
same kind of change ("shorter", "higher", "more variation"), and each of those
is one number here rather than a re-export somebody has to remember the
settings for.

What it makes, per mob, from a single ambient recording:

  ambient1/2/3.ogg  three shortened cuts taken from DIFFERENT windows of the
                    recording, so the variation is real timbre rather than the
                    same clip repitched three times. Minecraft picks between
                    them at random (see ModSoundDefinitionsProvider), and then
                    applies its own random pitch on top.
  hurt1/2.ogg       short, pitched up, sharp attack and fast decay.
  death.ogg         pitched up at the onset and gliding DOWN across the clip,
                    which is what makes it read as dying rather than as a
                    longer yelp.

Everything is mono on purpose -- Minecraft only pans a sound in 3D if it has
one channel, and a stereo file plays flat inside the player's head.
"""

import math
import pathlib

import numpy as np
import soundfile as sf

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC_DIR = REPO_ROOT / "assets-src" / "audio-src"
OUT_DIR = REPO_ROOT / "src" / "main" / "resources" / "assets" / "formicary" / "sounds" / "entity"

# Vorbis is what Minecraft's sound engine reads. 24 kHz is the rate Logan
# recorded at; resampling up would only invent detail that is not there.
SAMPLE_RATE = 24000

# Peak levels the finished files are normalised to. Ambients sit lower because
# several ants are audible at once and they stack; a hurt or death is one
# event and wants to cut through. Well under 1.0 either way -- Minecraft mixes
# several sounds together and clipping happens in the mixer, not in the file.
AMBIENT_PEAK = 0.62
HURT_PEAK = 0.74
DEATH_PEAK = 0.76

# Rumble below this is recording-room noise, not ant. Removing it stops the
# quiet ambients sounding muddy when a few overlap.
HIGHPASS_HZ = 90.0


# --------------------------------------------------------------- per mob --
#
# `windows` are (start, end) seconds into the source recording -- three
# deliberately different stretches, overlapping only a little. `rates` are the
# playback-rate multipliers applied to each of those cuts: >1 is faster and
# higher, <1 slower and lower. Kept within +-6% because Minecraft already
# randomises ambient pitch by +-20% at playback, and stacking a big baked
# offset on top of that just makes one variant sound like a different animal.
#
# `hurt_at` / `death_at` are (start, end) seconds of the stretch each is cut
# from, chosen off the loudest part of the recording.
VOICES = {
    "worker_ant": {
        "windows": [(0.15, 1.25), (0.90, 2.00), (1.62, 2.72)],
        "rates": [1.00, 1.05, 0.94],
        "hurt_at": (0.55, 1.05),
        "death_at": (0.35, 1.45),
    },
    "soldier_ant": {
        # The soldier's recording peaks in its middle burst (0.85-2.52s), so
        # both the hurt and the death are cut from there.
        "windows": [(0.15, 1.30), (0.88, 2.03), (1.58, 2.73)],
        "rates": [1.00, 1.04, 0.95],
        "hurt_at": (1.00, 1.50),
        "death_at": (0.90, 2.05),
    },
    "queen": {
        # Longer cuts and no upward rate variant: she is the boss, and this
        # same event is also played at pitch 0.5 for her phase-change burst,
        # so anything baked in gets heard twice.
        "windows": [(0.02, 1.42), (0.52, 1.92), (0.88, 2.28)],
        "rates": [1.00, 1.03, 0.96],
        "hurt_at": (0.30, 0.85),
        "death_at": (0.10, 1.35),
    },
}

# How far up a hurt is pitched, and the glide a death makes across its length.
# A death starting ABOVE the hurt and ending below it is the whole effect:
# the pitch falling away is what a listener reads as the animal going down.
HURT_RATE = 1.38
HURT_RATE_VAR2 = 1.28          # second variant, a touch lower so the pair differ
DEATH_RATE_START = 1.30
DEATH_RATE_END = 0.82

HURT_ATTACK_MS = 4.0
HURT_DECAY_TAU = 0.10          # seconds; exponential, so ~5*tau to silence
DEATH_ATTACK_MS = 7.0
DEATH_DECAY_TAU = 0.30

FADE_IN_MS = 12.0              # ambient edges, just enough to kill the click
FADE_OUT_MS = 90.0


def load_mono(path):
    data, rate = sf.read(str(path), always_2d=True)
    mono = data[:, 0].astype(np.float64)
    if rate != SAMPLE_RATE:
        # Not expected for Logan's recordings, but a re-record at another rate
        # should not silently change every pitch in the mod.
        n = int(round(len(mono) * SAMPLE_RATE / rate))
        mono = np.interp(np.linspace(0.0, len(mono) - 1.0, n),
                         np.arange(len(mono)), mono)
    return mono


def cut(x, span):
    a = max(0, int(round(span[0] * SAMPLE_RATE)))
    b = min(len(x), int(round(span[1] * SAMPLE_RATE)))
    if b - a < 2:
        raise ValueError("window %r is empty against a %.3fs source"
                         % (span, len(x) / SAMPLE_RATE))
    return x[a:b].copy()


def highpass(x, cutoff_hz):
    """One-pole high pass, applied forward then backward so it adds no phase
    smear -- the sounds are short and transient-led, and a lopsided filter
    audibly softens the attack."""
    if len(x) < 4:
        return x
    dt = 1.0 / SAMPLE_RATE
    rc = 1.0 / (2.0 * math.pi * cutoff_hz)
    a = rc / (rc + dt)

    def once(sig):
        out = np.empty_like(sig)
        prev_in = sig[0]
        prev_out = 0.0
        for i, v in enumerate(sig):
            prev_out = a * (prev_out + v - prev_in)
            prev_in = v
            out[i] = prev_out
        return out

    return once(once(x)[::-1])[::-1]


def lowpass_fft(x, cutoff_hz):
    """Brick wall with a raised-cosine skirt, used as an anti-alias guard
    BEFORE speeding a clip up. Skipping this is what makes a naively
    pitched-up game sound fizz: content above the new Nyquist folds back down
    the spectrum as inharmonic grit."""
    if len(x) < 8 or cutoff_hz >= SAMPLE_RATE / 2.0:
        return x
    spectrum = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(len(x), 1.0 / SAMPLE_RATE)
    skirt = max(cutoff_hz * 0.12, 200.0)
    gain = np.clip((cutoff_hz + skirt - freqs) / (2.0 * skirt), 0.0, 1.0)
    gain = 0.5 - 0.5 * np.cos(math.pi * gain)      # raised cosine, not a cliff
    return np.fft.irfft(spectrum * gain, n=len(x))


def resample(x, rate_start, rate_end=None):
    """Plays `x` back at a rate that ramps from `rate_start` to `rate_end`
    (constant if `rate_end` is None). Rate and pitch move together, exactly as
    they would on tape -- which is why a pitched-up hurt also comes out
    shorter, and why the death's downward glide also slows it down.

    Integrated a sample at a time rather than by scaling a cumulative sum: the
    read position advances by the rate AT that position, so the ramp is
    honoured along the clip instead of just at its ends."""
    if rate_end is None:
        rate_end = rate_start
    fastest = max(rate_start, rate_end)
    if fastest > 1.0:
        x = lowpass_fft(x, 0.45 * SAMPLE_RATE / fastest)

    last = len(x) - 1
    positions = []
    pos = 0.0
    while pos < last:
        positions.append(pos)
        progress = pos / last if last > 0 else 1.0
        pos += rate_start + (rate_end - rate_start) * progress
    if not positions:
        return x
    return np.interp(np.array(positions), np.arange(len(x)), x)


def fade(x, fade_in_ms, fade_out_ms):
    """Raised-cosine edges. A hard cut at a non-zero sample is an instant step,
    and a step is a click -- audible on every single play."""
    y = x.copy()
    n_in = min(int(SAMPLE_RATE * fade_in_ms / 1000.0), len(y) // 2)
    n_out = min(int(SAMPLE_RATE * fade_out_ms / 1000.0), len(y) // 2)
    if n_in > 1:
        ramp = 0.5 - 0.5 * np.cos(np.linspace(0.0, math.pi, n_in))
        y[:n_in] *= ramp
    if n_out > 1:
        ramp = 0.5 + 0.5 * np.cos(np.linspace(0.0, math.pi, n_out))
        y[-n_out:] *= ramp
    return y


def strike_envelope(x, attack_ms, decay_tau):
    """Fast attack, exponential decay -- the shape of something being hit,
    rather than the flat sustain an ambient recording has. Applied AFTER the
    resample so the decay is in real output seconds."""
    y = x.copy()
    n_attack = min(int(SAMPLE_RATE * attack_ms / 1000.0), len(y) // 2)
    if n_attack > 1:
        y[:n_attack] *= np.linspace(0.0, 1.0, n_attack)
    t = np.arange(len(y)) / SAMPLE_RATE
    y *= np.exp(-t / decay_tau)
    # The exponential never truly reaches zero; land it there so the file ends
    # on silence instead of a step.
    return fade(y, 0.0, 25.0)


def normalise(x, peak):
    top = float(np.max(np.abs(x)))
    if top < 1e-9:
        raise ValueError("silent clip")
    return x * (peak / top)


def write(path, x):
    path.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(path), x.astype(np.float32), SAMPLE_RATE,
             format="OGG", subtype="VORBIS")
    return path


def describe(x):
    peak = float(np.max(np.abs(x)))
    rms = float(np.sqrt(np.mean(x ** 2)))
    window = x * np.hanning(len(x))
    mag = np.abs(np.fft.rfft(window))
    freqs = np.fft.rfftfreq(len(x), 1.0 / SAMPLE_RATE)
    centroid = float((mag * freqs).sum() / max(mag.sum(), 1e-9))
    return "%.3fs  peak %.2f  rms %.3f  centroid %4.0f Hz" % (
        len(x) / SAMPLE_RATE, peak, rms, centroid)


def build_mob(name, spec):
    source = load_mono(SRC_DIR / ("%s-ambient.ogg" % name))
    clean = highpass(source, HIGHPASS_HZ)
    out = OUT_DIR / name
    print("\n%s  (source %.3fs)" % (name, len(source) / SAMPLE_RATE))

    made = []
    for i, (window, rate) in enumerate(zip(spec["windows"], spec["rates"]), start=1):
        clip = resample(cut(clean, window), rate)
        clip = normalise(fade(clip, FADE_IN_MS, FADE_OUT_MS), AMBIENT_PEAK)
        made.append(write(out / ("ambient%d.ogg" % i), clip))
        print("  ambient%d  %s   (window %.2f-%.2fs, rate %.2f)"
              % (i, describe(clip), window[0], window[1], rate))

    for i, rate in enumerate((HURT_RATE, HURT_RATE_VAR2), start=1):
        clip = resample(cut(clean, spec["hurt_at"]), rate)
        clip = strike_envelope(clip, HURT_ATTACK_MS, HURT_DECAY_TAU)
        clip = normalise(clip, HURT_PEAK)
        made.append(write(out / ("hurt%d.ogg" % i), clip))
        print("  hurt%d     %s   (rate %.2f)" % (i, describe(clip), rate))

    clip = resample(cut(clean, spec["death_at"]), DEATH_RATE_START, DEATH_RATE_END)
    clip = strike_envelope(clip, DEATH_ATTACK_MS, DEATH_DECAY_TAU)
    clip = normalise(clip, DEATH_PEAK)
    made.append(write(out / "death.ogg", clip))
    print("  death     %s   (glide %.2f -> %.2f)"
          % (describe(clip), DEATH_RATE_START, DEATH_RATE_END))
    return made


def assert_shippable(paths):
    """Guard, in the spirit of blocks.py's transparent-border check: every
    file the game will load has to be mono, at the expected rate, non-silent
    and not clipped. A bad export here is silent in the build and only shows
    up as a sound that will not pan."""
    for path in paths:
        data, rate = sf.read(str(path), always_2d=True)
        if data.shape[1] != 1:
            raise AssertionError("%s is not mono (%d channels) -- Minecraft "
                                 "will not position it in 3D" % (path.name, data.shape[1]))
        if rate != SAMPLE_RATE:
            raise AssertionError("%s is %d Hz, expected %d" % (path.name, rate, SAMPLE_RATE))
        peak = float(np.max(np.abs(data)))
        if peak > 0.999:
            raise AssertionError("%s clips (peak %.3f)" % (path.name, peak))
        if peak < 0.05:
            raise AssertionError("%s is effectively silent (peak %.3f)" % (path.name, peak))
        if len(data) < SAMPLE_RATE * 0.05:
            raise AssertionError("%s is shorter than 50ms" % path.name)


def main():
    written = []
    for name, spec in VOICES.items():
        written.extend(build_mob(name, spec))
    assert_shippable(written)
    total = sum(p.stat().st_size for p in written)
    print("\n%d files, %.1f KB total -- all mono %d Hz, none clipped"
          % (len(written), total / 1024.0, SAMPLE_RATE))


if __name__ == "__main__":
    main()
