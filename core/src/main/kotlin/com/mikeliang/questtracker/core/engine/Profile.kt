package com.mikeliang.questtracker.core.engine

import com.mikeliang.questtracker.core.model.Attribute
import com.mikeliang.questtracker.core.model.Cadence
import com.mikeliang.questtracker.core.model.CompletionRecord
import com.mikeliang.questtracker.core.model.Quest
import com.mikeliang.questtracker.core.model.QuestKind
import com.mikeliang.questtracker.core.model.QuestStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * One attribute's profile card. Everything here only ever grows — there is no field
 * that could display loss, decay, or a broken anything.
 *
 * @property completionsToNextRank fewest completions that reach the next rank, using
 * the fastest-accruing active quest feeding this attribute (full base rate — escalation
 * always restores it); attributes with no active quests count in daily completions.
 * @property progressToNextRank 0..1 fraction of the way from the current rank's
 * threshold to the next — drives the milestone progress bar.
 * @property evidence neutral evidence line ("41 completions over 12 weeks"). For an
 * untouched attribute it states the fact ("No quests feed Social yet") — information,
 * never shame; the UI must not editorialize beyond it.
 */
data class AttributeCard(
    val attribute: Attribute,
    val rank: Int,
    val title: String,
    val points: Double,
    val completions: Int,
    val nextTitle: String,
    val completionsToNextRank: Int,
    val progressToNextRank: Double,
    val evidence: String,
)

/** A retired quest in the archive, with the completions it banked (kept forever). */
data class CompletedChapter(
    val quest: Quest,
    val completions: Int,
)

/**
 * Everything the profile screen renders: the four attribute cards (always all four,
 * in [Attribute] order), the lifetime completion total (never resets), and the
 * completed-chapters archive of retired quests.
 */
data class ProfileSummary(
    val attributes: List<AttributeCard>,
    val lifetimeCompletions: Int,
    val chapters: List<CompletedChapter>,
)

/**
 * Builds the profile as of [today]. Pure fold over history: retired quests keep every
 * point they earned (gains are permanent), and consistency deliberately does not
 * appear here — it stays quest-scoped, and the profile shows accumulated evidence
 * instead.
 */
fun buildProfile(
    quests: List<Quest>,
    completions: List<CompletionRecord>,
    today: LocalDate,
): ProfileSummary {
    val progress = attributeProgress(quests, completions)
    val byQuest = completions.groupBy { it.questId }

    val cards = Attribute.entries.map { attribute ->
        val p = progress.getValue(attribute)
        val feeding = quests.filter { (it.kind as? QuestKind.Recurring)?.attribute == attribute }
        val active = feeding.filter { it.status == QuestStatus.Active }

        // Fastest honest path to the next rank: the biggest base among active quests
        // (one weekly completion outruns one daily). Untouched attributes are framed
        // in daily completions — the track a new quest would most likely run on.
        val bestBase = active
            .maxOfOrNull { AccrualRules.basePoints((it.kind as QuestKind.Recurring).cadence) }
            ?: AccrualRules.basePoints(Cadence.Daily)

        val firstEvidence = feeding.mapNotNull { quest ->
            val cadence = (quest.kind as QuestKind.Recurring).cadence
            byQuest[quest.id].orEmpty().dedupedByPeriod(cadence).minOfOrNull { it.periodStart }
        }.minOrNull()

        val rankFloor = Milestones.thresholdFor(p.rank)
        val nextThreshold = Milestones.thresholdFor(p.rank + 1)

        AttributeCard(
            attribute = attribute,
            rank = p.rank,
            title = p.title,
            points = p.points,
            completions = p.completions,
            nextTitle = p.nextTitle,
            completionsToNextRank = ceil(p.pointsToNextRank / bestBase).toInt(),
            progressToNextRank = ((p.points - rankFloor) / (nextThreshold - rankFloor))
                .coerceIn(0.0, 1.0),
            evidence = evidenceCopy(p.completions, firstEvidence, active.isNotEmpty(), attribute, today),
        )
    }

    return ProfileSummary(
        attributes = cards,
        lifetimeCompletions = lifetimeCompletionCount(quests, completions),
        chapters = quests
            .filter { it.status == QuestStatus.Retired }
            .sortedByDescending { it.createdAt }
            .map { quest ->
                val records = byQuest[quest.id].orEmpty()
                val count = when (val kind = quest.kind) {
                    is QuestKind.Recurring -> records.dedupedByPeriod(kind.cadence).size
                    QuestKind.SideQuest -> if (records.isEmpty()) 0 else 1
                }
                CompletedChapter(quest, count)
            },
    )
}

/**
 * The evidence line. Copy lives here so the UI never invents it — especially the
 * untouched-attribute case, which must read as a plain fact, not a deficit.
 */
private fun evidenceCopy(
    completions: Int,
    firstEvidence: LocalDate?,
    hasActiveQuests: Boolean,
    attribute: Attribute,
    today: LocalDate,
): String = when {
    completions > 0 && firstEvidence != null -> {
        val noun = if (completions == 1) "completion" else "completions"
        "$completions $noun over ${spanCopy(firstEvidence, today)}"
    }

    hasActiveQuests -> "No completions banked yet"

    else -> "No quests feed $attribute yet"
}

/** "12 weeks", "5 days", "3 months" — inclusive of both endpoints, always rounded up. */
private fun spanCopy(first: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(first, today) + 1
    return when {
        days <= 1 -> "1 day"
        days < 14 -> "$days days"
        days < 16 * 7 -> {
            val weeks = ceil(days / 7.0).toInt()
            if (weeks == 1) "1 week" else "$weeks weeks"
        }

        else -> {
            val months = ceil(days / 30.44).toInt()
            "$months months"
        }
    }
}
