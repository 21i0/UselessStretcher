"""Generate original 2i dialogue syllables and little head-pat melodies.

Requires numpy and soundfile. No input recordings, reference assets, speech,
game samples, or extracted model-mod sounds are read by this script.
Only arithmetic oscillators and a fixed random seed are used. Regenerating
with the same NumPy/libsndfile versions reproduces the audio byte for byte.

Production assets: src/main/resources/assets/useless_stretcher/sounds/*.ogg
Listening previews: build/audio-preview/*.wav (not included in the mod JAR)

The Java dialogue voice controller determines actual character timestamps,
speaker, emotional contour, pauses, and volume. These are short *syllables*,
not a loop: the soft/bright recordings change timbre as well as pitch.
"""

from hashlib import sha256
from pathlib import Path

import numpy as np
import soundfile as sf


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/useless_stretcher/sounds"
PREVIEW = ROOT / "build/audio-preview"
RATE = 24_000
SEED = 2_026_101_002
OUT.mkdir(parents=True, exist_ok=True)
PREVIEW.mkdir(parents=True, exist_ok=True)


def envelope(size, attack=0.004, release=0.010):
    """Zero at both ends; cosine ramps remove clicks without a sharp transient."""
    result = np.ones(size)
    attack_size = min(size // 2, max(2, round(attack * RATE)))
    release_size = min(size - attack_size, max(2, round(release * RATE)))
    result[:attack_size] = np.sin(np.linspace(0, np.pi / 2, attack_size)) ** 2
    result[-release_size:] = np.cos(np.linspace(0, np.pi / 2, release_size)) ** 2
    return result


def syllable(frequency, duration, harmonics, bend, gain, seed_offset):
    """A warm, rounded 'pu' made from a fundamental and three quiet harmonics."""
    rng = np.random.default_rng(SEED + seed_offset)
    size = round(RATE * duration)
    progress = np.linspace(0, 1, size)
    # A small upward onset then relaxed tail; never a siren or a square-wave beep.
    contour = 1 + bend * (np.sin(np.pi * progress) - 0.35 * progress)
    phase = 2 * np.pi * np.cumsum(frequency * contour) / RATE
    wave = np.sin(phase)
    for harmonic, weight in enumerate(harmonics, start=2):
        wave += weight * np.sin(harmonic * phase + rng.uniform(-0.03, 0.03))
    wave *= (1 - 0.22 * progress) * envelope(size) * gain
    wave[0] = wave[-1] = 0
    return wave


def pat_melody(notes, duration, seed_offset):
    """A tiny two/three-note greeting, not the reference mod's toy squeak."""
    result = np.zeros(round(duration * RATE))
    rng = np.random.default_rng(SEED + seed_offset)
    for onset, frequency, length, gain in notes:
        size = round(length * RATE)
        progress = np.linspace(0, 1, size)
        # Rounded, lightly breathy mallet sound with a very small smiling glide.
        curve = frequency * (1 + 0.035 * np.sin(np.pi * progress))
        phase = 2 * np.pi * np.cumsum(curve) / RATE
        wave = np.sin(phase) + 0.13 * np.sin(2 * phase) + 0.025 * np.sin(3 * phase)
        # Low-passed procedural breath, not a recording. It remains almost inaudible.
        breath = np.convolve(rng.standard_normal(size), np.ones(21) / 21, mode="same")
        wave += 0.012 * breath
        wave *= gain * np.exp(-2.5 * progress) * envelope(size, 0.010, 0.030)
        start = round(onset * RATE)
        end = min(len(result), start + size)
        result[start:end] += wave[:end - start]
    result[0] = result[-1] = 0
    return result


def stable_ogg_container(path, name):
    """libsndfile randomizes Ogg serials; normalize ours and recalculate page CRCs."""
    data = bytearray(path.read_bytes())
    serial = sha256(name.encode("ascii")).digest()[:4]
    position = 0
    while position < len(data):
        assert data[position:position + 4] == b"OggS", name
        segment_count = data[position + 26]
        header_end = position + 27 + segment_count
        end = header_end + sum(data[position + 27:header_end])
        data[position + 14:position + 18] = serial
        data[position + 22:position + 26] = b"\0\0\0\0"
        checksum = 0
        for byte in data[position:end]:
            checksum ^= byte << 24
            for _ in range(8):
                checksum = ((checksum << 1) ^ (0x04C11DB7 if checksum & 0x80000000 else 0)) & 0xFFFFFFFF
        data[position + 22:position + 26] = checksum.to_bytes(4, "little")
        position = end
    assert position == len(data), name
    path.write_bytes(data)


def write_asset(name, samples):
    assert np.isfinite(samples).all(), name
    assert samples[0] == samples[-1] == 0, name
    assert np.max(np.abs(samples)) < 0.48, name
    path = OUT / f"{name}.ogg"
    sf.write(path, samples, RATE, format="OGG", subtype="VORBIS")
    stable_ogg_container(path, name)
    sf.write(PREVIEW / f"{name}.wav", samples, RATE, subtype="PCM_16")
    decoded, decoded_rate = sf.read(path)
    assert decoded_rate == RATE and decoded.shape == samples.shape, name
    print(f"{name}: {len(samples) / RATE:.3f}s, peak={max(abs(samples)):.3f}, "
          f"sha256={sha256(path.read_bytes()).hexdigest()}")


voices = {
    "two_i_voice": syllable(535, 0.036, (0.17, 0.040, 0.010), 0.028, 0.28, 1),
    # Less upper-harmonic energy and a gentler onset for shy/tender lines.
    "two_i_voice_soft": syllable(505, 0.040, (0.065, 0.012, 0.0), 0.015, 0.24, 2),
    # Joy/mischief is slightly brighter, not simply much louder or shrill.
    "two_i_voice_bright": syllable(565, 0.034, (0.22, 0.055, 0.012), 0.042, 0.27, 3),
    "player_voice": syllable(385, 0.036, (0.12, 0.028, 0.0), 0.017, 0.26, 4),
}

pats = {
    "two_i_pat_1": pat_melody(((0.0, 523.25, 0.14, 0.35),
                              (0.11, 659.25, 0.18, 0.30)), 0.32, 11),
    "two_i_pat_2": pat_melody(((0.0, 587.33, 0.12, 0.30),
                              (0.09, 739.99, 0.12, 0.26),
                              (0.18, 659.25, 0.17, 0.25)), 0.38, 12),
    "two_i_pat_3": pat_melody(((0.0, 493.88, 0.15, 0.32),
                              (0.13, 587.33, 0.18, 0.29)), 0.34, 13),
}

for asset_name, audio in {**voices, **pats}.items():
    write_asset(asset_name, audio)

# Quiet single-file listening sheet. Deliberately includes pauses between voices.
preview_parts = []
for audio in voices.values():
    for delay in (0.10, 0.08, 0.12, 0.07, 0.10, 0.08):
        preview_parts.extend((audio, np.zeros(round(delay * RATE))))
    preview_parts.append(np.zeros(round(0.45 * RATE)))
for audio in pats.values():
    preview_parts.extend((audio, np.zeros(round(0.5 * RATE))))
sf.write(PREVIEW / "2i-original-audio-listening-sheet.wav",
         np.concatenate(preview_parts), RATE, subtype="PCM_16")
