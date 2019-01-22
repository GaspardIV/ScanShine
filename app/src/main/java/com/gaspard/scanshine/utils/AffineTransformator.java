package com.gaspard.scanshine.utils;

import android.graphics.Point;
//import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.Vector;

import static java.lang.Math.max;
import static org.opencv.imgproc.Imgproc.getPerspectiveTransform;
import static org.opencv.imgproc.Imgproc.warpPerspective;


public class AffineTransformator {
    private static final double ISO_STANDARD_RATIO = 1.41421356237;
    private static final String TAG = "AffineTransformator";


    private static Mat ratioSizeTransform(Mat matSrc, Vector<Point> sheetCoords, double ratio) {
        Vector<org.opencv.core.Point> ordered = orderPoints(sheetCoords);

        // src prepare
        MatOfPoint2f srcPoints = new MatOfPoint2f(
                ordered.get(0),
                ordered.get(1),
                ordered.get(2),
                ordered.get(3));
        // src prepare end

        // dst prepare
        double wa = distance(ordered.get(2), ordered.get(3));
        double wb = distance(ordered.get(1), ordered.get(0));
        int dstWidth = (int) max(wa, wb);

        double ha = distance(ordered.get(1), ordered.get(2));
        double hb = distance(ordered.get(0), ordered.get(3));
        int dstHeight = (int) max(ha, hb);

        if (ratio > 0 ) { // if ratio specified - else autostraightened
            if (dstHeight > dstWidth) { //
                dstWidth = (int) (dstHeight/ratio);
            } else {
                dstHeight = (int) (dstWidth/ratio);
            }
        }

        MatOfPoint2f dstPoints = new MatOfPoint2f(
                new org.opencv.core.Point(0, 0),
                new org.opencv.core.Point(dstWidth - 1, 0),
                new org.opencv.core.Point(dstWidth - 1, dstHeight - 1),
                new org.opencv.core.Point(0, dstHeight - 1)
        );
        Mat matDst = new Mat(dstHeight, dstWidth, CvType.CV_8UC4);
        // dst prepare end

        Mat transformMatrix = getPerspectiveTransform(srcPoints, dstPoints);
        warpPerspective(matSrc, matDst, transformMatrix, matDst.size());

        return matDst;
    }

    public static Mat resize(Mat src, int dstWidth, int dstHeight) {
        Mat res = new Mat();
        Imgproc.resize(src, res, new Size(dstWidth, dstHeight));
        return res;
    }

    public static Mat autostraightenTransform(Mat picture, Vector<Point> sheetCoords) {
        return ratioSizeTransform(picture, sheetCoords,0);
    }

    public static Mat iso216ratioTransform(Mat picture, Vector<Point> sheetCoords) {
        return ratioSizeTransform(picture, sheetCoords,ISO_STANDARD_RATIO);
    }


    private static double distance(org.opencv.core.Point p1, org.opencv.core.Point p2) {
        return Math.sqrt(((p1.x - p2.x) * (p1.x - p2.x)) +
                ((p1.y - p2.y) * (p1.y - p2.y)));
    }

    private static int argmin(int[] arr) {
        if (arr == null || arr.length <= 0) {
            return -1;
        }
        int argmin = 0;
        int min = arr[0];
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] < min) {
                argmin = i;
                min = arr[i];
            }
        }
        return argmin;
    }

    private static int argmax(int[] arr) {
        if (arr == null || arr.length <= 0) {
            return -1;
        }
        int argmax = 0;
        int max = arr[0];
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] > max) {
                argmax = i;
                max = arr[i];
            }
        }
        return argmax;
    }


    /**
     * # the first entry in the list is the top-left,
     * # the second entry is the top-right, the third is the
     * # bottom-right, and the fourth is the bottom-left
     */
    private static Vector<org.opencv.core.Point> orderPoints(Vector<Point> sheetShitCoords) {
        Vector<org.opencv.core.Point> result = new Vector<>(4);
        if (sheetShitCoords.size() == 4) {
            int[] sums = new int[4];
            int[] difs = new int[4];
            for (int i = 0; i < 4; i++) {
                sums[i] = sheetShitCoords.get(i).x + sheetShitCoords.get(i).y;
                difs[i] = sheetShitCoords.get(i).x - sheetShitCoords.get(i).y;
            }
            Point p1 = sheetShitCoords.get(argmin(sums));
            Point p2 = sheetShitCoords.get(argmax(difs));
            Point p3 = sheetShitCoords.get(argmax(sums));
            Point p4 = sheetShitCoords.get(argmin(difs));

            result.add(0, new org.opencv.core.Point(p1.x, p1.y)); // top left
            result.add(1, new org.opencv.core.Point(p2.x, p2.y)); // top right
            result.add(2, new org.opencv.core.Point(p3.x, p3.y)); // bottom right
            result.add(3, new org.opencv.core.Point(p4.x, p4.y)); // bottom left

        } else {
//            Log.d(TAG, "orderPoints: ORDER LESS/MORE THAN 4 POINTS.");
            result = null;
        }
        // TODO this method can be better
        return result;
    }

    public static void orderPointsInPlace(Vector<Point> sheetShitCoords) {
        if (sheetShitCoords.size() == 4) {
            int[] sums = new int[4];
            int[] difs = new int[4];
            for (int i = 0; i < 4; i++) {
                sums[i] = sheetShitCoords.get(i).x + sheetShitCoords.get(i).y;
                difs[i] = sheetShitCoords.get(i).x - sheetShitCoords.get(i).y;
            }
            Point p1 = sheetShitCoords.get(argmin(sums));
            Point p2 = sheetShitCoords.get(argmax(difs));
            Point p3 = sheetShitCoords.get(argmax(sums));
            Point p4 = sheetShitCoords.get(argmin(difs));

            sheetShitCoords.set(0, p1);
            sheetShitCoords.set(1, p2);
            sheetShitCoords.set(2, p3);
            sheetShitCoords.set(3, p4);
        } else {
//            Log.d(TAG, "orderPoints: ORDER LESS/MORE THAN 4 POINTS.");
        }
    }


    public static double computeScaleRatio(int src, int dst) {
        return (double) src / (double) dst;
    }
}
