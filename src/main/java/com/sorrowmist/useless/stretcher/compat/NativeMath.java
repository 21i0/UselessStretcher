package com.sorrowmist.useless.stretcher.compat;
public final class NativeMath {
    private NativeMath() {}
    public static int clamp(int v,int lo,int hi) { return Math.max(lo,Math.min(hi,v)); }
    public static int clamp(long v,int lo,int hi) { return (int)Math.max(lo,Math.min(hi,v)); }
    public static long clamp(long v,long lo,long hi) { return Math.max(lo,Math.min(hi,v)); }
    public static float clamp(float v,float lo,float hi) { return Math.max(lo,Math.min(hi,v)); }
    public static double clamp(double v,double lo,double hi) { return Math.max(lo,Math.min(hi,v)); }
}
