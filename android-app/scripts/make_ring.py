"""Generate a self-contained, indexed glTF torus with normals; no external assets."""
import json,struct,math,pathlib
vertices=[]; normals=[]; indices=[]
for i in range(97):
 a=i/96*math.tau
 for j in range(17):
  b=j/16*math.tau
  vertices.extend(((1+.15*math.cos(b))*math.cos(a),(1+.15*math.cos(b))*math.sin(a),.15*math.sin(b)))
  normals.extend((math.cos(b)*math.cos(a),math.cos(b)*math.sin(a),math.sin(b)))
for i in range(96):
 for j in range(16):
  k=i*17+j
  indices.extend((k,k+1,k+17,k+1,k+18,k+17))
v=struct.pack('<%sf'%len(vertices),*vertices); n=struct.pack('<%sf'%len(normals),*normals); ix=struct.pack('<%sH'%len(indices),*indices)
data=v+n+ix
g={'asset':{'version':'2.0'},'buffers':[{'byteLength':len(data)}],
'bufferViews':[{'buffer':0,'byteOffset':0,'byteLength':len(v)},{'buffer':0,'byteOffset':len(v),'byteLength':len(n)},{'buffer':0,'byteOffset':len(v)+len(n),'byteLength':len(ix)}],
'accessors':[{'bufferView':0,'componentType':5126,'count':len(vertices)//3,'type':'VEC3','min':[-1.15,-1.15,-.15],'max':[1.15,1.15,.15]},{'bufferView':1,'componentType':5126,'count':len(normals)//3,'type':'VEC3'},{'bufferView':2,'componentType':5123,'count':len(indices),'type':'SCALAR'}],
'materials':[{'pbrMetallicRoughness':{'baseColorFactor':[.02,.52,.62,1],'metallicFactor':.55,'roughnessFactor':.22}}],
'meshes':[{'primitives':[{'attributes':{'POSITION':0,'NORMAL':1},'indices':2,'material':0}]}],
'nodes':[{'mesh':0}],'scenes':[{'nodes':[0]}],'scene':0}
j=json.dumps(g,separators=(',',':')).encode()
j+=b' '*((-len(j))%4); data+=b'\0'*((-len(data))%4)
out=struct.pack('<III',0x46546c67,2,12+8+len(j)+8+len(data))+struct.pack('<II',len(j),0x4e4f534a)+j+struct.pack('<II',len(data),0x004e4942)+data
p=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets/timer-ring.glb'
p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(out)
