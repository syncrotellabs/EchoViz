package com.syncrotellabs.echoviz;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    static final String EXTRA_OPEN_DOCTOR = "com.syncrotellabs.echoviz.extra.OPEN_DOCTOR";
    private static final String ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";

    private TextView allStatus;
    private TextView floatingStatus;
    private TextView bubbleStatus;
    private TextView fontStatus;
    private TextView baselineStatus;
    private TextView readingStatus;
    private TextView magLensStatus;
    private TextView voiceStatus;
    private Button repairButton;
    private Button closeButton;
    private boolean closingAfterShortcutRestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (shouldRestoreAndClose()) {
            restoreBubbleAndClose();
            return;
        }
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (shouldRestoreAndClose()) {
            restoreBubbleAndClose();
            return;
        }
        if (!isDoctorRequested()) {
            EchoVizRuntime.requestRestore(this);
        }
        updateStatuses();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (shouldRestoreAndClose()) {
            restoreBubbleAndClose();
            return;
        }
        updateStatuses();
    }

    private boolean shouldRestoreAndClose() {
        return SetupStatus.isComplete(this) && !isDoctorRequested();
    }

    private boolean isDoctorRequested() {
        Intent intent = getIntent();
        return intent != null && intent.getBooleanExtra(EXTRA_OPEN_DOCTOR, false);
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

        repairButton = primaryButton("Repair EchoViz");
        repairButton.setOnClickListener(v -> repairEchoViz());
        root.addView(repairButton);

        closeButton = compactButton("Back to Page");
        closeButton.setOnClickListener(v -> closeDoctor());
        root.addView(closeButton);

        root.addView(section("Health Checks"));

        floatingStatus = statusText();
        root.addView(featureCard(
                "Floating Button",
                "Required for the movable EchoViz bubble and popup controls.",
                floatingStatus,
                "Open Accessibility Settings",
                v -> openAccessibilitySettings()
        ));

        bubbleStatus = statusText();
        root.addView(featureCard(
                "Bubble Startup",
                "Restores the floating button after EchoViz has been closed or dragged away.",
                bubbleStatus,
                "Restart Bubble",
                v -> restartBubble()
        ));

        fontStatus = statusText();
        root.addView(featureCard(
                "Text Size Control",
                "Lets +, 100%, and - change Android text size from EchoViz.",
                fontStatus,
                "Allow Text Size Control",
                v -> openWriteSettings()
        ));

        baselineStatus = statusText();
        root.addView(featureCard(
                "Text Size Baseline",
                "Keeps EchoViz 100% tied to the phone text size you choose.",
                baselineStatus,
                "Use Current as 100%",
                v -> captureFontBaseline()
        ));

        readingStatus = statusText();
        root.addView(featureCard(
                "Reading Mode",
                "Opens shared text or the readable text Android exposes from the current screen.",
                readingStatus,
                "Try Reading Mode",
                v -> openReaderSample()
        ));

        voiceStatus = statusText();
        root.addView(featureCard(
                "Read Aloud",
                "Uses Android text-to-speech for spoken Reading Mode and screen text.",
                voiceStatus,
                "Open Voice Settings",
                v -> openTextToSpeechSettings()
        ));

        magLensStatus = statusText();
        root.addView(featureCard(
                "MagLens",
                "Uses Android magnification from the floating EchoViz menu.",
                magLensStatus,
                "Open Accessibility Settings",
                v -> openAccessibilitySettings()
        ));

        TextView privacy = text(
                "EchoViz keeps this local. Android requires manual approval for Accessibility and text-size control.",
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

        TextView title = text("Setup Doctor", 34, true);
        copy.addView(title);

        TextView subtitle = text("Check and repair EchoViz features.", 20, false);
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
        boolean accessibilityReady = SetupStatus.isAccessibilityServiceEnabled(this);
        boolean fontReady = Settings.System.canWrite(this);
        boolean bubbleActive = EchoVizRuntime.isActive(this);
        boolean voiceReady = SetupStatus.hasTextToSpeechEngine(this);
        float baseline = FontScaleBaseline.ensure(this);
        float current = FontScaleBaseline.current(this);

        floatingStatus.setText(accessibilityReady
                ? "READY: EchoViz Floating Controls is enabled."
                : "NEEDS SETUP: enable EchoViz Floating Controls.");

        bubbleStatus.setText(!accessibilityReady
                ? "WAITING: enable Accessibility first."
                : bubbleActive
                ? "READY: EchoViz is allowed to show the bubble."
                : "OFF: EchoViz was shut down. Repair will restart it.");

        fontStatus.setText(fontReady
                ? "READY: +, 100%, and - can change system text size."
                : "NEEDS SETUP: allow EchoViz to modify system settings.");

        baselineStatus.setText("EchoViz 100% = Android " + FontScaleBaseline.percentLabel(baseline)
                + ". Current phone text = EchoViz " + FontScaleBaseline.percentLabel(current / baseline) + ".");

        readingStatus.setText(accessibilityReady
                ? "READY: shared text and visible screen text can open in Reading Mode."
                : "PARTIAL: shared text works now; screen text needs Accessibility.");

        voiceStatus.setText(voiceReady
                ? "READY: Android text-to-speech is available."
                : "NEEDS SETUP: install or enable an Android text-to-speech engine.");

        magLensStatus.setText(accessibilityReady
                ? "READY: MagLens can use Android magnification."
                : "NEEDS SETUP: MagLens needs EchoViz Floating Controls enabled.");

        if (!accessibilityReady) {
            allStatus.setText("Setup Doctor found one main issue: enable EchoViz Floating Controls.");
        } else if (!fontReady) {
            allStatus.setText("Setup Doctor found one main issue: allow text-size control.");
        } else if (!bubbleActive) {
            allStatus.setText("Setup Doctor found one issue: the EchoViz bubble is turned off.");
        } else if (!voiceReady) {
            allStatus.setText("EchoViz core is ready. Read Aloud needs Android text-to-speech.");
        } else {
            allStatus.setText("EchoViz looks healthy.");
        }

        closeButton.setText(SetupStatus.isComplete(this) ? "Back to Page" : "Close Setup Doctor");
    }

    private void repairEchoViz() {
        FontScaleBaseline.ensure(this);
        if (!SetupStatus.isAccessibilityServiceEnabled(this)) {
            openAccessibilitySettings();
            return;
        }
        EchoVizRuntime.requestShortcutRestore(this);
        if (!Settings.System.canWrite(this)) {
            openWriteSettings();
            return;
        }
        if (!SetupStatus.hasTextToSpeechEngine(this)) {
            Toast.makeText(this, "EchoViz bubble restored. Read Aloud needs Android voice settings.", Toast.LENGTH_LONG).show();
            openTextToSpeechSettings();
            return;
        }
        Toast.makeText(this, "EchoViz repaired. Bubble restored.", Toast.LENGTH_SHORT).show();
        updateStatuses();
    }

    private void restartBubble() {
        if (!SetupStatus.isAccessibilityServiceEnabled(this)) {
            openAccessibilitySettings();
            return;
        }
        EchoVizRuntime.requestShortcutRestore(this);
        Toast.makeText(this, "EchoViz bubble restored.", Toast.LENGTH_SHORT).show();
        updateStatuses();
    }

    private void restoreBubbleAndClose() {
        if (closingAfterShortcutRestore) {
            return;
        }

        closingAfterShortcutRestore = true;
        EchoVizRuntime.requestShortcutRestore(this);
        finishAndRemoveTask();
        overridePendingTransition(0, 0);
    }

    private void closeDoctor() {
        if (SetupStatus.isAccessibilityServiceEnabled(this)) {
            EchoVizRuntime.requestShortcutRestore(this);
        }
        finishAndRemoveTask();
        overridePendingTransition(0, 0);
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void openWriteSettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void openTextToSpeechSettings() {
        Intent intent = new Intent(ACTION_TTS_SETTINGS);
        if (intent.resolveActivity(getPackageManager()) == null) {
            intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        }
        startActivity(intent);
    }

    private void captureFontBaseline() {
        FontScaleBaseline.captureCurrent(this);
        updateStatuses();
        Toast.makeText(this, "Current phone text saved as EchoViz 100%.", Toast.LENGTH_SHORT).show();
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
