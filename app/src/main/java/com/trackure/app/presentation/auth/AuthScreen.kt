package com.trackure.app.presentation.auth

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.trackure.app.data.repository.PersistentTrackureRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(repository: PersistentTrackureRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords never enter saved-instance state.
    var password by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val green = Color(0xFF00E676)
    val orange = Color(0xFFFC4C02)
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Unable to connect. Please try again." }
            finally { busy = false }
        }
    }
    Surface(color = Color(0xFF101419), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp).widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            com.trackure.app.presentation.components.TrackureBrand(prominent = true)
            Text("One Sure Step Everyday", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFF00B0FF))
            Spacer(Modifier.height(12.dp))
            Text("Your next step starts here.", color = Color(0xFF94A3B8))
            Button(onClick = { perform {
                repository.signInGoogleAccount(context).getOrThrow()
            } }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = orange, contentColor = Color.White)) {
                Text("Continue with Google", fontWeight = FontWeight.Bold)
            }
            Surface(color = Color(0xFF181C21), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF262A30))) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf(false to "Sign In", true to "Register").forEach { (value, title) ->
                            TextButton(onClick = { register = value; message = null }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                Text(title, color = if (register == value) green else Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (register) OutlinedTextField(name, { name = it }, label = { Text("Display Name") }, singleLine = true,
                        enabled = !busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !busy, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { perform {
                        val authenticated = repository.authenticateEmail(context, email, password, name, register).getOrThrow()
                        password = ""
                        if (!authenticated) message = "Check your email to confirm your account, then sign in."
                    } }, enabled = !busy && email.contains('@') && password.isNotEmpty() && (!register || name.isNotBlank()),
                        modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = green, contentColor = Color(0xFF101419))) {
                        Text(if (register) "Create Account" else "Sign In", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (busy) CircularProgressIndicator(color = green, modifier = Modifier.size(24.dp))
            message?.let {
                SelectionContainer {
                    Text(it, color = Color(0xFFFC4C02), fontSize = 13.sp,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            TextButton(onClick = { perform { repository.continueOffline(context) } }, enabled = !busy) {
                Text("Continue with Offline Local Account", color = Color(0xFF94A3B8))
            }
        }
    }
}
