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

import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updateActionsWithPlaybackState;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updatePlayButtonWithPlaybackState;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TableLayout;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.transition.Slide;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.android.car.apps.common.util.ViewUtils;
import com.android.car.carlauncher.R;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.playback.PlaybackProgress;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.source.MediaSourceColors;
import com.android.car.media.common.ui.PlaybackCardController;

public class MediaCardController extends PlaybackCardController {

    private static final int SWIPE_MAX_OFF_PATH = 75;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;

    private final MediaIntentRouter mMediaIntentRouter = MediaIntentRouter.getInstance();
    private Resources mViewResources;
    private TableLayout mOverflowActionsGrid;
    private FrameLayout mQueueRecyclerView;
    private FrameLayout mHistoryRecyclerView;
    private View mPanelHandlebar;
    private LinearLayout mPanel;
    private ObjectAnimator mPanelAnimation;
    private ObjectAnimator mPanelHandlebarAnimation;
    private MediaCardFragment.MediaCardViewModel mCardViewModel;
    private int mSkipPrevVisibility;
    private int mSkipNextVisibility;
    private int mAlbumCoverVisibility;
    private int mSubtitleVisibility;
    private int mLogoVisibility;

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

        mCardViewModel = (MediaCardFragment.MediaCardViewModel) mViewModel;
        mViewResources = mView.getContext().getResources();

        mView.setOnClickListener(view -> {
            MediaSource mediaSource = mDataModel.getMediaSource().getValue();
            Intent intent = mediaSource != null ? mediaSource.getIntent() : null;
            mMediaIntentRouter.handleMediaIntent(intent);
        });

        mPanel = mView.findViewById(R.id.button_panel_background);
        mPanelAnimation = ObjectAnimator.ofFloat(mPanel, "y",
                        mViewResources.getDimension(
                                R.dimen.media_card_bottom_panel_margin_top)
                                - mViewResources.getDimension(
                                        R.dimen.media_card_bottom_panel_button_size)
                                - mViewResources.getDimension(
                                        R.dimen.media_card_margin_panel_open))
                .setDuration(mViewResources.getInteger(
                        R.integer.media_card_bottom_panel_open_duration));

        mOverflowActionsGrid = mView.findViewById(R.id.overflow_grid);
        mQueueRecyclerView = mView.findViewById(R.id.queue_list_container);
        mHistoryRecyclerView = mView.findViewById(R.id.history_list_container);
        mOverflowActionsGrid.setOnClickListener(null);
        mQueueRecyclerView.setOnClickListener(null);
        mHistoryRecyclerView.setOnClickListener(null);
        mPanelHandlebar = mView.findViewById(R.id.media_card_panel_handlebar);
        int height = mViewResources.getDisplayMetrics().heightPixels;
        mPanelHandlebarAnimation = ObjectAnimator.ofFloat(mPanelHandlebar, "y",
                height, mViewResources.getDimension(
                        R.dimen.media_card_bottom_panel_margin_top))
                .setDuration(mViewResources.getInteger(
                        R.integer.media_card_bottom_panel_open_duration));
        mPanelHandlebarAnimation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation, boolean isReverse) {
                mPanelHandlebar.setVisibility(View.VISIBLE);
                if (!isReverse) {
                    setPanelLayoutParams(mViewResources.getDimension(
                                    R.dimen.media_card_bottom_panel_button_size),
                            mViewResources.getDimension(R.dimen.media_card_horizontal_margin));
                    mPanel.setSelected(true);

                    mAlbumCoverVisibility = mAlbumCover.getVisibility();
                    mAlbumCover.setVisibility(View.INVISIBLE);
                    mAppIcon.setVisibility(View.INVISIBLE);
                } else {
                    setPanelLayoutParams(mViewResources.getDimension(
                            R.dimen.media_card_bottom_panel_height), 0);
                    mPanel.setSelected(false);

                    setPlayButtonLayoutParams(0, 0, ConstraintSet.UNSET, 1);
                    setSeekBarLayoutParams(mViewResources.getDimension(
                            R.dimen.media_card_horizontal_margin),
                            ConstraintSet.PARENT_ID, ConstraintSet.UNSET, ConstraintSet.UNSET,
                            mSubtitle.getId());
                    mSeekBar.getThumb().mutate().setAlpha(255);
                    mSeekBar.setSplitTrack(true);

                    setTitleLayoutParams(mViewResources.getDimension(
                                    R.dimen.media_card_horizontal_margin),
                            ConstraintSet.PARENT_ID, ConstraintSet.UNSET, ConstraintSet.UNSET,
                            ConstraintSet.UNSET);
                    mTitle.setTextAppearance(R.style.TextAppearance_Car_Body_Medium);

                    mAlbumCover.setVisibility(mAlbumCoverVisibility);
                    mAppIcon.setVisibility(View.VISIBLE);
                    mActions.get(0).setVisibility(mSkipPrevVisibility);
                    mActions.get(1).setVisibility(mSkipNextVisibility);
                    mSubtitle.setVisibility(mSubtitleVisibility);
                    mLogo.setVisibility(mLogoVisibility);
                }
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation, boolean isReverse) {
                if (!isReverse) {
                    setPlayButtonLayoutParams(mViewResources.getDimension(
                            R.dimen.media_card_large_button_size), mViewResources.getDimension(
                            R.dimen.media_card_margin_panel_open), 0, 0);
                    mSkipPrevVisibility = mActions.get(0).getVisibility();
                    mSkipNextVisibility = mActions.get(1).getVisibility();
                    mActions.get(0).setVisibility(View.GONE);
                    mActions.get(1).setVisibility(View.GONE);

                    setSeekBarLayoutParams(mViewResources.getDimension(
                            R.dimen.media_card_margin_panel_open),
                            ConstraintSet.UNSET, mPlayPauseButton.getId(), mPlayPauseButton.getId(),
                            mTitle.getId());
                    mSeekBar.getThumb().mutate().setAlpha(0);
                    mSeekBar.setSplitTrack(false);

                    setTitleLayoutParams(mViewResources.getDimension(
                            R.dimen.media_card_margin_panel_open),
                            ConstraintSet.UNSET, mPlayPauseButton.getId(), mPlayPauseButton.getId(),
                            mSeekBar.getId());
                    mTitle.setTextAppearance(R.style.TextAppearance_Car_Body_Small);

                    mSubtitleVisibility = mSubtitle.getVisibility();
                    mLogoVisibility = mLogo.getVisibility();
                    mSubtitle.setVisibility(View.GONE);
                    mLogo.setVisibility(View.GONE);
                }
            }

            @Override
            public void onAnimationStart(@NonNull Animator animation) {
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
            }

            @Override
            public void onAnimationCancel(@NonNull Animator animation) {
            }

            @Override
            public void onAnimationRepeat(@NonNull Animator animation) {
            }
        });

        GestureDetector mCloseGestureDetector = new GestureDetector(mView.getContext(),
                new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onSingleTapUp(@NonNull MotionEvent e) {
                        return super.onSingleTapUp(e);
                    }

                    @Override
                    public boolean onFling(MotionEvent event1, MotionEvent event2,
                            float velocityX, float velocityY) {
                        if (Math.abs(event1.getX() - event2.getX()) > SWIPE_MAX_OFF_PATH
                                || Math.abs(velocityY) < SWIPE_THRESHOLD_VELOCITY) {
                            // swipe was not vertical or was not fast enough
                            return false;
                        }
                        boolean isInClosingDirection = velocityY > 0;
                        if (isInClosingDirection) {
                            animateClosePanel();
                            return true;
                        }
                        return false;
                    }
                }
        );
        mPanelHandlebar.setOnClickListener(null);
        mPanelHandlebar.setOnTouchListener((v, event) -> {
            return mCloseGestureDetector.onTouchEvent(event);
        });
    }

    @Override
    protected void setupController() {
        super.setupController();

        mSkipPrevVisibility = mActions.get(0).getVisibility();
        mSkipNextVisibility = mActions.get(1).getVisibility();
        mAlbumCoverVisibility = mAlbumCover.getVisibility();
        mSubtitleVisibility = mSubtitle.getVisibility();
        mLogoVisibility = mLogo.getVisibility();
    }

    @Override
    protected void updateMetadata(MediaItemMetadata metadata) {
        super.updateMetadata(metadata);
        if (mCardViewModel.getPanelExpanded()) {
            mSubtitleVisibility = mSubtitle.getVisibility();
            mSubtitle.setVisibility(View.GONE);
        }
    }

    @Override
    protected void updateAlbumCoverWithDrawable(Drawable drawable) {
        super.updateAlbumCoverWithDrawable(drawable);
        if (mCardViewModel.getPanelExpanded()) {
            mAlbumCoverVisibility = mAlbumCover.getVisibility();
            mAlbumCover.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void updateLogoWithDrawable(Drawable drawable) {
        super.updateLogoWithDrawable(drawable);
        if (mCardViewModel.getPanelExpanded()) {
            mLogoVisibility = mLogo.getVisibility();
            mLogo.setVisibility(View.GONE);
        }
    }

    @Override
    protected void updateMediaSource(MediaSource mediaSource) {
        super.updateMediaSource(mediaSource);
        if (mCardViewModel.getPanelExpanded()) {
            mAppIcon.setVisibility(View.INVISIBLE);
        }
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
        int defaultColor = mViewResources.getColor(R.color.car_on_surface, null);
        ColorStateList accentColor = colors != null ? ColorStateList.valueOf(
                colors.getAccentColor(defaultColor)) :
                ColorStateList.valueOf(defaultColor);

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
        } else {
            mActions.get(0).setVisibility(View.GONE);
            mActions.get(1).setVisibility(View.GONE);
        }
        if (mCardViewModel.getPanelExpanded()) {
            mSkipPrevVisibility = mActions.get(0).getVisibility();
            mSkipNextVisibility = mActions.get(1).getVisibility();
            mActions.get(0).setVisibility(View.GONE);
            mActions.get(1).setVisibility(View.GONE);
        }
    }

    @Override
    protected void setUpActionsOverflowButton() {
        super.setUpActionsOverflowButton();
        setOverflowState(mCardViewModel.getOverflowExpanded(), false);
    }

    @Override
    protected void handleCustomActionsOverflowButtonClicked(View overflow) {
        super.handleCustomActionsOverflowButtonClicked(overflow);
        setOverflowState(mCardViewModel.getOverflowExpanded(), true);
    }

    @Override
    protected void setUpQueueButton() {
        super.setUpQueueButton();
        setQueueState(mCardViewModel.getQueueVisible(), false);
    }

    @Override
    protected void updateQueueState(boolean hasQueue, boolean isQueueVisible) {
        super.updateQueueState(hasQueue, isQueueVisible);
        ViewUtils.setVisible(mQueueButton, hasQueue);
        if (mCardViewModel.getPanelExpanded()) {
            animateClosePanel();
        }
    }

    @Override
    protected void handleQueueButtonClicked(View queue) {
        super.handleQueueButtonClicked(queue);
        setQueueState(mCardViewModel.getQueueVisible(), true);
    }

    @Override
    protected void setUpHistoryButton() {
        super.setUpHistoryButton();
        setHistoryState(mCardViewModel.getHistoryVisible(), false);
    }

    @Override
    protected void handleHistoryButtonClicked(View history) {
        super.handleHistoryButtonClicked(history);
        setHistoryState(mCardViewModel.getHistoryVisible(), true);
    }

    private void setOverflowState(boolean isExpanded, boolean stateSetThroughClick) {
        if (mActionOverflowButton == null) {
            return;
        }
        if (!mCardViewModel.getPanelExpanded()) {
            if (stateSetThroughClick) {
                handlePanelAndViewOpenAnimations(mOverflowActionsGrid);

                mCardViewModel.setPanelExpanded(true);

                mActionOverflowButton.setSelected(true);
                mCardViewModel.setOverflowExpanded(true);

                mQueueButton.setSelected(false);
                mHistoryButton.setSelected(false);
            } else {
                unselectPanel();
            }
        } else {
            // If the panel is already open and overflow is clicked again,
            // always switch to overflow tab
            mCardViewModel.setOverflowExpanded(true);
            mOverflowActionsGrid.setVisibility(View.VISIBLE);
            mPanelHandlebar.setVisibility(View.VISIBLE);
            mPanel.setSelected(true);
            mActionOverflowButton.setSelected(true);

            //set queue panel to gone
            hideQueue();

            // also set history panel to gone
            hideHistory();
        }
    }

    private void setQueueState(boolean isVisible, boolean stateSetThroughClick) {
        if (mQueueButton == null) {
            return;
        }
        if (!mCardViewModel.getPanelExpanded()) {
            if (stateSetThroughClick) {
                handlePanelAndViewOpenAnimations(mQueueRecyclerView);

                mCardViewModel.setPanelExpanded(true);

                mQueueButton.setSelected(true);
                mCardViewModel.setQueueVisible(true);

                mActionOverflowButton.setSelected(false);
                mHistoryButton.setSelected(false);
            } else {
                unselectPanel();
            }
        } else {
            // If the panel is already open and queue is clicked again,
            // always switch to queue tab
            mCardViewModel.setQueueVisible(true);
            mQueueRecyclerView.setVisibility(View.VISIBLE);
            mPanelHandlebar.setVisibility(View.VISIBLE);
            mPanel.setSelected(true);
            mQueueButton.setSelected(true);

            //set overflow panel to gone
            hideOverflow();

            // also set history panel to gone
            hideHistory();
        }
    }

    private void setHistoryState(boolean isVisible, boolean stateSetThroughClick) {
        if (mHistoryButton == null) {
            return;
        }
        if (!mCardViewModel.getPanelExpanded()) {
            if (stateSetThroughClick) {
                handlePanelAndViewOpenAnimations(mHistoryRecyclerView);

                mCardViewModel.setPanelExpanded(true);

                mHistoryButton.setSelected(true);
                mCardViewModel.setHistoryVisible(true);

                mActionOverflowButton.setSelected(false);
                mQueueButton.setSelected(false);
            } else {
                unselectPanel();
            }
        } else {
            // If the panel is already open and history is clicked again,
            // always switch to history tab
            mCardViewModel.setHistoryVisible(true);
            mHistoryRecyclerView.setVisibility(View.VISIBLE);
            mPanelHandlebar.setVisibility(View.VISIBLE);
            mPanel.setSelected(true);
            mHistoryButton.setSelected(true);

            // set overflow panel to gone
            hideOverflow();

            // also set queue panel to gone
            hideQueue();
        }
    }

    private void animateClosePanel() {
        View transitionTarget = null;
        if (mOverflowActionsGrid.getVisibility() == View.VISIBLE) {
            transitionTarget = mOverflowActionsGrid;
        } else if (mQueueRecyclerView.getVisibility() == View.VISIBLE) {
            transitionTarget = mQueueRecyclerView;
        } else if (mHistoryRecyclerView.getVisibility() == View.VISIBLE) {
            transitionTarget = mHistoryRecyclerView;
        }
        if (transitionTarget != null) {
            doPanelContentTransition(transitionTarget, View.GONE);
        }

        mPanelHandlebarAnimation.reverse();

        mPanelAnimation.reverse();

        mCardViewModel.setPanelExpanded(false);

        unselectAllPanelButtons();
    }

    private void setPanelLayoutParams(float height, float horizontalMargin) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams)
                mPanel.getLayoutParams();
        params.height = (int) Math.ceil(height);
        params.setMarginEnd((int) Math.ceil(horizontalMargin));
        params.setMarginStart((int) Math.ceil(horizontalMargin));
        mPanel.setLayoutParams(params);
    }

    private void setPlayButtonLayoutParams(float width, float topMargin, float horizontalBias,
            float verticalBias) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams)
                mPlayPauseButton.getLayoutParams();
        params.width = (int) Math.ceil(width);
        params.topMargin = (int) Math.ceil(topMargin);
        params.horizontalBias = horizontalBias;
        params.verticalBias = verticalBias;
        mPlayPauseButton.setLayoutParams(params);
    }

    private void setSeekBarLayoutParams(float startMargin, int startToStartId, int startToEndId,
            int bottomToBottomId, int topToBottomId) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams)
                mSeekBar.getLayoutParams();
        params.setMarginStart((int) Math.ceil(startMargin));
        params.startToStart = startToStartId;
        params.startToEnd = startToEndId;
        params.bottomToBottom = bottomToBottomId;
        params.topToBottom = topToBottomId;
        mSeekBar.setLayoutParams(params);
    }

    private void setTitleLayoutParams(float startMargin, int startToStartId, int startToEndId,
            int topToTopId, int bottomToTopId) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams)
                mTitle.getLayoutParams();
        params.setMarginStart((int) Math.ceil(startMargin));
        params.startToStart = startToStartId;
        params.startToEnd = startToEndId;
        params.topToTop = topToTopId;
        params.bottomToTop = bottomToTopId;
        mTitle.setLayoutParams(params);
    }

    private void doPanelContentTransition(View content, int finalVisibility) {
        Transition transition = new Slide(Gravity.BOTTOM);
        transition.setDuration(mViewResources.getInteger(
                R.integer.media_card_bottom_panel_open_duration));
        transition.addTarget(content);
        TransitionManager.beginDelayedTransition(mView, transition);
        content.setVisibility(finalVisibility);
    }

    private void handlePanelAndViewOpenAnimations(View content) {
        doPanelContentTransition(content, View.VISIBLE);

        mPanelHandlebarAnimation.start();

        mPanelAnimation.start();
    }

    private void unselectPanel() {
        mPanel.setSelected(false);

        unselectAllPanelButtons();
    }

    private void hideQueue() {
        mQueueRecyclerView.setVisibility(View.GONE);
        mCardViewModel.setQueueVisible(false);
        mQueueButton.setSelected(false);
    }

    private void hideOverflow() {
        mOverflowActionsGrid.setVisibility(View.GONE);
        mCardViewModel.setOverflowExpanded(false);
        mActionOverflowButton.setSelected(false);
    }

    private void hideHistory() {
        mHistoryRecyclerView.setVisibility(View.GONE);
        mCardViewModel.setHistoryVisible(false);
        mHistoryButton.setSelected(false);
    }

    private void unselectAllPanelButtons() {
        mCardViewModel.setOverflowExpanded(false);
        mActionOverflowButton.setSelected(false);

        mCardViewModel.setQueueVisible(false);
        mQueueButton.setSelected(false);

        mCardViewModel.setHistoryVisible(false);
        mHistoryButton.setSelected(false);
    }
}
