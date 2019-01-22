package com.gaspard.scanshine;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
//import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.gaspard.scanshine.utils.ExcelentRotator;
import com.gaspard.scanshine.utils.GallerySaveExpert;
import com.gaspard.scanshine.views.CameraView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import static com.gaspard.scanshine.utils.FocusAreaSpecialist.convert;
import static com.gaspard.scanshine.utils.FocusAreaSpecialist.getBoundingBox;

@SuppressWarnings("DanglingJavadoc")
public class CameraActivity extends AppCompatActivity implements Camera.PictureCallback, Camera.AutoFocusCallback {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
    private static final int READ_PERMISSION_REQUEST_CODE = 2;
    private static final int WRITE_PERMISSION_REQUEST_CODE = 3;
    private Camera mCamera;
    private CameraView mPreview;
    private boolean isTorchOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        ((FloatingActionButton) findViewById(R.id.button_capture)).setScaleType(ImageView.ScaleType.CENTER); // android:scaleType is now broken
        checkAndAskForNextPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        new SafeCamAndPreviewStarter().execute();
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCameraAndPreview();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        checkAndAskForNextPermission();
    }

    public void onCaptureClicked(View view) {
        try {
            if (mPreview != null && mCamera != null) {
                mPreview.setDetectingPaperSheet(false);
                startRotatingCaptureFab();
                Camera.Parameters parameters = mCamera.getParameters();
                setUpFocus(parameters);
                mCamera.setParameters(parameters);
                mCamera.autoFocus(this);
            }
        } catch (Exception ignored) {
//            Log.e(getString(R.string.app_name), "failed to capture photo");
            stopRotatingCaptureFab();
            new SafeCamAndPreviewStarter().execute();
            ignored.printStackTrace();
        }
    }

    public void onTorchClick(View view) {
        if (isTorchOn) {
            isTorchOn = false;
            view.setBackground(ContextCompat.getDrawable(this, R.drawable.half_trans_button));
            Camera.Parameters params = mCamera.getParameters();
            params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            mCamera.setParameters(params);
        } else {
            isTorchOn = true;
            view.setBackground(ContextCompat.getDrawable(this, R.drawable.clicked_button));
            Camera.Parameters params = mCamera.getParameters();
            params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            mCamera.setParameters(params);
        }
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        if (success) {
            try {
                camera.takePicture(null, null, this);
            } catch (RuntimeException e) {
//                Log.e(getString(R.string.app_name), "failed to capture photo");
                stopRotatingCaptureFab();
                Toast.makeText(this, R.string.oneMoreTime, Toast.LENGTH_SHORT).show();
                new SafeCamAndPreviewStarter().execute();
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, R.string.holdStillForAMoment, Toast.LENGTH_SHORT).show();
            mCamera.cancelAutoFocus();
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            mCamera.setParameters(parameters);
            mCamera.autoFocus(this);
        }
    }

    @Override
    public void onPictureTaken(byte[] bytes, Camera camera) {
        new PhotoTakenTask(bytes).execute();
    }

    private void stopRotatingCaptureFab() {
        FloatingActionButton captureButton = findViewById(R.id.button_capture);
        if (captureButton != null) {
            captureButton.clearAnimation();
            captureButton.setEnabled(true);
        }
    }

    private void startRotatingCaptureFab() {
        FloatingActionButton captureButton = findViewById(R.id.button_capture);
        captureButton.setEnabled(false);
        RotateAnimation rotateAnimation = new RotateAnimation(0, 180,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setDuration(200);
        rotateAnimation.setRepeatCount(Animation.INFINITE);
        rotateAnimation.setRepeatMode(Animation.RESTART);
        captureButton.startAnimation(rotateAnimation);
    }

    private void checkAndAskForNextPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_PERMISSION_REQUEST_CODE);
            } else if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void releaseCameraAndPreview() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
        ((FrameLayout) findViewById(R.id.camera_preview)).removeView(mPreview);
        if (mPreview != null) {
            mPreview.getHolder().removeCallback(mPreview);
            mPreview = null;
        }
        this.findViewById(R.id.torch_button).setBackground(ContextCompat.getDrawable(this, R.drawable.half_trans_button));
    }

    private void setUpFocus(Camera.Parameters parameters) {
        Rect rArea = getBoundingBox(mPreview.getSheetXCoords(), mPreview.getSheetYCoords());
        rArea = convert(rArea, mPreview.getPreviewFrameWidth(), mPreview.getPreviewFrameHeight());
        Camera.Area area = new Camera.Area(rArea, 1000);
        if (parameters.getMaxNumFocusAreas() > 0) {
            parameters.setFocusAreas(Collections.singletonList(area));
        }

        if (parameters.getMaxNumMeteringAreas() > 0) {
            parameters.setMeteringAreas(Collections.singletonList(area));
        }
    }

    @SuppressLint("StaticFieldLeak") // can be not safe but yolo
    private class SafeCamAndPreviewStarter extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            releaseCameraAndPreview();
        }

        @Override
        protected Void doInBackground(Void... unused) {
            try {
                mCamera = Camera.open();
            } catch (Exception e) {
                this.cancel(true);
//                Log.e(getString(R.string.app_name), "failed to open Camera");
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            if (!isCancelled()) {
                ImageView canvasFrame = findViewById(R.id.camera_drawing_pane);
                mPreview = new CameraView(CameraActivity.this, mCamera, canvasFrame);
                ((FrameLayout) findViewById(R.id.camera_preview)).addView(mPreview);
                findViewById(R.id.button_capture).setVisibility(View.VISIBLE);
                findViewById(R.id.torch_button).setVisibility(View.VISIBLE);
                stopRotatingCaptureFab();
            }
        }

    }

    @SuppressLint("StaticFieldLeak") // can be not safe but yolo
    private class PhotoTakenTask extends AsyncTask<Void, Void, Void> {
        Intent intent;
        byte[] bytes;

        PhotoTakenTask(byte[] bytes) {
            this.bytes = bytes;
            intent = new Intent(CameraActivity.this, ScanEditShapeActivity.class);
        }

        @Override
        protected Void doInBackground(Void... unused) {
            Bitmap picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            int additionalRotate = samsungAndSonyDevicesRotationBugAdditionalRotateCompute(picture.getWidth(), picture.getHeight());
            picture = ExcelentRotator.rotateBitmap(picture, additionalRotate);
            preparePictureToPass(picture);
            prepareCoordsToPass(picture.getWidth(), picture.getHeight());
            return null;
        }

        @Override
        protected void onPreExecute() {
            mPreview.stopCameraPreview();
            findViewById(R.id.button_capture).setVisibility(View.GONE);
            findViewById(R.id.torch_button).setVisibility(View.GONE);
        }

        @SuppressWarnings({"PointlessBooleanExpression", "ConstantConditions"})
        @Override
        protected void onPostExecute(Void unused) {
            if (!isCancelled()) {
                if (false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && false /*fixme*/) {
                    View sharedView = findViewById(R.id.button_capture);
                    String transitionName = getString(R.string.fab_trans_name);
                    ActivityOptions transitionActivityOptions = ActivityOptions.makeSceneTransitionAnimation(CameraActivity.this, sharedView, transitionName);
                    CameraActivity.this.startActivity(intent, transitionActivityOptions.toBundle());
                    // FIXME Scene Transition Animation bug - first app open
                } else {
                    CameraActivity.this.startActivity(intent); // normal transition
                }
            }
        }

        // https://github.com/google/cameraview/issues/22#issuecomment-269321811
        // https://github.com/react-native-community/react-native-camera/blob/master/android/src/main/java/org/reactnative/camera/tasks/ResolveTakenPictureAsyncTask.java
        private int samsungAndSonyDevicesRotationBugAdditionalRotateCompute(int width, int height) {
            int rotationDegrees = 0;
            try {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

                int orientation = ExifInterface.ORIENTATION_NORMAL;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    ExifInterface exifInterface = new ExifInterface(inputStream);
                    orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                } else {
                    if (width > height) {
                        orientation = ExifInterface.ORIENTATION_ROTATE_90;
                    }
                }

                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        rotationDegrees = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        rotationDegrees = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        rotationDegrees = 270;
                        break;
                }
            } catch (IOException e) {
//                Log.e(getString(R.string.app_name), "samsungAndSonyDevicesRotationBugAdditionalRotateCompute: ", e);
            }
            return rotationDegrees;
        }

        private void prepareCoordsToPass(int dstWidth, int dstHeight) {
            if (mPreview != null) {
                int[] xsy = mPreview.getSheetXCoords();
                int[] yki = mPreview.getSheetYCoords();
                ExcelentRotator.scaleCoords(xsy, yki, mPreview.getPreviewFrameWidth(), mPreview.getPreviewFrameHeight(), dstWidth, dstHeight);
                intent.putExtra(ScanEditShapeActivity.SHEET_X_COORDS_INTENT, xsy);
                intent.putExtra(ScanEditShapeActivity.SHEET_Y_COORDS_INTENT, yki);
            } else {
                this.cancel(true);
            }
        }

        private void preparePictureToPass(Bitmap picture) {
            String result = GallerySaveExpert.writePhotoFile(picture, "photo", getString(R.string.app_name), Bitmap.CompressFormat.JPEG, true, CameraActivity.this);
            intent.putExtra(ScanEditShapeActivity.SCAN_URL_INTENT, result);
        }
    }

}