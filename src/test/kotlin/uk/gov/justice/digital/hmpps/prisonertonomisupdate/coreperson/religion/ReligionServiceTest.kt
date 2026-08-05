package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReference
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList
import java.util.*

@JsonTest
internal class ReligionServiceTest(@Autowired jsonMapper: JsonMapper) {

  private val telemetryClient: TelemetryClient = mock()
  private val religionService = ReligionService(telemetryClient)

  @Nested
  inner class ReligionCreated {

    @Test
    fun `should log a religion being created`() = runTest {
      val cprReligionId = UUID.randomUUID()
      val prisonNumber = "A1234BC"
      religionService.religionCreated(
        ReligionService.ReligionEvent(
          "core-person-record.prison.religion.created",
          ReligionService.CprReligionCreatedInfo(cprReligionId),
          PersonReferenceList(listOf(PersonReference("prisonNumber", prisonNumber))),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("religion-created-success"),
        check {
          assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
          assertThat(it["cprReligionId"]).isEqualTo(cprReligionId.toString())
        },
        isNull(),
      )
    }
  }
}
