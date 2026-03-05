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

fun prepareCrossProfileIntents(
    contentResolver: ContentResolver,
    targetIntent: Intent,
    intents: List<Intent?>,
    source: UserHandle,
    target: UserHandle
): List<Intent?> {
    val packageManager = AppGlobals.getPackageManager()
    if (!isCrossProfileIntent(
            targetIntent,
            source.getIdentifier(),
            target.getIdentifier(),
            packageManager,
            contentResolver)) {
        // If the target intent can't be forwarded, then we can't forward any intents The empty
        // collections will be handled by the NoCrossProfileEmptyStateProvider.
        return emptyList()
    }

    return sanitizePayloadIntents(intents) { intent ->
        // The first item in the list is the target intent (but it is not necessarily the same
        // object as the targetIntent passed in to this method). See the ResolverActivity.mIntents
        // initialization.
        intents[0] == intent ||
            isCrossProfileIntent(
                    intent,
                    source.getIdentifier(),
                    target.getIdentifier(),
                    packageManager,
                    contentResolver)
    }
}

fun sanitizePayloadIntents(
    intents: List<Intent?>,
    predicate: (Intent) -> Boolean = { true },
): List<Intent?> =
    buildList(capacity = intents.size) {
        for (intent in intents) {
            if (intent == null) {
                add(null)
                continue
            }
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

fun isCrossProfileIntent(
    intent: Intent,
    sourceUserId: Int,
    targetUserId: Int,
    packageManager: IPackageManager,
    contentResolver: ContentResolver,
) = IntentForwarderActivity.canForward(
        intent,
        sourceUserId,
        targetUserId,
        packageManager,
        intent.resolveTypeIfNeeded(contentResolver),
    ) != null
