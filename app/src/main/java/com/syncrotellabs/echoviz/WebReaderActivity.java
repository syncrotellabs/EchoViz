package com.syncrotellabs.echoviz;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class WebReaderActivity extends Activity {
    public static final String EXTRA_URL = "com.syncrotellabs.echoviz.extra.WEB_READER_URL";
    public static final String EXTRA_RETURN_TO_SOURCE_TASK = "com.syncrotellabs.echoviz.extra.RETURN_TO_SOURCE_TASK";

    private static final int DEFAULT_TEXT_ZOOM = 160;
    private static final int MIN_TEXT_ZOOM = 80;
    private static final int MAX_TEXT_ZOOM = 300;
    private static final int TEXT_ZOOM_STEP = 20;

    private LinearLayout root;
    private TextView zoomLabel;
    private WebView webView;
    private int textZoom = DEFAULT_TEXT_ZOOM;

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

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    view.loadUrl(request.getUrl().toString());
                    return true;
                }
                return false;
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
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
        webView.loadUrl(url);
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
        String html = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<style>body{font-family:sans-serif;line-height:1.35;padding:20px;color:#101418;}"
                + "h1{font-size:1.4em;}p{font-size:1em;}</style></head><body>"
                + "<h1>Web Reader</h1>"
                + "<p>EchoViz could not find a web link on this screen.</p>"
                + "<p>Open the page in Chrome, use Share, and choose EchoViz. You can also use Reading Mode for visible page text.</p>"
                + "</body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private void returnToPage() {
        if (getIntent().getBooleanExtra(EXTRA_RETURN_TO_SOURCE_TASK, false)) {
            moveTaskToBack(true);
        }
        finish();
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
