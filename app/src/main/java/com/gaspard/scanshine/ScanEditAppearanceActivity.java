package com.gaspard.scanshine;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
//import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import com.gaspard.scanshine.utils.AffineTransformator;
import com.gaspard.scanshine.utils.EffectiveMagician;
import com.gaspard.scanshine.utils.ExcelentRotator;
import com.gaspard.scanshine.utils.GallerySaveExpert;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.Vector;

import androidx.appcompat.app.AppCompatActivity;

import static com.gaspard.scanshine.ScanEditShapeActivity.RESULT_EXIT;
import static com.gaspard.scanshine.ScanEditShapeActivity.SCAN_URL_INTENT;
import static com.gaspard.scanshine.ScanEditShapeActivity.SHEET_X_COORDS_INTENT;
import static com.gaspard.scanshine.ScanEditShapeActivity.SHEET_Y_COORDS_INTENT;

public class ScanEditAppearanceActivity extends AppCompatActivity {
    private static final String TAG = "EditorScan";
    private static final int ISO_SIZE = 0;
    private static final int AUTO_STRAIGHTEN = 1;
    private static final int EFFECT_NONE = 0;
    private static final int EFFECT_TEXT = 1;
    private static final int EFFECT_COLOR = 2;

    static {
        System.loadLibrary("opencv_java3");
    }

    private int needToRotateBy = 0;
    private Mat originalImg;
    private Bitmap bitmapResult;
    private PhotoView photoView;
    private Vector<Point> sheetCoords = new Vector<>();
    private Spinner proportionSpinner;
    private Spinner effectSpinner;
    private FloatingActionButton fabSave;
    private FloatingActionButton fabShare;
    private ImageButton rotateButton;
    private Switch fabSwitch;
    private ProgressBar progressBar;

    private int selectedSizeID = EFFECT_NONE;
    private int selectedEffectID = ISO_SIZE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_edit_appearance);

        initViews();
    }

    private void initViews() {
        fabSave = findViewById(R.id.okSaveButton);
        fabSave.setScaleType(ImageView.ScaleType.CENTER);
        fabShare = findViewById(R.id.okShareButton);
        fabShare.setScaleType(ImageView.ScaleType.CENTER);
        proportionSpinner = findViewById(R.id.spinner_proportions);
        effectSpinner = findViewById(R.id.spinner_effects);
        photoView = findViewById(R.id.photo_view);
        rotateButton = findViewById(R.id.buttonRotate);
        progressBar = findViewById(R.id.progressBarLoading);
        fabSwitch = findViewById(R.id.switch1);
        fabSwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        onSwitch();
                    }
                }
        );
        initSpinners();
        initSwitchAndFabs();
    }

    private void initSwitchAndFabs() {
        if (isShareEnabled()) {
            fabSave.setVisibility(View.INVISIBLE);
            fabShare.setVisibility(View.VISIBLE);
            fabSwitch.setChecked(true);
        } else {
            fabSave.setVisibility(View.VISIBLE);
            fabShare.setVisibility(View.INVISIBLE);
            fabSwitch.setChecked(false);
        }
    }

    private void initSpinners() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.sizes, R.layout.my_spinner_item);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        proportionSpinner.setAdapter(adapter);

        ArrayAdapter<CharSequence> adapter2 = ArrayAdapter.createFromResource(this, R.array.effects, R.layout.my_spinner_item);
        adapter2.setDropDownViewResource(R.layout.spinner_dropdown_item);
        effectSpinner.setAdapter(adapter2);

        proportionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSizeID = position;
                new AsyncPhotoRefreshApplyChangesTask().execute();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        effectSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedEffectID = position;
                new AsyncPhotoRefreshApplyChangesTask().execute();

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onResume() {
        super.onResume();

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                if (originalImg == null || sheetCoords.size() == 0) {
                    parseIntentPrepareImgAndCoords();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                new AsyncPhotoRefreshApplyChangesTask().execute();
            }
        }.execute();
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
            error.printStackTrace();
            this.finish();
        }
    }

    @SuppressLint("StaticFieldLeak")
    public void onOkClicked(View view) {
        startLoading();
        bitmapResult = ExcelentRotator.rotateBitmap(bitmapResult, needToRotateBy);
        new AsyncTask<Void, Void, Void>() {
            String filepath;

            @Override
            protected Void doInBackground(Void... voids) {
                filepath = saveImg();
                if (isShareEnabled()) {
                    shareImg(filepath);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                Toast.makeText(ScanEditAppearanceActivity.this, "Scan saved at: " + filepath, Toast.LENGTH_LONG).show();
            }
        }.execute();
        setResult(RESULT_EXIT, null);
        ScanEditAppearanceActivity.this.finish();
        Intent intent = new Intent(ScanEditAppearanceActivity.this, CameraActivity.class);
        startActivity(intent);
    }

    private boolean isShareEnabled() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        boolean defaultValue = getResources().getBoolean(R.bool.default_switch_val);
        return sharedPref.getBoolean(getString(R.string.share_enabled), defaultValue);
    }

    private String saveImg() {
        String result = "";
        if (bitmapResult != null) {
            result = GallerySaveExpert.writePhotoFile(bitmapResult, "scan", getString(R.string.app_name), Bitmap.CompressFormat.JPEG, true, this);
        }
        return result;
    }

    private void shareImg(String filepath) {
        final Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/jpg");
        final File photoFile = new File(filepath);
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(photoFile));
        shareIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent chooserIntent = Intent.createChooser(shareIntent, "Share using: ");
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(chooserIntent);
        stopLoading();
    }

    public void onRotateClicked(View view) {
        photoView.setRotationBy(90);
        needToRotateBy += 90;
        needToRotateBy %= 360;
    }

    public void onBackClicked(View view) {
        this.onBackPressed();
    }

    private void startLoading() {
        fabSave.setEnabled(false);
        fabShare.setEnabled(false);
        fabSwitch.setEnabled(false);
        proportionSpinner.setEnabled(false);
        effectSpinner.setEnabled(false);
        photoView.setEnabled(false);
        rotateButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
    }

    private void stopLoading() {
        fabSave.setEnabled(true);
        fabShare.setEnabled(true);
        fabSwitch.setEnabled(true);
        proportionSpinner.setEnabled(true);
        effectSpinner.setEnabled(true);
        photoView.setEnabled(true);
        rotateButton.setEnabled(true);
        progressBar.setVisibility(View.GONE);
    }

    @SuppressLint("ApplySharedPref")
    public void onSwitch() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.share_enabled), fabSwitch.isChecked());
        editor.commit();
        initSwitchAndFabs();
    }


    @SuppressLint("StaticFieldLeak")
    private class AsyncPhotoRefreshApplyChangesTask extends AsyncTask<Void, Void, Void> {
        @SuppressLint("CutPasteId")
        @Override
        protected void onPreExecute() {
            startLoading();
        }

        protected Void doInBackground(Void... args) {
            Mat resultImg;
            switch (selectedSizeID) {
                case ISO_SIZE:
                    resultImg = AffineTransformator.iso216ratioTransform(originalImg, sheetCoords);
                    break;
                case AUTO_STRAIGHTEN:
                    resultImg = AffineTransformator.autostraightenTransform(originalImg, sheetCoords);
                    break;
                default:
                    // error but let's do autostraighten to handle this xd
                    resultImg = AffineTransformator.autostraightenTransform(originalImg, sheetCoords);
                    break;
            }

            switch (selectedEffectID) {
                case EFFECT_NONE:
                    break;
                case EFFECT_TEXT:
                    EffectiveMagician.realMagicBW(resultImg.getNativeObjAddr());
                    break;
                case EFFECT_COLOR:
                    EffectiveMagician.realMagic(resultImg.getNativeObjAddr());
                    break;
                default:
                    break;
            }

            if (resultImg != null) {
                bitmapResult = Bitmap.createBitmap(resultImg.cols(), resultImg.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(resultImg, bitmapResult);
            }
            return null;
        }

        protected void onPostExecute(Void result) {
            if (bitmapResult != null) {
                photoView.setImageBitmap(bitmapResult);
                photoView.setRotationBy(needToRotateBy);
            }
            stopLoading();
        }
    }

}
