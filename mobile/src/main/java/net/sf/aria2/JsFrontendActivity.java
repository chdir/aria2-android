package net.sf.aria2;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.webkit.ClientCertRequest;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import net.sf.aria2.util.WebUiExtractor;

import java.io.File;
import java.util.ArrayList;

public final class JsFrontendActivity extends Activity {
    private static final String START_PAGE = "file:///android_asset/webui/index.html";

    private ValueCallback uploadMessage;

    private WebView webView;

    @Override
    @SuppressWarnings("deprecation")
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        final boolean externalFrontendMandated = prefs.getBoolean(
                getString(R.string.external_browser_pref),
                getResources().getBoolean(R.bool.prefer_external_browser));

        if (externalFrontendMandated) {
            final String dDir = prefs.getString(getString(R.string.download_dir_pref), "");

            if (TextUtils.isEmpty(dDir)) {
                Toast.makeText(this, "Unable to start WebUI: set your aria2 directory first", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            final File uiDir = new File(dDir, WebUiExtractor.DIR_NAME);

            if (!uiDir.exists()) {
                Toast.makeText(this, "WebUI directory '" + WebUiExtractor.DIR_NAME + "' not found in aria2 directory", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            final Intent i = new Intent(Intent.ACTION_VIEW);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setDataAndType(Uri.fromFile(new File(uiDir, "index.html")), "text/html");

            final PackageManager pm = getPackageManager();

            if (pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) == null) {
                Toast.makeText(this, "No browser supporting a file:// scheme found, falling back to WebView", Toast.LENGTH_SHORT).show();
            } else {
                startActivity(i);
                finish();
                return;
            }
        }

        if (Build.VERSION.SDK_INT <= 21) {
            CookieSyncManager.createInstance(this);
        }

        if (Build.VERSION.SDK_INT >= 12) {
            CookieManager.setAcceptFileSchemeCookies(true);
        }

        final CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);

        setContentView(R.layout.activity_js_frontend);

        webView = (WebView) findViewById(R.id.uiCore);

        if (Build.VERSION.SDK_INT >= 21) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        final WebSettings config = webView.getSettings();
        webView.setWebChromeClient(new QuietChromeClient());
        webView.setWebViewClient(new JsAwareWebViewClient());

        config.setJavaScriptEnabled(true);
        config.setAllowFileAccess(true);
        config.setLoadsImagesAutomatically(true);

        // apparently setting this to true prevents data: and some other uri types from working
        config.setBlockNetworkImage(false);

        config.setAppCacheEnabled(false);
        config.setSavePassword(false);
        config.setSaveFormData(false);
        config.setDomStorageEnabled(false);
        config.setGeolocationEnabled(false);
        config.setSupportMultipleWindows(false);
        config.setJavaScriptCanOpenWindowsAutomatically(false);
        config.setCacheMode(WebSettings.LOAD_NO_CACHE);

        if (Build.VERSION.SDK_INT >= 11) {
            config.setAllowContentAccess(true);

            if (Build.VERSION.SDK_INT >= 16) {
                config.setAllowUniversalAccessFromFileURLs(true);
                config.setAllowFileAccessFromFileURLs(true);

                if (Build.VERSION.SDK_INT >= 17) {
                    config.setMediaPlaybackRequiresUserGesture(true);

                    if (Build.VERSION.SDK_INT >= 21) {
                        config.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                    }
                }
            }
        }

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        webView.loadUrl(START_PAGE);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onPause() {
        if (Build.VERSION.SDK_INT < 21) {
            CookieSyncManager.getInstance().stopSync();
        }

        if (Build.VERSION.SDK_INT >= 11) {
            webView.onPause();
        }

        super.onPause();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= 11) {
            webView.onResume();
        }

        if (Build.VERSION.SDK_INT < 21) {
            CookieSyncManager.getInstance().startSync();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case R.id.req_file_chooser:
                try {
                    if (resultCode != RESULT_OK) {
                        return;
                    }

                    Uri result = null;

                    if (data != null) {
                        result = data.getData();
                    }

                    if (result != null) {
                        if (Build.VERSION.SDK_INT >= 21) {
                            uploadMessage.onReceiveValue(new Uri[] { result });
                        } else {
                            uploadMessage.onReceiveValue(result);
                        }
                    }
                } finally {
                    uploadMessage = null;
                }
                break;
        }
    }

    @SuppressWarnings("deprecation")
    private final class QuietChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            return false;
        }

        @Override
        public void onConsoleMessage(String message, int lineNumber, String sourceID) {
            // do nothing
            Thread.yield();
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            // do nothing
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            // do nothing
        }

        @Override
        public void onReceivedTouchIconUrl(WebView view, String url, boolean precomposed) {
            // do nothing
        }

        // For Android 3.0+
        public void openFileChooser(ValueCallback<Uri> uploadMsg) {
            uploadMessage = uploadMsg;

            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            openChooser(i);
        }

        // For Android 3.0+
        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
            uploadMessage = uploadMsg;

            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            openChooser(i);
        }

        //For Android 4.1
        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
            uploadMessage = uploadMsg;

            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            openChooser(i);
        }


        @Override
        @TargetApi(21)
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            uploadMessage = filePathCallback;

            final Intent primary = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            primary.setType("*/*");

            if (Build.VERSION.SDK_INT < 23) {
                primary.addCategory(Intent.CATEGORY_OPENABLE);
            }

            Intent backupGetContent = new Intent(Intent.ACTION_GET_CONTENT);
            backupGetContent.addCategory(Intent.CATEGORY_OPENABLE);
            backupGetContent.setType("*/*");

            Intent backupPick = new Intent(Intent.ACTION_PICK,
                    Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)));
            backupPick.addCategory(Intent.CATEGORY_OPENABLE);
            backupPick.setType("*/*");

            final ArrayList<Intent> allOptions = new ArrayList<>(3);
            allOptions.add(primary);
            allOptions.add(backupGetContent);
            allOptions.add(backupPick);

            return openChooserSafely(createChooser(allOptions), "Failed to start file picker");
        }

        private Intent createChooser(ArrayList<Intent> intents) {
            final PackageManager pm = getPackageManager();

            Intent primary = null;
            for (Intent possibleOption : intents) {
                if (pm.resolveActivity(possibleOption, 0) != null) {
                    primary = possibleOption;
                    intents.remove(possibleOption);
                    break;
                }
            }

            if (primary == null) {
                return null;
            }

            final Intent chooser = Intent.createChooser(primary, "Select .torrent file");

            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toArray(new Parcelable[intents.size()]));

            return chooser;
        }

        private void openChooser(Intent intent) {
            final String errMsg = "No file managers found!";

            final PackageManager packageManager = getPackageManager();
            if (intent.resolveActivity(packageManager) != null) {
                openChooserSafely(Intent.createChooser(intent, "Select .torrent file"), errMsg);
            } else {
                Toast.makeText(getApplicationContext(), errMsg, Toast.LENGTH_SHORT).show();
            }
        }

        private boolean openChooserSafely(Intent intent, CharSequence title) {
            if (intent != null) {
                final PackageManager packageManager = getPackageManager();
                if (intent.resolveActivity(packageManager) != null) {
                    startActivityForResult(intent, R.id.req_file_chooser);
                    return true;
                }
            }

            Toast.makeText(getApplicationContext(), title, Toast.LENGTH_SHORT).show();

            return false;
        }

        @Override
        public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {
            throw new UnsupportedOperationException();
        }
    }

    private final class JsAwareWebViewClient extends WebViewClient {
        @Override
        public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (!START_PAGE.equals(url)) return;

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(view.getContext());

            final String authPref = prefs.getString("token", getString(R.string.rpc_secret));

            final String config = "aria2authConfig.token = '"+ authPref + "';";

            initializeConfig(view, config);
        }

        private void initializeConfig(WebView view, String expression) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                view.evaluateJavascript(expression, null);
            } else {
                view.loadUrl("javascript:" + expression);
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url)
        {
            if (url.startsWith("file:")) {
                return false;
            }

            final Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            final PackageManager packageManager = getPackageManager();
            if (i.resolveActivity(packageManager) != null) {
                startActivity(i);
            } else if (url.startsWith("http:") || url.startsWith("https:")) {
                Toast.makeText(getApplicationContext(), "No web browsers found!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "None of installed applications can handle the link", Toast.LENGTH_SHORT).show();
            }

            return true;
        }
    }
}
