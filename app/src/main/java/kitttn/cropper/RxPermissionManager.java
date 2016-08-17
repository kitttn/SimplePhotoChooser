package kitttn.cropper;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;

import rx.functions.Action1;

/**
 * This class works on principle "subscribe once - getString updates every time".
 * To getString a permission result, you need three steps:
 * <ul>
 * <li> Call asObservable(), which you subscribe on. </li>
 * <li> Ask for a permisson(s) with requestPermissions(String[]). </li>
 * <li> Unsubscribe if you don't need to listen on permissions anymore </li>
 * </ul>
 *
 *
 * This class is thread-safe, you can subscribe and listen on different threads simultaneously.
 * By default it doesn't work on any particular {@link rx.Scheduler}, but you can always
 * change them via subscribeOn() and observeOn()
 * @author kitttn
 */

public class RxPermissionManager {
    private static final String TAG = "RxPermissionManager";
    private static final String PERMISSIONS_KEY = "permissions";

    private Context context;
    private static RxPermissionManager manager;
    private boolean retryDenied = true;

    private final ArrayList<String> pending = new ArrayList<>();
    private final ArrayList<String> granted = new ArrayList<>();
    private final ArrayList<String> denied = new ArrayList<>();

    private Action1<Boolean> result = (res) -> {};

    private RxPermissionManager(Context ctx) {
        context = ctx;
    }

    /**
     * Set this to true, if you want to retry with permission retrieval, if it was previously denied.
     * If you develop custom logic (for example, showing a warning, if user declines permission), set this to true.
     * Set to false, if you don't need denied permission anymore
     * <p>Default: true</p>
     * @param retry if you wish to retry denied permissions next time you call requestPermissions()
     */
    public void setRetryDenied(boolean retry) {
        retryDenied = retry;
    }

    public void setResult(Action1<Boolean> result) {
        this.result = result;
    }

    /**
     * Request needed permission(s) with this method
     * @param permissions - String[] of the permission(s) you want to process.
     */
    public void requestPermissions(String[] permissions) {
        synchronized (this) {
            Log.i(TAG, "requestPermissions");
            addToPending(permissions);
            if (!retryDenied && yieldDenied())
                return;
            checkAlreadyGranted();
            checkIfNeedToStartActivity();
        }
    }

    /**
     * if we have at least one permission, which is already denied, we can yield "False"
     */
    private boolean yieldDenied() {
        synchronized (pending) {
            Log.i(TAG, "yieldDenied: Checking already denied...");
            for (String s : pending)
                if (denied.contains(s)) {
                    Log.i(TAG, "yieldDenied: Found denied one, no need to continue!");
                    sendResult(false);
                    return true;
                }
        }

        return false;
    }

    /**
     * Puts all permissions to pending
     * @param permissions - permissions you will request and filter
     */
    private void addToPending(String[] permissions) {
        synchronized (pending) {
            Collections.addAll(pending, permissions);
            Log.i(TAG, "addToPending: Added all to \"Pending\"...");
        }
    }

    /**
     * This method checks, if you have granted some permissions to avoid long Activity callbacks and multiple starts
     */
    private void checkAlreadyGranted() {
        Log.i(TAG, "checkAlreadyGranted: Running on: " + Thread.currentThread().getName());
        Log.i(TAG, "checkAlreadyGranted: Looking for already processed...");
        synchronized (pending) {
            for (String s : pending)
                if (ContextCompat.checkSelfPermission(context, s) == PackageManager.PERMISSION_GRANTED)
                    granted.add(s);

            pending.removeAll(granted);
        }
    }

    /**
     * This method checks, if pending permissions is not empty.
     * If so, you will request an Activity. This process runs invisibly on Android prior to M.
     * On Android M, if you ask the permission for the first time, you will see a window,
     * which will allow you to grant access or deny it.
     *
     * <p>If the pending list is empty, you will not start an Activity. So every permission you want is already granted or denied.
     * We checked for denied permissions before, so you will already know the result of the permissions request. So emit True</p>
     */
    private void checkIfNeedToStartActivity() {
        synchronized (pending) {
            if (pending.size() > 0) {
                Log.i(TAG, "checkIfNeedToStartActivity: Need to start activity!");
                requestActivityPermissions();
            } else {
                Log.i(TAG, "checkIfNeedToStartActivity: No need to start activity, from here we can getString only granted permissions!");
                sendResult(true);
            }
        }
    }

    /**
     * <p><b>DON'T USE MANUALLY!</b></p>
     * Callback from {@link RxShadowPermissionActivity}, which provides results for permission(s) request
     * @param permissions Array of the permission(s) you've asked for
     * @param results Array of the results for permission requests - one per permission
     */
    public void onRequestPermissionsResult(String[] permissions, int[] results) {
        Log.i(TAG, "onRequestPermissionsResult: Got permissions result!");
        boolean result = true;
        synchronized (pending) {
            for (String s : permissions)
                if (pending.contains(s))
                    pending.remove(s);

            for (int i = 0; i < results.length; ++i)
                if (results[i] == PackageManager.PERMISSION_DENIED) {
                    result = false;
                    denied.add(permissions[i]);
                } else granted.add(permissions[i]);

            sendResult(result);
        }
    }

    private void sendResult(boolean result) {
        if (this.result != null)
            this.result.call(result);
    }

    /**
     * starts {@link android.app.Activity} for requesting permissions
     */
    private void requestActivityPermissions() {
        Log.i(TAG, "requestPermissions: Starting activity...");
        Intent i = new Intent(context, RxShadowPermissionActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(PERMISSIONS_KEY, pending.toArray(new String[pending.size()]));
        context.getApplicationContext().startActivity(i);
    }

    public static RxPermissionManager getInstance(Context context) {
        if (manager == null)
            manager = new RxPermissionManager(context.getApplicationContext());
        return manager;
    }
}
