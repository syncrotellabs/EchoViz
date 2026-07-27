package com.syncrotellabs.echoviz;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONTokener;

public class WebReaderActivity extends Activity {
    public static final String EXTRA_URL = "com.syncrotellabs.echoviz.extra.WEB_READER_URL";
    public static final String EXTRA_RETURN_TO_SOURCE_TASK = "com.syncrotellabs.echoviz.extra.RETURN_TO_SOURCE_TASK";

    private static final int DEFAULT_TEXT_ZOOM = 160;
    private static final int MIN_TEXT_ZOOM = 80;
    private static final int MAX_TEXT_ZOOM = 300;
    private static final int TEXT_ZOOM_STEP = 20;
    private static final int MIN_ARTICLE_CHARS = 280;
    private static final int MAX_ARTICLE_CHARS = 60000;
    private static final String ARTICLE_EXTRACT_SCRIPT =
            "(function(){"
                    + "function norm(t){return (t||'').replace(/\\s+/g,' ').trim();}"
                    + "function bad(el){var c=((el.id||'')+' '+(el.className||'')).toLowerCase();"
                    + "return /comment|nav|footer|header|sidebar|related|ad-| ads|advert|promo|subscribe|menu|share|social|cookie/.test(c);}"
                    + "function score(el){var text=norm(el.innerText||'');if(text.length<120){return 0;}"
                    + "var tag=(el.tagName||'').toLowerCase();var c=((el.id||'')+' '+(el.className||'')).toLowerCase();"
                    + "var s=text.length+(el.querySelectorAll('p').length*140)+(el.querySelectorAll('h1,h2,h3').length*80);"
                    + "if(/article|content|story|post|entry|main|body/.test(c)){s+=1000;}"
                    + "if(tag==='article'||tag==='main'){s+=1600;}if(bad(el)){s-=2500;}return s;}"
                    + "var title=norm((document.querySelector('h1')||{}).innerText||document.title||'');"
                    + "var candidates=Array.prototype.slice.call(document.querySelectorAll('article,main,[role=\"main\"],section,div'));"
                    + "var best=document.body,bestScore=0;for(var i=0;i<candidates.length;i++){var s=score(candidates[i]);if(s>bestScore){bestScore=s;best=candidates[i];}}"
                    + "var nodes=Array.prototype.slice.call(best.querySelectorAll('h1,h2,h3,p,li,blockquote'));"
                    + "var seen={},parts=[];for(var j=0;j<nodes.length;j++){var n=nodes[j];if(bad(n)){continue;}var text=norm(n.innerText||'');"
                    + "if(text.length<35&&!/^h[1-3]$/i.test(n.tagName||'')){continue;}if(text===title){continue;}if(seen[text]){continue;}"
                    + "seen[text]=true;parts.push(text);if(parts.join('\\n\\n').length>65000){break;}}"
                    + "var body=parts.join('\\n\\n');if(body.length<280){body=norm(best.innerText||'');}"
                    + "return JSON.stringify({title:title,text:body,url:location.href});"
                    + "})()";

    private LinearLayout root;
    private EditText addressInput;
    private TextView zoomLabel;
    private TextView statusLabel;
    private Button cleanArticleButton;
    private WebView webView;
    private int textZoom = DEFAULT_TEXT_ZOOM;
    private boolean pageHadMainFrameError;
    private boolean currentPageCanCleanArticle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
        loadIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadIntent(intent);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private LinearLayout buildContent() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(18), dp(12), dp(12));
        root.setBackgroundColor(Color.rgb(248, 250, 246));

        Button backToPage = toolButton("Back to page");
        backToPage.setTextSize(22);
        backToPage.setOnClickListener(v -> returnToPage());
        root.addView(backToPage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addressInput = new EditText(this);
        addressInput.setSingleLine(true);
        addressInput.setSelectAllOnFocus(true);
        addressInput.setHint("Paste or type link");
        addressInput.setTextSize(17);
        addressInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressInput.setOnEditorActionListener((view, actionId, event) -> {
            if (event == null || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                loadEnteredUrl();
                return true;
            }
            return false;
        });
        root.addView(addressInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout addressActions = new LinearLayout(this);
        addressActions.setGravity(Gravity.CENTER_VERTICAL);
        addressActions.setOrientation(LinearLayout.HORIZONTAL);
        addressActions.setPadding(0, dp(8), 0, 0);

        Button paste = toolButton("Paste Link");
        paste.setTextSize(16);
        paste.setOnClickListener(v -> pasteLinkAndLoad());
        addressActions.addView(paste, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        Button go = toolButton("Go");
        go.setTextSize(16);
        go.setOnClickListener(v -> loadEnteredUrl());
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        goParams.setMargins(dp(8), 0, 0, 0);
        addressActions.addView(go, goParams);

        root.addView(addressActions);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(0, dp(10), 0, dp(10));

        Button smaller = toolButton("-");
        smaller.setOnClickListener(v -> changeTextZoom(-TEXT_ZOOM_STEP));
        toolbar.addView(smaller);

        zoomLabel = new TextView(this);
        zoomLabel.setGravity(Gravity.CENTER);
        zoomLabel.setTextColor(Color.rgb(16, 20, 24));
        zoomLabel.setTextSize(20);
        zoomLabel.setTypeface(Typeface.DEFAULT_BOLD);
        zoomLabel.setOnClickListener(v -> setTextZoom(DEFAULT_TEXT_ZOOM));
        toolbar.addView(zoomLabel, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        Button larger = toolButton("+");
        larger.setOnClickListener(v -> changeTextZoom(TEXT_ZOOM_STEP));
        toolbar.addView(larger);

        root.addView(toolbar);

        cleanArticleButton = secondaryButton("Clean Article");
        cleanArticleButton.setOnClickListener(v -> cleanArticle());
        LinearLayout.LayoutParams cleanParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cleanParams.setMargins(0, 0, 0, dp(10));
        root.addView(cleanArticleButton, cleanParams);
        setCleanArticleAvailable(false);

        statusLabel = new TextView(this);
        statusLabel.setGravity(Gravity.CENTER);
        statusLabel.setTextColor(Color.rgb(16, 20, 24));
        statusLabel.setTextSize(15);
        statusLabel.setTypeface(Typeface.DEFAULT_BOLD);
        statusLabel.setText(" ");
        statusLabel.setPadding(0, 0, 0, dp(8));
        root.addView(statusLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (!currentPageCanCleanArticle || pageHadMainFrameError) {
                    return;
                }
                if (newProgress >= 80) {
                    showStatus("Loaded " + hostLabel(view == null ? null : view.getUrl()));
                    setCleanArticleAvailable(true);
                } else if (newProgress > 0) {
                    showStatus("Loading " + hostLabel(view == null ? null : view.getUrl()) + " " + newProgress + "%");
                }
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                pageHadMainFrameError = false;
                setCleanArticleAvailable(false);
                showStatus(currentPageCanCleanArticle ? "Loading " + hostLabel(url) : "Preparing Web Reader");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!pageHadMainFrameError) {
                    showStatus(currentPageCanCleanArticle ? "Loaded " + hostLabel(url) : "Paste a link, then tap Go.");
                    setCleanArticleAvailable(currentPageCanCleanArticle);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    pageHadMainFrameError = true;
                    String description = error == null || error.getDescription() == null
                            ? "Could not load page"
                            : error.getDescription().toString();
                    currentPageCanCleanArticle = false;
                    showStatus(description);
                    setCleanArticleAvailable(false);
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (request != null && request.isForMainFrame() && errorResponse != null) {
                    int statusCode = errorResponse.getStatusCode();
                    if (statusCode >= 400) {
                        pageHadMainFrameError = true;
                        currentPageCanCleanArticle = false;
                        showStatus("Page returned HTTP " + statusCode);
                        setCleanArticleAvailable(false);
                    }
                }
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkLoads(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        setTextZoom(textZoom);

        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        return root;
    }

    private void loadIntent(Intent intent) {
        String url = readIntentUrl(intent);
        if (url.isEmpty()) {
            showMissingUrl();
            return;
        }
        loadWebUrl(url);
    }

    private String readIntentUrl(Intent intent) {
        if (intent == null) {
            return "";
        }

        String url = intent.getStringExtra(EXTRA_URL);
        if (url == null && intent.getData() != null) {
            Uri data = intent.getData();
            url = data.toString();
        }
        if (url == null && Intent.ACTION_SEND.equals(intent.getAction())) {
            CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared != null) {
                url = UrlExtractor.firstUrl(shared.toString());
            }
        }
        return UrlExtractor.firstUrl(url);
    }

    private void showMissingUrl() {
        if (addressInput != null) {
            addressInput.setText("");
        }
        currentPageCanCleanArticle = false;
        setCleanArticleAvailable(false);
        showStatus("Paste a link, then tap Go.");
        String html = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<style>body{font-family:sans-serif;line-height:1.35;padding:20px;color:#101418;}"
                + "h1{font-size:1.4em;}p{font-size:1em;}</style></head><body>"
                + "<h1>Web Reader</h1>"
                + "<p>EchoViz could not find a web link on this screen.</p>"
                + "<p>Paste or type a link above and tap Go. You can also open the page in Chrome, use Share, and choose EchoViz.</p>"
                + "</body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private void loadEnteredUrl() {
        if (addressInput == null) {
            return;
        }
        loadWebUrl(addressInput.getText().toString());
    }

    private void pasteLinkAndLoad() {
        String url = readClipboardUrl();
        if (url.isEmpty()) {
            Toast.makeText(this, "No copied link found", Toast.LENGTH_SHORT).show();
            return;
        }
        loadWebUrl(url);
    }

    private void loadWebUrl(String rawUrl) {
        String url = UrlExtractor.firstUrl(rawUrl);
        if (url.isEmpty()) {
            Toast.makeText(this, "Enter a web link first", Toast.LENGTH_SHORT).show();
            showMissingUrl();
            return;
        }

        if (addressInput != null) {
            addressInput.setText(displayUrl(url));
            addressInput.setSelection(0);
        }
        pageHadMainFrameError = false;
        currentPageCanCleanArticle = true;
        setCleanArticleAvailable(false);
        showStatus("Loading " + hostLabel(url));
        webView.loadUrl(url);
    }

    private String readClipboardUrl() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return "";
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null) {
            return "";
        }
        for (int i = 0; i < clip.getItemCount(); i++) {
            CharSequence text = clip.getItemAt(i).coerceToText(this);
            String url = UrlExtractor.firstUrl(text == null ? "" : text.toString());
            if (!url.isEmpty()) {
                return url;
            }
        }
        return "";
    }

    private void returnToPage() {
        if (getIntent().getBooleanExtra(EXTRA_RETURN_TO_SOURCE_TASK, false)) {
            moveTaskToBack(true);
        }
        finish();
    }

    private void cleanArticle() {
        if (webView == null) {
            return;
        }

        cleanArticleButton.setEnabled(false);
        cleanArticleButton.setText("Cleaning...");
        webView.evaluateJavascript(ARTICLE_EXTRACT_SCRIPT, this::openCleanArticleResult);
    }

    private void openCleanArticleResult(String rawResult) {
        cleanArticleButton.setEnabled(true);
        cleanArticleButton.setText("Clean Article");

        String title;
        String text;
        try {
            Object decoded = new JSONTokener(rawResult == null ? "null" : rawResult).nextValue();
            Object articlePayload = decoded instanceof String
                    ? new JSONTokener((String) decoded).nextValue()
                    : decoded;
            JSONObject article = articlePayload instanceof JSONObject ? (JSONObject) articlePayload : new JSONObject();
            title = article.optString("title", "").trim();
            text = article.optString("text", "").trim();
        } catch (Exception ignored) {
            title = "";
            text = "";
        }

        if (text.length() < MIN_ARTICLE_CHARS) {
            Toast.makeText(this, "Clean Article could not find enough readable text on this page.", Toast.LENGTH_LONG).show();
            return;
        }

        String readerText = buildReaderText(title, text);
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_READER_TEXT, readerText);
        startActivity(intent);
    }

    private String buildReaderText(String title, String text) {
        String body = text.length() > MAX_ARTICLE_CHARS
                ? text.substring(0, MAX_ARTICLE_CHARS) + "\n\n[Article shortened by EchoViz.]"
                : text;
        if (title == null || title.trim().isEmpty()) {
            return body;
        }
        return title.trim() + "\n\n" + body;
    }

    private void changeTextZoom(int delta) {
        setTextZoom(textZoom + delta);
    }

    private void setTextZoom(int zoom) {
        textZoom = Math.max(MIN_TEXT_ZOOM, Math.min(MAX_TEXT_ZOOM, zoom));
        if (webView != null) {
            webView.getSettings().setTextZoom(textZoom);
        }
        if (zoomLabel != null) {
            zoomLabel.setText(textZoom + "%");
        }
    }

    private Button toolButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.button_round);
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = toolButton(label);
        button.setTextSize(20);
        button.setBackgroundResource(R.drawable.button_round_dark);
        return button;
    }

    private void setCleanArticleAvailable(boolean available) {
        if (cleanArticleButton == null) {
            return;
        }
        cleanArticleButton.setEnabled(available);
        cleanArticleButton.setAlpha(available ? 1.0f : 0.6f);
        if (available) {
            cleanArticleButton.setText("Clean Article");
        }
    }

    private void showStatus(String message) {
        if (statusLabel == null) {
            return;
        }
        statusLabel.setText(message == null || message.trim().isEmpty() ? " " : message.trim());
    }

    private String hostLabel(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "page";
        }
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            return host == null || host.trim().isEmpty() ? "page" : host;
        } catch (RuntimeException ignored) {
            return "page";
        }
    }

    private String displayUrl(String url) {
        if (url == null) {
            return "";
        }
        String display = url.trim();
        if (display.regionMatches(true, 0, "https://", 0, 8)) {
            display = display.substring(8);
        } else if (display.regionMatches(true, 0, "http://", 0, 7)) {
            display = display.substring(7);
        }
        if (display.endsWith("/") && display.indexOf('/') == display.length() - 1) {
            display = display.substring(0, display.length() - 1);
        }
        return display;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
