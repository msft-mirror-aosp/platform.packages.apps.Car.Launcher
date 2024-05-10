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

package com.android.car.carlauncher.calmmode;

import static androidx.lifecycle.Lifecycle.State.RESUMED;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import android.app.Activity;

import androidx.fragment.app.testing.FragmentScenario;

import com.android.car.carlauncher.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CalmModeFragmentTest {

    private FragmentScenario<CalmModeFragment> mFragmentScenario;
    private CalmModeFragment mCalmModeFragment;
    private Activity mActivity;

    @Before
    public void setUp() {
        mFragmentScenario =
                FragmentScenario.launchInContainer(
                        CalmModeFragment.class, null, R.style.Theme_CalmMode);
        mFragmentScenario.onFragment(fragment -> mCalmModeFragment = fragment);
        mActivity = mCalmModeFragment.getActivity();
    }

    @After
    public void tearDown() throws InterruptedException {
        if (mFragmentScenario != null) {
            mFragmentScenario.close();
        }
    }

    @Test
    public void fragmentResumed_testContainerTouched_activityFinishes() {
        mFragmentScenario.moveToState(RESUMED);

        onView(withId(R.id.calm_mode_container)).perform(click());

        assertTrue(mActivity.isFinishing());
    }

    @Test
    public void fragmentResumed_testClock_isVisible() {
        mFragmentScenario.moveToState(RESUMED);

        onView(withId(R.id.clock)).check(matches(isDisplayed()));
    }

    @Test
    public void fragmentResumed_testDate_isVisible() {
        mFragmentScenario.moveToState(RESUMED);

        onView(withId(R.id.date)).check(matches(isDisplayed()));
    }

    @Test
    public void fragmentResumed_testMedia_isVisible() {
        String testMediaTitle = "Test media title";
        mFragmentScenario.moveToState(RESUMED);

        mActivity.runOnUiThread(()->mCalmModeFragment.updateMediaTitle(testMediaTitle));

        onView(withId(R.id.media_title)).check(matches(isDisplayed()));
    }

    @Test
    public void fragmentResumed_testMediaTitleNull_isGone() {
        String testMediaTitle = null;
        mFragmentScenario.moveToState(RESUMED);

        mActivity.runOnUiThread(()->mCalmModeFragment.updateMediaTitle(testMediaTitle));

        onView(withId(R.id.media_title)).check(matches(not(isDisplayed())));
    }
}
