package com.freelancerheaven.timetracker;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.Matrix;
import android.view.*;
import com.google.android.filament.*;
import com.google.android.filament.gltfio.*;
import java.nio.*;
import java.io.*;

/** One local scene, UI-thread ownership, 30fps only while visible and active. */
final class TimerScene extends TextureView implements TextureView.SurfaceTextureListener, Choreographer.FrameCallback {
    private Engine engine; private Renderer renderer; private Scene scene;
    private com.google.android.filament.View view; private Camera camera;
    private SwapChain swap; private Surface surface; private int cameraEntity, light;
    private AssetLoader assets; private ResourceLoader resources; private UbershaderProvider materials;
    private FilamentAsset asset; private boolean resumed, running, reduced, scheduled, failed;
    private int pendulumArm;
    private long last;
    private final MetronomeMotion motion=new MetronomeMotion();
    private final float[] rest=new float[16], rotation=new float[16];
    private IndirectLight ambient;
    private Texture environment;
    private int fillLight;
    private final float[] matrix=new float[16];
    TimerScene(Context context) { super(context); setOpaque(false); setSurfaceTextureListener(this); setContentDescription("Decorative 3D timer"); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    void state(boolean active, boolean reduce) { running=active; reduced=reduce; motion.setActive(active && !reduce,System.nanoTime()); requestFrame(); }
    void resumeScene() { resumed=true; requestFrame(); }
    void pauseScene() { resumed=false; Choreographer.getInstance().removeFrameCallback(this); scheduled=false; last=0; }
    private void requestFrame() { if(!scheduled && resumed && !failed && engine!=null && swap!=null) { scheduled=true; Choreographer.getInstance().postFrameCallback(this); } }
    public void onSurfaceTextureAvailable(SurfaceTexture texture,int w,int h) {
        try {
            Filament.init(); Gltfio.init();
            engine=Engine.create(); renderer=engine.createRenderer(); scene=engine.createScene(); view=engine.createView();
            cameraEntity=EntityManager.get().create(); camera=engine.createCamera(cameraEntity);
            camera.setExposure(16f,1f/125f,100f);
            view.setCamera(camera); view.setScene(scene); view.setBlendMode(com.google.android.filament.View.BlendMode.TRANSLUCENT);
            com.google.android.filament.View.BloomOptions bloom=new com.google.android.filament.View.BloomOptions();
            bloom.enabled=true;bloom.strength=.035f;bloom.threshold=true;bloom.resolution=256;
            view.setBloomOptions(bloom);
            Renderer.ClearOptions clear=new Renderer.ClearOptions(); clear.clear=true; clear.clearColor=new float[]{0,0,0,0}; renderer.setClearOptions(clear);
            light=EntityManager.get().create();
            new LightManager.Builder(LightManager.Type.DIRECTIONAL).color(1,.98f,.95f).intensity(65000).direction(-.5f,-.7f,-1).build(engine,light); scene.addEntity(light);
            fillLight=EntityManager.get().create();
            new LightManager.Builder(LightManager.Type.DIRECTIONAL).color(.8f,.93f,1).intensity(16000).direction(.6f,-.2f,-1).build(engine,fillLight);scene.addEntity(fillLight);
            environment=StudioEnvironment.create(engine);
            ambient=new IndirectLight.Builder().reflections(environment).irradiance(1,new float[]{.3f,.35f,.4f}).intensity(18000).build(engine);
            scene.setIndirectLight(ambient);
            materials=new UbershaderProvider(engine); assets=new AssetLoader(engine,materials,EntityManager.get()); resources=new ResourceLoader(engine);
            InputStream in=getContext().getAssets().open("timer-metronome.glb"); ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] bytes=new byte[8192]; int count;
            try { while((count=in.read(bytes))!=-1) out.write(bytes,0,count); } finally { in.close(); }
            byte[] model=out.toByteArray(); ByteBuffer buffer=ByteBuffer.allocateDirect(model.length).order(ByteOrder.nativeOrder()); buffer.put(model).flip();
            asset=assets.createAsset(buffer); if(asset==null) throw new IOException("Invalid local model");
            resources.loadResources(asset); scene.addEntities(asset.getEntities()); asset.releaseSourceData();
            pendulumArm=asset.getFirstEntityByName("PendulumPivot"); if(pendulumArm==0) throw new IOException("Pendulum pivot missing");
            engine.getTransformManager().getTransform(engine.getTransformManager().getInstance(pendulumArm),rest);
            surface=new Surface(texture); swap=engine.createSwapChain(surface); onSurfaceTextureSizeChanged(texture,w,h); showFallbackModel(false); requestFrame();
        } catch(Exception | LinkageError failure) { failed=true; release(); showFallbackModel(true); setVisibility(GONE); }
    }
    private void showFallbackModel(boolean visible) {
        if(getParent() instanceof ViewGroup) {
            ViewGroup parent=(ViewGroup)getParent();
            if(parent.getChildCount()>0 && parent.getChildAt(0) instanceof RingFallback)
                ((RingFallback)parent.getChildAt(0)).setModelVisible(visible);
        }
    }
    public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h) {
        if(engine==null || h==0)return;
        view.setViewport(new Viewport(0,0,w,h)); camera.setProjection(33.5,(double)w/h,.1,20,Camera.Fov.VERTICAL); camera.lookAt(-2.25,1.95,4.35,0,1.3,0,0,1,0); requestFrame();
    }
    public void onSurfaceTextureUpdated(SurfaceTexture t) {}
    public boolean onSurfaceTextureDestroyed(SurfaceTexture t) { release(); showFallbackModel(true); return true; }
    public void doFrame(long time) {
        scheduled=false;
        if(!resumed || engine==null || swap==null)return;
        android.graphics.Rect rect=new android.graphics.Rect();
        boolean visible=getGlobalVisibleRect(rect);
        if(visible && (last==0 || time-last>=33000000L)) {
            last=time;
            Matrix.setIdentityM(rotation,0); Matrix.rotateM(rotation,0,reduced?0:motion.angle(time),0,0,1);
            Matrix.multiplyMM(matrix,0,rest,0,rotation,0);
            engine.getTransformManager().setTransform(engine.getTransformManager().getInstance(pendulumArm),matrix);
            if(renderer.beginFrame(swap,time)) { renderer.render(view); renderer.endFrame(); }
        }
        if(visible && !reduced && motion.animating(time))requestFrame();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); if(visibility==VISIBLE)requestFrame(); }
    void release() {
        boolean hostResumed=resumed;
        pauseScene();
        if(engine==null){resumed=hostResumed;return;}
        if(swap!=null){engine.destroySwapChain(swap);swap=null;}
        engine.flushAndWait();
        if(surface!=null){surface.release();surface=null;}
        if(asset!=null){assets.destroyAsset(asset);asset=null;}
        if(resources!=null)resources.destroy();
        if(assets!=null)assets.destroy();
        if(materials!=null){materials.destroyMaterials();materials.destroy();}
        if(light!=0){engine.destroyEntity(light); EntityManager.get().destroy(light); light=0;}
        if(fillLight!=0){engine.destroyEntity(fillLight);EntityManager.get().destroy(fillLight);fillLight=0;}
        if(ambient!=null){scene.setIndirectLight(null);engine.destroyIndirectLight(ambient);ambient=null;}
        if(environment!=null){engine.destroyTexture(environment);environment=null;}
        if(view!=null)engine.destroyView(view); if(scene!=null)engine.destroyScene(scene); if(renderer!=null)engine.destroyRenderer(renderer);
        if(cameraEntity!=0){engine.destroyCameraComponent(cameraEntity); EntityManager.get().destroy(cameraEntity);cameraEntity=0;}
        engine.destroy();engine=null;resumed=hostResumed;
    }
}
