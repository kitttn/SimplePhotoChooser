package kitttn.cropper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

/**
 * @author kitttn
 */

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button click = (Button) findViewById(R.id.mainClick);
        click.setOnClickListener(view -> PhotoChooserCropperActivity.start(this, 41, false));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 41 && resultCode == RESULT_OK) {
            String path = data.getStringExtra("PATH");
            Log.i(TAG, "onActivityResult: Path to photo: " + path);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
