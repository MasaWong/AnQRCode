/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mw.ankara.qrcode.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.google.zxing.ResultPoint;

import java.util.Collection;
import java.util.HashSet;

import mw.ankara.qrcode.R;
import mw.ankara.qrcode.camera.CameraManager;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class QRCaptureView extends View {

    private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
    private static final long ANIMATION_DELAY = 100L;
    private static final int OPAQUE = 0xFF;

    private static final int MASK_COLOR = 0x60000000;
    private static final int RESULT_COLOR = 0xb0000000;

    private final Paint mPaint;
    private final int mFrameColor;
    private final int mLaserColor;
    private final int mResultPointColor;
    private Bitmap mResultBitmap;

    private int mScannerAlpha;

    private int mCurrentPosition;

    private Collection<ResultPoint> mPossibleResultPoints;
    private Collection<ResultPoint> mLastPossibleResultPoints;

    // This constructor is used when the class is built from an XML resource.
    public QRCaptureView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every time in onDraw().
        mPaint = new Paint();

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true);
        final int colorPrimary = typedValue.data;
        mFrameColor = colorPrimary;
        mLaserColor = colorPrimary;
        mResultPointColor = colorPrimary;

        mScannerAlpha = 0;
        mPossibleResultPoints = new HashSet<>(5);
    }

    @Override
    public void onDraw(Canvas canvas) {
        Rect frame = CameraManager.get().getFramingRect();
        if (frame == null) {
            return;
        }
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Draw the exterior (i.e. outside the framing rect) darkened
        mPaint.setColor(mResultBitmap != null ? RESULT_COLOR : MASK_COLOR);
        canvas.drawRect(0, 0, width, frame.top, mPaint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom, mPaint);
        canvas.drawRect(frame.right, frame.top, width, frame.bottom, mPaint);
        canvas.drawRect(0, frame.bottom, width, height, mPaint);

        if (mResultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            mPaint.setAlpha(OPAQUE);
            canvas.drawBitmap(mResultBitmap, frame.left, frame.top, mPaint);
        } else {
            // Draw a two pixel solid black border inside the framing rect
            mPaint.setColor(mFrameColor);

            int size = width / 16;
            // 左上
            canvas.drawRect(frame.left - 4, frame.top - 4, frame.left, frame.top + size, mPaint);
            canvas.drawRect(frame.left, frame.top - 4, frame.left + size, frame.top, mPaint);

            // 左下
            canvas.drawRect(frame.left - 4, frame.bottom - size, frame.left, frame.bottom + 4, mPaint);
            canvas.drawRect(frame.left, frame.bottom, frame.left + size, frame.bottom + 4, mPaint);

            // 右上
            canvas.drawRect(frame.right, frame.top - 4, frame.right + 4, frame.top + size, mPaint);
            canvas.drawRect(frame.right - size, frame.top - 4, frame.right, frame.top, mPaint);

            // 右下
            canvas.drawRect(frame.right, frame.bottom - size, frame.right + 4, frame.bottom + 4, mPaint);
            canvas.drawRect(frame.right - size, frame.bottom, frame.right, frame.bottom + 4, mPaint);

            // Draw a red "laser scanner" line through the middle to show decoding is active
            mPaint.setColor(mLaserColor);
            mPaint.setAlpha(SCANNER_ALPHA[mScannerAlpha]);
            mScannerAlpha = (mScannerAlpha + 1) % SCANNER_ALPHA.length;
            mCurrentPosition = (mCurrentPosition + frame.height() / 50) % frame.height();
            canvas.drawRect(frame.left + 8, frame.top + mCurrentPosition - 1,
                    frame.right - 8, frame.top + mCurrentPosition + 1, mPaint);

            Collection<ResultPoint> currentPossible = mPossibleResultPoints;
            Collection<ResultPoint> currentLast = mLastPossibleResultPoints;
            if (currentPossible.isEmpty()) {
                mLastPossibleResultPoints = null;
            } else {
                mPossibleResultPoints.clear();
                mLastPossibleResultPoints = currentPossible;
                mPaint.setAlpha(OPAQUE);
                mPaint.setColor(mResultPointColor);
                for (ResultPoint point : currentPossible) {
                    canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 6.0f, mPaint);
                }
            }
            if (currentLast != null) {
                mPaint.setAlpha(OPAQUE / 2);
                mPaint.setColor(mResultPointColor);
                for (ResultPoint point : currentLast) {
                    canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 3.0f, mPaint);
                }
            }

            // Request another update at the animation interval, but only repaint the laser line,
            // not the entire viewfinder mask.
            postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top, frame.right, frame.bottom);
        }
    }

    public void drawViewfinder() {
        mResultBitmap = null;
        invalidate();
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live scanning display.
     *
     * @param barcode An image of the decoded barcode.
     */
    public void drawResultBitmap(Bitmap barcode) {
        mResultBitmap = barcode;
        invalidate();
    }

    public void addPossibleResultPoint(ResultPoint point) {
        mPossibleResultPoints.add(point);
    }

}
