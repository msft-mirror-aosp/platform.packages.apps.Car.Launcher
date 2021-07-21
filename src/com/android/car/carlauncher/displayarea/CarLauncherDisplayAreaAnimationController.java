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

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.view.SurfaceControl;
import android.view.animation.BaseInterpolator;
import android.window.WindowContainerToken;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller class of display area animations (between different states).
 */
public class CarLauncherDisplayAreaAnimationController {
    private static final String TAG = "CarLauncherDisplayAreaAnimationController";
    private static final float FRACTION_START = 0f;
    private static final float FRACTION_END = 1f;

    public static final int TRANSITION_DIRECTION_NONE = 0;
    public static final int TRANSITION_DIRECTION_TRIGGER = 1;
    public static final int TRANSITION_DIRECTION_EXIT = 2;

    private final CarLauncherDisplayAreaInterpolator mInterpolator;
    private final CarLauncherDisplayAreaTransactionHelper mSurfaceTransactionHelper;
    private final ArrayMap<WindowContainerToken,
            CarLauncherDisplayAreaTransitionAnimator>
            mAnimatorMap = new ArrayMap<>();

    /**
     * Constructor of CarLauncherDisplayAreaAnimationController
     */
    public CarLauncherDisplayAreaAnimationController(Context context) {
        mSurfaceTransactionHelper = new CarLauncherDisplayAreaTransactionHelper(context);
        mInterpolator = new CarLauncherDisplayAreaInterpolator();
    }

    @SuppressWarnings("unchecked")
    CarLauncherDisplayAreaTransitionAnimator getAnimator(
            WindowContainerToken token, SurfaceControl leash,
            float startPos, float endPos, Rect displayBounds) {
        CarLauncherDisplayAreaTransitionAnimator animator = mAnimatorMap.get(token);
        if (animator == null) {
            mAnimatorMap.put(token, setupDisplayAreaTransitionAnimator(
                    CarLauncherDisplayAreaTransitionAnimator.ofYOffset(
                            token, leash, startPos, endPos, displayBounds)));
        } else if (animator.isRunning()) {
            animator.updateEndValue(endPos);
        } else {
            animator.cancel();
            mAnimatorMap.put(token, setupDisplayAreaTransitionAnimator(
                    CarLauncherDisplayAreaTransitionAnimator.ofYOffset(
                            token, leash, startPos, endPos, displayBounds)));
        }
        return mAnimatorMap.get(token);
    }

    ArrayMap<WindowContainerToken,
            CarLauncherDisplayAreaTransitionAnimator> getAnimatorMap() {
        return mAnimatorMap;
    }

    boolean isAnimatorsConsumed() {
        return mAnimatorMap.isEmpty();
    }

    void removeAnimator(WindowContainerToken token) {
        CarLauncherDisplayAreaTransitionAnimator animator = mAnimatorMap.remove(token);
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
    }

    CarLauncherDisplayAreaTransitionAnimator setupDisplayAreaTransitionAnimator(
            CarLauncherDisplayAreaTransitionAnimator animator) {
        animator.setSurfaceTransactionHelper(mSurfaceTransactionHelper);
        animator.setInterpolator(mInterpolator);
        animator.setFloatValues(FRACTION_START, FRACTION_END);
        return animator;
    }

    /**
     * Animator for display area transition animation which supports both alpha and bounds
     * animation.
     *
     * @param <T> Type of property to animate, either offset (float)
     */
    public abstract static class CarLauncherDisplayAreaTransitionAnimator<T> extends
            ValueAnimator implements
            ValueAnimator.AnimatorUpdateListener,
            ValueAnimator.AnimatorListener {

        private final SurfaceControl mLeash;
        private final WindowContainerToken mToken;
        private T mStartValue;
        private T mEndValue;
        private T mCurrentValue;

        private final List<CarLauncherDisplayAreaAnimationCallback>
                mDisplayAreaAnimationCallbacks =
                new ArrayList<>();
        private CarLauncherDisplayAreaTransactionHelper mSurfaceTransactionHelper;
        private final CarLauncherDisplayAreaTransactionHelper.SurfaceControlTransactionFactory
                mSurfaceControlTransactionFactory;

        int mTransitionDirection;

        private CarLauncherDisplayAreaTransitionAnimator(WindowContainerToken token,
                SurfaceControl leash,
                T startValue, T endValue) {
            mLeash = leash;
            mToken = token;
            mStartValue = startValue;
            mEndValue = endValue;
            addListener(this);
            addUpdateListener(this);
            mSurfaceControlTransactionFactory = SurfaceControl.Transaction::new;
            mTransitionDirection = TRANSITION_DIRECTION_NONE;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mCurrentValue = mStartValue;
            mDisplayAreaAnimationCallbacks.forEach(
                    callback -> callback.onAnimationStart(this)
            );
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mCurrentValue = mEndValue;
            SurfaceControl.Transaction tx = newSurfaceControlTransaction();
            onEndTransaction(mLeash, tx);
            mDisplayAreaAnimationCallbacks.forEach(
                    callback -> callback.onAnimationEnd(tx, this)
            );
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mCurrentValue = mEndValue;
            mDisplayAreaAnimationCallbacks.forEach(
                    callback -> callback.onAnimationCancel(this)
            );
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            SurfaceControl.Transaction tx = newSurfaceControlTransaction();
            applySurfaceControlTransaction(mLeash, tx, animation.getAnimatedFraction());
            mDisplayAreaAnimationCallbacks.forEach(
                    callback -> callback.onAnimationUpdate(0f, (float) mCurrentValue)
            );
        }

        void onStartTransaction(SurfaceControl leash, SurfaceControl.Transaction tx) {
        }

        void onEndTransaction(SurfaceControl leash, SurfaceControl.Transaction tx) {
        }

        abstract void applySurfaceControlTransaction(SurfaceControl leash,
                SurfaceControl.Transaction tx, float fraction);

        CarLauncherDisplayAreaTransactionHelper getSurfaceTransactionHelper() {
            return mSurfaceTransactionHelper;
        }

        void setSurfaceTransactionHelper(CarLauncherDisplayAreaTransactionHelper helper) {
            mSurfaceTransactionHelper = helper;
        }

        CarLauncherDisplayAreaTransitionAnimator<T> addDisplayAreaAnimationCallback(
                CarLauncherDisplayAreaAnimationCallback callback) {
            mDisplayAreaAnimationCallbacks.add(callback);
            return this;
        }

        WindowContainerToken getToken() {
            return mToken;
        }

        float getDestinationOffset() {
            return ((float) mEndValue - (float) mStartValue);
        }

        int getTransitionDirection() {
            return mTransitionDirection;
        }

        CarLauncherDisplayAreaTransitionAnimator<T> setTransitionDirection(
                int direction) {
            mTransitionDirection = direction;
            return this;
        }

        T getStartValue() {
            return mStartValue;
        }

        T getEndValue() {
            return mEndValue;
        }

        void setCurrentValue(T value) {
            mCurrentValue = value;
        }

        /**
         * Updates the {@link #mEndValue}.
         */
        void updateEndValue(T endValue) {
            mEndValue = endValue;
        }

        SurfaceControl.Transaction newSurfaceControlTransaction() {
            return mSurfaceControlTransactionFactory.getTransaction();
        }

        @VisibleForTesting
        static CarLauncherDisplayAreaTransitionAnimator<Float> ofYOffset(
                WindowContainerToken token,
                SurfaceControl leash, float startValue, float endValue, Rect displayBounds) {

            return new CarLauncherDisplayAreaTransitionAnimator<Float>(
                    token, leash, startValue, endValue) {

                private final Rect mTmpRect = new Rect(displayBounds);

                private float getCastedFractionValue(float start, float end, float fraction) {
                    return (start * (1 - fraction) + end * fraction + .5f);
                }

                @Override
                void applySurfaceControlTransaction(SurfaceControl leash,
                        SurfaceControl.Transaction tx, float fraction) {
                    float start = getStartValue();
                    float end = getEndValue();
                    float currentValue = getCastedFractionValue(start, end, fraction);
                    mTmpRect.set(
                            mTmpRect.left,
                            mTmpRect.top + Math.round(currentValue),
                            mTmpRect.right,
                            mTmpRect.bottom + Math.round(currentValue));
                    setCurrentValue(currentValue);
                    getSurfaceTransactionHelper()
                            .crop(tx, leash, mTmpRect)
                            .round(tx, leash)
                            .translate(tx, leash, currentValue);
                    tx.apply();
                }

                @Override
                void onStartTransaction(SurfaceControl leash, SurfaceControl.Transaction tx) {
                    getSurfaceTransactionHelper()
                            .crop(tx, leash, mTmpRect)
                            .round(tx, leash)
                            .translate(tx, leash, getStartValue());
                    tx.apply();
                }
            };
        }
    }

    /**
     * An Interpolator for display area transition animation.
     */
    public static class CarLauncherDisplayAreaInterpolator extends BaseInterpolator {
        @Override
        public float getInterpolation(float input) {
            return (float) (Math.pow(2, -10 * input) * Math.sin(((input - 4.0f) / 4.0f)
                    * (2.0f * Math.PI) / 4.0f) + 1);
        }
    }

    void dump(@NonNull PrintWriter pw) {
        String innerPrefix = "  ";
        pw.println(TAG + "states: ");
        pw.print(innerPrefix + "mAnimatorMap=");
        pw.println(mAnimatorMap);

        if (mSurfaceTransactionHelper != null) {
            mSurfaceTransactionHelper.dump(pw);
        }
    }
}

