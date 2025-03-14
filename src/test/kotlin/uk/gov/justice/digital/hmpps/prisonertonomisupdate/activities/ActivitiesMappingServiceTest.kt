@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    fun `should call mapping api with OAuth2 token`() = runTest {
      mappingService.createMapping(newMapping())

      MappingExtension.mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/activities"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to mapping api`() = runTest {
      mappingService.createMapping(newMapping())

      MappingExtension.mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/activities"))
          .withRequestBody(matchingJsonPath("$.nomisCourseActivityId", equalTo("456"))),
      )
    }

    @Test
    fun `when a bad response is received an exception is thrown`() = runTest {
      MappingExtension.mappingServer.stubCreateActivityWithError(400)

      assertThrows<BadRequest> {
        mappingService.createMapping(newMapping())
      }
    }
  }

  @Nested
  inner class GetMappingGivenActivityScheduleId {

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      MappingExtension.mappingServer.stubGetMappings(
        activityScheduleId = 1234,
        response = """{
          "nomisCourseActivityId": 456,
          "activityScheduleId": 1234,
          "activityId": 2345,
          "mappingType": "TYPE"
        }
        """.trimMargin(),
      )

      mappingService.getMappings(1234)

      MappingExtension.mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/activities/activity-schedule-id/1234"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      MappingExtension.mappingServer.stubGetMappings(
        activityScheduleId = 1234,
        response = """{
          "nomisCourseActivityId": 456,
          "activityScheduleId": 1234,
          "activityId": 2345,
          "mappingType": "A_TYPE"
        }
        """.trimMargin(),
      )

      val data = mappingService.getMappings(1234)

      assertThat(data).isEqualTo(newMapping())
    }

    @Test
    fun `when mapping is not found null is returned`() = runTest {
      MappingExtension.mappingServer.stubGetMappingsWithError(123, 404)

      assertThat(mappingService.getMappingsOrNull(123)).isNull()
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      MappingExtension.mappingServer.stubGetMappingsWithError(123, 503)

      assertThrows<ServiceUnavailable> {
        mappingService.getMappings(123)
      }
    }
  }

  @Nested
  inner class GetScheduledInstanceMapping {

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      MappingExtension.mappingServer.stubGetScheduleInstanceMapping(
        scheduledInstanceId = 456,
        response = """{
          "scheduledInstanceId": 456,
          "nomisCourseScheduleId": 1234,
          "mappingType": "TYPE"
        }
        """.trimMargin(),
      )

      mappingService.getScheduledInstanceMappingOrNull(456)

      MappingExtension.mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/activities/schedules/scheduled-instance-id/456"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      MappingExtension.mappingServer.stubGetScheduleInstanceMapping(
        scheduledInstanceId = 456,
        response = """{
          "scheduledInstanceId": 456,
          "nomisCourseScheduleId": 1234,
          "mappingType": "A_TYPE"
        }
        """.trimMargin(),
      )

      val data = mappingService.getScheduledInstanceMappingOrNull(456)

      assertThat(data).isEqualTo(ActivityScheduleMappingDto(scheduledInstanceId = 456, nomisCourseScheduleId = 1234, mappingType = "A_TYPE"))
    }

    @Test
    fun `when mapping is not found null is returned`() = runTest {
      MappingExtension.mappingServer.stubGetScheduledInstanceMappingWithError(456, 404)

      assertThat(mappingService.getScheduledInstanceMappingOrNull(456)).isNull()
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      MappingExtension.mappingServer.stubGetScheduledInstanceMappingWithError(456, 503)

      assertThrows<ServiceUnavailable> {
        mappingService.getScheduledInstanceMappingOrNull(456)
      }
    }
  }

  @Nested
  inner class DeleteMappingsGreaterThanNomisCourseScheduleId {

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      MappingExtension.mappingServer.stubDeleteMappingsGreaterThan(1234)

      mappingService.deleteMappingsGreaterThan(1234)

      MappingExtension.mappingServer.verify(
        deleteRequestedFor(urlEqualTo("/mapping/schedules/max-nomis-schedule-id/1234"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      MappingExtension.mappingServer.stubDeleteMappingsGreaterThanError(1234)

      assertThrows<ServiceUnavailable> {
        mappingService.deleteMappingsGreaterThan(1234)
      }
    }
  }

  private fun newMapping() = ActivityMappingDto(nomisCourseActivityId = 456L, activityScheduleId = 1234L, activityId = 2345L, mappingType = "A_TYPE")
}
