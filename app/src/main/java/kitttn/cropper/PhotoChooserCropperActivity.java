package kitttn.cropper;

import android.Manifest;
import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.ArrayAdapter;

import java.io.File;
import java.io.IOException;


/**
 * @author kitttn
 *         This activity allows you easily choose image source, crop it and return a file
 */
public class PhotoChooserCropperActivity extends ListActivity {
    private static final String TAG = "PhotoChooserCropperAct";
    private static final Integer CAMERA_REQUEST_CODE = 0;
    private static final Integer GALLERY_REQUEST_CODE = 1;
    private static final int CROP_PHOTO_REQUEST_CODE = 101;
    private Uri imagePath;
    private AlertDialog dialog;
    private boolean cropToSquare = true;

    public void showChooser() {
        RxPermissionManager mgr = RxPermissionManager.getInstance(this);
        mgr.setResult(res -> {
            if (res)
                showDialogue();
        });

        String[] permissions = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
        };

        mgr.requestPermissions(permissions);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cropToSquare = getIntent().getBooleanExtra("SQUARE_CROP", true);
        showChooser();
    }

    @Override
    public void finish() {
        if (dialog != null)
            dialog.dismiss();
        dialog = null;
        super.finish();
    }

    // =========== private methods ============

    private void showDialogue() {
        final String[] items = {"From camera", "From gallery"};
        setContentView(R.layout.activity_chooser_list);
        ArrayAdapter<String> list = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        setListAdapter(list);

        getListView().setOnItemClickListener((adapterView, view, i, l) -> {
            if (i == 0) onCameraChosen();
            else onGalleryChosen();
        });
    }

    // --------------- creating intent -------------------

    private void onCameraChosen() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File imageFile = createCameraImage();
        if (imageFile == null) {
            Log.i(TAG, "showPhotoDialogChooser: Can't create file! :(");
            return;
        }
        imagePath = Uri.fromFile(imageFile);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imagePath);
        startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE);
    }

    private void onGalleryChosen() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select source:"), GALLERY_REQUEST_CODE);
    }

    private File createCameraImage() {
        try {
            String filename = "image" + System.currentTimeMillis();
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            return File.createTempFile(filename, ".png", dir);
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------- processing photo with result ---------------------------

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GALLERY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                imagePath = data.getData();
                Log.i(TAG, "onActivityResult: Gallery result!");
                Log.i(TAG, "onActivityResult: URI: " + imagePath);
            } else finish();
        }
        if (requestCode == CAMERA_REQUEST_CODE) {
            Log.i(TAG, "onActivityResult: Camera request");
            Log.i(TAG, "onActivityResult: Image uri: " + imagePath);

            if (resultCode == Activity.RESULT_CANCELED) {
                File image = new File(imagePath.getEncodedPath());
                if (image.exists())
                    image.delete();

                finish();
                return;
            }
        }

        if (requestCode == CROP_PHOTO_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Log.i(TAG, "onActivityResult: Photo cropped!");
                setResult(RESULT_OK, data);
            }

            finish();
            return;
        }

        if (resultCode == Activity.RESULT_OK) {
            Intent intent = new Intent(this, CropActivity.class);
            intent.putExtra("CROP_SQUARE", cropToSquare);
            intent.putExtra("IMAGE_PATH", imagePath);
            startActivityForResult(intent, CROP_PHOTO_REQUEST_CODE);
        }
    }

    // ==================== static initializing ================

    public static void start(Activity launchFrom, int requestCode) {
        start(launchFrom, requestCode, true);
    }
    public static void start(Activity launchFrom, int requestCode, boolean cropToSquare) {
        Intent i = new Intent(launchFrom, PhotoChooserCropperActivity.class);
        i.putExtra("SQUARE_CROP", cropToSquare);
        launchFrom.startActivityForResult(i, requestCode);
    }
}
