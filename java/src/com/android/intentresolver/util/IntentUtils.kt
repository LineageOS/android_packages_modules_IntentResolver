/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("IntentUtils")

package com.android.intentresolver.util

import android.app.AppGlobals
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.IPackageManager
import android.os.UserHandle
import com.android.intentresolver.IntentForwarderActivity

@JvmOverloads
fun prepareCrossProfileIntents(
    contentResolver: ContentResolver,
    targetIntent: Intent,
    intents: List<Intent>,
    source: UserHandle,
    target: UserHandle,
    packageManagerProvider: () -> IPackageManager = { AppGlobals.getPackageManager() },
    isCrossProfileIntent: (Intent, Int, Int, IPackageManager, ContentResolver) -> Boolean =
        ::isCrossProfileIntent,
): List<Intent> {
    val packageManager = packageManagerProvider()
    if (
        !isCrossProfileIntent(
            targetIntent,
            source.identifier,
            target.identifier,
            packageManager,
            contentResolver,
        )
    ) {
        // If the target intent can't be forwarded, then we can't forward any intents The empty
        // collections will be handled by the NoCrossProfileEmptyStateProvider.
        return emptyList()
    }

    return sanitizePayloadIntents(intents) { intent ->
        // The first item in the list is the target intent (but it is not necessarily the same
        // object as the targetIntent passed in to this method). See the
        // ChooserRequest.payloadIntents.
        intents[0] == intent ||
            isCrossProfileIntent.invoke(
                intent,
                source.identifier,
                target.identifier,
                packageManager,
                contentResolver,
            )
    }
}

fun sanitizePayloadIntents(
    intents: List<Intent>,
    predicate: (Intent) -> Boolean = { true },
): List<Intent> =
    buildList(capacity = intents.size) {
        for (intent in intents) {
            if (predicate(intent)) {
                add(
                    Intent(intent).also { sanitized ->
                        sanitized.setPackage(null)
                        sanitized.setComponent(null)
                        sanitized.selector?.let {
                            sanitized.setSelector(
                                Intent(it).apply {
                                    setPackage(null)
                                    setComponent(null)
                                }
                            )
                        }
                    }
                )
            }
        }
    }

// TODO: move `IntentForwarderActivity#canForward()` and related methods here.
fun isCrossProfileIntent(
    intent: Intent,
    sourceUserId: Int,
    targetUserId: Int,
    packageManager: IPackageManager,
    contentResolver: ContentResolver,
) =
    IntentForwarderActivity.canForward(
        intent,
        sourceUserId,
        targetUserId,
        packageManager,
        intent.resolveTypeIfNeeded(contentResolver),
    ) != null
