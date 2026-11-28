package org.mobyapp.android.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mobyapp.android.data.AuthInfo
import org.mobyapp.android.data.AuthField
import org.mobyapp.android.data.SetupIssue
import org.mobyapp.android.ui.MobyTopBar
import org.mobyapp.android.ui.appViewModel
import org.mobyapp.android.ui.theme.MobyButton

/**
 * @param siteId A saved site to log in to again, or null to add a site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(siteId: String?, backLabel: String, onBack: () -> Unit, onLoggedIn: () -> Unit) {
    val vm = appViewModel { LoginViewModel(it, siteId) }
    val addressFocus = remember { FocusRequester() }
    // Adding a site starts with its address, so the keyboard opens for it.
    // A saved site's address is already known.
    LaunchedEffect(Unit) {
        if (!vm.isSavedSite) runCatching { addressFocus.requestFocus() }
    }
    val discovery = vm.discovery
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            MobyTopBar(title = if (vm.isSavedSite) "Log in" else "Add site", backLabel = backLabel, onBack = onBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (discovery == null && vm.isSavedSite && vm.error == null && vm.setupIssues.isEmpty()) {
                // Connecting to a saved site, whose address is already known.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Connecting to ${vm.siteUrl}…", style = MaterialTheme.typography.bodyLarge)
                }
            } else if (discovery == null) {
                OutlinedTextField(
                    value = vm.siteUrl,
                    onValueChange = { vm.siteUrl = it },
                    label = { Text("Site address") },
                    placeholder = { Text("example.com") },
                    supportingText = { Text(SITE_ADDRESS_DESCRIPTION) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { vm.connect() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(addressFocus).testTag("siteUrl"),
                )
                ErrorText(vm.error)
                if (vm.setupIssues.isNotEmpty()) {
                    SetupIssuesCard("This site isn't ready for posting from the app", vm.setupIssues)
                }
                MobyButton(onClick = vm::connect, enabled = !vm.loading, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    ButtonContent("Continue", vm.loading)
                }
            } else {
                val auth = requireNotNull(vm.mechanism)
                val step = vm.step
                Text(step?.title ?: "Log in to ${discovery.site.name}", style = MaterialTheme.typography.titleMedium)
                if (step == null && vm.setupIssues.isNotEmpty()) {
                    SetupIssuesCard("Some features aren't available on this site", vm.setupIssues)
                }
                (if (step != null) step.description else auth.description)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                // The form is built from the site's authentication mechanism,
                // or the step it answered with, so other mechanisms work
                // without app changes.
                val fields = vm.fields
                fields.forEach { field ->
                    CredentialField(
                        field = field,
                        value = vm.credentials[field.name] ?: "",
                        onValueChange = { vm.credentials[field.name] = it },
                        isError = vm.errorField == field.name,
                        isLast = field == fields.last(),
                        onDone = { vm.logIn(onLoggedIn) },
                    )
                }
                ErrorText(vm.error)
                MobyButton(onClick = { vm.logIn(onLoggedIn) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth().testTag("logIn")) {
                    ButtonContent(step?.submitLabel ?: auth.submitLabel ?: "Log in", vm.loading)
                }
                if (step != null) {
                    TextButton(onClick = vm::startOver, enabled = !vm.loading, modifier = Modifier.testTag("startOver")) { Text("Start over") }
                } else {
                    auth.passwordResetUrl?.let { url ->
                        TextButton(onClick = { uriHandler.openUri(url) }) { Text("Forgot your password?") }
                    }
                    if (vm.otherMechanisms.isNotEmpty()) {
                        OtherMechanisms(vm.otherMechanisms, enabled = !vm.loading, onSelect = vm::selectMechanism)
                    }
                    if (!vm.isSavedSite) {
                        TextButton(onClick = vm::changeSite, enabled = !vm.loading) { Text("Use a different site") }
                    }
                }
            }
        }
    }
}

/** "Log in a different way": the site's other mechanisms, collapsed at first. */
@Composable
private fun OtherMechanisms(mechanisms: List<AuthInfo>, enabled: Boolean, onSelect: (AuthInfo) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.testTag("otherMechanisms").semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
        ) {
            Text("Log in a different way")
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                mechanisms.forEach { other ->
                    OutlinedButton(
                        onClick = { expanded = false; onSelect(other) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().testTag("mechanism_${other.mechanism}"),
                    ) { Text(other.label) }
                }
            }
        }
    }
}

/**
 * One field of a login form, from the site's description of it.
 *
 * Number fields take digits only, with a number keyboard.
 */
@Composable
private fun CredentialField(
    field: AuthField,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    isLast: Boolean,
    onDone: () -> Unit,
) {
    val isPassword = field.type == "password"
    val isNumber = field.type == "number"
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(if (isNumber) it.filter(Char::isDigit) else it) },
        label = { Text(field.label) },
        placeholder = field.placeholder?.let { { Text(it) } },
        singleLine = true,
        isError = isError,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when (field.type) {
                "password" -> KeyboardType.Password
                "email" -> KeyboardType.Email
                // Digits only, unlike KeyboardType.Number, which can have a
                // decimal point and minus sign.
                "number" -> KeyboardType.NumberPassword
                else -> KeyboardType.Text
            },
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().testTag("credential_${field.name}"),
    )
}

/** Lists setup issues, worded for the user to pass on to an administrator. */
@Composable
private fun SetupIssuesCard(title: String, issues: List<SetupIssue>) {
    val hasErrors = issues.any { it.isError }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (hasErrors) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().testTag("setupIssues"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            issues.forEach { issue ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        if (issue.isError) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = if (issue.isError) "Problem" else "Note",
                        modifier = Modifier.size(20.dp),
                    )
                    Text(issue.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ButtonContent(label: String, loading: Boolean) {
    if (loading) {
        // In the button's text color, so it shows on the blue.
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
    } else {
        Text(label)
    }
}

private const val SITE_ADDRESS_DESCRIPTION =
    "URL of a site that has the Moby module or plugin installed (Backdrop, Drupal, or WordPress)."
