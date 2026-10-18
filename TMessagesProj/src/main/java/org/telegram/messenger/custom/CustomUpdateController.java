package org.telegram.messenger.custom;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class CustomUpdateController {

    private static volatile CustomUpdateController Instance;

    public static CustomUpdateController getInstance() {
        CustomUpdateController localInstance = Instance;
        if (localInstance == null) {
            synchronized (CustomUpdateController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new CustomUpdateController();
                }
            }
        }
        return localInstance;
    }

    private static final String UPDATE_URL_ENDPOINT_1 = "https://akakgram.cc.cd/updates/version-androif";
    private static final String UPDATE_URL_ENDPOINT_2 = "https://akakgram.cc.cd/updates/version-android";

    private static final String PREF_NAME = "custom_update_pref";
    private static final String KEY_REMIND_LATER_TIMESTAMP = "remind_later_timestamp";

    private boolean isChecking = false;
    private UpdateBottomSheet activeSheet = null;

    public static class UpdateInfo {
        public int versionCode;
        public String downloadUrl;
        public String versionName;
        public String changelog;
        public ArrayList<String> features = new ArrayList<>();
    }

    public void checkAppUpdate(Activity activity, boolean force) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long remindLaterUntil = prefs.getLong(KEY_REMIND_LATER_TIMESTAMP, 0L);
        long now = System.currentTimeMillis();

        if (!force && now < remindLaterUntil) {
            return;
        }

        if (isChecking) {
            return;
        }
        isChecking = true;

        Utilities.globalQueue.postRunnable(() -> {
            FileLog.d("CustomUpdateController: checking update from " + UPDATE_URL_ENDPOINT_1);
            UpdateInfo info = fetchUpdateInfo(UPDATE_URL_ENDPOINT_1);
            if (info == null) {
                FileLog.d("CustomUpdateController: endpoint 1 failed, trying " + UPDATE_URL_ENDPOINT_2);
                info = fetchUpdateInfo(UPDATE_URL_ENDPOINT_2);
            }

            final UpdateInfo finalInfo = info;
            AndroidUtilities.runOnUIThread(() -> {
                isChecking = false;
                if (finalInfo != null) {
                    int currentVersionCode = SharedConfig.buildVersion();
                    int normalizedCurrentCode = currentVersionCode;
                    int remoteCode = finalInfo.versionCode;
                    if (currentVersionCode > 10000 && remoteCode < 10000) {
                        normalizedCurrentCode = currentVersionCode / 10;
                    } else if (remoteCode > 10000 && currentVersionCode < 10000) {
                        remoteCode = remoteCode / 10;
                    }

                    FileLog.d("CustomUpdateController: remoteCode=" + finalInfo.versionCode + " (norm=" + remoteCode + "), currentCode=" + currentVersionCode + " (norm=" + normalizedCurrentCode + ")");
                    if (remoteCode > normalizedCurrentCode) {
                        showUpdateBottomSheet(activity, finalInfo);
                    }
                } else {
                    FileLog.d("CustomUpdateController: fetchUpdateInfo returned null");
                }
            });
        });
    }

    private UpdateInfo fetchUpdateInfo(String endpointUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpointUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setRequestProperty("User-Agent", "AkakGram-Android/" + BuildVars.BUILD_VERSION_STRING);
            connection.connect();

            int code = connection.getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();

                String body = sb.toString().trim();
                return parseResponse(body);
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception ignore) {}
            }
        }
        return null;
    }

    private UpdateInfo parseResponse(String body) {
        if (TextUtils.isEmpty(body)) {
            return null;
        }

        try {
            if (body.startsWith("{")) {
                JSONObject json = new JSONObject(body);
                UpdateInfo info = new UpdateInfo();
                info.versionCode = json.optInt("version_code", json.optInt("versionCode", json.optInt("code", json.optInt("version", 0))));
                info.downloadUrl = json.optString("url", json.optString("download_url", json.optString("downloadUrl", json.optString("link", ""))));
                info.versionName = json.optString("version_name", json.optString("versionName", ""));
                info.changelog = json.optString("changelog", json.optString("description", json.optString("what_new", json.optString("whats_new", ""))));

                // 1) Read features list if present in JSON: "features": ["Feature 1", "Feature 2"] or "whats_new": [...]
                JSONArray array = json.optJSONArray("features");
                if (array == null) array = json.optJSONArray("items");
                if (array == null) array = json.optJSONArray("whats_new");
                if (array == null) array = json.optJSONArray("what_new");

                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        String item = array.optString(i, null);
                        if (!TextUtils.isEmpty(item)) {
                            info.features.add(item);
                        }
                    }
                } else if (!TextUtils.isEmpty(info.changelog)) {
                    // Split changelog by newlines or bullet points
                    String[] split = info.changelog.split("\n");
                    for (String s : split) {
                        String clean = cleanBullet(s);
                        if (!TextUtils.isEmpty(clean)) {
                            info.features.add(clean);
                        }
                    }
                }

                if (info.versionCode > 0 && !TextUtils.isEmpty(info.downloadUrl)) {
                    return info;
                }
            } else {
                // Text file format:
                // Line 1: 76969
                // Line 2: https://github.com/kakashka
                // Following lines: What's new bullet points
                String[] lines = body.split("\n");
                int code = 0;
                String downloadUrl = "";
                UpdateInfo info = new UpdateInfo();

                for (String l : lines) {
                    String clean = l.trim();
                    if (clean.isEmpty()) continue;

                    if (clean.startsWith("http://") || clean.startsWith("https://")) {
                        downloadUrl = clean;
                    } else if (code == 0 && clean.matches("^\\d+$")) {
                        try {
                            code = Integer.parseInt(clean);
                        } catch (Exception ignore) {}
                    } else {
                        String feature = cleanBullet(clean);
                        if (!TextUtils.isEmpty(feature)) {
                            info.features.add(feature);
                        }
                    }
                }

                if (code > 0 && !TextUtils.isEmpty(downloadUrl)) {
                    info.versionCode = code;
                    info.downloadUrl = downloadUrl;
                    return info;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private static String cleanBullet(String s) {
        if (s == null) return "";
        String clean = s.trim();
        if (clean.startsWith("- ") || clean.startsWith("• ") || clean.startsWith("* ")) {
            clean = clean.substring(2).trim();
        }
        return clean;
    }

    private void showUpdateBottomSheet(Activity activity, UpdateInfo info) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (activeSheet != null && activeSheet.isShowing()) {
            return;
        }

        activeSheet = new UpdateBottomSheet(activity, info);
        activeSheet.show();
    }

    public static class UpdateBottomSheet extends BottomSheet {

        public UpdateBottomSheet(Context context, UpdateInfo info) {
            super(context, false);
            setCanceledOnTouchOutside(true);

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            // Animated top icon block
            FrameLayout iconBlock = new FrameLayout(context);
            RLottieImageView lottieView = new RLottieImageView(context);
            lottieView.setScaleType(ImageView.ScaleType.CENTER);
            lottieView.setAnimation(R.raw.ic_download, 46, 46);
            lottieView.playAnimation();
            lottieView.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(72), Theme.getColor(Theme.key_featuredStickers_addButton)));
            iconBlock.addView(lottieView, LayoutHelper.createFrame(72, 72, Gravity.CENTER));
            iconBlock.setOnClickListener(e -> {
                if (!lottieView.isPlaying()) {
                    lottieView.setProgress(0);
                    lottieView.playAnimation();
                }
            });
            linearLayout.addView(iconBlock, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 110));

            // Title
            TextView titleView = new TextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleView.setGravity(Gravity.CENTER_HORIZONTAL);
            titleView.setText("Обновите AkakGram");
            titleView.setPadding(AndroidUtilities.dp(30), 0, AndroidUtilities.dp(30), 0);
            linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            // Subtitle
            TextView subtitleView = new TextView(context);
            subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
            String versionText;
            if (!TextUtils.isEmpty(info.versionName)) {
                versionText = "Доступна новая версия " + info.versionName + " (" + info.versionCode + ")";
            } else {
                versionText = "Доступна новая версия " + info.versionCode;
            }
            subtitleView.setText(versionText);
            subtitleView.setPadding(AndroidUtilities.dp(30), AndroidUtilities.dp(6), AndroidUtilities.dp(30), AndroidUtilities.dp(16));
            linearLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            // Feature items from file / JSON
            if (info.features != null && !info.features.isEmpty()) {
                int[] icons = new int[] {
                        R.drawable.msg_download,
                        R.drawable.msg_customize_s,
                        R.drawable.msg_message_s,
                        R.drawable.msg_saved,
                        R.drawable.msg_theme
                };
                for (int i = 0; i < info.features.size(); i++) {
                    int icon = icons[i % icons.length];
                    linearLayout.addView(new SectionItemView(context, icon, info.features.get(i)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
            } else {
                linearLayout.addView(new SectionItemView(context, R.drawable.msg_download, "Быстрая загрузка последней версии APK"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                linearLayout.addView(new SectionItemView(context, R.drawable.msg_customize_s, "Новые функции, улучшения и исправления ошибок"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }

            setCustomView(linearLayout);
            fixNavigationBar(getThemedColor(Theme.key_dialogBackground));

            // Main Download Button
            TextView downloadBtn = new TextView(context);
            downloadBtn.setText("Скачать");
            downloadBtn.setGravity(Gravity.CENTER);
            downloadBtn.setTypeface(AndroidUtilities.bold());
            downloadBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            downloadBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            downloadBtn.setBackground(Theme.AdaptiveRipple.filledRect(Theme.getColor(Theme.key_featuredStickers_addButton), 24));
            downloadBtn.setOnClickListener(e -> {
                downloadUpdateApk(context, info.downloadUrl);
                dismiss();
            });
            linearLayout.addView(downloadBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 20, 16, 6));

            // Small "Не напоминать 1 день" text button underneath
            TextView snoozeBtn = new TextView(context);
            snoozeBtn.setText("Не напоминать 1 день");
            snoozeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            snoozeBtn.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
            snoozeBtn.setGravity(Gravity.CENTER);
            snoozeBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
            snoozeBtn.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), AndroidUtilities.dp(8), AndroidUtilities.dp(8)));
            snoozeBtn.setOnClickListener(v -> {
                SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                long oneDayLater = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
                prefs.edit().putLong(KEY_REMIND_LATER_TIMESTAMP, oneDayLater).apply();
                dismiss();
            });
            linearLayout.addView(snoozeBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));
        }

        private static class SectionItemView extends FrameLayout {
            public SectionItemView(Context context, int resId, CharSequence text) {
                super(context);
                setPadding(0, AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7));

                ImageView imageView = new ImageView(context);
                imageView.setImageResource(resId);
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));
                addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 22, 0, LocaleController.isRTL ? 22 : 0, 0));

                TextView textView = new TextView(context);
                textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                textView.setText(text);
                addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, LocaleController.isRTL ? 0 : 61, 0, LocaleController.isRTL ? 61 : 0, 0));
            }
        }
    }

    public static void downloadUpdateApk(Context context, String downloadUrl) {
        if (context == null || TextUtils.isEmpty(downloadUrl)) {
            return;
        }

        try {
            Uri uri = Uri.parse(downloadUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setMimeType("application/vnd.android.package-archive");
            request.setTitle("AkakGram Update");
            request.setDescription("Скачивание обновления AkakGram...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            String fileName = uri.getLastPathSegment();
            if (TextUtils.isEmpty(fileName) || !fileName.endsWith(".apk")) {
                fileName = "AkakGram_update.apk";
            }
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager != null) {
                downloadManager.enqueue(request);
                Toast.makeText(context, "Скачивание обновления началось...", Toast.LENGTH_SHORT).show();
            } else {
                Browser.openUrl(context, downloadUrl);
            }
        } catch (Exception e) {
            FileLog.e(e);
            Browser.openUrl(context, downloadUrl);
        }
    }
}
