package app.opia.common.ui.auth.registration

import androidx.compose.animation.*
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.opia.common.ui.auth.TextFieldError
import app.opia.common.ui.auth.registration.OpiaRegistration.Event
import app.opia.common.ui.auth.registration.OpiaRegistration.Model
import app.opia.common.ui.component.DatePickerView
import app.opia.common.ui.component.OpiaErrorSnackbarHost
import app.opia.common.ui.component.opiaBlue
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.*

// State.step -> AnimatedVisibility
// custom Child hierarchy like OpiaRoot is not worth the boilerplate
@OptIn(ExperimentalAnimationApi::class)
@ExperimentalComposeUiApi
@Composable
fun RegistrationContent(component: OpiaRegistration) {
    val model by component.models.subscribeAsState()

    val scaffoldState: ScaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        component.events.collectLatest {
            when (it) {
                is Event.OwnedFieldConfirmed -> {
                    component.onAuthenticate()
                }
                is Event.Authenticated -> {
                    component.onAuthenticated(it.selfId)
                }
                is Event.NetworkError -> {
                    // TODO cancel on new (passing `this` does not help)
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

    Scaffold(scaffoldState = scaffoldState, snackbarHost = { OpiaErrorSnackbarHost(it) }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // with 2xAnimatedVisibility: both visible below each other during animation
            // doc: https://developer.android.com/jetpack/compose/animation#animate-as-state
            val transition = updateTransition(model.uiState is RegistrationState.StepOne)
            transition.AnimatedVisibility(
                visible = { targetSelected -> targetSelected },
                enter = slideInHorizontally(),
                exit = slideOutHorizontally()
            ) {}
            transition.AnimatedContent { targetState ->
                if (targetState) StepOne(component, model)
                else StepTwo(component, model)
            }
        }
    }
}

@Composable
fun StepOne(component: OpiaRegistration, model: Model) {
    var showDatePickerView by rememberSaveable { mutableStateOf(false) }

    // padding & icon button hare and not in Scaffold for animation
    Column(
        modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = component::onBackToAuthClicked, modifier = Modifier.align(Alignment.Start)
        ) {
            Icon(imageVector = Icons.Default.ArrowBackIosNew, contentDescription = "back")
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Setup account",
            modifier = Modifier.fillMaxWidth(),
            color = opiaBlue,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = model.name,
            onValueChange = component::onNameChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = !model.isLoading,
            label = { Text("Name") },
            isError = model.nameError != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true,
            maxLines = 1
        )
        model.nameError?.let { error -> TextFieldError(error) }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = model.handle,
            onValueChange = component::onHandleChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = !model.isLoading,
            label = { Text("Username") },
            isError = model.handleError != null,
            keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Next),
            singleLine = true,
            maxLines = 1
        )
        model.handleError?.let { error -> TextFieldError(error) }
        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).border(
            0.5.dp,
            if (model.dobError == null) MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
            else MaterialTheme.colors.error,
            RoundedCornerShape(5.dp)
        ).clickable { showDatePickerView = true }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    //text = if (model.dob?.toString().isNullOrEmpty()) "Click to pick" else model.dob.toString(),
                    text = if (model.dob == null) "Date of Birth"
                    else model.dob.format(DateTimeFormatter.ISO_DATE),
                    color = MaterialTheme.colors.onSurface,
                )

                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp, 20.dp),
                    tint = MaterialTheme.colors.onSurface
                )
            }
        }
        model.dobError?.let { error -> TextFieldError(error) }
        Spacer(modifier = Modifier.height(8.dp))

        if (showDatePickerView) {
            DatePickerView {
                showDatePickerView = false
                println("[*] Registration > date: $it -- ${it?.dayOfMonth}/${it?.monthValue}/${it?.year}")
                it?.let {
                    component.onDOBChanged(it)
                }
                //it?.let { date = DateFormatter(it) }
            }
        }

        Button(
            onClick = { component.onNextToFinalizeClicked() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !model.isLoading,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = opiaBlue, contentColor = Color.White
            ),
        ) {
            Text("Next", modifier = Modifier.padding(2.dp))
        }
    }
}

// TODO override BackHandler
@Composable
@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
fun StepTwo(component: OpiaRegistration, model: Model) {
    val keyboardController = LocalSoftwareKeyboardController.current

    if (model.phoneVerification?.valid == false) {
        AlertDialog(onDismissRequest = {
            //component.dismissVerificationDialog()
        }, confirmButton = {
            TextButton(onClick = {
                component.confirmPhoneVerificationDialog()
            }) {
                Text("Confirm")
            }
        }, dismissButton = {
            TextButton(onClick = {
                component.dismissPhoneVerificationDialog()
            }) {
                Text("Cancel")
            }
        }, title = { Text("Enter the verification code.") },
            //properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            text = {
                Column {
                    Text("A 6 digit code has been sent to your phone. Please enter it below to verify your number:")
                    Spacer(modifier = Modifier.height(8.dp))
                    // verificationCode.verification_code doesn't trigger
                    // verificationCodeCopy is updated at the same time (Msg.VerificationCodeChanged)
                    OutlinedTextField(
                        value = model.phoneVerificationCode,
                        onValueChange = component::onPhoneVerificationCodeChanged,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !model.isLoading,
                        label = { Text("Verification code") },
                        isError = model.phoneVerificationCodeError != null,
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false, keyboardType = KeyboardType.Companion.Number
                        ),
                        singleLine = true,
                        maxLines = 1
                    )
                    model.phoneVerificationCodeError?.let { error -> TextFieldError(error) }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            })
    }

    Column(
        modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = component::onBackToRegistrationClicked,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Icon(imageVector = Icons.Default.ArrowBackIosNew, contentDescription = "back")
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Finalize account",
            modifier = Modifier.fillMaxWidth(),
            color = opiaBlue,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = model.email,
            onValueChange = component::onEmailChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = !model.isLoading,
            label = { Text("E-mail") },
            isError = model.emailError != null,
            keyboardOptions = KeyboardOptions(
                autoCorrect = false, keyboardType = KeyboardType.Email, imeAction = ImeAction.Next
            ),
            singleLine = true,
            maxLines = 1
        )
        model.emailError?.let { error -> TextFieldError(error) }
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "or",
            modifier = Modifier.fillMaxWidth(),
            color = opiaBlue,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = model.phoneNo,
            onValueChange = component::onPhoneNoChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = !model.isLoading,
            label = { Text("Phone number") },
            isError = model.phoneNoError != null,
            keyboardOptions = KeyboardOptions(
                autoCorrect = false, keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next
            ),
            singleLine = true,
            maxLines = 1
        )
        model.phoneNoError?.let { error -> TextFieldError(error) }
        Spacer(modifier = Modifier.height(8.dp))

        var isSecretHidden by rememberSaveable { mutableStateOf(true) }
        OutlinedTextField(
            value = model.secret,
            onValueChange = component::onSecretChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = !model.isLoading,
            label = { Text("Password") },
            isError = model.secretError != null,
            visualTransformation = if (isSecretHidden) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            maxLines = 1
        )
        model.secretError?.let { error -> TextFieldError(error) }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = model.secretRepeat,
            onValueChange = component::onSecretRepeatChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = !model.isLoading,
            label = { Text("Repeat password") },
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
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = component::onAuthenticate,
            modifier = Modifier.fillMaxWidth(),
            enabled = !model.isLoading,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = opiaBlue, contentColor = Color.White
            )
        ) {
            Text("Finish", modifier = Modifier.padding(2.dp))
        }

        Text(
            "By creating an account, you automatically agree to out Terms of Service.",
            color = opiaBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
