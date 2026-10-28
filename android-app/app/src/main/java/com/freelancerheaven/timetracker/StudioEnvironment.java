package com.freelancerheaven.timetracker;
import com.google.android.filament.*;
import java.nio.*;

/** Local studio softboxes, specular-prefiltered by Filament for PBR roughness. */
final class StudioEnvironment {
    static Texture create(Engine engine) {
        int size=64; ByteBuffer pixels=ByteBuffer.allocateDirect(size*size*6*3*4).order(ByteOrder.nativeOrder());
        int[] offsets=new int[6];
        for(int face=0;face<6;face++) {
            offsets[face]=pixels.position();
            for(int y=0;y<size;y++) for(int x=0;x<size;x++) {
                float u=2f*(x+.5f)/size-1,v=2f*(y+.5f)/size-1;
                float box=(float)Math.exp(-Math.pow(u/.22f,8)-Math.pow(v/.8f,8));
                float intensity=.055f+box*(face==2?1.2f:face==0||face==4?1.1f:.4f);
                pixels.putFloat(intensity*.92f).putFloat(intensity*.97f).putFloat(intensity);
            }
        }
        pixels.flip();
        Texture texture=new Texture.Builder().width(size).height(size).levels(7).sampler(Texture.Sampler.SAMPLER_CUBEMAP).format(Texture.InternalFormat.R11F_G11F_B10F).build(engine);
        texture.generatePrefilterMipmap(engine,new Texture.PixelBufferDescriptor(pixels,Texture.Format.RGB,Texture.Type.FLOAT),offsets,new Texture.PrefilterOptions());
        return texture;
    }
}
