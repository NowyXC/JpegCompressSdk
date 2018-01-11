package com.nowy.commonlib;

import android.graphics.Bitmap;

/**
 * Created by Nowy on 2018/1/10.
 */

public class JpegUtil {
    static {
        System.loadLibrary("compress");
        System.loadLibrary("jpeg");
    }

    private JpegUtil() {
    }


    public static native boolean compressBitmap(Bitmap bit, int w, int h, String fineName,int quality,
                                                boolean optimize);
}
