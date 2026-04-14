package org.outreach.feature.visits

import org.outreach.core.model.HouseholdRecord
import java.time.LocalDate

fun selectNextTarget(items: List<HouseholdRecord>): HouseholdRecord? {
    return items.minByOrNull {
        it.lastVisited?.let(LocalDate::parse)?.toEpochDay() ?: Long.MIN_VALUE
    }
}
