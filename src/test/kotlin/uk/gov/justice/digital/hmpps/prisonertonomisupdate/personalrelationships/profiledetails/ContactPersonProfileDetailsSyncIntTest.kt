package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.BAD_GATEWAY
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileType.CHILD
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileType.MARITAL
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.ProfileDetailsNomisApiMockServer
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

class ContactPersonProfileDetailsSyncIntTest(
  @Autowired private val nomisApi: ProfileDetailsNomisApiMockServer,
  @Autowired private val dpsApi: ContactPersonProfileDetailsDpsApiMockServer,
) : SqsIntegrationTestBase() {

  @Nested
  inner class DomesticStatusCreatedEvent {
    @Nested
    inner class HappyPath {

      @Test
      fun `should synchronise domestic status`() {
        dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", response = domesticStatus(domesticStatusCode = "M"))
        nomisApi.stubPutProfileDetails(offenderNo = "A1234BC", created = true, bookingId = 12345)

        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.created",
          payload = domesticStatusCreatedEvent(
            prisonerNumber = "A1234BC",
            domesticStatusId = 123,
            source = "DPS",
          ),
        ).also { waitForAnyProcessingToComplete() }

        verifyDpsApiCall(prisonerNumber = "A1234BC")
        verifyNomisPutProfileDetails(
          prisonerNumber = "A1234BC",
          profileType = equalTo("MARITAL"),
          profileCode = equalTo("M"),
        )
        verifyTelemetry(
          profileType = MARITAL,
          telemetryType = "created",
          offenderNo = "A1234BC",
          requestedDpsId = 123,
          dpsId = 54321,
          bookingId = 12345,
        )
      }

      @Test
      fun `should raise updated telemetry`() {
        dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC")
        nomisApi.stubPutProfileDetails(created = false)

        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.created",
          payload = domesticStatusCreatedEvent(),
        ).also { waitForAnyProcessingToComplete() }

        verifyDpsApiCall()
        verifyNomisPutProfileDetails()
        verifyTelemetry(
          telemetryType = "updated",
          bookingId = 12345,
        )
      }

      @Test
      fun `should ignore if change performed in NOMIS`() {
        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.created",
          payload = domesticStatusCreatedEvent(source = "NOMIS"),
        ).also { waitForAnyProcessingToComplete() }

        dpsApi.verify(count = 0, getRequestedFor(urlPathEqualTo("/sync/A1234BC/domestic-status")))
        nomisApi.verify(count = 0, putRequestedFor(urlPathEqualTo("/prisoners/A1234BC/profile-details")))
        verifyTelemetry(telemetryType = "ignored", ignoreReason = "Entity was created in NOMIS", dpsId = null)
      }

      @Test
      fun `should set NOMIS to null if DPS returns not found`() {
        dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", errorStatus = NOT_FOUND)
        nomisApi.stubPutProfileDetails()

        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.created",
          payload = domesticStatusCreatedEvent(),
        ).also { waitForAnyProcessingToComplete() }

        verifyDpsApiCall()
        verifyNomisPutProfileDetails(
          prisonerNumber = "A1234BC",
          profileType = equalTo("MARITAL"),
          profileCode = absent(),
        )
        verifyTelemetry(
          profileType = MARITAL,
          telemetryType = "created",
          offenderNo = "A1234BC",
          requestedDpsId = 123,
          dpsId = null,
          bookingId = 12345,
        )
      }
    }

    @Nested
    inner class Failures {
      @Test
      fun `should fail if call to DPS fails`() {
        dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", errorStatus = BAD_GATEWAY)

        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.created",
          payload = domesticStatusCreatedEvent(),
        ).also { waitForDlqMessage() }

        verifyDpsApiCall()
        nomisApi.verify(count = 0, putRequestedFor(urlPathEqualTo("/prisoners/A1234BC/profile-details")))
        verifyTelemetry(
          telemetryType = "error",
          errorReason = "502 Bad Gateway from GET http://localhost:8099/sync/A1234BC/domestic-status",
          dpsId = null,
        )
      }

      @Test
      fun `should fail if call to DPS returns error`() {
        dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", errorStatus = BAD_REQUEST)

        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.created",
          payload = domesticStatusCreatedEvent(),
        ).also { waitForDlqMessage() }

        verifyDpsApiCall()
        nomisApi.verify(count = 0, putRequestedFor(urlPathEqualTo("/prisoners/A1234BC/profile-details")))
        verifyTelemetry(
          telemetryType = "error",
          errorReason = "400 Bad Request from GET http://localhost:8099/sync/A1234BC/domestic-status",
          dpsId = null,
        )
      }

      @Test
      fun `should fail if call to NOMIS fails`() {
        dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC")
        nomisApi.stubPutProfileDetails(errorStatus = BAD_REQUEST)

        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.created",
          payload = domesticStatusCreatedEvent(),
        ).also { waitForDlqMessage() }

        verifyDpsApiCall()
        verifyNomisPutProfileDetails()
        verifyTelemetry(
          telemetryType = "error",
          errorReason = "400 Bad Request from PUT http://localhost:8082/prisoners/A1234BC/profile-details",
          dpsId = null,
        )
      }
    }
  }

  @Nested
  inner class NumberOfChildrenCreatedEvent {
    @Nested
    inner class HappyPath {

      @Test
      fun `should synchronise number of children`() {
        dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", response = numberOfChildren(numberOfChildren = "3"))
        nomisApi.stubPutProfileDetails(offenderNo = "A1234BC", created = true, bookingId = 12345)

        publishDomainEvent(
          eventType = "personal-relationships-api.number-of-children.created",
          payload = numberOfChildrenCreatedEvent(
            prisonerNumber = "A1234BC",
            prisonerNumberOfChildrenId = 123,
            source = "DPS",
          ),
        ).also { waitForAnyProcessingToComplete() }

        verifyDpsApiCall(prisonerNumber = "A1234BC", profileType = CHILD)
        verifyNomisPutProfileDetails(
          prisonerNumber = "A1234BC",
          profileType = equalTo("CHILD"),
          profileCode = equalTo("3"),
        )
        verifyTelemetry(
          profileType = CHILD,
          telemetryType = "created",
          offenderNo = "A1234BC",
          requestedDpsId = 123,
          dpsId = 54321,
          bookingId = 12345,
        )
      }

      @Test
      fun `should raise updated telemetry`() {
        dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC")
        nomisApi.stubPutProfileDetails(created = false)

        publishDomainEvent(
          eventType = "personal-relationships-api.number-of-children.created",
          payload = numberOfChildrenCreatedEvent(),
        ).also { waitForAnyProcessingToComplete() }

        verifyDpsApiCall(profileType = CHILD)
        verifyNomisPutProfileDetails(profileType = equalTo("CHILD"), profileCode = equalTo("3"))
        verifyTelemetry(
          profileType = CHILD,
          telemetryType = "updated",
          bookingId = 12345,
        )
      }

      @Test
      fun `should ignore if change performed in NOMIS`() {
        publishDomainEvent(
          eventType = "personal-relationships-api.number-of-children.created",
          payload = numberOfChildrenCreatedEvent(source = "NOMIS"),
        ).also { waitForAnyProcessingToComplete() }

        dpsApi.verify(count = 0, getRequestedFor(urlPathEqualTo("/sync/A1234BC/number-of-children")))
        nomisApi.verify(count = 0, putRequestedFor(urlPathEqualTo("/prisoners/A1234BC/number-of-children")))
        verifyTelemetry(
          profileType = CHILD,
          telemetryType = "ignored",
          ignoreReason = "Entity was created in NOMIS",
          dpsId = null,
        )
      }

      @Test
      fun `should set NOMIS to null if DPS returns not found`() {
        dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", errorStatus = NOT_FOUND)
        nomisApi.stubPutProfileDetails()

        publishDomainEvent(
          eventType = "personal-relationships-api.number-of-children.created",
          payload = numberOfChildrenCreatedEvent(),
        ).also { waitForAnyProcessingToComplete() }

        verifyDpsApiCall(profileType = CHILD)
        verifyNomisPutProfileDetails(
          prisonerNumber = "A1234BC",
          profileType = equalTo("CHILD"),
          profileCode = absent(),
        )
        verifyTelemetry(
          profileType = CHILD,
          telemetryType = "created",
          offenderNo = "A1234BC",
          requestedDpsId = 123,
          dpsId = null,
          bookingId = 12345,
        )
      }
    }

    @Nested
    inner class Failures {
      @Test
      fun `should fail if call to DPS fails`() {
        dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", errorStatus = BAD_GATEWAY)

        publishDomainEvent(
          eventType = "personal-relationships-api.number-of-children.created",
          payload = numberOfChildrenCreatedEvent(),
        ).also { waitForDlqMessage() }

        verifyDpsApiCall(profileType = CHILD)
        nomisApi.verify(count = 0, putRequestedFor(urlPathEqualTo("/prisoners/A1234BC/number-of-children")))
        verifyTelemetry(
          profileType = CHILD,
          telemetryType = "error",
          errorReason = "502 Bad Gateway from GET http://localhost:8099/sync/A1234BC/number-of-children",
          dpsId = null,
        )
      }

      @Test
      fun `should fail if call to DPS returns error`() {
        dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", errorStatus = BAD_REQUEST)

        publishDomainEvent(
          eventType = "personal-relationships-api.number-of-children.created",
          payload = numberOfChildrenCreatedEvent(),
        ).also { waitForDlqMessage() }

        verifyDpsApiCall(profileType = CHILD)
        nomisApi.verify(count = 0, putRequestedFor(urlPathEqualTo("/prisoners/A1234BC/number-of-children")))
        verifyTelemetry(
          profileType = CHILD,
          telemetryType = "error",
          errorReason = "400 Bad Request from GET http://localhost:8099/sync/A1234BC/number-of-children",
          dpsId = null,
        )
      }

      @Test
      fun `should fail if call to NOMIS fails`() {
        dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC")
        nomisApi.stubPutProfileDetails(errorStatus = BAD_REQUEST)

        publishDomainEvent(
          eventType = "personal-relationships-api.number-of-children.created",
          payload = numberOfChildrenCreatedEvent(),
        ).also { waitForDlqMessage() }

        verifyDpsApiCall(profileType = CHILD)
        verifyNomisPutProfileDetails(profileType = equalTo("CHILD"), profileCode = equalTo("3"))
        verifyTelemetry(
          profileType = CHILD,
          telemetryType = "error",
          errorReason = "400 Bad Request from PUT http://localhost:8082/prisoners/A1234BC/profile-details",
          dpsId = null,
        )
      }
    }
  }

  @Nested
  inner class DomesticStatusDeletedEvent {
    @Nested
    inner class HappyPath {
      @Test
      fun `should sync null domestic status on delete`() {
        nomisApi.stubPutProfileDetails(offenderNo = "A1234BC", created = false, bookingId = 12345)

        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.deleted",
          payload = domesticStatusDeletedEvent(
            prisonerNumber = "A1234BC",
            domesticStatusId = 123,
            source = "DPS",
          ),
        ).also { waitForAnyProcessingToComplete() }

        verifyNomisPutProfileDetails(
          prisonerNumber = "A1234BC",
          profileType = equalTo("MARITAL"),
          profileCode = absent(),
        )
        verifyTelemetry(
          profileType = MARITAL,
          telemetryType = "deleted",
          offenderNo = "A1234BC",
          requestedDpsId = 123,
          bookingId = 12345,
          dpsId = null,
        )
      }

      @Test
      fun `should ignore if delete performed in NOMIS`() {
        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.deleted",
          payload = domesticStatusDeletedEvent(source = "NOMIS"),
        ).also { waitForAnyProcessingToComplete() }

        nomisApi.verify(count = 0, putRequestedFor(urlPathEqualTo("/prisoners/A1234BC/profile-details")))
        verifyTelemetry(telemetryType = "ignored", ignoreReason = "Entity was created in NOMIS", dpsId = null)
      }
    }

    @Nested
    inner class Failures {

      @Test
      fun `should fail if call to NOMIS fails`() {
        nomisApi.stubPutProfileDetails(errorStatus = BAD_REQUEST)

        publishDomainEvent(
          eventType = "personal-relationships-api.domestic-status.deleted",
          payload = domesticStatusDeletedEvent(),
        ).also { waitForDlqMessage() }

        verifyNomisPutProfileDetails(profileCode = absent())
        verifyTelemetry(
          telemetryType = "error",
          errorReason = "400 Bad Request from PUT http://localhost:8082/prisoners/A1234BC/profile-details",
          dpsId = null,
        )
      }
    }
  }

  @Nested
  inner class NumberOfChildrenDeletedEvent {
    @Nested
    inner class HappyPath {
      @Test
      fun `should sync null number of children on delete`() {
        nomisApi.stubPutProfileDetails(offenderNo = "A1234BC", created = false, bookingId = 12345)

        publishDomainEvent(
          eventType = "personal-relationships-api.number-of-children.deleted",
          payload = numberOfChildrenDeletedEvent(
            prisonerNumber = "A1234BC",
            prisonerNumberOfChildrenId = 123,
            source = "DPS",
          ),
        ).also { waitForAnyProcessingToComplete() }

        verifyNomisPutProfileDetails(
          prisonerNumber = "A1234BC",
          profileType = equalTo("CHILD"),
          profileCode = absent(),
        )
        verifyTelemetry(
          profileType = CHILD,
          telemetryType = "deleted",
          offenderNo = "A1234BC",
          requestedDpsId = 123,
          bookingId = 12345,
          dpsId = null,
        )
      }

      @Test
      fun `should ignore if delete performed in NOMIS`() {
        publishDomainEvent(
          eventType = "personal-relationships-api.number-of-children.deleted",
          payload = numberOfChildrenDeletedEvent(source = "NOMIS"),
        ).also { waitForAnyProcessingToComplete() }

        nomisApi.verify(count = 0, putRequestedFor(urlPathEqualTo("/prisoners/A1234BC/profile-details")))
        verifyTelemetry(
          profileType = CHILD,
          telemetryType = "ignored",
          ignoreReason = "Entity was created in NOMIS",
          dpsId = null,
        )
      }
    }

    @Nested
    inner class Failures {

      @Test
      fun `should fail if call to NOMIS fails`() {
        nomisApi.stubPutProfileDetails(errorStatus = BAD_REQUEST)

        publishDomainEvent(
          eventType = "personal-relationships-api.number-of-children.deleted",
          payload = numberOfChildrenDeletedEvent(),
        ).also { waitForDlqMessage() }

        verifyNomisPutProfileDetails(profileType = equalTo("CHILD"), profileCode = absent())
        verifyTelemetry(
          profileType = CHILD,
          telemetryType = "error",
          errorReason = "400 Bad Request from PUT http://localhost:8082/prisoners/A1234BC/profile-details",
          dpsId = null,
        )
      }
    }
  }

  @Nested
  @DisplayName("PUT /contactperson/sync/profile-details/{prisonerNumber}/{profileType}")
  inner class SyncEndpoint {

    @Test
    fun `access forbidden when no role`() {
      webTestClient.put().uri("/contactperson/sync/profile-details/A1234BC/MARITAL")
        .headers(setAuthorisation(roles = listOf()))
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.put().uri("/contactperson/sync/profile-details/A1234BC/MARITAL")
        .headers(setAuthorisation(roles = listOf("BANANAS")))
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.put().uri("/contactperson/sync/profile-details/A1234BC/MARITAL")
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should sync profile details`() = runTest {
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC")
      nomisApi.stubPutProfileDetails(created = false)

      webTestClient.put().uri("/contactperson/sync/profile-details/A1234BC/MARITAL")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__SYNCHRONISATION__RW")))
        .exchange()
        .expectStatus().isOk

      verifyDpsApiCall()
      verifyNomisPutProfileDetails()
      verifyTelemetry(
        telemetryType = "updated",
        requestedDpsId = 0,
        bookingId = 12345,
      )
    }

    @Test
    fun `should handle error from DPS`() {
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", errorStatus = BAD_GATEWAY)

      webTestClient.put().uri("/contactperson/sync/profile-details/A1234BC/MARITAL")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__SYNCHRONISATION__RW")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("userMessage").value<String> {
          assertThat(it).contains("502 Bad Gateway from GET http://localhost:8099/sync/A1234BC/domestic-status")
        }

      verifyDpsApiCall()
      nomisApi.verify(count = 0, putRequestedFor(urlPathEqualTo("/prisoners/A1234BC/profile-details")))
      verifyTelemetry(
        telemetryType = "error",
        errorReason = "502 Bad Gateway from GET http://localhost:8099/sync/A1234BC/domestic-status",
        requestedDpsId = 0,
        dpsId = null,
      )
    }

    @Test
    fun `should handle error from NOMIS`() {
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC")
      nomisApi.stubPutProfileDetails(errorStatus = BAD_REQUEST)

      webTestClient.put().uri("/contactperson/sync/profile-details/A1234BC/MARITAL")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__SYNCHRONISATION__RW")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("userMessage").value<String> {
          assertThat(it).contains("400 Bad Request from PUT http://localhost:8082/prisoners/A1234BC/profile-details")
        }

      verifyDpsApiCall()
      verifyNomisPutProfileDetails()
      verifyTelemetry(
        telemetryType = "error",
        errorReason = "400 Bad Request from PUT http://localhost:8082/prisoners/A1234BC/profile-details",
        requestedDpsId = 0,
        dpsId = null,
      )
    }

    @Test
    fun `should handle invalid profile type`() {
      webTestClient.put().uri("/contactperson/sync/profile-details/A1234BC/BUILD")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__SYNCHRONISATION__RW")))
        .exchange()
        .expectStatus().isBadRequest

      dpsApi.verify(count = 0, getRequestedFor(urlPathEqualTo("/sync/A1234BC/domestic-status")))
      nomisApi.verify(count = 0, putRequestedFor(urlPathEqualTo("/prisoners/A1234BC/profile-details")))
      verify(telemetryClient, times(0)).trackEvent(anyString(), anyMap(), isNull())
    }
  }

  @Nested
  inner class ReadmissionSwitchBookingEvent {
    @Test
    fun `should sync domestic status and number of children`() = runTest {
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC")
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC")
      nomisApi.stubPutProfileDetails(created = false)

      publishDomainEvent(
        eventType = "prisoner-offender-search.prisoner.received",
        payload = readmissionSwitchedBookingEvent(prisonerNumber = "A1234BC", reason = "READMISSION_SWITCH_BOOKING"),
      ).also { waitForAnyProcessingToComplete(times = 2) }

      verifyDpsApiCall(profileType = MARITAL)
      verifyDpsApiCall(profileType = CHILD)
      verifyNomisPutProfileDetails(profileType = equalTo("MARITAL"), profileCode = equalTo("M"))
      verifyNomisPutProfileDetails(profileType = equalTo("CHILD"), profileCode = equalTo("3"))
      verifyTelemetry(
        profileType = MARITAL,
        telemetryType = "updated",
        requestedDpsId = 0,
        bookingId = 12345,
      )
      verifyTelemetry(
        profileType = CHILD,
        telemetryType = "updated",
        requestedDpsId = 0,
        bookingId = 12345,
      )
    }

    @Test
    fun `should handle a failed call to DPS`() = runTest {
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC")
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", BAD_GATEWAY)
      nomisApi.stubPutProfileDetails(created = false)

      publishDomainEvent(
        eventType = "prisoner-offender-search.prisoner.received",
        payload = readmissionSwitchedBookingEvent(prisonerNumber = "A1234BC"),
      ).also { waitForDlqMessage() }

      verifyDpsApiCall(profileType = MARITAL)
      verifyDpsApiCall(profileType = CHILD)
      verifyNomisPutProfileDetails(profileType = equalTo("MARITAL"), profileCode = equalTo("M"))
      verifyNomisPutProfileDetails(count = 0, profileType = equalTo("CHILD"), profileCode = equalTo("3"))
      verifyTelemetry(
        profileType = MARITAL,
        telemetryType = "updated",
        requestedDpsId = 0,
        bookingId = null,
      )
      verifyTelemetry(
        profileType = CHILD,
        telemetryType = "error",
        requestedDpsId = 0,
        bookingId = null,
        dpsId = null,
        errorReason = "502 Bad Gateway from GET http://localhost:8099/sync/A1234BC/number-of-children",
      )
    }

    @Test
    fun `should handle a failed call to NOMIS`() = runTest {
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC")
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC")
      nomisApi.stubPutProfileDetails(BAD_REQUEST)

      publishDomainEvent(
        eventType = "prisoner-offender-search.prisoner.received",
        payload = readmissionSwitchedBookingEvent(prisonerNumber = "A1234BC"),
      ).also { waitForDlqMessage() }

      verifyDpsApiCall(profileType = MARITAL)
      verifyDpsApiCall(count = 0, profileType = CHILD)
      verifyNomisPutProfileDetails(profileType = equalTo("MARITAL"), profileCode = equalTo("M"))
      verifyNomisPutProfileDetails(count = 0, profileType = equalTo("CHILD"), profileCode = equalTo("3"))
      verifyTelemetry(
        profileType = MARITAL,
        telemetryType = "error",
        requestedDpsId = 0,
        dpsId = null,
        bookingId = null,
        errorReason = "400 Bad Request from PUT http://localhost:8082/prisoners/A1234BC/profile-details",
      )
    }

    @Test
    fun `should do nothing if receive not for a switch booking`() = runTest {
      publishDomainEvent(
        eventType = "prisoner-offender-search.prisoner.received",
        payload = readmissionSwitchedBookingEvent(prisonerNumber = "A1234BC", reason = "ANY_OTHER_REASON"),
      ).also { waitForAnyProcessingToComplete(times = 0) }

      verifyDpsApiCall(count = 0, profileType = MARITAL)
      verifyDpsApiCall(count = 0, profileType = CHILD)
      verifyNomisPutProfileDetails(count = 0, profileType = equalTo("MARITAL"), profileCode = equalTo("M"))
      verifyNomisPutProfileDetails(count = 0, profileType = equalTo("CHILD"), profileCode = equalTo("3"))
      verify(telemetryClient, times(0)).trackEvent(anyString(), anyMap(), isNull())
    }
  }

  private fun verifyDpsApiCall(prisonerNumber: String = "A1234BC", profileType: ContactPersonProfileType = MARITAL, count: Int = 1) {
    dpsApi.verify(
      count = count,
      getRequestedFor(urlPathEqualTo("/sync/$prisonerNumber/${profileType.identifier}")),
    )
  }

  private fun verifyNomisPutProfileDetails(
    count: Int = 1,
    prisonerNumber: String = "A1234BC",
    profileType: StringValuePattern = equalTo("MARITAL"),
    profileCode: StringValuePattern = equalTo("M"),
  ) {
    nomisApi.verify(
      count = count,
      pattern = putRequestedFor(urlPathEqualTo("/prisoners/$prisonerNumber/profile-details"))
        .withRequestBody(matchingJsonPath("profileType", profileType))
        .withRequestBody(matchingJsonPath("profileCode", profileCode)),
    )
  }

  private fun verifyTelemetry(
    profileType: ContactPersonProfileType = MARITAL,
    telemetryType: String,
    offenderNo: String = "A1234BC",
    requestedDpsId: Long = 123,
    dpsId: Long? = 54321,
    bookingId: Long? = null,
    ignoreReason: String? = null,
    errorReason: String? = null,
  ) {
    verify(telemetryClient).trackEvent(
      eq("contact-person-${profileType.identifier}-$telemetryType"),
      check {
        assertThat(it["offenderNo"]).isEqualTo(offenderNo)
        assertThat(it["requestedDpsId"]).isEqualTo(requestedDpsId.toString())
        dpsId?.run { assertThat(it["dpsId"]).isEqualTo(dpsId.toString()) }
        bookingId?.run { assertThat(it["bookingId"]).isEqualTo(this.toString()) }
        ignoreReason?.run { assertThat(it["reason"]).isEqualTo(this) }
        errorReason?.run { assertThat(it["error"]).isEqualTo(this) }
      },
      isNull(),
    )
  }

  private fun publishDomainEvent(
    eventType: String,
    payload: String,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(payload)
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String").stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun waitForDlqMessage() = await untilCallTo {
    personalRelationshipsDlqClient!!.countAllMessagesOnQueue(personalRelationshipsDlqUrl!!).get()
  } matches { it == 1 }
}

fun domesticStatusCreatedEvent(
  prisonerNumber: String = "A1234BC",
  domesticStatusId: Long = 123,
  source: String = "DPS",
) = //language=JSON
  """
    {
      "eventType":"personal-relationships-api.domestic-status.created", 
      "additionalInformation": {
        "domesticStatusId": $domesticStatusId,
        "source": "$source"
      },
      "personReference": {
        "identifiers": [
          {
            "type": "NOMS",
            "value": "$prisonerNumber"
          }
        ]
      }
    }
    """

fun numberOfChildrenCreatedEvent(
  prisonerNumber: String = "A1234BC",
  prisonerNumberOfChildrenId: Long = 123,
  source: String = "DPS",
) = //language=JSON
  """
    {
      "eventType":"personal-relationships-api.number-of-children.created", 
      "additionalInformation": {
        "prisonerNumberOfChildrenId": $prisonerNumberOfChildrenId,
        "source": "$source"
      },
      "personReference": {
        "identifiers": [
          {
            "type": "NOMS",
            "value": "$prisonerNumber"
          }
        ]
      }
    }
    """

fun domesticStatusDeletedEvent(
  prisonerNumber: String = "A1234BC",
  domesticStatusId: Long = 123,
  source: String = "DPS",
) = //language=JSON
  """
    {
      "eventType":"personal-relationships-api.domestic-status.deleted", 
      "additionalInformation": {
        "domesticStatusId": $domesticStatusId,
        "source": "$source"
      },
      "personReference": {
        "identifiers": [
          {
            "type": "NOMS",
            "value": "$prisonerNumber"
          }
        ]
      }
    }
    """

fun numberOfChildrenDeletedEvent(
  prisonerNumber: String = "A1234BC",
  prisonerNumberOfChildrenId: Long = 123,
  source: String = "DPS",
) = //language=JSON
  """
    {
      "eventType":"personal-relationships-api.number-of-children.deleted", 
      "additionalInformation": {
        "prisonerNumberOfChildrenId": $prisonerNumberOfChildrenId,
        "source": "$source"
      },
      "personReference": {
        "identifiers": [
          {
            "type": "NOMS",
            "value": "$prisonerNumber"
          }
        ]
      }
    }
    """

fun readmissionSwitchedBookingEvent(
  prisonerNumber: String = "A1234BC",
  reason: String = "READMISSION_SWITCH_BOOKING",
) = //language=JSON
  """
    {
      "eventType":"prisoner-offender-search.prisoner.received", 
      "additionalInformation": {
        "nomsNumber": "$prisonerNumber",
        "reason": "$reason",
        "prisonId": "ANY"
      }
    }
    """
