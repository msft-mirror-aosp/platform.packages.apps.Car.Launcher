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

package com.android.car.docklib

interface DockInterface {
    /**
     * called when an app is statically pinned to the Dock
     */
    fun appPinned(componentName: String)

    /**
     * called when an app is launched
     */
    fun appLaunched(componentName: String)

    /**
     * called when an app should be removed from the Dock
     */
    fun appUnpinned(componentName: String)
}
