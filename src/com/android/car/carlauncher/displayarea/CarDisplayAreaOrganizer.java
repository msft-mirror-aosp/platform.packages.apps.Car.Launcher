/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.carlauncher.displayarea;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.car.carlauncher.displayarea.CarDisplayAreaController.BACKGROUND_LAYER_INDEX;
import static com.android.car.carlauncher.displayarea.CarDisplayAreaController.FOREGROUND_LAYER_INDEX;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.SurfaceControl;
import android.window.DisplayAreaAppearedInfo;
import android.window.DisplayAreaInfo;
import android.window.DisplayAreaOrganizer;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.car.carlauncher.AppGridActivity;
import com.android.wm.shell.common.SyncTransactionQueue;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Organizer for controlling the policies defined in
 * {@link com.android.server.wm.CarDisplayAreaPolicyProvider}
 */
public class CarDisplayAreaOrganizer extends DisplayAreaOrganizer {

    /**
     * The display partition to launch applications by default.
     */
    public static final int FOREGROUND_DISPLAY_AREA_ROOT = FEATURE_VENDOR_FIRST + 1;

    /**
     * Background applications task container.
     */
    public static final int BACKGROUND_TASK_CONTAINER = FEATURE_VENDOR_FIRST + 2;

    private static final int FEATURE_TASKDISPLAYAREA_PARENT = FEATURE_VENDOR_FIRST + 3;

    /**
     * Control bar task container.
     */
    public static final int CONTROL_BAR_DISPLAY_AREA = FEATURE_VENDOR_FIRST + 4;

    public static final int FEATURE_TITLE_BAR = FEATURE_VENDOR_FIRST + 5;

    private static CarDisplayAreaOrganizer sCarDisplayAreaOrganizer;

    private final Context mContext;
    private final Intent mMapsIntent;
    private final SyncTransactionQueue mTransactionQueue;
    private final Rect mBackgroundApplicationDisplayBounds = new Rect();
    private final Intent mAudioControlIntent;
    private boolean mIsShowingBackgroundDisplay;
    private boolean mIsShowingControlBarDisplay;
    private final CarLauncherDisplayAreaAnimationController mAnimationController;
    private final Rect mLastVisualDisplayBounds = new Rect();
    private final ArrayMap<WindowContainerToken, SurfaceControl> mDisplayAreaTokenMap =
            new ArrayMap();

    private WindowContainerToken mBackgroundDisplayToken;
    private WindowContainerToken mForegroundDisplayToken;
    private WindowContainerToken mControbarDisplayToken;
    private WindowContainerToken mTitleBarDisplayToken;
    private int mDpiDensity = -1;
    private DisplayAreaAppearedInfo mBackgroundApplicationDisplay;
    private DisplayAreaAppearedInfo mForegroundApplicationDisplay;
    private DisplayAreaAppearedInfo mTitleBarDisplay;
    private DisplayAreaAppearedInfo mControlBarDisplay;
    private boolean mIsRegistered = false;

    private AppGridActivity.CAR_LAUNCHER_STATE mToState;

    @VisibleForTesting
    CarLauncherDisplayAreaAnimationCallback mDisplayAreaAnimationCallback =
            new CarLauncherDisplayAreaAnimationCallback() {
                @Override
                public void onAnimationStart(
                        CarLauncherDisplayAreaAnimationController
                                .CarLauncherDisplayAreaTransitionAnimator animator) {
                    SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
                    // Update the foreground panel layer index to animate on top of the
                    // background DA.
                    tx.setLayer(mForegroundApplicationDisplay.getLeash(),
                            BACKGROUND_LAYER_INDEX + 1);
                    tx.apply(true);
                }

                @Override
                public void onAnimationEnd(SurfaceControl.Transaction tx,
                        CarLauncherDisplayAreaAnimationController
                                .CarLauncherDisplayAreaTransitionAnimator animator) {
                    mAnimationController.removeAnimator(animator.getToken());
                    if (mAnimationController.isAnimatorsConsumed()) {
                        WindowContainerTransaction wct = new WindowContainerTransaction();

                        if (mToState == AppGridActivity.CAR_LAUNCHER_STATE.DEFAULT) {
                            // Foreground DA opens to default height.
                            updateBackgroundDisplayBounds(wct);
                        }
                    }
                }

                @Override
                public void onAnimationCancel(
                        CarLauncherDisplayAreaAnimationController
                                .CarLauncherDisplayAreaTransitionAnimator animator) {
                    mAnimationController.removeAnimator(animator.getToken());
                }
            };

    /**
     * Gets the instance of {@link CarDisplayAreaOrganizer}.
     */
    public static CarDisplayAreaOrganizer getInstance(Executor executor,
            Context context, Intent mapsIntent, Intent audioControlIntent,
            SyncTransactionQueue tx) {
        if (sCarDisplayAreaOrganizer == null) {
            sCarDisplayAreaOrganizer = new CarDisplayAreaOrganizer(executor,
                    context, mapsIntent, audioControlIntent, tx);
        }
        return sCarDisplayAreaOrganizer;
    }

    private CarDisplayAreaOrganizer(Executor executor, Context context, Intent mapsIntent,
            Intent audioControlIntent, SyncTransactionQueue tx) {
        super(executor);
        mContext = context;
        mMapsIntent = mapsIntent;
        mAudioControlIntent = audioControlIntent;
        mTransactionQueue = tx;

        mAnimationController = new CarLauncherDisplayAreaAnimationController(mContext);
    }

    int getDpiDensity() {
        if (mDpiDensity != -1) {
            return mDpiDensity;
        }

        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        Display display = displayManager.getDisplay(DEFAULT_DISPLAY);
        Resources displayResources = mContext.createDisplayContext(display).getResources();
        mDpiDensity = displayResources.getConfiguration().densityDpi;

        return mDpiDensity;
    }

    private void updateBackgroundDisplayBounds(WindowContainerTransaction wct) {
        Rect backgroundApplicationDisplayBound = mBackgroundApplicationDisplayBounds;
        WindowContainerToken backgroundDisplayToken =
                mBackgroundApplicationDisplay.getDisplayAreaInfo().token;

        int backgroundDisplayWidthDp =
                backgroundApplicationDisplayBound.width() * DisplayMetrics.DENSITY_DEFAULT
                        / getDpiDensity();
        int backgroundDisplayHeightDp =
                backgroundApplicationDisplayBound.height() * DisplayMetrics.DENSITY_DEFAULT
                        / getDpiDensity();
        wct.setBounds(backgroundDisplayToken, backgroundApplicationDisplayBound);
        wct.setScreenSizeDp(backgroundDisplayToken, backgroundDisplayWidthDp,
                backgroundDisplayHeightDp);
        wct.setSmallestScreenWidthDp(backgroundDisplayToken,
                Math.min(backgroundDisplayWidthDp, backgroundDisplayHeightDp));

        mTransactionQueue.runInSync(t -> {
            t.setPosition(mBackgroundApplicationDisplay.getLeash(),
                    backgroundApplicationDisplayBound.left,
                    backgroundApplicationDisplayBound.top);
        });

        applyTransaction(wct);
    }

    void resetWindowsOffset() {
        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        mDisplayAreaTokenMap.forEach(
                (token, leash) -> {
                    CarLauncherDisplayAreaAnimationController
                            .CarLauncherDisplayAreaTransitionAnimator animator =
                            mAnimationController.getAnimatorMap().remove(token);
                    if (animator != null && animator.isRunning()) {
                        animator.cancel();
                    }
                    tx.setPosition(leash, /* x= */ 0, /* y= */ 0)
                            .setWindowCrop(leash, /* width= */ -1, /* height= */ -1)
                            .setCornerRadius(leash, /* cornerRadius= */ -1);
                });
        tx.apply();
    }

    /**
     * Offsets the windows by a given offset on Y-axis, triggered also from screen rotation.
     * Directly perform manipulation/offset on the leash.
     */
    void scheduleOffset(int fromPos, int toPos, Rect finalBackgroundBounds,
            DisplayAreaAppearedInfo backgroundApplicationDisplay,
            DisplayAreaAppearedInfo foregroundDisplay,
            DisplayAreaAppearedInfo titleBarDisplay,
            DisplayAreaAppearedInfo controlBarDisplay,
            AppGridActivity.CAR_LAUNCHER_STATE toState,
            int durationMs) {
        mToState = toState;
        mBackgroundApplicationDisplay = backgroundApplicationDisplay;
        mForegroundApplicationDisplay = foregroundDisplay;
        mControlBarDisplay = controlBarDisplay;
        mTitleBarDisplay = titleBarDisplay;
        mDisplayAreaTokenMap.forEach(
                (token, leash) -> {
                    if (token == mBackgroundDisplayToken) {
                        mBackgroundApplicationDisplayBounds.set(finalBackgroundBounds);
                    } else if (token == mForegroundDisplayToken) {
                        animateWindows(token, leash, fromPos, toPos, durationMs);
                    }
                });

        if (mToState == AppGridActivity.CAR_LAUNCHER_STATE.CONTROL_BAR) {
            WindowContainerTransaction wct = new WindowContainerTransaction();
            updateBackgroundDisplayBounds(wct);
        }
    }

    void animateWindows(WindowContainerToken token, SurfaceControl leash, float fromPos,
            float toPos, int durationMs) {
        CarLauncherDisplayAreaAnimationController.CarLauncherDisplayAreaTransitionAnimator
                animator =
                mAnimationController.getAnimator(token, leash, fromPos, toPos,
                        mLastVisualDisplayBounds);

        if (animator != null) {
            animator.addDisplayAreaAnimationCallback(mDisplayAreaAnimationCallback)
                    .setDuration(durationMs)
                    .start();
        }
    }

    @Override
    public void onDisplayAreaAppeared(@NonNull DisplayAreaInfo displayAreaInfo,
            @NonNull SurfaceControl leash) {
        if (displayAreaInfo.featureId == BACKGROUND_TASK_CONTAINER) {
            mBackgroundDisplayToken = displayAreaInfo.token;
            mIsShowingBackgroundDisplay = true;
        } else if (displayAreaInfo.featureId == CONTROL_BAR_DISPLAY_AREA) {
            mControbarDisplayToken = displayAreaInfo.token;
            mIsShowingControlBarDisplay = true;
        } else if (displayAreaInfo.featureId == FOREGROUND_DISPLAY_AREA_ROOT) {
            mForegroundDisplayToken = displayAreaInfo.token;
        } else if (displayAreaInfo.featureId == FEATURE_TITLE_BAR) {
            mTitleBarDisplayToken = displayAreaInfo.token;
        }
        mDisplayAreaTokenMap.put(displayAreaInfo.token, leash);
    }

    WindowContainerToken getBackgroundDisplayToken() {
        return mBackgroundDisplayToken;
    }

    /**
     * Launches the map in the background DA.
     */
    public void startMapsInBackGroundDisplayArea() {
        ActivityOptions options = ActivityOptions
                .makeCustomAnimation(mContext,
                        /* enterResId= */ 0, /* exitResId= */ 0);
        options.setLaunchTaskDisplayArea(mBackgroundDisplayToken);
        mContext.startActivity(mMapsIntent, options.toBundle());
    }

    /**
     * Launches the control bar in the control bar DA.
     */
    public void startControlBarInDisplayArea() {
        ActivityOptions options = ActivityOptions
                .makeCustomAnimation(mContext,
                        /* enterResId= */ 0, /* exitResId= */ 0);
        options.setLaunchTaskDisplayArea(mControbarDisplayToken);
        mContext.startActivity(mAudioControlIntent, options.toBundle());
    }

    @Override
    public void onDisplayAreaVanished(@NonNull DisplayAreaInfo displayAreaInfo) {
        if (displayAreaInfo.featureId == BACKGROUND_TASK_CONTAINER) {
            mIsShowingBackgroundDisplay = false;
        } else if (displayAreaInfo.featureId == CONTROL_BAR_DISPLAY_AREA) {
            mIsShowingControlBarDisplay = false;
        }
        if (!mIsRegistered) {
            mDisplayAreaTokenMap.remove(displayAreaInfo.token);
        }
    }

    @Override
    public void onDisplayAreaInfoChanged(DisplayAreaInfo displayAreaInfo) {
        super.onDisplayAreaInfoChanged(displayAreaInfo);

        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        tx.setLayer(mForegroundApplicationDisplay.getLeash(), FOREGROUND_LAYER_INDEX);
        tx.apply(true);
    }

    @Override
    public List<DisplayAreaAppearedInfo> registerOrganizer(int displayAreaFeature) {
        List<DisplayAreaAppearedInfo> displayAreaInfos =
                super.registerOrganizer(displayAreaFeature);
        for (DisplayAreaAppearedInfo info : displayAreaInfos) {
            onDisplayAreaAppeared(info.getDisplayAreaInfo(), info.getLeash());
        }
        mIsRegistered = true;
        return displayAreaInfos;
    }

    @Override
    public void unregisterOrganizer() {
        super.unregisterOrganizer();
        mIsRegistered = false;
    }
}
