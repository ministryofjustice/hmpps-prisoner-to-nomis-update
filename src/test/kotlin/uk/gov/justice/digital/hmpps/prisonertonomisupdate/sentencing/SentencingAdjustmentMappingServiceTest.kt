package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer

@SpringAPIServiceTest
@Import(SentencingAdjustmentsMappingService::class)
internal class SentencingAdjustmentMappingServiceTest {

  @Autowired
  private lateinit var mappingService: SentencingAdjustmentsMappingService

  @Nested
  @DisplayName("POST /mapping/sentencing/adjustments")
  inner class CreateMapping {
    @BeforeEach
    internal fun setUp() {
      mappingServer.stubCreateSentencingAdjustment()
    }

    @Test
    fun `should call mapping api with OAuth2 token`() = runBlocking {
      mappingService.createMapping(newMapping())

      mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/sentencing/adjustments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post data to mapping api`() = runBlocking {
      mappingService.createMapping(
        newMapping(
          nomisAdjustmentId = 123,
          nomisAdjustmentType = "BOOKING",
          sentenceAdjustmentId = "9876"
        )
      )

      mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/sentencing/adjustments"))
          .withRequestBody(matchingJsonPath("nomisAdjustmentId", equalTo("123")))
          .withRequestBody(matchingJsonPath("nomisAdjustmentType", equalTo("BOOKING")))
          .withRequestBody(matchingJsonPath("sentenceAdjustmentId", equalTo("9876")))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("SENTENCING_CREATED")))
      )
    }

    @Test
    internal fun `when a bad response is received an exception is thrown`() {
      mappingServer.stubCreateSentencingAdjustmentWithError(400)

      assertThatThrownBy {
        runBlocking { mappingService.createMapping(newMapping()) }
      }.isInstanceOf(BadRequest::class.java)
    }
  }

  @Nested
  @DisplayName("GET mapping/sentencing/adjustments/sentence-adjustment-id/{sentenceAdjustmentId}")
  inner class GetMappingGivenSentenceAdjustmentId {
    @Test
    fun `should call api with OAuth2 token`() = runBlocking {
      mappingServer.stubGetBySentenceAdjustmentId(
        sentenceAdjustmentId = "1234",
      )

      mappingService.getMappingGivenSentenceAdjustmentId("1234")

      mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/sentencing/adjustments/sentence-adjustment-id/1234"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will return mapping data`(): Unit = runBlocking {
      mappingServer.stubGetBySentenceAdjustmentId(
        sentenceAdjustmentId = "1234",
        nomisAdjustmentId = 123,
        nomisAdjustmentType = "BOOKING",
      )

      val mapping = mappingService.getMappingGivenSentenceAdjustmentId("1234")

      assertThat(mapping?.nomisAdjustmentId).isEqualTo(123)
      assertThat(mapping?.nomisAdjustmentType).isEqualTo("BOOKING")
    }

    @Test
    internal fun `when mapping is not found null is returned`() = runBlocking {
      mappingServer.stubGetBySentenceAdjustmentIdWithError("1234", 404)

      assertThat(mappingService.getMappingGivenSentenceAdjustmentId("1234")).isNull()
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      mappingServer.stubGetBySentenceAdjustmentIdWithError("1234", 503)

      assertThatThrownBy {
        runBlocking { mappingService.getMappingGivenSentenceAdjustmentId("1234") }
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  private fun newMapping(
    nomisAdjustmentId: Long = 456L,
    nomisAdjustmentType: String = "SENTENCE",
    sentenceAdjustmentId: String = "1234"
  ) =
    SentencingAdjustmentMappingDto(
      nomisAdjustmentId = nomisAdjustmentId,
      nomisAdjustmentType = nomisAdjustmentType,
      sentenceAdjustmentId = sentenceAdjustmentId,
    )
}
