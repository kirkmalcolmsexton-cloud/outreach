package org.outreach.testing

import androidx.test.platform.app.InstrumentationRegistry
import org.outreach.app.testing.NavigationTestSupport
import org.outreach.core.data.CollaborationRepository
import org.outreach.core.data.OutreachRepository
import org.outreach.core.data.OutreachServiceLocator

object OutreachUiTestEnvironment {
    fun installOverrides(
        repository: OutreachRepository? = null,
        collaborationRepository: CollaborationRepository? = null
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            OutreachServiceLocator.installTestOverrides(repository, collaborationRepository)
        }
    }

    fun clearOverrides() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            OutreachServiceLocator.clearTestOverrides()
            NavigationTestSupport.reset()
        }
    }
}
