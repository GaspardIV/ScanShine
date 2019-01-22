package com.gaspard.scanshine.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;

public class ExcelentRotator {
    public static void scaleCoords(int[] sheetXCoords, int[] sheetYCoords, int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        final float wScale = (float) dstWidth / srcWidth;
        final float hScale = (float) dstHeight / srcHeight;
        for (int i = 0; i < sheetXCoords.length /* == sheetYCoords.length*/ ; i++) {
            sheetXCoords[i] = (int) (wScale * sheetXCoords[i]);
            sheetYCoords[i] = (int) (hScale * sheetYCoords[i]);
        }
    }

    public static void rotate90(int[] sheetCoords, int srcWidth, int srcHeight) {
        final float wScale = (float) srcWidth / srcHeight;
        final float hScale = (float) srcHeight / srcWidth;
        for (int i = 0; i < sheetCoords.length/2; i++) {
            int tmp = sheetCoords[2*i];
            sheetCoords[2*i] = (int) (wScale * (srcHeight - 1 - sheetCoords[2*i + 1]));
            sheetCoords[2*i+1] = (int) (hScale * (tmp));
        }
    }

    public static Bitmap rotateBitmap(Bitmap source, int angle) {
        if (angle != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(angle);
            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        } else  {
            return source;
        }
    }
}
