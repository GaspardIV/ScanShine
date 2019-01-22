package com.gaspard.scanshine.utils;

import android.graphics.Rect;

public class FocusAreaSpecialist {
    private static final float FOCUS_AREA_FULL_SIZE = 2000f;

    public static Rect convert(Rect rect, int width, int height) {
        Rect result = new Rect();
        float focusKoefW = width / FOCUS_AREA_FULL_SIZE;
        float focusKoefH = height / FOCUS_AREA_FULL_SIZE;
        result.top = normalize(rect.top / focusKoefH - FOCUS_AREA_FULL_SIZE / 2);
        result.left = normalize(rect.left / focusKoefW - FOCUS_AREA_FULL_SIZE / 2);
        result.right = normalize(rect.right / focusKoefW - FOCUS_AREA_FULL_SIZE / 2);
        result.bottom = normalize(rect.bottom / focusKoefH - FOCUS_AREA_FULL_SIZE / 2);
        return result;
    }

    private static int normalize(float value) {
        if (value > FOCUS_AREA_FULL_SIZE / 2) {
            return (int) (FOCUS_AREA_FULL_SIZE / 2);
        }
        if (value < -FOCUS_AREA_FULL_SIZE / 2) {
            return (int) (-FOCUS_AREA_FULL_SIZE / 2);
        }
        return Math.round(value);
    }

    public static Rect getBoundingBox(int[] sheetXCoords, int[] sheetYCoords) {
        int minX, maxX;
        int minY, maxY;
        minX = maxX = sheetXCoords[0];
        minY = maxY = sheetYCoords[0];
        for (int sheetXCoord : sheetXCoords) {
            minX = Math.min(minX, sheetXCoord);
            maxX = Math.max(maxX, sheetXCoord);
        }
        for (int sheetYCoord : sheetYCoords) {
            minY = Math.min(minY, sheetYCoord);
            maxY = Math.max(maxY, sheetYCoord);
        }
        return new Rect(minX, minY, maxX, maxY);
    }
}
