package com.knowarth.fingerprintutil.utils;

import android.app.FragmentManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaInterface;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by mitul.varmora on 01-08-2017.
 */
@RequiresApi(api = Build.VERSION_CODES.M)
public class FingerprintHelper extends CordovaPlugin {

    private static final String DIALOG_FRAGMENT_TAG = "myFragment";
    private static final String DEFAULT_KEY_NAME = "default_key";
    private final Context context;
    private KeyStore mKeyStore;
    private KeyGenerator mKeyGenerator;
    private Callback mCallback;

    public static PluginResult mPluginResult;
    public static CallbackContext mCallbackContext;
    public static CordovaInterface mCordova;

    public FingerprintHelper() {
        
    }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        mCordova = cordova;
        context = cordova.getActivity().getApplicationContext();

        if (android.os.Build.VERSION.SDK_INT < 23) {
            return;
        }

        mPluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        init();
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action          The action to execute.
     * @param args            JSONArry of arguments for the plugin.
     * @param callbackContext The callback id used when calling back into JavaScript.
     * @return A PluginResult object with a status and message.
     */
    public boolean execute(final String action,
                           JSONArray args,
                           CallbackContext callbackContext) throws JSONException {

        mCallbackContext = callbackContext;

        Log.i("cordovaLog","execute called");
        
        if (android.os.Build.VERSION.SDK_INT < 23) {
            Log.i("cordovaLog","sdk int < 23 true");
            mPluginResult = new PluginResult(PluginResult.Status.ERROR);
            mCallbackContext.error("minimum SDK version 23 required");
            mCallbackContext.sendPluginResult(mPluginResult);
            return true;
        }

        Log.i("cordovaLog","sdk int < 23 false");

        if (action.equals("authenticate")) {

            Log.i("cordovaLog","action authenticate");

            if (isFingerprintAuthAvailable()) {

                Log.i("cordovaLog","fingerprint available");
            
                mCordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        authenticate(mCordova.getActivity().getFragmentManager(), new Callback() {

                            void onAuthenticated() {
                                JSONObject resultJson = new JSONObject();
                                mCallbackContext.success(resultJson);
                                mPluginResult = new PluginResult(PluginResult.Status.OK);
                                mCallbackContext.sendPluginResult(mPluginResult);
                            }

                            void onError() {
                                mPluginResult = new PluginResult(PluginResult.Status.ERROR);
                                mCallbackContext.error("Something went wrong");
                                mCallbackContext.sendPluginResult(mPluginResult);
                            }
                        });
                    }
                });
            } else {
                Log.i("cordovaLog","fingerprint not available");
            }

        } else if (action.equals("isAuthAvailable")) {

            Log.i("cordovaLog","action isAuthAvailable");

            if (isFingerprintAuthAvailable()) {
                Log.i("cordovaLog","fingerprint available");
                mPluginResult = new PluginResult(PluginResult.Status.OK);
                mCallbackContext.success();
            } else {
                Log.i("cordovaLog","fingerprint not available");
                mPluginResult = new PluginResult(PluginResult.Status.ERROR);
                mCallbackContext.error("Fingerprint authentication not ready");
            }
            mCallbackContext.sendPluginResult(mPluginResult);
        }
        
        return false;
    }


    public boolean isFingerprintAuthAvailable() {

        FingerprintManager mFingerprintManager = context.getSystemService(FingerprintManager.class);
        KeyguardManager keyguardManager = context.getSystemService(KeyguardManager.class);

        return mFingerprintManager != null
                && mFingerprintManager.isHardwareDetected()
                && mFingerprintManager.hasEnrolledFingerprints()
                && keyguardManager != null
                && keyguardManager.isKeyguardSecure();
    }

    private void init() {

        try {
            mKeyStore = KeyStore.getInstance("AndroidKeyStore");
        } catch (KeyStoreException e) {
            throw new RuntimeException("Failed to get an instance of KeyStore", e);
        }
        try {
            mKeyGenerator = KeyGenerator
                    .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        } catch (Exception e) {
            throw new RuntimeException("Failed to get an instance of KeyGenerator", e);
        }

        createKey();
    }

    /**
     * Creates a symmetric key in the Android Key Store which can only be used after the user has
     * authenticated with fingerprint.
     */
    private void createKey() {
        try {
            mKeyStore.load(null);

            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(DEFAULT_KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT |
                            KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(true);
            }
            mKeyGenerator.init(builder.build());
            mKeyGenerator.generateKey();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Initialize the {@link Cipher} instance with the created key in the
     *
     * @return {@code true} if initialization is successful, {@code false} if the lock screen has
     * been disabled or reset after the key was generated, or if a fingerprint got enrolled after
     * the key was generated.
     */
    private boolean initCipher(Cipher cipher) {

        try {
            mKeyStore.load(null);
            SecretKey key = (SecretKey) mKeyStore.getKey(DEFAULT_KEY_NAME, null);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return true;
        } catch (KeyPermanentlyInvalidatedException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Failed to init Cipher", e);
        }
    }

    public void authenticate(FragmentManager fragmentManager, Callback callback) {
        this.mCallback = callback;
        Cipher cipher;

        try {
            cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get an instance of Cipher", e);
        }

        // Set up the crypto object for later. The object will be authenticated by use
        // of the fingerprint.
        if (initCipher(cipher)) {

            // Show the fingerprint dialog. The user has the option to use the fingerprint with
            // crypto, or you can fall back to using a server-side verified password.
            FingerprintAuthenticationDialogFragment fragment
                    = new FingerprintAuthenticationDialogFragment();
            fragment.setFingerprintHelper(this);
            fragment.setCryptoObject(new FingerprintManager.CryptoObject(cipher));

            fragment.show(fragmentManager, DIALOG_FRAGMENT_TAG);
        } else {
            // This happens if the lock screen has been disabled or or a fingerprint got
            // enrolled. Thus show the dialog to authenticate with their password first
            // and ask the user if they want to authenticate with fingerprints in the
            // future
            FingerprintAuthenticationDialogFragment fragment
                    = new FingerprintAuthenticationDialogFragment();
            fragment.setCryptoObject(new FingerprintManager.CryptoObject(cipher));

            fragment.show(fragmentManager, DIALOG_FRAGMENT_TAG);
        }
    }

    /**
     * @param cryptoObject the Crypto object
     */
    void onAuthenticated(@Nullable FingerprintManager.CryptoObject cryptoObject) {
        mCallback.onAuthenticated();
    }

    /**
     * @param cryptoObject the Crypto object
     */
    void onError(@Nullable FingerprintManager.CryptoObject cryptoObject) {
        mCallback.onError();
    }

    public interface Callback {

        /**
         * Called when fingerprint auth is success
         */
        void onAuthenticated();

        /**
         * Called when fingerprint auth error occurred
         */
        void onError();
    }
}
