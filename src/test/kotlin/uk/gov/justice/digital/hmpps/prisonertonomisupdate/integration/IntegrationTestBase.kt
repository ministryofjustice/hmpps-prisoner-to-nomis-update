package uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.AdjudicationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.AlertsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.CaseNotesDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonCprApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.CSIPDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances.VisitBalanceDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AppointmentsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.IncentivesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.LocationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NonAssociationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentencingAdjustmentsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.VisitsApiExtension
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper

@ExtendWith(
  NomisApiExtension::class,
  MappingExtension::class,
  HmppsAuthApiExtension::class,
  VisitsApiExtension::class,
  IncentivesApiExtension::class,
  ActivitiesApiExtension::class,
  AppointmentsApiExtension::class,
  SentencingAdjustmentsApiExtension::class,
  AdjudicationsApiExtension::class,
  NonAssociationsApiExtension::class,
  LocationsApiExtension::class,
  CourtSentencingApiExtension::class,
  AlertsDpsApiExtension::class,
  CaseNotesDpsApiExtension::class,
  CSIPDpsApiExtension::class,
  ContactPersonDpsApiExtension::class,
  FinanceDpsApiExtension::class,
  IncidentsDpsApiExtension::class,
  OrganisationsDpsApiExtension::class,
  VisitBalanceDpsApiExtension::class,
  ExternalMovementsDpsApiExtension::class,
  CorePersonCprApiExtension::class,
  OfficialVisitsDpsApiExtension::class,
)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
abstract class IntegrationTestBase {

  @MockitoSpyBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

  internal fun setAuthorisation(
    user: String = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf(),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = user, roles = roles, scope = scopes)

  companion object {
    private val localStackContainer = LocalStackContainer.instance

    @JvmStatic
    @DynamicPropertySource
    fun testcontainers(registry: DynamicPropertyRegistry) {
      localStackContainer?.also { LocalStackContainer.setLocalStackProperties(it, registry) }
    }
  }
}
