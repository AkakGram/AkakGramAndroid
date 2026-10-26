package org.telegram.ui.Components.glass;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * Liquid Glass Drawable supporting 3 glass levels:
 * Level 1: Light (list items, tabs, cards)
 *   - Fill: rgba(255,255,255,0.06), Stroke: 1dp rgba(255,255,255,0.12), Top highlight: 1dp rgba(255,255,255,0.24)
 * Level 2: Main (top bar, bottom nav, enter field, banners)
 *   - Fill: rgba(34,38,50,0.40), Stroke: 1dp rgba(255,255,255,0.16), Top highlight: rgba(255,255,255,0.34), Bottom highlight: rgba(255,255,255,0.06)
 * Level 3: Active (selected tab, active nav item)
 *   - Fill: rgba(255,255,255,0.14), Stroke: rgba(255,255,255,0.18), Top highlight: rgba(255,255,255,0.42)
 */
public class GlassDrawable extends Drawable {

    public static final int LEVEL_LIGHT = 1;
    public static final int LEVEL_MAIN = 2;
    public static final int LEVEL_ACTIVE = 3;
    public static final int LEVEL_DIALOG = 4;

    private int level = LEVEL_LIGHT;
    private float cornerRadius;
    private boolean isCapsule;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightTopPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightBottomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF boundsF = new RectF();
    private final RectF strokeBoundsF = new RectF();
    private final RectF topHighlightBoundsF = new RectF();
    private final RectF bottomHighlightBoundsF = new RectF();

    private final float strokeWidth = AndroidUtilities.dp(1f);
    private final float highlightHeight = AndroidUtilities.dp(1f);

    public GlassDrawable(int level) {
        this(level, AndroidUtilities.dp(22));
    }

    public GlassDrawable(int level, float cornerRadius) {
        this.level = level;
        this.cornerRadius = cornerRadius;

        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeWidth);

        highlightTopPaint.setStyle(Paint.Style.FILL);
        highlightBottomPaint.setStyle(Paint.Style.FILL);

        updateColors();
    }

    public void setGlassLevel(int level) {
        if (this.level != level) {
            this.level = level;
            updateColors();
            invalidateSelf();
        }
    }

    public int getGlassLevel() {
        return level;
    }

    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
        this.isCapsule = false;
        invalidateSelf();
    }

    public void setIsCapsule(boolean capsule) {
        this.isCapsule = capsule;
        invalidateSelf();
    }

    public void updateColors() {
        boolean fallback = !SharedConfig.isLiquidGlassBlurActuallyEnabled();

        if (level == LEVEL_LIGHT) {
            int fill = Theme.getColor(Theme.key_glass_l1_fill);
            if (fallback) {
                // denser fill fallback when blur is unavailable
                fill = 0x8C1B202A;
            }
            fillPaint.setColor(fill);
            strokePaint.setColor(Theme.getColor(Theme.key_glass_l1_stroke));
            highlightTopPaint.setColor(Theme.getColor(Theme.key_glass_l1_highlight));
            highlightBottomPaint.setColor(0);
        } else if (level == LEVEL_MAIN) {
            int fill = Theme.getColor(Theme.key_glass_l2_fill);
            if (fallback) {
                fill = 0xAA161A22;
            }
            fillPaint.setColor(fill);
            strokePaint.setColor(Theme.getColor(Theme.key_glass_l2_stroke));
            highlightTopPaint.setColor(Theme.getColor(Theme.key_glass_l2_highlight));
            highlightBottomPaint.setColor(Theme.getColor(Theme.key_glass_l2_highlight_bottom));
        } else if (level == LEVEL_DIALOG) {
            int fill = Theme.getColor(Theme.key_glass_dialog_fill);
            if (fallback) {
                fill = 0xF0181C25;
            }
            fillPaint.setColor(fill);
            strokePaint.setColor(Theme.getColor(Theme.key_glass_l2_stroke));
            highlightTopPaint.setColor(Theme.getColor(Theme.key_glass_l2_highlight));
            highlightBottomPaint.setColor(Theme.getColor(Theme.key_glass_l2_highlight_bottom));
        } else { // LEVEL_ACTIVE
            int fill = Theme.getColor(Theme.key_glass_l3_fill);
            if (fallback) {
                fill = 0x40FFFFFF;
            }
            fillPaint.setColor(fill);
            strokePaint.setColor(Theme.getColor(Theme.key_glass_l3_stroke));
            highlightTopPaint.setColor(Theme.getColor(Theme.key_glass_l3_highlight));
            highlightBottomPaint.setColor(0);
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        boundsF.set(bounds);

        float halfStroke = strokeWidth / 2f;
        strokeBoundsF.set(boundsF.left + halfStroke, boundsF.top + halfStroke, boundsF.right - halfStroke, boundsF.bottom - halfStroke);

        topHighlightBoundsF.set(boundsF.left + strokeWidth, boundsF.top + strokeWidth, boundsF.right - strokeWidth, boundsF.top + strokeWidth + highlightHeight);
        bottomHighlightBoundsF.set(boundsF.left + strokeWidth, boundsF.bottom - strokeWidth - highlightHeight, boundsF.right - strokeWidth, boundsF.bottom - strokeWidth);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (boundsF.width() <= 0 || boundsF.height() <= 0) {
            return;
        }

        float radius = isCapsule ? boundsF.height() / 2f : cornerRadius;

        // 1. Glass background fill
        canvas.drawRoundRect(boundsF, radius, radius, fillPaint);

        // 2. Subtle top highlight
        if (highlightTopPaint.getColor() != 0) {
            canvas.save();
            canvas.clipRect(topHighlightBoundsF);
            canvas.drawRoundRect(boundsF, radius, radius, highlightTopPaint);
            canvas.restore();
        }

        // 3. Subtle bottom highlight (Level 2 & Dialog)
        if ((level == LEVEL_MAIN || level == LEVEL_DIALOG) && highlightBottomPaint.getColor() != 0) {
            canvas.save();
            canvas.clipRect(bottomHighlightBoundsF);
            canvas.drawRoundRect(boundsF, radius, radius, highlightBottomPaint);
            canvas.restore();
        }

        // 4. Perimeter 1dp stroke
        float strokeRadius = Math.max(0, radius - (strokeWidth / 2f));
        canvas.drawRoundRect(strokeBoundsF, strokeRadius, strokeRadius, strokePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        strokePaint.setAlpha(alpha);
        highlightTopPaint.setAlpha(alpha);
        highlightBottomPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
        highlightTopPaint.setColorFilter(colorFilter);
        highlightBottomPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
