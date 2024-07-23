package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import io.swagger.v3.oas.annotations.Operation
import kotlinx.coroutines.delay
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNote
import java.time.LocalDateTime

/**
 * This represents the possible interface for the CaseNote API service.
 * This can be deleted once the real service is available.
 */
@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('ROLE_MIGRATE_CASENOTES')")
class MockCaseNotesResource {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @GetMapping("/case-notes/case-note-id/{caseNoteIdentifier}")
  @Operation(hidden = true)
  suspend fun getCaseNote(
    @PathVariable
    caseNoteIdentifier: String,
  ): CaseNote {
    log.info("Getting case note id $caseNoteIdentifier}")
    delay(20)
    return CaseNote(
      caseNoteId = caseNoteIdentifier,
      offenderIdentifier = "A1234AA",
      type = "X",
      typeDescription = "Security",
      subType = "Y",
      subTypeDescription = "subtype desc",
      source = "DPS",
      creationDateTime = LocalDateTime.of(2024, 1, 1, 1, 1),
      occurrenceDateTime = LocalDateTime.of(2024, 1, 1, 1, 1),
      authorName = "me",
      authorUserId = "ME.COM",
      text = "contents of case note",
      eventId = 1234567,
      sensitive = false,
      amendments = emptyList(),
    )
  }

  @GetMapping("/case-notes/{offenderIdentifier}")
  @Operation(hidden = true)
  suspend fun getCaseNotesForPrisoner(
    @PathVariable
    offenderIdentifier: String,
  ): List<CaseNote> {
    log.info("Getting unpaged case notes for $offenderIdentifier}")
    delay(150)
    return listOf(
      CaseNote(
        caseNoteId = "00001111-0000-1111-1111-000011112222",
        offenderIdentifier = offenderIdentifier,
        type = "X",
        typeDescription = "Security",
        subType = "Y",
        subTypeDescription = "subtype desc",
        source = "DPS",
        creationDateTime = LocalDateTime.of(2024, 1, 1, 1, 1),
        occurrenceDateTime = LocalDateTime.of(2024, 1, 1, 1, 1),
        authorName = "me",
        authorUserId = "ME.COM",
        text = "contents of case note",
        eventId = 1234567,
        sensitive = false,
        amendments = emptyList(),
      ),
    )
  }
}
