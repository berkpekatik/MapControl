package com.mapcontrol.ui.builder;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.mapcontrol.R;
import com.mapcontrol.api.ProfileApiService;
import com.mapcontrol.ui.theme.UiStyles;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Profil sekmesi: kullanıcı adı ve şifre ile giriş, çıkış.
 */
public class ProfileTabBuilder {

    public interface ProfileCallback {
        void log(String message);

        void onSessionChanged(boolean loggedIn, boolean premium);
    }

    private final Context context;
    private final ProfileApiService apiService;
    private final ProfileCallback callback;

    private TextView statusText;
    private TextView usernameLabel;
    private EditText usernameInput;
    private TextView passwordLabel;
    private EditText passwordInput;
    private Button loginButton;
    private Button registerButton;
    private Button logoutButton;
    private TextView noteText;
    private TextView avatar;
    private LinearLayout featuresCard;
    private TextView aiBadge;

    public ProfileTabBuilder(Context context, SharedPreferences prefs, ProfileApiService apiService,
            ProfileCallback callback) {
        this.context = context;
        this.apiService = apiService;
        this.callback = callback;
    }

    public ScrollView build() {
        boolean tall = tallLayout();
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));

        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.TOP);
        int side = dp(tall ? 22 : 16);
        int top = dp(tall ? 36 : 8);
        page.setPadding(side, top, side, dp(tall ? 36 : 16));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int inner = dp(tall ? 22 : 18);
        card.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(card);

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        avatar = new TextView(context);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setTextColor(UiStyles.color(context, R.color.textPrimary));
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(UiStyles.color(context, R.color.surfaceCard));
        avatarBg.setStroke(dp(1), UiStyles.color(context, R.color.outline));
        avatar.setBackground(avatarBg);
        head.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);

        TextView title = new TextView(context);
        title.setText(R.string.side_rail_profile);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, tall ? 26 : 22);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        titles.addView(title);

        statusText = new TextView(context);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusText.setTextColor(UiStyles.color(context, R.color.textSecondary));
        statusText.setLineSpacing(2f, 1.1f);
        statusText.setPadding(0, dp(4), 0, 0);
        titles.addView(statusText);
        head.addView(titles, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(head);

        usernameLabel = label(R.string.profile_username_label);
        LinearLayout.LayoutParams userLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        userLabelParams.setMargins(0, dp(18), 0, 0);
        card.addView(usernameLabel, userLabelParams);
        usernameInput = field(R.string.profile_hint_username,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        card.addView(usernameInput, fieldParams());

        passwordLabel = label(R.string.profile_password_label);
        card.addView(passwordLabel);
        passwordInput = field(R.string.profile_hint_password,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
        card.addView(passwordInput, fieldParams());

        loginButton = new Button(context);
        loginButton.setText(R.string.profile_login);
        loginButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        loginButton.setTextColor(UiStyles.color(context, R.color.textPrimary));
        loginButton.setPadding(24, 16, 24, 16);
        UiStyles.styleOemButton(loginButton, UiStyles.color(context, R.color.buttonPrimary));
        card.addView(loginButton, buttonParams());

        registerButton = new Button(context);
        registerButton.setText(R.string.profile_register);
        registerButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        registerButton.setTextColor(UiStyles.color(context, R.color.textPrimary));
        registerButton.setPadding(24, 16, 24, 16);
        UiStyles.styleOemButton(registerButton, UiStyles.color(context, R.color.buttonSecondaryMuted));
        card.addView(registerButton, buttonParams());

        logoutButton = new Button(context);
        logoutButton.setText(R.string.profile_logout);
        logoutButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        logoutButton.setTextColor(UiStyles.color(context, R.color.textPrimary));
        logoutButton.setPadding(24, 16, 24, 16);
        UiStyles.styleOemButton(logoutButton, UiStyles.color(context, R.color.buttonDanger));
        card.addView(logoutButton, buttonParams());

        noteText = new TextView(context);
        noteText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        noteText.setTextColor(UiStyles.color(context, R.color.textSecondary));
        noteText.setLineSpacing(2f, 1.15f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.setMargins(0, dp(16), 0, 0);
        card.addView(noteText, noteParams);

        loginButton.setOnClickListener(v -> submitLogin());
        registerButton.setOnClickListener(v -> showRegisterQr());
        logoutButton.setOnClickListener(v -> submitLogout());

        int cardWidth = LinearLayout.LayoutParams.MATCH_PARENT;
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        page.addView(card, cardParams);

        featuresCard = new LinearLayout(context);
        featuresCard.setOrientation(LinearLayout.VERTICAL);
        featuresCard.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(featuresCard);
        TextView featuresTitle = new TextView(context);
        featuresTitle.setText(R.string.profile_features_title);
        featuresTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        featuresTitle.setTypeface(null, Typeface.BOLD);
        featuresTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        featuresTitle.setPadding(0, 0, 0, dp(12));
        featuresCard.addView(featuresTitle);
        aiBadge = badge();
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        featuresCard.addView(featureRow(R.string.profile_feature_ai_title,
                R.string.profile_feature_ai_desc, aiBadge), rowParams);
        TextView usbBadge = badge();
        usbBadge.setText(R.string.profile_badge_free);
        setBadgeColor(usbBadge, R.color.buttonPrimary);
        featuresCard.addView(featureRow(R.string.profile_feature_usb_title,
                R.string.profile_feature_usb_desc, usbBadge), rowParams);
        LinearLayout.LayoutParams featuresParams = new LinearLayout.LayoutParams(
                cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        featuresParams.topMargin = dp(16);
        page.addView(featuresCard, featuresParams);
        scrollView.addView(page, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        showSession();
        if (apiService != null && apiService.isLoggedIn()) {
            apiService.refreshMe(new ProfileApiService.ApiCallback() {
                @Override
                public void onSuccess(String message, JSONObject data) {
                    showSession();
                }

                @Override
                public void onError(String error) {
                    showSession();
                }
            });
        }
        return scrollView;
    }

    private void submitLogin() {
        if (apiService == null) {
            return;
        }
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (username.isEmpty()) {
            Toast.makeText(context, R.string.profile_username_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.isEmpty()) {
            Toast.makeText(context, R.string.profile_password_required, Toast.LENGTH_SHORT).show();
            return;
        }
        loginButton.setEnabled(false);
        loginButton.setText(R.string.profile_signing_in);
        apiService.login(username, password, new ProfileApiService.ApiCallback() {
            @Override
            public void onSuccess(String message, JSONObject data) {
                loginButton.setEnabled(true);
                loginButton.setText(R.string.profile_login);
                passwordInput.setText("");
                showSession();
                Toast.makeText(context,
                        context.getString(R.string.profile_signed_in_as, apiService.getUsername()),
                        Toast.LENGTH_SHORT).show();
                callback.log("Giriş başarılı: " + apiService.getUsername());
            }

            @Override
            public void onError(String error) {
                loginButton.setEnabled(true);
                loginButton.setText(R.string.profile_login);
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show();
                callback.log("Giriş hatası: " + error);
            }
        });
    }

    private void showRegisterQr() {
        String url = ProfileApiService.API_ROOT + "/Account/Register";
        new Thread(() -> {
            try {
                Bitmap bitmap = encodeQr(url);
                new Handler(Looper.getMainLooper()).post(() -> showRegisterDialog(url, bitmap));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "" : e.getMessage();
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context,
                        context.getString(R.string.profile_qr_failed, message),
                        Toast.LENGTH_SHORT).show());
            }
        }, "register-qr").start();
    }

    private void showRegisterDialog(String url, Bitmap bitmap) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(20);
        content.setPadding(pad, pad, pad, dp(8));

        TextView hint = new TextView(context);
        hint.setText(R.string.profile_register_qr_hint);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        hint.setTextColor(UiStyles.color(context, R.color.textPrimary));
        hint.setGravity(Gravity.CENTER);
        content.addView(hint);

        ImageView image = new ImageView(context);
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size = dp(240);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(size, size);
        imageParams.topMargin = dp(16);
        imageParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(image, imageParams);

        TextView link = new TextView(context);
        link.setText(url);
        link.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        link.setTextColor(UiStyles.color(context, R.color.textSecondary));
        link.setGravity(Gravity.CENTER);
        link.setPadding(0, dp(12), 0, 0);
        content.addView(link);

        new AlertDialog.Builder(context)
                .setTitle(R.string.profile_register)
                .setView(content)
                .setPositiveButton(R.string.dialog_close, null)
                .show();
    }

    private static Bitmap encodeQr(String url) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);
        int size = 512;
        BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size, hints);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }

    private void submitLogout() {
        if (apiService == null) {
            return;
        }
        apiService.logout();
        usernameInput.setText("");
        passwordInput.setText("");
        showSession();
        Toast.makeText(context, R.string.profile_logged_out, Toast.LENGTH_SHORT).show();
        callback.log("Kullanıcı çıkış yaptı");
    }

    private void showSession() {
        boolean loggedIn = apiService != null && apiService.isLoggedIn();
        boolean premium = loggedIn && apiService.isPremium();
        int formVisibility = loggedIn ? View.GONE : View.VISIBLE;
        usernameLabel.setVisibility(formVisibility);
        usernameInput.setVisibility(formVisibility);
        passwordLabel.setVisibility(formVisibility);
        passwordInput.setVisibility(formVisibility);
        loginButton.setVisibility(formVisibility);
        registerButton.setVisibility(formVisibility);
        logoutButton.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        featuresCard.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        aiBadge.setText(premium ? R.string.profile_badge_available : R.string.profile_badge_premium_only);
        setBadgeColor(aiBadge, premium ? R.color.buttonPrimary : R.color.textSecondary);
        if (!loggedIn) {
            avatar.setText("?");
            statusText.setText(R.string.profile_sign_in_hint);
            noteText.setText(R.string.profile_guest_note);
        } else {
            String name = apiService.getUsername();
            if (name == null || name.isEmpty()) {
                name = context.getString(R.string.common_unknown);
            }
            avatar.setText(name.substring(0, 1).toUpperCase());
            statusText.setText(context.getString(R.string.profile_signed_in_as, name));
            noteText.setText(R.string.profile_member_note);
        }
        if (callback != null) {
            callback.onSessionChanged(loggedIn, premium);
        }
    }

    private TextView badge() {
        TextView badge = new TextView(context);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setTextColor(0xFFFFFFFF);
        badge.setPadding(dp(12), dp(5), dp(12), dp(5));
        badge.setGravity(Gravity.CENTER);
        return badge;
    }

    private void setBadgeColor(TextView badge, int colorRes) {
        UiStyles.applySolidRoundedBackgroundDp(badge, UiStyles.color(context, colorRes), 12f);
    }

    private LinearLayout featureRow(int titleRes, int descRes, TextView badge) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(context, R.color.surfaceColor));
        bg.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        bg.setStroke(dp(1), UiStyles.color(context, R.color.outlineMuted));
        row.setBackground(bg);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(titleRes);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        TextView desc = new TextView(context);
        desc.setText(descRes);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        desc.setTextColor(UiStyles.color(context, R.color.textSecondary));
        desc.setLineSpacing(2f, 1.1f);
        desc.setPadding(0, dp(4), 0, 0);
        texts.addView(title);
        texts.addView(desc);
        row.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeParams.leftMargin = dp(12);
        row.addView(badge, badgeParams);
        return row;
    }

    private boolean tallLayout() {
        Configuration configuration = context.getResources().getConfiguration();
        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            return true;
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return metrics.widthPixels <= 1200 && metrics.heightPixels >= metrics.widthPixels;
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private TextView label(int textRes) {
        TextView label = new TextView(context);
        label.setText(textRes);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTextColor(UiStyles.color(context, R.color.textSecondary));
        label.setPadding(0, 0, 0, dp(6));
        return label;
    }

    private EditText field(int hintRes, int inputType) {
        EditText input = new EditText(context);
        input.setHint(hintRes);
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        int pad = dp(14);
        input.setPadding(pad, pad, pad, pad);
        input.setTextColor(UiStyles.color(context, R.color.textPrimary));
        input.setHintTextColor(UiStyles.color(context, R.color.textHint));
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiStyles.color(context, R.color.surfaceColor));
        background.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        background.setStroke(dp(1), UiStyles.color(context, R.color.outlineMuted));
        input.setBackground(background);
        return input;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        return params;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, 0);
        return params;
    }
}
