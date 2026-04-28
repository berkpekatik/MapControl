package com.mapcontrol.util;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;

public class DialogHelper {
    private DialogHelper() {}

    public interface StringConsumer {
        void accept(String value);
    }

    public static void showLegalDisclaimer(Context context, Runnable onAccept, Runnable onDecline) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LinearLayout mainContainer = createBaseContainer(context);
        LinearLayout titleContainer = createTitleContainer(context, "Hoş Geldiniz", "Yasal Uyarı ve Kullanım Koşulları");
        mainContainer.addView(titleContainer);

        LinearLayout contentContainer = createContentContainer(context);
        String disclaimerText = "### Yasal Uyarı ve Sorumluluk Reddi\n" +
                "1. **Ücretsiz Dağıtım:** Bu yazılım, herhangi bir ücret talep edilmeksizin tamamen ücretsiz olarak dağıtılmaktadır. Yazılım içinde belirtilen içerikler ayrı bir ücret karşılığında satılmaz.\n" +
                "2. **Kullanıcı Onayı ve Risk Kabulü:** Kullanıcı, cihazın bellek (hafıza) ayarlarını veya araç konfigürasyonlarını kendi rızasıyla ve bilinciyle değiştirdiğini onaylar.\n" +
                "3. **Sorumluluk Reddi:** Geliştirici, bu değişiklikler veya uygulamanın kullanımı sonucunda ortaya çıkabilecek hiçbir doğrudan veya dolaylı zarardan, veri kaybından veya arızadan **sorumlu değildir ve hiçbir yükümlülük kabul etmez.**\n" +
                "**Onay:** Lütfen uygulamayı kullanmaya başlamadan önce yukarıdaki tüm bilgileri **okuduğunuzu, anladığınızu ve kabul ettiğinizi** onaylayın.";
        TextView messageView = createMessage(context, disclaimerText);
        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(ContextCompat.getColor(context, R.color.backgroundPage));
        scrollView.addView(messageView);
        contentContainer.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        mainContainer.addView(contentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        builder.setView(mainContainer);
        builder.setPositiveButton("Kabul Ediyorum", (d, w) -> onAccept.run());
        builder.setNegativeButton("Kabul Etmiyorum", (d, w) -> onDecline.run());
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        styleDialog(dialog);
        dialog.show();
    }

    public static void showAppManagementDisclaimer(Context context, Runnable onAccept, Runnable onDecline) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LinearLayout mainContainer = createBaseContainer(context);
        LinearLayout titleContainer = createTitleContainer(context, "Uygulama Yönetimi", "Yasal Uyarı ve Sorumluluk Reddi");
        mainContainer.addView(titleContainer);

        LinearLayout contentContainer = createContentContainer(context);
        String disclaimerText = "Uygulama yükleme ve kaldırma işlemleri tamamen kullanıcının sorumluluğundadır. Geliştirici, kullanıcının yüklediği veya kaldırdığı uygulamalardan kaynaklanan hiçbir sorumluluğu kabul etmez.";
        TextView messageView = createMessage(context, disclaimerText);
        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(ContextCompat.getColor(context, R.color.backgroundPage));
        scrollView.addView(messageView);
        contentContainer.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        mainContainer.addView(contentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        builder.setView(mainContainer);
        builder.setPositiveButton("Kabul Ediyorum", (d, w) -> onAccept.run());
        builder.setNegativeButton("Geri", (d, w) -> onDecline.run());
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();
        styleDialog(dialog);
        dialog.show();
    }

    public static void showSafetyWarningDialog(Context context, String settingKey, int value, Runnable onConfirm) {
        String title = settingKey.equals("fcwSetting") ? "Ön Çarpışma Uyarısı" : "Aktif Acil Fren Sistemi";
        String message = "Bu güvenlik özelliğini devre dışı bırakmak tamamen sizin sorumluluğunuzdadır.\n\n" +
                "Bu ayar, aracın güvenlik sistemlerini etkiler. Devre dışı bırakıldığında olası risklerden geliştirici sorumlu değildir.\n\n" +
                "Devam etmek istiyor musunuz?";

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LinearLayout dialogLayout = new LinearLayout(context);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(48, 40, 48, 24);
        dialogLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.backgroundPage));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(20);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.accentHighlight));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setGravity(android.view.Gravity.CENTER);
        dialogLayout.addView(titleView);

        TextView messageView = new TextView(context);
        messageView.setText(message);
        messageView.setTextSize(15);
        messageView.setTextColor(ContextCompat.getColor(context, R.color.textMessage));
        messageView.setPadding(0, 32, 0, 32);
        messageView.setLineSpacing(6, 1);
        dialogLayout.addView(messageView);

        builder.setView(dialogLayout);
        builder.setPositiveButton("Kabul Ediyorum", (d, w) -> onConfirm.run());
        builder.setNegativeButton("İptal", (d, w) -> d.dismiss());
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(context, R.color.accentHighlight));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(context, R.color.textDialogButtonSecondary));
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
                    textView.setTextColor(ContextCompat.getColor(context, R.color.accentHighlight));
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
            builder.setPositiveButton("Otomatik Seç", (dialog, which) -> onAutoSelect.run());
        }
        builder.setNegativeButton("İptal", null);
        builder.setNeutralButton("Temizle", (dialog, which) -> onClear.run());

        AlertDialog dialog = builder.create();
        dialog.show();

        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(context, R.color.buttonSuccessBright));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(17);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(null, android.graphics.Typeface.BOLD);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(context, R.color.statusErrorBright));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(17);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTypeface(null, android.graphics.Typeface.BOLD);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(ContextCompat.getColor(context, R.color.textLoading));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextSize(17);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTypeface(null, android.graphics.Typeface.BOLD);
        }
    }

    private static LinearLayout createBaseContainer(Context context) {
        LinearLayout mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setBackgroundColor(ContextCompat.getColor(context, R.color.backgroundPage));
        return mainContainer;
    }

    private static LinearLayout createTitleContainer(Context context, String title, String subtitle) {
        LinearLayout titleContainer = new LinearLayout(context);
        titleContainer.setOrientation(LinearLayout.VERTICAL);
        titleContainer.setPadding(32, 32, 32, 24);
        titleContainer.setBackgroundColor(ContextCompat.getColor(context, R.color.surfaceCard));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(24);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleContainer.addView(titleView);

        TextView subtitleView = new TextView(context);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(16);
        subtitleView.setTextColor(ContextCompat.getColor(context, R.color.accentHighlight));
        subtitleView.setPadding(0, 8, 0, 0);
        titleContainer.addView(subtitleView);
        return titleContainer;
    }

    private static LinearLayout createContentContainer(Context context) {
        LinearLayout contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(32, 24, 32, 24);
        contentContainer.setBackgroundColor(ContextCompat.getColor(context, R.color.backgroundPage));
        return contentContainer;
    }

    private static TextView createMessage(Context context, String text) {
        TextView messageView = new TextView(context);
        messageView.setText(text);
        messageView.setTextSize(15);
        messageView.setTextColor(ContextCompat.getColor(context, R.color.textMessage));
        messageView.setLineSpacing(12, 1.4f);
        return messageView;
    }

    private static void styleDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(ContextCompat.getColor(dialog.getContext(), R.color.transparent)));
        }
        dialog.setOnShowListener(dialogInterface -> {
            android.widget.Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setTextColor(ContextCompat.getColor(dialog.getContext(), R.color.textPrimary));
                positiveButton.setTextSize(16);
                positiveButton.setTypeface(null, android.graphics.Typeface.BOLD);
                android.graphics.drawable.GradientDrawable positiveBg = new android.graphics.drawable.GradientDrawable();
                positiveBg.setColor(ContextCompat.getColor(dialog.getContext(), R.color.buttonPrimary));
                positiveBg.setCornerRadius(12);
                positiveButton.setBackground(positiveBg);
            }
            android.widget.Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTextColor(ContextCompat.getColor(dialog.getContext(), R.color.textSecondary));
                negativeButton.setBackgroundColor(ContextCompat.getColor(dialog.getContext(), R.color.transparent));
                negativeButton.setTextSize(16);
                negativeButton.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        });
    }
}
