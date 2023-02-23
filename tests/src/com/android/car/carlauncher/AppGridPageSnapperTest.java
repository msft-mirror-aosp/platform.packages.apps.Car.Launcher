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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AppGridPageSnapperTest {
    private AppGridPageSnapper.AppGridPageSnapCallback mAppGridPageSnapCallback;
    private AppGridPageSnapper mPageSnapper;
    private int mRowNo = 3;
    private int mItemPerPage = 15;

    @Rule
    public ActivityScenarioRule<TestActivity> mActivityRule = new ActivityScenarioRule<>(
            TestActivity.class);

    @After
    public void tearDown() {
        for (IdlingResource idlingResource : IdlingRegistry.getInstance().getResources()) {
            IdlingRegistry.getInstance().unregister(idlingResource);
        }

        if (mActivityRule != null) {
            mActivityRule.getScenario().close();
        }

    }

    @Test
    public void testScrollToNextPage() {
        mActivityRule.getScenario().onActivity(
                activity -> activity.setContentView(R.xml.empty_test_activity));
        onView(withId(R.id.list)).check(matches(isDisplayed()));
        TestAdapter adapter = new TestAdapter(100);

        mActivityRule.getScenario().onActivity(activity -> {
            Context testableContext = mock(Context.class);
            RecyclerView rv = activity.requireViewById(R.id.list);
            GridLayoutManager gridLayoutManager = new GridLayoutManager(testableContext, mRowNo,
                    GridLayoutManager.HORIZONTAL, false);
            rv.setLayoutManager(gridLayoutManager);
            rv.setAdapter(adapter);
        });
        // Check if first item on the first page is displayed
        onView(withText(getItemText(0, 0))).check(matches(isCompletelyDisplayed()));

        mActivityRule.getScenario().onActivity(activity -> {
            Context testableContext = (Context) spy(activity);
            RecyclerView rv = activity.requireViewById(R.id.list);
            mAppGridPageSnapCallback = mock(AppGridPageSnapper.AppGridPageSnapCallback.class);
            mPageSnapper = new AppGridPageSnapper(testableContext, mAppGridPageSnapCallback);
            mPageSnapper.attachToRecyclerView(rv);
        });
        // Check if first item on the first page is displayed
        onView(withText(getItemText(0, 0))).check(matches(isCompletelyDisplayed()));
        // Check if last item on the first page is displayed
        onView(withText(getItemText(mItemPerPage - 1, 0))).check(matches(isCompletelyDisplayed()));
        RecyclerViewIdlingResource.register(mActivityRule.getScenario());

        mActivityRule.getScenario().onActivity(activity -> {
            RecyclerView rv = activity.requireViewById(R.id.list);
            rv.smoothScrollBy(rv.getWidth() / 2, 0);
        });

        // Check if first item on the second page is displayed
        onView(withText(getItemText(0, 1))).check(matches(isDisplayed()));
        // Check if last item on the second page is displayed
        onView(withText(getItemText(mItemPerPage - 1, 1))).check(matches(isDisplayed()));
    }

    @Test
    public void testSwipeRightAndStayOnCurrentPage() {
        mActivityRule.getScenario().onActivity(
                activity -> activity.setContentView(R.xml.empty_test_activity));
        onView(withId(R.id.list)).check(matches(isDisplayed()));
        TestAdapter adapter = new TestAdapter(100);

        mActivityRule.getScenario().onActivity(activity -> {
            Context testableContext = mock(Context.class);
            RecyclerView rv = activity.requireViewById(R.id.list);
            GridLayoutManager gridLayoutManager = new GridLayoutManager(testableContext,
                    mRowNo,
                    GridLayoutManager.HORIZONTAL,
                    false);
            rv.setLayoutManager(gridLayoutManager);
            rv.setAdapter(adapter);
        });

        onView(withText(getItemText(0, 0))).check(matches(isCompletelyDisplayed()));

        mActivityRule.getScenario().onActivity(activity -> {
            Context testableContext = (Context) spy(activity);
            RecyclerView rv = activity.requireViewById(R.id.list);
            mAppGridPageSnapCallback = mock(AppGridPageSnapper.AppGridPageSnapCallback.class);
            mPageSnapper = new AppGridPageSnapper(testableContext, mAppGridPageSnapCallback);
            mPageSnapper.attachToRecyclerView(rv);
        });

        // Check if first item on the first page is displayed
        onView(withText(getItemText(0, 0))).check(matches(isCompletelyDisplayed()));
        // Check if last item on the first page is displayed
        onView(withText(getItemText(mItemPerPage - 1, 0))).check(matches(isCompletelyDisplayed()));
        RecyclerViewIdlingResource.register(mActivityRule.getScenario());

        mActivityRule.getScenario().onActivity(activity -> {
            RecyclerView rv = activity.requireViewById(R.id.list);
            rv.smoothScrollBy(rv.getWidth() / 10, 0);
        });
        // Check if last item on the first page is displayed
        onView(withText(getItemText(mItemPerPage - 1, 0))).check(matches(isCompletelyDisplayed()));
        // Check if 5th item on the 5th page is displayed
        onView(withText(getItemText(4, 0))).check(matches(isCompletelyDisplayed()));
        // Check if first item on the first page is displayed
        onView(withText(getItemText(0, 0))).check(matches(isCompletelyDisplayed()));
    }

    @Test
    public void testScrollToPrevPage() {
        mActivityRule.getScenario().onActivity(
                activity -> activity.setContentView(R.xml.empty_test_activity));
        onView(withId(R.id.list)).check(matches(isDisplayed()));
        TestAdapter adapter = new TestAdapter(100);

        mActivityRule.getScenario().onActivity(activity -> {
            Context testableContext = mock(Context.class);
            RecyclerView rv = activity.requireViewById(R.id.list);
            GridLayoutManager gridLayoutManager = new GridLayoutManager(testableContext,
                    mRowNo,
                    GridLayoutManager.HORIZONTAL,
                    false);
            rv.setLayoutManager(gridLayoutManager);
            rv.setAdapter(adapter);
        });

        // Check if first item on the first page is displayed
        onView(withText(getItemText(0, 0))).check(matches(isCompletelyDisplayed()));
        mActivityRule.getScenario().onActivity(activity -> {
            Context testableContext = (Context) spy(activity);
            RecyclerView rv = activity.requireViewById(R.id.list);
            mAppGridPageSnapCallback = mock(AppGridPageSnapper.AppGridPageSnapCallback.class);
            mPageSnapper = new AppGridPageSnapper(testableContext, mAppGridPageSnapCallback);
            mPageSnapper.attachToRecyclerView(rv);
        });

        // Check if first item on the first page is displayed
        onView(withText(getItemText(0, 0))).check(matches(isCompletelyDisplayed()));
        // Check if last item on the first page is displayed
        onView(withText(getItemText(mItemPerPage - 1, 0))).check(matches(isCompletelyDisplayed()));
        RecyclerViewIdlingResource.register(mActivityRule.getScenario());

        mActivityRule.getScenario().onActivity(activity -> {
            RecyclerView rv = activity.requireViewById(R.id.list);
            rv.smoothScrollBy(rv.getWidth() / 2, 0);
        });
        // Check if first item on the second page is displayed
        onView(withText(getItemText(0, 1))).check(matches(isCompletelyDisplayed()));
        // Check if last item on the second page is displayed
        onView(withText(getItemText(mItemPerPage - 1, 1))).check(matches(isCompletelyDisplayed()));

        mActivityRule.getScenario().onActivity(activity -> {
            RecyclerView rv = activity.requireViewById(R.id.list);
            rv.smoothScrollBy(rv.getWidth() / 2, 0);
        });
        // Check if first item on the third page is displayed
        onView(withText(getItemText(0, 2))).check(matches(isCompletelyDisplayed()));
        // Check if 5th item on the third page is displayed
        onView(withText(getItemText(4, 2))).check(matches(isCompletelyDisplayed()));
        // Check if last item on the third page is displayed
        onView(withText(getItemText(mItemPerPage - 1, 2))).check(matches(isCompletelyDisplayed()));

        mActivityRule.getScenario().onActivity(activity -> {
            RecyclerView rv = activity.requireViewById(R.id.list);
            rv.smoothScrollBy(-rv.getWidth() / 2, 0);
        });
        // Check if last item on the second page is displayed
        onView(withText(getItemText(mItemPerPage - 1, 1))).check(matches(isCompletelyDisplayed()));
        // Check if first item on the second page is displayed
        onView(withText(getItemText(0, 1))).check(matches(isCompletelyDisplayed()));
    }

    @Test
    public void testScrollLeftAndStayOnCurrentPage() {
        mActivityRule.getScenario().onActivity(
                activity -> activity.setContentView(R.xml.empty_test_activity));
        onView(withId(R.id.list)).check(matches(isDisplayed()));
        TestAdapter adapter = new TestAdapter(100);

        mActivityRule.getScenario().onActivity(activity -> {
            Context testableContext = mock(Context.class);
            RecyclerView rv = activity.requireViewById(R.id.list);
            GridLayoutManager gridLayoutManager = new GridLayoutManager(testableContext, mRowNo,
                    GridLayoutManager.HORIZONTAL, false);
            rv.setLayoutManager(gridLayoutManager);
            rv.setAdapter(adapter);
        });

        // Check if first item on the first page is displayed
        onView(withText(getItemText(0, 0))).check(matches(isCompletelyDisplayed()));
        mActivityRule.getScenario().onActivity(activity -> {
            Context testableContext = (Context) spy(activity);
            RecyclerView rv = activity.requireViewById(R.id.list);
            mAppGridPageSnapCallback = mock(AppGridPageSnapper.AppGridPageSnapCallback.class);
            mPageSnapper = new AppGridPageSnapper(testableContext, mAppGridPageSnapCallback);
            mPageSnapper.attachToRecyclerView(rv);
        });

        // Check if first item on the first page is displayed
        onView(withText(getItemText(0, 0))).check(matches(isCompletelyDisplayed()));
        // Check if last item on the first page is displayed
        onView(withText(getItemText(mItemPerPage - 1, 0))).check(matches(isCompletelyDisplayed()));
        RecyclerViewIdlingResource.register(mActivityRule.getScenario());

        mActivityRule.getScenario().onActivity(activity -> {
            RecyclerView rv = activity.requireViewById(R.id.list);
            rv.smoothScrollBy(rv.getWidth() / 2, 0);
        });
        // Check if first item on the second page is displayed
        onView(withText(getItemText(0, 1))).check(matches(isCompletelyDisplayed()));
        // Check if fifth item on the second page is displayed
        onView(withText(getItemText(5, 1))).check(matches(isCompletelyDisplayed()));
        // Check if last item on the second page is displayed
        onView(withText(getItemText(mItemPerPage - 1, 1))).check(matches(isCompletelyDisplayed()));

        mActivityRule.getScenario().onActivity(activity -> {
            RecyclerView rv = activity.requireViewById(R.id.list);
            rv.smoothScrollBy(rv.getWidth() / 10, 0);
        });
        // Check if last item on the second page is displayed
        onView(withText(getItemText(mItemPerPage - 1, 1))).check(matches(isCompletelyDisplayed()));
        // Check if first item on the second page is displayed
        onView(withText(getItemText(0, 1))).check(matches(isCompletelyDisplayed()));
    }

    private static class TestAdapter extends RecyclerView.Adapter<TestViewHolder> {

        protected final List<String> mData;
        protected final int mCols;
        protected final int mRows;


        TestAdapter(int itemCount) {
            mCols = 5;
            mRows = 3;
            mData = new ArrayList<>(itemCount);

            for (int i = 0; i < itemCount; i++) {
                mData.add("test " + i);
            }
        }

        @Override
        public TestViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup viewGroup, int i) {
            LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
            View view = inflater.inflate(R.xml.test_list_item, viewGroup, /* attachToRoot= */
                    false);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    viewGroup.getWidth() / mCols, viewGroup.getHeight() / mRows);
            view.setLayoutParams(layoutParams);
            return new TestViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull @NotNull TestViewHolder testViewHolder, int i) {
            testViewHolder.bind(mData.get(i));
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }
    }

    private static class TestViewHolder extends RecyclerView.ViewHolder {
        private final TextView mTextView;

        TestViewHolder(View view) {
            super(view);
            mTextView = itemView.findViewById(R.id.text);
        }

        void bind(String text) {
            mTextView.setText(text);
        }
    }

    public static class RecyclerViewIdlingResource implements IdlingResource, AutoCloseable {
        private final RecyclerView mRecyclerView;
        private ResourceCallback mResourceCallback;

        public RecyclerViewIdlingResource(RecyclerView rv) {
            mRecyclerView = rv;
            rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull @NotNull RecyclerView recyclerView,
                        int newState) {
                    if ((newState == RecyclerView.SCROLL_STATE_IDLE
                            || newState == RecyclerView.SCROLL_STATE_DRAGGING)
                            && mResourceCallback != null) {
                        mResourceCallback.onTransitionToIdle();
                    }
                }

                @Override
                public void onScrolled(@NonNull @NotNull RecyclerView recyclerView, int dx,
                        int dy) {
                }
            });
        }

        @Override
        public String getName() {
            return RecyclerViewIdlingResource.class.getSimpleName();
        }

        @Override
        public boolean isIdleNow() {
            return mRecyclerView.getScrollState() == mRecyclerView.SCROLL_STATE_IDLE;
        }

        @Override
        public void registerIdleTransitionCallback(ResourceCallback callback) {
            mResourceCallback = callback;
        }

        @Override
        public void close() throws Exception {
            IdlingRegistry.getInstance().unregister(this);
        }

        public static RecyclerViewIdlingResource register(ActivityScenario<TestActivity> scenario) {
            final RecyclerViewIdlingResource[] idlingResources = new RecyclerViewIdlingResource[1];
            scenario.onActivity((activity -> {
                idlingResources[0] = new RecyclerViewIdlingResource(
                        activity.findViewById(R.id.list));
            }));
            IdlingRegistry.getInstance().register(idlingResources[0]);
            return idlingResources[0];
        }
    }

    private String getItemText(int order, int page) {
        int itemNo = order + page * mItemPerPage;
        return "test " + itemNo;
    }
}