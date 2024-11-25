/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.carlauncher.homescreen.audio;

import com.android.car.carlauncher.homescreen.HomeCardInterface;

/** A wrapper around {@code MediaViewModel} and {@code InCallModel}. */
public class AudioCardModel implements HomeCardInterface.Model {

    private final MediaViewModel mMediaViewModel;
    private final InCallModel mInCallViewModel;

    public AudioCardModel(MediaViewModel mediaViewModel, InCallModel inCallModel) {
        mMediaViewModel = mediaViewModel;
        mInCallViewModel = inCallModel;
    }

    MediaViewModel getMediaViewModel() {
        return mMediaViewModel;
    }

    InCallModel getInCallViewModel() {
        return mInCallViewModel;
    }

    @Override
    public void setOnModelUpdateListener(OnModelUpdateListener onModelUpdateListener) {
        // No-op
    }
}
