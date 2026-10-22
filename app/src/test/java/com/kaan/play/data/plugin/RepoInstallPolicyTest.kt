package com.kaan.play.data.plugin

import com.kaan.play.data.db.AddonType
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoInstallPolicyTest {

    @Test
    fun initialPluginEnabledReturnsTrueForNuvioPluginRepo() {
        assertTrue(RepoInstallPolicy.initialPluginEnabled(AddonType.NUVIO_PLUGIN_REPO))
    }

    @Test
    fun initialPluginEnabledReturnsTrueForCloudStreamRepo() {
        assertTrue(RepoInstallPolicy.initialPluginEnabled(AddonType.CLOUDSTREAM_REPO))
    }

    @Test
    fun defaultArgumentReturnsTrue() {
        assertTrue(RepoInstallPolicy.initialPluginEnabled())
    }
}
