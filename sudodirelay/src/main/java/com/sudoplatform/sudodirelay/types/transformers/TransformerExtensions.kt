/*
 * Copyright © 2021 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudodirelay.types.transformers

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Extensions used by several data transformers.
 *
 * @since 2021-06-30
 */
internal fun String.toDate(): Date {
    return Date(this)
}

internal fun Date.toUTCString(): String {
    val days = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    val cal = Calendar.getInstance()
    cal.time = this
    cal.timeZone = TimeZone.getTimeZone("GMT")
    val dayOfWeek = (cal.get(Calendar.DAY_OF_WEEK)) - Calendar.SUNDAY

    return days[dayOfWeek] + ", " + this.toGMTString()
}
