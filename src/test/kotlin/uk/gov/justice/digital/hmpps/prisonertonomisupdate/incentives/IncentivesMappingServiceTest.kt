package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension

@SpringAPIServiceTest
@Import(IncentivesMappingService::class)
internal class IncentivesMappingServiceTest {

  @Autowired
  private lateinit var mappingService: IncentivesMappingService

  @Nested
  inner class CreateMapping {
    @BeforeEach
    internal fun setUp() {
      MappingExtension.mappingServer.stubCreateIncentive()
    }

    @Test
    fun `should call mapping api with OAuth2 token`() {
      mappingService.createMapping(newMapping())

      MappingExtension.mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/incentives"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post data to mapping api`() {
      mappingService.createMapping(newMapping())

      MappingExtension.mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/incentives"))
          .withRequestBody(matchingJsonPath("$.nomisBookingId", equalTo("456")))
      )
    }

    @Test
    internal fun `when a bad response is received an exception is thrown`() {
      MappingExtension.mappingServer.stubCreateIncentiveWithError(400)

      assertThatThrownBy {
        mappingService.createMapping(newMapping())
      }.isInstanceOf(BadRequest::class.java)
    }
  }

  @Nested
  inner class GetMappingGivenIncentiveId {

    @Test
    fun `should call api with OAuth2 token`() {
      MappingExtension.mappingServer.stubGetIncentiveId(
        incentiveId = 1234,
        response = """{
          "nomisBookingId": 456,
          "nomisIncentiveSequence": 3,
          "incentiveId": 1234,
          "mappingType": "TYPE"
        }""".trimMargin(),
      )

      mappingService.getMappingGivenIncentiveId(1234)

      MappingExtension.mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/incentives/incentive-id/1234"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will return data`() {
      MappingExtension.mappingServer.stubGetIncentiveId(
        incentiveId = 1234,
        response = """{
          "nomisBookingId": 456,
          "nomisIncentiveSequence": 3,
          "incentiveId": 1234,
          "mappingType": "A_TYPE"
        }""".trimMargin(),
      )

      val data = mappingService.getMappingGivenIncentiveId(1234)

      assertThat(data).isEqualTo(newMapping())
    }

    @Test
    internal fun `when mapping is not found null is returned`() {
      MappingExtension.mappingServer.stubGetIncentiveIdWithError(123, 404)

      assertThat(mappingService.getMappingGivenIncentiveId(123)).isNull()
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      MappingExtension.mappingServer.stubGetIncentiveIdWithError(123, 503)

      assertThatThrownBy {
        mappingService.getMappingGivenIncentiveId(123)
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  private fun newMapping() =
    IncentiveMappingDto(nomisBookingId = 456L, nomisIncentiveSequence = 3, incentiveId = 1234L, mappingType = "A_TYPE")
}
