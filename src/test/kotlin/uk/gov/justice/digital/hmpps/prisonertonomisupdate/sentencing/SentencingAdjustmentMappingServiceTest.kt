package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
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
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
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
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to mapping api`() = runBlocking {
      mappingService.createMapping(
        newMapping(
          nomisAdjustmentId = 123,
          nomisAdjustmentCategory = "KEY-DATE",
          adjustmentId = "9876",
        ),
      )

      mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/sentencing/adjustments"))
          .withRequestBody(matchingJsonPath("nomisAdjustmentId", equalTo("123")))
          .withRequestBody(matchingJsonPath("nomisAdjustmentCategory", equalTo("KEY-DATE")))
          .withRequestBody(matchingJsonPath("adjustmentId", equalTo("9876")))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("SENTENCING_CREATED"))),
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
  @DisplayName("GET mapping/sentencing/adjustments/adjustment-id/{adjustmentId} - NULLABLE")
  inner class GetMappingGivenAdjustmentIdOrNull {
    @Test
    fun `should call api with OAuth2 token`() = runBlocking {
      mappingServer.stubGetByAdjustmentId(
        adjustmentId = "1234",
      )

      mappingService.getMappingGivenAdjustmentIdOrNull("1234")

      mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/sentencing/adjustments/adjustment-id/1234"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return mapping data`(): Unit = runBlocking {
      mappingServer.stubGetByAdjustmentId(
        adjustmentId = "1234",
        nomisAdjustmentId = 123,
        nomisAdjustmentCategory = "KEY-DATE",
      )

      val mapping = mappingService.getMappingGivenAdjustmentIdOrNull("1234")

      assertThat(mapping?.nomisAdjustmentId).isEqualTo(123)
      assertThat(mapping?.nomisAdjustmentCategory).isEqualTo("KEY-DATE")
    }

    @Test
    internal fun `when mapping is not found null is returned`() = runBlocking {
      mappingServer.stubGetByAdjustmentIdWithError("1234", 404)

      assertThat(mappingService.getMappingGivenAdjustmentIdOrNull("1234")).isNull()
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      mappingServer.stubGetByAdjustmentIdWithError("1234", 503)

      assertThatThrownBy {
        runBlocking { mappingService.getMappingGivenAdjustmentIdOrNull("1234") }
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  @DisplayName("GET mapping/sentencing/adjustments/adjustment-id/{adjustmentId}")
  inner class GetMappingGivenAdjustmentId {
    @Test
    fun `should call api with OAuth2 token`() = runBlocking {
      mappingServer.stubGetByAdjustmentId(
        adjustmentId = "1234",
      )

      mappingService.getMappingGivenAdjustmentId("1234")

      mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/sentencing/adjustments/adjustment-id/1234"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return mapping data`(): Unit = runBlocking {
      mappingServer.stubGetByAdjustmentId(
        adjustmentId = "1234",
        nomisAdjustmentId = 123,
        nomisAdjustmentCategory = "KEY-DATE",
      )

      val mapping = mappingService.getMappingGivenAdjustmentId("1234")

      assertThat(mapping.nomisAdjustmentId).isEqualTo(123)
      assertThat(mapping.nomisAdjustmentCategory).isEqualTo("KEY-DATE")
    }

    @Test
    internal fun `when mapping is not found throws not found`(): Unit = runBlocking {
      mappingServer.stubGetByAdjustmentIdWithError("1234", 404)

      assertThatThrownBy {
        runBlocking { mappingService.getMappingGivenAdjustmentId("1234") }
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      mappingServer.stubGetByAdjustmentIdWithError("1234", 503)

      assertThatThrownBy {
        runBlocking { mappingService.getMappingGivenAdjustmentId("1234") }
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  @Nested
  @DisplayName("DELETE mapping/sentencing/adjustments/adjustment-id/{adjustmentId}")
  inner class DeleteMappingGivenSentenceAdjustmentId {
    @Test
    fun `should call api with OAuth2 token`() = runBlocking {
      mappingServer.stubDeleteByAdjustmentId(
        adjustmentId = "1234",
      )

      mappingService.deleteMappingGivenAdjustmentId("1234")

      mappingServer.verify(
        deleteRequestedFor(urlEqualTo("/mapping/sentencing/adjustments/adjustment-id/1234"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `when mapping is not found exception is thrown`(): Unit = runBlocking {
      mappingServer.stubDeleteByAdjustmentIdWithError("1234", 404)

      assertThatThrownBy {
        runBlocking { mappingService.deleteMappingGivenAdjustmentId("1234") }
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      mappingServer.stubDeleteByAdjustmentIdWithError("1234", 503)

      assertThatThrownBy {
        runBlocking { mappingService.deleteMappingGivenAdjustmentId("1234") }
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  private fun newMapping(
    nomisAdjustmentId: Long = 456L,
    nomisAdjustmentCategory: String = "SENTENCE",
    adjustmentId: String = "1234",
  ) =
    SentencingAdjustmentMappingDto(
      nomisAdjustmentId = nomisAdjustmentId,
      nomisAdjustmentCategory = nomisAdjustmentCategory,
      adjustmentId = adjustmentId,
    )
}
