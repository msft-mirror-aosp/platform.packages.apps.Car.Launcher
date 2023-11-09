/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.carlauncher;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * Banner view to display text with multiple cta actions
 */
public class Banner extends ConstraintLayout {

    private final TextView mFirstButton;
    private final TextView mSecondButton;
    private final TextView mTitleTextView;

    public Banner(@NonNull Context context) {
        this(context, null);
    }

    public Banner(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Banner(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public Banner(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.banner, this);

        mFirstButton = requireViewById(R.id.first_button);
        mSecondButton = requireViewById(R.id.second_button);
        mTitleTextView = requireViewById(R.id.banner_title);

        TypedArray attrArray = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.Banner,
                defStyleAttr,
                defStyleRes);

        try {
            setFirstButtonText(attrArray.getString(R.styleable.Banner_first_button_text));
            setSecondButtonText(attrArray.getString(R.styleable.Banner_second_button_text));
            setTitleText(attrArray.getString(R.styleable.Banner_title_text));
        } finally {
            attrArray.recycle();
        }
    }

    /**
     * Sets the text to be displayed on the first button
     *
     * @param text text to be displayed
     */
    public void setFirstButtonText(String text) {
        mFirstButton.setText(text);
    }

    /**
     * Register a callback to be invoked when the first button is clicked.
     *
     * @param listener The callback that will run on clicking the button
     */
    public void setFirstButtonOnClickListener(@Nullable View.OnClickListener listener) {
        mFirstButton.setOnClickListener(listener);
    }

    /**
     * Sets the text to be displayed on the second button
     *
     * @param text text to be displayed
     */
    public void setSecondButtonText(String text) {
        mSecondButton.setText(text);
    }

    /**
     * Register a callback to be invoked when the first button is clicked.
     *
     * @param listener The callback that will run on clicking the button
     */
    public void setSecondButtonOnClickListener(@Nullable View.OnClickListener listener) {
        mSecondButton.setOnClickListener(listener);
    }

    /**
     * Sets the primary text to be displayed on banner
     *
     * @param text text to be displayed
     */
    public void setTitleText(String text) {
        mTitleTextView.setText(text);
    }
}