/*
 * Copyright (C) 2010 Daniel Nilsson
 * Copyright (C) 2013 Slimroms
 * Copyright (C) 2015 DarkKat
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

package net.margaritov.preference.colorpicker;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.view.ViewCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.android.settings.R;

public class ColorPickerDialog extends Dialog implements
        ColorPickerView.OnColorChangedListener, PopupMenu.OnMenuItemClickListener,
        TextWatcher, View.OnClickListener, View.OnLongClickListener, View.OnFocusChangeListener {

    private static final String PREFERENCE_NAME  =
            "color_picker_dialog";
    private static final String FAVORITE_COLOR_BUTTON  =
            "favorite_color_button_";

    private static final int PALETTE_VRTOXIN     = 0;
    private static final int PALETTE_BLACKOUT    = 1;
    private static final int PALETTE_MATERIAL    = 2;
    private static final int PALETTE_RGB         = 3;

    private View mColorPickerView;
    private LinearLayout mActionBarMain;
    private LinearLayout mActionBarEditHex;

    private ImageButton mBackButton;
    private ImageButton mEditHexButton;
    private ImageButton mPaletteSwitchButton;
    private ImageButton mResetButton;

    private ImageButton mHexBackButton;
    private EditText mHex;
    private ImageButton mSetButton;
    private View mDivider;

    private ColorPickerView mColorPicker;
    private TextView mPaletteColorButtonsTitle;
    private ColorPickerColorButton[] mFavoriteColorButtons;
    private ColorPickerColorButton[] mPaletteColorButtons;
    private ColorPickerPanelView mOldColor;
    private ColorPickerPanelView mNewColor;

    private Animator mEditHexBarFadeInAnimator;
    private Animator mEditHexBarFadeOutAnimator;
    private boolean mHideEditHexBar = false;

    private Animator mColorTransitionAnimator;
    private boolean mAnimateColorTransition = true;
    private boolean mIsPaletteColorButtons = true;

    private final int mInitialColor;
    private final int mAndroidColor;
    private final int mVRToxinColor;
    private int mNewColorValue;

    private final ContentResolver mResolver;
    private final Resources mResources;

    private OnColorChangedListener mListener;

    public interface OnColorChangedListener {
        public void onColorChanged(int color);
    }

    public ColorPickerDialog(Context context, int theme, int initialColor,
            int androidColor, int vrtoxinColor) {
        super(context, theme);

        mInitialColor = initialColor;
        mAndroidColor = androidColor;
        mVRToxinColor = vrtoxinColor;
        mResolver = context.getContentResolver();
        mResources = context.getResources();
        setUp();
    }

    private void setUp() {
        // To fight color branding.
        getWindow().setFormat(PixelFormat.RGBA_8888);
        getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        mColorPickerView = inflater.inflate(R.layout.dialog_color_picker, null);
        setContentView(mColorPickerView);

        mActionBarMain = (LinearLayout) mColorPickerView.findViewById(R.id.action_bar_main);

        mActionBarEditHex = (LinearLayout) mColorPickerView.findViewById(R.id.action_bar_edit_hex);
        mActionBarEditHex.setVisibility(View.GONE);

        mDivider = mColorPickerView.findViewById(R.id.divider);
        mDivider.setVisibility(View.GONE);

        mBackButton = (ImageButton) mColorPickerView.findViewById(R.id.back);
        mBackButton.setOnClickListener(this);

        mEditHexButton = (ImageButton) mColorPickerView.findViewById(R.id.edit_hex);
        mEditHexButton.setOnClickListener(this);

        mPaletteSwitchButton = (ImageButton) mColorPickerView.findViewById(R.id.palette);
        mPaletteSwitchButton.setOnClickListener(this);

        mResetButton = (ImageButton) mColorPickerView.findViewById(R.id.reset);
        if (mAndroidColor != 0x00000000 && mVRToxinColor != 0x00000000) {
            mResetButton.setOnClickListener(this);
        } else {
            mResetButton.setVisibility(View.GONE);
        }

        mHexBackButton = (ImageButton) mColorPickerView.findViewById(R.id.action_bar_edit_hex_back);
        mHexBackButton.setOnClickListener(this);

        mHex = (EditText) mColorPickerView.findViewById(R.id.hex);
        mHex.setText(ColorPickerPreference.convertToARGB(mInitialColor));
        mHex.setOnFocusChangeListener(this);

        mSetButton = (ImageButton) mColorPickerView.findViewById(R.id.enter);
        mSetButton.setOnClickListener(this);

        mColorPicker = (ColorPickerView) mColorPickerView.findViewById(R.id.color_picker_view);
        mColorPicker.setOnColorChangedListener(this);

        mPaletteColorButtonsTitle = (TextView) mColorPickerView.findViewById(R.id.palette_color_buttons_title);
        setUpFavoriteColorButtons();
        setUpPaletteColorButtons();

        mOldColor = (ColorPickerPanelView) mColorPickerView.findViewById(R.id.old_color_panel);
        mOldColor.setOnClickListener(this);

        mNewColor = (ColorPickerPanelView) mColorPickerView.findViewById(R.id.new_color_panel);
        mNewColor.setOnClickListener(this);

        mNewColorValue = mInitialColor;
        mOldColor.setColor(mInitialColor);

        setupAnimators();
        mAnimateColorTransition = false;
        mColorPicker.setColor(mInitialColor, true);
    }

    private void setUpFavoriteColorButtons() {
        TypedArray ta = mResources.obtainTypedArray(R.array.color_picker_favorite_color_buttons);
        mFavoriteColorButtons = new ColorPickerColorButton[4];

        for (int i=0; i<mFavoriteColorButtons.length; i++) {
            int resId = ta.getResourceId(i, 0);
            mFavoriteColorButtons[i] = (ColorPickerColorButton) mColorPickerView.findViewById(resId);
            mFavoriteColorButtons[i].setOnLongClickListener(this);
            if (getFavoriteButtonValue(i) != 0) {
                mFavoriteColorButtons[i].setImageResource(R.drawable.color_picker_color_button_color);
                mFavoriteColorButtons[i].setColor(getFavoriteButtonValue(i));
                mFavoriteColorButtons[i].setOnClickListener(this);
            }
        }

        ta.recycle();
    }

    private void setUpPaletteColorButtons() {
        TypedArray ta = mResources.obtainTypedArray(R.array.color_picker_palette_color_buttons);
        mPaletteColorButtons = new ColorPickerColorButton[8];

        for (int i=0; i<mPaletteColorButtons.length; i++) {
            int resId = ta.getResourceId(i, 0);
            mPaletteColorButtons[i] = (ColorPickerColorButton) mColorPickerView.findViewById(resId);
            mPaletteColorButtons[i].setOnClickListener(this);
        }

        ta.recycle();
        updatePaletteColorButtonsColor();
    }

    private void setupAnimators() {
        mColorPickerView.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mColorPickerView.getViewTreeObserver().removeOnPreDrawListener(this);
                mHideEditHexBar = false;
                mEditHexBarFadeInAnimator = createAlphaAnimator(0, 100);
                mHideEditHexBar = true;
                mEditHexBarFadeOutAnimator = createAlphaAnimator(100, 0);
                return true;
            }
        });
        mColorTransitionAnimator = createColorTransitionAnimator(0, 1);
    }

    private ValueAnimator createAlphaAnimator(int start, int end) {
        ValueAnimator animator = ValueAnimator.ofInt(start, end);

        animator.setDuration(500);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int value = (Integer) valueAnimator.getAnimatedValue();
                float currentAlpha = value / 100f;
                mActionBarMain.setAlpha(1f - currentAlpha);
                mActionBarEditHex.setAlpha(currentAlpha);
                mDivider.setAlpha(currentAlpha);
            }
        });
        if (mHideEditHexBar) {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mActionBarMain.setVisibility(View.VISIBLE);
                    ViewCompat.jumpDrawablesToCurrentState(mActionBarMain);
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mActionBarEditHex.setVisibility(View.GONE);
                    mDivider.setVisibility(View.GONE);
                }
            });
        } else {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mActionBarEditHex.setVisibility(View.VISIBLE);
                    ViewCompat.jumpDrawablesToCurrentState(mActionBarEditHex);
                    mDivider.setVisibility(View.VISIBLE);
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mActionBarMain.setVisibility(View.GONE);
                }
            });
        }
        return animator;
    }

    private ValueAnimator createColorTransitionAnimator(float start, float end) {
        ValueAnimator animator = ValueAnimator.ofFloat(start, end);

        animator.setDuration(500);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                float position = animation.getAnimatedFraction();
                if (mIsPaletteColorButtons) {
                    int[] blended = new int[8];
                    for (int i=0; i<mPaletteColorButtons.length; i++) {
                        blended[i] = blendColors(
                                mPaletteColorButtons[i].getColor(),
                                getPaletteColorButtonColor(getPalette(), i),
                                position);
                        mPaletteColorButtons[i].setColor(blended[i]);
                    }
                } else {
                    int blended = blendColors(mNewColor.getColor(), mNewColorValue, position);
                    mNewColor.setColor(blended);
                }
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mIsPaletteColorButtons) {
                        updatePaletteColorButtonsTitle();
                    } else {
                        mIsPaletteColorButtons = true;
                    }
                }
            });
        return animator;
    }

    /**
     * Set a OnColorChangedListener to get notified when the color selected by the user has changed.
     *
     * @param listener
     */
    public void setOnColorChangedListener(OnColorChangedListener listener) {
        mListener = listener;
    }

    @Override
    public void onColorChanged(int color) {
        mNewColorValue = color;
        if (mAnimateColorTransition == false) {
            mAnimateColorTransition = true;
            mNewColor.setColor(mNewColorValue);
        } else {
            mIsPaletteColorButtons = false;
            mColorTransitionAnimator.start();
        }
        try {
            if (mHex != null) {
                mHex.setText(ColorPickerPreference.convertToARGB(color));
            }
        } catch (Exception e) {

        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.back ||
                v.getId() == R.id.old_color_panel ||
                v.getId() == R.id.new_color_panel) {
            if (mListener != null && v.getId() == R.id.new_color_panel) {
                mListener.onColorChanged(mNewColor.getColor());
            }
            dismiss();
        } else if (v.getId() == R.id.palette) {
            showPalettePopupMenu(v);
        } else if (v.getId() == R.id.edit_hex) {
            showActionBarEditHex();
        } else if (v.getId() == R.id.reset) {
            showResetPopupMenu(v);
        } else if (v.getId() == R.id.action_bar_edit_hex_back) {
            hideActionBarEditHex();
        } else if (v.getId() == R.id.enter) {
            String text = mHex.getText().toString();
            try {
                int newColor = ColorPickerPreference.convertToColorInt(text);
                mColorPicker.setColor(newColor, true);
            } catch (Exception e) {
            }
            hideActionBarEditHex();
        } else {
            boolean isFavoriteColorButton = false;
            for (int j=0; j<mFavoriteColorButtons.length; j++) {
                int favoriteButtonId = mFavoriteColorButtons[j].getId();
                if (v.getId() == favoriteButtonId) {
                    isFavoriteColorButton = true;
                    try {
                        mColorPicker.setColor(mFavoriteColorButtons[j].getColor(), true);
                    } catch (Exception e) {
                    }
                }
            }
            if (!isFavoriteColorButton) {
                for (int i=0; i<mPaletteColorButtons.length; i++) {
                    int paletteColorButtonId = mPaletteColorButtons[i].getId();
                    if (v.getId() == paletteColorButtonId) {
                        try {
                            mColorPicker.setColor(getPaletteColorButtonColor(getPalette(), i), true);
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean onLongClick(View v) {
        for (int i=0; i<mFavoriteColorButtons.length; i++) {
            int favoriteButtonId = mFavoriteColorButtons[i].getId();
            if (v.getId() == favoriteButtonId) {
                if (!v.hasOnClickListeners()) {
                    mFavoriteColorButtons[i].setImageResource(R.drawable.color_picker_color_button_color);
                    mFavoriteColorButtons[i].setOnClickListener(this);
                }
                mFavoriteColorButtons[i].setColor(mNewColor.getColor());
                writeFavoriteButtonValue(i);
            }
        }
        return true;
    }


    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.palette_vrtoxin) {
            setPalette(PALETTE_VRTOXIN);
            mColorTransitionAnimator.start();
            return true;
        } else if (item.getItemId() == R.id.palette_blackout) {
            setPalette(PALETTE_BLACKOUT);
            mColorTransitionAnimator.start();
            return true;
        } else if (item.getItemId() == R.id.palette_material) {
            setPalette(PALETTE_MATERIAL);
            mColorTransitionAnimator.start();
            return true;
        } else if (item.getItemId() == R.id.palette_rgb) {
            setPalette(PALETTE_RGB);
            mColorTransitionAnimator.start();
            return true;
        } else if (item.getItemId() == R.id.reset_android) {
            mColorPicker.setColor(mAndroidColor, true);
            return true;
        } else if (item.getItemId() == R.id.reset_vrtoxin) {
            mColorPicker.setColor(mVRToxinColor, true);
            return true;
        }
        return false;
    }

    private void showPalettePopupMenu(View v) {
        PopupMenu popup = new PopupMenu(getContext(), v);
        popup.setOnMenuItemClickListener(this);
        popup.inflate(R.menu.palette);
        popup.show();
    }

    private void showResetPopupMenu(View v) {
        PopupMenu popup = new PopupMenu(getContext(), v);
        popup.setOnMenuItemClickListener(this);
        popup.inflate(R.menu.reset);
        popup.show();
    }

    private void showActionBarEditHex() {
        mEditHexBarFadeInAnimator.start();
    }

    private void hideActionBarEditHex() {
        mEditHexBarFadeOutAnimator.start();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (!hasFocus) {
            mHex.removeTextChangedListener(this);
            InputMethodManager inputMethodManager = (InputMethodManager) getContext()
                    .getSystemService(Activity.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } else {
            mHex.addTextChangedListener(this);
        }
    }

    private int blendColors(int from, int to, float ratio) {
        final float inverseRatio = 1f - ratio;

        final float a = Color.alpha(to) * ratio + Color.alpha(from) * inverseRatio;
        final float r = Color.red(to) * ratio + Color.red(from) * inverseRatio;
        final float g = Color.green(to) * ratio + Color.green(from) * inverseRatio;
        final float b = Color.blue(to) * ratio + Color.blue(from) * inverseRatio;

        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }

    private int getColor() {
        return mColorPicker.getColor();
    }

    public void setAlphaSliderVisible(boolean visible) {
        mColorPicker.setAlphaSliderVisible(visible);
    }

    private void updatePaletteColorButtonsColor() {
        for (int i=0; i<mPaletteColorButtons.length; i++) {
            mPaletteColorButtons[i].setColor(getPaletteColorButtonColor(getPalette(), i));
        }
        updatePaletteColorButtonsTitle();
    }

    private void updatePaletteColorButtonsTitle() {
        int resId = R.string.palette_vrtoxin_title;
        if (getPalette() == PALETTE_BLACKOUT) {
            resId = R.string.palette_blackout_title;
        } else if (getPalette() == PALETTE_MATERIAL) {
            resId = R.string.palette_material_title;
        } else if (getPalette() == PALETTE_RGB) {
            resId = R.string.palette_rgb_title;
        }
        mPaletteColorButtonsTitle.setText(resId);
    }

    private int getPalette() {
        return Settings.System.getInt(mResolver,
                Settings.System.COLOR_PICKER_PALETTE, PALETTE_VRTOXIN);
    }

    private void setPalette(int palette) {
        Settings.System.putInt(mResolver,
                Settings.System.COLOR_PICKER_PALETTE, palette);
    }

    private int getPaletteColorButtonColor(int pallete, int index) {
        TypedArray ta;
        if (pallete == PALETTE_VRTOXIN) {
            ta = mResources.obtainTypedArray(R.array.color_picker_vrtoxin_palette);
        } else if (pallete == PALETTE_BLACKOUT) {
            ta = mResources.obtainTypedArray(R.array.color_picker_blackout_palette);
        } else if (pallete == PALETTE_MATERIAL) {
            ta = mResources.obtainTypedArray(R.array.color_picker_material_palette);
        } else {
            ta = mResources.obtainTypedArray(R.array.color_picker_rgb_palette);
        }

        int palettecolor = mResources.getColor(ta.getResourceId(index, 0));
        ta.recycle();
        return palettecolor;
    }

    private void writeFavoriteButtonValue(int index) {
        int buttonIndex = index + 1;
        SharedPreferences preferences =
                getContext().getSharedPreferences(PREFERENCE_NAME, Activity.MODE_PRIVATE);
        preferences.edit().putInt(FAVORITE_COLOR_BUTTON + buttonIndex,
                mFavoriteColorButtons[index].getColor()).commit();
    }

    private int getFavoriteButtonValue(int index) {
        int buttonIndex = index + 1;
        SharedPreferences preferences =
                getContext().getSharedPreferences(PREFERENCE_NAME, Activity.MODE_PRIVATE);
        return preferences.getInt(FAVORITE_COLOR_BUTTON + buttonIndex, 0);
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putInt("old_color", mOldColor.getColor());
        state.putInt("new_color", mNewColor.getColor());
        return state;
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mOldColor.setColor(savedInstanceState.getInt("old_color"));
        mColorPicker.setColor(savedInstanceState.getInt("new_color"), true);
        updatePaletteColorButtonsColor();
    }
}
