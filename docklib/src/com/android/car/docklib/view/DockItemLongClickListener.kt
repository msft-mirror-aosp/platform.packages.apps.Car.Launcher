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

package com.android.car.docklib.view

import android.content.res.Resources
import android.view.View
import androidx.annotation.OpenForTesting
import androidx.annotation.VisibleForTesting
import com.android.car.docklib.data.DockAppItem
import com.android.car.dockutil.shortcuts.PinShortcutItem
import com.android.car.ui.shortcutspopup.CarUiShortcutsPopup

/**
 * [View.OnLongClickListener] for handling long clicks on dock item.
 * It is responsible to create and show th popup window
 *
 * @param dockAppItem the [DockAppItem] to be used on long click.
 * @param pinItemClickDelegate called when item should be pinned at that position
 * @param unpinItemClickDelegate called when item should be unpinned at that position
 */
@OpenForTesting
open class DockItemLongClickListener(
    private var dockAppItem: DockAppItem,
    private val pinItemClickDelegate: Runnable,
    private val unpinItemClickDelegate: Runnable
) : View.OnLongClickListener {
    override fun onLongClick(view: View?): Boolean {
        if (view == null) return false

        createCarUiShortcutsPopupBuilder()
            .addShortcut(
                createPinShortcutItem(
                    view.context.resources,
                    isItemPinned = (dockAppItem.type == DockAppItem.Type.STATIC),
                    pinItemClickDelegate,
                    unpinItemClickDelegate
                )
            )
            .build(view.context, view)
            .show()
        return true
    }

    /**
     * Set the [DockAppItem] to be used on long click.
     */
    fun setDockAppItem(dockAppItem: DockAppItem) {
        this.dockAppItem = dockAppItem
    }

    /**
     * Need to be overridden in test.
     */
    @VisibleForTesting
    @OpenForTesting
    open fun createCarUiShortcutsPopupBuilder(): CarUiShortcutsPopup.Builder =
        CarUiShortcutsPopup.Builder()

    /**
     * Need to be overridden in test.
     */
    @VisibleForTesting
    fun createPinShortcutItem(
        resources: Resources,
        isItemPinned: Boolean,
        pinItemClickDelegate: Runnable,
        unpinItemClickDelegate: Runnable
    ): PinShortcutItem = PinShortcutItem(
        resources,
        isItemPinned,
        pinItemClickDelegate,
        unpinItemClickDelegate
    )
}