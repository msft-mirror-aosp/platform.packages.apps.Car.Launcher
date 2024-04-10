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

package com.android.car.carlauncher.homescreen.audio.media;

import static android.car.media.CarMediaIntents.EXTRA_MEDIA_COMPONENT;

import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updateActionsWithPlaybackState;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updatePlayButtonWithPlaybackState;

import android.car.media.CarMediaIntents;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TableLayout;

import com.android.car.carlauncher.R;
import com.android.car.media.common.playback.PlaybackProgress;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.source.MediaSourceColors;
import com.android.car.media.common.ui.PlaybackCardController;

public class MediaCardController extends PlaybackCardController {

    private final MediaIntentRouter mMediaIntentRouter = MediaIntentRouter.getInstance();
    private TableLayout mOverflowActionsGrid;

    /** Builder for {@link MediaCardController}. Overrides build() method to return
     * NowPlayingController rather than base {@link PlaybackCardController}
     */
    public static class Builder extends PlaybackCardController.Builder {

        @Override
        public MediaCardController build() {
            MediaCardController controller = new MediaCardController(this);
            controller.setupController();
            return controller;
        }
    }

    public MediaCardController(Builder builder) {
        super(builder);

        mView.setOnClickListener(view -> {
            MediaSource mediaSource = mDataModel.getMediaSource().getValue();
            Intent intent = new Intent(CarMediaIntents.ACTION_MEDIA_TEMPLATE);
            if (mediaSource != null) {
                intent.putExtra(EXTRA_MEDIA_COMPONENT,
                        mediaSource.getBrowseServiceComponentName().flattenToString());
            }
            mMediaIntentRouter.handleMediaIntent(intent);
        });

        mOverflowActionsGrid = mView.findViewById(R.id.overflow_grid);
    }

    @Override
    protected void updateProgress(PlaybackProgress progress) {
        super.updateProgress(progress);
        if (progress == null || !progress.hasTime()) {
            mSeekBar.setVisibility(View.GONE);
            mLogo.setVisibility(View.GONE);
        }
    }

    @Override
    protected void updateViewsWithMediaSourceColors(MediaSourceColors colors) {
        ColorStateList accentColor = colors != null ? ColorStateList.valueOf(
                colors.getAccentColor(R.color.car_surface)) :
                ColorStateList.valueOf(R.color.car_surface);
        if (mPlayPauseButton != null) {
            mPlayPauseButton.setBackgroundTintList(accentColor);
        }
        if (mSeekBar != null) {
            mSeekBar.setProgressTintList(accentColor);
        }
    }

    @Override
    protected void updatePlaybackState(PlaybackViewModel.PlaybackStateWrapper playbackState) {
        if (playbackState != null) {
            updatePlayButtonWithPlaybackState(mPlayPauseButton, playbackState);
            updateActionsWithPlaybackState(mView.getContext(), mActions, playbackState,
                    mDataModel.getPlaybackController().getValue(),
                    mView.getContext().getDrawable(
                            com.android.car.media.common.R.drawable.ic_skip_previous),
                    mView.getContext().getDrawable(
                            com.android.car.media.common.R.drawable.ic_skip_next),
                    mView.getContext().getDrawable(R.drawable.dark_circle_button_background),
                    mView.getContext().getDrawable(R.drawable.dark_circle_button_background),
                    /* reserveSkipSlots */ true, mView.getContext().getDrawable(
                            R.drawable.empty_action_drawable));
        }
    }
}
