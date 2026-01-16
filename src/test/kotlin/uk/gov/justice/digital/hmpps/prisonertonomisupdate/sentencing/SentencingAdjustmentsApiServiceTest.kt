@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
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
import org.springframework.web.reactive.function.client.WebClientResponseException.Forbidden
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentencingAdjustmentsApiExtension.Companion.sentencingAdjustmentsApi
import java.time.LocalDate
import java.util.UUID

@SpringAPIServiceTest
@Import(SentencingAdjustmentsApiService::class, SentencingConfiguration::class)
internal class SentencingAdjustmentsApiServiceTest {

  val adjustmentId = UUID.randomUUID().toString()

  @Autowired
  private lateinit var sentencingAdjustmentsApiService: SentencingAdjustmentsApiService

  @Nested
  @DisplayName("GET /legacy/adjustments/{adjustmentId}")
  inner class GetAdjustment {
    @BeforeEach
    internal fun setUp() {
      sentencingAdjustmentsApi.stubAdjustmentGet(adjustmentId = adjustmentId)
    }

    @Test
    fun `should call api with OAuth2 token`(): Unit = runTest {
      sentencingAdjustmentsApiService.getAdjustment(adjustmentId)

      sentencingAdjustmentsApi.verify(
        getRequestedFor(urlEqualTo("/legacy/adjustments/$adjustmentId"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call api with legacy content header`(): Unit = runTest {
      sentencingAdjustmentsApiService.getAdjustment(adjustmentId)

      sentencingAdjustmentsApi.verify(
        getRequestedFor(urlEqualTo("/legacy/adjustments/$adjustmentId"))
          .withHeader("Content-Type", equalTo("application/vnd.nomis-offence+json")),
      )
    }

    @Test
    internal fun `will parse data for a sentence adjustment`(): Unit = runTest {
      sentencingAdjustmentsApi.stubAdjustmentGet(
        adjustmentId = adjustmentId,
        adjustmentType = "RX",
        adjustmentDate = "2022-01-01",
        adjustmentDays = 1,
        adjustmentFromDate = "2021-07-01",
        sentenceSequence = 6,
        comment = "Remand added",
        active = true,
      )
      val adjustment = sentencingAdjustmentsApiService.getAdjustment(adjustmentId)

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
        adjustmentId = adjustmentId,
        adjustmentType = "ADA",
        adjustmentDate = "2022-01-01",
        adjustmentDays = 1,
      )
      val adjustment = sentencingAdjustmentsApiService.getAdjustment(adjustmentId)

      assertThat(adjustment.adjustmentDate).isEqualTo(LocalDate.parse("2022-01-01"))
      assertThat(adjustment.adjustmentType.value).isEqualTo("ADA")
      assertThat(adjustment.adjustmentDays).isEqualTo(1)
      assertThat(adjustment.adjustmentFromDate).isNull()
      assertThat(adjustment.sentenceSequence).isNull()
      assertThat(adjustment.comment).isNull()
    }

    @Test
    internal fun `when adjustment is not found an exception is thrown`() = runTest {
      sentencingAdjustmentsApi.stubAdjustmentGetWithError(adjustmentId, status = 404)

      assertThrows<NotFound> {
        sentencingAdjustmentsApiService.getAdjustment(adjustmentId)
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      sentencingAdjustmentsApi.stubAdjustmentGetWithError(adjustmentId, status = 503)

      assertThrows<ServiceUnavailable> {
        sentencingAdjustmentsApiService.getAdjustment(adjustmentId)
      }
    }
  }

  @Nested
  inner class GetAdjustmentOrNull {
    @BeforeEach
    internal fun setUp() {
      sentencingAdjustmentsApi.stubAdjustmentGet(adjustmentId = adjustmentId)
    }

    @Test
    fun `should call api with OAuth2 token`(): Unit = runTest {
      sentencingAdjustmentsApiService.getAdjustmentOrNull(adjustmentId)

      sentencingAdjustmentsApi.verify(
        getRequestedFor(urlEqualTo("/legacy/adjustments/$adjustmentId"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call api with legacy content header`(): Unit = runTest {
      sentencingAdjustmentsApiService.getAdjustmentOrNull(adjustmentId)

      sentencingAdjustmentsApi.verify(
        getRequestedFor(urlEqualTo("/legacy/adjustments/$adjustmentId"))
          .withHeader("Content-Type", equalTo("application/vnd.nomis-offence+json")),
      )
    }

    @Test
    internal fun `when adjustment is not found it will return null`() = runTest {
      sentencingAdjustmentsApi.stubAdjustmentGetWithError(adjustmentId, status = 404)

      assertThat(sentencingAdjustmentsApiService.getAdjustmentOrNull(adjustmentId)).isNull()
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
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call api with legacy content header`(): Unit = runTest {
      sentencingAdjustmentsApiService.getAdjustments("A1234AA")

      sentencingAdjustmentsApi.verify(
        getRequestedFor(urlEqualTo("/adjustments?person=A1234AA"))
          .withHeader("Content-Type", equalTo("application/vnd.nomis-offence+json")),
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
    internal fun `when person has not active sentence (identified by Unprocessable Content error ) null is returned`() = runTest {
      sentencingAdjustmentsApi.stubAdjustmentsGetWithError(status = 422)

      assertThat(sentencingAdjustmentsApiService.getAdjustments("A1234AA")).isNull()
    }

    @Test
    internal fun `when any other client bad response is received an exception is thrown`() = runTest {
      sentencingAdjustmentsApi.stubAdjustmentsGetWithError(status = 403)

      assertThrows<Forbidden> {
        sentencingAdjustmentsApiService.getAdjustments("A1234AA")
      }
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
