package org.telegram.ui.Components.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;

/**
 * GlassFrameLayout:
 * Container for floating liquid glass panels (ActionBar capsule, Bottom Nav, Enter View, Cards)
 * - Renders GlassDrawable background
 * - Applies RenderEffect.createBlurEffect on API 31+ if enabled
 * - Supports capsule and rounded rectangle clipping
 */
public class GlassFrameLayout extends FrameLayout {

    private final GlassDrawable glassDrawable;
    private boolean isCapsule = true;
    private float cornerRadius = AndroidUtilities.dp(22);

    public GlassFrameLayout(@NonNull Context context) {
        this(context, GlassDrawable.LEVEL_MAIN);
    }

    public GlassFrameLayout(@NonNull Context context, int level) {
        super(context);
        glassDrawable = new GlassDrawable(level);
        glassDrawable.setIsCapsule(isCapsule);
        setBackground(glassDrawable);
        setWillNotDraw(false);
        setupOutline();
        setupBlur();
    }

    public void setGlassLevel(int level) {
        glassDrawable.setGlassLevel(level);
    }

    public void setIsCapsule(boolean capsule) {
        this.isCapsule = capsule;
        glassDrawable.setIsCapsule(capsule);
        invalidateOutline();
    }

    public void setCornerRadius(float radius) {
        this.cornerRadius = radius;
        this.isCapsule = false;
        glassDrawable.setCornerRadius(radius);
        invalidateOutline();
    }

    private void setupOutline() {
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float radius = isCapsule ? view.getHeight() / 2f : cornerRadius;
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
    }

    private void setupBlur() {
        if (Build.VERSION.SDK_INT >= 31 && SharedConfig.isLiquidGlassBlurActuallyEnabled()) {
            try {
                float blurRadius = AndroidUtilities.dp(24);
                RenderEffect blur = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP);
                setRenderEffect(blur);
            } catch (Throwable ignored) {
            }
        } else if (Build.VERSION.SDK_INT >= 31) {
            try {
                setRenderEffect(null);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setupBlur();
    }
}
