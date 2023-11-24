@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ActivePrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentencingAdjustmentsApiExtension.Companion.sentencingAdjustmentsApi

@SpringAPIServiceTest
@Import(
  SentencingReconciliationService::class,
  NomisApiService::class,
  SentencingAdjustmentsApiService::class,
  SentencingConfiguration::class,
)
internal class SentencingReconciliationServiceTest {
  @MockBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var service: SentencingReconciliationService

  @Nested
  inner class WhenBothSystemsHaveNoAdjustments {
    @BeforeEach
    fun beforeEach() {
      sentencingAdjustmentsApi.stubAdjustmentsGet(emptyList())
      NomisApiExtension.nomisApi.stubGetSentencingAdjustments(123456)
    }

    @Test
    fun `will not report a mismatch`() = runTest {
      assertThat(
        service.checkBookingIncentiveMatch(
          ActivePrisonerId(
            bookingId = 123456L,
            offenderNo = "A1234AA",
          ),
        ),
      ).isNull()
    }
  }
}
