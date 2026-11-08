"""Read-only NBT/Anvil audit of the isolated capsule regression save."""
from collections import Counter
from pathlib import Path
import gzip
import json
import math
import struct
import sys
import zlib


class NbtReader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def read(self, count):
        result = self.data[self.pos:self.pos + count]
        if len(result) != count:
            raise ValueError("Truncated NBT")
        self.pos += count
        return result

    def unpack(self, fmt):
        return struct.unpack(fmt, self.read(struct.calcsize(fmt)))[0]

    def string(self):
        return self.read(self.unpack(">H")).decode("utf-8", errors="replace")

    def payload(self, tag):
        formats = {1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}
        if tag in formats:
            return self.unpack(formats[tag])
        if tag == 7:
            return list(self.read(self.unpack(">i")))
        if tag == 8:
            return self.string()
        if tag == 9:
            child = self.unpack(">B")
            length = self.unpack(">i")
            return [self.payload(child) for _ in range(length)]
        if tag == 10:
            result = {}
            while True:
                child = self.unpack(">B")
                if child == 0:
                    return result
                name = self.string()
                result[name] = self.payload(child)
        if tag in (11, 12):
            length = self.unpack(">i")
            fmt = ">i" if tag == 11 else ">q"
            return [self.unpack(fmt) for _ in range(length)]
        raise ValueError(f"Unknown NBT tag {tag}")

    def root(self):
        tag = self.unpack(">B")
        name = self.string()
        result = self.payload(tag)
        if self.pos != len(self.data):
            raise ValueError(f"Trailing NBT bytes for {name!r}")
        return result


def chunks(region):
    data = region.read_bytes()
    for index in range(1024):
        entry = int.from_bytes(data[index * 4:index * 4 + 4], "big")
        offset = entry >> 8
        if not offset:
            continue
        start = offset * 4096
        length = int.from_bytes(data[start:start + 4], "big")
        compression = data[start + 4]
        raw = data[start + 5:start + 4 + length]
        if compression == 1:
            raw = gzip.decompress(raw)
        elif compression == 2:
            raw = zlib.decompress(raw)
        elif compression != 3:
            raise ValueError(f"Unsupported Anvil compression {compression}")
        yield NbtReader(raw).root()


def main():
    world = Path(sys.argv[1])
    saved = NbtReader(gzip.decompress((world / "data/useless_stretcher_native_capsules.dat").read_bytes())).root()
    rooms = saved["data"]["rooms"]
    report = {"world": str(world), "rooms": []}
    for room in rooms:
        summary = {k: v for k, v in room.items() if k not in ("plan", "pixels", "owner")}
        summary["plan"] = room["plan"]
        min_x, min_y, min_z, max_x, max_y, max_z = room["bounds"]
        region_dir = world / "dimensions" / room["dimension"].replace(":", "/") / "region"
        if not region_dir.is_dir():
            raise ValueError(f"Missing physical dimension: {region_dir}")
        counts = Counter()
        bounds_counts = Counter()
        entities = []
        special_positions = []
        chunk_count = 0
        for region in sorted(region_dir.glob("*.mca")):
            for chunk in chunks(region):
                cx, cz = chunk["xPos"], chunk["zPos"]
                if cx * 16 > max_x or cx * 16 + 15 < min_x or cz * 16 > max_z or cz * 16 + 15 < min_z:
                    continue
                chunk_count += 1
                for entity in chunk.get("block_entities", []):
                    if min_x <= entity.get("x", 0) <= max_x and min_z <= entity.get("z", 0) <= max_z:
                        entities.append(entity)
                for section in chunk.get("sections", []):
                    states = section.get("block_states")
                    if not states:
                        continue
                    palette = states["palette"]
                    if len(palette) == 1 and palette[0]["Name"] in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air"):
                        continue
                    packed = states.get("data", [])
                    bits = max(4, math.ceil(math.log2(len(palette))))
                    per_long = 64 // bits
                    mask = (1 << bits) - 1
                    base_y = section["Y"] * 16
                    for index in range(4096):
                        x = cx * 16 + (index & 15)
                        z = cz * 16 + ((index >> 4) & 15)
                        if not (min_x <= x <= max_x and min_z <= z <= max_z):
                            continue
                        pi = 0 if len(palette) == 1 else (packed[index // per_long] >> ((index % per_long) * bits)) & mask
                        name = palette[pi]["Name"]
                        if name in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air"):
                            continue
                        y = base_y + (index >> 8)
                        counts[name] += 1
                        if min_y <= y <= max_y:
                            bounds_counts[name] += 1
                        if "mysterious_cube" in name or "budding" in name:
                            special_positions.append({"name": name, "x": x, "y": y, "z": z})
        summary["physical_region_directory"] = str(region_dir)
        summary["examined_chunks"] = chunk_count
        summary["non_air_counts_room_xz_all_y"] = dict(counts.most_common())
        summary["non_air_counts_saved_bounds"] = dict(bounds_counts.most_common())
        summary["block_entities"] = entities
        summary["special_positions"] = special_positions
        report["rooms"].append(summary)
    output = Path(sys.argv[2])
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
