package app.opia.common.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldState
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opia.common.ui.auth.OpiaAuth.Event
import app.opia.common.ui.auth.store.IdentityProvider
import app.opia.common.ui.component.OpiaErrorSnackbarHost
import app.opia.common.ui.component.opiaBlue
import app.opia.common.ui.component.opiaGray
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import kotlinx.coroutines.launch

@ExperimentalComposeUiApi
@Composable
fun AuthContent(component: OpiaAuth) {
    val model by component.models.subscribeAsState()

    val keyboardController = LocalSoftwareKeyboardController.current

    // Scaffold for correct Snackbar animations
    val scaffoldState: ScaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()
    Scaffold(scaffoldState = scaffoldState, snackbarHost = { OpiaErrorSnackbarHost(it) }) {
        //val snackbarHostState = remember { SnackbarHostState() }
        //SnackbarHost(hostState = snackbarHostState)

        LaunchedEffect(Unit) {
            // collectLatest may swallow important events?
            component.events.collect {
                when (it) {
                    is Event.Authenticated -> {
                        component.onAuthenticated(it.authCtx)
                    }

                    is Event.NetworkError -> {
                        // TODO collectLatest's cancel on new (passing `this` does not help)
                        scope.launch {
                            scaffoldState.snackbarHostState.showSnackbar(
                                "No network connection",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }

                    is Event.UnknownError -> {
                        scope.launch {
                            scaffoldState.snackbarHostState.showSnackbar(
                                "Something went wrong, please try again later",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "OPIA",
                modifier = Modifier.padding(top = 20.dp),
                color = opiaBlue,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Login",
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                color = opiaBlue,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start
            )

            OutlinedTextField(
                value = model.unique,
                onValueChange = component::onUniqueChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp),
                enabled = !model.isLoading,
                label = { Text("Username") },
                placeholder = { Text("Username, email or phone number") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null)
                },
                isError = model.uniqueError != null,
                keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Next),
                singleLine = true,
                maxLines = 1
            )
            model.uniqueError?.let { error -> TextFieldError(error) }

            var isSecretHidden by rememberSaveable { mutableStateOf(true) }
            OutlinedTextField(
                value = model.secret,
                onValueChange = component::onSecretChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp),
                enabled = !model.isLoading,
                label = { Text("Password") },
                leadingIcon = { Icon(imageVector = Icons.Default.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { isSecretHidden = !isSecretHidden }) {
                        Icon(
                            imageVector = if (isSecretHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "show/hide password"
                        )
                    }
                },
                isError = model.secretError != null,
                visualTransformation = if (isSecretHidden) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                singleLine = true,
                maxLines = 1
            )
            model.secretError?.let { error -> TextFieldError(error) }

            // TODO IconButton, either person symbol or loading symbol / trailingIcon loading
            Button(
                onClick = component::onLogin,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                enabled = !model.isLoading,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = opiaBlue, contentColor = Color.White
                )
            ) {
                Text("Login", modifier = Modifier.padding(2.dp))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxHeight().fillMaxWidth().background(opiaGray)
            ) {
                Text(
                    "No account yet?",
                    color = opiaBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )

                Button(
                    onClick = component::onRegister,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    enabled = !model.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.White, contentColor = opiaBlue
                    )
                ) {
                    Text("Register", modifier = Modifier.padding(2.dp))
                }

                Button(
                    onClick = { component.onContinueWithProvider(IdentityProvider.Google) },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    enabled = !model.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.White, contentColor = opiaBlue
                    )
                ) {
                    Text("Continue with Google", modifier = Modifier.padding(2.dp))
                }

                Button(
                    onClick = { component.onContinueWithProvider(IdentityProvider.Apple) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    enabled = !model.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.White, contentColor = opiaBlue
                    )
                ) {
                    Text("Continue with Apple", modifier = Modifier.padding(2.dp))
                }
            }
        }
    }
}

/**
 * To be removed when [TextField]s support error
 * source: https://github.com/android/compose-samples/blob/main/Jetsurvey/app/src/main/java/com/example/compose/jetsurvey/signinsignup/SignInSignUp.kt
 */
@Composable
fun TextFieldError(textError: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        //Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = textError,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            fontSize = 14.sp,
            style = LocalTextStyle.current.copy(color = MaterialTheme.colors.error)
        )
    }
}
