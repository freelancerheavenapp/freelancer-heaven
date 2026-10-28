package com.freelancerheaven.timetracker;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.io.*;
/** Static pendulum silhouette kept available even if the native renderer fails. */
final class RingFallback extends View {
    private final Paint paint=new Paint(3);
    private final Paint shadow=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap bitmap;
    private boolean modelVisible=true;
    RingFallback(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        try(InputStream in=c.getAssets().open("metronome-static.png")){bitmap=BitmapFactory.decodeStream(in);}catch(IOException ignored){}
    }
    // The shadow remains visible underneath the transparent 3D surface.
    void setModelVisible(boolean visible){modelVisible=visible;invalidate();}
    @Override protected void onDraw(Canvas c){
        float size=Math.min(getWidth(),getHeight());
        if(size<=0)return;
        float centerX=getWidth()/2f, centerY=getHeight()*.91f;
        float radius=size*.30f;
        shadow.setShader(new RadialGradient(0,0,radius,
            new int[]{Color.argb(32,31,58,76),Color.argb(12,31,58,76),Color.TRANSPARENT},
            new float[]{0f,.55f,1f},Shader.TileMode.CLAMP));
        c.save();c.translate(centerX,centerY);c.scale(1f,.17f);c.drawCircle(0,0,radius,shadow);c.restore();
        if(bitmap!=null && modelVisible){
            float scaled=size*1.04f;
            c.drawBitmap(bitmap,null,new RectF(centerX-scaled/2,(getHeight()-scaled)/2,centerX+scaled/2,(getHeight()+scaled)/2),paint);
        }
    }
}
