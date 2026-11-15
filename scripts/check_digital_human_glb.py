#!/usr/bin/env python3
"""Strict structural/provenance gate for a self-contained glTF 2.0 GLB."""
import json, struct, sys
from pathlib import Path

REQUIRED = {
 "jawopen","mouthclose","mouthfunnel","mouthpucker","mouthwide",
 "mouthsmileleft","mouthsmileright","mouthfrownleft","mouthfrownright",
 "browinnerup","browdownleft","browdownright","cheekpuff",
 "cheeksquintleft","cheeksquintright","eyeblinkleft","eyeblinkright",
 "eyelookupleft","eyelookupright","viseme_aa","viseme_ee","viseme_ih","viseme_oh","viseme_ou"
}
def fail(message): raise SystemExit("FAIL: " + message)
def read_glb(path):
 data=path.read_bytes()
 if len(data)<20: fail("file too small")
 magic,version,length=struct.unpack_from("<4sII",data,0)
 if magic!=b"glTF" or version!=2: fail("only glTF 2.0 GLB is supported")
 if length!=len(data): fail("header length mismatch")
 offset=12; doc=None
 while offset+8<=len(data):
  size,kind=struct.unpack_from("<II",data,offset); offset+=8; end=offset+size
  if end>len(data): fail("chunk outside file")
  if kind==0x4E4F534A: doc=json.loads(data[offset:end].rstrip(b" \\t\\r\\n\\0").decode("utf-8"))
  offset=end
 if doc is None: fail("missing JSON chunk")
 return doc
def main():
 if len(sys.argv)!=2: fail("usage: check_digital_human_glb.py path/to/avatar.glb")
 path=Path(sys.argv[1])
 if not path.is_file(): fail("asset not found: "+str(path))
 doc=read_glb(path); nodes=doc.get("nodes",[]); meshes=doc.get("meshes",[])
 skins=doc.get("skins",[]); animations=doc.get("animations",[])
 materials=doc.get("materials",[]); images=doc.get("images",[]); textures=doc.get("textures",[])
 if not meshes: fail("no meshes")
 if not skins or not any(n.get("skin") is not None for n in nodes): fail("skinned mesh and skin required")
 if not animations: fail("animation clips required")
 names=set(); max_morphs=0; joints=False
 for mesh in meshes:
  target_names=mesh.get("extras",{}).get("targetNames",[])
  for primitive in mesh.get("primitives",[]):
   attrs=primitive.get("attributes",{})
   joints |= "JOINTS_0" in attrs and "WEIGHTS_0" in attrs
   targets=primitive.get("targets",[]); max_morphs=max(max_morphs,len(targets))
   for i in range(len(targets)):
    if i<len(target_names): names.add(str(target_names[i]).lower())
 if not joints: fail("JOINTS_0 and WEIGHTS_0 are required")
 if max_morphs<20: fail("at least 20 facial morph targets required")
 missing=sorted(REQUIRED-names)
 if missing: fail("missing facial targets: "+", ".join(missing))
 base=normal=mr=False
 for material in materials:
  pbr=material.get("pbrMetallicRoughness",{})
  base |= bool(pbr.get("baseColorTexture"))
  mr |= bool(pbr.get("metallicRoughnessTexture"))
  normal |= bool(material.get("normalTexture"))
 if not base: fail("PBR base-color texture missing")
 if not normal: fail("tangent-space normal map missing")
 if not mr: fail("metallic-roughness map missing")
 if not images or not textures: fail("embedded image/texture data required")
 if any("uri" in x for x in images): fail("external image URI not supported; embed images")
 if any("uri" in x for x in doc.get("buffers",[])): fail("external buffer URI not supported; embed buffers")
 manifest=path.with_suffix(".license.json")
 if not manifest.is_file(): fail("missing rights manifest "+manifest.name)
 rights=json.loads(manifest.read_text(encoding="utf-8"))
 for key in ("asset_name","author","source_url","license","attribution"):
  if not str(rights.get(key,"")).strip(): fail("rights manifest field required: "+key)
 if rights.get("commercial_redistribution") is not True: fail("commercial_redistribution must be true")
 if rights.get("modification_allowed") is not True: fail("modification_allowed must be true")
 print("STRUCTURAL PASS")
 print(f"Meshes: {len(meshes)} | skins: {len(skins)} | animations: {len(animations)}")
 print(f"Facial targets: {len(names)} | max targets per primitive: {max_morphs}")
 print("PBR maps and rights manifest present.")
 print("Human visual review, legal verification, runtime compatibility and device performance remain separate gates.")
if __name__=="__main__": main()
