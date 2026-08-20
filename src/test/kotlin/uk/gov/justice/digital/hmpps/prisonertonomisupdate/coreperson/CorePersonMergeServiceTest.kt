package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion.ReligionService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReference
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList

@JsonTest
internal class CorePersonMergeServiceTest(@Autowired jsonMapper: JsonMapper) {

  private val corePersonNomisApiService: CorePersonNomisApiService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val religionService: ReligionService = mock()
  private val corePersonMergeService =
    CorePersonMergeService(telemetryClient, religionService)

  @Nested
  inner class PersonMerged {

    @Nested
    inner class NoPrisonNumber {
      @Test
      fun `should log when no prison number is provided`() = runTest {
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `should call the religion service to merge religions`() = runTest {
        val prisonNumber = "A1234BC"

        // Method under test
        corePersonMergeService.mergePerson(
          CorePersonMergeService.MergePersonEvent(
            "core-person-record.prison.merged",
            PersonReferenceList(listOf(PersonReference("prisonNumber", prisonNumber))),
          ),
        )

        verify(religionService).mergeReligions(
          eq("A1234BC"),
        )
      }

      @Test
      fun `should log a merge has happened`() = runTest {
        val prisonNumber = "A1234BC"

        // Method under test
        corePersonMergeService.mergePerson(
          CorePersonMergeService.MergePersonEvent(
            "core-person-record.prison.merged",
            PersonReferenceList(listOf(PersonReference("prisonNumber", prisonNumber))),
          ),
        )

        verify(corePersonNomisApiService, never()).mergeReligions(any(), any())
      }
    }
  }
}
