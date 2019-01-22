package com.gaspard.scanshine.utils;

import android.hardware.Camera;

import java.util.List;

public class BestPreviewSizeTool {

    private static final double MAX_ASPECT_DISTORTION = 0.15;
    private static final float ASPECT_RATIO_TOLERANCE = 0.01f;

    //desiredWidth and desiredHeight can be the screen size of mobile device
    public static SizePair generateValidPreviewSize(Camera.Parameters parameters, int desiredWidth,
                                                    int desiredHeight) {
        double screenAspectRatio = desiredWidth / (double) desiredHeight;
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        List<Camera.Size> supportedPictureSizes = parameters.getSupportedPictureSizes();
        SizePair bestPair = null;
        double currentMinDistortion = MAX_ASPECT_DISTORTION;
        for (Camera.Size previewSize : supportedPreviewSizes) {
            float previewAspectRatio = (float) previewSize.width / (float) previewSize.height;
            for (Camera.Size pictureSize : supportedPictureSizes) {
                float pictureAspectRatio = (float) pictureSize.width / (float) pictureSize.height;
                if (Math.abs(previewAspectRatio - pictureAspectRatio) < ASPECT_RATIO_TOLERANCE) {
                    SizePair sizePair = new SizePair(previewSize, pictureSize);
                    double aspectRatio = previewSize.width / (double) previewSize.height;
                    double distortion = Math.abs(aspectRatio - screenAspectRatio);
                    if (distortion < currentMinDistortion) {
                        currentMinDistortion = distortion;
                        bestPair = sizePair;
                    }
                    break;
                }
            }
        }
        return bestPair;
    }

    public static class SizePair {
        private Camera.Size previewSize;

        private Camera.Size pictureSize;

        public SizePair(Camera.Size previewSize, Camera.Size pictureSize) {
            this.previewSize = previewSize;
            this.pictureSize = pictureSize;
        }

        public Camera.Size getPreviewSize() {
            return previewSize;
        }

        public void setPreviewSize(Camera.Size previewSize) {
            this.previewSize = previewSize;
        }

        public Camera.Size getPictureSize() {
            return pictureSize;
        }

        public void setPictureSize(Camera.Size pictureSize) {
            this.pictureSize = pictureSize;
        }

    }
}
