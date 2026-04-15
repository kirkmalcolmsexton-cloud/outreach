package org.outreach.feature.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.outreach.core.model.HouseholdRecord

private const val MAPS_PIN_URL_PREFIX = "https://www.google.com/maps?q="
private const val MAPS_SEARCH_URL_PREFIX = "https://www.google.com/maps/search/?api=1&query="

fun mapsUrlForHousehold(household: HouseholdRecord): String {
    val lat = household.latitude
    val lng = household.longitude
    if (lat != null && lng != null) {
        return "$MAPS_PIN_URL_PREFIX$lat,$lng"
    }
    val addressQuery = buildString {
        append(household.streetAddress.trim())
        if (household.neighborhood.isNotBlank()) {
            append(", ")
            append(household.neighborhood.trim())
        }
    }
    return MAPS_SEARCH_URL_PREFIX + Uri.encode(addressQuery)
}

fun shareHouseholdLocation(context: Context, household: HouseholdRecord) {
    val mapsUrl = mapsUrlForHousehold(household)
    val payload = buildString {
        append("Meet us at ")
        append(household.name)
        append("\n")
        append(household.streetAddress)
        if (household.neighborhood.isNotBlank()) {
            append(", ")
            append(household.neighborhood)
        }
        append("\n")
        append(mapsUrl)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, payload)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
