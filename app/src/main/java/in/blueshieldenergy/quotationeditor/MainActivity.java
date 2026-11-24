package in.blueshieldenergy.quotationeditor;

import android.Manifest;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.graphics.Color;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.Base64;
import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String HOME_URL = "https://blueshieldenergy.in/projects/quotation-editor/";
    private static final String ALLOWED_HOST = "blueshieldenergy.in";
    private static final int FILE_CHOOSER_REQUEST = 4102;
    private static final int STORAGE_PERMISSION_REQUEST = 4103;
    private static final String DOWNLOAD_FOLDER = "Quotations Blue Shield Energy";

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private boolean printInProgress = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(10, 24, 48));
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(28, 102, 190)));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3), android.view.Gravity.TOP);
        root.addView(progressBar, progressParams);
        setContentView(root);

        configureWebView();
        requestLegacyStoragePermissionIfNeeded();
        if (savedInstanceState == null) webView.loadUrl(HOME_URL);
        else webView.restoreState(savedInstanceState);
    }

    // Android only permits runtime storage permission requests on first launch, not at install time.
    private void requestLegacyStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 29) return; // Scoped storage / MediaStore on Android 10+
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
        }
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        // Honor the webpage's responsive viewport just as Chrome mobile does.
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(100);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.addJavascriptInterface(new PdfBridge(), "QuotationPdfBridge");
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAllowedNavigation(request.getUrl());
            }
            @Override @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isAllowedNavigation(Uri.parse(url));
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                injectPdfSupport();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this,
                            "Unable to load Quotation Editor. Check your internet connection.",
                            Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel(); // Never bypass TLS certificate errors.
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException ex) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker is available.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, disposition, mimeType, length) ->
                handleDownload(url, userAgent, disposition, mimeType));
    }

    // Supports both anchor/blob exports and window.print() used by the live quotation page.
    private void injectPdfSupport() {
        String js = "(function(){if(window.__bsePdfHook)return;window.__bsePdfHook=true;" +
          "function save(a){try{var u=a.href||a.getAttribute('href');if(!u||u.indexOf('blob:')!==0)return false;" +
          "var n=a.download||'Quotation.pdf';fetch(u).then(function(r){return r.blob()}).then(function(b){" +
          "var fr=new FileReader();fr.onloadend=function(){var d=String(fr.result).split(',')[1];" +
          "if(d)window.QuotationPdfBridge.savePdf(d,n)};fr.readAsDataURL(b)}).catch(function(){alert('PDF export failed. Please try again.')} );return true}catch(e){return false}}" +
          "document.addEventListener('click',function(e){var a=e.target.closest&&e.target.closest('a');" +
          "if(a&&save(a)){e.preventDefault();e.stopImmediatePropagation()}},true);" +
          "var old=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){" +
          "if(save(this))return;return old.apply(this,arguments)};" +
          "window.print=function(){window.QuotationPdfBridge.printPage()};})();";
        webView.evaluateJavascript(js, null);
    }

    private boolean isAllowedNavigation(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return true;
        String host = uri.getHost();
        if (host == null) return true;
        boolean allowed = host.equalsIgnoreCase(ALLOWED_HOST)
                || host.toLowerCase(Locale.ROOT).endsWith("." + ALLOWED_HOST);
        if (!allowed) runOnUiThread(() -> Toast.makeText(this,
                "External websites are blocked in this application.", Toast.LENGTH_SHORT).show());
        return !allowed;
    }

    private class PdfBridge {
        @JavascriptInterface public void savePdf(String base64, String fileName) {
            runOnUiThread(() -> {
                try {
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    saveBytes(bytes, safeName(fileName));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "PDF could not be saved.", Toast.LENGTH_LONG).show();
                }
            });
        }
        @JavascriptInterface public void printPage() {
            runOnUiThread(() -> saveWebViewAsPdf());
        }
    }

    private void saveWebViewAsPdf() {
        if (printInProgress || webView == null) return;
        printInProgress = true;
        try {
            String fileName = "Quotation-" + System.currentTimeMillis() + ".pdf";
            PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
            if (printManager == null) throw new IllegalStateException("Print service unavailable");

            PrintAttributes attrs = new PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(new PrintAttributes.Resolution("pdf", "PDF", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build();

            // PrintDocumentAdapter callbacks are owned by Android's print framework.
            // Calling onLayout/onWrite and constructing their callbacks directly is illegal
            // outside the framework and caused the reported Java compilation errors.
            printManager.print("Quotation", webView.createPrintDocumentAdapter(fileName), attrs);
            Toast.makeText(this,
                    "Choose 'Save as PDF' in the print screen, then select the destination folder.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open PDF print screen.", Toast.LENGTH_LONG).show();
        } finally {
            printInProgress = false;
        }
    }

    private Uri pendingPdfUri;
    private File legacyPdfFile;

    private ParcelFileDescriptor createPdfDescriptor(String fileName) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.RELATIVE_PATH, DOWNLOAD_FOLDER + "/");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            pendingPdfUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (pendingPdfUri == null) throw new Exception("MediaStore insert failed");
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(pendingPdfUri, "w");
            if (pfd == null) throw new Exception("Cannot open PDF descriptor");
            return pfd;
        }
        File dir = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_FOLDER);
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create directory");
        legacyPdfFile = new File(dir, fileName);
        return ParcelFileDescriptor.open(legacyPdfFile,
                ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY);
    }

    private void finishPendingPdf() {
        if (Build.VERSION.SDK_INT >= 29 && pendingPdfUri != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(pendingPdfUri, values, null, null);
            pendingPdfUri = null;
        }
        legacyPdfFile = null;
    }

    private void deletePendingPdf() {
        if (Build.VERSION.SDK_INT >= 29 && pendingPdfUri != null) {
            getContentResolver().delete(pendingPdfUri, null, null);
            pendingPdfUri = null;
        }
        if (legacyPdfFile != null) legacyPdfFile.delete();
        legacyPdfFile = null;
    }

    private void saveBytes(byte[] bytes, String fileName) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.RELATIVE_PATH, DOWNLOAD_FOLDER + "/");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri item = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (item == null) throw new Exception("Unable to create PDF file");
            try (OutputStream out = getContentResolver().openOutputStream(item)) {
                if (out == null) throw new Exception("Unable to open PDF file");
                out.write(bytes);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(item, done, null, null);
        } else {
            File dir = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_FOLDER);
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Unable to create folder");
            try (OutputStream out = new FileOutputStream(new File(dir, fileName))) { out.write(bytes); }
        }
        Toast.makeText(this, "PDF saved in Internal Storage/" + DOWNLOAD_FOLDER, Toast.LENGTH_LONG).show();
    }

    private void handleDownload(String url, String userAgent, String disposition, String mimeType) {
        Uri uri = Uri.parse(url);
        if (!isAllowedNavigation(uri) || "blob".equalsIgnoreCase(uri.getScheme())) {
            Toast.makeText(this, "Unsupported download link.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("Quotation Editor file");
            request.setDescription("Downloading quotation file");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            if (mimeType != null && !mimeType.isEmpty()) request.setMimeType(mimeType);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) request.addRequestHeader("Cookie", cookies);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            // DownloadManager is limited to public Downloads; the site's PDF button uses the native
            // print/Blob bridges above so quotation PDFs are saved in the requested root folder.
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    DOWNLOAD_FOLDER + "/" + safeName(disposition, uri));
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, "Download started.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download could not be started.", Toast.LENGTH_SHORT).show();
        }
    }

    private String safeName(String input) {
        String n = (input == null || input.trim().isEmpty()) ? "Quotation-" + System.currentTimeMillis() + ".pdf"
                : input.trim().replace("\"", "");
        n = n.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!n.toLowerCase(Locale.ROOT).endsWith(".pdf")) n += ".pdf";
        return n;
    }

    private String safeName(String disposition, Uri uri) {
        String name = null;
        if (disposition != null) {
            try {
                int i = disposition.toLowerCase(Locale.ROOT).indexOf("filename=");
                if (i >= 0) name = disposition.substring(i + 9).replace("\"", "").trim();
            } catch (Exception ignored) {}
        }
        if (name == null || name.isEmpty()) name = uri.getLastPathSegment();
        return safeName(name);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }
    @Override protected void onDestroy() {
        if (filePathCallback != null) filePathCallback.onReceiveValue(null);
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
