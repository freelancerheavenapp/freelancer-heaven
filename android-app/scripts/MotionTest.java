package com.freelancerheaven.timetracker;
public class MotionTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        MetronomeMotion m=new MetronomeMotion();long start=1000000000L;
        check(m.angle(start)==0);m.setActive(true,start);check(m.angle(start)==0);
        for(int i=0;i<15000;i++)check(Math.abs(m.angle(start+i*1000000L))<=18.001);
        check(Math.abs(m.angle(start+1500000000L)-18)<.001);
        long stop=start+1500000000L;m.setActive(false,stop);
        check(Math.abs(m.angle(stop)-18)<.001);
        check(m.angle(stop+600000000L)==0);check(!m.animating(stop+600000000L));
        m.setActive(true,stop+800000000L);check(m.angle(stop+800000000L)==0);
        System.out.println("Motion bounds, start continuity, exact stop and restart verified");
    }
}
