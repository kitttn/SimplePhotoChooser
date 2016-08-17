package kitttn.cropper;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;

/**
 * @author kitttn
 */

public class RxShadowPermissionActivity extends Activity implements ActivityCompat.OnRequestPermissionsResultCallback {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String[] requestedPermissions = getIntent().getStringArrayExtra("permissions");
        ActivityCompat.requestPermissions(this, requestedPermissions, 42);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 42)
            RxPermissionManager.getInstance(this).onRequestPermissionsResult(permissions, grantResults);
        finish();
    }
}
