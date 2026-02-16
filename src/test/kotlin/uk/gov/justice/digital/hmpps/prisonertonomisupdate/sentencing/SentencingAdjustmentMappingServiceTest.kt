@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentencingAdjustmentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentencingAdjustmentMappingDto.MappingType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentencingAdjustmentMappingDto.NomisAdjustmentCategory
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
    fun `should call mapping api with OAuth2 token`() = runTest {
      mappingService.createMapping(newMapping())

      mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/sentencing/adjustments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to mapping api`() = runTest {
      mappingService.createMapping(
        newMapping(
          nomisAdjustmentId = 123,
          nomisAdjustmentCategory = NomisAdjustmentCategory.KEYMinusDATE,
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
    internal fun `when a bad response is received an exception is thrown`() = runTest {
      mappingServer.stubCreateSentencingAdjustmentWithError(400)

      assertThrows<BadRequest> {
        mappingService.createMapping(newMapping())
      }
    }
  }

  @Nested
  @DisplayName("GET mapping/sentencing/adjustments/adjustment-id/{adjustmentId} - NULLABLE")
  inner class GetMappingGivenAdjustmentIdOrNull {
    @Test
    fun `should call api with OAuth2 token`() = runTest {
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
    fun `will return mapping data`(): Unit = runTest {
      mappingServer.stubGetByAdjustmentId(
        adjustmentId = "1234",
        nomisAdjustmentId = 123,
        nomisAdjustmentCategory = "KEY-DATE",
      )

      val mapping = mappingService.getMappingGivenAdjustmentIdOrNull("1234")

      assertThat(mapping?.nomisAdjustmentId).isEqualTo(123)
      assertThat(mapping?.nomisAdjustmentCategory?.value).isEqualTo("KEY-DATE")
    }

    @Test
    internal fun `when mapping is not found null is returned`() = runTest {
      mappingServer.stubGetByAdjustmentIdWithError("1234", 404)

      assertThat(mappingService.getMappingGivenAdjustmentIdOrNull("1234")).isNull()
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      mappingServer.stubGetByAdjustmentIdWithError("1234", 503)

      assertThrows<ServiceUnavailable> {
        mappingService.getMappingGivenAdjustmentIdOrNull("1234")
      }
    }
  }

  @Nested
  @DisplayName("GET mapping/sentencing/adjustments/adjustment-id/{adjustmentId}")
  inner class GetMappingGivenAdjustmentId {
    @Test
    fun `should call api with OAuth2 token`() = runTest {
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
    fun `will return mapping data`(): Unit = runTest {
      mappingServer.stubGetByAdjustmentId(
        adjustmentId = "1234",
        nomisAdjustmentId = 123,
        nomisAdjustmentCategory = "KEY-DATE",
      )

      val mapping = mappingService.getMappingGivenAdjustmentId("1234")

      assertThat(mapping.nomisAdjustmentId).isEqualTo(123)
      assertThat(mapping.nomisAdjustmentCategory.value).isEqualTo("KEY-DATE")
    }

    @Test
    internal fun `when mapping is not found throws not found`() = runTest {
      mappingServer.stubGetByAdjustmentIdWithError("1234", 404)

      assertThrows<NotFound> {
        mappingService.getMappingGivenAdjustmentId("1234")
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      mappingServer.stubGetByAdjustmentIdWithError("1234", 503)

      assertThrows<ServiceUnavailable> {
        mappingService.getMappingGivenAdjustmentId("1234")
      }
    }
  }

  @Nested
  @DisplayName("DELETE mapping/sentencing/adjustments/adjustment-id/{adjustmentId}")
  inner class DeleteMappingGivenSentenceAdjustmentId {
    @Test
    fun `should call api with OAuth2 token`() = runTest {
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
    internal fun `when mapping is not found exception is thrown`() = runTest {
      mappingServer.stubDeleteByAdjustmentIdWithError("1234", 404)

      assertThrows<NotFound> {
        mappingService.deleteMappingGivenAdjustmentId("1234")
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      mappingServer.stubDeleteByAdjustmentIdWithError("1234", 503)

      assertThrows<ServiceUnavailable> {
        mappingService.deleteMappingGivenAdjustmentId("1234")
      }
    }
  }

  private fun newMapping(
    nomisAdjustmentId: Long = 456L,
    nomisAdjustmentCategory: NomisAdjustmentCategory = NomisAdjustmentCategory.SENTENCE,
    adjustmentId: String = "1234",
  ) = SentencingAdjustmentMappingDto(
    nomisAdjustmentId = nomisAdjustmentId,
    nomisAdjustmentCategory = nomisAdjustmentCategory,
    adjustmentId = adjustmentId,
    mappingType = MappingType.SENTENCING_CREATED,
  )
}
