package com.gaspard.scanshine.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.hardware.Camera;
import android.os.AsyncTask;
//import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.ImageView;

import com.gaspard.scanshine.R;
import com.gaspard.scanshine.utils.BestPreviewSizeTool;
import com.gaspard.scanshine.utils.ExcelentRotator;

import java.util.Arrays;
import java.util.List;

import static android.content.Context.WINDOW_SERVICE;


@SuppressWarnings("deprecation")
@SuppressLint("ViewConstructor")
public class CameraView extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private static final String TAG = "CameraPreview";
    private static final int ANGLE_BETWEEN_ROTATION_STATES = 90;
    private static final float STROKE_WIDTH = 5.0f;
    private static final int FULL_ANGLE = 360;

    static {
        System.loadLibrary("native-lib");
    }

    private final int[] sheetXCoords = new int[4];
    private final int[] sheetYCoords = new int[4];

    private Camera mCamera;
    private int previewFrameWidth = 0;
    private int previewFrameHeight = 0;
    private Canvas canvas;
    private Paint paint;

    private ImageView canvasFrame;

    private int canvasFrameHeight = 0;
    private int canvasFrameWidth = 0;
    private boolean dismissLaterFrames = false;


    private boolean processFrame = true;
    private int correctCameraOrientation = 0;

    public CameraView(Context context, Camera mCamera, ImageView canvasFrame) {
        super(context);
        this.mCamera = mCamera;
        this.canvasFrame = canvasFrame;

        initHolderAndSurfaceCallback();
    }

    @SuppressWarnings("JniMissingFunction")
    private static native void decode(byte[] yuv420sp, int width, int height, int[] arr);


    private void initHolderAndSurfaceCallback() {
        SurfaceHolder holder = getHolder();
        if (holder != null) {
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            holder.setKeepScreenOn(true);
        }
    }

    public int getPreviewFrameHeight() {
        return previewFrameHeight;
    }

    public int getPreviewFrameWidth() {
        return previewFrameWidth;
    }

    public int[] getSheetXCoords() {
        return Arrays.copyOf(sheetXCoords, sheetXCoords.length);
    }

    public int[] getSheetYCoords() {
        return Arrays.copyOf(sheetYCoords, sheetYCoords.length);
    }

    public void setDetectingPaperSheet(boolean dismissLaterFrames) {
        this.dismissLaterFrames = dismissLaterFrames;
    }

    public void stopCameraPreview() {
        try {
            if (mCamera != null) {
                mCamera.stopPreview();
            }
            if (canvas != null && canvasFrame != null) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                canvasFrame.draw(canvas);
                canvasFrame.invalidate();
            }
        } catch (Exception e) {
//            Log.i(TAG, "stopCameraPreview(): tried to stop a non-running preview, this is not an error");
        }
    }

    private void initPreview() {
        try {
            SurfaceHolder holder = getHolder();
            if (mCamera != null && holder.getSurface() != null) {
                previewFrameWidth = mCamera.getParameters().getPreviewSize().width;
                previewFrameHeight = mCamera.getParameters().getPreviewSize().height;

                mCamera.setPreviewDisplay(holder);

                setUpPreviewSizeAndPictureSizeAndDisplayOrientation(previewFrameWidth, previewFrameHeight);

                Camera.Parameters cameraParams = mCamera.getParameters();
                cameraParams.setPreviewFormat(ImageFormat.NV21);
                mCamera.setParameters(cameraParams);

                mCamera.startPreview();
                mCamera.setPreviewCallback(this);

            }
            dismissLaterFrames = false;
        } catch (Exception e) {
//            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    private void setFocus() {
        Camera.Parameters params = mCamera.getParameters();
        List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        mCamera.setParameters(params);
    }

    private void setBestPictureAndPreviewSize(int width, int height) {
        Camera.Parameters parameters = mCamera.getParameters();
        BestPreviewSizeTool.SizePair calculatedPair = BestPreviewSizeTool.generateValidPreviewSize(parameters, width, height);
        if (calculatedPair != null) {
            parameters.setPictureSize(calculatedPair.getPictureSize().width, calculatedPair.getPictureSize().height);
            parameters.setPreviewSize(calculatedPair.getPreviewSize().width, calculatedPair.getPreviewSize().height);
            mCamera.setParameters(parameters);
        }
    }

    private void updateCorrectCameraOrientation() {
        int rotation = 0, degrees, result;

        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(0, info);

        WindowManager windowManager = (WindowManager) getContext().getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            Display display = windowManager.getDefaultDisplay();
            if (display != null) rotation = display.getRotation();
        }

        degrees = rotation * ANGLE_BETWEEN_ROTATION_STATES;

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % FULL_ANGLE;
            result = (FULL_ANGLE - result) % FULL_ANGLE;
        } else {
            result = (info.orientation - degrees + FULL_ANGLE) % FULL_ANGLE;
        }
        correctCameraOrientation = result;
    }

    private void setUpPreviewSizeAndPictureSizeAndDisplayOrientation(int w, int h) {
        mCamera.setDisplayOrientation(correctCameraOrientation);
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setRotation(correctCameraOrientation);
        mCamera.setParameters(parameters);
        setBestPictureAndPreviewSize(w, h);
    }


    private void initDrawingTools(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);

        canvasFrameWidth = width;
        canvasFrameHeight = height;
        canvas = new Canvas(bitmap);

        canvasFrame.setScaleType(ImageView.ScaleType.FIT_XY);
        canvasFrame.setImageBitmap(bitmap);

        paint = new Paint();
        paint.setColor(getResources().getColor(R.color.shapeColor));
        paint.setStrokeWidth(STROKE_WIDTH);

    }

    private void drawLinesWithScale(int srcWidth, int srcHeight, int dstWidth, int dstHeight, int[] arr) {
        final float wScale = (float) dstWidth / srcWidth; // bcs rotate also
        final float hScale = (float) dstHeight / srcHeight; // bcs rotate also

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        canvas.drawLine((int) (wScale * arr[0]), (int) (hScale * arr[1]), (int) (wScale * (arr[2])), (int) (hScale * arr[3]), paint);
        canvas.drawLine((int) (wScale * arr[2]), (int) (hScale * arr[3]), (int) (wScale * (arr[4])), (int) (hScale * arr[5]), paint);
        canvas.drawLine((int) (wScale * arr[4]), (int) (hScale * arr[5]), (int) (wScale * (arr[6])), (int) (hScale * arr[7]), paint);
        canvas.drawLine((int) (wScale * arr[6]), (int) (hScale * arr[7]), (int) (wScale * (arr[0])), (int) (hScale * arr[1]), paint);

        canvasFrame.draw(canvas);
        canvasFrame.invalidate();
    }


    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (processFrame) {
            processFrame = false;
            new PreviewFrameAsyncTask(bytes).execute();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // wait until surfaceChanged()
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopCameraPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (holder.getSurface() == null) {
            return; // preview surface does not exist
        }

        stopCameraPreview();

        updateCorrectCameraOrientation();
        initPreview();
        initDrawingTools(w, h);
        setFocus();
    }

    @SuppressLint("StaticFieldLeak")
    private class PreviewFrameAsyncTask extends AsyncTask<Void, Void, Void> {
        private final byte[] bytes;
        private final int[] arr = new int[8];

        PreviewFrameAsyncTask(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        protected Void doInBackground(Void... unused) {
            decode(bytes, previewFrameWidth, previewFrameHeight, arr);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (!dismissLaterFrames) {
                for (int i = 0; i < correctCameraOrientation / ANGLE_BETWEEN_ROTATION_STATES; i++) {
                    ExcelentRotator.rotate90(arr, previewFrameWidth, previewFrameHeight);
                }

                sheetXCoords[0] = arr[0];
                sheetYCoords[0] = arr[1];
                sheetXCoords[1] = arr[2];
                sheetYCoords[1] = arr[3];
                sheetXCoords[2] = arr[4];
                sheetYCoords[2] = arr[5];
                sheetXCoords[3] = arr[6];
                sheetYCoords[3] = arr[7];
                drawLinesWithScale(previewFrameWidth, previewFrameHeight, canvasFrameWidth, canvasFrameHeight, arr);
                processFrame = true;
            }
        }
    }
}