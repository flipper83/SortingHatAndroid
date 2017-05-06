/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sortinghat.demo;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.util.Size;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import java.nio.ByteBuffer;
import java.util.Vector;
import org.tensorflow.demo.R;
import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;

public abstract class CameraActivity extends Activity implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  private static final int PERMISSIONS_REQUEST = 1;

  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;

  // These are the settings for the original v1 Inception model. If you want to
  // use a model that's been produced from the TensorFlow for Poets codelab,
  // you'll need to set IMAGE_SIZE = 299, IMAGE_MEAN = 128, IMAGE_STD = 128,
  // INPUT_NAME = "Mul:0", and OUTPUT_NAME = "final_result:0".
  // You'll also need to update the MODEL_FILE and LABEL_FILE paths to point to
  // the ones you produced.
  //
  // To use v3 Inception model, strip the DecodeJpeg Op from your retrained
  // model first:
  //
  // python strip_unused.py \
  // --input_graph=<retrained-pb-file> \
  // --output_graph=<your-stripped-pb-file> \
  // --input_node_names="Mul" \
  // --output_node_names="final_result" \
  // --input_binary=true

  //protected static final int INPUT_SIZE = 224;
  //protected static final int IMAGE_MEAN = 117;
  protected static final int INPUT_SIZE = 299;
  protected static final int IMAGE_MEAN = 128;


  private boolean debug = false;

  private Handler handler;
  private HandlerThread handlerThread;
  private ImageReader imageReader;
  private PhotoReadyListener photoReadyListener;

  private byte[][] yuvBytes;
  private int[] rgbBytes = null;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private boolean computing = false;

  protected Integer sensorOrientation;

  protected int previewWidth = 0;
  protected int previewHeight = 0;

  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private Matrix frameToCropTransform;

  private static final boolean MAINTAIN_ASPECT = true;
  private boolean savingImage;

  @Override protected void onCreate(final Bundle savedInstanceState) {
    LOGGER.d("onCreate " + this);
    super.onCreate(null);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    setContentView(R.layout.activity_camera);

    if (hasPermission()) {
      setFragment();
    } else {
      requestPermission();
    }
  }

  @Override public synchronized void onStart() {
    LOGGER.d("onStart " + this);
    super.onStart();
  }

  @Override public synchronized void onResume() {
    LOGGER.d("onResume " + this);
    super.onResume();

    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }

  @Override public synchronized void onPause() {
    LOGGER.d("onPause " + this);

    if (!isFinishing()) {
      LOGGER.d("Requesting finish");
      finish();
    }

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }

    super.onPause();
  }

  @Override public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    super.onStop();
  }

  @Override public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    super.onDestroy();
  }

  protected synchronized void runInBackground(final Runnable r) {
    if (handler != null) {
      handler.post(r);
    }
  }

  @Override
  public void onRequestPermissionsResult(final int requestCode, final String[] permissions,
      final int[] grantResults) {
    switch (requestCode) {
      case PERMISSIONS_REQUEST: {
        if (grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
            && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
          setFragment();
        } else {
          requestPermission();
        }
      }
    }
  }

  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED
          && checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)
          || shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
        Toast.makeText(CameraActivity.this,
            "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
      }
      requestPermissions(new String[] { PERMISSION_CAMERA, PERMISSION_STORAGE },
          PERMISSIONS_REQUEST);
    }
  }

  protected void setFragment() {
    final Fragment fragment =
        CameraConnectionFragment.newInstance(new CameraConnectionFragment.ConnectionCallback() {
          @Override public void onPreviewSizeChosen(final Size size, final int rotation) {
            CameraActivity.this.onPreviewSizeChosen(size, rotation);
          }
        }, this, takeSnapshot, getLayoutId(), getDesiredPreviewFrameSize());

    getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
  }

  protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  public boolean isDebug() {
    return debug;
  }

  public void requestRender() {
    final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
    if (overlay != null) {
      overlay.postInvalidate();
    }
  }

  public void addCallback(final OverlayView.DrawCallback callback) {
    final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
    if (overlay != null) {
      overlay.addCallback(callback);
    }
  }

  public void onSetDebug(final boolean debug) {
    this.debug = debug;
  }

  @Override public boolean onKeyDown(final int keyCode, final KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      debug = !debug;
      onSetDebug(debug);
      requestRender();
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  private View.OnClickListener takeSnapshot = new View.OnClickListener() {
    @Override public void onClick(View v) {
      if (croppedBitmap != null && photoReadyListener != null) {
        lockCamera();
        photoReadyListener.photoTaked(croppedBitmap);
      }
    }
  };

  @Override public void onImageAvailable(final ImageReader reader) {
    imageReader = reader;

    Image image = null;

    try {
      image = reader.acquireLatestImage();

      if (image == null) {
        return;
      }

      if (savingImage || computing) {
        image.close();
        return;
      }
      savingImage = true;

      Trace.beginSection("imageAvailable");

      final Plane[] planes = image.getPlanes();
      fillBytes(planes, yuvBytes);

      final int yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();
      ImageUtils.convertYUV420ToARGB8888(yuvBytes[0], yuvBytes[1], yuvBytes[2], rgbBytes,
          previewWidth, previewHeight, yRowStride, uvRowStride, uvPixelStride, false);

      image.close();
    } catch (final Exception e) {
      if (image != null) {
        image.close();
      }
      LOGGER.e(e, "Exception!");
      Trace.endSection();
      return;
    }

    rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveBitmap(croppedBitmap);
    }

    savingImage = false;
    Trace.endSection();
  }

  protected void registerPhotoReady(PhotoReadyListener photoReadyListener) {
    this.photoReadyListener = photoReadyListener;
  }

  public void onPreviewSizeChosen(final Size size, final int rotation) {

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    final Display display = getWindowManager().getDefaultDisplay();
    final int screenOrientation = display.getRotation();

    LOGGER.i("Sensor orientation: %d, Screen orientation: %d", rotation, screenOrientation);

    sensorOrientation = rotation + screenOrientation;

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbBytes = new int[previewWidth * previewHeight];
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);

    frameToCropTransform =
        ImageUtils.getTransformationMatrix(previewWidth, previewHeight, INPUT_SIZE, INPUT_SIZE,
            sensorOrientation, MAINTAIN_ASPECT);

    Matrix cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    yuvBytes = new byte[3][];
  }


  protected abstract int getLayoutId();

  protected abstract int getDesiredPreviewFrameSize();

  public void lockCamera() {
    computing = true;
  }
  public void unlockCamera() {
    computing = false;
  }

  interface PhotoReadyListener {
    void photoTaked(final Bitmap croppedBitmap);
  }
}
