package vn.t17.notes;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.database.Cursor;
import android.provider.DocumentsContract;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.OutputStream;

/**
 * Vỏ Android cho T17 Notes: một WebView chạy file assets/index.html.
 * - Không có quyền INTERNET → ứng dụng không thể kết nối mạng.
 * - FLAG_SECURE → chặn chụp màn hình và ẩn nội dung trong danh sách ứng dụng gần đây.
 * - Cầu nối "T17Android" cho JS: lưu file (hộp "Lưu vào…"), chia sẻ chữ, màu thanh hệ thống.
 */
public class MainActivity extends Activity {
    private static final int REQ_OPEN = 1;
    private static final int REQ_SAVE = 2;
    private static final int REQ_TREE = 3;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingBytes;
    private String pendingName;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;                                     // liên kết ngoài mở bằng trình duyệt
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    startActivityForResult(i, REQ_OPEN);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "T17Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    /** Các hàm JS gọi được qua window.T17Android */
    public class Bridge {
        @JavascriptInterface
        public void saveFile(final String name, final String mime, final String base64) {
            runOnUiThread(new Runnable() {
                public void run() {
                    pendingBytes = Base64.decode(base64, Base64.DEFAULT);
                    pendingName = name;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType(mime == null || mime.length() == 0 ? "application/octet-stream" : mime);
                    i.putExtra(Intent.EXTRA_TITLE, name);
                    try {
                        startActivityForResult(i, REQ_SAVE);
                    } catch (Exception e) {
                        pendingBytes = null;
                        notifySave(false);
                    }
                }
            });
        }

        @JavascriptInterface
        public void shareText(final String title, final String text) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_SUBJECT, title);
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    try { startActivity(Intent.createChooser(i, "Chia sẻ ghi chú")); } catch (Exception ignored) { }
                }
            });
        }

        @JavascriptInterface
        public void setBars(final String color, final boolean light) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        int c = Color.parseColor(color);
                        Window w = getWindow();
                        w.setStatusBarColor(c);
                        w.setNavigationBarColor(c);
                        int flags = 0;
                        if (light) {
                            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                        }
                        w.getDecorView().setSystemUiVisibility(flags);
                    } catch (Exception ignored) { }
                }
            });
        }

        /** Chọn cả thư mục (Storage Access Framework) — trả về các file .txt kèm đường dẫn tương đối */
        @JavascriptInterface
        public void pickFolder() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_TREE); }
                    catch (Exception e) { web.evaluateJavascript("window.t17FolderPicked && t17FolderPicked(null)", null); }
                }
            });
        }

        @JavascriptInterface
        public void exit() {
            runOnUiThread(new Runnable() { public void run() { finish(); } });
        }
    }

    private void notifySave(boolean ok) {
        String name = pendingName == null ? "" : pendingName.replace("\\", "\\\\").replace("'", "\\'");
        web.evaluateJavascript("window.t17SaveResult && t17SaveResult(" + ok + ", '" + name + "')", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                ClipData clip = data.getClipData();
                if (clip != null && clip.getItemCount() > 0) {
                    result = new Uri[clip.getItemCount()];
                    for (int k = 0; k < clip.getItemCount(); k++) result[k] = clip.getItemAt(k).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[] { data.getData() };
                }
            }
            if (fileCallback != null) fileCallback.onReceiveValue(result);
            fileCallback = null;
        } else if (requestCode == REQ_TREE) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                web.evaluateJavascript("window.t17FolderPicked && t17FolderPicked(null)", null);
                return;
            }
            final Uri tree = data.getData();
            new Thread(new Runnable() {
                public void run() {
                    final JSONArray out = new JSONArray();
                    try {
                        String rootId = DocumentsContract.getTreeDocumentId(tree);
                        String rootName = queryName(DocumentsContract.buildDocumentUriUsingTree(tree, rootId));
                        walk(tree, rootId, rootName == null ? "Thu muc" : rootName, out, 0);
                    } catch (Exception ignored) { }
                    final String js = "window.t17FolderPicked && t17FolderPicked(" + JSONObject.quote(out.toString()) + ")";
                    runOnUiThread(new Runnable() { public void run() { web.evaluateJavascript(js, null); } });
                }
            }).start();
        } else if (requestCode == REQ_SAVE) {
            boolean ok = false;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingBytes != null) {
                OutputStream os = null;
                try {
                    os = getContentResolver().openOutputStream(data.getData(), "wt");
                    os.write(pendingBytes);
                    os.flush();
                    ok = true;
                } catch (Exception e) {
                    ok = false;
                } finally {
                    try { if (os != null) os.close(); } catch (Exception ignored) { }
                }
            }
            pendingBytes = null;
            notifySave(ok);
        }
    }

    private String queryName(Uri doc) {
        Cursor c = null;
        try {
            c = getContentResolver().query(doc, new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { } finally { if (c != null) c.close(); }
        return null;
    }

    private void walk(Uri tree, String docId, String path, JSONArray out, int depth) throws Exception {
        if (depth > 30 || out.length() > 5000) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        Cursor c = getContentResolver().query(children, new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE }, null, null, null);
        if (c == null) return;
        try {
            while (c.moveToNext()) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                long size = c.isNull(3) ? 0 : c.getLong(3);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) { walk(tree, id, path + "/" + name, out, depth + 1); continue; }
                if (name == null || !name.toLowerCase().endsWith(".txt") || size > 100L * 1024 * 1024) continue;
                InputStream in = getContentResolver().openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id));
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                JSONObject o = new JSONObject();
                o.put("path", path + "/" + name);
                o.put("b64", Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP));
                out.put(o);
            }
        } finally { c.close(); }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.t17Back ? t17Back() : 'exit'", new ValueCallback<String>() {
            public void onReceiveValue(String v) {
                if (v == null || v.contains("exit")) finish();
            }
        });
    }

    @Override
    protected void onPause() {
        web.evaluateJavascript("window.t17Pause && t17Pause()", null);
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.evaluateJavascript("window.t17Resume && t17Resume()", null);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
