package com.sudoplatform.sudodirelay.graphql

import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import org.mockito.ArgumentMatchers.any
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

fun KStubbing<ApiCategory>.onMutate(
    operationDocument: String,
    responseJson: String?,
    errors: List<GraphQLResponse.Error>? = null,
): KStubbing<ApiCategory> {
    on {
        mutate<String>(
            argThat { this.query == operationDocument },
            any(),
            any(),
        )
    } doAnswer {
        @Suppress("UNCHECKED_CAST")
        (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
            GraphQLResponse(responseJson, errors),
        )
        mock<GraphQLOperation<String>>()
    }
    return this
}

fun KStubbing<ApiCategory>.onQuery(
    operationDocument: String,
    responseJson: String?,
    errors: List<GraphQLResponse.Error>? = null,
): KStubbing<ApiCategory> {
    on {
        query<String>(
            argThat { this.query == operationDocument },
            any(),
            any(),
        )
    } doAnswer {
        @Suppress("UNCHECKED_CAST")
        (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
            GraphQLResponse(responseJson, errors),
        )
        mock<GraphQLOperation<String>>()
    }
    return this
}
