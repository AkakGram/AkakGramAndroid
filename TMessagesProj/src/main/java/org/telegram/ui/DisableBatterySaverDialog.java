package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

public class DisableBatterySaverDialog extends BottomSheet {

    private static final String TAG = "BatterySaver";
    private static final String PREF_DONT_ASK = "battery_saver_dont_ask";

    private RLottieImageView rLottieImageView;
    private Utilities.Callback<Boolean> whenGranted;

    public DisableBatterySaverDialog(Context context, Utilities.Callback<Boolean> whenGranted) {
        super(context, false);
        this.whenGranted = whenGranted;

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        // --- иконка ---
        FrameLayout block = new FrameLayout(context);
        rLottieImageView = new RLottieImageView(context);
        rLottieImageView.setScaleType(ImageView.ScaleType.CENTER);
        rLottieImageView.setAnimation(R.raw.silent_unmute, 46, 46);
        rLottieImageView.playAnimation();
        rLottieImageView.setBackground(Theme.createCircleDrawable(
                AndroidUtilities.dp(72),
                Theme.getColor(Theme.key_featuredStickers_addButton)));
        block.addView(rLottieImageView, LayoutHelper.createFrame(72, 72, Gravity.CENTER));
        linearLayout.addView(block, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 110));

        // --- заголовок ---
        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setText("Отключите ограничения");
        textView.setPadding(AndroidUtilities.dp(30), 0, AndroidUtilities.dp(30), 0);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // --- подзаголовок ---
        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setText("Чтобы уведомления приходили вовремя и приложение не закрывалось в фоне, выберите «Нет ограничений»");
        textView.setPadding(AndroidUtilities.dp(30), AndroidUtilities.dp(10), AndroidUtilities.dp(30), AndroidUtilities.dp(21));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // --- пункты ---
        linearLayout.addView(new SectionView(context, R.drawable.msg_message_s,
                "Сообщения будут приходить сразу"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        linearLayout.addView(new SectionView(context, R.drawable.msg_members_list2,
                "Приложение не будет закрываться в фоне"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        linearLayout.addView(new SectionView(context, R.drawable.msg_customize_s,
                "В разделе «Контроль активности» выберите «Нет ограничений»"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        setCustomView(linearLayout);
        fixNavigationBar(getThemedColor(Theme.key_dialogBackground));

        // --- кнопка "Отключить" ---
        textView = new TextView(context);
        textView.setText("Отключить");
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        textView.setBackground(Theme.AdaptiveRipple.filledRect(
                Theme.getColor(Theme.key_featuredStickers_addButton), 24));
        textView.setOnClickListener(e -> {
            Log.d(TAG, "кнопка 'Отключить' нажата");
            openBatterySettings(getContext());
            if (this.whenGranted != null) {
                this.whenGranted.run(true);
                this.whenGranted = null;
            }
            dismiss();
        });
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 14, 14, 14, 4));

        // --- кнопка "Больше не напоминать" ---
        TextView dontAskView = new TextView(context);
        dontAskView.setText("Больше не напоминать");
        dontAskView.setGravity(Gravity.CENTER);
        dontAskView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        dontAskView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue));
        dontAskView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        dontAskView.setOnClickListener(e -> {
            Log.d(TAG, "кнопка 'Больше не напоминать' нажата");
            setDontAsk(true);
            if (this.whenGranted != null) {
                this.whenGranted.run(false);
                this.whenGranted = null;
            }
            dismiss();
        });
        linearLayout.addView(dontAskView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 0, 14, 8));
    }

    // --- открыть системные настройки (App Info) ---
    private static void openBatterySettings(Context context) {
        try {
            Log.d(TAG, "открываю ACTION_APPLICATION_DETAILS_SETTINGS для " + context.getPackageName());
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.d(TAG, "App Info открыт");
        } catch (Exception e1) {
            Log.e(TAG, "не вышло открыть App Info, пробую ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS", e1);
            try {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "открыл ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS");
            } catch (Exception e2) {
                Log.e(TAG, "не вышло, пробую ACTION_BATTERY_SAVER_SETTINGS", e2);
                try {
                    Intent intent = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Log.d(TAG, "открыл ACTION_BATTERY_SAVER_SETTINGS");
                } catch (Exception e3) {
                    Log.e(TAG, "всё провалилось", e3);
                }
            }
        }
    }

    // --- флаг "больше не напоминать" ---
    public static boolean isDontAsk() {
        return MessagesController.getGlobalMainSettings().getBoolean(PREF_DONT_ASK, false);
    }

    public static void setDontAsk(boolean value) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(PREF_DONT_ASK, value).apply();
    }

    // --- когда спрашивать ---
    public static boolean shouldAsk(Context context) {
        Log.d(TAG, "shouldAsk() вызван");

        if (context == null) {
            Log.d(TAG, "shouldAsk=false: context == null");
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.d(TAG, "shouldAsk=false: SDK < M");
            return false;
        }
        if (isDontAsk()) {
            Log.d(TAG, "shouldAsk=false: пользователь нажал 'Больше не напоминать'");
            return false;
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            Log.d(TAG, "shouldAsk=false: PowerManager == null");
            return false;
        }

        boolean ignoring = pm.isIgnoringBatteryOptimizations(context.getPackageName());
        Log.d(TAG, "ignoring=" + ignoring + " package=" + context.getPackageName() + " → shouldAsk=" + (!ignoring));
        return !ignoring;
    }

    // --- вспомогательный класс для пунктов ---
    private static class SectionView extends FrameLayout {
        public SectionView(Context context, int resId, CharSequence text) {
            super(context);
            setPadding(0, AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7));

            ImageView imageView = new ImageView(context);
            imageView.setImageResource(resId);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrame(24, 24,
                    Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT),
                    LocaleController.isRTL ? 0 : 22, 0, LocaleController.isRTL ? 22 : 0, 0));

            TextView textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView.setText(text);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL,
                    LocaleController.isRTL ? 0 : 61, 0, LocaleController.isRTL ? 61 : 0, 0));
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (whenGranted != null) {
            whenGranted.run(false);
            whenGranted = null;
        }
    }
}