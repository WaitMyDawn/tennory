#!/usr/bin/env python3
"""Verify scaffold release artifacts without loading Minecraft (Python 3.11+)."""

import argparse
import hashlib
from io import BytesIO
import json
from pathlib import Path
import re
import struct
import tomllib
from zipfile import BadZipFile, ZipFile


COMMON_ANIMATION_DIRECTORY = "assets/tennory/player_animations/"
FORGE_ANIMATION_DIRECTORY = "assets/tennory/attack_animations/"


def properties(path):
    """Read the UTF-8 Java Properties subset emitted by scaffold.py."""
    result = {}
    for line in re.split(r"\r\n|\n|\r", path.read_text(encoding="utf-8")):
        if not line.strip() or line.lstrip().startswith(("#", "!")):
            continue
        key, separator, value = line.partition("=")
        if not separator or (len(line) - len(line.rstrip("\\"))) % 2 == 1:
            raise ValueError(f"Unsupported property syntax in {path}: {key}")
        def decode(match):
            token = match[1]
            return chr(int(token[1:], 16)) if token.startswith("u") else {"n": "\n", "r": "\r", "t": "\t", "f": "\f"}.get(token, token)
        result[key.strip()] = re.sub(r"\\(u[0-9a-fA-F]{4}|.)", decode, value)
    return result


def require(condition, message):
    if not condition:
        raise ValueError(message)


def packaged_path(loader, source_path):
    if loader == "forge" and source_path.startswith(COMMON_ANIMATION_DIRECTORY):
        return FORGE_ANIMATION_DIRECTORY + source_path.removeprefix(COMMON_ANIMATION_DIRECTORY)
    return source_path


def verify_tennory_data(project, loader, archive):
    entries = set(archive.namelist())
    for damage in ("glaive_burst", "weapon_volley"):
        definition = json.loads(archive.read(f"data/tennory/damage_type/{damage}.json"))
        require(definition["message_id"] == f"tennory.{damage}", f"Wrong damage type: {damage}")
    explosion_tag = "data/minecraft/tags/damage_type/is_explosion.json"
    if explosion_tag in entries:
        values = json.loads(archive.read(explosion_tag))["values"]
        require(all((entry if isinstance(entry, str) else entry["id"]) != "tennory:glaive_burst" for entry in values),
                "Glaive burst must use its own damage type without the vanilla explosion tag")
    for weapon in ("glaive", "redeemer", "quassus"):
        attributes = json.loads(archive.read(f"data/tennory/weapon_attributes/{weapon}.json"))["attributes"]
        require(len(attributes["attacks"]) == 3, f"Missing Better Combat combo: {weapon}")
        for attack in attributes["attacks"]:
            namespace, animation = attack["animation"].split(":", 1)
            source = f"assets/{namespace}/player_animations/{animation}.json"
            require(packaged_path(loader, source) in entries,
                    f"Missing Better Combat animation: {attack['animation']}")
    if loader == "forge":
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
        require("MixinConfigs: tennory.mixins.json" in manifest.splitlines(), "Forge manifest lacks Tennory mixins")
        mixins = json.loads(archive.read("tennory.mixins.json"))
        refmap = json.loads(archive.read(mixins["refmap"]))["mappings"]
        path = mixins["package"].replace(".", "/")
        for name in ("VolleyPlayerModelMixin", "VolleyItemInHandLayerMixin"):
            require(bool(refmap.get(f"{path}/{name}")), f"Missing remapped injection targets for {name}")
        pool = "data/tennory/loot_tables/rewards/tennory_weapon_pool.json"
        source = project / "common/src/main/resources" / pool.replace("/loot_tables/", "/loot_table/")
        require(archive.read(pool) == source.read_bytes(), "Forge loot pool differs from the shared pool")


def verify_jar(project, target, jar):
    shared = properties(project / "gradle.properties")
    local = properties(project / "platforms" / target / "gradle.properties")
    loader, minecraft = target.split("-", 1)
    java = int(local["platform_java"])
    root = project / "platforms" / target / "src/main"
    with ZipFile(jar) as archive:
        entries = archive.namelist()
        require(len(entries) == len(set(entries)), f"Duplicate ZIP entries in {jar}")
        require(archive.testzip() is None, f"Corrupt ZIP entry in {jar}")
        require(archive.read("LICENSE.md") == (project / "LICENSE.md").read_bytes(), "Embedded license differs")
        for source, version in ((project / "common/src/main", 17), (root, java)):
            for path in (source / "java").rglob("*.java"):
                relative = path.relative_to(source / "java").with_suffix(".class").as_posix()
                require(relative in entries, f"Missing class {relative}")
                bytecode = archive.read(relative)
                require(len(bytecode) >= 8 and bytecode[:4] == b"\xca\xfe\xba\xbe", f"Invalid class {relative}")
                require(struct.unpack(">H", bytecode[6:8])[0] <= version + 44, f"Bytecode target too new: {relative}")
        for path in (project / "common/src/main/resources").rglob("*"):
            if path.is_file():
                relative = path.relative_to(project / "common/src/main/resources").as_posix()
                require(archive.read(packaged_path(loader, relative)) == path.read_bytes(), f"Shared resource differs: {relative}")
        if loader == "fabric":
            metadata = json.loads(archive.read("fabric.mod.json"))
            for key, property_key in (("id", "mod_id"), ("version", "mod_version"), ("name", "mod_name"),
                                      ("description", "mod_description"), ("license", "mod_license")):
                require(metadata[key] == shared[property_key], f"Wrong Fabric metadata: {key}")
            require(metadata["authors"] == [shared["mod_authors"]], "Wrong Fabric authors")
            expected = {"minecraft": minecraft, "java": f">={java}", "fabricloader": ">=" + local["loader_version"],
                        "fabric-api": ">=" + local["fabric_api_version"]}
            require(all(metadata["depends"].get(k) == v for k, v in expected.items()), "Wrong Fabric dependency ranges")
            for side in ("main", "client"):
                for entrypoint in metadata["entrypoints"][side]:
                    require(entrypoint.replace(".", "/") + ".class" in entries, f"Missing {side} entrypoint")
            nested_classes = set()
            for nested in metadata.get("jars", []):
                name = nested["file"]
                require(name in entries and name.startswith("META-INF/jars/") and ".." not in Path(name).parts,
                        f"Missing or unsafe nested JAR: {name}")
                with ZipFile(BytesIO(archive.read(name))) as dependency:
                    nested_classes.update(dependency.namelist())
            require({"com/electronwill/nightconfig/core/Config.class", "com/electronwill/nightconfig/toml/TomlFormat.class"}
                    <= nested_classes, "Fabric config runtime dependencies are not bundled")
        else:
            descriptor = "META-INF/neoforge.mods.toml" if loader == "neoforge" else "META-INF/mods.toml"
            metadata = tomllib.loads(archive.read(descriptor).decode("utf-8"))
            require(metadata["modLoader"] == "javafml", "Wrong FML mod loader")
            mod = next((m for m in metadata["mods"] if m["modId"] == shared["mod_id"]), None)
            require(mod is not None, "Missing FML mod descriptor")
            for key, property_key in (("version", "mod_version"), ("displayName", "mod_name"), ("description", "mod_description")):
                require(mod[key] == shared[property_key], f"Wrong FML metadata: {key}")
            require(metadata["license"] == shared["mod_license"], "Wrong FML license")
            require(metadata["loaderVersion"] == local["fml_range"], "Wrong FML language-loader range")
            dependencies = {value["modId"]: value for value in metadata["dependencies"][shared["mod_id"]]}
            require(dependencies["minecraft"]["versionRange"] == f"[{minecraft}]", "Wrong Minecraft compatibility range")
            require(dependencies[loader]["versionRange"] == local["loader_range"], "Wrong loader compatibility range")
            if loader == "forge":
                require(json.loads(archive.read("pack.mcmeta"))["pack"]["pack_format"] > 0, "Missing Forge resource-pack format")
        verify_tennory_data(project, loader, archive)
    return {"target": target, "jar": str(jar), "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(), "status": "passed"}


def verify_project(project, targets=None, release=False):
    project = Path(project).resolve()
    matrix = json.loads((project / "gradle/platforms.json").read_text(encoding="utf-8"))
    available = {entry["directory"] for entry in matrix.values()}
    selected = available if targets is None else set(targets)
    require(bool(selected) and selected <= available, "Unknown or empty verification target selection")
    shared = properties(project / "gradle.properties")
    expected = {target: f"{shared['mod_id']}-{target}-{shared['mod_version']}.jar" for target in available}
    if release:
        actual = {path.name for path in (project / "build/libs").glob("*.jar")}
        require(actual == set(expected.values()), f"Root release set differs: expected {sorted(expected.values())}, got {sorted(actual)}")
    results = []
    for target in sorted(selected):
        jar = project / ("build/libs" if release else f"platforms/{target}/build/libs") / expected[target]
        results.append(verify_jar(project, target, jar))
        if release:
            original = project / "platforms" / target / "build/libs" / expected[target]
            require(jar.read_bytes() == original.read_bytes(), f"Root collection differs: {target}")
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, required=True)
    parser.add_argument("--target", action="append")
    parser.add_argument("--release", action="store_true", help="Verify exact root build/libs set and byte-for-byte collection")
    args = parser.parse_args()
    try:
        print(json.dumps(verify_project(args.project, args.target, args.release), indent=2))
    except (OSError, ValueError, KeyError, TypeError, BadZipFile) as error:
        parser.exit(1, f"Artifact verification failed: {error}\n")


if __name__ == "__main__":
    main()
