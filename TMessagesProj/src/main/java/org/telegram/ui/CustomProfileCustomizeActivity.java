package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.custom.CustomProfileController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CustomProfileCustomizeActivity extends BaseFragment {

    // Modes:
    // 0: gradient (full screen gradient)
    // 1: photo (single full photo)
    // 2: photo_gradient (top photo + bottom gradient)
    // 3: photo_photo (top photo + bottom photo)
    // 4: gradient_photo (top gradient + bottom photo)
    private static final String[] MODES = {"gradient", "photo", "photo_gradient", "photo_photo", "gradient_photo"};
    private static final String[] MODE_NAMES = {
            "Градиент (на весь экран)",
            "Фото (на весь экран)",
            "Фото сверху + Градиент снизу",
            "2 Фото (Шапка + Фон публикаций)",
            "Градиент сверху + Фото снизу"
    };
    private int currentModeIndex = 0;

    private int startColor = 0xFF8A2387;
    private int endColor = 0xFFF27121;
    private int gradientAngle = 0; // 0 = TOP_BOTTOM, 1 = LEFT_RIGHT, 2 = TL_BR
    private boolean fullScreenGradient = true;

    // Card background tint and Text color customization
    private int cardColor = 0x33FFFFFF; // translucent white by default
    private int textColor = 0xFFFFFFFF; // white text by default

    private View previewView;
    private View previewTopView;
    private View previewBottomView;

    private Bitmap selectedPhotoBitmap;      // Main / bottom photo
    private File selectedPhotoFile;
    private Bitmap selectedTopPhotoBitmap;   // Header photo
    private File selectedTopPhotoFile;

    private LinearLayout gradientOptionsLayout;
    private TextSettingsCell modeCell;
    private TextSettingsCell selectPhotoCell;
    private TextSettingsCell selectTopPhotoCell;
    private TextSettingsCell cardColorCell;
    private TextSettingsCell textColorCell;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Кастомизация профиля");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        ScrollView scrollView = new ScrollView(context);
        root.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Preview Header
        HeaderCell previewHeader = new HeaderCell(context);
        previewHeader.setText("Предпросмотр профиля");
        previewHeader.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        container.addView(previewHeader);

        // Preview Container (simulates profile header + overlapping body with rounded corners)
        FrameLayout previewContainer = new FrameLayout(context);
        previewContainer.setClipChildren(false);

        previewTopView = new View(context) {
            private final Path clipPath = new Path();
            private final RectF rect = new RectF();
            private final float[] radii = new float[]{0, 0, 0, 0, AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16)};

            @Override
            protected void onDraw(Canvas canvas) {
                clipPath.rewind();
                rect.set(0, 0, getWidth(), getHeight());
                clipPath.addRoundRect(rect, radii, Path.Direction.CW);
                canvas.clipPath(clipPath);
                super.onDraw(canvas);
            }
        };
        previewContainer.addView(previewTopView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 90, Gravity.TOP));

        previewBottomView = new View(context) {
            private final Path clipPath = new Path();
            private final RectF rect = new RectF();
            private final float[] radii = new float[]{AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), 0, 0, 0, 0};

            @Override
            protected void onDraw(Canvas canvas) {
                clipPath.rewind();
                rect.set(0, 0, getWidth(), getHeight());
                clipPath.addRoundRect(rect, radii, Path.Direction.CW);
                canvas.clipPath(clipPath);
                super.onDraw(canvas);
            }
        };
        previewContainer.addView(previewBottomView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.TOP, 0, 78, 0, 0));

        previewView = previewContainer;
        updatePreview();
        container.addView(previewContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 180, Gravity.CENTER_HORIZONTAL, 16, 8, 16, 16));

        // Mode Selector Header
        HeaderCell typeHeader = new HeaderCell(context);
        typeHeader.setText("Тип оформления");
        typeHeader.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        container.addView(typeHeader);

        modeCell = new TextSettingsCell(context);
        modeCell.setTextAndValue("Режим оформления", MODE_NAMES[currentModeIndex], true);
        modeCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        modeCell.setOnClickListener(v -> {
            currentModeIndex = (currentModeIndex + 1) % MODES.length;
            modeCell.setTextAndValue("Режим оформления", MODE_NAMES[currentModeIndex], true);
            updateVisibility();
            updatePreview();
        });
        container.addView(modeCell);

        // Top Photo Picker Row (Header)
        selectTopPhotoCell = new TextSettingsCell(context);
        selectTopPhotoCell.setTextAndValue("Выбрать фото для шапки", selectedTopPhotoFile != null ? selectedTopPhotoFile.getName() : "Не выбрано", true);
        selectTopPhotoCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        selectTopPhotoCell.setOnClickListener(v -> choosePhotoFromGallery(102));
        container.addView(selectTopPhotoCell);

        // Main / Bottom Photo Picker Row
        selectPhotoCell = new TextSettingsCell(context);
        selectPhotoCell.setTextAndValue("Выбрать главное фото (фон)", selectedPhotoFile != null ? selectedPhotoFile.getName() : "Не выбрано", true);
        selectPhotoCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        selectPhotoCell.setOnClickListener(v -> choosePhotoFromGallery(101));
        container.addView(selectPhotoCell);

        // Gradient options container
        gradientOptionsLayout = new LinearLayout(context);
        gradientOptionsLayout.setOrientation(LinearLayout.VERTICAL);
        container.addView(gradientOptionsLayout);

        // Settings Header
        HeaderCell settingsHeader = new HeaderCell(context);
        settingsHeader.setText("Настройки градиента");
        settingsHeader.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        gradientOptionsLayout.addView(settingsHeader);

        // Gradient Presets
        final int[][] presets = new int[][]{
                {0xFF8A2387, 0xFFF27121}, // Sunset / Neon purple-orange
                {0xFF00C9FF, 0xFF92FE9D}, // Cyan to Lime
                {0xFFFC466B, 0xFF3F5EFB}, // Pink to Indigo
                {0xFF11998E, 0xFF38EF7D}, // Mint green
                {0xFF8E2DE2, 0xFF4A00E0}, // Deep purple
                {0xFF0F2027, 0xFF2C5364}  // Dark metallic
        };

        LinearLayout presetsLayout = new LinearLayout(context);
        presetsLayout.setOrientation(LinearLayout.HORIZONTAL);
        presetsLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        presetsLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        for (int i = 0; i < presets.length; i++) {
            final int[] p = presets[i];
            View presetBtn = new View(context);
            GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, p);
            gd.setCornerRadius(AndroidUtilities.dp(20));
            presetBtn.setBackground(gd);
            presetBtn.setOnClickListener(v -> {
                startColor = p[0];
                endColor = p[1];
                updatePreview();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(AndroidUtilities.dp(40), AndroidUtilities.dp(40));
            lp.setMargins(0, 0, AndroidUtilities.dp(12), 0);
            presetsLayout.addView(presetBtn, lp);
        }
        gradientOptionsLayout.addView(presetsLayout);

        // Start Color Picker Cell
        TextSettingsCell startColorCell = new TextSettingsCell(context);
        startColorCell.setTextAndValue("Начальный цвет", String.format("#%06X", (0xFFFFFF & startColor)), true);
        startColorCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        startColorCell.setOnClickListener(v -> {
            ColorPicker colorPicker = new ColorPicker(context, false, new ColorPicker.ColorPickerDelegate() {
                @Override
                public void setColor(int color, int num, boolean applyNow) {
                    startColor = color;
                    startColorCell.setTextAndValue("Начальный цвет", String.format("#%06X", (0xFFFFFF & startColor)), true);
                    updatePreview();
                }
            }) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(300), MeasureSpec.EXACTLY));
                }
            };
            colorPicker.setColor(startColor, 0);
            colorPicker.setType(-1, true, 1, 1, false, 0, false);
            org.telegram.ui.ActionBar.BottomSheet bottomSheet = new org.telegram.ui.ActionBar.BottomSheet(context, false);
            bottomSheet.setCustomView(colorPicker);
            bottomSheet.setDimBehind(false);
            bottomSheet.show();
        });
        gradientOptionsLayout.addView(startColorCell);

        // End Color Picker Cell
        TextSettingsCell endColorCell = new TextSettingsCell(context);
        endColorCell.setTextAndValue("Конечный цвет", String.format("#%06X", (0xFFFFFF & endColor)), true);
        endColorCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        endColorCell.setOnClickListener(v -> {
            ColorPicker colorPicker = new ColorPicker(context, false, new ColorPicker.ColorPickerDelegate() {
                @Override
                public void setColor(int color, int num, boolean applyNow) {
                    endColor = color;
                    endColorCell.setTextAndValue("Конечный цвет", String.format("#%06X", (0xFFFFFF & endColor)), true);
                    updatePreview();
                }
            }) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(300), MeasureSpec.EXACTLY));
                }
            };
            colorPicker.setColor(endColor, 0);
            colorPicker.setType(-1, true, 1, 1, false, 0, false);
            org.telegram.ui.ActionBar.BottomSheet bottomSheet = new org.telegram.ui.ActionBar.BottomSheet(context, false);
            bottomSheet.setCustomView(colorPicker);
            bottomSheet.setDimBehind(false);
            bottomSheet.show();
        });
        gradientOptionsLayout.addView(endColorCell);

        // Full Screen Toggle
        TextCheckCell fullScreenCell = new TextCheckCell(context);
        fullScreenCell.setTextAndCheck("Тема на весь экран (включая публикации)", fullScreenGradient, false);
        fullScreenCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fullScreenCell.setOnClickListener(v -> {
            fullScreenGradient = !fullScreenGradient;
            fullScreenCell.setChecked(fullScreenGradient);
        });
        container.addView(fullScreenCell);

        // Text and Cards Color Settings Header
        HeaderCell colorsHeader = new HeaderCell(context);
        colorsHeader.setText("Цвета текста и карточек (под песней, номер, имя)");
        colorsHeader.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        container.addView(colorsHeader);

        // Card / Under-Music Tint Picker
        cardColorCell = new TextSettingsCell(context);
        cardColorCell.setTextAndValue("Цвет карточек (под музыкой, номер, био)", String.format("#%08X", cardColor), true);
        cardColorCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cardColorCell.setOnClickListener(v -> {
            ColorPicker colorPicker = new ColorPicker(context, true, new ColorPicker.ColorPickerDelegate() {
                @Override
                public void setColor(int color, int num, boolean applyNow) {
                    cardColor = color;
                    cardColorCell.setTextAndValue("Цвет карточек (под музыкой, номер, био)", String.format("#%08X", cardColor), true);
                }
            }) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(300), MeasureSpec.EXACTLY));
                }
            };
            colorPicker.setColor(cardColor, 0);
            colorPicker.setType(-1, true, 1, 1, false, 0, false);
            org.telegram.ui.ActionBar.BottomSheet bottomSheet = new org.telegram.ui.ActionBar.BottomSheet(context, false);
            bottomSheet.setCustomView(colorPicker);
            bottomSheet.setDimBehind(false);
            bottomSheet.show();
        });
        container.addView(cardColorCell);

        // Text Color Picker
        textColorCell = new TextSettingsCell(context);
        textColorCell.setTextAndValue("Цвет текста в профиле", String.format("#%06X", (0xFFFFFF & textColor)), true);
        textColorCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        textColorCell.setOnClickListener(v -> {
            ColorPicker colorPicker = new ColorPicker(context, false, new ColorPicker.ColorPickerDelegate() {
                @Override
                public void setColor(int color, int num, boolean applyNow) {
                    textColor = color;
                    textColorCell.setTextAndValue("Цвет текста в профиле", String.format("#%06X", (0xFFFFFF & textColor)), true);
                }
            }) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(300), MeasureSpec.EXACTLY));
                }
            };
            colorPicker.setColor(textColor, 0);
            colorPicker.setType(-1, true, 1, 1, false, 0, false);
            org.telegram.ui.ActionBar.BottomSheet bottomSheet = new org.telegram.ui.ActionBar.BottomSheet(context, false);
            bottomSheet.setCustomView(colorPicker);
            bottomSheet.setDimBehind(false);
            bottomSheet.show();
        });
        container.addView(textColorCell);

        updateVisibility();

        // Save Button
        TextView saveBtn = new TextView(context);
        saveBtn.setText("Сохранить и применить");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setTextSize(16);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), 0xFF2AABEE, 0xFF2293CC));
        saveBtn.setOnClickListener(v -> {
            long currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();
            String hexStart = String.format("#%06X", (0xFFFFFF & startColor));
            String hexEnd = String.format("#%06X", (0xFFFFFF & endColor));
            String hexCard = String.format("#%08X", cardColor);
            String hexText = String.format("#%06X", (0xFFFFFF & textColor));
            String bgType = MODES[currentModeIndex];

            saveBtn.setEnabled(false);
            saveBtn.setAlpha(0.6f);

            CustomProfileController.getInstance().saveCustomization(
                    currentUserId,
                    bgType,
                    hexStart,
                    hexEnd,
                    gradientAngle,
                    fullScreenGradient,
                    hexCard,
                    hexText,
                    selectedTopPhotoFile,
                    selectedPhotoFile,
                    (success, error) -> {
                        saveBtn.setEnabled(true);
                        saveBtn.setAlpha(1.0f);
                        if (success) {
                            Toast.makeText(getParentActivity(), "Кастомизация успешно сохранена!", Toast.LENGTH_SHORT).show();
                            finishFragment();
                        } else {
                            String msg = error != null ? error : "Ошибка отправки запроса";
                            Toast.makeText(getParentActivity(), "Ошибка: " + msg, Toast.LENGTH_LONG).show();
                        }
                    }
            );
        });
        container.addView(saveBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER, 16, 24, 16, 8));

        // Delete / Reset Button
        TextView deleteBtn = new TextView(context);
        deleteBtn.setText("Убрать всю кастомизацию");
        deleteBtn.setTextColor(0xFFE53935);
        deleteBtn.setTextSize(16);
        deleteBtn.setGravity(Gravity.CENTER);
        deleteBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), 0x1AE53935, 0x33E53935));
        deleteBtn.setOnClickListener(v -> {
            long currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();
            deleteBtn.setEnabled(false);
            deleteBtn.setAlpha(0.6f);

            CustomProfileController.getInstance().deleteCustomization(currentUserId, (success, error) -> {
                deleteBtn.setEnabled(true);
                deleteBtn.setAlpha(1.0f);
                if (success) {
                    Toast.makeText(getParentActivity(), "Кастомизация удалена!", Toast.LENGTH_SHORT).show();
                    finishFragment();
                } else {
                    String msg = error != null ? error : "Ошибка удаления";
                    Toast.makeText(getParentActivity(), "Ошибка: " + msg, Toast.LENGTH_LONG).show();
                }
            });
        });
        container.addView(deleteBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER, 16, 8, 16, 24));

        return fragmentView;
    }

    private void updateVisibility() {
        String mode = MODES[currentModeIndex];
        if ("gradient".equals(mode)) {
            selectTopPhotoCell.setVisibility(View.GONE);
            selectPhotoCell.setVisibility(View.GONE);
            gradientOptionsLayout.setVisibility(View.VISIBLE);
        } else if ("photo".equals(mode)) {
            selectTopPhotoCell.setVisibility(View.GONE);
            selectPhotoCell.setVisibility(View.VISIBLE);
            gradientOptionsLayout.setVisibility(View.GONE);
        } else if ("photo_gradient".equals(mode)) {
            selectTopPhotoCell.setVisibility(View.VISIBLE);
            selectPhotoCell.setVisibility(View.GONE);
            gradientOptionsLayout.setVisibility(View.VISIBLE);
        } else if ("photo_photo".equals(mode)) {
            selectTopPhotoCell.setVisibility(View.VISIBLE);
            selectPhotoCell.setVisibility(View.VISIBLE);
            gradientOptionsLayout.setVisibility(View.GONE);
        } else if ("gradient_photo".equals(mode)) {
            selectTopPhotoCell.setVisibility(View.GONE);
            selectPhotoCell.setVisibility(View.VISIBLE);
            gradientOptionsLayout.setVisibility(View.VISIBLE);
        }
    }

    private void choosePhotoFromGallery(int requestCode) {
        try {
            Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
            photoPickerIntent.setType("image/*");
            startActivityForResult(photoPickerIntent, requestCode);
        } catch (Exception e) {
            FileLog.e(e);
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, requestCode);
            } catch (Exception e2) {
                FileLog.e(e2);
            }
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            try {
                File cacheDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
                String tag = requestCode == 102 ? "top_orig_" : "main_orig_";
                File tempFile = new File(cacheDir, "temp_profile_bg_" + tag + System.currentTimeMillis() + ".jpg");

                try (InputStream inputStream = getParentActivity().getContentResolver().openInputStream(imageUri);
                     FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                // Open standard Telegram photo crop and adjustment activity
                Bundle args = new Bundle();
                args.putString("photoPath", tempFile.getAbsolutePath());
                args.putBoolean("freeform", true);
                PhotoCropActivity cropActivity = new PhotoCropActivity(args);
                final int finalReqCode = requestCode;
                cropActivity.setDelegate(bitmap -> {
                    if (bitmap == null) return;
                    try {
                        String cropTag = finalReqCode == 102 ? "top_cropped_" : "main_cropped_";
                        File croppedFile = new File(cacheDir, "temp_profile_bg_" + cropTag + System.currentTimeMillis() + ".jpg");
                        try (FileOutputStream fos = new FileOutputStream(croppedFile)) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos);
                        }

                        if (finalReqCode == 102) {
                            selectedTopPhotoFile = croppedFile;
                            selectedTopPhotoBitmap = bitmap;
                            if (selectTopPhotoCell != null) {
                                selectTopPhotoCell.setTextAndValue("Выбрать фото для шапки", "Выбрано (кадрировано)", true);
                            }
                        } else {
                            selectedPhotoFile = croppedFile;
                            selectedPhotoBitmap = bitmap;
                            if (selectPhotoCell != null) {
                                selectPhotoCell.setTextAndValue("Выбрать главное фото (фон)", "Выбрано (кадрировано)", true);
                            }
                        }
                        updatePreview();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
                presentFragment(cropActivity);
            } catch (Exception e) {
                FileLog.e(e);
                Toast.makeText(getParentActivity(), "Ошибка загрузки фото", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updatePreview() {
        if (previewTopView == null || previewBottomView == null) return;
        String mode = MODES[currentModeIndex];

        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{startColor, endColor});

        // Top view preview
        if ("photo_gradient".equals(mode) || "photo_photo".equals(mode)) {
            if (selectedTopPhotoBitmap != null) {
                previewTopView.setBackground(new BitmapDrawable(previewTopView.getResources(), selectedTopPhotoBitmap));
            } else {
                previewTopView.setBackgroundColor(0xFF555555);
            }
        } else if ("photo".equals(mode)) {
            if (selectedPhotoBitmap != null) {
                previewTopView.setBackground(new BitmapDrawable(previewTopView.getResources(), selectedPhotoBitmap));
            } else {
                previewTopView.setBackgroundColor(0xFF555555);
            }
        } else {
            previewTopView.setBackground(gd);
        }

        // Bottom view preview (with rounded top corners so it smoothly overlaps top photo)
        float bottomRadius = AndroidUtilities.dp(16);
        float[] bottomRadii = new float[]{bottomRadius, bottomRadius, bottomRadius, bottomRadius, 0, 0, 0, 0};

        if ("photo_photo".equals(mode) || "gradient_photo".equals(mode) || "photo".equals(mode)) {
            if (selectedPhotoBitmap != null) {
                previewBottomView.setBackground(new BitmapDrawable(previewBottomView.getResources(), selectedPhotoBitmap));
            } else {
                GradientDrawable fallbackGd = new GradientDrawable();
                fallbackGd.setColor(0xFF333333);
                fallbackGd.setCornerRadii(bottomRadii);
                previewBottomView.setBackground(fallbackGd);
            }
        } else {
            GradientDrawable bottomGd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{startColor, endColor});
            bottomGd.setCornerRadii(bottomRadii);
            previewBottomView.setBackground(bottomGd);
        }
    }
}
