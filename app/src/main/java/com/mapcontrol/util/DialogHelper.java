package com.mapcontrol.util;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

public class DialogHelper {
    private DialogHelper() {}

    public interface StringConsumer {
        void accept(String value);
    }

    public static void showLegalDisclaimer(Context context, Runnable onAccept, Runnable onDecline) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LinearLayout mainContainer = createBaseContainer(context);
        LinearLayout titleContainer = createTitleContainer(context,
                context.getString(R.string.dialog_welcome_title),
                context.getString(R.string.dialog_legal_subtitle));
        mainContainer.addView(titleContainer);

        LinearLayout contentContainer = createContentContainer(context);
        String disclaimerText = context.getString(R.string.dialog_legal_disclaimer);
        TextView messageView = createMessage(context, disclaimerText);
        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));
        scrollView.addView(messageView);
        contentContainer.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        mainContainer.addView(contentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        builder.setView(mainContainer);
        builder.setPositiveButton(R.string.dialog_accept, (d, w) -> onAccept.run());
        builder.setNegativeButton(R.string.dialog_decline, (d, w) -> onDecline.run());
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        styleDialog(dialog);
        dialog.show();
    }

    public static void showAppManagementDisclaimer(Context context, Runnable onAccept, Runnable onDecline) {
        showResponsibilityDialog(
                context,
                context.getString(R.string.dialog_app_mgmt_title),
                context.getString(R.string.dialog_app_mgmt_disclaimer),
                onAccept,
                onDecline);
    }

    /** Kamera 360, Otomasyon ve Yüz Tanıma için tek seferlik erken erişim onayı. */
    public static void showEarlyAccessDisclaimer(Context context, String title, Runnable onAccept, Runnable onDecline) {
        showResponsibilityDialog(
                context,
                title,
                context.getString(R.string.dialog_early_access_disclaimer),
                onAccept,
                onDecline);
    }

    /** Dosya gezgininde serbest gezinmeyi açmadan önce her seferinde gösterilen sorumluluk reddi. */
    public static void showFileBrowserFreeModeDisclaimer(Context context, Runnable onAccept) {
        showResponsibilityDialog(
                context,
                context.getString(R.string.file_browser_free_mode_title),
                context.getString(R.string.file_browser_free_mode_disclaimer),
                onAccept,
                () -> {});
    }

    /**
     * Seçilen kaydırma hareketi başka bir özellikte kullanıldığında uyarır.
     *
     * @param ownerLabelRes hareketi kullanan özelliğin ad kaynağı
     */
    public static void showGestureConflict(Context context, int ownerLabelRes) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LinearLayout mainContainer = createBaseContainer(context);
        mainContainer.addView(createTitleContainer(context,
                context.getString(R.string.gesture_conflict_title),
                context.getString(R.string.gesture_conflict_subtitle)));

        LinearLayout contentContainer = createContentContainer(context);
        contentContainer.addView(createMessage(context,
                context.getString(R.string.gesture_conflict_message, context.getString(ownerLabelRes))));
        mainContainer.addView(contentContainer);

        builder.setView(mainContainer);
        builder.setPositiveButton(R.string.gesture_conflict_ok, (d, w) -> d.dismiss());
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();
        styleDialog(dialog);
        dialog.show();
    }

    /** Başlık + alt başlık + kaydırılabilir metin; [positive] onAccept'i çalıştırır, Geri kapatır. */
    public static void showConfirm(Context context, String title, String subtitle, String message,
            int positive, Runnable onAccept) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LinearLayout mainContainer = createBaseContainer(context);
        mainContainer.addView(createTitleContainer(context, title, subtitle));
        LinearLayout contentContainer = createContentContainer(context);
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(createMessage(context, message));
        contentContainer.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        mainContainer.addView(contentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        builder.setView(mainContainer);
        builder.setPositiveButton(positive, (d, w) -> onAccept.run());
        builder.setNegativeButton(R.string.dialog_back, (d, w) -> d.dismiss());
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();
        styleDialog(dialog);
        dialog.show();
    }

    /**
     * Onay düğmesi {@code seconds} saniye kilitli kalan uyarı penceresi (düğmede geri sayım görünür).
     * Dışarı dokunarak kapanmaz; İptal ya da Geri tuşu {@code onCancel}'ı, onay {@code onConfirm}'ı çalıştırır.
     * İkisinden tam olarak biri, bir kez çağrılır.
     */
    public static void showTimedConfirm(Context context, String title, String message, int seconds,
            Runnable onConfirm, Runnable onCancel) {
        showTimedConfirm(context, title, message, seconds, null, false, onConfirm, onCancel);
    }

    /**
     * {@code danger} iken başlık ve onay düğmesi kırmızıdır (geri alınması zor işlemler için).
     * {@code confirmLabelOrNull} null ise "Onayla" yazar.
     */
    public static void showTimedConfirm(Context context, String title, String message, int seconds,
            String confirmLabelOrNull, boolean danger, Runnable onConfirm, Runnable onCancel) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LinearLayout mainContainer = createBaseContainer(context);
        LinearLayout titleContainer = createTitleContainer(context, danger ? "\u26A0  " + title : title, null);
        if (danger) {
            ((TextView) titleContainer.getChildAt(0)).setTextColor(UiStyles.color(context, R.color.statusErrorBright));
        }
        mainContainer.addView(titleContainer);
        LinearLayout contentContainer = createContentContainer(context);
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(createMessage(context, message));
        contentContainer.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        mainContainer.addView(contentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        final boolean[] confirmed = {false};
        String confirmLabel = confirmLabelOrNull != null ? confirmLabelOrNull : context.getString(R.string.dialog_confirm);
        builder.setView(mainContainer);
        builder.setPositiveButton(confirmLabel, (d, w) -> confirmed[0] = true);
        builder.setNegativeButton(R.string.wifi_cancel, (d, w) -> d.dismiss());
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        styleDialog(dialog, danger);

        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final int[] left = {Math.max(0, seconds)};
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (positive == null) {
                    return;
                }
                if (left[0] <= 0) {
                    positive.setText(confirmLabel);
                    positive.setEnabled(true);
                    positive.setAlpha(1f);
                    return;
                }
                positive.setText(confirmLabel + " (" + left[0] + ")");
                positive.setEnabled(false);
                positive.setAlpha(0.5f);
                left[0]--;
                handler.postDelayed(this, 1000);
            }
        };
        dialog.setOnDismissListener(d -> {
            handler.removeCallbacks(tick);
            if (confirmed[0]) {
                onConfirm.run();
            } else {
                onCancel.run();
            }
        });
        dialog.show();
        // styleDialog OnShowListener'ı düğmeleri boyadıktan sonra geri sayım başlar.
        tick.run();
    }

    /** Salt okunur uygulama listesi satırı. */
    public static final class AppRow {
        public final android.graphics.drawable.Drawable icon;
        public final String label;
        public final String packageName;

        public AppRow(android.graphics.drawable.Drawable icon, String label, String packageName) {
            this.icon = icon;
            this.label = label;
            this.packageName = packageName;
        }
    }

    /**
     * Yalnızca görüntülenen (seçim yok) uygulama listesi; altında {@code note}. Liste boşsa {@code emptyText} gösterilir ve
     * devam düğmesi kapalıdır. Dışarı dokunarak kapanmaz; {@code onContinue} ya da {@code onCancel} tam bir kez çağrılır.
     */
    public static void showAppListConfirm(Context context, String title, String note, String emptyText,
            java.util.List<AppRow> rows, int continueRes, Runnable onContinue, Runnable onCancel) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LinearLayout mainContainer = createBaseContainer(context);
        mainContainer.addView(createTitleContainer(context, title, null));
        LinearLayout contentContainer = createContentContainer(context);

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        float density = context.getResources().getDisplayMetrics().density;
        int iconPx = Math.round(40 * density);
        if (rows.isEmpty()) {
            list.addView(createMessage(context, emptyText));
        }
        for (AppRow row : rows) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(android.view.Gravity.CENTER_VERTICAL);
            line.setPadding(0, Math.round(6 * density), 0, Math.round(6 * density));
            android.widget.ImageView icon = new android.widget.ImageView(context);
            if (row.icon != null) {
                icon.setImageDrawable(row.icon);
            }
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconPx, iconPx);
            iconLp.rightMargin = Math.round(14 * density);
            line.addView(icon, iconLp);
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView label = new TextView(context);
            label.setText(row.label);
            label.setTextSize(16);
            label.setTextColor(UiStyles.color(context, R.color.textPrimary));
            texts.addView(label);
            TextView pkg = new TextView(context);
            pkg.setText(row.packageName);
            pkg.setTextSize(12);
            pkg.setTextColor(UiStyles.color(context, R.color.textHint));
            texts.addView(pkg);
            line.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            list.addView(line);
        }
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(list);
        int maxHeight = Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.45f);
        contentContainer.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, rows.size() > 6 ? maxHeight : LinearLayout.LayoutParams.WRAP_CONTENT));
        if (note != null) {
            TextView noteView = createMessage(context, note);
            noteView.setTextSize(13);
            noteView.setTextColor(UiStyles.color(context, R.color.textHint));
            noteView.setPadding(0, Math.round(10 * density), 0, 0);
            contentContainer.addView(noteView);
        }
        mainContainer.addView(contentContainer);

        final boolean[] confirmed = {false};
        builder.setView(mainContainer);
        builder.setPositiveButton(continueRes, (d, w) -> confirmed[0] = true);
        builder.setNegativeButton(R.string.wifi_cancel, (d, w) -> d.dismiss());
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        styleDialog(dialog);
        dialog.setOnDismissListener(d -> {
            if (confirmed[0]) {
                onContinue.run();
            } else {
                onCancel.run();
            }
        });
        dialog.show();
        android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null && rows.isEmpty()) {
            positive.setEnabled(false);
            positive.setAlpha(0.5f);
        }
    }

    /**
     * Şifre + tekrar penceresi. Şifre {@code minLength}'ten kısaysa ya da iki alan farklıysa pencere kapanmaz, hata yazar.
     * {@code onSave} girilen şifreyle, iptalde {@code onCancel} çağrılır (tam biri, bir kez).
     */
    public static void showPasswordSetup(Context context, String title, String message, int minLength,
            int saveRes, StringConsumer onSave, Runnable onCancel) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LinearLayout mainContainer = createBaseContainer(context);
        mainContainer.addView(createTitleContainer(context, title, null));
        LinearLayout contentContainer = createContentContainer(context);
        contentContainer.addView(createMessage(context, message));
        android.widget.EditText first = createPasswordField(context, R.string.service_mode_password_hint);
        android.widget.EditText second = createPasswordField(context, R.string.service_mode_password_repeat_hint);
        contentContainer.addView(first);
        contentContainer.addView(second);
        TextView error = new TextView(context);
        error.setTextColor(UiStyles.color(context, R.color.statusErrorBright));
        error.setTextSize(14);
        error.setVisibility(android.view.View.GONE);
        contentContainer.addView(error);
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(contentContainer);
        mainContainer.addView(scrollView);

        final String[] saved = {null};
        builder.setView(mainContainer);
        builder.setPositiveButton(saveRes, null);
        builder.setNegativeButton(R.string.wifi_cancel, (d, w) -> d.dismiss());
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        styleDialog(dialog);
        dialog.setOnDismissListener(d -> {
            if (saved[0] != null) {
                onSave.accept(saved[0]);
            } else {
                onCancel.run();
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            // Ekran klavyesi açılınca alanlar görünür kalsın.
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = first.getText().toString().trim();
            String b = second.getText().toString().trim();
            if (a.length() < minLength) {
                error.setText(context.getString(R.string.service_mode_password_too_short, minLength));
                error.setVisibility(android.view.View.VISIBLE);
                return;
            }
            if (!a.equals(b)) {
                error.setText(R.string.service_mode_password_mismatch);
                error.setVisibility(android.view.View.VISIBLE);
                return;
            }
            saved[0] = a;
            dialog.dismiss();
        });
    }

    private static android.widget.EditText createPasswordField(Context context, int hintRes) {
        android.widget.EditText field = new android.widget.EditText(context);
        field.setHint(hintRes);
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setTextColor(UiStyles.color(context, R.color.textPrimary));
        field.setHintTextColor(UiStyles.color(context, R.color.textHint));
        return field;
    }

    /** Tek düğmeli bilgi penceresi. */
    public static void showInfo(Context context, String title, String message) {
        AlertDialog dialog = buildInfo(context, title, message);
        dialog.show();
    }

    /**
     * Etkinlik olmadan (yayın alıcısından) her şeyin üstünde bilgi penceresi. Üstte gösterme izni yoksa ya da pencere
     * eklenemezse false döner; çağıran başka yolla (Toast) bildirmeli. Ana iş parçacığında çağrılmalı.
     */
    public static boolean showOverlayInfo(Context context, String title, String message) {
        Context app = context.getApplicationContext();
        if (!android.provider.Settings.canDrawOverlays(app)) return false;
        try {
            Context themed = new android.view.ContextThemeWrapper(app, R.style.Theme_MapControl);
            AlertDialog dialog = buildInfo(themed, title, message);
            if (dialog.getWindow() == null) return false;
            dialog.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            dialog.show();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static AlertDialog buildInfo(Context context, String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LinearLayout mainContainer = createBaseContainer(context);
        mainContainer.addView(createTitleContainer(context, title, null));
        LinearLayout contentContainer = createContentContainer(context);
        contentContainer.addView(createMessage(context, message));
        mainContainer.addView(contentContainer);
        builder.setView(mainContainer);
        builder.setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss());
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();
        styleDialog(dialog);
        return dialog;
    }

    private static void showResponsibilityDialog(
            Context context,
            String title,
            String disclaimerText,
            Runnable onAccept,
            Runnable onDecline) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LinearLayout mainContainer = createBaseContainer(context);
        LinearLayout titleContainer = createTitleContainer(context, title,
                context.getString(R.string.dialog_legal_subtitle));
        mainContainer.addView(titleContainer);

        LinearLayout contentContainer = createContentContainer(context);
        TextView messageView = createMessage(context, disclaimerText);
        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));
        scrollView.addView(messageView);
        contentContainer.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        mainContainer.addView(contentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        builder.setView(mainContainer);
        builder.setPositiveButton(R.string.dialog_accept, (d, w) -> onAccept.run());
        builder.setNegativeButton(R.string.dialog_back, (d, w) -> onDecline.run());
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();
        styleDialog(dialog);
        dialog.show();
    }

    public static void showSafetyWarningDialog(Context context, String settingKey, int value, Runnable onConfirm) {
        String title = settingKey.equals("fcwSetting")
                ? context.getString(R.string.dialog_safety_fcw_title)
                : context.getString(R.string.dialog_safety_aeb_title);
        String message = context.getString(R.string.dialog_safety_message);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LinearLayout dialogLayout = new LinearLayout(context);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(48, 40, 48, 24);
        dialogLayout.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(20);
        titleView.setTextColor(UiStyles.color(context, R.color.accentHighlight));
        UiStyles.setFontStyle(titleView, android.graphics.Typeface.BOLD);
        titleView.setGravity(android.view.Gravity.CENTER);
        dialogLayout.addView(titleView);

        TextView messageView = new TextView(context);
        messageView.setText(message);
        messageView.setTextSize(15);
        messageView.setTextColor(UiStyles.color(context, R.color.textMessage));
        messageView.setPadding(0, 32, 0, 32);
        messageView.setLineSpacing(6, 1);
        dialogLayout.addView(messageView);

        builder.setView(dialogLayout);
        builder.setPositiveButton(R.string.dialog_accept, (d, w) -> onConfirm.run());
        builder.setNegativeButton(R.string.wifi_cancel, (d, w) -> d.dismiss());
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(UiStyles.color(context, R.color.surfaceColor)));
        }
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(UiStyles.color(context, R.color.accentHighlight));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(UiStyles.color(context, R.color.textDialogButtonSecondary));
    }

    public static void showAppSelectionDialog(
            Context context,
            String titleText,
            String[] items,
            java.util.List<String> sortedPackages,
            String preferredPackageOrNull,
            StringConsumer onPackageSelected,
            Runnable onAutoSelect,
            Runnable onClear
    ) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(titleText);

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                context, android.R.layout.simple_list_item_1, items) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View view = super.getView(position, convertView, parent);
                android.widget.TextView textView = (android.widget.TextView) view.findViewById(android.R.id.text1);
                if (textView != null) {
                    textView.setTextColor(UiStyles.color(context, R.color.accentHighlight));
                    textView.setTextSize(16);
                }
                return view;
            }
        };

        builder.setAdapter(adapter, (dialog, which) -> {
            if (sortedPackages == null || which < 0 || which >= sortedPackages.size()) return;
            onPackageSelected.accept(sortedPackages.get(which));
        });

        if (preferredPackageOrNull != null) {
            builder.setPositiveButton(R.string.dialog_auto_select, (dialog, which) -> onAutoSelect.run());
        }
        builder.setNegativeButton(R.string.wifi_cancel, null);
        builder.setNeutralButton(R.string.common_clear, (dialog, which) -> onClear.run());

        AlertDialog dialog = builder.create();
        dialog.show();

        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(UiStyles.color(context, R.color.buttonSuccessBright));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(17);
            UiStyles.setFontStyle(dialog.getButton(AlertDialog.BUTTON_POSITIVE), android.graphics.Typeface.BOLD);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(UiStyles.color(context, R.color.statusErrorBright));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(17);
            UiStyles.setFontStyle(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), android.graphics.Typeface.BOLD);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(UiStyles.color(context, R.color.textLoading));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextSize(17);
            UiStyles.setFontStyle(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), android.graphics.Typeface.BOLD);
        }
    }

    private static LinearLayout createBaseContainer(Context context) {
        LinearLayout mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));
        return mainContainer;
    }

    private static LinearLayout createTitleContainer(Context context, String title, String subtitle) {
        LinearLayout titleContainer = new LinearLayout(context);
        titleContainer.setOrientation(LinearLayout.VERTICAL);
        titleContainer.setPadding(32, 32, 32, 24);
        titleContainer.setBackgroundColor(UiStyles.color(context, R.color.surfaceCard));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(24);
        titleView.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.setFontStyle(titleView, android.graphics.Typeface.BOLD);
        titleContainer.addView(titleView);
        if (subtitle == null) {
            return titleContainer;
        }

        TextView subtitleView = new TextView(context);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(16);
        subtitleView.setTextColor(UiStyles.color(context, R.color.accentHighlight));
        subtitleView.setPadding(0, 8, 0, 0);
        titleContainer.addView(subtitleView);
        return titleContainer;
    }

    private static LinearLayout createContentContainer(Context context) {
        LinearLayout contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(32, 24, 32, 24);
        contentContainer.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));
        return contentContainer;
    }

    private static TextView createMessage(Context context, String text) {
        TextView messageView = new TextView(context);
        messageView.setText(MarkdownUtil.parse(text));
        messageView.setTextSize(15);
        messageView.setTextColor(UiStyles.color(context, R.color.textMessage));
        messageView.setLineSpacing(12, 1.4f);
        return messageView;
    }

    private static void styleDialog(AlertDialog dialog) {
        styleDialog(dialog, false);
    }

    /** {@code danger}: onay düğmesi kırmızı zemin, beyaz yazı. */
    private static void styleDialog(AlertDialog dialog, boolean danger) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(UiStyles.color(dialog.getContext(), R.color.surfaceColor)));
        }
        dialog.setOnShowListener(dialogInterface -> {
            android.widget.Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setTextColor(danger ? 0xFFFFFFFF : UiStyles.color(dialog.getContext(), R.color.textPrimary));
                positiveButton.setTextSize(16);
                UiStyles.setFontStyle(positiveButton, android.graphics.Typeface.BOLD);
                android.graphics.drawable.GradientDrawable positiveBg = new android.graphics.drawable.GradientDrawable();
                positiveBg.setColor(UiStyles.color(dialog.getContext(), danger ? R.color.statusError : R.color.buttonPrimary));
                if (danger) {
                    // Material teması düğmeyi kendi rengiyle boyar (tint); kırmızı görünsün diye tint de kırmızı.
                    positiveButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            UiStyles.color(dialog.getContext(), R.color.statusError)));
                }
                positiveBg.setCornerRadius(12);
                positiveButton.setBackground(positiveBg);
            }
            android.widget.Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTextColor(UiStyles.color(dialog.getContext(), R.color.textSecondary));
                negativeButton.setBackgroundColor(UiStyles.color(dialog.getContext(), R.color.transparent));
                negativeButton.setTextSize(16);
                UiStyles.setFontStyle(negativeButton, android.graphics.Typeface.NORMAL);
            }
        });
    }
}
