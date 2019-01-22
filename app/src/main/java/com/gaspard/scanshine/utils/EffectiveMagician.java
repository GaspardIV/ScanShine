package com.gaspard.scanshine.utils;

public class EffectiveMagician {

    static {
        System.loadLibrary("magic-lib");
    }

    public static native void realMagic(long addrMat);

    public static native void realMagicBW(long addrMat);
}
