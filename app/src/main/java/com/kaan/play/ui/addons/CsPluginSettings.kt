package com.kaan.play.ui.addons

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.kaan.play.R
import com.kaan.play.data.cloudstream.CsApiResult
import com.kaan.play.ui.rememberContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One settings load at a time, owned by the installed-addons screen's lifetime. */
@Composable
internal fun rememberCsPluginSettingsLauncher(): (Long) -> Unit {
    val repository = rememberContainer().csRepos
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unsupported = stringResource(R.string.cs_settings_unavailable)
    val failed = stringResource(R.string.cs_settings_failed)
    var opening by remember { mutableStateOf(false) }
    return remember(repository, context, scope, unsupported, failed) {
        { rowId ->
            if (!opening) {
                opening = true
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { repository.loadSettings(rowId) }
                        // Do not open a delayed dialog after Back, Home, or activity destruction.
                        val activity = context.settingsActivity()
                        if (activity != null && !activity.isFinishing && !activity.isDestroyed &&
                            (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
                            val callback = (result as? CsApiResult.Success)?.openSettings
                            when {
                                result is CsApiResult.Failure -> Toast.makeText(context, failed, Toast.LENGTH_LONG).show()
                                callback == null -> Toast.makeText(context, unsupported, Toast.LENGTH_LONG).show()
                                else -> callback(activity) // Native UI needs the live FragmentActivity, never applicationContext.
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: VirtualMachineError) {
                        throw e
                    } catch (e: ThreadDeath) {
                        throw e
                    } catch (_: Throwable) {
                        // Plugin exceptions can contain credentials or URLs; neither log nor display their messages.
                        Toast.makeText(context, failed, Toast.LENGTH_LONG).show()
                    } finally {
                        opening = false
                    }
                }
            }
        }
    }
}

private fun Context.settingsActivity(): Activity? {
    var candidate: Context = this
    val visited = HashSet<Context>()
    while (visited.add(candidate)) {
        if (candidate is Activity) return candidate
        candidate = (candidate as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}
