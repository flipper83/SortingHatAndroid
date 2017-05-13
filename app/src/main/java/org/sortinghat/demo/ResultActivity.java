package org.sortinghat.demo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import org.sortinghat.demo.data.storage.ImageProcessedStorage;
import org.tensorflow.demo.R;

public class ResultActivity extends Activity {

  public static final String EXTRA_HOUSE = "EXTRA_HOUSE";
  private View toolbar;
  private View background;
  private TextView titleHouse;
  private ImageView avatar;
  private ImageView logoView;
  private Button buttonTryAgain;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_result);

    String houseName = "";
    Intent intent = getIntent();
    if (intent != null && !intent.getExtras().isEmpty()) {
      houseName = intent.getExtras().getString(EXTRA_HOUSE);
    }

    mapView();
    settingValues(houseName);
  }

  private void mapView() {
    toolbar = findViewById(R.id.toolbar);
    background = findViewById(R.id.root);
    titleHouse = (TextView) findViewById(R.id.tv_title_house);
    avatar = (ImageView) findViewById(R.id.iv_avatar);
    logoView = (ImageView) findViewById(R.id.iv_logo);
    buttonTryAgain = (Button) findViewById(R.id.bt_try_again);
    buttonTryAgain.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        ClassifierActivity.open(ResultActivity.this);
        ResultActivity.this.finish();
      }
    });
  }

  public static void open(ClassifierActivity activity, String potterHouse) {
    Intent intent = new Intent(activity, ResultActivity.class);
    intent.putExtra(EXTRA_HOUSE, potterHouse);
    activity.startActivity(intent);
  }

  public void settingValues(String houseName) {
    if (houseName.equals("gryffindor")) {
      toolbar.setBackgroundColor(Color.parseColor("#740001"));
      background.setBackgroundColor(Color.parseColor("#ae0001"));
      titleHouse.setTextColor(Color.parseColor("#eeba30"));
      buttonTryAgain.setBackgroundColor(Color.parseColor("#740001"));
      buttonTryAgain.setTextColor(Color.parseColor("#eeba30"));
      logoView.setImageResource(R.drawable.gryffindor);
    } else if (houseName.equals("hufflepuff")) {
      toolbar.setBackgroundColor(Color.parseColor("#ecb939"));
      background.setBackgroundColor(Color.parseColor("#f0c75e"));
      titleHouse.setTextColor(Color.parseColor("#726255"));
      buttonTryAgain.setBackgroundColor(Color.parseColor("#ecb939"));
      buttonTryAgain.setTextColor(Color.parseColor("#726255"));
      logoView.setImageResource(R.drawable.hufflepuff);
    } else if (houseName.equals("ravenclaw")) {
      toolbar.setBackgroundColor(Color.parseColor("#0e1a40"));
      background.setBackgroundColor(Color.parseColor("#222f5b"));
      titleHouse.setTextColor(Color.parseColor("#5d5d5d"));
      buttonTryAgain.setBackgroundColor(Color.parseColor("#0e1a40"));
      buttonTryAgain.setTextColor(Color.parseColor("#5d5d5d"));
      logoView.setImageResource(R.drawable.ravenclaw);
    } else if (houseName.equals("slytheryn")) {
      toolbar.setBackgroundColor(Color.parseColor("#20472e"));
      background.setBackgroundColor(Color.parseColor("#2d5e3e"));
      titleHouse.setTextColor(Color.parseColor("#a1a1a1"));
      buttonTryAgain.setBackgroundColor(Color.parseColor("#20472e"));
      buttonTryAgain.setTextColor(Color.parseColor("#a1a1a1"));
      logoView.setImageResource(R.drawable.slytherin);
    }

    titleHouse.setText(houseName.toUpperCase());
    avatar.setImageBitmap(ImageProcessedStorage.getInstance().getStoredImage());

  }
}
