/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.app.Activity.RESULT_OK;

import android.app.ActivityOptions;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.ArraySet;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.android.car.carlauncher.homescreen.HomeCardModule;
import com.android.car.carlauncher.homescreen.audio.IntentHandler;
import com.android.car.carlauncher.homescreen.audio.MediaLaunchHandler;
import com.android.car.carlauncher.homescreen.audio.dialer.InCallIntentRouter;
import com.android.car.carlauncher.homescreen.audio.media.MediaLaunchRouter;
import com.android.car.carlauncher.widgets.CarAppWidgetHost;
import com.android.car.carlauncher.widgets.CarAppWidgetHostView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Launcher activity that shows the control bar fragment & a list of app widgets
 */
public class WidgetHostActivity extends AppCompatActivity {

    private static final String TAG = "WidgetHostActivity";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final int REQUEST_BIND = 100;
    private static final int REQUEST_CONFIGURE = REQUEST_BIND + 1;
    private static final int RESULT_SUCCESS = RESULT_OK;
    private static final int RESULT_NEEDS_BIND = RESULT_SUCCESS + 1;
    private static final int RESULT_NEEDS_CONFIGURE = RESULT_NEEDS_BIND + 1;
    private final IntentHandler mIntentHandler = intent -> {
        if (intent != null) {
            ActivityOptions options = ActivityOptions.makeBasic();
            startActivity(intent, options.toBundle());
        }
    };
    // Used instead of IntentHandler because media apps may provide a PendingIntent instead
    private final MediaLaunchHandler mMediaMediaLaunchHandler = mediaSource -> {
        if (DEBUG) {
            Log.d(TAG, "Launching media source " + mediaSource);
        }
        mediaSource.launchActivity(WidgetHostActivity.this, ActivityOptions.makeBasic());
    };
    private final Map<Integer, AppWidgetProviderInfo> mAppWidgetInfoMap = new HashMap<>();
    private final BroadcastReceiver mOverlayChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
                if (DEBUG) {
                    Log.d(TAG, "Configuration changed, recreating activity");
                }
                recreate();
            }
        }
    };
    private int mWidgetHeight;
    private int mWidgetMediaCardHeight;
    private AppWidgetManager mAppWidgetManager;
    private CarAppWidgetHost mAppWidgetHost;
    private LinearLayout mWidgetContainer;
    private ViewGroup mCardContainer;
    private Set<HomeCardModule> mHomeCardModules;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getTheme().applyStyle(R.style.CarLauncherActivityThemeOverlay, true);

        setContentView(R.layout.widget_host_activity);

        initializeCards();

        MediaLaunchRouter.getInstance().registerMediaLaunchHandler(mMediaMediaLaunchHandler);
        InCallIntentRouter.getInstance().registerInCallIntentHandler(mIntentHandler);

        mAppWidgetManager = AppWidgetManager.getInstance(this);
        mAppWidgetHost = new CarAppWidgetHost(this,
                getResources().getInteger(R.integer.config_appwidget_host_id));
        mWidgetContainer = findViewById(R.id.widget_container);
        mCardContainer = findViewById(R.id.card_container);

        mWidgetHeight = (int) getResources().getDimension(R.dimen.widget_height);
        mWidgetMediaCardHeight = (int) getResources().getDimension(
                R.dimen.widget_media_card_height);

        mCardContainer.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mCardContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        if (Flags.appWidgetHost()) {
                            loadAndDisplayWidgets();
                        } else {
                            mCardContainer.getLayoutParams().height =
                                    ViewGroup.LayoutParams.MATCH_PARENT;
                        }
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAppWidgetHost.startListening();
        registerReceiver(mOverlayChangeReceiver,
                new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));
    }

    @Override
    protected void onStop() {
        super.onStop();
        mAppWidgetHost.stopListening();
        unregisterReceiver(mOverlayChangeReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAppWidgetInfoMap.forEach((id, info) -> mAppWidgetHost.deleteAppWidgetId(id));
        mAppWidgetInfoMap.clear();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_BIND && resultCode == RESULT_OK) {
            int widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (widgetId == -1) {
                Log.e(TAG, "Unable to find widget id after bind");
                return;
            }

            AppWidgetProviderInfo appWidgetInfo = mAppWidgetInfoMap.get(widgetId);
            if (appWidgetInfo == null) {
                Log.w(TAG, "REQUEST_BIND no AppWidgetProviderInfo");
                return;
            }
            if (appWidgetInfo.configure == null) {
                createHostView(widgetId, appWidgetInfo);
            } else {
                Bundle options = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                        )
                        .toBundle();
                mAppWidgetHost.startAppWidgetConfigureActivityForResult(this, widgetId,
                        /* intentFlags= */ 0,
                        REQUEST_CONFIGURE,
                        options);
            }
        } else if (requestCode == REQUEST_CONFIGURE && resultCode == RESULT_OK) {
            int widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (widgetId == -1) {
                Log.e(TAG, "Unable to find widget id after configure");
                return;
            }

            AppWidgetProviderInfo appWidgetInfo = mAppWidgetInfoMap.get(widgetId);
            if (appWidgetInfo == null) {
                return;
            }

            createHostView(widgetId, appWidgetInfo);
        }
    }

    private void initializeCards() {
        if (mHomeCardModules == null) {
            mHomeCardModules = new ArraySet<>();
            for (String providerClassName : getResources().getStringArray(
                    R.array.config_homeCardModuleClasses)) {
                try {
                    long reflectionStartTime = System.currentTimeMillis();
                    HomeCardModule cardModule = (HomeCardModule) Class.forName(
                            providerClassName).newInstance();
                    if (cardModule.getCardResId() == R.id.top_card) {
                        findViewById(R.id.top_card).setVisibility(View.GONE);
                    }
                    cardModule.setViewModelProvider(new ViewModelProvider(/* owner= */this));
                    mHomeCardModules.add(cardModule);
                    if (DEBUG) {
                        long reflectionTime = System.currentTimeMillis() - reflectionStartTime;
                        Log.d(TAG, "Initialization of HomeCardModule class " + providerClassName
                                + " took " + reflectionTime + " ms");
                    }
                } catch (IllegalAccessException | InstantiationException
                         | ClassNotFoundException e) {
                    Log.w(TAG, "Unable to create HomeCardProvider class " + providerClassName, e);
                }
            }
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        for (HomeCardModule cardModule : mHomeCardModules) {
            transaction.replace(cardModule.getCardResId(), cardModule.getCardView().getFragment());
        }
        transaction.commitNow();
    }

    private void loadAndDisplayWidgets() {
        List<AppWidgetProviderInfo> providers = mAppWidgetManager.getInstalledProviders();
        Map<ComponentName, AppWidgetProviderInfo> providerMap = new HashMap<>();
        for (AppWidgetProviderInfo provider : providers) {
            providerMap.put(provider.provider, provider);
        }

        boolean widgetAdded = false;
        for (String componentString : getResources().getStringArray(
                R.array.config_initialAppWidgets)) {
            ComponentName componentName = ComponentName.unflattenFromString(componentString);
            AppWidgetProviderInfo info = providerMap.get(componentName);
            if (info == null) {
                Log.e(TAG, "Preconfigured AppWidget not found: " + componentName);
                continue;
            }

            int appWidgetId = -1;
            for (Map.Entry<Integer, AppWidgetProviderInfo> entry : mAppWidgetInfoMap.entrySet()) {
                if (info.equals(entry.getValue())) {
                    appWidgetId = entry.getKey();
                    break;
                }
            }
            if (appWidgetId == -1) {
                appWidgetId = mAppWidgetHost.allocateAppWidgetId();
            }

            int result = addWidget(appWidgetId, info);

            switch (result) {
                case RESULT_SUCCESS -> {
                    widgetAdded = true;
                }
                case RESULT_NEEDS_BIND -> {
                    Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE,
                            info.getProfile());
                    startActivityForResult(intent, REQUEST_BIND);
                }
                case RESULT_NEEDS_CONFIGURE -> {
                    Bundle options = ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(
                                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                            )
                            .toBundle();
                    mAppWidgetHost.startAppWidgetConfigureActivityForResult(this, appWidgetId,
                            /* intentFlags= */ 0, REQUEST_CONFIGURE,
                            options);
                }
            }
        }

        if (!widgetAdded) {
            mCardContainer.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        mWidgetContainer.setVisibility(widgetAdded ? View.VISIBLE : View.GONE);
    }

    private int addWidget(int appWidgetId, AppWidgetProviderInfo info) {
        mAppWidgetInfoMap.put(appWidgetId, info);

        if (mAppWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)) {
            if (info.configure != null) {
                return RESULT_NEEDS_CONFIGURE;
            }

            createHostView(appWidgetId, info);

            if (DEBUG) {
                Log.d(TAG, "Added widget with ID: " + appWidgetId + " from provider: "
                        + info.provider);
            }
            return RESULT_SUCCESS;
        } else {
            if (DEBUG) {
                Log.d(TAG, "Widget bind not allowed; with ID: " + appWidgetId + " from provider: "
                        + info.provider);
            }
            return RESULT_NEEDS_BIND;
        }
    }

    private void createHostView(int id, AppWidgetProviderInfo info) {
        CarAppWidgetHostView hostView =
                (CarAppWidgetHostView) mAppWidgetHost.createView(this, id, info);
        hostView.bind(info, mCardContainer.getWidth(), mWidgetHeight);
        mWidgetContainer.addView(hostView);
        mCardContainer.getLayoutParams().height = mWidgetMediaCardHeight;
        mWidgetContainer.setVisibility(View.VISIBLE);
    }
}
