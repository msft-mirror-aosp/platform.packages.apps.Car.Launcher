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

package com.android.car.carlauncher.recents;

import static com.android.car.carlauncher.recents.CarRecentsActivity.OPEN_RECENT_TASK_ACTION;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Region;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.statusbar.phone.BarTransitions;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.wm.shell.recents.IRecentTasks;

import java.util.List;

public class CarQuickStepService extends Service {

    private static final String TAG = "CarQuickStepService";

    private RecentTasksProvider mRecentTasksProvider;
    private ActivityManager mActivityManager;
    private ComponentName mRecentsComponent;

    @Override
    public void onCreate() {
        mRecentTasksProvider = RecentTasksProvider.getInstance();
        mActivityManager = this.getSystemService(ActivityManager.class);
        mRecentsComponent = ComponentName.unflattenFromString(this.getString(
                com.android.internal.R.string.config_recentsComponentName));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new CarOverviewProxyBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mRecentTasksProvider.terminate();
        return false;
    }

    private boolean isRecentsActivityShown() {
        List<ComponentName> activeComponentNames = mActivityManager.getAppTasks().stream()
                .filter(appTask -> appTask.getTaskInfo().isVisible())
                .map(appTask -> appTask.getTaskInfo().topActivity).toList();
        for (ComponentName componentName : activeComponentNames) {
            if (componentName != null && mRecentsComponent.getClassName().equalsIgnoreCase(
                    componentName.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void toggleRecentsIntent(boolean closeRecents) {
        Intent intent = new Intent();
        intent.setComponent(mRecentsComponent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (closeRecents) {
            intent.setAction(OPEN_RECENT_TASK_ACTION);
        }
        startActivity(intent);
    }

    private class CarOverviewProxyBinder extends IOverviewProxy.Stub {
        @Override
        public void onActiveNavBarRegionChanges(Region activeRegion) {
            // no-op
        }

        @Override
        public void onInitialize(Bundle params) throws RemoteException {
            IRecentTasks recentTasks = IRecentTasks.Stub.asInterface(
                    params.getBinder(IRecentTasks.DESCRIPTOR));
            mRecentTasksProvider.init(getApplicationContext(), recentTasks);
        }

        /**
         * Sent when overview button is pressed to toggle show/hide of overview.
         */
        @Override
        public void onOverviewToggle() {
            toggleRecentsIntent(/* closeRecents= */ isRecentsActivityShown());
        }

        /**
         * Sent when overview is to be shown.
         */
        @Override
        public void onOverviewShown(boolean triggeredFromAltTab) {
            if (!isRecentsActivityShown()) {
                toggleRecentsIntent(/* closeRecents= */ false);
            }
        }

        /**
         * Sent when overview is to be hidden.
         */
        @Override
        public void onOverviewHidden(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
            if (isRecentsActivityShown()) {
                toggleRecentsIntent(/* closeRecents= */ true);
            }
        }

        @Override
        public void onAssistantAvailable(boolean available, boolean longPressHomeEnabled) {
            // no-op
        }

        @Override
        public void onAssistantVisibilityChanged(float visibility) {
            // no-op
        }

        @Override
        public void onAssistantOverrideInvoked(int invocationType) {
            // no-op
        }

        @Override
        public void onSystemUiStateChanged(@SystemUiStateFlags long stateFlags) {
            // no-op
        }

        @Override
        public void onRotationProposal(int rotation, boolean isValid) {
            // no-op
        }

        @Override
        public void disable(int displayId, int state1, int state2, boolean animate) {
            // no-op
        }

        @Override
        public void onSystemBarAttributesChanged(int displayId, int behavior) {
            // no-op
        }

        @Override
        public void onTransitionModeUpdated(int barMode, boolean checkBarModes) {
            // no-op
        }

        @Override
        public void onNavButtonsDarkIntensityChanged(float darkIntensity) {
            // no-op
        }

        @Override
        public void onNavigationBarLumaSamplingEnabled(int displayId, boolean enable) {
            // no-op
        }

        @Override
        public void enterStageSplitFromRunningApp(boolean leftOrTop) {
            // no-op
        }

        @Override
        public void onTaskbarToggled() {
            // no-op
        }

        @Override
        public void updateWallpaperVisibility(int displayId, boolean visible) {
            // no-op
        }

        @Override
        public void checkNavBarModes(int displayId) {
            // no-op
        }

        @Override
        public void finishBarAnimations(int displayId) {
            // no-op
        }

        @Override
        public void touchAutoDim(int displayid, boolean reset) {
            // no-op
        }

        @Override
        public void transitionTo(int displayId, @BarTransitions.TransitionMode int barMode,
                boolean animate) {
            // no-op
        }

        @Override
        public void appTransitionPending(boolean pending) {
            // no-op
        }

        @Override
        public void onUnbind(IRemoteCallback reply) {
            // no-op but immediately call the reply to unblock OveriewProxyService.
            try {
                reply.sendResult(null);
            } catch (RemoteException e) {
                Log.w(TAG, "onUnbind: Failed to reply to OverviewProxyService", e);
            }
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            // no-op
        }

        @Override
        public void onDisplayAddSystemDecorations(int displayId) {
            // no-op
        }
    }
}
