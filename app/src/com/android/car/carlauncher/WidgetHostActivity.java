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

import android.app.ActivityOptions;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
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
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.ArraySet;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.android.car.carlauncher.homescreen.HomeCardModule;
import com.android.car.carlauncher.homescreen.audio.IntentHandler;
import com.android.car.carlauncher.homescreen.audio.MediaLaunchHandler;
import com.android.car.carlauncher.homescreen.audio.dialer.InCallIntentRouter;
import com.android.car.carlauncher.homescreen.audio.media.MediaLaunchRouter;

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
    private final Set<Integer> mAppWidgetIds = new ArraySet<>();
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
    private AppWidgetManager mAppWidgetManager;
    private AppWidgetHost mAppWidgetHost;
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
        mAppWidgetHost = new AppWidgetHost(this,
                getResources().getInteger(R.integer.config_appwidget_host_id));
        mWidgetContainer = findViewById(R.id.widget_container);
        mCardContainer = findViewById(R.id.card_container);

        if (Flags.appWidgetHost()) {
            loadAndDisplayWidgets();
        } else {
            mCardContainer.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
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
        mAppWidgetIds.forEach(id -> mAppWidgetHost.deleteAppWidgetId(id));
        mAppWidgetIds.clear();
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

            int appWidgetId = mAppWidgetHost.allocateAppWidgetId();
            mAppWidgetIds.add(appWidgetId);

            if (mAppWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, componentName)) {
                AppWidgetHostView hostView = mAppWidgetHost.createView(this, appWidgetId, info);
                mWidgetContainer.addView(hostView);
                widgetAdded = true;

                if (DEBUG) {
                    Log.d(TAG, "Added widget with ID: " + appWidgetId + " from provider: "
                            + componentName);
                }
            } else {
                if (DEBUG) {
                    Log.d(TAG,
                            "Widget bind not allowed; with ID: " + appWidgetId + " from provider: "
                                    + componentName);
                }
            }
        }

        if (!widgetAdded) {
            mCardContainer.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        mWidgetContainer.setVisibility(widgetAdded ? View.VISIBLE : View.GONE);
    }
}
