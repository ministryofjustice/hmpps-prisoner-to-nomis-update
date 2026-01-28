package uk.gov.justice.digital.hmpps.prisonertonomisupdate.appointments

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AppointmentsApiExtension.Companion.appointmentsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

const val APPOINTMENT_INSTANCE_ID = 1234567L
const val BOOKING_ID = 987651L
const val APPOINTMENT_NOMIS_LOCATION_ID = 987651L
const val EVENT_ID = 111222333L
const val APPOINTMENT_DPS_LOCATION_ID = "17f5a650-f82b-444d-aed3-aef1719cfa8f"
internal val appointmentLocationMappingResponse = """
    {
      "dpsLocationId": "$APPOINTMENT_DPS_LOCATION_ID",
      "nomisLocationId": $APPOINTMENT_NOMIS_LOCATION_ID,
      "mappingType": "LOCATION_CREATED"
    }
""".trimIndent()
class AppointmentsToNomisIntTest : SqsIntegrationTestBase() {

  private val appointmentResponse = """{
      "id": $APPOINTMENT_INSTANCE_ID,
      "appointmentSeriesId": 1234,
      "appointmentId": 1234,
      "appointmentAttendeeId": 1234,
      "appointmentType": "INDIVIDUAL",
      "bookingId": $BOOKING_ID,
      "internalLocationId": $APPOINTMENT_NOMIS_LOCATION_ID,
      "dpsLocationId": "$APPOINTMENT_DPS_LOCATION_ID",
      "appointmentDate": "2023-03-14",
      "startTime": "10:15",
      "endTime":  "11:42",
      "categoryCode": "MEDI",
      "prisonCode": "SKI",
      "inCell": false,
      "prisonerNumber": "A1234BC",
      "cancelled": false,
      "extraInformation": "Sensitive notes",
      "prisonerExtraInformation": "Some comment",
      "createdTime": "2021-03-14T10:15:00",
      "createdBy": "user1"
    }
  """.trimIndent()

  private val mappingResponse = """{
      "appointmentInstanceId": $APPOINTMENT_INSTANCE_ID,
      "nomisEventId": $EVENT_ID
    }
  """.trimIndent()

  @Nested
  inner class CreateAppointment {
    @Nested
    inner class WhenAppointmentHasJustBeenCreatedByAppointmentService {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
        mappingServer.stubGetMappingGivenDpsLocationId(APPOINTMENT_DPS_LOCATION_ID, appointmentLocationMappingResponse)
        mappingServer.stubCreateAppointment()
      }

      @Nested
      inner class WhenAppointmentIsProvided {

        @BeforeEach
        fun setUp() {
          nomisApi.stubAppointmentCreate("""{ "eventId": $EVENT_ID }""")
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)
          publishAppointmentEvent("appointments.appointment-instance.created")
        }

        @Test
        fun `will callback back to appointment service to get more details`() {
          await untilAsserted {
            appointmentsApi.verify(getRequestedFor(urlEqualTo("/appointment-instances/$APPOINTMENT_INSTANCE_ID")))
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will get the NOMIS location`() {
          await untilAsserted {
            mappingServer.verify(getRequestedFor(urlEqualTo("/mapping/locations/dps/$APPOINTMENT_DPS_LOCATION_ID")))
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create an appointment in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(
              postRequestedFor(urlEqualTo("/appointments"))
                .withRequestBody(matchingJsonPath("bookingId", equalTo("$BOOKING_ID")))
                .withRequestBody(matchingJsonPath("internalLocationId", equalTo("$APPOINTMENT_NOMIS_LOCATION_ID")))
                .withRequestBody(matchingJsonPath("eventDate", equalTo("2023-03-14")))
                .withRequestBody(matchingJsonPath("startTime", equalTo("10:15")))
                .withRequestBody(matchingJsonPath("endTime", equalTo("11:42")))
                .withRequestBody(matchingJsonPath("eventSubType", equalTo("MEDI")))
                .withRequestBody(matchingJsonPath("comment", equalTo("Some comment"))),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create a mapping between the two appointments`() {
          await untilAsserted {
            mappingServer.verify(
              postRequestedFor(urlEqualTo("/mapping/appointments"))
                .withRequestBody(matchingJsonPath("nomisEventId", equalTo(EVENT_ID.toString())))
                .withRequestBody(
                  matchingJsonPath(
                    "appointmentInstanceId",
                    equalTo(APPOINTMENT_INSTANCE_ID.toString()),
                  ),
                ),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create success telemetry`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-success"),
              check {
                assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["locationId"]).isEqualTo(APPOINTMENT_NOMIS_LOCATION_ID.toString())
                assertThat(it["date"]).isEqualTo("2023-03-14")
                assertThat(it["start"]).isEqualTo("10:15")
                assertThat(it["nomisEventId"]).isEqualTo(EVENT_ID.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreated {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
      }

      @Nested
      inner class WhenAppointmentIsProvided {

        @BeforeEach
        fun setUp() {
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)

          publishAppointmentEvent("appointments.appointment-instance.created")
        }

        @Test
        fun `will callback back to mapping service to check message has not already been processed`() {
          await untilAsserted {
            mappingServer.verify(
              getRequestedFor(urlEqualTo("/mapping/appointments/appointment-instance-id/$APPOINTMENT_INSTANCE_ID")),
            )
          }
        }

        @Test
        fun `will not create an appointment in NOMIS`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-duplicate"),
              any(),
              isNull(),
            )
          }
          nomisApi.verify(0, postRequestedFor(urlEqualTo("/appointments")))
        }
      }
    }

    @Nested
    inner class ExceptionHandling {

      @Nested
      inner class WhenAppointmentServiceFailsOnce {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          mappingServer.stubGetMappingGivenDpsLocationId(APPOINTMENT_DPS_LOCATION_ID, appointmentLocationMappingResponse)
          mappingServer.stubCreateAppointment()
          nomisApi.stubAppointmentCreate("""{ "eventId": $EVENT_ID }""")
          appointmentsApi.stubGetAppointmentInstanceWithErrorFollowedBySlowSuccess(
            id = APPOINTMENT_INSTANCE_ID,
            response = appointmentResponse,
          )
          publishAppointmentEvent("appointments.appointment-instance.created")
        }

        @Test
        fun `will callback back to appointment service twice to get more details`() {
          await untilAsserted {
            appointmentsApi.verify(2, getRequestedFor(urlEqualTo("/appointment-instances/$APPOINTMENT_INSTANCE_ID")))
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually create an appointment in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(
              1,
              postRequestedFor(urlEqualTo("/appointments"))
                .withRequestBody(matchingJsonPath("bookingId", equalTo("$BOOKING_ID")))
                .withRequestBody(matchingJsonPath("internalLocationId", equalTo("$APPOINTMENT_NOMIS_LOCATION_ID")))
                .withRequestBody(matchingJsonPath("eventDate", equalTo("2023-03-14")))
                .withRequestBody(matchingJsonPath("startTime", equalTo("10:15")))
                .withRequestBody(matchingJsonPath("endTime", equalTo("11:42")))
                .withRequestBody(matchingJsonPath("eventSubType", equalTo("MEDI"))),
            )
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-success"),
              any(),
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenAppointmentServiceKeepsFailing {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          appointmentsApi.stubGetAppointmentInstanceWithError(
            APPOINTMENT_INSTANCE_ID,
            503,
          )
          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 0 }

          publishAppointmentEvent("appointments.appointment-instance.created")
        }

        @Test
        fun `will callback back to appointment service 3 times before given up`() {
          await untilAsserted {
            appointmentsApi.verify(3, getRequestedFor(urlEqualTo("/appointment-instances/$APPOINTMENT_INSTANCE_ID")))
          }
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 1 }
        }
      }

      @Nested
      inner class WhenNomisServiceFailsOnce {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          mappingServer.stubGetMappingGivenDpsLocationId(APPOINTMENT_DPS_LOCATION_ID, appointmentLocationMappingResponse)
          mappingServer.stubCreateAppointment()
          nomisApi.stubAppointmentCreateWithErrorFollowedBySlowSuccess("""{ "eventId": $EVENT_ID }""")
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)

          publishAppointmentEvent("appointments.appointment-instance.created")
        }

        @Test
        fun `will callback back to appointment api and NOMIS service twice`() {
          await untilAsserted {
            appointmentsApi.verify(2, getRequestedFor(urlEqualTo("/appointment-instances/$APPOINTMENT_INSTANCE_ID")))
          }
          await untilAsserted {
            nomisApi.verify(2, postRequestedFor(urlEqualTo("/appointments")))
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually create a mapping after NOMIS appointment is created`() {
          await untilAsserted {
            mappingServer.verify(postRequestedFor(urlEqualTo("/mapping/appointments")))
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-success"),
              any(),
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenNomisServiceKeepsFailing {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          mappingServer.stubGetMappingGivenDpsLocationId(APPOINTMENT_DPS_LOCATION_ID, appointmentLocationMappingResponse)
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)

          nomisApi.stubAppointmentCreateWithError(503)
          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 0 }

          publishAppointmentEvent("appointments.appointment-instance.created")
        }

        @Test
        fun `will callback back to appointment service 3 times before given up`() {
          await untilAsserted {
            nomisApi.verify(3, postRequestedFor(urlEqualTo("/appointments")))
          }
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 1 }
        }
      }

      @Nested
      inner class WhenMappingServiceFailsOnce {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          mappingServer.stubGetMappingGivenDpsLocationId(APPOINTMENT_DPS_LOCATION_ID, appointmentLocationMappingResponse)
          mappingServer.stubCreateAppointmentWithErrorFollowedBySlowSuccess()
          nomisApi.stubAppointmentCreate("""{ "eventId": $EVENT_ID }""")
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)

          publishAppointmentEvent("appointments.appointment-instance.created")

          await untilCallTo { appointmentsApi.getCountFor("/appointment-instances/$APPOINTMENT_INSTANCE_ID") } matches { it == 1 }
          await untilCallTo { nomisApi.postCountFor("/appointments") } matches { it == 1 }
        }

        @Test
        fun `should only create the NOMIS appointment once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-mapping-retry-success"),
              any(),
              isNull(),
            )
          }
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/appointments")))
        }

        @Test
        fun `will eventually create a mapping after NOMIS appointment is created`() {
          await untilAsserted {
            mappingServer.verify(
              2,
              postRequestedFor(urlEqualTo("/mapping/appointments"))
                .withRequestBody(matchingJsonPath("appointmentInstanceId", equalTo("$APPOINTMENT_INSTANCE_ID")))
                .withRequestBody(matchingJsonPath("nomisEventId", equalTo("$EVENT_ID"))),
            )
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-create-mapping-retry-success"),
              any(),
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenMappingServiceKeepsFailing {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          mappingServer.stubGetMappingGivenDpsLocationId(APPOINTMENT_DPS_LOCATION_ID, appointmentLocationMappingResponse)
          mappingServer.stubCreateAppointmentWithError(status = 503)
          nomisApi.stubAppointmentCreate("""{ "eventId": $EVENT_ID }""")
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)

          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 0 }

          publishAppointmentEvent("appointments.appointment-instance.created")
        }

        @Test
        fun `will try to create mapping 4 times and only create the NOMIS appointment once`() {
          await untilAsserted {
            mappingServer.verify(4, postRequestedFor(urlEqualTo("/mapping/appointments")))
          }
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/appointments")))
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 1 }
        }
      }

      @Nested
      inner class WhenMappingServiceFailsOnLocation {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          mappingServer.stubGetMappingGivenDpsLocationIdWithError(APPOINTMENT_DPS_LOCATION_ID, 404)
          mappingServer.stubCreateAppointment()
          nomisApi.stubAppointmentCreate("""{ "eventId": $EVENT_ID }""")
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)

          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 0 }

          publishAppointmentEvent("appointments.appointment-instance.created")
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 1 }
        }
      }
    }

    @Test
    fun `will log when duplicate is detected`() {
      appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)
      mappingServer.stubGetMappingGivenDpsLocationId(APPOINTMENT_DPS_LOCATION_ID, appointmentLocationMappingResponse)
      mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
      nomisApi.stubAppointmentCreate("""{ "eventId": $EVENT_ID }""")
      mappingServer.stubCreateAppointmentWithDuplicateError(
        appointmentInstanceId = APPOINTMENT_INSTANCE_ID,
        nomisEventId = EVENT_ID,
        duplicateNomisEventId = 999,
      )

      publishAppointmentEvent("appointments.appointment-instance.created")

      await untilAsserted {
        verify(telemetryClient).trackEvent(eq("appointment-mapping-create-failed"), any(), isNull())
      }
      await untilCallTo { appointmentsApi.getCountFor("/appointment-instances/$APPOINTMENT_INSTANCE_ID") } matches { it == 1 }
      await untilCallTo { nomisApi.postCountFor("/appointments") } matches { it == 1 }

      // the mapping call fails but is not queued for retry
      await untilCallTo {
        awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
      } matches { it == 0 }

      await untilCallTo { mappingServer.postCountFor("/mapping/appointments") } matches { it == 1 } // only tried once

      verify(telemetryClient).trackEvent(
        eq("to-nomis-synch-appointment-duplicate"),
        check {
          assertThat(it["appointmentInstanceId"]).isEqualTo("$APPOINTMENT_INSTANCE_ID")
          assertThat(it["existingNomisEventId"]).isEqualTo("$EVENT_ID")
          assertThat(it["duplicateAppointmentInstanceId"]).isEqualTo("$APPOINTMENT_INSTANCE_ID")
          assertThat(it["duplicateNomisEventId"]).isEqualTo("999")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class UpdateAppointment {

    @Nested
    inner class WhenAppointmentHasJustBeenUpdatedByAppointmentService {

      @BeforeEach
      fun setUp() {
        appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)
        mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
        mappingServer.stubGetMappingGivenDpsLocationId(APPOINTMENT_DPS_LOCATION_ID, appointmentLocationMappingResponse)
        nomisApi.stubAppointmentUpdate(EVENT_ID)
        publishAppointmentEvent("appointments.appointment-instance.updated")
      }

      @Test
      fun `will callback back to appointment service to get more details`() {
        await untilAsserted {
          appointmentsApi.verify(getRequestedFor(urlEqualTo("/appointment-instances/$APPOINTMENT_INSTANCE_ID")))
        }
      }

      @Test
      fun `will get the NOMIS location`() {
        await untilAsserted {
          mappingServer.verify(getRequestedFor(urlEqualTo("/mapping/locations/dps/$APPOINTMENT_DPS_LOCATION_ID")))
        }
        await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
      }

      @Test
      fun `will update an appointment in NOMIS`() {
        await untilAsserted {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/appointments/$EVENT_ID"))
              .withRequestBody(matchingJsonPath("internalLocationId", equalTo("$APPOINTMENT_NOMIS_LOCATION_ID")))
              .withRequestBody(matchingJsonPath("eventDate", equalTo("2023-03-14")))
              .withRequestBody(matchingJsonPath("startTime", equalTo("10:15")))
              .withRequestBody(matchingJsonPath("endTime", equalTo("11:42")))
              .withRequestBody(matchingJsonPath("eventSubType", equalTo("MEDI"))),
          )
        }
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("appointment-amend-success"),
            check {
              assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
              assertThat(it["nomisEventId"]).isEqualTo(EVENT_ID.toString())
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class Exceptions {

      @Nested
      inner class WhenServiceFailsOnce {

        @BeforeEach
        fun setUp() {
          appointmentsApi.stubGetAppointmentInstanceWithErrorFollowedBySlowSuccess(
            id = APPOINTMENT_INSTANCE_ID,
            response = appointmentResponse,
          )
          mappingServer.stubGetMappingGivenDpsLocationId(APPOINTMENT_DPS_LOCATION_ID, appointmentLocationMappingResponse)
          mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
          nomisApi.stubAppointmentUpdate(EVENT_ID)
          publishAppointmentEvent("appointments.appointment-instance.updated")
        }

        @Test
        fun `will callback back to appointment service twice to get more details`() {
          await untilAsserted {
            appointmentsApi.verify(2, getRequestedFor(urlEqualTo("/appointment-instances/$APPOINTMENT_INSTANCE_ID")))
            verify(telemetryClient).trackEvent(eq("appointment-amend-success"), any(), isNull())
          }
        }

        @Test
        fun `will eventually update the appointment in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(1, putRequestedFor(urlEqualTo("/appointments/$EVENT_ID")))
            verify(telemetryClient).trackEvent(eq("appointment-amend-failed"), any(), isNull())
            verify(telemetryClient).trackEvent(eq("appointment-amend-success"), any(), isNull())
          }
        }
      }

      @Nested
      inner class WhenServiceKeepsFailing {

        @BeforeEach
        fun setUp() {
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)
          mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
          nomisApi.stubAppointmentUpdateWithError(EVENT_ID, 503)
          publishAppointmentEvent("appointments.appointment-instance.updated")
        }

        @Test
        fun `will callback back to appointment service 3 times before given up`() {
          await untilAsserted {
            appointmentsApi.verify(3, getRequestedFor(urlEqualTo("/appointment-instances/$APPOINTMENT_INSTANCE_ID")))
          }
        }

        @Test
        fun `will create failure telemetry`() {
          await untilAsserted {
            verify(telemetryClient, times(3)).trackEvent(
              eq("appointment-amend-failed"),
              check {
                assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
                assertThat(it["nomisEventId"]).isEqualTo(EVENT_ID.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 1 }
        }
      }

      @Nested
      inner class WhenNotInMappingTable {
        @BeforeEach
        fun setUp() {
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)
          mappingServer.stubGetMappingGivenNonAssociationIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          publishAppointmentEvent("appointments.appointment-instance.updated")
        }

        @Test
        fun `will create failure telemetry`() {
          await untilAsserted {
            verify(telemetryClient, times(3)).trackEvent(
              eq("appointment-amend-failed"),
              anyMap(),
              isNull(),
            )
          }
          nomisApi.verify(0, putRequestedFor(urlPathMatching("/.+")))
        }
      }

      @Nested
      inner class WhenNotInDPS {
        @BeforeEach
        fun setUp() {
          appointmentsApi.stubGetAppointmentInstanceWithError(id = APPOINTMENT_INSTANCE_ID, 404)
          mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
          publishAppointmentEvent("appointments.appointment-instance.updated")
        }

        @Test
        fun `will create failure telemetry`() {
          await untilAsserted {
            verify(telemetryClient, times(3)).trackEvent(
              eq("appointment-amend-failed"),
              anyMap(),
              isNull(),
            )
          }
          nomisApi.verify(0, putRequestedFor(urlPathMatching("/.+")))
        }
      }

      @Nested
      inner class WhenNotInDPSNorInMapping {
        @BeforeEach
        fun setUp() {
          appointmentsApi.stubGetAppointmentInstanceWithError(id = APPOINTMENT_INSTANCE_ID, 404)
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          publishAppointmentEvent("appointments.appointment-instance.updated")
        }

        @Test
        fun `will create ignored telemetry`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-amend-missing-ignored"),
              check {
                assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
                assertThat(it["dps-error"]).isEqualTo("404 Not Found from GET http://localhost:8088/appointment-instances/1234567")
                assertThat(it["mapping-error"]).isEqualTo("404 Not Found from GET http://localhost:8084/mapping/appointments/appointment-instance-id/1234567")
              },
              isNull(),
            )
          }
          nomisApi.verify(0, putRequestedFor(urlPathMatching("/.+")))
        }
      }
    }
  }

  @Nested
  inner class CancelAppointment {
    @Nested
    inner class WhenAppointmentHasJustBeenCancelledByAppointmentService {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
        nomisApi.stubAppointmentCancel(EVENT_ID)
        publishAppointmentEvent("appointments.appointment-instance.cancelled")
      }

      @Test
      fun `will cancel an appointment in NOMIS`() {
        await untilAsserted {
          nomisApi.verify(putRequestedFor(urlEqualTo("/appointments/$EVENT_ID/cancel")))
        }
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("appointment-cancel-success"),
            check {
              assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
              assertThat(it["nomisEventId"]).isEqualTo(EVENT_ID.toString())
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class WhenAppointmentHasBeenCancelledButNotFoundInNomis {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
        nomisApi.stubAppointmentCancelWithError(EVENT_ID, 404)
        publishAppointmentEvent("appointments.appointment-instance.cancelled")
      }

      @Test
      fun `will create ignored telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("appointment-cancel-success"),
            check {
              assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
              assertThat(it["nomisEventId"]).isEqualTo(EVENT_ID.toString())
              assertThat(it["nomis-error"]).isEqualTo("404 Not found in Nomis: event ignored")
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class Exceptions {

      @Nested
      inner class WhenServiceFailsOnce {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
          nomisApi.stubAppointmentCancelWithErrorFollowedBySlowSuccess(EVENT_ID)
          publishAppointmentEvent("appointments.appointment-instance.cancelled")
        }

        @Test
        fun `will callback back to mapping service twice to get more details`() {
          await untilAsserted {
            mappingServer.verify(2, getRequestedFor(urlEqualTo("/mapping/appointments/appointment-instance-id/$APPOINTMENT_INSTANCE_ID")))
          }
        }

        @Test
        fun `will eventually cancel the appointment in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(2, putRequestedFor(urlEqualTo("/appointments/$EVENT_ID/cancel")))
            verify(telemetryClient).trackEvent(eq("appointment-cancel-failed"), any(), isNull())
            verify(telemetryClient).trackEvent(eq("appointment-cancel-success"), any(), isNull())
          }
        }
      }

      @Nested
      inner class WhenServiceKeepsFailing {
        @BeforeEach
        fun setUp() {
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)
          mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
          nomisApi.stubAppointmentCancelWithError(EVENT_ID, 503)
          publishAppointmentEvent("appointments.appointment-instance.cancelled")
        }

        @Test
        fun `will callback back to mapping service 3 times before given up`() {
          await untilAsserted {
            mappingServer.verify(3, getRequestedFor(urlEqualTo("/mapping/appointments/appointment-instance-id/$APPOINTMENT_INSTANCE_ID")))
          }
        }

        @Test
        fun `will create failure telemetry`() {
          await untilAsserted {
            verify(telemetryClient, atLeast(3)).trackEvent(
              eq("appointment-cancel-failed"),
              check {
                assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
                assertThat(it["nomisEventId"]).isEqualTo(EVENT_ID.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 1 }
        }
      }

      @Nested
      inner class WhenNotInMappingTable {
        @BeforeEach
        fun setUp() {
          appointmentsApi.stubGetAppointmentInstance(id = APPOINTMENT_INSTANCE_ID, response = appointmentResponse)
          mappingServer.stubGetMappingGivenNonAssociationIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          publishAppointmentEvent("appointments.appointment-instance.cancelled")
        }

        @Test
        fun `will create failure telemetry`() {
          await untilAsserted {
            verify(telemetryClient, times(3)).trackEvent(
              eq("appointment-cancel-failed"),
              anyMap(),
              isNull(),
            )
          }
          nomisApi.verify(0, putRequestedFor(urlPathMatching("/.+")))
        }
      }

      @Nested
      inner class WhenNotInDPSNorInMapping {
        @BeforeEach
        fun setUp() {
          appointmentsApi.stubGetAppointmentInstanceWithError(id = APPOINTMENT_INSTANCE_ID, 404)
          mappingServer.stubGetMappingGivenAppointmentInstanceIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          publishAppointmentEvent("appointments.appointment-instance.cancelled")
        }

        @Test
        fun `will create ignored telemetry`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-cancel-missing-ignored"),
              check {
                assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
                assertThat(it["dps-error"]).isEqualTo("404 Not Found from GET http://localhost:8088/appointment-instances/1234567")
                assertThat(it["mapping-error"]).isEqualTo("404 Not Found from GET http://localhost:8084/mapping/appointments/appointment-instance-id/1234567")
              },
              isNull(),
            )
          }
          nomisApi.verify(0, putRequestedFor(urlPathMatching("/.+")))
        }
      }
    }
  }

  @Nested
  inner class UncancelAppointment {
    @Nested
    inner class WhenAppointmentHasJustBeenUncancelledByAppointmentService {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
        nomisApi.stubAppointmentUncancel(EVENT_ID)
        publishAppointmentEvent("appointments.appointment-instance.uncancelled")
      }

      @Test
      fun `will uncancel an appointment in NOMIS`() {
        await untilAsserted {
          nomisApi.verify(putRequestedFor(urlEqualTo("/appointments/$EVENT_ID/uncancel")))
        }
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("appointment-uncancel-success"),
            check {
              assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
              assertThat(it["nomisEventId"]).isEqualTo(EVENT_ID.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  inner class DeleteAppointment {
    @Nested
    inner class WhenAppointmentHasJustBeenDeletedByAppointmentService {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
        nomisApi.stubAppointmentDelete(EVENT_ID)
        mappingServer.stubDeleteAppointmentMapping(APPOINTMENT_INSTANCE_ID)
        publishAppointmentEvent("appointments.appointment-instance.deleted")
      }

      @Test
      fun `will delete an appointment in NOMIS`() {
        await untilAsserted {
          nomisApi.verify(deleteRequestedFor(urlEqualTo("/appointments/$EVENT_ID")))
        }
      }

      @Test
      fun `will delete the mapping`() {
        await untilAsserted {
          mappingServer.verify(deleteRequestedFor(urlEqualTo("/mapping/appointments/appointment-instance-id/$APPOINTMENT_INSTANCE_ID")))
        }
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("appointment-delete-success"),
            check {
              assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
              assertThat(it["nomisEventId"]).isEqualTo(EVENT_ID.toString())
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class WhenDeletedAppointmentIsMissingFromNomis {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
        nomisApi.stubAppointmentDeleteWithError(EVENT_ID, 404)
        mappingServer.stubDeleteAppointmentMapping(APPOINTMENT_INSTANCE_ID)
        publishAppointmentEvent("appointments.appointment-instance.deleted")
      }

      @Test
      fun `will attempt to delete the appointment in NOMIS`() {
        await untilAsserted {
          nomisApi.verify(deleteRequestedFor(urlEqualTo("/appointments/$EVENT_ID")))
        }
      }

      @Test
      fun `will delete the mapping`() {
        await untilAsserted {
          mappingServer.verify(deleteRequestedFor(urlEqualTo("/mapping/appointments/appointment-instance-id/$APPOINTMENT_INSTANCE_ID")))
        }
      }

      @Test
      fun `will create success and warning telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("appointment-delete-missing-nomis-ignored"),
            check {
              assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
              assertThat(it["nomisEventId"]).isEqualTo(EVENT_ID.toString())
            },
            isNull(),
          )
          verify(telemetryClient).trackEvent(
            eq("appointment-delete-success"),
            check {
              assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
              assertThat(it["nomisEventId"]).isEqualTo(EVENT_ID.toString())
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class Exceptions {

      @Nested
      inner class WhenServiceFailsOnce {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
          nomisApi.stubAppointmentDeleteWithErrorFollowedBySlowSuccess(EVENT_ID)
          mappingServer.stubDeleteAppointmentMapping(APPOINTMENT_INSTANCE_ID)
          publishAppointmentEvent("appointments.appointment-instance.deleted")
        }

        @Test
        fun `will callback back to mapping service twice to get more details`() {
          await untilAsserted {
            mappingServer.verify(2, getRequestedFor(urlEqualTo("/mapping/appointments/appointment-instance-id/$APPOINTMENT_INSTANCE_ID")))
            verify(telemetryClient).trackEvent(eq("appointment-delete-success"), any(), isNull())
          }
        }

        @Test
        fun `will eventually delete the appointment in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(2, deleteRequestedFor(urlEqualTo("/appointments/$EVENT_ID")))
            verify(telemetryClient).trackEvent(eq("appointment-delete-failed"), any(), isNull())
            verify(telemetryClient).trackEvent(eq("appointment-delete-success"), any(), isNull())
          }
        }
      }

      @Nested
      inner class WhenServiceKeepsFailing {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
          nomisApi.stubAppointmentDeleteWithError(EVENT_ID, 503)
          publishAppointmentEvent("appointments.appointment-instance.deleted")
        }

        @Test
        fun `will callback back to mapping service 3 times before given up`() {
          await untilAsserted {
            mappingServer.verify(3, getRequestedFor(urlEqualTo("/mapping/appointments/appointment-instance-id/$APPOINTMENT_INSTANCE_ID")))
          }
        }

        @Test
        fun `will create failure telemetry`() {
          await untilAsserted {
            verify(telemetryClient, atLeast(3)).trackEvent(
              eq("appointment-delete-failed"),
              check {
                assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
                assertThat(it["nomisEventId"]).isEqualTo(EVENT_ID.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsAppointmentDlqClient!!.countAllMessagesOnQueue(appointmentDlqUrl!!).get()
          } matches { it == 1 }
        }
      }

      @Nested
      inner class WhenNotInMappingTable {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenNonAssociationIdWithError(APPOINTMENT_INSTANCE_ID, 404)
          nomisApi.stubAppointmentDelete(EVENT_ID)
          publishAppointmentEvent("appointments.appointment-instance.deleted")
        }

        @Test
        fun `will create ignored telemetry`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-delete-missing-mapping-ignored"),
              check {
                assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
                assertThat(it["error"]).isEqualTo("404 Not Found from GET http://localhost:8084/mapping/appointments/appointment-instance-id/1234567")
              },
              isNull(),
            )
          }
          nomisApi.verify(0, deleteRequestedFor(urlPathMatching("/.+")))
        }
      }

      @Nested
      inner class WhenNotInNomis {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingGivenAppointmentInstanceId(APPOINTMENT_INSTANCE_ID, mappingResponse)
          nomisApi.stubAppointmentDeleteWithError(EVENT_ID, 404)
          mappingServer.stubDeleteAppointmentMapping(APPOINTMENT_INSTANCE_ID)
          publishAppointmentEvent("appointments.appointment-instance.deleted")
        }

        @Test
        fun `will create ignored telemetry and delete mapping`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("appointment-delete-missing-nomis-ignored"),
              check {
                assertThat(it["appointmentInstanceId"]).isEqualTo(APPOINTMENT_INSTANCE_ID.toString())
                assertThat(it["error"]).isEqualTo("404 Not Found from DELETE http://localhost:8082/appointments/111222333")
              },
              isNull(),
            )
            mappingServer.verify(deleteRequestedFor(urlEqualTo("/mapping/appointments/appointment-instance-id/$APPOINTMENT_INSTANCE_ID")))
          }
        }
      }
    }
  }

  private fun publishAppointmentEvent(eventType: String) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(appointmentMessagePayload(eventType, APPOINTMENT_INSTANCE_ID))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  fun appointmentMessagePayload(eventType: String, appointmentInstanceId: Long) = """
    {"eventType":"$eventType", "additionalInformation": { "appointmentInstanceId": $appointmentInstanceId }, "version": "1.0", "description": "description", "occurredAt": "2023-02-05T11:23:56.031Z"}"""
}
