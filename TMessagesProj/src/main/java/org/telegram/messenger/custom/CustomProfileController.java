package org.telegram.messenger.custom;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CustomProfileController {

    private static volatile CustomProfileController Instance;

    public static CustomProfileController getInstance() {
        CustomProfileController localInstance = Instance;
        if (localInstance == null) {
            synchronized (CustomProfileController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new CustomProfileController();
                }
            }
        }
        return localInstance;
    }

    public static final String API_BASE_URL = "https://akakgram.cc.cd/api/client/cast/";

    public static class ProfileCustomization {
        public long userId;
        public String bgType = "none"; // "none", "gradient", "photo"
        public int gradientStart = 0;
        public int gradientEnd = 0;
        public int gradientAngle = 0;
        public String photoUrl;
        public String photoTopUrl;
        public boolean fullScreenGradient = true;
        public int cardColor = 0; // custom tint color for cards (under music, bio, phone)
        public int textColor = 0; // custom text color for profile text

        // Runtime cached objects
        public GradientDrawable gradientDrawable;
        public Bitmap photoBitmap;
        public Bitmap photoTopBitmap;

        public boolean hasCustomization() {
            return !"none".equalsIgnoreCase(bgType) && (!TextUtils.isEmpty(photoUrl) || !TextUtils.isEmpty(photoTopUrl) || (gradientStart != 0 && gradientEnd != 0));
        }

        public GradientDrawable getOrCreateGradientDrawable() {
            if (gradientDrawable == null && gradientStart != 0 && gradientEnd != 0) {
                GradientDrawable.Orientation orientation;
                switch (gradientAngle) {
                    case 1:
                        orientation = GradientDrawable.Orientation.LEFT_RIGHT;
                        break;
                    case 2:
                        orientation = GradientDrawable.Orientation.TL_BR;
                        break;
                    case 3:
                        orientation = GradientDrawable.Orientation.BL_TR;
                        break;
                    default:
                        orientation = GradientDrawable.Orientation.TOP_BOTTOM;
                        break;
                }
                gradientDrawable = new GradientDrawable(orientation, new int[]{gradientStart, gradientEnd});
            }
            return gradientDrawable;
        }
    }

    private final Map<Long, ProfileCustomization> cache = new HashMap<>();
    private final Set<Long> loadingUsers = new HashSet<>();

    public ProfileCustomization getCustomization(long userId) {
        return cache.get(userId);
    }

    public void loadCustomization(final long userId) {
        if (userId <= 0) {
            return;
        }
        synchronized (loadingUsers) {
            if (loadingUsers.contains(userId)) {
                return;
            }
            loadingUsers.add(userId);
        }

        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(API_BASE_URL + "profile/" + userId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if ("ok".equals(json.optString("status")) && json.optBoolean("customized", false)) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            final ProfileCustomization customization = new ProfileCustomization();
                            customization.userId = userId;
                            customization.bgType = data.optString("bg_type", "gradient");
                            customization.gradientStart = parseColor(data.optString("gradient_start", "#FF4500"));
                            customization.gradientEnd = parseColor(data.optString("gradient_end", "#1E90FF"));
                            customization.gradientAngle = data.optInt("gradient_angle", 0);
                            customization.photoUrl = data.optString("photo_url", null);
                            customization.photoTopUrl = data.optString("photo_top_url", null);
                            customization.fullScreenGradient = data.optBoolean("full_screen_gradient", true);
                            customization.cardColor = parseColor(data.optString("card_color", null));
                            customization.textColor = parseColor(data.optString("text_color", null));

                            if (!TextUtils.isEmpty(customization.photoUrl)) {
                                downloadPhoto(customization, customization.photoUrl, false);
                            }
                            if (!TextUtils.isEmpty(customization.photoTopUrl)) {
                                downloadPhoto(customization, customization.photoTopUrl, true);
                            }

                            AndroidUtilities.runOnUIThread(() -> {
                                cache.put(userId, customization);
                                synchronized (loadingUsers) {
                                    loadingUsers.remove(userId);
                                }
                                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.profileMusicUpdated, userId);
                            });
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                synchronized (loadingUsers) {
                    loadingUsers.remove(userId);
                }
            }
        });
    }

    private void downloadPhoto(ProfileCustomization customization, String urlStr, boolean isTop) {
        if (TextUtils.isEmpty(urlStr)) {
            return;
        }
        try {
            File cacheDir = ApplicationLoader.applicationContext.getCacheDir();
            String hash = Utilities.MD5(urlStr);
            File photoFile = new File(cacheDir, "prof_bg_" + hash + ".img");
            if (!photoFile.exists()) {
                URL photoUrl = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) photoUrl.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(photoFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }
                conn.disconnect();
            }

            if (photoFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                if (bitmap != null) {
                    if (isTop) {
                        customization.photoTopBitmap = bitmap;
                    } else {
                        customization.photoBitmap = bitmap;
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static int parseColor(String colorStr) {
        if (TextUtils.isEmpty(colorStr)) {
            return 0;
        }
        try {
            colorStr = colorStr.trim();
            if (colorStr.startsWith("0x") || colorStr.startsWith("0X")) {
                return (int) Long.parseLong(colorStr.substring(2), 16);
            }
            if (!colorStr.startsWith("#")) {
                colorStr = "#" + colorStr;
            }
            return Color.parseColor(colorStr);
        } catch (Exception e) {
            return 0;
        }
    }

    public interface SaveCallback {
        void onComplete(boolean success, String error);
    }

    public void saveCustomization(final long userId, final String bgType, final String gradientStart, final String gradientEnd, final int gradientAngle, final boolean fullScreen, final String cardColor, final String textColor, final File photoTopFile, final File photoMainFile, final SaveCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            boolean success = false;
            String errorMsg = null;
            try {
                String boundary = "Boundary" + System.currentTimeMillis();
                URL url = new URL(API_BASE_URL + "profile");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                boolean hasFiles = (photoMainFile != null && photoMainFile.exists()) || (photoTopFile != null && photoTopFile.exists());
                if (hasFiles) {
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    OutputStream os = conn.getOutputStream();
                    writeFormField(os, boundary, "user_id", String.valueOf(userId));
                    writeFormField(os, boundary, "bg_type", bgType);
                    writeFormField(os, boundary, "full_screen_gradient", fullScreen ? "1" : "0");
                    if (gradientStart != null) {
                        writeFormField(os, boundary, "gradient_start", gradientStart);
                    }
                    if (gradientEnd != null) {
                        writeFormField(os, boundary, "gradient_end", gradientEnd);
                    }
                    writeFormField(os, boundary, "gradient_angle", String.valueOf(gradientAngle));
                    if (cardColor != null) {
                        writeFormField(os, boundary, "card_color", cardColor);
                    }
                    if (textColor != null) {
                        writeFormField(os, boundary, "text_color", textColor);
                    }

                    if (photoMainFile != null && photoMainFile.exists()) {
                        writeFileField(os, boundary, "photo", photoMainFile);
                    }
                    if (photoTopFile != null && photoTopFile.exists()) {
                        writeFileField(os, boundary, "photo_top", photoTopFile);
                    }

                    os.write(("--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    os.flush();
                    os.close();
                } else {
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    JSONObject body = new JSONObject();
                    body.put("user_id", userId);
                    body.put("bg_type", bgType);
                    body.put("gradient_start", gradientStart);
                    body.put("gradient_end", gradientEnd);
                    body.put("gradient_angle", gradientAngle);
                    body.put("full_screen_gradient", fullScreen);
                    if (cardColor != null) {
                        body.put("card_color", cardColor);
                    }
                    if (textColor != null) {
                        body.put("text_color", textColor);
                    }

                    OutputStream os = conn.getOutputStream();
                    os.write(body.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    success = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        cache.remove(userId);
                        synchronized (loadingUsers) {
                            loadingUsers.remove(userId);
                        }
                        loadCustomization(userId);
                    });
                } else {
                    errorMsg = "HTTP error: " + code;
                }
            } catch (Exception e) {
                FileLog.e(e);
                errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
                final boolean finalSuccess = success;
                final String finalError = errorMsg;
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onComplete(finalSuccess, finalError));
                }
            }
        });
    }

    private void writeFormField(OutputStream out, String boundary, String name, String value) throws Exception {
        String data = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        out.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void writeFileField(OutputStream out, String boundary, String fieldName, File file) throws Exception {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + file.getName() + "\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n";
        out.write(header.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        try (InputStream is = java.nio.file.Files.newInputStream(file.toPath())) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
        }
        out.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public void deleteCustomization(final long userId, final SaveCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection conn = null;
            boolean success = false;
            String errorMsg = null;
            try {
                URL url = new URL(API_BASE_URL + "profile/" + userId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code == 200 || code == 204) {
                    success = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        cache.remove(userId);
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.profileMusicUpdated, userId);
                    });
                } else {
                    errorMsg = "HTTP error: " + code;
                }
            } catch (Exception e) {
                FileLog.e(e);
                errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
                final boolean finalSuccess = success;
                final String finalError = errorMsg;
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onComplete(finalSuccess, finalError));
                }
            }
        });
    }
}
