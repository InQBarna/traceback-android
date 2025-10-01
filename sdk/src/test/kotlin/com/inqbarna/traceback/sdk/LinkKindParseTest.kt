/*
 * MIT License
 *
 * Copyright (c) 2025 inqbarna
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.inqbarna.traceback.sdk

import androidx.core.net.toUri
import com.google.common.truth.Expect
import com.inqbarna.traceback.sdk.impl.LinkKind
import io.ktor.http.Url
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 3/11/25
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class LinkKindParseTest(val linkText: String, val expectedKind: LinkKind) {

    @get:Rule
    val expect = Expect.create()

    @Test
    fun `link kind process works as expected`() {
        val kind = LinkKind.fromUri(linkText.toUri(), "traceback.com")
        expect.that(kind).isEqualTo(expectedKind)
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: linkText={0}, expectedKind={1}")
        @JvmStatic
        fun parameters(): List<Array<Any>> {
            return listOf(
                arrayOf(
                    "https://traceback.com/campaign",
                    LinkKind.CampaignLink(
                        original = Url("https://traceback.com/campaign"),
                        campaignId = "campaign",
                        deeplink = null
                    )
                ),
                arrayOf(
                    "https://traceback.com/campaign/second",
                    LinkKind.CampaignLink(
                        original = Url("https://traceback.com/campaign/second"),
                        campaignId = "campaign/second",
                        deeplink = null
                    )
                ),
                arrayOf(
                    "https://traceback.com/",
                    LinkKind.Unknown(
                        original = Url("https://traceback.com/")
                    )
                ),
                arrayOf(
                    "https://traceback.com/campaign/",
                    LinkKind.CampaignLink(
                        original = Url("https://traceback.com/campaign/"),
                        campaignId = "campaign",
                        deeplink = null
                    )
                ),
                arrayOf(
                    "https://traceback.com/campaign/?link=https%3A%2F%2Fanother.com%2Fdeeplink%26param%3D1%26other%3D2",
                    LinkKind.CampaignLink(
                        original = Url("https://traceback.com/campaign/?link=https%3A%2F%2Fanother.com%2Fdeeplink%26param%3D1%26other%3D2"),
                        campaignId = "campaign",
                        deeplink = Url("https://another.com/deeplink&param=1&other=2")
                    )
                ),
                arrayOf(
                    "https://traceback.com/?link=https%3A%2F%2Fanother.com%2Fdeeplink%3Fparam%3D1%26other%3D2",
                    LinkKind.RegularDeeplink(
                        original = Url("https://traceback.com/?link=https%3A%2F%2Fanother.com%2Fdeeplink%3Fparam%3D1%26other%3D2"),
                        deeplink = Url("https://another.com/deeplink?param=1&other=2")
                    )
                ),
                arrayOf(
                    "https://otherdomain.com/campaign",
                    LinkKind.Unknown(
                        original = Url("https://otherdomain.com/campaign")
                    )
                )
            )
        }
    }
}
