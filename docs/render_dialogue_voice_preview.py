"""Render the actual Java-exported dialogue cues, not a separate guessed rhythm.

Run after runStoryPreview. Produces local WAV listening examples only; never bundled.
Uses the same OGG source, pitch and volume as the production SimpleSoundInstance.
"""
import json
from pathlib import Path
import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/audio-preview"
SOUNDS = ROOT / "src/main/resources/assets/useless_stretcher/sounds"
RATE = 24000
NAMES = {"NATURAL": "two_i_voice", "SOFT": "two_i_voice_soft", "BRIGHT": "two_i_voice_bright", "PLAYER": "player_voice"}
LABELS = ["shy", "bright", "hurt-to-serious", "surprise-to-shy", "player", "warm-to-shy"]
assets = {name: sf.read(SOUNDS / f"{file}.ogg")[0] for name, file in NAMES.items()}

for language in ("zh_cn", "en_us"):
    data = json.loads((OUT / f"dialogue-cues-{language}.json").read_text(encoding="utf-8"))
    combined = []
    for index, line in enumerate(data):
        cues = line["cues"]
        result = np.zeros(round((cues[-1]["start_ms"] / 1000 + 0.6) * RATE))
        for cue in cues:
            raw = assets[cue["timbre"]]
            samples = np.interp(np.arange(0, len(raw), cue["pitch"]), np.arange(len(raw)), raw) * cue["volume"]
            start = round(cue["start_ms"] * RATE / 1000)
            result[start:start + len(samples)] += samples
        assert np.isfinite(result).all() and np.max(np.abs(result)) < 0.5
        sf.write(OUT / f"{language}-{index + 1}-{LABELS[index]}.wav", result, RATE, subtype="PCM_16")
        combined.extend((result, np.zeros(RATE // 2)))
        print(f"{language}/{LABELS[index]}: {len(cues)} syllables, {len(result) / RATE:.2f}s; {line['text']}")
    sf.write(OUT / f"2i-dialogue-{language}-performance.wav", np.concatenate(combined), RATE, subtype="PCM_16")
