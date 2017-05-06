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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import java.util.List;
import java.util.Vector;
import org.tensorflow.demo.R;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.env.Logger;

public class ClassifierActivity extends CameraActivity
    implements CameraActivity.PhotoReadyListener {
  private static final Logger LOGGER = new Logger(Log.VERBOSE);

  private static final float IMAGE_STD = 1;
  private static final String INPUT_NAME = "input";
  private static final String OUTPUT_NAME = "output";

  private static final String MODEL_FILE = "file:///android_asset/tensorflow_inception_graph.pb";
  private static final String LABEL_FILE =
      "file:///android_asset/imagenet_comp_graph_label_strings.txt";

  private Classifier classifier;

  private Bitmap cropCopyBitmap;

  private BorderedText borderedText;

  private long lastProcessingTimeMs;
  private boolean debugAdded = false;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    registerPhotoReady(this);

    final float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP,
        getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    classifier = TensorFlowImageClassifier.create(getAssets(), MODEL_FILE, LABEL_FILE,
        CameraActivity.INPUT_SIZE, CameraActivity.IMAGE_MEAN, IMAGE_STD, INPUT_NAME, OUTPUT_NAME);
  }

  @Override protected int getLayoutId() {
    return R.layout.camera_connection_fragment;
  }

  @Override protected int getDesiredPreviewFrameSize() {
    return INPUT_SIZE;
  }

  private static final float TEXT_SIZE_DIP = 10;

  @Override public void photoTaked(final Bitmap croppedBitmap) {

    runInBackground(new Runnable() {
      @Override public void run() {
        final long startTime = SystemClock.uptimeMillis();
        final List<Classifier.Recognition> results = classifier.recognizeImage(croppedBitmap);
        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);

        if (results != null) {
          for (Classifier.Recognition result : results) {
            LOGGER.d(result.getTitle() + " " + result.getConfidence());
          }
        }

        requestRender();
        ClassifierActivity.super.unlockCamera();
      }
    });
  }

  @Override public void onSetDebug(boolean debug) {
    if (!debugAdded) {
      addCallback(new OverlayView.DrawCallback() {
        @Override public void drawCallback(final Canvas canvas) {
          renderDebug(canvas);
        }
      });
      debugAdded = true;
    }
    classifier.enableStatLogging(debug);
  }

  private void renderDebug(final Canvas canvas) {
    if (!isDebug()) {
      return;
    }
    final Bitmap copy = cropCopyBitmap;
    if (copy != null) {
      final Matrix matrix = new Matrix();
      final float scaleFactor = 2;
      matrix.postScale(scaleFactor, scaleFactor);
      matrix.postTranslate(canvas.getWidth() - copy.getWidth() * scaleFactor,
          canvas.getHeight() - copy.getHeight() * scaleFactor);
      canvas.drawBitmap(copy, matrix, new Paint());

      final Vector<String> lines = new Vector<String>();
      if (classifier != null) {
        String statString = classifier.getStatString();
        String[] statLines = statString.split("\n");
        for (String line : statLines) {
          lines.add(line);
        }
      }

      lines.add("Frame: " + super.previewWidth + "x" + super.previewHeight);
      lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
      lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
      lines.add("Rotation: " + super.sensorOrientation);
      lines.add("Inference time: " + lastProcessingTimeMs + "ms");

      borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
    }
  }
}
