package org.telegram.ui.Components.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * AccentOrbButton:
 * Liquid Glass Accent Orb Button (FAB / Send / Camera / Action button)
 * - Circular shape
 * - Accent color fill
 * - White diagonal gradient on top (45% -> 0%)
 * - 1dp perimeter stroke rgba(255, 255, 255, 0.5)
 * - Top highlight
 * - Bottom accent glow
 */
public class AccentOrbButton extends View {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF boundsF = new RectF();
    private final RectF strokeBoundsF = new RectF();

    private Drawable iconDrawable;
    private int iconColor = 0xFF04241A; // #04241A default text/icon on accent

    private int lastWidth = -1;
    private int lastHeight = -1;
    private int lastAccent = -1;

    public AccentOrbButton(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        fillPaint.setStyle(Paint.Style.FILL);
        glossPaint.setStyle(Paint.Style.FILL);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dp(1f));
        strokePaint.setColor(0x80FFFFFF); // rgba(255, 255, 255, 0.5)

        glowPaint.setStyle(Paint.Style.FILL);
    }

    public void setIcon(Drawable drawable) {
        this.iconDrawable = drawable;
        if (iconDrawable != null) {
            Theme.setCombinedDrawableColor(iconDrawable, iconColor, false);
        }
        invalidate();
    }

    public void setIconColor(int color) {
        this.iconColor = color;
        if (iconDrawable != null) {
            Theme.setCombinedDrawableColor(iconDrawable, iconColor, false);
            invalidate();
        }
    }

    private void updateShaders(int w, int h) {
        if (w <= 0 || h <= 0) return;

        int accent = SharedConfig.liquidGlassAccentColor;
        if (w == lastWidth && h == lastHeight && accent == lastAccent) {
            return;
        }

        lastWidth = w;
        lastHeight = h;
        lastAccent = accent;

        fillPaint.setColor(accent);

        // Diagonal white sheen (45% -> 0%)
        LinearGradient sheen = new LinearGradient(
                0, 0, w, h,
                new int[]{0x73FFFFFF, 0x00FFFFFF},
                new float[]{0.0f, 0.75f},
                Shader.TileMode.CLAMP
        );
        glossPaint.setShader(sheen);

        // Accent glow towards bottom
        int glowColor = Color.argb(0x60, Color.red(accent), Color.green(accent), Color.blue(accent));
        RadialGradient glow = new RadialGradient(
                w / 2f, h * 0.85f,
                w / 2f,
                new int[]{glowColor, Color.TRANSPARENT},
                new float[]{0.0f, 1.0f},
                Shader.TileMode.CLAMP
        );
        glowPaint.setShader(glow);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        boundsF.set(0, 0, w, h);
        float halfStroke = AndroidUtilities.dp(0.5f);
        strokeBoundsF.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke);
        updateShaders(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        updateShaders(w, h);

        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(cx, cy);

        // 1. Accent base fill
        canvas.drawCircle(cx, cy, radius, fillPaint);

        // 2. Bottom accent glow
        canvas.drawCircle(cx, cy, radius, glowPaint);

        // 3. Diagonal glass sheen
        canvas.drawCircle(cx, cy, radius, glossPaint);

        // 4. White perimeter stroke
        canvas.drawCircle(cx, cy, radius - AndroidUtilities.dp(0.5f), strokePaint);

        // 5. Centered icon
        if (iconDrawable != null) {
            int iconSize = (int) (radius * 1.05f);
            int left = (int) (cx - iconSize / 2f);
            int top = (int) (cy - iconSize / 2f);
            iconDrawable.setBounds(left, top, left + iconSize, top + iconSize);
            iconDrawable.draw(canvas);
        }
    }
}
