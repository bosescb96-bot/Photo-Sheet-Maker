package com.megesticmemories.photosheetmaker;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int STORAGE_PERMISSION_REQUEST = 1002;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    private PendingSave pendingSave;

    private static class PendingSave {
        final byte[] bytes;
        final String filename;
        final String mime;
        final int callbackId;
        PendingSave(byte[] bytes, String filename, String mime, int callbackId) {
            this.bytes = bytes; this.filename = filename; this.mime = mime; this.callbackId = callbackId;
        }
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                                        FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    // Keep the browser's requested single/multiple mode. ACTION_OPEN_DOCUMENT
                    // returns persistable content Uris that WebView can read reliably.
                    boolean multiple = params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "Unable to open photo picker: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    return true;
                }
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST) return;
        if (fileCallback == null) return;

        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            LinkedHashSet<Uri> selected = new LinkedHashSet<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    if (uri != null) selected.add(uri);
                }
            }
            if (data.getData() != null) selected.add(data.getData());
            if (selected.isEmpty()) {
                Uri[] parsed = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                if (parsed != null) for (Uri uri : parsed) if (uri != null) selected.add(uri);
            }
            if (!selected.isEmpty()) results = selected.toArray(new Uri[0]);
        }
        ValueCallback<Uri[]> callback = fileCallback;
        fileCallback = null;
        callback.onReceiveValue(results); // null cleanly reports cancellation
    }

    private void reportSave(int callbackId, boolean success, String message) {
        String safe = message == null ? "" : message.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\n", " ").replace("\r", " ");
        runOnUiThread(() -> {
            if (webView != null) {
                String js = "window.onNativeSaveResult && window.onNativeSaveResult(" + callbackId + "," + success + ", '" + safe + "');";
                webView.evaluateJavascript(js, null);
            }
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void saveImageBytes(PendingSave request) {
        saveExecutor.execute(() -> {
            Uri savedUri = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, request.filename);
                    values.put(MediaStore.Images.Media.MIME_TYPE, request.mime);
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Photo Sheet Maker");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                    savedUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (savedUri == null) throw new Exception("Android could not create the image file.");
                    try (OutputStream out = getContentResolver().openOutputStream(savedUri, "w")) {
                        if (out == null) throw new Exception("Could not open the new image file.");
                        out.write(request.bytes);
                        out.flush();
                    }
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(savedUri, ready, null, null);
                } else {
                    // Older Android versions: save in the app's public-facing Pictures folder.
                    File base = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    if (base == null) throw new Exception("Pictures storage is unavailable.");
                    File folder = new File(base, "Photo Sheet Maker");
                    if (!folder.exists() && !folder.mkdirs()) throw new Exception("Could not create the Pictures folder.");
                    File file = new File(folder, request.filename);
                    try (FileOutputStream out = new FileOutputStream(file)) { out.write(request.bytes); out.flush(); }
                    savedUri = Uri.fromFile(file);
                }
                String folderMessage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? "Pictures/Photo Sheet Maker/"
                        : "Android/data/com.megesticmemories.photosheetmaker/files/Pictures/Photo Sheet Maker/";
                reportSave(request.callbackId, true, "Image saved successfully in " + folderMessage + request.filename);
            } catch (Exception e) {
                if (savedUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try { getContentResolver().delete(savedUri, null, null); } catch (Exception ignored) { }
                }
                reportSave(request.callbackId, false, "Image save failed: " + (e.getMessage() == null ? "Unknown storage error" : e.getMessage()));
            }
        });
    }

    public class AndroidBridge {
        @JavascriptInterface public void saveImage(String dataUrl, String filename, int callbackId) {
            try {
                if (dataUrl == null || !dataUrl.startsWith("data:" ) || !dataUrl.contains(","))
                    throw new IllegalArgumentException("Invalid image data received.");
                String header = dataUrl.substring(0, dataUrl.indexOf(','));
                String mime = header.substring(5).split(";")[0];
                if (!mime.equals("image/jpeg") && !mime.equals("image/png") && !mime.equals("image/webp"))
                    throw new IllegalArgumentException("Only JPEG, PNG and WebP images are supported.");
                String safeName = filename == null ? "Photo-Sheet-A4-300dpi" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
                if (!safeName.contains(".")) safeName += mime.equals("image/png") ? ".png" : mime.equals("image/webp") ? ".webp" : ".jpg";
                byte[] bytes = Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
                if (bytes.length == 0) throw new IllegalArgumentException("The exported image is empty.");
                saveImageBytes(new PendingSave(bytes, safeName, mime, callbackId));
            } catch (Exception e) {
                reportSave(callbackId, false, "Image save failed: " + (e.getMessage() == null ? "Invalid image" : e.getMessage()));
            }
        }

        @JavascriptInterface public void printPage() {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                if (pm != null) pm.print("Photo Sheet Maker", webView.createPrintDocumentAdapter("Photo Sheet Maker"),
                        new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build());
            });
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (webView != null) { webView.destroy(); webView = null; }
        saveExecutor.shutdown();
        super.onDestroy();
    }
}
