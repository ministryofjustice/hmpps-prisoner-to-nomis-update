@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentencingAdjustmentsApiExtension.Companion.sentencingAdjustmentsApi
import java.time.LocalDate

@SpringAPIServiceTest
@Import(SentencingAdjustmentsApiService::class, SentencingConfiguration::class)
internal class SentencingAdjustmentsApiServiceTest {

  @Autowired
  private lateinit var sentencingAdjustmentsApiService: SentencingAdjustmentsApiService

  @Nested
  @DisplayName("GET /legacy/adjustments/{adjustmentId}")
  inner class GetAdjustment {
    @BeforeEach
    internal fun setUp() {
      sentencingAdjustmentsApi.stubAdjustmentGet(adjustmentId = "1234")
    }

    @Test
    fun `should call api with OAuth2 token`(): Unit = runTest {
      sentencingAdjustmentsApiService.getAdjustment("1234")

      sentencingAdjustmentsApi.verify(
        getRequestedFor(urlEqualTo("/legacy/adjustments/1234"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will parse data for a sentence adjustment`(): Unit = runTest {
      sentencingAdjustmentsApi.stubAdjustmentGet(
        adjustmentId = "1234",
        adjustmentType = "RX",
        adjustmentDate = "2022-01-01",
        adjustmentDays = 1,
        adjustmentFromDate = "2021-07-01",
        sentenceSequence = 6,
        comment = "Remand added",
        active = true,
      )
      val adjustment = sentencingAdjustmentsApiService.getAdjustment("1234")

      assertThat(adjustment.adjustmentDate).isEqualTo(LocalDate.parse("2022-01-01"))
      assertThat(adjustment.adjustmentType.value).isEqualTo("RX")
      assertThat(adjustment.adjustmentDays).isEqualTo(1)
      assertThat(adjustment.adjustmentFromDate).isEqualTo(LocalDate.parse("2021-07-01"))
      assertThat(adjustment.sentenceSequence).isEqualTo(6)
      assertThat(adjustment.comment).isEqualTo("Remand added")
    }

    @Test
    internal fun `will parse data for a key date adjustment`() = runTest {
      sentencingAdjustmentsApi.stubAdjustmentGet(
        adjustmentId = "1234",
        adjustmentType = "ADA",
        adjustmentDate = "2022-01-01",
        adjustmentDays = 1,
      )
      val adjustment = sentencingAdjustmentsApiService.getAdjustment("1234")

      assertThat(adjustment.adjustmentDate).isEqualTo(LocalDate.parse("2022-01-01"))
      assertThat(adjustment.adjustmentType.value).isEqualTo("ADA")
      assertThat(adjustment.adjustmentDays).isEqualTo(1)
      assertThat(adjustment.adjustmentFromDate).isNull()
      assertThat(adjustment.sentenceSequence).isNull()
      assertThat(adjustment.comment).isNull()
    }

    @Test
    internal fun `when adjustment is not found an exception is thrown`() = runTest {
      sentencingAdjustmentsApi.stubAdjustmentGetWithError("1234", status = 404)

      assertThrows<NotFound> {
        sentencingAdjustmentsApiService.getAdjustment("1234")
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      sentencingAdjustmentsApi.stubAdjustmentGetWithError("1234", status = 503)

      assertThrows<ServiceUnavailable> {
        sentencingAdjustmentsApiService.getAdjustment("1234")
      }
    }
  }

  @Nested
  @DisplayName("GET /adjustments?person={offenderNo}")
  inner class GetAdjustments {
    @BeforeEach
    internal fun setUp() {
      sentencingAdjustmentsApi.stubAdjustmentsGet()
    }

    @Test
    fun `should call api with OAuth2 token`(): Unit = runTest {
      sentencingAdjustmentsApiService.getAdjustments("A1234AA")

      sentencingAdjustmentsApi.verify(
        getRequestedFor(urlEqualTo("/adjustments?person=A1234AA"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will parse data for adjustments`(): Unit = runTest {
      val adjustments = sentencingAdjustmentsApiService.getAdjustments("A1234AA")

      assertThat(adjustments?.size).isEqualTo(11)
    }

    @Test
    internal fun `when no person with no adjustments found null is returns`() = runTest {
      sentencingAdjustmentsApi.stubAdjustmentsGetWithError(status = 404)

      assertThat(sentencingAdjustmentsApiService.getAdjustments("A1234AA")).isNull()
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      sentencingAdjustmentsApi.stubAdjustmentsGetWithError(status = 503)

      assertThrows<ServiceUnavailable> {
        sentencingAdjustmentsApiService.getAdjustments("A1234AA")
      }
    }
  }
}
