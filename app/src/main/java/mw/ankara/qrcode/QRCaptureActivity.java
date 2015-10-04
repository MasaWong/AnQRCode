package mw.ankara.qrcode;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import java.io.IOException;
import java.util.Vector;

import mw.ankara.qrcode.camera.CameraManager;
import mw.ankara.qrcode.decoding.CaptureActivityHandler;
import mw.ankara.qrcode.decoding.InactivityTimer;
import mw.ankara.qrcode.view.QRCaptureView;

public class QRCaptureActivity extends AppCompatActivity implements Callback {

    private static final float BEEP_VOLUME = 0.10f;

    private QRCaptureView mQRCaptureView;

    private CaptureActivityHandler mCaptureActivityHandler;
    private Vector<BarcodeFormat> mBarcodeFormats;
    private InactivityTimer mInactivityTimer;
    private MediaPlayer mMediaPlayer;

    private String mCharacterset;

    private boolean mHasSurface;
    private boolean mPlayBeep;
    private boolean mVibrate;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_capture);

        Toolbar toolbar = (Toolbar) findViewById(R.id.qr_capture_tb_toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        CameraManager.init(getApplication());
        mQRCaptureView = (QRCaptureView) findViewById(R.id.qr_capture_qcv_finder);
        mInactivityTimer = new InactivityTimer(this);

        mHasSurface = false;

        mVibrate = true;
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        mPlayBeep = audioService.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.qr_capture_sv_preview);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (mHasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
        }
        mBarcodeFormats = null;
        mCharacterset = null;

        initBeepSound();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCaptureActivityHandler != null) {
            mCaptureActivityHandler.quitSynchronously();
            mCaptureActivityHandler = null;
        }
        CameraManager.get().closeDriver();
    }

    @Override
    protected void onDestroy() {
        mInactivityTimer.shutdown();
        super.onDestroy();
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            CameraManager.get().openDriver(surfaceHolder);
        } catch (IOException | RuntimeException ioe) {
            return;
        }

        if (mCaptureActivityHandler == null) {
            mCaptureActivityHandler = new CaptureActivityHandler(this, mBarcodeFormats, mCharacterset);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!mHasSurface) {
            mHasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mHasSurface = false;

    }

    public QRCaptureView getQRCaptureView() {
        return mQRCaptureView;
    }

    public Handler getCaptureActivityHandler() {
        return mCaptureActivityHandler;
    }

    public void drawViewfinder() {
        mQRCaptureView.drawViewfinder();
    }

    public void handleDecode(final Result obj, Bitmap barcode) {
        mInactivityTimer.onActivity();
        playBeepSoundAndVibrate();

        try {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.VIEW");
            Uri uri = Uri.parse(obj.getText());
            intent.setData(uri);
            startActivity(intent);
            finish();
        } catch (ActivityNotFoundException ignored) {
            new AlertDialog.Builder(this)
                .setMessage(R.string.qr_capture_error)
                .setPositiveButton(R.string.qr_capture_noted, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        QRCaptureActivity.this.finish();
                    }
                })
                .show();
        }
    }

    private void initBeepSound() {
        if (mPlayBeep && mMediaPlayer == null) {
            // The volume on STREAM_SYSTEM is not adjustable, and users found it
            // too loud,
            // so we now play on the music stream.
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setOnCompletionListener(beepListener);

            AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.beep);
            try {
                mMediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
                file.close();
                mMediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                mMediaPlayer.prepare();
            } catch (IOException e) {
                mMediaPlayer = null;
            }
        }
    }

    private static final long VIBRATE_DURATION = 200L;

    private void playBeepSoundAndVibrate() {
        if (mPlayBeep && mMediaPlayer != null) {
            mMediaPlayer.start();
        }
        if (mVibrate) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }

    /**
     * When the beep has finished playing, rewind to queue up another one.
     */
    private final OnCompletionListener beepListener = new OnCompletionListener() {
        public void onCompletion(MediaPlayer mediaPlayer) {
            mediaPlayer.seekTo(0);
        }
    };
}