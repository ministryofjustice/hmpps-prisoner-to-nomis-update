package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.github.tomakehurst.wiremock.client.WireMock
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension

class ActivityResourceIntTest : IntegrationTestBase() {

  @Nested
  inner class DeleteAll {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient.delete().uri("/activities")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.delete().uri("/activities")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `create visit forbidden with wrong role`() {
      webTestClient.delete().uri("/activities")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should delete all activities`() {
      MappingExtension.mappingServer.stubGetAllActivityMappings(
        """[
           { "activityScheduleId": 101, "nomisCourseActivityId": 201, "mappingType": "ACTIVITY_CREATED", "whenCreated": "2020-01-01T00:00:00Z" },
           { "activityScheduleId": 102, "nomisCourseActivityId": 202, "mappingType": "MIGRATED" }
           ]
        """.trimIndent(),
      )
      MappingExtension.mappingServer.stubDeleteActivityMapping(101)
      MappingExtension.mappingServer.stubDeleteActivityMapping(102)
      NomisApiExtension.nomisApi.stubActivityDelete(201)
      NomisApiExtension.nomisApi.stubActivityDelete(202)

      webTestClient.delete()
        .uri("/activities")
        .headers(setAuthorisation(roles = listOf("ROLE_QUEUE_ADMIN")))
        .exchange()
        .expectStatus()
        .isNoContent

      await untilAsserted {
        MappingExtension.mappingServer.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/mapping/activities/activity-schedule-id/101")))
        MappingExtension.mappingServer.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/mapping/activities/activity-schedule-id/102")))
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/activities/201")))
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/activities/202")))
      }
    }
  }
}
