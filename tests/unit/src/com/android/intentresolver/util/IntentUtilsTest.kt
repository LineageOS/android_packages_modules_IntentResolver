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

package com.android.intentresolver.util

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_VIEW
import android.content.pm.IPackageManager
import android.net.Uri
import android.os.UserHandle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.mock

class IntentUtilsTest {
    private val mockContentResolver = mock<ContentResolver>()
    private val mockPackageManager = mock<IPackageManager>()

    private val sourceUser = UserHandle.of(0)
    private val targetUser = UserHandle.of(10)

    @Test
    fun test_sanitizePayloadIntents_defaultPredicate() {
        val intents =
            listOf(
                Intent(ACTION_SEND).apply { setPackage("org.test.example") },
                Intent(ACTION_SEND).apply {
                    component = ComponentName.unflattenFromString("org.test.example/.TestActivity")
                },
                Intent(ACTION_SEND).apply {
                    selector = Intent(ACTION_SEND).apply { setPackage("org.test.example") }
                },
                Intent(ACTION_SEND).apply {
                    selector =
                        Intent(ACTION_SEND).apply {
                            component =
                                ComponentName.unflattenFromString("org.test.example/.TestActivity")
                        }
                },
            )

        val sanitized = sanitizePayloadIntents(intents)

        assertThat(sanitized).hasSize(intents.size)
        for (i in sanitized) {
            assertThat(i.getPackage()).isNull()
            assertThat(i.component).isNull()
            i.selector?.let {
                assertThat(it.getPackage()).isNull()
                assertThat(it.component).isNull()
            }
        }
    }

    @Test
    fun test_sanitizePayloadIntents_emptyList() {
        val sanitized = sanitizePayloadIntents(emptyList())
        assertThat(sanitized).isEmpty()
    }

    @Test
    fun test_sanitizePayloadIntents_withPredicate() {
        val intentToKeep = Intent(ACTION_SEND).apply { type = "text/plain" }
        val intentToFilter = Intent(ACTION_VIEW).apply { data = Uri.parse("http://example.com") }
        val intents = listOf(intentToKeep, intentToFilter)

        val sanitized = sanitizePayloadIntents(intents) { it.action == ACTION_SEND }

        assertThat(sanitized).hasSize(1)
        assertThat(sanitized[0].action).isEqualTo(ACTION_SEND)
    }

    @Test
    fun prepareCrossProfileIntents_targetIntentCannotBeForwarded_returnsEmptyList() {
        val targetIntent = Intent(ACTION_SEND).apply { type = "text/plain" }
        val payloadIntents = listOf(targetIntent)

        val result =
            prepareCrossProfileIntents(
                mockContentResolver,
                targetIntent,
                payloadIntents,
                sourceUser,
                targetUser,
                { mockPackageManager },
                { _, _, _, _, _ -> false },
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun prepareCrossProfileIntents_somePayloadsCannotBeForwarded_returnsFilteredList() {
        val targetIntent = Intent(ACTION_SEND).apply { type = "text/plain" }
        val forwardablePayload = Intent(ACTION_SEND).apply { type = "image/png" }
        val nonForwardablePayload = Intent(ACTION_VIEW)
        val intents = listOf(targetIntent, forwardablePayload, nonForwardablePayload)

        val result =
            prepareCrossProfileIntents(
                mockContentResolver,
                targetIntent,
                intents,
                sourceUser,
                targetUser,
                { mockPackageManager },
                { intent, _, _, _, _ ->
                    when (intent) {
                        targetIntent -> true
                        forwardablePayload -> true
                        else -> false
                    }
                },
            )

        assertThat(result).hasSize(2)
        assertThat(result[0].action).isEqualTo(ACTION_SEND)
        assertThat(result[0].type).isEqualTo("text/plain")
        assertThat(result[1].action).isEqualTo(ACTION_SEND)
        assertThat(result[1].type).isEqualTo("image/png")
    }

    @Test
    fun prepareCrossProfileIntents_allPayloadsCanBeForwarded_returnsSanitizedList() {
        val targetIntent =
            Intent(ACTION_SEND).apply {
                type = "text/plain"
                setPackage("foo")
            }
        val payloadIntent =
            Intent(ACTION_SEND).apply {
                type = "image/png"
                component = ComponentName("bar", "baz")
            }
        val intents = listOf(targetIntent, payloadIntent)

        val result =
            prepareCrossProfileIntents(
                mockContentResolver,
                targetIntent,
                intents,
                sourceUser,
                targetUser,
                { mockPackageManager },
                { _, _, _, _, _ -> true },
            )

        assertThat(result).hasSize(2)
        result.forEach {
            assertThat(it.getPackage()).isNull()
            assertThat(it.component).isNull()
        }
    }

    @Test
    fun prepareCrossProfileIntents_emptyIntentList_returnsEmptyList() {
        val targetIntent = Intent(ACTION_SEND).apply { type = "text/plain" }

        val result =
            prepareCrossProfileIntents(
                mockContentResolver,
                targetIntent,
                emptyList(),
                sourceUser,
                targetUser,
                { mockPackageManager },
                { _, _, _, _, _ -> true },
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun prepareCrossProfileIntents_usesPackageManagerProvider() {
        val targetIntent = Intent(ACTION_SEND).apply { type = "text/plain" }
        val intents = listOf(targetIntent)
        var providerCalled = false
        val packageManagerProvider = {
            providerCalled = true
            mockPackageManager
        }

        prepareCrossProfileIntents(
            mockContentResolver,
            targetIntent,
            intents,
            sourceUser,
            targetUser,
            packageManagerProvider,
            { _, _, _, _, _ -> true },
        )

        assertThat(providerCalled).isTrue()
    }
}
