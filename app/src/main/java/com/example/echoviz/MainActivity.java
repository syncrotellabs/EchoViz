package com.example.echoviz;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView allStatus;
    private TextView floatingStatus;
    private TextView fontStatus;
    private TextView baselineStatus;
    private TextView readingStatus;
    private TextView magLensStatus;
    private Button setupButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        EchoVizRuntime.requestRestore(this);
        updateStatuses();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        updateStatuses();
    }

    private ScrollView buildContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(248, 250, 246));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(header());

        allStatus = text("", 20, true);
        allStatus.setPadding(0, dp(18), 0, dp(10));
        root.addView(allStatus);

        setupButton = primaryButton("Enable EchoViz Features");
        setupButton.setOnClickListener(v -> continueSetup());
        root.addView(setupButton);

        root.addView(section("Feature Setup"));

        floatingStatus = statusText();
        root.addView(featureCard(
                "Floating Button",
                "Shows the movable Echo logo control on top of other apps.",
                floatingStatus,
                "Open Accessibility Settings",
                v -> openAccessibilitySettings()
        ));

        fontStatus = statusText();
        root.addView(featureCard(
                "Text Size Control",
                "Uses the phone's saved baseline as EchoViz 100%, then applies +, 100%, and - from there.",
                fontStatus,
                "Allow Text Size Control",
                v -> openWriteSettings()
        ));

        baselineStatus = statusText();
        root.addView(featureCard(
                "Text Size Baseline",
                "Set EchoViz 100% to the phone's current Android font size before using the overlay controls.",
                baselineStatus,
                "Use Current as 100%",
                v -> captureFontBaseline()
        ));

        readingStatus = statusText();
        root.addView(featureCard(
                "Reading Mode",
                "Opens shared text, or visible screen text when Accessibility is enabled.",
                readingStatus,
                "Try Reading Mode",
                v -> openReaderSample()
        ));

        magLensStatus = statusText();
        root.addView(featureCard(
                "MagLens",
                "Toggles Android magnification from the floating Echo logo menu.",
                magLensStatus,
                "Open Accessibility Settings",
                v -> openAccessibilitySettings()
        ));

        TextView privacy = text(
                "EchoViz does not store or transmit screen content. Android requires you to manually enable Accessibility and text-size control.",
                17,
                false
        );
        privacy.setPadding(0, dp(18), 0, 0);
        root.addView(privacy);

        return scrollView;
    }

    private LinearLayout header() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.echo_logo_circle);
        logo.setContentDescription("EchoViz logo");
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(88), dp(88));
        logoParams.setMargins(0, 0, dp(16), 0);
        header.addView(logo, logoParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);

        TextView title = text("EchoViz", 38, true);
        copy.addView(title);

        TextView subtitle = text("Set up larger text, Reading Mode, and MagLens.", 20, false);
        subtitle.setPadding(0, dp(4), 0, 0);
        copy.addView(subtitle);

        header.addView(copy, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        return header;
    }

    private void updateStatuses() {
        boolean accessibilityReady = isAccessibilityServiceEnabled();
        boolean fontReady = Settings.System.canWrite(this);
        float baseline = FontScaleBaseline.ensure(this);
        float current = FontScaleBaseline.current(this);

        floatingStatus.setText(accessibilityReady
                ? "READY: the side logo button can appear."
                : "NEEDS SETUP: enable EchoViz Floating Controls.");

        fontStatus.setText(fontReady
                ? "READY: +, 100%, and - adjust text relative to the saved baseline."
                : "NEEDS SETUP: allow EchoViz to modify system settings.");

        baselineStatus.setText("EchoViz 100% = Android " + FontScaleBaseline.percentLabel(baseline)
                + ". Current phone text = EchoViz " + FontScaleBaseline.percentLabel(current / baseline) + ".");

        readingStatus.setText(accessibilityReady
                ? "READY: shared text and visible screen text can open in Reading Mode."
                : "PARTIAL: shared text works now; screen text needs Accessibility.");

        magLensStatus.setText(accessibilityReady
                ? "READY: MagLens can use Android magnification."
                : "NEEDS SETUP: MagLens needs EchoViz Floating Controls enabled.");

        if (accessibilityReady && fontReady) {
            allStatus.setText("All EchoViz features are ready.");
            setupButton.setText("Open Reading Mode Test");
        } else if (!accessibilityReady) {
            allStatus.setText("One main step left: enable EchoViz Floating Controls.");
            setupButton.setText("Enable Floating Button, Reading Mode, and MagLens");
        } else {
            allStatus.setText("One main step left: allow text-size control.");
            setupButton.setText("Enable + / 100% / - Text Controls");
        }
    }

    private void continueSetup() {
        if (!isAccessibilityServiceEnabled()) {
            openAccessibilitySettings();
            return;
        }
        if (!Settings.System.canWrite(this)) {
            openWriteSettings();
            return;
        }
        openReaderSample();
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }
        String expected = getPackageName() + "/" + EchoVizAccessibilityService.class.getName();
        return enabledServices.toLowerCase().contains(expected.toLowerCase());
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void openWriteSettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void captureFontBaseline() {
        FontScaleBaseline.captureCurrent(this);
        updateStatuses();
    }

    private void openReaderSample() {
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_READER_TEXT,
                "Reading Mode\n\nShare text into EchoViz, or tap Reading Mode from the floating Echo logo while another app is open. EchoViz will show the text Android exposes from the current screen in a large, calm reading view.");
        startActivity(intent);
    }

    private LinearLayout featureCard(
            String title,
            String description,
            TextView status,
            String actionLabel,
            android.view.View.OnClickListener listener
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.setup_panel);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);

        TextView titleView = text(title, 22, true);
        card.addView(titleView);

        TextView descriptionView = text(description, 17, false);
        descriptionView.setPadding(0, dp(5), 0, dp(9));
        card.addView(descriptionView);

        card.addView(status);

        Button action = compactButton(actionLabel);
        action.setOnClickListener(listener);
        card.addView(action);

        return card;
    }

    private TextView section(String label) {
        TextView view = text(label, 18, true);
        view.setPadding(0, dp(22), 0, dp(10));
        return view;
    }

    private TextView statusText() {
        TextView view = text("", 16, true);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.rgb(16, 20, 24));
        view.setTextSize(sp);
        view.setLineSpacing(0, 1.15f);
        if (bold) {
            view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private Button primaryButton(String label) {
        Button button = button(label, 20);
        button.setBackgroundResource(R.drawable.button_round);
        return button;
    }

    private Button compactButton(String label) {
        Button button = button(label, 17);
        button.setBackgroundResource(R.drawable.button_round_dark);
        return button;
    }

    private Button button(String label, int sp) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(sp);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(6), 0, dp(6));
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
