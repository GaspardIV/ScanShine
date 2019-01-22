package com.gaspard.scanshine.views;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Point;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.gaspard.scanshine.R;
import com.gaspard.scanshine.ScanEditShapeActivity;
import com.gaspard.scanshine.utils.AffineTransformator;

import androidx.constraintlayout.widget.ConstraintLayout;

public class DraggableCorner {
    private static final int img_square_size = 140;
    private ConstraintLayout root;
    private int maxWidth;
    private int maxHeight;
    private float xCoOrdinate;
    private float yCoOrdinate;
    private ImageView img;
    private double wScaleRatio;
    private double hScaleRatio;
    private ScanEditShapeActivity activity;


    private View.OnTouchListener onTouchListener = new View.OnTouchListener() {
        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    xCoOrdinate = view.getX() - motionEvent.getRawX();
                    yCoOrdinate = view.getY() - motionEvent.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (motionEvent.getRawX() + xCoOrdinate + img_square_size / 2 > 0
                            && motionEvent.getRawX() + xCoOrdinate + img_square_size / 2 < maxWidth
                            && motionEvent.getRawY() + yCoOrdinate + img_square_size / 2 > 0
                            && motionEvent.getRawY() + yCoOrdinate + img_square_size / 2 < maxHeight) {
                        view.animate().x(motionEvent.getRawX() + xCoOrdinate).y(motionEvent.getRawY() + yCoOrdinate).setDuration(0).start();
                        activity.redrawLines();
                    }
                    break;
                default:
                    return false;
            }
            return true;
        }
    };


    @SuppressLint("ClickableViewAccessibility")
    public DraggableCorner(final ScanEditShapeActivity activity, Point orgPoint, int orgWidth, int orgHeigth, int dstWidth, int dstHeigth) {

        wScaleRatio = AffineTransformator.computeScaleRatio(orgWidth, dstWidth);
        hScaleRatio = AffineTransformator.computeScaleRatio(orgHeigth, dstHeigth);
        Point point = new Point((int) ((double) orgPoint.x / wScaleRatio), (int) ((double) orgPoint.y / hScaleRatio));

        maxWidth = dstWidth;
        maxHeight = dstHeigth;
        root = ((Activity) activity).findViewById(R.id.container);
        this.activity = activity;
        img = new ImageView(activity);
        img.setImageDrawable(activity.getResources().getDrawable(R.drawable.corner_shape));

        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(img_square_size, img_square_size);
        root.addView(img, params);

        img.setOnTouchListener(onTouchListener);
        moveTo(point.x, point.y);
    }

    public Point getPoint() {
        return new Point((int) ((img.getX() + img_square_size / 2) * wScaleRatio), (int) ((img.getY() + img_square_size / 2) * hScaleRatio));
    }

    public void moveTo(int x, int y) {
        img.animate().x(x - img_square_size / 2).y(y - img_square_size / 2).setDuration(500).withEndAction(new Runnable() {
            @Override
            public void run() {
                activity.redrawLines();
            }
        }).start();
    }

    public void dispose() {
        root.removeView(img);
    }

    public Point getRealPoint() {
        return new Point((int) (img.getX() + img_square_size / 2), (int) (img.getY() + img_square_size / 2));
    }
}
