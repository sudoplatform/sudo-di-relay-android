package com.sudoplatform.sudodirelay

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.HttpURLConnection

object CommonData {
    private val request = Request.Builder()
        .get()
        .url("http://www.smh.com.au")
        .build()
    private val responseBody = "{}".toResponseBody("application/json; charset=utf-8".toMediaType())
    val forbiddenHTTPResponse = okhttp3.Response.Builder()
        .protocol(Protocol.HTTP_1_1)
        .code(HttpURLConnection.HTTP_FORBIDDEN)
        .request(request)
        .message("Forbidden")
        .body(responseBody)
        .build()
}
