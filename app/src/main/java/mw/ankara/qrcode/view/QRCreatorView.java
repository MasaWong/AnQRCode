package mw.ankara.qrcode.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.HashMap;

/**
 * 生成二维码的ImageView
 *
 * @author masawong
 * @since 10/3/15
 */
public class QRCreatorView extends ImageView {

    public QRCreatorView(Context context) {
        super(context);
    }

    public QRCreatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public QRCreatorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public QRCreatorView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void showQrCode(String message, OnQrCodeCreatedListener listener) {
        // 字符不能为空
        if (TextUtils.isEmpty(message)) {
            return;
        }

        try {
            HashMap<EncodeHintType, String> hints = new HashMap<>(1);
            hints.put(EncodeHintType.CHARACTER_SET, "utf-8");

            //图像数据转换，使用了矩阵转换
            final int width = getMeasuredWidth();
            final int height = getMeasuredHeight();
            BitMatrix rawBitMatrix = new QRCodeWriter().encode(
                message, BarcodeFormat.QR_CODE, width, height, hints);

            //下面这里按照二维码的算法，逐个生成二维码的图片，
            int[] pixels = new int[width * height];
            //两个for循环是图片横列扫描的结果
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (rawBitMatrix.get(x, y)) {
                        pixels[y * width + x] = 0xff000000;
                    } else {
                        pixels[y * width + x] = 0xffffffff;
                    }
                }
            }

            //生成二维码图片的格式，使用ARGB_8888
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

            //显示到一个ImageView上面
            setImageBitmap(bitmap);

            if (listener != null) {
                listener.onSuccess(bitmap);
            }
        } catch (Exception e) {
            if (listener != null) {
                listener.onFailed(e);
            }
        }
    }

    public interface OnQrCodeCreatedListener {
        void onSuccess(Bitmap bitmap);

        void onFailed(Exception e);
    }
}
