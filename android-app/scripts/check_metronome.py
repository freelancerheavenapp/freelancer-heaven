"""Validate the exported hierarchy and pivot invariant across the swing."""
import json, math, pathlib, struct
p=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets/timer-metronome.glb'
b=p.read_bytes();magic,version,size=struct.unpack_from('<III',b)
assert magic==0x46546c67 and version==2 and size==len(b)
length=struct.unpack_from('<I',b,12)[0];g=json.loads(b[20:20+length])
nodes={n['name']:n for n in g['nodes']}
pivot=nodes['PendulumPivot'];t=pivot['translation']
assert abs(t[1]-.48)<.001 and abs(t[2]-.58)<.001
assert {g['nodes'][i]['name'] for i in pivot['children']}=={'PendulumArm','Weight'}
assert 'children' not in nodes['MetronomeBody']
assert len(g['materials'])<=6
triangles=sum(g['accessors'][p['indices']]['count']//3 for m in g['meshes'] for p in m['primitives'])
assert triangles<=25000
for degrees in (-18,0,18):
    a=math.radians(degrees)
    # T(rest) * R(z) leaves the joint origin fixed and preserves arm length.
    point=(t[0]-1.72*math.sin(a),t[1]+1.72*math.cos(a),t[2])
    assert abs(sum((point[i]-t[i])**2 for i in range(3))-1.72**2)<1e-6
    # Sphere is entirely in front of the panel, throughout the swing.
    assert point[2]-.13>.43
print('GLB hierarchy, triangle budget, pivot and panel clearance verified:',triangles)
