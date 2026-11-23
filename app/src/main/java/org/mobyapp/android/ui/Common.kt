package org.mobyapp.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.json.JSONException
import org.mobyapp.android.MobyApplication
import org.mobyapp.android.data.ApiException
import java.io.IOException

/** A message for the user describing why a request failed. */
fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message
    is JSONException -> "The site's response was not understood. Is this a Moby site?"
    is IOException -> "Couldn't reach the site. Check the address and your connection."
    else -> message ?: "Something went wrong."
}

/** Whether a request failed because the token is no longer valid. */
val Throwable.isUnauthorized: Boolean get() = this is ApiException && status == 401

/** Creates a view model that has access to the application. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (MobyApplication) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as MobyApplication
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app) } })
}
