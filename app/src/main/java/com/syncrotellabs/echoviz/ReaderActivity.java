package com.syncrotellabs.echoviz;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ReaderActivity extends Activity {
    public static final String EXTRA_READER_TEXT = "com.syncrotellabs.echoviz.extra.READER_TEXT";
    public static final String EXTRA_RETURN_TO_SOURCE_TASK = "com.syncrotellabs.echoviz.extra.RETURN_TO_SOURCE_TASK";

    private TextView body;
    private TextView sizeLabel;
    private LinearLayout root;
    private int textSizeSp = 30;
    private boolean darkMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
        showText(readIntentText(getIntent()));
        applyTheme();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showText(readIntentText(intent));
    }

    private LinearLayout buildContent() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(16));

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
        toolbar.setPadding(0, dp(10), 0, 0);

        Button smaller = toolButton("-");
        smaller.setOnClickListener(v -> changeSize(-2));
        toolbar.addView(smaller);

        sizeLabel = new TextView(this);
        sizeLabel.setGravity(Gravity.CENTER);
        sizeLabel.setTextSize(20);
        sizeLabel.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        toolbar.addView(sizeLabel, labelParams);

        Button larger = toolButton("+");
        larger.setOnClickListener(v -> changeSize(2));
        toolbar.addView(larger);

        Button theme = toolButton("Theme");
        theme.setOnClickListener(v -> {
            darkMode = !darkMode;
            applyTheme();
        });
        LinearLayout.LayoutParams themeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        themeParams.setMargins(dp(10), 0, 0, 0);
        toolbar.addView(theme, themeParams);

        root.addView(toolbar);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setPadding(0, dp(16), 0, 0);
        body = new TextView(this);
        body.setTextSize(textSizeSp);
        body.setLineSpacing(dp(6), 1.18f);
        body.setPadding(dp(14), dp(14), dp(14), dp(24));
        body.setBackgroundResource(R.drawable.reader_panel);
        scrollView.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        return root;
    }

    private void returnToPage() {
        if (getIntent().getBooleanExtra(EXTRA_RETURN_TO_SOURCE_TASK, false)) {
            moveTaskToBack(true);
        }
        finish();
    }

    private String readIntentText(Intent intent) {
        String text = intent.getStringExtra(EXTRA_READER_TEXT);
        if (text == null && Intent.ACTION_SEND.equals(intent.getAction())) {
            CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared != null) {
                text = shared.toString();
            }
        }
        if (text == null || text.trim().isEmpty()) {
            return "Reading Mode\n\nShare text into EchoViz, or open it from the floating button while another app is visible.";
        }
        return text.trim();
    }

    private void showText(String text) {
        body.setText(text);
        updateSizeLabel();
    }

    private void changeSize(int delta) {
        textSizeSp = Math.max(22, Math.min(56, textSizeSp + delta));
        body.setTextSize(textSizeSp);
        updateSizeLabel();
    }

    private void updateSizeLabel() {
        sizeLabel.setText(textSizeSp + " sp");
    }

    private void applyTheme() {
        int background = darkMode ? Color.rgb(16, 20, 24) : Color.rgb(248, 250, 246);
        int text = darkMode ? Color.WHITE : Color.rgb(16, 20, 24);
        root.setBackgroundColor(background);
        body.setTextColor(text);
        sizeLabel.setTextColor(text);
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
