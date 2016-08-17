package kitttn.cropper;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.isseiaoki.simplecropview.CropImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import butterknife.Bind;
import butterknife.ButterKnife;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * @author kitttn
 */
public class CropActivity extends AppCompatActivity {
    private static final String TAG = "CropActivity";
    private static final float maxHeight = 1024.0f;
    private static final float maxWidth = 1024.0f;

    private Uri fileURI;
    private boolean cropToSquare = true;

    @Bind(R.id.toolbar)
    Toolbar toolbar;

    @Bind(R.id.cropImageView)
    CropImageView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);
        ButterKnife.bind(this);

        toolbar.setTitle("Cropping photo");
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.white));
        toolbar.inflateMenu(R.menu.activity_crop);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_done) {
                onCropPhotoClicked();
                return true;
            }
            return false;
        });

        fileURI = getIntent().getParcelableExtra("IMAGE_PATH");
        cropToSquare = getIntent().getBooleanExtra("CROP_SQUARE", true);

        view.setCropMode(cropToSquare ? CropImageView.CropMode.RATIO_1_1 : CropImageView.CropMode.RATIO_FREE);
        view.post(() -> compressBitmapAndUpload(fileURI));
    }

    private void compressBitmapAndUpload(Uri fileURI) {
        Observable.defer(() -> Observable.just(fileURI))
                .map(this::compress)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bmp -> view.setImageBitmap(bmp));
    }

    public void onCropPhotoClicked() {
        Bitmap bmp = view.getCroppedBitmap();
        String link = saveAndReturnPath(bmp, fileURI);

        Intent data = new Intent();
        data.putExtra("PATH", link);
        setResult(RESULT_OK, data);
        finish();
    }

    public String saveAndReturnPath(Bitmap bmp, Uri path) {
        try {
            File file;
            String name = "image" + System.currentTimeMillis();
            if (path.getScheme().equals("file")) {
                file = new File(path.getEncodedPath());
            } else
                file = File.createTempFile(name, ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES));

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, os);

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(os.toByteArray());
            fos.flush();

            fos.close();
            os.close();
            bmp.recycle();

            Log.i(TAG, "saveAndReturnPath: Path to save: " + file.getAbsolutePath());

            return file.getAbsolutePath();
        } catch (IOException e) {
            throw new Error("Error while writing!");
        }
    }

    public Bitmap compress(Uri imagePath) {
        try {
            //Thread.sleep(5000);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;

            InputStream is = getContentResolver().openInputStream(imagePath);
            int times = 0;
            while (opts.outWidth <= 0 && times++ < 10) {
                BitmapFactory.decodeStream(is, null, opts);
            }

            int originW = opts.outWidth;
            int originH = opts.outHeight;

            float factor = (originW > originH) ? 1.0f * maxWidth / originW : 1.0f * maxHeight / originH;
            int newW = (int) (factor * originW);
            int newH = (int) (factor * originH);
            int inSampleSize = calculateInSampleSize(opts, newW, newH);

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = inSampleSize;
            opts.inMutable = true;
            Log.i(TAG, "compress: Times to getString photo:" + times);
            Log.i(TAG, "compress: New size: " + newW + "x" + newH);
            Log.i(TAG, "compress: Sample size: " + inSampleSize);

            is = getContentResolver().openInputStream(imagePath);
            Bitmap result = BitmapFactory.decodeStream(is, null, opts);
            int rotation = 0;

            if (imagePath.getScheme().equals("file")) {
                Log.i(TAG, "compress: Rotating file from camera...");
                rotation = getRotationFromCamera(imagePath.getEncodedPath());
            } else {
                Log.i(TAG, "compress: Rotating file from media storage...");
                rotation = getRotationFromGallery(imagePath);
            }

            int w = result.getWidth(), h = result.getHeight();
            if (Math.abs(rotation) == 90) {
                int tmp = w;
                w = h;
                h = tmp;
            }
            Log.i(TAG, "compress: New size: " + w + "x" + h + "; Rotation: " + rotation);

            Bitmap.Config config = result.getConfig();
            if (config == null) config = Bitmap.Config.ARGB_8888;
            Bitmap output = Bitmap.createBitmap(w, h, config);

            Canvas offscreenCanvas = new Canvas(output);
			/*offscreenCanvas.save(Canvas.MATRIX_SAVE_FLAG);
			offscreenCanvas.rotate(90);
			offscreenCanvas.drawBitmap(result, 0, 0, null);
			offscreenCanvas.restore();*/

            Rect rect = new Rect(0, 0, w, h);
            Matrix matrix = new Matrix();
            float px = rect.exactCenterX();
            float py = rect.exactCenterY();
            matrix.postTranslate(-result.getWidth() / 2, -result.getHeight() / 2);
            matrix.postRotate(rotation);
            matrix.postTranslate(px, py);
            offscreenCanvas.drawBitmap(
                    result, matrix,
                    new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG)
            );

            return output;
        } catch (Exception e) {
            throw new Error("Can't open stream!");
        }
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        final float totalPixels = width * height;
        final float totalReqPixelsCap = reqWidth * reqHeight * 2;

        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++;
        }

        return inSampleSize;
    }

    public int getRotationFromCamera(String fileName) {
        try {
            int orientation = getExifOrientation(fileName);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return -90;
            }
            return 0;
        } catch (IOException e) {
            e.printStackTrace();
            throw new Error("Something happened :(");
        }
    }

    public int getRotationFromGallery(Uri photoUri) {
        Cursor cursor = getContentResolver().query(photoUri,
                new String[]{MediaStore.Images.ImageColumns.ORIENTATION}, null, null, null);

        if (cursor == null) {
            throw new Error("Can't access Cursor!");
        }

        cursor.moveToFirst();
        int orientation = cursor.getInt(0);
        cursor.close();

        return orientation;
    }

    private static int getExifOrientation(String src) throws IOException {
        ExifInterface exif = new ExifInterface(src);
        return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
    }
}
