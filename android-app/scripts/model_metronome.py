"""Blender source, GLB and fallback render for the reference metronome."""
import bpy, math, pathlib
from mathutils import Vector

ROOT=pathlib.Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
SOURCE=ROOT/'art'; SOURCE.mkdir(exist_ok=True)
bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)

def material(name,color,metal,rough,emission=0):
    m=bpy.data.materials.new(name); m.use_nodes=True
    p=m.node_tree.nodes.get('Principled BSDF')
    p.inputs['Base Color'].default_value=(*color,1)
    p.inputs['Metallic'].default_value=metal; p.inputs['Roughness'].default_value=rough
    if emission:
        p.inputs['Emission Color'].default_value=(*color,1); p.inputs['Emission Strength'].default_value=emission
    return m
navy=material('Satin navy',(.008,.025,.052),.38,.27)
silver=material('Brushed silver',(.62,.67,.7),.95,.23)
chrome=material('Polished steel',(.7,.75,.78),1,.18)
panel=material('Inset dial',(.002,.009,.016),.45,.16)
teal=material('Teal luminous trim',(.003,.7,.8),.15,.18,4)
rubber=material('Feet',(.012,.018,.024),0,.65)

# Fine horizontal machining lines, embedded in GLB roughness texture.
grain=bpy.data.images.new('Brushed metal roughness',width=128,height=1024)
grain.colorspace_settings.name='Non-Color'
pixels=[]
for row in range(1024):
    value=.24+.035*math.sin(row*2.1)+.012*math.sin(row*.19)
    pixels.extend([value,value,value,1]*128)
grain.pixels.foreach_set(pixels);grain.pack()
tex=silver.node_tree.nodes.new('ShaderNodeTexImage');tex.image=grain
silver.node_tree.links.new(tex.outputs['Color'],silver.node_tree.nodes['Principled BSDF'].inputs['Roughness'])

def finish(o,name,mat,bevel=.02):
    o.name=name; o.data.materials.append(mat)
    if bevel:
        mod=o.modifiers.new('Soft machined edges','BEVEL'); mod.width=bevel; mod.segments=3
        bpy.context.view_layer.objects.active=o; bpy.ops.object.modifier_apply(modifier=mod.name)
    for p in o.data.polygons: p.use_smooth=True
    mod=o.modifiers.new('Weighted normals','WEIGHTED_NORMAL')
    bpy.context.view_layer.objects.active=o; bpy.ops.object.modifier_apply(modifier=mod.name)
    uv=o.data.uv_layers.new(name='MachiningUV')
    for poly in o.data.polygons:
        for li in poly.loop_indices:
            co=o.data.vertices[o.data.loops[li].vertex_index].co
            uv.data[li].uv=((co.x if abs(poly.normal.y)>.5 else co.y)+.5,co.z/2.6)
    return o

def tapered(name,z0,z1,w0,w1,d,mat,y=0):
    v=[(-w0/2,y-d/2,z0),(w0/2,y-d/2,z0),(w0/2,y+d/2,z0),(-w0/2,y+d/2,z0),
       (-w1/2,y-d/2,z1),(w1/2,y-d/2,z1),(w1/2,y+d/2,z1),(-w1/2,y+d/2,z1)]
    mesh=bpy.data.meshes.new(name); mesh.from_pydata(v,[],[(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)])
    o=bpy.data.objects.new(name,mesh); bpy.context.collection.objects.link(o)
    return finish(o,name,mat)

def bar(name,a,b,r,mat):
    a,b=Vector(a),Vector(b); delta=b-a
    bpy.ops.mesh.primitive_cylinder_add(vertices=24,radius=r,depth=delta.length,location=(a+b)/2)
    o=bpy.context.object; o.rotation_euler=delta.to_track_quat('Z','Y').to_euler()
    return finish(o,name,mat,.006)

def sphere(name,pos,r,mat):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24,ring_count=16,radius=r,location=pos)
    return finish(bpy.context.object,name,mat,0)

tapered('MetronomeBody',.22,2.6,1.35,.48,.65,navy)
tapered('Silver base',.08,.39,1.44,1.32,.74,silver)
tapered('Front bezel',.35,2.57,1.28,.44,.055,silver,-.354)
tapered('Recessed scale',.42,2.49,1.08,.29,.025,panel,-.39)
bar('Teal centerline',(0,-.414,.42),(0,-.414,2.45),.009,teal)
for i in range(29):
    z=.52+i*.063; width=.30 if i%4==0 else .17
    bar('Graduation %02d'%i,(-width/2,-.417,z),(width/2,-.417,z),.0035,silver)
for x in (-.53,.53):
    for y in (-.25,.25): sphere('Rubber foot',(x,y,.075),.075,rubber)
bar('Base teal edge',(-.64,-.376,.105),(.64,-.376,.105),.008,teal)
bar('Left teal edge',(-.64,-.30,.12),(-.64,.29,.12),.007,teal)
pivot=Vector((0,-.58,.48))
sphere('PivotHousing',pivot,.105,chrome)
arm=bpy.data.objects.new('PendulumPivot',None); bpy.context.collection.objects.link(arm);arm.location=pivot
rod=bar('PendulumArm',pivot+Vector((0,0,.03)),pivot+Vector((0,0,1.72)),.019,chrome)
bob=sphere('Weight',pivot+Vector((0,0,1.72)),.13,chrome)
for o in (rod,bob):
    world=o.matrix_world.copy(); o.parent=arm; o.matrix_world=world

# Export only product geometry. GLTF converts Z-up to Android's Y-up convention.
bpy.ops.object.select_all(action='DESELECT')
for o in bpy.context.scene.objects: o.select_set(True)
bpy.ops.export_scene.gltf(filepath=str(ASSETS/'timer-metronome.glb'),export_format='GLB',use_selection=True,export_yup=True)

# Studio source and static image: same model and three-quarter view.
world=bpy.context.scene.world; world.use_nodes=True
world.node_tree.nodes['Background'].inputs[0].default_value=(.4,.5,.6,1)
world.node_tree.nodes['Background'].inputs[1].default_value=.06
def area(name,pos,power,size):
    bpy.ops.object.light_add(type='AREA',location=pos);o=bpy.context.object;o.name=name;o.data.energy=power;o.data.shape='RECTANGLE';o.data.size=size;o.data.size_y=size*2
    o.rotation_euler=(Vector((0,0,1.3))-o.location).to_track_quat('-Z','Y').to_euler()
area('Key softbox',(-3,-4,5),360,2.4)
area('Fill softbox',(3,-2,3),180,1.2)
area('Edge softbox',(-1,3,4),280,1.8)
bpy.ops.object.camera_add(location=(-2.25,-4.35,1.95));camera=bpy.context.object
camera.rotation_euler=(Vector((0,0,1.3))-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.lens=50
scene=bpy.context.scene; scene.camera=camera;scene.render.engine='CYCLES';scene.cycles.samples=128;scene.cycles.use_denoising=False
scene.render.resolution_x=900;scene.render.resolution_y=900;scene.render.resolution_percentage=100
scene.render.film_transparent=True;scene.view_settings.view_transform='AgX'
scene.use_nodes=True
nt=scene.node_tree;nt.nodes.clear()
layers=nt.nodes.new('CompositorNodeRLayers');glow=nt.nodes.new('CompositorNodeGlare');glow.glare_type='FOG_GLOW';glow.quality='HIGH';glow.threshold=2.5;glow.size=7
output=nt.nodes.new('CompositorNodeComposite');nt.links.new(layers.outputs['Image'],glow.inputs['Image']);nt.links.new(glow.outputs['Image'],output.inputs['Image'])
bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE/'metronome.blend'))
scene.render.filepath=str(ASSETS/'metronome-static.png');bpy.ops.render.render(write_still=True)
# Presentation is rendered from the exact same scene; the neutral floor adds contact shadow.
floor=material('Studio floor',(.32,.38,.43),0,.45)
bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.005));bpy.context.object.data.materials.append(floor)
scene.render.film_transparent=False
arm.rotation_euler[1]=math.radians(18)
scene.render.filepath=str(ROOT/'build/metronome-polished.png');bpy.ops.render.render(write_still=True)
