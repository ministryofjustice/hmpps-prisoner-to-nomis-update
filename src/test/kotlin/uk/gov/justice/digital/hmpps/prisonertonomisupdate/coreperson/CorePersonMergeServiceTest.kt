package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion.ReligionMappingApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion.ReligionService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReference
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList
import java.util.*

@JsonTest
internal class CorePersonMergeServiceTest(@Autowired jsonMapper: JsonMapper) {

  private val religionService: ReligionService = mock()
  private val corePersonNomisApiService: CorePersonNomisApiService = mock()
  private val mapping: ReligionMappingApiService = mock()
  private val corePersonCprApiService: CorePersonCprApiService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val corePersonMergeService = CorePersonMergeService(telemetryClient, corePersonNomisApiService, mapping, corePersonCprApiService)

  @Nested
  inner class ReligionCreated {

    @Test
    fun `should log a merge`() = runTest {
      val cprReligionId = UUID.randomUUID()
      val prisonNumber = "A1234BC"
      corePersonMergeService.mergePerson(
        CorePersonMergeService.MergePersonEvent(
          "core-person-record.prison.religion.created",
          PersonReferenceList(listOf(PersonReference("prisonNumber", prisonNumber))),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("person-merged-success"),
        check {
          assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
        },
        isNull(),
      )
    }
  }
}
