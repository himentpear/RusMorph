"""Generate legal synthetic fixtures; not used to claim model accuracy."""
from pathlib import Path

import numpy as np
import soundfile as sf

OUTPUT = Path(__file__).resolve().parent.parent / "sample_audio"
OUTPUT.mkdir(exist_ok=True)
rate = 16_000
fixtures = {
    "silence.wav": np.zeros(rate, dtype=np.float32),
    "too_short.wav": np.sin(2 * np.pi * 220 * np.arange(int(rate * 0.2)) / rate).astype(np.float32) * 0.1,
    "low_volume.wav": np.sin(2 * np.pi * 180 * np.arange(rate) / rate).astype(np.float32) * 0.002,
}
for name, samples in fixtures.items():
    sf.write(OUTPUT / name, samples, rate, subtype="PCM_16")
print(f"Generated {len(fixtures)} CC0 synthetic diagnostic fixtures in {OUTPUT}")
