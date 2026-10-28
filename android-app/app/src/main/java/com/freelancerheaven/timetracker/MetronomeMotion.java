package com.freelancerheaven.timetracker;

/** Monotonic-time motion, independent of frame rate and timer accounting. */
final class MetronomeMotion {
    private boolean active;
    private long changed;
    private float stopAngle;
    void setActive(boolean value,long now) {
        if(value==active)return;
        stopAngle=angle(now); active=value; changed=now;
    }
    float angle(long now) {
        double seconds=Math.max(0,(now-changed)/1e9);
        if(active) {
            double t=Math.min(1,seconds/.45); double envelope=t*t*(3-2*t);
            return (float)(18*envelope*Math.sin(2*Math.PI*seconds/1.2));
        }
        double t=Math.min(1,seconds/.6);
        return (float)(stopAngle*(1-t*t*(3-2*t)));
    }
    boolean animating(long now){return active || now-changed<600000000L && Math.abs(stopAngle)>.001f;}
}
