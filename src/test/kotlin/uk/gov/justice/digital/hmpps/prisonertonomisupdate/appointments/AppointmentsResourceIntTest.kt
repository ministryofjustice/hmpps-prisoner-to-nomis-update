package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.github.tomakehurst.wiremock.client.WireMock
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension

class AppointmentsResourceIntTest : IntegrationTestBase() {

  @Nested
  inner class DeleteAll {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient.delete().uri("/appointments")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.delete().uri("/appointments")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `create visit forbidden with wrong role`() {
      webTestClient.delete().uri("/appointments")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should delete all appointments`() {
      MappingExtension.mappingServer.stubGetAllAppointmentMappings(
        """[
        {"appointmentInstanceId": 101, "nomisEventId": 201, "label": "2021-04-11", "mappingType": "APPOINTMENT_CREATED", "whenCreated": "2020-01-01T00:00:00Z"},
        {"appointmentInstanceId": 102, "nomisEventId": 202, "label": "2022-05-12", "mappingType": "MIGRATED"}
        ]
        """.trimMargin(),
      )
      MappingExtension.mappingServer.stubDeleteAppointmentMapping(101)
      MappingExtension.mappingServer.stubDeleteAppointmentMapping(102)
      NomisApiExtension.nomisApi.stubAppointmentDelete(201)
      NomisApiExtension.nomisApi.stubAppointmentDelete(202)

      webTestClient.delete()
        .uri("/appointments")
        .headers(setAuthorisation(roles = listOf("ROLE_QUEUE_ADMIN")))
        .exchange()
        .expectStatus()
        .isNoContent

      await untilAsserted {
        MappingExtension.mappingServer.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/mapping/appointments/appointment-instance-id/101")))
        MappingExtension.mappingServer.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/mapping/appointments/appointment-instance-id/102")))
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/appointments/201")))
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/appointments/202")))
      }
    }
  }
}
