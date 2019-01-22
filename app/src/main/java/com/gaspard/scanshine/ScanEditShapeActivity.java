package com.gaspard.scanshine;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.appcompat.app.AppCompatActivity;
//import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.github.chrisbanes.photoview.PhotoView;
import com.gaspard.scanshine.utils.AffineTransformator;
import com.gaspard.scanshine.views.DraggableCorner;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.Vector;

public class ScanEditShapeActivity extends AppCompatActivity {
    public static final String SCAN_URL_INTENT = "SCAN_URL_INTENT";
    public static final String SHEET_Y_COORDS_INTENT = "SHEET_Y_COORDS";
    public static final String SHEET_X_COORDS_INTENT = "SHEET_X_COORDS";
    public static final int RESULT_EXIT = 11;
    private static final String TAG = "EditorScan";
    private static final int REQUEST_EXIT = 10;
    private static final float STROKE_WIDTH = 5.0f;
    private static final float FONT_SIZE = 50.0f;


    static {
        System.loadLibrary("opencv_java3");
    }

    private Canvas canvas;
    private Paint paint;
    private ImageView canvasFrame;

    private Mat originalImg;
    private Mat resizedImg;
    private Vector<Point> sheetCoords = new Vector<>();
    private Vector<DraggableCorner> corners = new Vector<>();
    private PhotoView photoView;


    private void initDrawingTools(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);
        canvas = new Canvas(bitmap);

        canvasFrame.setScaleType(ImageView.ScaleType.FIT_XY);
        canvasFrame.setImageBitmap(bitmap);

        paint = new Paint();
        paint.setTextSize(FONT_SIZE);
        paint.setColor(getResources().getColor(R.color.shapeColor));
        paint.setStrokeWidth(STROKE_WIDTH);
    }

    public void redrawLines() {
        Vector<Point> points = new Vector<>();
        for (DraggableCorner corner : corners) {
            points.add(corner.getRealPoint());
        }
        AffineTransformator.orderPointsInPlace(points);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        boolean wrongQuad = false;
        for (int i = 0; i < 4; i++) {
            int x1 = points.elementAt(i).x;
            int y1 = points.elementAt(i).y;
            int x2 = points.elementAt((i+1)%4).x;
            int y2 = points.elementAt((i+1)%4).y;

            wrongQuad = wrongQuad | (x1==x2 && y1 == y2);
            canvas.drawLine(x1, y1, x2, y2, paint);
        }

        if (wrongQuad) {
            int x1=points.elementAt(0).x; int y1=points.elementAt(0).y;
            int x2=points.elementAt(1).x; int y2=points.elementAt(1).y;
            int x3=points.elementAt(2).x; int y3=points.elementAt(2).y;
            int x4=points.elementAt(3).x; int y4=points.elementAt(3).y;
            canvas.drawText(":(", (x1+x2+x3)/3, (y1+y2+y3)/3, paint);
            canvas.drawText(":(", (x1+x2+x4)/3, (y1+y2+y4)/3, paint);
            canvas.drawText(":(", (x1+x4+x3)/3, (y1+y4+y3)/3, paint);
            canvas.drawText(":(", (x2+x3+x4)/3, (y2+y3+y4)/3, paint);
            canvas.drawText(":(", (x1+x3)/2, (y1+y3)/2, paint);
            canvas.drawText(":(", (x2+x4)/2, (y2+y4)/2, paint);
        }

        canvasFrame.draw(canvas);
        canvasFrame.invalidate();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_edit_shape);
        parseIntentPrepareImgAndCoords();
        initViews();
    }

    private void initViews() {
        ((FloatingActionButton) findViewById(R.id.okShapeButton)).setScaleType(ImageView.ScaleType.CENTER); // android:scaleType is now broken
        ((FloatingActionButton) findViewById(R.id.buttonBack)).setScaleType(ImageView.ScaleType.CENTER); // android:scaleType is now broken
        ((FloatingActionButton) findViewById(R.id.rearangeCornersButton)).setScaleType(ImageView.ScaleType.CENTER); // android:scaleType is now broken
        canvasFrame = findViewById(R.id.canvasFrame);
        photoView = findViewById(R.id.photo_view);
        photoView.setZoomable(false);
        photoView.post(new Runnable() { // post to ui thread to get photoview width and height right
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public void run() {
                resizedImg = AffineTransformator.resize(originalImg, photoView.getWidth(), photoView.getHeight());
                initDrawingTools(photoView.getWidth(), photoView.getHeight());
                new AsyncShapeEditorImageLoaderTask().execute();
                initCorners();
            }
        });
    }

    private void initCorners() {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        canvasFrame.draw(canvas);
        canvasFrame.invalidate();
        for (DraggableCorner corner : corners) corner.dispose();

        corners.clear();
        for (int i = 0; i < sheetCoords.size(); i++) {
            corners.add(new DraggableCorner(ScanEditShapeActivity.this, sheetCoords.get(i), originalImg.cols(), originalImg.rows(), photoView.getWidth(), photoView.getHeight()));
        }
    }


    private void parseIntentPrepareImgAndCoords() {
        try {
            Intent intent = getIntent();
            originalImg = Imgcodecs.imread(intent.getStringExtra(SCAN_URL_INTENT));
            Imgproc.cvtColor(originalImg, originalImg, Imgproc.COLOR_BGR2RGB);
            int[] x = intent.getIntArrayExtra(SHEET_X_COORDS_INTENT);
            int[] y = intent.getIntArrayExtra(SHEET_Y_COORDS_INTENT);
            for (int i = 0; i < x.length/*== y length*/; i++) {
                sheetCoords.add(new Point(x[i], y[i]));
            }
        } catch (Error error) {
//            Log.e(TAG, "parseIntent: Can't load img and coords from intent. Finishing activity.");
            this.finish();
        }
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sure)
                .setMessage(R.string.sure_sure)
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                        ScanEditShapeActivity.super.onBackPressed();
                    }
                }).create().show();
    }

    public void onOkClicked(View view) {
        Intent intent = getIntent();
        int[] x = intent.getIntArrayExtra(SHEET_X_COORDS_INTENT);
        int[] y = intent.getIntArrayExtra(SHEET_Y_COORDS_INTENT);
        for (int i = 0; i < 4; i++) {
            x[i] = corners.elementAt(i).getPoint().x;
            y[i] = corners.elementAt(i).getPoint().y;
        }
        intent.putExtra(SHEET_X_COORDS_INTENT, x);
        intent.putExtra(SHEET_Y_COORDS_INTENT, y);
        intent.setClass(this, ScanEditAppearanceActivity.class);

        startActivityWithTransIfPossible(intent);
    }

    private void startActivityWithTransIfPossible(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View sharedView = findViewById(R.id.okShapeButton);
            String transitionName = getString(R.string.fab_trans_name);
            ActivityOptions transitionActivityOptions = ActivityOptions.makeSceneTransitionAnimation(this, sharedView, transitionName);
            startActivityForResult(intent, REQUEST_EXIT, transitionActivityOptions.toBundle());
        } else {
            startActivityForResult(intent, REQUEST_EXIT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_EXIT) {
            if (resultCode == RESULT_EXIT) {
                this.finish();
            }
        }
    }

    public void onBackClicked(View view) {
        initCorners();
    }

    public void onRearangeClicked(View view) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        canvasFrame.draw(canvas);
        canvasFrame.invalidate();
        corners.elementAt(0).moveTo(0, 0);
        corners.elementAt(1).moveTo(photoView.getWidth(), 0);
        corners.elementAt(2).moveTo(0, photoView.getHeight());
        corners.elementAt(3).moveTo(photoView.getWidth(), photoView.getHeight());
    }

    private class AsyncShapeEditorImageLoaderTask extends AsyncTask<Void, Void, Void> {
        private Bitmap bitmapResult;

        protected Void doInBackground(Void... args) {
            if (resizedImg != null) {
                bitmapResult = Bitmap.createBitmap(resizedImg.cols(), resizedImg.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(resizedImg, bitmapResult);
            }
            return null;
        }

        protected void onPostExecute(Void result) {
            if (bitmapResult != null) {
                photoView.setImageBitmap(bitmapResult);
            }
        }
    }
}
