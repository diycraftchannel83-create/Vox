#!/usr/bin/env python3
"""Structural and provenance gate for production glTF 2.0 binary avatars.

This is a conservative CI gate, not a realism, legal, or runtime certification.
"""
import json
import struct
import sys
from pathlib import Path

REQUIRED = {
    "jawopen", "mouthclose", "mouthfunnel", "mouthpucker", "mouthwide",
    "mouthsmileleft", "mouthsmileright", "mouthfrownleft", "mouthfrownright",
    "browinnerup", "browdownleft", "browdownright", "cheekpuff",
    "cheeksquintleft", "cheeksquintright", "eyeblinkleft", "eyeblinkright",
    "eyelookupleft", "eyelookupright", "viseme_aa", "viseme_ee",
    "viseme_ih", "viseme_oh", "viseme_ou",
}


def fail(message):
    raise SystemExit("FAIL: " + message)


def read_glb(path):
    data = path.read_bytes()
    if len(data) < 20:
        fail("file too small")
    magic, version, length = struct.unpack_from("<4sII", data, 0)
    if magic != b"glTF" or version != 2:
        fail("only glTF 2.0 GLB is supported")
    if length != len(data):
        fail("header length mismatch")
    offset = 12
    doc = None
    while offset + 8 <= len(data):
        size, kind = struct.unpack_from("<II", data, offset)
        offset += 8
        end = offset + size
        if end > len(data):
            fail("chunk outside file")
        if kind == 0x4E4F534A:
            try:
                doc = json.loads(data[offset:end].rstrip(b" \\t\\r\\n\\0").decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                fail("invalid JSON chunk: " + str(exc))
        offset = end
    if offset != len(data):
        fail("trailing bytes or malformed chunk boundary")
    if doc is None:
        fail("missing JSON chunk")
    return doc


def check_index(index, collection, label):
    if not isinstance(index, int) or index < 0 or index >= len(collection):
        fail(f"{label} index out of range: {index}")


def main():
    if len(sys.argv) != 2:
        fail("usage: check_digital_human_glb.py path/to/avatar.glb")
    path = Path(sys.argv[1])
    if not path.is_file():
        fail("asset not found: " + str(path))

    doc = read_glb(path)
    nodes = doc.get("nodes", [])
    meshes = doc.get("meshes", [])
    skins = doc.get("skins", [])
    animations = doc.get("animations", [])
    materials = doc.get("materials", [])
    images = doc.get("images", [])
    textures = doc.get("textures", [])
    accessors = doc.get("accessors", [])
    buffer_views = doc.get("bufferViews", [])
    buffers = doc.get("buffers", [])

    if not meshes:
        fail("no meshes")
    if not skins:
        fail("no skins")
    if not animations:
        fail("no animation clips")

    skinned_nodes = [n for n in nodes if n.get("skin") is not None and n.get("mesh") is not None]
    if not skinned_nodes:
        fail("a node must reference both a mesh and a skin")

    joint_node_indices = set()
    for skin in skins:
        joints = skin.get("joints", [])
        if len(joints) < 20:
            fail("humanoid skin should contain at least 20 joints")
        for joint in joints:
            check_index(joint, nodes, "skin joint")
            joint_node_indices.add(joint)
        if skin.get("inverseBindMatrices") is not None:
            check_index(skin["inverseBindMatrices"], accessors, "inverseBindMatrices accessor")

    names = set()
    max_morphs = 0
    has_joint_weights = False
    for mesh in meshes:
        target_names = mesh.get("extras", {}).get("targetNames", [])
        for primitive in mesh.get("primitives", []):
            attrs = primitive.get("attributes", {})
            has_joint_weights |= "JOINTS_0" in attrs and "WEIGHTS_0" in attrs
            for semantic, accessor_index in attrs.items():
                check_index(accessor_index, accessors, f"attribute {semantic}")
            targets = primitive.get("targets", [])
            max_morphs = max(max_morphs, len(targets))
            for target in targets:
                for semantic, accessor_index in target.items():
                    check_index(accessor_index, accessors, f"morph {semantic}")
            for i in range(len(targets)):
                if i < len(target_names):
                    names.add(str(target_names[i]).lower())

    if not has_joint_weights:
        fail("JOINTS_0 and WEIGHTS_0 are required")
    missing = sorted(REQUIRED - names)
    if missing:
        fail("missing facial targets: " + ", ".join(missing))
    if max_morphs < len(REQUIRED):
        fail(f"at least {len(REQUIRED)} morph targets are required on a facial primitive")

    # Require animation channels for both body joints and facial morph weights.
    animated_nodes = set()
    animated_weights = False
    for animation in animations:
        samplers = animation.get("samplers", [])
        for channel in animation.get("channels", []):
            sampler_index = channel.get("sampler")
            check_index(sampler_index, samplers, "animation sampler")
            target = channel.get("target", {})
            if target.get("path") == "weights":
                animated_weights = True
            if target.get("node") is not None:
                check_index(target["node"], nodes, "animation target node")
                animated_nodes.add(target["node"])
    if not (animated_nodes & joint_node_indices):
        fail("no animation channel targets a joint in the humanoid skeleton")
    if not animated_weights:
        fail("no animation channel targets facial morph weights")

    base = normal = mr = False
    for material in materials:
        pbr = material.get("pbrMetallicRoughness", {})
        base |= bool(pbr.get("baseColorTexture"))
        mr |= bool(pbr.get("metallicRoughnessTexture"))
        normal |= bool(material.get("normalTexture"))
        for slot in (pbr.get("baseColorTexture"), pbr.get("metallicRoughnessTexture"),
                     material.get("normalTexture"), material.get("occlusionTexture"),
                     material.get("emissiveTexture")):
            if slot is not None:
                check_index(slot.get("index"), textures, "material texture")
    if not base:
        fail("PBR base-color texture missing")
    if not normal:
        fail("tangent-space normal map missing")
    if not mr:
        fail("metallic-roughness map missing")
    if not images or not textures:
        fail("embedded image/texture data required")
    for texture in textures:
        if texture.get("source") is not None:
            check_index(texture["source"], images, "texture image")
    for image in images:
        if "uri" in image or "bufferView" not in image:
            fail("images must be embedded using bufferView (external/data URIs are not accepted)")
        check_index(image["bufferView"], buffer_views, "image bufferView")
    for buffer_view in buffer_views:
        check_index(buffer_view.get("buffer"), buffers, "bufferView buffer")
    if any("uri" in buffer for buffer in buffers):
        fail("external buffer URI not supported; embed buffers")

    manifest = path.with_suffix(".license.json")
    if not manifest.is_file():
        fail("missing rights manifest " + manifest.name)
    try:
        rights = json.loads(manifest.read_text(encoding="utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        fail("invalid rights manifest: " + str(exc))
    for key in ("asset_name", "author", "source_url", "license", "attribution"):
        if not str(rights.get(key, "")).strip():
            fail("rights manifest field required: " + key)
    if rights.get("commercial_redistribution") is not True:
        fail("commercial_redistribution must be true")
    if rights.get("modification_allowed") is not True:
        fail("modification_allowed must be true")

    print("STRUCTURAL PASS")
    print(f"Meshes: {len(meshes)} | skins: {len(skins)} | animated clips: {len(animations)}")
    print(f"Facial targets: {len(names)} | skeleton joints: {len(joint_node_indices)}")
    print("PBR maps, embedded resources, and rights manifest are structurally present.")
    print("Human realism review, license verification, runtime compatibility, and device performance remain separate gates.")


if __name__ == "__main__":
    main()
