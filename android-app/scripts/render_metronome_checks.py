import bpy, pathlib, math
ROOT=pathlib.Path(__file__).resolve().parents[1]
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'art/metronome.blend'))
scene=bpy.context.scene;scene.cycles.samples=24;scene.cycles.use_denoising=False
for label,degrees in [('left',-18),('right',18)]:
    bpy.data.objects['PendulumPivot'].rotation_euler[1]=math.radians(degrees)
    scene.render.filepath=str(ROOT/'build'/('metronome-'+label+'.png'))
    bpy.ops.render.render(write_still=True)
