package com.syncrotellabs.echoviz;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.MagnificationController;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class EchoVizAccessibilityService extends AccessibilityService {
    private static final float[] RELATIVE_FONT_SCALES = {0.75f, 0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 1.75f, 2.0f};
    private static final float MAG_LENS_SCALE = 2.0f;
    private static final String OVERLAY_PREFS_NAME = "echoviz_overlay";
    private static final String KEY_HANDLE_X = "handle_x";
    private static final String KEY_HANDLE_Y = "handle_y";

    private WindowManager windowManager;
    private ImageView handle;
    private ImageView magLensExitButton;
    private LinearLayout menu;
    private LinearLayout exitDialog;
    private WindowManager.LayoutParams handleParams;
    private WindowManager.LayoutParams magLensExitParams;
    private WindowManager.LayoutParams menuParams;
    private WindowManager.LayoutParams exitDialogParams;
    private Button percentButton;
    private Button magLensButton;
    private BroadcastReceiver runtimeReceiver;
    private Handler mainHandler;

    private float downRawX;
    private float downRawY;
    private float lastRawX;
    private float lastRawY;
    private int startX;
    private int startY;
    private int normalHandleX;
    private int normalHandleY;
    private boolean dragging;
    private boolean magLensActive;
    private boolean hiddenOnHome;
    private boolean showOnHomeFromShortcut;
    private final Set<String> homePackages = new LinkedHashSet<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());
        registerRuntimeReceiver();
        refreshHomePackages();
        showOnHomeFromShortcut = EchoVizRuntime.consumeShowOnHomeRestore(this);
        updateOverlayVisibility(activeWindowPackageName());
        scheduleOverlayRefresh(500);
        scheduleOverlayRefresh(1500);
        scheduleOverlayRefresh(3000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!EchoVizRuntime.isActive(this)) {
            hideAllEchoVizOverlays();
            return;
        }
        if (event == null) {
            return;
        }
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            CharSequence packageName = event.getPackageName();
            updateOverlayVisibility(packageName == null ? "" : packageName.toString());
            scheduleOverlayRefresh(250);
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        unregisterRuntimeReceiver();
        hideExitConfirmation();
        hideMagLensExitButton();
        removeMenu();
        hideHandle();
        super.onDestroy();
    }

    private void updateOverlayVisibility(String packageName) {
        if (!EchoVizRuntime.isActive(this)) {
            hideAllEchoVizOverlays();
            return;
        }
        if (!showOnHomeFromShortcut && EchoVizRuntime.consumeShowOnHomeRestore(this)) {
            showOnHomeFromShortcut = true;
        }
        if (isHomePackage(packageName)) {
            if (showOnHomeFromShortcut) {
                hiddenOnHome = false;
                showHandle();
                return;
            }
            hideOverlaysForHomeScreen();
            return;
        }

        if (!packageName.isEmpty() && !packageName.equals(getPackageName())) {
            showOnHomeFromShortcut = false;
        }
        if (hiddenOnHome) {
            hiddenOnHome = false;
        }
        showHandle();
    }

    private void scheduleOverlayRefresh(long delayMillis) {
        if (mainHandler == null) {
            return;
        }

        mainHandler.postDelayed(() -> updateOverlayVisibility(activeWindowPackageName()), delayMillis);
    }

    private void registerRuntimeReceiver() {
        if (runtimeReceiver != null) {
            return;
        }

        runtimeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !EchoVizRuntime.ACTION_RESTORE_OVERLAY.equals(intent.getAction())) {
                    return;
                }
                showOnHomeFromShortcut = intent.getBooleanExtra(EchoVizRuntime.EXTRA_SHOW_ON_HOME, false)
                        || EchoVizRuntime.consumeShowOnHomeRestore(context);
                hiddenOnHome = false;
                showHandle();
                scheduleOverlayRefresh(400);
                scheduleOverlayRefresh(1200);
            }
        };

        IntentFilter filter = new IntentFilter(EchoVizRuntime.ACTION_RESTORE_OVERLAY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(runtimeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(runtimeReceiver, filter);
        }
    }

    private void unregisterRuntimeReceiver() {
        if (runtimeReceiver == null) {
            return;
        }

        unregisterReceiver(runtimeReceiver);
        runtimeReceiver = null;
    }

    private void hideOverlaysForHomeScreen() {
        if (hiddenOnHome) {
            return;
        }

        hiddenOnHome = true;
        hideAllEchoVizOverlays();
    }

    private void hideAllEchoVizOverlays() {
        if (isMagLensMode()) {
            getMagnificationController().reset(true);
            magLensActive = false;
            restoreNormalHandlePosition();
            updateHandleForMagLensMode(false);
        }
        hideExitConfirmation();
        hideMagLensExitButton();
        removeMenu();
        hideHandle();
    }

    private boolean isHomePackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        if (packageName.equals(getPackageName()) || packageName.equals("com.android.settings")) {
            return false;
        }
        if (homePackages.isEmpty()) {
            refreshHomePackages();
        }
        return homePackages.contains(packageName);
    }

    private void refreshHomePackages() {
        homePackages.clear();

        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        PackageManager packageManager = getPackageManager();

        ResolveInfo defaultHome = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (defaultHome != null && defaultHome.activityInfo != null) {
            addHomePackage(defaultHome.activityInfo.packageName);
        }

        List<ResolveInfo> launchers = packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo launcher : launchers) {
            if (launcher.activityInfo != null && launcher.activityInfo.packageName != null) {
                addHomePackage(launcher.activityInfo.packageName);
            }
        }

        addHomePackage("com.android.launcher");
        addHomePackage("com.android.launcher3");
        addHomePackage("com.google.android.apps.nexuslauncher");
        addHomePackage("com.sec.android.app.launcher");
    }

    private void addHomePackage(String packageName) {
        if (packageName == null
                || packageName.equals(getPackageName())
                || packageName.equals("com.android.settings")) {
            return;
        }

        homePackages.add(packageName);
    }

    private String activeWindowPackageName() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) {
            return "";
        }
        return root.getPackageName().toString();
    }

    private void showHandle() {
        if (!EchoVizRuntime.isActive(this)) {
            return;
        }
        if (hiddenOnHome) {
            return;
        }
        if (handle != null) {
            return;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        handle = new ImageView(this);
        handle.setImageResource(R.drawable.echo_logo_circle);
        handle.setBackgroundResource(R.drawable.logo_bubble);
        handle.setPadding(dp(5), dp(5), dp(5), dp(5));
        handle.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        handle.setClipToOutline(true);
        handle.setClickable(true);
        handle.setElevation(dp(8));
        handle.setContentDescription("EchoViz controls");
        updateHandleForMagLensMode(false);

        if (handleParams == null) {
            handleParams = new WindowManager.LayoutParams(
                    dp(52),
                    dp(52),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );
            handleParams.gravity = Gravity.START | Gravity.TOP;
            int defaultY = Math.max(dp(80), metrics.heightPixels / 2 - dp(26));
            SharedPreferences prefs = getSharedPreferences(OVERLAY_PREFS_NAME, Context.MODE_PRIVATE);
            handleParams.x = clampX(prefs.getInt(KEY_HANDLE_X, 0));
            handleParams.y = clampY(prefs.getInt(KEY_HANDLE_Y, defaultY));
        }

        handle.setOnTouchListener(this::onHandleTouch);
        try {
            windowManager.addView(handle, handleParams);
            updateHandleGestureExclusion();
        } catch (RuntimeException e) {
            handle = null;
            scheduleOverlayRefresh(500);
        }
    }

    private void updateHandleGestureExclusion() {
        if (handle == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }

        handle.post(() -> {
            if (handle == null) {
                return;
            }
            handle.setSystemGestureExclusionRects(Collections.singletonList(
                    new Rect(0, 0, handle.getWidth(), handle.getHeight())
            ));
        });
    }

    private void hideHandle() {
        if (handle != null) {
            try {
                windowManager.removeView(handle);
            } catch (IllegalArgumentException ignored) {
                // The overlay can already be detached during service restarts.
            }
            handle = null;
        }
    }

    private boolean onHandleTouch(View view, MotionEvent event) {
        if (isMagLensMode()) {
            return onMagLensHandleTouch(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = handleParams.x;
                startY = handleParams.y;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float moveY = event.getRawY() - downRawY;
                float moveX = event.getRawX() - downRawX;
                if (Math.abs(moveY) > dp(6) || Math.abs(moveX) > dp(6)) {
                    dragging = true;
                    handleParams.x = clampX(startX + Math.round(moveX));
                    handleParams.y = clampY(startY + Math.round(moveY));
                    windowManager.updateViewLayout(handle, handleParams);
                    if (menu != null) {
                        positionMenu();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) {
                    toggleMenu();
                } else {
                    snapHandleToNearestSide();
                    saveHandlePosition();
                }
                return true;
            default:
                return false;
        }
    }

    private boolean onMagLensHandleTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                lastRawX = downRawX;
                lastRawY = downRawY;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float totalX = event.getRawX() - downRawX;
                float totalY = event.getRawY() - downRawY;
                float deltaX = event.getRawX() - lastRawX;
                float deltaY = event.getRawY() - lastRawY;
                lastRawX = event.getRawX();
                lastRawY = event.getRawY();

                if (Math.abs(totalX) > dp(4) || Math.abs(totalY) > dp(4)) {
                    dragging = true;
                    panMagnificationBy(-deltaX, -deltaY);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    Toast.makeText(this, "Drag to pan. Use X to close.", Toast.LENGTH_SHORT).show();
                }
                return true;
            default:
                return false;
        }
    }

    private void toggleMenu() {
        if (menu == null) {
            showMenu();
        } else {
            removeMenu();
        }
    }

    private void showMenu() {
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.setBackgroundResource(R.drawable.overlay_menu);

        LinearLayout scaleRow = new LinearLayout(this);
        scaleRow.setGravity(Gravity.CENTER);
        scaleRow.setOrientation(LinearLayout.HORIZONTAL);

        FontScaleBaseline.ensure(this);

        Button minus = menuButton("-");
        minus.setOnClickListener(v -> changeFontScale(-1));
        scaleRow.addView(minus);

        percentButton = menuButton(requestedRelativeFontLabel());
        percentButton.setMinWidth(dp(92));
        percentButton.setOnClickListener(v -> setRelativeFontScale(1.0f));
        scaleRow.addView(percentButton);

        Button plus = menuButton("+");
        plus.setOnClickListener(v -> changeFontScale(1));
        scaleRow.addView(plus);
        menu.addView(scaleRow);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setGravity(Gravity.CENTER);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        Button readingMode = menuButton("Reading Mode");
        readingMode.setOnClickListener(v -> openReadingMode());
        modeRow.addView(readingMode);

        magLensButton = menuButton(isMagLensMode() ? "Back to page" : "MagLens");
        magLensButton.setOnClickListener(v -> toggleMagnification());
        modeRow.addView(magLensButton);
        menu.addView(modeRow);

        Button exitButton = dangerButton("Exit EchoViz");
        exitButton.setOnClickListener(v -> showExitConfirmation());
        LinearLayout.LayoutParams exitParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        exitParams.setMargins(dp(4), dp(8), dp(4), dp(4));
        menu.addView(exitButton, exitParams);

        menuParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.START | Gravity.TOP;
        menuParams.x = handleParams.x + handleParams.width + dp(4);
        menuParams.y = handleParams.y;
        windowManager.addView(menu, menuParams);
        positionMenu();
        menu.post(this::positionMenu);
    }

    private void removeMenu() {
        if (menu != null) {
            windowManager.removeView(menu);
            menu = null;
            percentButton = null;
            magLensButton = null;
        }
    }

    private void positionMenu() {
        if (menu == null || menuParams == null || handleParams == null) {
            return;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int menuWidth = menu.getWidth() > 0 ? menu.getWidth() : dp(300);
        int menuHeight = menu.getHeight() > 0 ? menu.getHeight() : dp(220);
        boolean handleOnLeft = handleParams.x + handleParams.width / 2 < metrics.widthPixels / 2;
        int targetX = handleOnLeft
                ? handleParams.x + handleParams.width + dp(4)
                : handleParams.x - menuWidth - dp(4);
        int maxX = Math.max(0, metrics.widthPixels - menuWidth - dp(8));
        int maxY = Math.max(dp(24), metrics.heightPixels - menuHeight - dp(24));

        menuParams.x = Math.max(0, Math.min(maxX, targetX));
        menuParams.y = Math.max(dp(24), Math.min(maxY, handleParams.y));
        windowManager.updateViewLayout(menu, menuParams);
    }

    private void showExitConfirmation() {
        if (exitDialog != null) {
            return;
        }

        removeMenu();

        exitDialog = new LinearLayout(this);
        exitDialog.setOrientation(LinearLayout.VERTICAL);
        exitDialog.setPadding(dp(16), dp(16), dp(16), dp(14));
        exitDialog.setBackgroundResource(R.drawable.overlay_menu);

        TextView title = new TextView(this);
        title.setText("Close EchoViz?");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        exitDialog.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView message = new TextView(this);
        message.setText("This will turn off the floating EchoViz button.");
        message.setTextColor(Color.WHITE);
        message.setTextSize(17);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, dp(8), 0, dp(12));
        exitDialog.addView(message, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button cancel = menuButton("Cancel");
        cancel.setOnClickListener(v -> hideExitConfirmation());
        actions.addView(cancel);

        Button close = dangerButton("Close");
        close.setOnClickListener(v -> closeEchoViz());
        actions.addView(close);

        exitDialog.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        exitDialogParams = new WindowManager.LayoutParams(
                Math.min(metrics.widthPixels - dp(48), dp(360)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        exitDialogParams.gravity = Gravity.CENTER;
        windowManager.addView(exitDialog, exitDialogParams);
    }

    private void hideExitConfirmation() {
        if (exitDialog != null) {
            windowManager.removeView(exitDialog);
            exitDialog = null;
            exitDialogParams = null;
        }
    }

    private void closeEchoViz() {
        EchoVizRuntime.setActive(this, false);
        if (isMagLensMode()) {
            getMagnificationController().reset(true);
            magLensActive = false;
        }
        hideExitConfirmation();
        hideMagLensExitButton();
        removeMenu();
        hideHandle();
        Toast.makeText(this, "EchoViz closed", Toast.LENGTH_SHORT).show();
        finishEchoVizTasks();
    }

    private void finishEchoVizTasks() {
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (activityManager == null) {
            return;
        }

        for (ActivityManager.AppTask task : activityManager.getAppTasks()) {
            task.finishAndRemoveTask();
        }
    }

    private void changeFontScale(int direction) {
        if (!ensureCanWriteSettings()) {
            return;
        }
        float current = FontScaleBaseline.requestedRelative(this);
        int index = nearestRelativeScaleIndex(current);
        int next = Math.max(0, Math.min(RELATIVE_FONT_SCALES.length - 1, index + direction));
        setRelativeFontScale(RELATIVE_FONT_SCALES[next]);
    }

    private void setRelativeFontScale(float relativeScale) {
        if (!ensureCanWriteSettings()) {
            return;
        }
        float scale = FontScaleBaseline.scaleForRelative(this, relativeScale);
        boolean updated = Settings.System.putFloat(
                getContentResolver(),
                Settings.System.FONT_SCALE,
                scale
        );
        if (updated) {
            float appliedRelativeScale = FontScaleBaseline.saveRequestedRelativeForAbsolute(this, scale);
            Toast.makeText(this, "Text size " + FontScaleBaseline.percentLabel(appliedRelativeScale), Toast.LENGTH_SHORT).show();
            if (percentButton != null) {
                percentButton.setText(FontScaleBaseline.percentLabel(appliedRelativeScale));
            }
        } else {
            Toast.makeText(this, "Couldn't change text size", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean ensureCanWriteSettings() {
        if (Settings.System.canWrite(this)) {
            return true;
        }
        Toast.makeText(this, "Allow EchoViz to change system text size", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return false;
    }

    private int nearestRelativeScaleIndex(float scale) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < RELATIVE_FONT_SCALES.length; i++) {
            float distance = Math.abs(RELATIVE_FONT_SCALES[i] - scale);
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    private String requestedRelativeFontLabel() {
        return FontScaleBaseline.percentLabel(FontScaleBaseline.requestedRelative(this));
    }

    private void openReadingMode() {
        String text = extractVisibleText();
        if (text.trim().isEmpty()) {
            text = "Reading Mode\n\nEchoViz could not read text from this screen. Try sharing text or a webpage into EchoViz instead.";
        }
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_READER_TEXT, text);
        intent.putExtra(ReaderActivity.EXTRA_RETURN_TO_SOURCE_TASK, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        removeMenu();
    }

    private String extractVisibleText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return "";
        }
        Set<String> lines = new LinkedHashSet<>();
        collectText(root, lines);
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() + line.length() > 12000) {
                break;
            }
            builder.append(line).append("\n\n");
        }
        return builder.toString().trim();
    }

    private void collectText(AccessibilityNodeInfo node, Set<String> lines) {
        if (node == null || !node.isVisibleToUser()) {
            return;
        }
        CharSequence text = node.getText();
        addLine(lines, text);
        if (text == null || text.length() == 0) {
            addLine(lines, node.getContentDescription());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectText(node.getChild(i), lines);
        }
    }

    private void addLine(Set<String> lines, CharSequence value) {
        if (value == null) {
            return;
        }
        String line = value.toString().trim();
        if (line.length() >= 2 && !line.equals("EchoViz controls")) {
            lines.add(line);
        }
    }

    private void toggleMagnification() {
        MagnificationController controller = getMagnificationController();
        if (isMagLensMode()) {
            controller.reset(true);
            magLensActive = false;
            if (magLensButton != null) {
                magLensButton.setText("MagLens");
            }
            restoreNormalHandlePosition();
            updateHandleForMagLensMode(false);
            hideMagLensExitButton();
            Toast.makeText(this, "Back to page", Toast.LENGTH_SHORT).show();
            removeMenu();
            return;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float centerX = metrics.widthPixels / 2f;
        float centerY = metrics.heightPixels / 2f;
        boolean scaled = controller.setScale(MAG_LENS_SCALE, false);
        boolean centered = controller.setCenter(
                centerX,
                centerY,
                false
        );
        boolean changed = scaled || centered;
        if (changed) {
            saveNormalHandlePosition();
        }
        if (changed && magLensButton != null) {
            magLensButton.setText("Back to page");
        }
        if (changed) {
            magLensActive = true;
            showMagLensExitButton();
            positionHandleInMagLens(centerX, centerY, MAG_LENS_SCALE);
            positionMagLensExitButton(centerX, centerY, MAG_LENS_SCALE);
            updateHandleForMagLensMode(true);
            scheduleHandleInMagLens();
            removeMenu();
        }
        Toast.makeText(this,
                changed ? "MagLens 2x. Drag center button to pan; tap X to close." : "MagLens unavailable",
                Toast.LENGTH_LONG
        ).show();
    }

    private boolean isMagnified() {
        return getMagnificationController().getScale() > 1.1f;
    }

    private boolean isMagLensMode() {
        return magLensActive || isMagnified();
    }

    private void updateHandleForMagLensMode(boolean active) {
        if (handle == null) {
            return;
        }

        if (active) {
            handle.setAlpha(0.42f);
            handle.setForeground(null);
            handle.setContentDescription("MagLens active. Drag center button to pan.");
        } else {
            handle.setAlpha(1.0f);
            handle.setForeground(null);
            handle.setContentDescription("EchoViz controls");
        }
    }

    private void saveNormalHandlePosition() {
        if (handleParams == null) {
            return;
        }

        normalHandleX = handleParams.x;
        normalHandleY = handleParams.y;
    }

    private void restoreNormalHandlePosition() {
        if (handle == null || handleParams == null) {
            return;
        }

        handleParams.x = normalHandleX;
        handleParams.y = clampY(normalHandleY);
        windowManager.updateViewLayout(handle, handleParams);
    }

    private void positionHandleInMagLens() {
        if (handle == null || handleParams == null) {
            return;
        }

        MagnificationController controller = getMagnificationController();
        positionHandleInMagLens(
                controller.getCenterX(),
                controller.getCenterY(),
                Math.max(1.0f, controller.getScale())
        );
    }

    private void positionHandleInMagLens(float centerX, float centerY, float scale) {
        if (handle == null || handleParams == null) {
            return;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float targetX = centerX - handleParams.width / 2f;
        float targetY = centerY - handleParams.height / 2f;
        handleParams.x = Math.round(clamp(targetX, 0, metrics.widthPixels - handleParams.width));
        handleParams.y = Math.round(clamp(targetY, dp(24), metrics.heightPixels - handleParams.height));
        windowManager.updateViewLayout(handle, handleParams);
    }

    private void showMagLensExitButton() {
        if (magLensExitButton != null) {
            return;
        }

        magLensExitButton = new ImageView(this);
        magLensExitButton.setImageResource(R.drawable.ic_close_badge);
        magLensExitButton.setBackgroundResource(R.drawable.maglens_exit_button);
        magLensExitButton.setPadding(dp(9), dp(9), dp(9), dp(9));
        magLensExitButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        magLensExitButton.setAlpha(0.78f);
        magLensExitButton.setClickable(true);
        magLensExitButton.setElevation(dp(12));
        magLensExitButton.setContentDescription("Close MagLens");
        magLensExitButton.setOnClickListener(v -> toggleMagnification());

        magLensExitParams = new WindowManager.LayoutParams(
                dp(42),
                dp(42),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        magLensExitParams.gravity = Gravity.START | Gravity.TOP;
        windowManager.addView(magLensExitButton, magLensExitParams);
    }

    private void hideMagLensExitButton() {
        if (magLensExitButton != null) {
            windowManager.removeView(magLensExitButton);
            magLensExitButton = null;
            magLensExitParams = null;
        }
    }

    private void positionMagLensExitButton() {
        if (magLensExitButton == null || magLensExitParams == null) {
            return;
        }

        MagnificationController controller = getMagnificationController();
        positionMagLensExitButton(
                controller.getCenterX(),
                controller.getCenterY(),
                Math.max(1.0f, controller.getScale())
        );
    }

    private void positionMagLensExitButton(float centerX, float centerY, float scale) {
        if (magLensExitButton == null || magLensExitParams == null) {
            return;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float viewportWidth = metrics.widthPixels / scale;
        float viewportHeight = metrics.heightPixels / scale;
        float viewportLeft = centerX - viewportWidth / 2f;
        float viewportTop = centerY - viewportHeight / 2f;
        float targetX = viewportLeft + viewportWidth - magLensExitParams.width - dp(16);
        float targetY = viewportTop + dp(28);

        magLensExitParams.x = Math.round(clamp(targetX, 0, metrics.widthPixels - magLensExitParams.width));
        magLensExitParams.y = Math.round(clamp(targetY, dp(24), metrics.heightPixels - magLensExitParams.height));
        windowManager.updateViewLayout(magLensExitButton, magLensExitParams);
    }

    private void scheduleHandleInMagLens() {
        if (handle == null) {
            return;
        }

        handle.postDelayed(() -> {
            if (magLensActive) {
                positionHandleInMagLens();
                positionMagLensExitButton();
            }
        }, 250);
        handle.postDelayed(() -> {
            if (magLensActive) {
                positionHandleInMagLens();
                positionMagLensExitButton();
            }
        }, 700);
    }

    private void panMagnificationBy(float deltaX, float deltaY) {
        MagnificationController controller = getMagnificationController();
        float scale = Math.max(1.0f, controller.getScale());
        DisplayMetrics metrics = getResources().getDisplayMetrics();

        float viewportWidth = metrics.widthPixels / scale;
        float viewportHeight = metrics.heightPixels / scale;

        float nextX = controller.getCenterX() + deltaX;
        float nextY = controller.getCenterY() + deltaY;

        float minX = viewportWidth / 2f;
        float maxX = metrics.widthPixels - viewportWidth / 2f;
        float minY = viewportHeight / 2f;
        float maxY = metrics.heightPixels - viewportHeight / 2f;

        nextX = clamp(nextX, minX, maxX);
        nextY = clamp(nextY, minY, maxY);

        boolean moved = controller.setCenter(nextX, nextY, false);
        if (!moved) {
            Toast.makeText(this, "MagLens edge", Toast.LENGTH_SHORT).show();
        } else {
            positionHandleInMagLens(nextX, nextY, scale);
            positionMagLensExitButton(nextX, nextY, scale);
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private Button menuButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.button_round);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        button.setLayoutParams(params);
        return button;
    }

    private Button dangerButton(String label) {
        Button button = menuButton(label);
        button.setBackgroundResource(R.drawable.button_danger);
        return button;
    }

    private int clampY(int y) {
        int max = getResources().getDisplayMetrics().heightPixels - dp(64);
        return Math.max(dp(24), Math.min(max, y));
    }

    private int clampX(int x) {
        int handleWidth = handleParams != null ? handleParams.width : dp(52);
        int max = getResources().getDisplayMetrics().widthPixels - handleWidth;
        return Math.max(0, Math.min(max, x));
    }

    private void snapHandleToNearestSide() {
        if (handle == null || handleParams == null) {
            return;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        boolean snapLeft = handleParams.x + handleParams.width / 2 < metrics.widthPixels / 2;
        handleParams.x = snapLeft ? 0 : metrics.widthPixels - handleParams.width;
        handleParams.y = clampY(handleParams.y);
        windowManager.updateViewLayout(handle, handleParams);
        if (menu != null) {
            positionMenu();
        }
    }

    private void saveHandlePosition() {
        if (handleParams == null) {
            return;
        }

        getSharedPreferences(OVERLAY_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_HANDLE_X, handleParams.x)
                .putInt(KEY_HANDLE_Y, handleParams.y)
                .apply();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
