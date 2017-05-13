package org.sortinghat.demo.data.storage;

import android.graphics.Bitmap;

public class ImageProcessedStorage {

  private Bitmap storedImage = null;

  private static ImageProcessedStorage instance = new ImageProcessedStorage();

  public static ImageProcessedStorage getInstance() {
    return instance;
  }

  public void store(Bitmap bitmap) {
    storedImage = bitmap;
  }

  public Bitmap getStoredImage() {
    return storedImage;
  }
}
