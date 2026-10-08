package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyEmailAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.createAgencyEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.updateAgencyEmailAddressesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.agencyEmailDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.courtDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyEmailAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyEmailAddressesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.getRequestBody

class AgencyRegisterToNomisIntTest(
  @Autowired
  private val nomisApi: AgencyNomisApiMockServer,
) : SqsIntegrationTestBase() {
  private val dpsApi = AgencyRegistersDpsApiExtension.agencyRegistersApi

  @Nested
  @DisplayName("register.court.email.inserted")
  inner class CourtEmailInserted {
    val dpsEmailId = 12345L
    val nomisEmailId = 8765L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCourtEmailInsertedEvent(emailId = dpsEmailId, courtId = courtId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-email-create-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetCourt(courtId = courtId, response = courtDto().copy(emailAddresses = listOf(agencyEmailDto().copy(id = dpsEmailId, address = "test@justice.gov.uk"))))
          nomisApi.stubCreateAgencyEmail(agencyId = courtId, response = createAgencyEmailResponse().copy(id = nomisEmailId))
          publishCourtEmailInsertedEvent(emailId = dpsEmailId, courtId = courtId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: CreateAgencyEmailAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$courtId/emails")), jsonMapper)
          assertThat(request.emailAddress).isEqualTo("test@justice.gov.uk")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-email-create-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailId"]).isEqualTo(nomisEmailId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.email.amended")
  inner class CourtEmailAmended {
    val dpsEmailId = 12345L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishCourtEmailAmendedEvent(emailId = dpsEmailId, courtId = courtId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-email-amended-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      private val emails = listOf("court.one@justice.gov.uk", "court.two@justice.gov.uk")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetCourt(
            courtId = courtId,
            response = courtDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailDto().copy(id = index + 1L, address = address)
              },
            ),
          )
          nomisApi.stubUpdateAgencyEmailAddresses(
            agencyId = courtId,
            response = updateAgencyEmailAddressesResponse().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailAddress().copy(id = index + 1L, emailAddress = address)
              },
            ),
          )
          publishCourtEmailAmendedEvent(emailId = dpsEmailId, courtId = courtId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS email addresses`() {
          val request: UpdateAgencyEmailAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/emails")), jsonMapper)
          assertThat(request.emailAddresses).containsExactlyElementsOf(emails)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-email-amended-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.email.deleted")
  inner class CourtEmailDeleted {
    val dpsEmailId = 12345L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishCourtEmailDeletedEvent(emailId = dpsEmailId, courtId = courtId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-email-deleted-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      private val emails = listOf("court.one@justice.gov.uk", "court.two@justice.gov.uk")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetCourt(
            courtId = courtId,
            response = courtDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailDto().copy(id = index + 1L, address = address)
              },
            ),
          )
          nomisApi.stubUpdateAgencyEmailAddresses(
            agencyId = courtId,
            response = updateAgencyEmailAddressesResponse().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailAddress().copy(id = index + 1L, emailAddress = address)
              },
            ),
          )
          publishCourtEmailDeletedEvent(emailId = dpsEmailId, courtId = courtId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS email addresses`() {
          val request: UpdateAgencyEmailAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/emails")), jsonMapper)
          assertThat(request.emailAddresses).containsExactlyElementsOf(emails)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-email-deleted-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun publishCourtEmailInsertedEvent(
    courtId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.court.email.inserted") {
      publishDomainEvent(
        eventType = this,
        payload = CourtEmailEvent(
          eventType = this,
          additionalInformation = CourtEmailAdditionalInformation(
            courtId = courtId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishCourtEmailAmendedEvent(
    courtId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.court.email.amended") {
      publishDomainEvent(
        eventType = this,
        payload = CourtEmailEvent(
          eventType = this,
          additionalInformation = CourtEmailAdditionalInformation(
            courtId = courtId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishCourtEmailDeletedEvent(
    courtId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.court.email.deleted") {
      publishDomainEvent(
        eventType = this,
        payload = CourtEmailEvent(
          eventType = this,
          additionalInformation = CourtEmailAdditionalInformation(
            courtId = courtId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishDomainEvent(
    eventType: String,
    payload: Any,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(jsonMapper.writeValueAsString(payload))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }
}
