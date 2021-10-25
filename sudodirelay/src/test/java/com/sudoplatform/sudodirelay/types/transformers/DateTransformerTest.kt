package com.sudoplatform.sudodirelay.types.transformers

import io.kotlintest.shouldBe
import org.junit.Test
import java.util.Date

class DateTransformerTest {

    @Test
    fun `normal gmt string converts to correct date`() {
        val dateStr = "Mon, 21 Jun 2021 19:11:50 GMT"

        dateStr.toDate() shouldBe Date(1624302710000)
    }

    @Test
    fun `gmt string with day and month swapped converts to correct date`() {
        val dateStr = "Mon, Jun 21 2021 19:11:50 GMT"

        dateStr.toDate() shouldBe Date(1624302710000)
    }

    @Test
    fun `date toString converts to correct GMT string`() {
        val date = Date(1624302710000)

        date.toUTCString() shouldBe "Mon, 21 Jun 2021 19:11:50 GMT"
    }
}
