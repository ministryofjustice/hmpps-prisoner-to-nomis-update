package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyEmailAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyPhoneNumber
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.createAgencyAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.createAgencyEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.createAgencyPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.hospitalAgencyResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.updateAgencyAddressesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.updateAgencyEmailAddressesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.updateAgencyPhoneNumbersResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyAddressDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyEmailDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyPhoneDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.model.LegacyAgencyType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyEmailAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyPhoneNumberRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyAddressesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyEmailAddressesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyPhoneNumber
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyPhoneNumbersRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.getRequestBody

class AgencyRegisterToNomisIntTest(
  @Autowired
  private val nomisApi: AgencyNomisApiMockServer,
) : SqsIntegrationTestBase() {
  private val dpsApi = AgencyRegistersDpsApiExtension.agencyRegistersApi

  @Nested
  @DisplayName("register.court.inserted")
  inner class CourtInserted {
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCourtInsertedEvent(courtId = courtId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-created-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
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
          dpsApi.stubGetAgency(agencyId = courtId, response = legacyAgencyDto())
          nomisApi.stubCreateAgency(response = agencyResponse().copy(agencyId = courtId))
          publishCourtInsertedEvent(courtId = courtId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create agency in NOMIS`() {
          val request: CreateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency")), jsonMapper)
          assertThat(request.agencyId).isEqualTo(courtId)
          assertThat(request.description).isEqualTo("Sheffield Crown Ct")
          assertThat(request.typeCode).isEqualTo("CRT")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Sheffield Crown Court")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.courtTypeCode).isEqualTo("CC")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-created-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["nomisAgencyId"]).isEqualTo(courtId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.amended")
  inner class CourtAmended {
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCourtAmendedEvent(courtId = courtId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-updated-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
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
          dpsApi.stubGetAgency(agencyId = courtId, response = legacyAgencyDto())
          nomisApi.stubUpdateAgency(agencyId = courtId, response = agencyResponse().copy(agencyId = courtId))
          publishCourtAmendedEvent(courtId = courtId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: UpdateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId")), jsonMapper)
          assertThat(request.description).isEqualTo("Sheffield Crown Ct")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Sheffield Crown Court")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.courtTypeCode).isEqualTo("CC")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-updated-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["nomisAgencyId"]).isEqualTo(courtId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.deleted")
  inner class CourtDeleted {
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishCourtDeletedEvent(courtId = courtId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will ignore the event`() {
        nomisApi.verify(0, deleteRequestedFor(urlEqualTo("/agency/$courtId")))
        verify(telemetryClient).trackEvent(
          eq("court-deleted-ignored"),
          check { assertThat(it["courtId"]).isEqualTo(courtId) },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @BeforeEach
      fun setUp() {
        nomisApi.stubDeleteAgency(agencyId = courtId)
        publishCourtDeletedEvent(courtId = courtId)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will delete agency in NOMIS`() {
        nomisApi.verify(deleteRequestedFor(urlEqualTo("/agency/$courtId")))
      }

      @Test
      fun `will send telemetry event showing success`() {
        verify(telemetryClient).trackEvent(
          eq("court-deleted-success"),
          check { assertThat(it["courtId"]).isEqualTo(courtId) },
          isNull(),
        )
      }
    }
  }

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
          eq("court-email-created-ignored"),
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
          dpsApi.stubGetAgency(courtId, legacyAgencyDto().copy(emailAddresses = listOf(legacyAgencyEmailDto().copy(id = dpsEmailId, address = "test@justice.gov.uk"))))
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
            eq("court-email-created-success"),
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
          eq("court-email-updated-ignored"),
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
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                legacyAgencyEmailDto().copy(id = index + 1L, address = address)
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
            eq("court-email-updated-success"),
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
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                legacyAgencyEmailDto().copy(id = index + 1L, address = address)
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

  @Nested
  @DisplayName("register.court.phone.inserted")
  inner class CourtPhoneInserted {
    val dpsPhoneId = 12345L
    val nomisPhoneId = 8765L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishCourtPhoneEvent("register.court.phone.inserted", courtId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-phone-created-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$courtId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$courtId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              phoneNumbers = listOf(legacyAgencyPhoneDto(), legacyAgencyPhoneDto().copy(id = dpsPhoneId, number = "0114 555 1234")),
            ),
          )
          nomisApi.stubCreateAgencyPhone(agencyId = courtId, response = createAgencyPhoneResponse().copy(id = nomisPhoneId))
          publishCourtPhoneEvent("register.court.phone.inserted", courtId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted phone in NOMIS`() {
          val request: CreateAgencyPhoneNumberRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$courtId/phones")), jsonMapper)
          assertThat(request).isEqualTo(CreateAgencyPhoneNumberRequest(number = "0114 555 1234", typeCode = "BUS"))
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$courtId/phones")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-phone-created-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.phone.amended")
  inner class CourtPhoneAmended {
    val dpsPhoneId = 12345L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishCourtPhoneEvent("register.court.phone.amended", courtId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-phone-updated-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$courtId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$courtId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = dpsPhoneId + index, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = courtId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishCourtPhoneEvent("register.court.phone.amended", courtId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-phone-updated-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.phone.deleted")
  inner class CourtPhoneDeleted {
    val dpsPhoneId = 12345L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishCourtPhoneEvent("register.court.phone.deleted", courtId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-phone-deleted-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$courtId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$courtId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = index + 1L, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = courtId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishCourtPhoneEvent("register.court.phone.deleted", courtId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-phone-deleted-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastPhoneDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = courtId, response = legacyAgencyDto().copy(phoneNumbers = emptyList()))
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = courtId,
            response = updateAgencyPhoneNumbersResponse().copy(phoneNumbers = emptyList()),
          )
          publishCourtPhoneEvent("register.court.phone.deleted", courtId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no phone ids`() {
          verify(telemetryClient).trackEvent(
            eq("court-phone-deleted-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.address.inserted")
  inner class CourtAddressInserted {
    val dpsAddressId = 12345L
    val nomisAddressId = 8765L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishCourtAddressEvent("register.court.address.inserted", courtId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-address-created-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$courtId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$courtId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        private val expectedAddress = CreateAgencyAddressRequest(
          street = "The Law Courts",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          nomisApi.stubCreateAgencyAddress(agencyId = courtId, response = createAgencyAddressResponse().copy(id = nomisAddressId))
        }

        private fun insertAddress(addressLine1: String?, addressLine2: String?) {
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "Another court address", addressLine2 = null, town = null, county = null, postcode = null, country = null),
                legacyAgencyAddressDto().copy(
                  id = dpsAddressId,
                  addressLine1 = addressLine1,
                  addressLine2 = addressLine2,
                  postcode = "S3 8PH",
                  town = "Sheffield",
                  county = "South Yorkshire",
                  country = "England",
                ),
              ),
            ),
          )
          publishCourtAddressEvent("register.court.address.inserted", courtId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        private fun assertNomisAddress(expected: CreateAgencyAddressRequest) {
          val request: CreateAgencyAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$courtId/addresses")), jsonMapper)
          assertThat(request).isEqualTo(expected)
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$courtId/addresses")))
        }

        @Nested
        inner class BothAddressLinesNull {
          @BeforeEach
          fun setUp() {
            insertAddress(addressLine1 = null, addressLine2 = null)
          }

          @Test
          fun `will create only the inserted address in NOMIS with no street or locality`() {
            assertNomisAddress(expectedAddress.copy(flat = null, premise = null, street = null, locality = null))
          }
        }

        @Nested
        inner class NeitherAddressLineContainsCommas {
          @BeforeEach
          fun setUp() {
            insertAddress(addressLine1 = "The Law Courts", addressLine2 = "50 West Bar")
          }

          @Test
          fun `will create only the inserted address in NOMIS with street and locality`() {
            assertNomisAddress(expectedAddress.copy(flat = null, premise = null, street = "The Law Courts", locality = "50 West Bar"))
          }

          @Test
          fun `will send telemetry event showing success`() {
            verify(telemetryClient).trackEvent(
              eq("court-address-created-success"),
              check {
                assertThat(it["courtId"]).isEqualTo(courtId)
                assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
                assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
              },
              isNull(),
            )
          }
        }

        @Nested
        inner class AddressLine1ContainsOneComma {
          @BeforeEach
          fun setUp() {
            insertAddress(addressLine1 = "The Law Courts, 50 West Bar", addressLine2 = "Sheffield City Centre")
          }

          @Test
          fun `will split address line 1 between premise and street`() {
            assertNomisAddress(expectedAddress.copy(flat = null, premise = "The Law Courts", street = "50 West Bar", locality = "Sheffield City Centre"))
          }
        }

        @Nested
        inner class AddressLine1ContainsTwoCommas {
          @BeforeEach
          fun setUp() {
            insertAddress(addressLine1 = "Floor 2, The Law Courts, 50 West Bar", addressLine2 = "Sheffield City Centre")
          }

          @Test
          fun `will split address line 1 between flat, premise and street`() {
            assertNomisAddress(expectedAddress.copy(flat = "Floor 2", premise = "The Law Courts", street = "50 West Bar", locality = "Sheffield City Centre"))
          }
        }

        @Nested
        inner class AddressLine1ContainsThreeCommas {
          @BeforeEach
          fun setUp() {
            insertAddress(addressLine1 = "Floor 2, The Law Courts, 50 West Bar, Justice Area", addressLine2 = "Sheffield City Centre")
          }

          @Test
          fun `will split address line 1 between flat, premise and street`() {
            assertNomisAddress(expectedAddress.copy(flat = "Floor 2", premise = "The Law Courts", street = "50 West Bar, Justice Area", locality = "Sheffield City Centre"))
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.address.amended")
  inner class CourtAddressAmended {
    val dpsAddressId = 12345L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishCourtAddressEvent("register.court.address.amended", courtId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-address-updated-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$courtId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$courtId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      @Nested
      inner class HappyPath {
        private val address = legacyAgencyAddressDto().copy(
          id = dpsAddressId,
          addressLine1 = "The Law Courts",
          addressLine2 = "50 West Bar",
          postcode = "S3 8PH",
          town = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )
        private val expectedAddress = UpdateAgencyAddress(
          street = "The Law Courts",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              addresses = listOf(
                address,
                address.copy(id = 2, addressLine1 = " The Law Courts , 50 West Bar "),
                address.copy(id = 3, addressLine1 = " Floor 2 , The Law Courts , 50 West Bar "),
                address.copy(id = 4, addressLine1 = "Floor 2, The Law Courts, 50 West Bar, Justice Area"),
                legacyAgencyAddressDto().copy(id = 5, addressLine1 = null, addressLine2 = null, town = null, county = null, postcode = null, country = null),
              ),
            ),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = courtId,
            response = updateAgencyAddressesResponse().copy(
              addresses = (1L..5L).map { agencyAddress().copy(id = it) },
            ),
          )
          publishCourtAddressEvent("register.court.address.amended", courtId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS addresses using the same mapping as creation`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(
            expectedAddress,
            expectedAddress.copy(premise = "The Law Courts", street = "50 West Bar"),
            expectedAddress.copy(flat = "Floor 2", premise = "The Law Courts", street = "50 West Bar"),
            expectedAddress.copy(flat = "Floor 2", premise = "The Law Courts", street = "50 West Bar, Justice Area"),
            UpdateAgencyAddress(),
          )
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-address-updated-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1, 2, 3, 4, 5")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.address.deleted")
  inner class CourtAddressDeleted {
    val dpsAddressId = 12345L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishCourtAddressEvent("register.court.address.deleted", courtId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-address-deleted-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$courtId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$courtId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "The Law Courts", addressLine2 = null, town = null, county = null, postcode = "S3 8PH", country = null),
                legacyAgencyAddressDto().copy(id = 2, addressLine1 = "Another court address", addressLine2 = null, town = null, county = null, postcode = "S1 2AB", country = null),
              ),
            ),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = courtId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1), agencyAddress().copy(id = 2))),
          )
          publishCourtAddressEvent("register.court.address.deleted", courtId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(
            UpdateAgencyAddress(street = "The Law Courts", postcode = "S3 8PH"),
            UpdateAgencyAddress(street = "Another court address", postcode = "S1 2AB"),
          )
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-address-deleted-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastAddressDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = courtId, response = legacyAgencyDto().copy(addresses = emptyList()))
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = courtId,
            response = updateAgencyAddressesResponse().copy(addresses = emptyList()),
          )
          publishCourtAddressEvent("register.court.address.deleted", courtId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/addresses")), jsonMapper)
          assertThat(request.addresses).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no address ids`() {
          verify(telemetryClient).trackEvent(
            eq("court-address-deleted-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.inserted")
  inner class HospitalInserted {
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishHospitalInsertedEvent(hospitalId = hospitalId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-created-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
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
          dpsApi.stubGetAgency(agencyId = hospitalId, response = hospitalLegacyAgencyDto())
          nomisApi.stubCreateAgency(response = hospitalAgencyResponse().copy(agencyId = hospitalId))
          publishHospitalInsertedEvent(hospitalId = hospitalId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create agency in NOMIS`() {
          val request: CreateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency")), jsonMapper)
          assertThat(request.agencyId).isEqualTo(hospitalId)
          assertThat(request.description).isEqualTo("Hallamshire Hospital")
          assertThat(request.typeCode).isEqualTo("HOSPITAL")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Hallamshire Hospital, Sheffield")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.courtTypeCode).isEqualTo("CC")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-created-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["nomisAgencyId"]).isEqualTo(hospitalId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.amended")
  inner class HospitalAmended {
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishHospitalAmendedEvent(hospitalId = hospitalId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-updated-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
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
          dpsApi.stubGetAgency(agencyId = hospitalId, response = hospitalLegacyAgencyDto())
          nomisApi.stubUpdateAgency(agencyId = hospitalId, response = hospitalAgencyResponse().copy(agencyId = hospitalId))
          publishHospitalAmendedEvent(hospitalId = hospitalId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: UpdateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId")), jsonMapper)
          assertThat(request.description).isEqualTo("Hallamshire Hospital")
          assertThat(request.typeCode).isEqualTo("HOSPITAL")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Hallamshire Hospital, Sheffield")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.courtTypeCode).isEqualTo("CC")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-updated-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["nomisAgencyId"]).isEqualTo(hospitalId)
            },
            isNull(),
          )
        }

        @Test
        fun `will update NOMIS when the agency type changes to HOSPITAL`() {
          dpsApi.stubGetAgency(agencyId = hospitalId, response = hospitalLegacyAgencyDto().copy(agencyType = LegacyAgencyType.HOSPITAL))
          publishHospitalAmendedEvent(hospitalId = hospitalId)
          waitForAnyProcessingToComplete(times = 2)

          val request: UpdateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId")), jsonMapper)
          assertThat(request.typeCode).isEqualTo("HOSPITAL")
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.deleted")
  inner class HospitalDeleted {
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishHospitalDeletedEvent(hospitalId = hospitalId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will ignore the event`() {
        nomisApi.verify(0, deleteRequestedFor(urlEqualTo("/agency/$hospitalId")))
        verify(telemetryClient).trackEvent(
          eq("hospital-deleted-ignored"),
          check { assertThat(it["hospitalId"]).isEqualTo(hospitalId) },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @BeforeEach
      fun setUp() {
        nomisApi.stubDeleteAgency(agencyId = hospitalId)
        publishHospitalDeletedEvent(hospitalId = hospitalId)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will delete agency in NOMIS`() {
        nomisApi.verify(deleteRequestedFor(urlEqualTo("/agency/$hospitalId")))
      }

      @Test
      fun `will send telemetry event showing success`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-deleted-success"),
          check { assertThat(it["hospitalId"]).isEqualTo(hospitalId) },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.phone.inserted")
  inner class HospitalPhoneInserted {
    val dpsPhoneId = 12345L
    val nomisPhoneId = 8765L
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishHospitalPhoneEvent("register.hospital.phone.inserted", hospitalId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-phone-created-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$hospitalId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$hospitalId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = hospitalId,
            response = hospitalLegacyAgencyDto().copy(
              phoneNumbers = listOf(legacyAgencyPhoneDto(), legacyAgencyPhoneDto().copy(id = dpsPhoneId, number = "0114 555 1234")),
            ),
          )
          nomisApi.stubCreateAgencyPhone(agencyId = hospitalId, response = createAgencyPhoneResponse().copy(id = nomisPhoneId))
          publishHospitalPhoneEvent("register.hospital.phone.inserted", hospitalId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted phone in NOMIS`() {
          val request: CreateAgencyPhoneNumberRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$hospitalId/phones")), jsonMapper)
          assertThat(request).isEqualTo(CreateAgencyPhoneNumberRequest(number = "0114 555 1234", typeCode = "BUS"))
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$hospitalId/phones")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-phone-created-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.phone.amended")
  inner class HospitalPhoneAmended {
    val dpsPhoneId = 12345L
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishHospitalPhoneEvent("register.hospital.phone.amended", hospitalId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-phone-updated-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$hospitalId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$hospitalId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = hospitalId,
            response = hospitalLegacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = dpsPhoneId + index, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = hospitalId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishHospitalPhoneEvent("register.hospital.phone.amended", hospitalId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-phone-updated-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.phone.deleted")
  inner class HospitalPhoneDeleted {
    val dpsPhoneId = 12345L
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishHospitalPhoneEvent("register.hospital.phone.deleted", hospitalId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-phone-deleted-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$hospitalId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$hospitalId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = hospitalId,
            response = hospitalLegacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = index + 1L, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = hospitalId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishHospitalPhoneEvent("register.hospital.phone.deleted", hospitalId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-phone-deleted-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastPhoneDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = hospitalId, response = hospitalLegacyAgencyDto().copy(phoneNumbers = emptyList()))
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = hospitalId,
            response = updateAgencyPhoneNumbersResponse().copy(phoneNumbers = emptyList()),
          )
          publishHospitalPhoneEvent("register.hospital.phone.deleted", hospitalId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no phone ids`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-phone-deleted-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.address.inserted")
  inner class HospitalAddressInserted {
    val dpsAddressId = 12345L
    val nomisAddressId = 8765L
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishHospitalAddressEvent("register.hospital.address.inserted", hospitalId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-address-created-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$hospitalId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        private val expectedAddress = CreateAgencyAddressRequest(
          street = "The Law Courts",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          nomisApi.stubCreateAgencyAddress(agencyId = hospitalId, response = createAgencyAddressResponse().copy(id = nomisAddressId))
          dpsApi.stubGetAgency(
            agencyId = hospitalId,
            response = hospitalLegacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "Another hospital address", addressLine2 = null, town = null, county = null, postcode = null, country = null),
                legacyAgencyAddressDto().copy(
                  id = dpsAddressId,
                  addressLine1 = "The Law Courts",
                  addressLine2 = "50 West Bar",
                  postcode = "S3 8PH",
                  town = "Sheffield",
                  county = "South Yorkshire",
                  country = "England",
                ),
              ),
            ),
          )
          publishHospitalAddressEvent("register.hospital.address.inserted", hospitalId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted address in NOMIS`() {
          val request: CreateAgencyAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")), jsonMapper)
          assertThat(request).isEqualTo(expectedAddress)
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-address-created-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.address.amended")
  inner class HospitalAddressAmended {
    val dpsAddressId = 12345L
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishHospitalAddressEvent("register.hospital.address.amended", hospitalId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-address-updated-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$hospitalId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      @Nested
      inner class HappyPath {
        private val address = legacyAgencyAddressDto().copy(
          id = dpsAddressId,
          addressLine1 = "The Law Courts",
          addressLine2 = "50 West Bar",
          postcode = "S3 8PH",
          town = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )
        private val expectedAddress = UpdateAgencyAddress(
          street = "The Law Courts",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = hospitalId,
            response = hospitalLegacyAgencyDto().copy(addresses = listOf(address)),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = hospitalId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1))),
          )
          publishHospitalAddressEvent("register.hospital.address.amended", hospitalId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS addresses using the same mapping as creation`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(expectedAddress)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-address-updated-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.address.deleted")
  inner class HospitalAddressDeleted {
    val dpsAddressId = 12345L
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishHospitalAddressEvent("register.hospital.address.deleted", hospitalId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-address-deleted-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$hospitalId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = hospitalId,
            response = hospitalLegacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "The Law Courts", addressLine2 = null, town = null, county = null, postcode = "S3 8PH", country = null),
                legacyAgencyAddressDto().copy(id = 2, addressLine1 = "Another hospital address", addressLine2 = null, town = null, county = null, postcode = "S1 2AB", country = null),
              ),
            ),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = hospitalId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1), agencyAddress().copy(id = 2))),
          )
          publishHospitalAddressEvent("register.hospital.address.deleted", hospitalId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(
            UpdateAgencyAddress(street = "The Law Courts", postcode = "S3 8PH"),
            UpdateAgencyAddress(street = "Another hospital address", postcode = "S1 2AB"),
          )
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-address-deleted-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastAddressDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = hospitalId, response = hospitalLegacyAgencyDto().copy(addresses = emptyList()))
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = hospitalId,
            response = updateAgencyAddressesResponse().copy(addresses = emptyList()),
          )
          publishHospitalAddressEvent("register.hospital.address.deleted", hospitalId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")), jsonMapper)
          assertThat(request.addresses).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no address ids`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-address-deleted-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.inserted")
  inner class PoliceCustodySuiteInserted {
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteInsertedEvent(policeCustodySuiteId = policeCustodySuiteId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-created-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
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
          dpsApi.stubGetAgency(agencyId = policeCustodySuiteId, response = policeCustodySuiteLegacyAgencyDto())
          nomisApi.stubCreateAgency(response = agencyResponse().copy(agencyId = policeCustodySuiteId))
          publishPoliceCustodySuiteInsertedEvent(policeCustodySuiteId = policeCustodySuiteId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create agency in NOMIS`() {
          val request: CreateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency")), jsonMapper)
          assertThat(request.agencyId).isEqualTo(policeCustodySuiteId)
          assertThat(request.description).isEqualTo("Sheffield Custody Suite")
          assertThat(request.typeCode).isEqualTo("POLICE")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Sheffield Custody Suite, Sheffield")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-created-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["nomisAgencyId"]).isEqualTo(policeCustodySuiteId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.amended")
  inner class PoliceCustodySuiteAmended {
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteAmendedEvent(policeCustodySuiteId = policeCustodySuiteId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-updated-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
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
          dpsApi.stubGetAgency(agencyId = policeCustodySuiteId, response = policeCustodySuiteLegacyAgencyDto())
          nomisApi.stubUpdateAgency(agencyId = policeCustodySuiteId, response = agencyResponse().copy(agencyId = policeCustodySuiteId))
          publishPoliceCustodySuiteAmendedEvent(policeCustodySuiteId = policeCustodySuiteId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: UpdateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId")), jsonMapper)
          assertThat(request.description).isEqualTo("Sheffield Custody Suite")
          assertThat(request.typeCode).isEqualTo("POLICE")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Sheffield Custody Suite, Sheffield")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-updated-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["nomisAgencyId"]).isEqualTo(policeCustodySuiteId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.deleted")
  inner class PoliceCustodySuiteDeleted {
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteDeletedEvent(policeCustodySuiteId = policeCustodySuiteId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will ignore the event`() {
        nomisApi.verify(0, deleteRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId")))
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-deleted-ignored"),
          check { assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId) },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @BeforeEach
      fun setUp() {
        nomisApi.stubDeleteAgency(agencyId = policeCustodySuiteId)
        publishPoliceCustodySuiteDeletedEvent(policeCustodySuiteId = policeCustodySuiteId)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will delete agency in NOMIS`() {
        nomisApi.verify(deleteRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId")))
      }

      @Test
      fun `will send telemetry event showing success`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-deleted-success"),
          check { assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId) },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.email.inserted")
  inner class PoliceCustodySuiteEmailInserted {
    val dpsEmailId = 12345L
    val nomisEmailId = 8765L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteEmailInsertedEvent(emailId = dpsEmailId, policeCustodySuiteId = policeCustodySuiteId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-email-created-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
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
          dpsApi.stubGetAgency(
            policeCustodySuiteId,
            policeCustodySuiteLegacyAgencyDto().copy(emailAddresses = listOf(legacyAgencyEmailDto().copy(id = dpsEmailId, address = "test@justice.gov.uk"))),
          )
          nomisApi.stubCreateAgencyEmail(agencyId = policeCustodySuiteId, response = createAgencyEmailResponse().copy(id = nomisEmailId))
          publishPoliceCustodySuiteEmailInsertedEvent(emailId = dpsEmailId, policeCustodySuiteId = policeCustodySuiteId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: CreateAgencyEmailAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/emails")), jsonMapper)
          assertThat(request.emailAddress).isEqualTo("test@justice.gov.uk")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-email-created-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
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
  @DisplayName("register.policecustodysuite.email.amended")
  inner class PoliceCustodySuiteEmailAmended {
    val dpsEmailId = 12345L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteEmailAmendedEvent(emailId = dpsEmailId, policeCustodySuiteId = policeCustodySuiteId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-email-updated-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      private val emails = listOf("custody.one@justice.gov.uk", "custody.two@justice.gov.uk")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                legacyAgencyEmailDto().copy(id = index + 1L, address = address)
              },
            ),
          )
          nomisApi.stubUpdateAgencyEmailAddresses(
            agencyId = policeCustodySuiteId,
            response = updateAgencyEmailAddressesResponse().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailAddress().copy(id = index + 1L, emailAddress = address)
              },
            ),
          )
          publishPoliceCustodySuiteEmailAmendedEvent(emailId = dpsEmailId, policeCustodySuiteId = policeCustodySuiteId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS email addresses`() {
          val request: UpdateAgencyEmailAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/emails")), jsonMapper)
          assertThat(request.emailAddresses).containsExactlyElementsOf(emails)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-email-updated-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
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
  @DisplayName("register.policecustodysuite.email.deleted")
  inner class PoliceCustodySuiteEmailDeleted {
    val dpsEmailId = 12345L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteEmailDeletedEvent(emailId = dpsEmailId, policeCustodySuiteId = policeCustodySuiteId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-email-deleted-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      private val emails = listOf("custody.one@justice.gov.uk", "custody.two@justice.gov.uk")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                legacyAgencyEmailDto().copy(id = index + 1L, address = address)
              },
            ),
          )
          nomisApi.stubUpdateAgencyEmailAddresses(
            agencyId = policeCustodySuiteId,
            response = updateAgencyEmailAddressesResponse().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailAddress().copy(id = index + 1L, emailAddress = address)
              },
            ),
          )
          publishPoliceCustodySuiteEmailDeletedEvent(emailId = dpsEmailId, policeCustodySuiteId = policeCustodySuiteId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS email addresses`() {
          val request: UpdateAgencyEmailAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/emails")), jsonMapper)
          assertThat(request.emailAddresses).containsExactlyElementsOf(emails)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-email-deleted-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
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
  @DisplayName("register.policecustodysuite.phone.inserted")
  inner class PoliceCustodySuitePhoneInserted {
    val dpsPhoneId = 12345L
    val nomisPhoneId = 8765L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuitePhoneEvent("register.policecustodysuite.phone.inserted", policeCustodySuiteId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-phone-created-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$policeCustodySuiteId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(
              phoneNumbers = listOf(legacyAgencyPhoneDto(), legacyAgencyPhoneDto().copy(id = dpsPhoneId, number = "0114 555 1234")),
            ),
          )
          nomisApi.stubCreateAgencyPhone(agencyId = policeCustodySuiteId, response = createAgencyPhoneResponse().copy(id = nomisPhoneId))
          publishPoliceCustodySuitePhoneEvent("register.policecustodysuite.phone.inserted", policeCustodySuiteId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted phone in NOMIS`() {
          val request: CreateAgencyPhoneNumberRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")), jsonMapper)
          assertThat(request).isEqualTo(CreateAgencyPhoneNumberRequest(number = "0114 555 1234", typeCode = "BUS"))
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-phone-created-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.phone.amended")
  inner class PoliceCustodySuitePhoneAmended {
    val dpsPhoneId = 12345L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuitePhoneEvent("register.policecustodysuite.phone.amended", policeCustodySuiteId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-phone-updated-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$policeCustodySuiteId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = dpsPhoneId + index, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = policeCustodySuiteId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishPoliceCustodySuitePhoneEvent("register.policecustodysuite.phone.amended", policeCustodySuiteId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-phone-updated-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.phone.deleted")
  inner class PoliceCustodySuitePhoneDeleted {
    val dpsPhoneId = 12345L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuitePhoneEvent("register.policecustodysuite.phone.deleted", policeCustodySuiteId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-phone-deleted-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$policeCustodySuiteId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = index + 1L, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = policeCustodySuiteId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishPoliceCustodySuitePhoneEvent("register.policecustodysuite.phone.deleted", policeCustodySuiteId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-phone-deleted-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastPhoneDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = policeCustodySuiteId, response = policeCustodySuiteLegacyAgencyDto().copy(phoneNumbers = emptyList()))
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = policeCustodySuiteId,
            response = updateAgencyPhoneNumbersResponse().copy(phoneNumbers = emptyList()),
          )
          publishPoliceCustodySuitePhoneEvent("register.policecustodysuite.phone.deleted", policeCustodySuiteId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no phone ids`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-phone-deleted-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.address.inserted")
  inner class PoliceCustodySuiteAddressInserted {
    val dpsAddressId = 12345L
    val nomisAddressId = 8765L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteAddressEvent("register.policecustodysuite.address.inserted", policeCustodySuiteId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-address-created-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$policeCustodySuiteId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        private val expectedAddress = CreateAgencyAddressRequest(
          street = "The Custody Suite",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          nomisApi.stubCreateAgencyAddress(agencyId = policeCustodySuiteId, response = createAgencyAddressResponse().copy(id = nomisAddressId))
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "Another custody suite address", addressLine2 = null, town = null, county = null, postcode = null, country = null),
                legacyAgencyAddressDto().copy(
                  id = dpsAddressId,
                  addressLine1 = "The Custody Suite",
                  addressLine2 = "50 West Bar",
                  postcode = "S3 8PH",
                  town = "Sheffield",
                  county = "South Yorkshire",
                  country = "England",
                ),
              ),
            ),
          )
          publishPoliceCustodySuiteAddressEvent("register.policecustodysuite.address.inserted", policeCustodySuiteId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted address in NOMIS`() {
          val request: CreateAgencyAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")), jsonMapper)
          assertThat(request).isEqualTo(expectedAddress)
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-address-created-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.address.amended")
  inner class PoliceCustodySuiteAddressAmended {
    val dpsAddressId = 12345L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteAddressEvent("register.policecustodysuite.address.amended", policeCustodySuiteId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-address-updated-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$policeCustodySuiteId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      @Nested
      inner class HappyPath {
        private val address = legacyAgencyAddressDto().copy(
          id = dpsAddressId,
          addressLine1 = "The Custody Suite",
          addressLine2 = "50 West Bar",
          postcode = "S3 8PH",
          town = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )
        private val expectedAddress = UpdateAgencyAddress(
          street = "The Custody Suite",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(addresses = listOf(address)),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = policeCustodySuiteId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1))),
          )
          publishPoliceCustodySuiteAddressEvent("register.policecustodysuite.address.amended", policeCustodySuiteId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS addresses using the same mapping as creation`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(expectedAddress)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-address-updated-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.address.deleted")
  inner class PoliceCustodySuiteAddressDeleted {
    val dpsAddressId = 12345L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteAddressEvent("register.policecustodysuite.address.deleted", policeCustodySuiteId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-address-deleted-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$policeCustodySuiteId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "The Custody Suite", addressLine2 = null, town = null, county = null, postcode = "S3 8PH", country = null),
                legacyAgencyAddressDto().copy(id = 2, addressLine1 = "Another custody suite address", addressLine2 = null, town = null, county = null, postcode = "S1 2AB", country = null),
              ),
            ),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = policeCustodySuiteId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1), agencyAddress().copy(id = 2))),
          )
          publishPoliceCustodySuiteAddressEvent("register.policecustodysuite.address.deleted", policeCustodySuiteId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(
            UpdateAgencyAddress(street = "The Custody Suite", postcode = "S3 8PH"),
            UpdateAgencyAddress(street = "Another custody suite address", postcode = "S1 2AB"),
          )
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-address-deleted-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastAddressDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = policeCustodySuiteId, response = policeCustodySuiteLegacyAgencyDto().copy(addresses = emptyList()))
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = policeCustodySuiteId,
            response = updateAgencyAddressesResponse().copy(addresses = emptyList()),
          )
          publishPoliceCustodySuiteAddressEvent("register.policecustodysuite.address.deleted", policeCustodySuiteId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")), jsonMapper)
          assertThat(request.addresses).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no address ids`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-address-deleted-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun hospitalLegacyAgencyDto() = legacyAgencyDto().copy(
    agencyType = LegacyAgencyType.HOSPITAL,
    name = "Hallamshire Hospital",
    description = "Hallamshire Hospital, Sheffield",
  )

  private fun policeCustodySuiteLegacyAgencyDto() = legacyAgencyDto().copy(
    agencyType = LegacyAgencyType.POLICE_CUSTODY_SUITE,
    name = "Sheffield Custody Suite",
    description = "Sheffield Custody Suite, Sheffield",
    courtTypeCode = null,
  )

  private fun publishCourtAddressEvent(
    eventType: String,
    courtId: String,
    addressId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = CourtAddressEvent(
        eventType = eventType,
        additionalInformation = CourtAddressAdditionalInformation(courtId = courtId, addressId = addressId, source = source),
      ),
    )
  }

  private fun publishCourtPhoneEvent(
    eventType: String,
    courtId: String,
    phoneId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = CourtPhoneEvent(
        eventType = eventType,
        additionalInformation = CourtPhoneAdditionalInformation(courtId = courtId, phoneId = phoneId, source = source),
      ),
    )
  }

  private fun publishCourtInsertedEvent(
    courtId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.court.inserted",
      payload = CourtEvent(
        eventType = "register.court.inserted",
        additionalInformation = CourtAdditionalInformation(
          courtId = courtId,
          source = source,
        ),
      ),
    )
  }

  private fun publishCourtAmendedEvent(
    courtId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.court.amended",
      payload = CourtEvent(
        eventType = "register.court.amended",
        additionalInformation = CourtAdditionalInformation(
          courtId = courtId,
          source = source,
        ),
      ),
    )
  }

  private fun publishCourtDeletedEvent(
    courtId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.court.deleted",
      payload = CourtEvent(
        eventType = "register.court.deleted",
        additionalInformation = CourtAdditionalInformation(courtId = courtId, source = source),
      ),
    )
  }

  private fun publishHospitalAddressEvent(
    eventType: String,
    hospitalId: String,
    addressId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = HospitalAddressEvent(
        eventType = eventType,
        additionalInformation = HospitalAddressAdditionalInformation(hospitalId = hospitalId, addressId = addressId, source = source),
      ),
    )
  }

  private fun publishHospitalPhoneEvent(
    eventType: String,
    hospitalId: String,
    phoneId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = HospitalPhoneEvent(
        eventType = eventType,
        additionalInformation = HospitalPhoneAdditionalInformation(hospitalId = hospitalId, phoneId = phoneId, source = source),
      ),
    )
  }

  private fun publishHospitalInsertedEvent(
    hospitalId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.hospital.inserted",
      payload = HospitalEvent(
        eventType = "register.hospital.inserted",
        additionalInformation = HospitalAdditionalInformation(
          hospitalId = hospitalId,
          source = source,
        ),
      ),
    )
  }

  private fun publishHospitalAmendedEvent(
    hospitalId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.hospital.amended",
      payload = HospitalEvent(
        eventType = "register.hospital.amended",
        additionalInformation = HospitalAdditionalInformation(
          hospitalId = hospitalId,
          source = source,
        ),
      ),
    )
  }

  private fun publishHospitalDeletedEvent(
    hospitalId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.hospital.deleted",
      payload = HospitalEvent(
        eventType = "register.hospital.deleted",
        additionalInformation = HospitalAdditionalInformation(hospitalId = hospitalId, source = source),
      ),
    )
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

  private fun publishPoliceCustodySuiteAddressEvent(
    eventType: String,
    policeCustodySuiteId: String,
    addressId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = PoliceCustodySuiteAddressEvent(
        eventType = eventType,
        additionalInformation = PoliceCustodySuiteAddressAdditionalInformation(policeCustodySuiteId = policeCustodySuiteId, addressId = addressId, source = source),
      ),
    )
  }

  private fun publishPoliceCustodySuitePhoneEvent(
    eventType: String,
    policeCustodySuiteId: String,
    phoneId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = PoliceCustodySuitePhoneEvent(
        eventType = eventType,
        additionalInformation = PoliceCustodySuitePhoneAdditionalInformation(policeCustodySuiteId = policeCustodySuiteId, phoneId = phoneId, source = source),
      ),
    )
  }

  private fun publishPoliceCustodySuiteInsertedEvent(
    policeCustodySuiteId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.policecustodysuite.inserted",
      payload = PoliceCustodySuiteEvent(
        eventType = "register.policecustodysuite.inserted",
        additionalInformation = PoliceCustodySuiteAdditionalInformation(
          policeCustodySuiteId = policeCustodySuiteId,
          source = source,
        ),
      ),
    )
  }

  private fun publishPoliceCustodySuiteAmendedEvent(
    policeCustodySuiteId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.policecustodysuite.amended",
      payload = PoliceCustodySuiteEvent(
        eventType = "register.policecustodysuite.amended",
        additionalInformation = PoliceCustodySuiteAdditionalInformation(
          policeCustodySuiteId = policeCustodySuiteId,
          source = source,
        ),
      ),
    )
  }

  private fun publishPoliceCustodySuiteDeletedEvent(
    policeCustodySuiteId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.policecustodysuite.deleted",
      payload = PoliceCustodySuiteEvent(
        eventType = "register.policecustodysuite.deleted",
        additionalInformation = PoliceCustodySuiteAdditionalInformation(policeCustodySuiteId = policeCustodySuiteId, source = source),
      ),
    )
  }

  private fun publishPoliceCustodySuiteEmailInsertedEvent(
    policeCustodySuiteId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.policecustodysuite.email.inserted") {
      publishDomainEvent(
        eventType = this,
        payload = PoliceCustodySuiteEmailEvent(
          eventType = this,
          additionalInformation = PoliceCustodySuiteEmailAdditionalInformation(
            policeCustodySuiteId = policeCustodySuiteId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishPoliceCustodySuiteEmailAmendedEvent(
    policeCustodySuiteId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.policecustodysuite.email.amended") {
      publishDomainEvent(
        eventType = this,
        payload = PoliceCustodySuiteEmailEvent(
          eventType = this,
          additionalInformation = PoliceCustodySuiteEmailAdditionalInformation(
            policeCustodySuiteId = policeCustodySuiteId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishPoliceCustodySuiteEmailDeletedEvent(
    policeCustodySuiteId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.policecustodysuite.email.deleted") {
      publishDomainEvent(
        eventType = this,
        payload = PoliceCustodySuiteEmailEvent(
          eventType = this,
          additionalInformation = PoliceCustodySuiteEmailAdditionalInformation(
            policeCustodySuiteId = policeCustodySuiteId,
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
