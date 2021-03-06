/*
 * Copyright (C) 2013 ParanoidAndroid.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.halo;

import android.os.Handler;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.util.TypedValue;
import android.provider.Settings;

import com.android.systemui.R;

public class HaloProperties extends FrameLayout {

    public enum Overlay {
        NONE,
        BLACK_X,
        BACK_LEFT,
        BACK_RIGHT,
        DISMISS,
        SILENCE_LEFT,
        SILENCE_RIGHT,
        CLEAR_ALL,
        MESSAGE
    }

    public enum ContentStyle {
        CONTENT_NONE,
        CONTENT_DOWN,
        CONTENT_UP
    }

    public enum MessageType {
        MESSAGE,
        PINNED,
        SYSTEM
    }

    private Handler mAnimQueue = new Handler();
    private LayoutInflater mInflater;

    protected int mHaloX = 0, mHaloY = 0;
    protected int mHaloContentY = 0;
    protected float mHaloContentAlpha = 0;
    private int mHaloContentHeight = 0;

    private Drawable mHaloDismiss;
    private Drawable mHaloBackL;
    private Drawable mHaloBackR;
    private Drawable mHaloBlackX;
    private Drawable mHaloClearAll;
    private Drawable mHaloSilenceL;
    private Drawable mHaloSilenceR;
    private Drawable mHaloMessage;
    private Drawable mHaloCurrentOverlay;
    private Drawable mHaloIconMessage;
    private Drawable mHaloIconPersistent;
    private Drawable mHaloIconPinned;

    protected Drawable mHaloSpeechL, mHaloSpeechR, mHaloSpeechLD, mHaloSpeechRD;

    protected View mHaloBubble;
    protected ImageView mHaloBg, mHaloIcon, mHaloOverlay;

    protected View mHaloContentView, mHaloTickerContent, mHaloTickerWrapper;
    protected TextView mHaloTextView;

    protected View mHaloNumberView;
    protected TextView mHaloNumber;
    protected ImageView mHaloNumberIcon;
    protected RelativeLayout mHaloNumberContainer;

    private float mFraction = 1.0f;
    private int mHaloMessageNumber = 0;
    private MessageType mHaloMessageType = MessageType.MESSAGE;

    private boolean mLastContentStateLeft = true;

    CustomObjectAnimator mHaloOverlayAnimator;

    public HaloProperties(Context context) {
        super(context);

        mHaloDismiss = mContext.getResources().getDrawable(R.drawable.halo_dismiss);
        mHaloBackL = mContext.getResources().getDrawable(R.drawable.halo_back_left);
        mHaloBackR = mContext.getResources().getDrawable(R.drawable.halo_back_right);
        mHaloBlackX = mContext.getResources().getDrawable(R.drawable.halo_black_x);
        mHaloClearAll = mContext.getResources().getDrawable(R.drawable.halo_clear_all);
        mHaloSilenceL = mContext.getResources().getDrawable(R.drawable.halo_silence_left);
        mHaloSilenceR = mContext.getResources().getDrawable(R.drawable.halo_silence_right);
        mHaloMessage = mContext.getResources().getDrawable(R.drawable.halo_message);

        mHaloSpeechL = mContext.getResources().getDrawable(R.drawable.halo_speech_l_u);
        mHaloSpeechR = mContext.getResources().getDrawable(R.drawable.halo_speech_r_u);
        mHaloSpeechLD = mContext.getResources().getDrawable(R.drawable.halo_speech_l_d);
        mHaloSpeechRD = mContext.getResources().getDrawable(R.drawable.halo_speech_r_d);

        mHaloIconMessage = mContext.getResources().getDrawable(R.drawable.halo_batch_message);
        mHaloIconPersistent = mContext.getResources().getDrawable(R.drawable.halo_system_message);
        mHaloIconPinned = mContext.getResources().getDrawable(R.drawable.halo_pinned_app);

        mInflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mHaloBubble = mInflater.inflate(R.layout.halo_bubble, null);
        mHaloBg = (ImageView) mHaloBubble.findViewById(R.id.halo_bg);
        mHaloIcon = (ImageView) mHaloBubble.findViewById(R.id.app_icon);
        mHaloOverlay = (ImageView) mHaloBubble.findViewById(R.id.halo_overlay);

        mHaloContentView = mInflater.inflate(R.layout.halo_speech, null);
        mHaloTickerWrapper = mHaloContentView.findViewById(R.id.ticker_wrapper);
        mHaloTickerContent = mHaloContentView.findViewById(R.id.ticker);
        mHaloTextView = (TextView) mHaloContentView.findViewById(R.id.bubble);
        mHaloTextView.setAlpha(1f);

        mHaloNumberView = mInflater.inflate(R.layout.halo_number, null);
        mHaloNumberContainer = (RelativeLayout)mHaloNumberView.findViewById(R.id.container);
        mHaloNumber = (TextView) mHaloNumberView.findViewById(R.id.number);
        mHaloNumberIcon = (ImageView) mHaloNumberView.findViewById(R.id.icon);
        mHaloNumberIcon.setImageDrawable(mHaloIconMessage);

        mHaloContentHeight = mContext.getResources().getDimensionPixelSize(R.dimen.notification_min_height);

        mFraction = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.HALO_SIZE, 1.0f);
        setHaloSize(mFraction);

        mHaloOverlayAnimator = new CustomObjectAnimator(this);
    }

    int newPaddingHShort;
    int newPaddingHWide;
    int newPaddingVTop;
    int newPaddingVBottom;
    public void setHaloSize(float fraction) {

        final int newBubbleSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_bubble_size) * fraction);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(newBubbleSize, newBubbleSize);
        mHaloBg.setLayoutParams(layoutParams);
        mHaloIcon.setLayoutParams(layoutParams);
        mHaloOverlay.setLayoutParams(layoutParams);

        newPaddingHShort = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_speech_hpadding_short) * fraction);
        newPaddingHWide = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_speech_hpadding_wide) * fraction);
        newPaddingVTop = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_speech_vpadding_top) * fraction);
        newPaddingVBottom = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_speech_vpadding_bottom) * fraction);        

        final int newBatchSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_number_size) * fraction);
        final int newBatchIconSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_number_icon_size) * fraction);
        final int newNumberTextSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_number_text_size) * fraction);

        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(newBatchSize, newBatchSize);
        mHaloNumberContainer.setLayoutParams(layoutParams2);

        RelativeLayout.LayoutParams layoutParams3 = new RelativeLayout.LayoutParams(newBatchSize, newBatchSize);
        layoutParams3.addRule(RelativeLayout.CENTER_VERTICAL);
        layoutParams3.addRule(RelativeLayout.CENTER_HORIZONTAL);
        mHaloNumber.setLayoutParams(layoutParams3);
        mHaloNumber.setTextSize(TypedValue.COMPLEX_UNIT_PX, newNumberTextSize);

        RelativeLayout.LayoutParams layoutParams4 = new RelativeLayout.LayoutParams(newBatchIconSize, newBatchIconSize);
        layoutParams4.addRule(RelativeLayout.CENTER_VERTICAL);
        layoutParams4.addRule(RelativeLayout.CENTER_HORIZONTAL);
        mHaloNumberIcon.setLayoutParams(layoutParams4);

        updateResources(mLastContentStateLeft);
    }

    public void setHaloX(int value) {
        mHaloX = value;
    }

    public void setHaloY(int value) {
        mHaloY = value;
    }

    public int getHaloX() {
        return mHaloX; 
    }

    public int getHaloY() {
        return mHaloY;
    }

    public void setHaloContentY(int value) {
        mHaloContentY = value;
    }

    public int getHaloContentY() {
        return mHaloContentY; 
    }

    protected CustomObjectAnimator msgNumberFlipAnimator = new CustomObjectAnimator(this);
    protected CustomObjectAnimator msgNumberAlphaAnimator = new CustomObjectAnimator(this);
    public void animateHaloBatch(final int value, final int msgCount, final boolean alwaysFlip, int delay, final MessageType msgType) {
        if (msgCount == 0) {
            msgNumberAlphaAnimator.animate(ObjectAnimator.ofFloat(mHaloNumberContainer, "alpha", 0f).setDuration(1000),
                    new DecelerateInterpolator(), null, delay, null);
            return;
        }
        mAnimQueue.removeCallbacksAndMessages(null);
        mAnimQueue.postDelayed(new Runnable() {
            public void run() {
                // Allow transitions only if no overlay is set
                if (mHaloCurrentOverlay == null) {
                    msgNumberAlphaAnimator.cancel(true);
                    float oldAlpha = mHaloNumberContainer.getAlpha();

                    mHaloNumberContainer.getBackground().clearColorFilter();
                    mHaloNumberContainer.setAlpha(1f);
                    mHaloNumber.setAlpha(0f);
                    mHaloNumberIcon.setAlpha(0f);

                    if (msgCount > 0) {
                        mHaloNumberContainer.getBackground().setColorFilter(0xff4fa736, PorterDuff.Mode.SRC_IN);
                        mHaloNumber.setText(String.valueOf(msgCount));
                        mHaloNumber.setAlpha(1f);
                    } else if (value < 1 && msgCount < 1) {
                        if (msgType == MessageType.PINNED) {
                            mHaloNumberIcon.setImageDrawable(mHaloIconPinned);
                        } else if (msgType == MessageType.SYSTEM) {
                            mHaloNumberIcon.setImageDrawable(mHaloIconPersistent);
                        } else {
                            mHaloNumberIcon.setImageDrawable(mHaloIconMessage);
                        }
                        mHaloNumberIcon.setAlpha(1f);
                    } else if (value < 100) {
                        mHaloNumber.setText(String.valueOf(value));
                        mHaloNumber.setAlpha(1f);
                    } else {
                        mHaloNumber.setText("+");
                        mHaloNumber.setAlpha(1f);
                    }
                    
                    if (value < 1 && msgCount < 1) {
                        msgNumberAlphaAnimator.animate(ObjectAnimator.ofFloat(mHaloNumberContainer, "alpha", 0f).setDuration(1000),
                                new DecelerateInterpolator(), null, 1500, null);
                    }

                    // Do NOT flip when ...
                    if (!alwaysFlip && oldAlpha == 1f && mHaloMessageType == msgType
                            && (value == mHaloMessageNumber || (value > 99 && mHaloMessageNumber > 99))) return;

                    msgNumberFlipAnimator.animate(ObjectAnimator.ofFloat(mHaloNumberContainer, "rotationY", -180, 0).setDuration(500),
                                new DecelerateInterpolator(), null);
                }
                mHaloMessageNumber = value;
                mHaloMessageType = msgType;
            }}, delay);
    }

    void setHaloMessageNumber(int count) {
        mHaloNumber.setText(String.valueOf(count));
        invalidate();
    }

    public void setHaloContentAlpha(float value) {
        mHaloTickerWrapper.setAlpha(value);
        mHaloTickerWrapper.invalidate();
        mHaloContentAlpha = value;
    }

    public float getHaloContentAlpha() {
        return mHaloContentAlpha;
    }

    public void setHaloOverlay(Overlay overlay) {
        setHaloOverlay(overlay, mHaloOverlay.getAlpha());
    }

    public void setHaloOverlay(Overlay overlay, float overlayAlpha) {

        Drawable d = null;

        switch(overlay) {
            case BLACK_X:
                d = mHaloBlackX;
                break;
            case BACK_LEFT:
                d = mHaloBackL;
                break;
            case BACK_RIGHT:
                d = mHaloBackR;
                break;
            case DISMISS:
                d = mHaloDismiss;
                break;
            case SILENCE_LEFT:
                d = mHaloSilenceL;
                break;
            case SILENCE_RIGHT:
                d = mHaloSilenceR;
                break;
            case CLEAR_ALL:
                d = mHaloClearAll;
                break;
            case MESSAGE:
                d = mHaloMessage;
                break;
        }

        if (d != mHaloCurrentOverlay) {
            mHaloOverlay.setImageDrawable(d);
            mHaloCurrentOverlay = d;

            // Fade out number batch
            if (overlay != Overlay.NONE) {
                msgNumberAlphaAnimator.animate(ObjectAnimator.ofFloat(mHaloNumberContainer, "alpha", 0f).setDuration(100),
                        new DecelerateInterpolator(), null);
            }
        }

        mHaloOverlay.setAlpha(overlayAlpha);
        updateResources(mLastContentStateLeft);
    }

    private ContentStyle mLastContentStyle = ContentStyle.CONTENT_NONE;
    public void setHaloContentBackground(boolean contentLeft, ContentStyle style) {
        if (contentLeft != mLastContentStateLeft || style != mLastContentStyle) {
            // Set background
            switch(style) {
                case CONTENT_UP:
                    mHaloTickerWrapper.setBackground(contentLeft ? mHaloSpeechL : mHaloSpeechR);
                    break;
                case CONTENT_DOWN:
                    mHaloTickerWrapper.setBackground(contentLeft ? mHaloSpeechLD : mHaloSpeechRD);
                    break;
            }

            // ... and override its padding
            if (contentLeft) {
                mHaloTickerWrapper.setPadding(newPaddingHWide, newPaddingVTop, newPaddingHShort, newPaddingVBottom);
            } else {
                mHaloTickerWrapper.setPadding(newPaddingHShort, newPaddingVTop, newPaddingHWide, newPaddingVBottom);
            }

            mLastContentStyle = style;
        }
    }

    public void setHaloContentHeight(int size) {
        mHaloContentHeight = size;
    }

    public void updateResources(boolean contentLeft) {

        // Maximal stretch for speech bubble, wrap_content regular text
        final boolean portrait = getWidth() < getHeight();
        final int iconSize = (int)(mContext.getResources().getDimensionPixelSize(R.dimen.halo_bubble_size) * mFraction);
        int portraitWidth = portrait ? ((int)(getWidth() * 0.95f) - iconSize) : ((int)(getHeight() * 0.95f));
        if (mHaloTextView.getVisibility() == View.VISIBLE) portraitWidth = LinearLayout.LayoutParams.WRAP_CONTENT;

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                portraitWidth, mHaloContentHeight);
        mHaloTickerWrapper.setLayoutParams(layoutParams);

        // Set background and override its padding
        setHaloContentBackground(contentLeft, mLastContentStyle);

        // Measure controls
        mHaloContentView.measure(MeasureSpec.getSize(mHaloContentView.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloContentView.getMeasuredHeight()));
        mHaloContentView.layout(0, 0, 0, 0);

        mHaloBubble.measure(MeasureSpec.getSize(mHaloBubble.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloBubble.getMeasuredHeight()));
        mHaloBubble.layout(0, 0, 0, 0);

        mHaloNumberView.measure(MeasureSpec.getSize(mHaloNumberView.getMeasuredWidth()),
                MeasureSpec.getSize(mHaloNumberView.getMeasuredHeight()));
        mHaloNumberView.layout(0, 0, 0, 0);

        mLastContentStateLeft = contentLeft;
    }
}
