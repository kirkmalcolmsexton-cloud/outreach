package org.outreach.feature.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session

/** Hosts [OutreachNavigationScreen] when Android Auto connects. */
class OutreachNavigationSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return OutreachNavigationScreen(carContext)
    }
}
