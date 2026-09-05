package com.przunk.kratkoza.data.ranking

import com.google.android.gms.common.api.CommonStatusCodes
import com.przunk.kratkoza.domain.ranking.SignInOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayGamesSignInDiagnosticsTest {

    @Test
    fun `a successful verification wins over an unauthenticated request response`() {
        val outcome = resolveSignInOutcome(
            request = snapshot(successful = true, authenticated = false),
            verification = snapshot(successful = true, authenticated = true),
        )

        assertTrue(outcome is SignInOutcome.Signed)
    }

    @Test
    fun `a failure retains both task states and the SDK exception`() {
        val outcome = resolveSignInOutcome(
            request = snapshot(
                successful = false,
                authenticated = null,
                exceptionType = "ApiException",
                statusCode = 10,
                statusName = "DEVELOPER_ERROR",
                message = "10: configuration rejected",
            ),
            verification = snapshot(successful = true, authenticated = false),
        ) as SignInOutcome.Failed

        assertEquals(
            "request{complete=true, success=false, canceled=false, authenticated=none, " +
                "exception=ApiException, status=10 DEVELOPER_ERROR, " +
                "message=10: configuration rejected}; " +
                "verification{complete=true, success=true, canceled=false, authenticated=false}",
            outcome.detail,
        )
    }

    @Test
    fun `an SDK cancellation remains visible in the dismissed outcome`() {
        val outcome = resolveSignInOutcome(
            request = snapshot(
                successful = false,
                authenticated = null,
                statusCode = CommonStatusCodes.CANCELED,
                statusName = "CANCELED",
            ),
            verification = snapshot(successful = true, authenticated = false),
        ) as SignInOutcome.Dismissed

        assertTrue(outcome.detail.contains("status=${CommonStatusCodes.CANCELED} CANCELED"))
        assertTrue(outcome.detail.contains("verification"))
    }

    private fun snapshot(
        successful: Boolean,
        authenticated: Boolean?,
        exceptionType: String? = null,
        statusCode: Int? = null,
        statusName: String? = null,
        message: String? = null,
    ) = SignInTaskSnapshot(
        complete = true,
        successful = successful,
        canceled = false,
        authenticated = authenticated,
        exceptionType = exceptionType,
        statusCode = statusCode,
        statusName = statusName,
        message = message,
    )
}
