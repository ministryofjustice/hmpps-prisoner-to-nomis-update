package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
  @DisplayName("GET /adjustments/{adjustmentId}")
  inner class GetAdjustment {
    @BeforeEach
    internal fun setUp() {
      sentencingAdjustmentsApi.stubAdjustmentGet(adjustmentId = "1234")
    }

    @Test
    fun `should call api with OAuth2 token`(): Unit = runBlocking {
      sentencingAdjustmentsApiService.getAdjustment("1234")

      sentencingAdjustmentsApi.verify(
        getRequestedFor(urlEqualTo("/adjustments/1234"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will parse data for a sentence adjustment`(): Unit = runBlocking {
      sentencingAdjustmentsApi.stubAdjustmentGet(
        adjustmentId = "1234",
        adjustmentType = "RX",
        adjustmentDate = "2022-01-01",
        adjustmentDays = 1,
        adjustmentStartPeriod = "2021-07-01",
        sentenceSequence = 6,
        comment = "Remand added",
      )
      val adjustment = sentencingAdjustmentsApiService.getAdjustment("1234")

      assertThat(adjustment.adjustmentId).isEqualTo("1234")
      assertThat(adjustment.adjustmentDate).isEqualTo(LocalDate.parse("2022-01-01"))
      assertThat(adjustment.adjustmentType).isEqualTo("RX")
      assertThat(adjustment.adjustmentDays).isEqualTo(1)
      assertThat(adjustment.adjustmentStartPeriod).isEqualTo(LocalDate.parse("2021-07-01"))
      assertThat(adjustment.sentenceSequence).isEqualTo(6)
      assertThat(adjustment.comment).isEqualTo("Remand added")
    }

    @Test
    internal fun `will parse data for a key date adjustment`(): Unit = runBlocking {
      sentencingAdjustmentsApi.stubAdjustmentGet(
        adjustmentId = "1234",
        adjustmentType = "ADA",
        adjustmentDate = "2022-01-01",
        adjustmentDays = 1,
      )
      val adjustment = sentencingAdjustmentsApiService.getAdjustment("1234")

      assertThat(adjustment.adjustmentId).isEqualTo("1234")
      assertThat(adjustment.adjustmentDate).isEqualTo(LocalDate.parse("2022-01-01"))
      assertThat(adjustment.adjustmentType).isEqualTo("ADA")
      assertThat(adjustment.adjustmentDays).isEqualTo(1)
      assertThat(adjustment.adjustmentStartPeriod).isNull()
      assertThat(adjustment.sentenceSequence).isNull()
      assertThat(adjustment.comment).isNull()
    }

    @Test
    internal fun `when adjustment is not found an exception is thrown`() {
      sentencingAdjustmentsApi.stubAdjustmentGetWithError("1234", status = 404)

      assertThatThrownBy {
        runBlocking { sentencingAdjustmentsApiService.getAdjustment("1234") }
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      sentencingAdjustmentsApi.stubAdjustmentGetWithError("1234", status = 503)

      assertThatThrownBy {
        runBlocking { sentencingAdjustmentsApiService.getAdjustment("1234") }
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }
}
