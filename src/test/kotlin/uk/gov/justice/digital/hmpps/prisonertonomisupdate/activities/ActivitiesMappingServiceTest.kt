package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

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
@Import(ActivitiesMappingService::class)
internal class ActivitiesMappingServiceTest {

  @Autowired
  private lateinit var mappingService: ActivitiesMappingService

  @Nested
  inner class CreateMapping {
    @BeforeEach
    internal fun setUp() {
      MappingExtension.mappingServer.stubCreateActivity()
    }

    @Test
    fun `should call mapping api with OAuth2 token`() {
      mappingService.createMapping(newMapping())

      MappingExtension.mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/activities"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will post data to mapping api`() {
      mappingService.createMapping(newMapping())

      MappingExtension.mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/activities"))
          .withRequestBody(matchingJsonPath("$.nomisCourseActivityId", equalTo("456")))
      )
    }

    @Test
    fun `when a bad response is received an exception is thrown`() {
      MappingExtension.mappingServer.stubCreateActivityWithError(400)

      assertThatThrownBy {
        mappingService.createMapping(newMapping())
      }.isInstanceOf(BadRequest::class.java)
    }
  }

  @Nested
  inner class GetMappingGivenActivityScheduleId {

    @Test
    fun `should call api with OAuth2 token`() {
      MappingExtension.mappingServer.stubGetMappingGivenActivityScheduleId(
        id = 1234,
        response = """{
          "nomisCourseActivityId": 456,
          "activityScheduleId": 1234,
          "mappingType": "TYPE"
        }""".trimMargin(),
      )

      mappingService.getMappingGivenActivityScheduleId(1234)

      MappingExtension.mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/activities/activity-schedule-id/1234"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will return data`() {
      MappingExtension.mappingServer.stubGetMappingGivenActivityScheduleId(
        id = 1234,
        response = """{
          "nomisCourseActivityId": 456,
          "activityScheduleId": 1234,
          "mappingType": "A_TYPE"
        }""".trimMargin(),
      )

      val data = mappingService.getMappingGivenActivityScheduleId(1234)

      assertThat(data).isEqualTo(newMapping())
    }

    @Test
    fun `when mapping is not found null is returned`() {
      MappingExtension.mappingServer.stubGetMappingGivenActivityScheduleIdWithError(123, 404)

      assertThat(mappingService.getMappingGivenActivityScheduleId(123)).isNull()
    }

    @Test
    fun `when any bad response is received an exception is thrown`() {
      MappingExtension.mappingServer.stubGetMappingGivenActivityScheduleIdWithError(123, 503)

      assertThatThrownBy {
        mappingService.getMappingGivenActivityScheduleId(123)
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }

  private fun newMapping() =
    ActivityMappingDto(nomisCourseActivityId = 456L, activityScheduleId = 1234L, mappingType = "A_TYPE")
}
