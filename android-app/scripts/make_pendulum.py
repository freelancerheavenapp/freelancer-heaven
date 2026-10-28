"""Generate a self-contained pendulum GLB with a separately animated arm."""
import json
import math
import pathlib
import struct


def cube(width, height, depth):
    x, y, z = width / 2, height / 2, depth / 2
    positions = [
        (-x, -y, -z), (x, -y, -z), (x, y, -z), (-x, y, -z),
        (-x, -y, z), (x, -y, z), (x, y, z), (-x, y, z),
    ]
    normals = [
        (-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
        (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1),
    ]
    faces = [0, 1, 2, 0, 2, 3, 4, 6, 5, 4, 7, 6, 0, 4, 5, 0, 5, 1,
             3, 2, 6, 3, 6, 7, 0, 3, 7, 0, 7, 4, 1, 5, 6, 1, 6, 2]
    return positions, normals, faces


def sphere(radius, rings=18, segments=24):
    positions, normals, indices = [], [], []
    for row in range(rings + 1):
        theta = row * math.pi / rings
        for col in range(segments + 1):
            phi = col * math.tau / segments
            normal = (math.sin(theta) * math.cos(phi), math.cos(theta),
                      math.sin(theta) * math.sin(phi))
            normals.append(normal)
            positions.append(tuple(radius * value for value in normal))
    for row in range(rings):
        for col in range(segments):
            point = row * (segments + 1) + col
            indices += [point, point + 1, point + segments + 1,
                        point + 1, point + segments + 2, point + segments + 1]
    return positions, normals, indices


def add_mesh(name, geometry, material, buffers, views, accessors, meshes):
    positions, normals, indices = geometry
    flat_positions = [value for point in positions for value in point]
    flat_normals = [value for point in normals for value in point]
    position_bytes = struct.pack("<%sf" % len(flat_positions), *flat_positions)
    normal_bytes = struct.pack("<%sf" % len(flat_normals), *flat_normals)
    index_bytes = struct.pack("<%sH" % len(indices), *indices)

    def append(data, target=None):
        offset = len(buffers)
        buffers.extend(data)
        while len(buffers) % 4:
            buffers.append(0)
        view = {"buffer": 0, "byteOffset": offset, "byteLength": len(data)}
        if target:
            view["target"] = target
        views.append(view)
        return len(views) - 1

    pview, nview, iview = append(position_bytes, 34962), append(normal_bytes, 34962), append(index_bytes, 34963)
    minimum = [min(point[index] for point in positions) for index in range(3)]
    maximum = [max(point[index] for point in positions) for index in range(3)]
    paccessor = len(accessors)
    accessors.append({"bufferView": pview, "componentType": 5126, "count": len(positions), "type": "VEC3", "min": minimum, "max": maximum})
    naccessor = len(accessors)
    accessors.append({"bufferView": nview, "componentType": 5126, "count": len(normals), "type": "VEC3"})
    iaccessor = len(accessors)
    accessors.append({"bufferView": iview, "componentType": 5123, "count": len(indices), "type": "SCALAR"})
    meshes.append({"name": name, "primitives": [{"attributes": {"POSITION": paccessor, "NORMAL": naccessor}, "indices": iaccessor, "material": material}]})
    return len(meshes) - 1


buffers, views, accessors, meshes = bytearray(), [], [], []
frame_mesh = add_mesh("Frame", cube(2.1, 0.24, 0.44), 0, buffers, views, accessors, meshes)
pillar_mesh = add_mesh("Pillar", cube(0.18, 1.82, 0.34), 0, buffers, views, accessors, meshes)
accent_mesh = add_mesh("Teal accent", cube(1.6, 0.075, 0.12), 1, buffers, views, accessors, meshes)
rod_mesh = add_mesh("Pendulum rod", cube(0.075, 1.38, 0.07), 2, buffers, views, accessors, meshes)
bob_mesh = add_mesh("Pendulum weight", sphere(0.22), 1, buffers, views, accessors, meshes)
pivot_mesh = add_mesh("Pendulum pivot", sphere(0.105), 2, buffers, views, accessors, meshes)

nodes = [
    {"name": "Base", "mesh": frame_mesh, "translation": [0, -1.32, 0]},
    {"name": "Left pillar", "mesh": pillar_mesh, "translation": [-0.84, -0.31, 0]},
    {"name": "Right pillar", "mesh": pillar_mesh, "translation": [0.84, -0.31, 0]},
    {"name": "Top bar", "mesh": frame_mesh, "scale": [0.92, 0.8, 1], "translation": [0, 0.64, 0]},
    {"name": "Teal rail", "mesh": accent_mesh, "translation": [0, -1.16, 0.24]},
    {"name": "PendulumArm", "translation": [0, 0.55, 0], "children": [6, 7, 8]},
    {"name": "Pivot", "mesh": pivot_mesh},
    {"name": "Rod", "mesh": rod_mesh, "translation": [0, -0.69, 0]},
    {"name": "Weight", "mesh": bob_mesh, "translation": [0, -1.45, 0]},
]

document = {
    "asset": {"version": "2.0", "generator": "Freelancer Heaven pendulum generator"},
    "buffers": [{"byteLength": len(buffers)}],
    "bufferViews": views,
    "accessors": accessors,
    "materials": [
        {"name": "Navy metal", "pbrMetallicRoughness": {"baseColorFactor": [0.025, 0.12, 0.22, 1], "metallicFactor": 0.72, "roughnessFactor": 0.27}},
        {"name": "Teal glass", "pbrMetallicRoughness": {"baseColorFactor": [0.02, 0.66, 0.70, 1], "metallicFactor": 0.45, "roughnessFactor": 0.18}},
        {"name": "Silver", "pbrMetallicRoughness": {"baseColorFactor": [0.78, 0.86, 0.88, 1], "metallicFactor": 0.92, "roughnessFactor": 0.20}},
    ],
    "meshes": meshes,
    "nodes": nodes,
    "scenes": [{"nodes": [0, 1, 2, 3, 4, 5]}],
    "scene": 0,
}

json_bytes = json.dumps(document, separators=(",", ":")).encode()
json_bytes += b" " * ((-len(json_bytes)) % 4)
binary = bytes(buffers) + b"\0" * ((-len(buffers)) % 4)
payload = (struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(json_bytes) + 8 + len(binary)) +
           struct.pack("<II", len(json_bytes), 0x4E4F534A) + json_bytes +
           struct.pack("<II", len(binary), 0x004E4942) + binary)

target = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/assets/timer-pendulum.glb"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(payload)
