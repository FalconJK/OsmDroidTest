package com.falconjk.osmdroidtest;

import static java.security.AccessController.getContext;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.content.ContextCompat;

public class NumberedMarkerView extends View {
    private Paint paint;
    private Drawable markerDrawable;
    private String number;
    public static final int MARKER_SIZE = 120; // 設定固定大小


    public NumberedMarkerView(Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(MARKER_SIZE / 4f);  // 較小的文字尺寸
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setAntiAlias(true);

        markerDrawable = ContextCompat.getDrawable(getContext(), R.drawable.location_on);

        setMinimumWidth(MARKER_SIZE);
        setMinimumHeight(MARKER_SIZE);
    }


    public void setNumber(String number) {
        this.number = number;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 先繪製標記
        if (markerDrawable != null) {
            markerDrawable.setBounds(0, 0, getWidth(), getHeight());
            markerDrawable.draw(canvas);
        }

        // 後繪製數字
        if (number != null) {
            paint.setStyle(Paint.Style.FILL);
            paint.setAntiAlias(true);

            // 調整文字大小，讓它在白色圓圈內看起來合適
            paint.setTextSize(MARKER_SIZE / 4f);  // 縮小文字尺寸

            // 計算位置：白色圓圈在整個標記的上半部
            float x = getWidth() / 2f;
            // 向上移動位置，大約在標記的 40% 高度處
            float y = getHeight() * 0.45f;

            canvas.drawText(number, x, y, paint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MARKER_SIZE, MARKER_SIZE);
    }
}

