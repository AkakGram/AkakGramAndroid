package org.telegram.ui.Components.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * GlowBackgroundView:
 * Base background: #0A0C10
 * 3 Soft Radial Orbs (radius ~380dp, transparent at edge):
 *  - Top: Accent color (33% alpha)
 *  - Middle: Blue #506EFF (30% alpha)
 *  - Bottom: Purple #AA64FF (26% alpha)
 * Dot pattern grid: points radius ~1dp every 22dp with 5.5% alpha
 */
public class GlowBackgroundView extends View {

    private final Paint bgPaint = new Paint();
    private final Paint orbTopPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbMidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbBottomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean showDotGrid = false;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int lastAccent = -1;

    public GlowBackgroundView(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Theme.getColor(Theme.key_glass_background));

        dotGridPaint.setStyle(Paint.Style.FILL);
        dotGridPaint.setColor(Color.argb((int) (255 * 0.055f), 255, 255, 255));
    }

    public void setShowDotGrid(boolean show) {
        if (this.showDotGrid != show) {
            this.showDotGrid = show;
            invalidate();
        }
    }

    private void updateShaders(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int accentColor = SharedConfig.liquidGlassAccentColor;
        if (width == lastWidth && height == lastHeight && accentColor == lastAccent) {
            return;
        }

        lastWidth = width;
        lastHeight = height;
        lastAccent = accentColor;

        float radius = AndroidUtilities.dp(380);

        // Top Orb: Accent with 35% alpha -> 0, centered at y=0 so top header is completely bathed in light and gradient never cuts off
        int topColor = Color.argb((int) (255 * 0.35f), Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
        float topRadius = AndroidUtilities.dp(460);
        RadialGradient topGradient = new RadialGradient(
                width * 0.40f,
                0f,
                topRadius,
                new int[]{topColor, Color.TRANSPARENT},
                new float[]{0.0f, 1.0f},
                Shader.TileMode.CLAMP
        );
        orbTopPaint.setShader(topGradient);

        // Middle Orb: Blue #506EFF with 30% alpha -> 0
        int midColor = Color.argb((int) (255 * 0.30f), 0x50, 0x6E, 0xFF);
        RadialGradient midGradient = new RadialGradient(
                width * 0.70f,
                height * 0.48f,
                radius,
                new int[]{midColor, Color.TRANSPARENT},
                new float[]{0.0f, 1.0f},
                Shader.TileMode.CLAMP
        );
        orbMidPaint.setShader(midGradient);

        // Bottom Orb: Purple #AA64FF with 26% alpha -> 0
        int botColor = Color.argb((int) (255 * 0.26f), 0xAA, 0x64, 0xFF);
        RadialGradient botGradient = new RadialGradient(
                width * 0.40f,
                height * 0.85f,
                radius,
                new int[]{botColor, Color.TRANSPARENT},
                new float[]{0.0f, 1.0f},
                Shader.TileMode.CLAMP
        );
        orbBottomPaint.setShader(botGradient);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShaders(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        // 1. Dark background
        bgPaint.setColor(Theme.getColor(Theme.key_glass_background));
        canvas.drawRect(0, 0, width, height, bgPaint);

        if (!SharedConfig.liquidGlassEnabled) {
            return;
        }

        // 2. Light Orbs
        updateShaders(width, height);
        canvas.drawRect(0, 0, width, height, orbTopPaint);
        canvas.drawRect(0, 0, width, height, orbMidPaint);
        canvas.drawRect(0, 0, width, height, orbBottomPaint);

        // 3. Dot Grid (for chat background and fine textures)
        if (showDotGrid) {
            float step = AndroidUtilities.dp(22);
            float dotRadius = AndroidUtilities.dp(1f);
            for (float y = step / 2; y < height; y += step) {
                for (float x = step / 2; x < width; x += step) {
                    canvas.drawCircle(x, y, dotRadius, dotGridPaint);
                }
            }
        }
    }
}
