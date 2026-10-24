"""Check the actual Forge release, including stale opt-in verification output."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile


def audit(root, artifact):
    properties = dict(line.split("=", 1) for line in
                      (root / "gradle.properties").read_text(encoding="utf-8").splitlines()
                      if "=" in line and not line.startswith("#"))
    with ZipFile(artifact) as jar:
        names = jar.namelist()
        assert len(names) == len(set(names)), "Duplicate JAR entries"
        metadata = jar.read("META-INF/mods.toml").decode("utf-8")
        assert f'version="{properties["mod_version"]}"' in metadata
        assert 'modId="forge"' in metadata and 'versionRange="[1.20.1,1.20.2)"' in metadata
        assert "${" not in metadata and "META-INF/neoforge.mods.toml" not in names
        forbidden = ("/capsulecheck/", "ForgeCapsule", "stretcher_capsule_check", "/regression/",
                     "/storytest/", "/clientprobe/", "/guidetest/", "org/junit/", "org/mockito/",
                     "data/useless_stretcher_test/")
        leaked = [name for name in names if any(token in name for token in forbidden)
                  or (name.endswith(".class") and "Checks" in name)]
        assert not leaked, leaked
        sources = sorted((root / "src/main/java").rglob("*.java"))
        missing = [str(source.relative_to(root)) for source in sources
                   if source.relative_to(root / "src/main/java").with_suffix(".class").as_posix() not in names]
        assert not missing, missing
        resources = sorted((root / "src/main/resources").rglob("*"))
        checked = 0
        for source in resources:
            if not source.is_file():
                continue
            name = source.relative_to(root / "src/main/resources").as_posix()
            assert name in names, name
            if name != "META-INF/mods.toml":
                assert jar.read(name) == source.read_bytes(), f"Stale resource: {name}"
            checked += 1
        mixins = json.loads(jar.read("useless_stretcher.mixins.json"))
        for name in mixins["mixins"] + mixins.get("client", []):
            assert mixins["package"].replace(".", "/") + "/" + name.replace(".", "/") + ".class" in names
        refmap = json.loads(jar.read("useless_stretcher.refmap.json"))
        clock = json.dumps(refmap["mappings"].get(
            "com/sorrowmist/useless/stretcher/mixin/OccultismRitualClockMixin", {}))
        assert "m_46467_" in clock, "Golden-bowl vanilla game-time call was not remapped"
        for name in ("OccultismAccelerationCompat", "RftoolsBuilderAccelerationCompat"):
            assert f"com/sorrowmist/useless/stretcher/compat/{name}.class" in names
        snapshot = refmap["mappings"].get(
            "com/sorrowmist/useless/stretcher/mixin/CapsuleChunkSaveSnapshotMixin", {})
        assert "m_140258_" in json.dumps(snapshot), "Capsule chunk save snapshot was not remapped"
        assert "com/sorrowmist/useless/stretcher/client/dialogue/TwoIStoryScript.class" in names
        assert sum(name.startswith("assets/useless_stretcher/dialogue/long_stories/chapter_")
                   and name.endswith(".json") for name in names) == 6
        embedded = json.loads(jar.read("META-INF/jarjar/metadata.json"))["jars"]
        assert len(embedded) == 1 and embedded[0]["identifier"]["artifact"] == "mixinextras-forge"
        assert embedded[0]["version"]["artifactVersion"] == "0.5.0"
        assert embedded[0]["path"] in names
        foreign = [name for name in names if name.endswith(".class")
                   and not name.startswith("com/sorrowmist/useless/stretcher/")]
        assert not foreign, foreign
        return {"artifact": str(artifact.resolve()), "version": properties["mod_version"],
                "bytes": artifact.stat().st_size,
                "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest().upper(),
                "production_java": len(sources), "production_resources": checked,
                "classes": sum(name.endswith(".class") for name in names),
                "verification_entries": len(leaked), "foreign_classes": len(foreign),
                "embedded_runtime": "MixinExtras Forge 0.5.0", "status": "PASSED"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("artifact", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = audit(Path(__file__).resolve().parent.parent, args.artifact)
    encoded = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)
