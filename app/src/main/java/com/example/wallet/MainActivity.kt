package com.example.wallet

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.wallet.ui.theme.WalletTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WalletTheme {
                WalletApp()
            }
        }
    }
}

// Data Model
data class CardItem(
    val id: Int,
    val type: String,
    val number: String,
    val bank: String,
    val expiry: String,
    val cvv: String,
    val photo: Bitmap? = null,
    val bgColor: Color? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WalletApp() {
    var expandedCardId by remember { mutableStateOf<Int?>(null) }
    var isCardFlipped by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }

    val cards = remember {
        mutableStateListOf(
            CardItem(1, "Credit Card", "1234 5678 9012 3456", "HDFC BANK", "12/28", "123"),
            CardItem(2, "Debit Card", "9876 5432 1098 7654", "SBI", "11/27", "456"),
            CardItem(3, "Metro Card", "DELHI-001122", "DMRC", "--", "--"),
            CardItem(4, "Driving License", "DL-123456789", "GOVT OF INDIA", "--", "--"),
            CardItem(5, "Travel Card", "TC-55667788", "VISA", "05/30", "789")
        )
    }

    var activeCard by remember { mutableStateOf<CardItem?>(null) }
    LaunchedEffect(expandedCardId) {
        if (expandedCardId != null) {
            isCardFlipped = false
            activeCard = cards.find { it.id == expandedCardId }
        }
    }

    BackHandler(enabled = expandedCardId != null) {
        expandedCardId = null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF121212),
        floatingActionButton = {
            if (expandedCardId == null) {
                FloatingActionButton(
                    onClick = { showForm = true },
                    containerColor = Color(0xFFFFD700),
                    contentColor = Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Card")
                }
            }
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        val density = LocalDensity.current

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(1400.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 32.dp)
                        .graphicsLayer { translationY = scrollState.value.toFloat() },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "My Wallet",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    )

                    val cardSpacing by remember {
                        derivedStateOf {
                            val scrollProgress = (scrollState.value / 800f).coerceIn(0f, 1f)
                            val startSpacing = -105f
                            val endSpacing = 10f
                            startSpacing + (scrollProgress * (endSpacing - startSpacing))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(top = 12.dp, bottom = 20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(cardSpacing.dp)) {
                            cards.forEachIndexed { index, card ->
                                var offsetX by remember { mutableStateOf(0f) }
                                var offsetY by remember { mutableStateOf(0f) }
                                var isDragging by remember { mutableStateOf(false) }

                                val dragScale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "dragScale")

                                WalletCard(
                                    card = card,
                                    isExpanded = false,
                                    isFlipped = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2.3f)
                                        .zIndex(if (isDragging) 100f else index.toFloat())
                                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                                        .graphicsLayer {
                                            scaleX = dragScale
                                            scaleY = dragScale
                                        }
                                        .pointerInput(Unit) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { isDragging = true },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    offsetX += dragAmount.x
                                                    offsetY += dragAmount.y
                                                },
                                                onDragEnd = {
                                                    isDragging = false
                                                    val cardHeightPx = with(density) { 80.dp.toPx() }
                                                    val moveIndex = (offsetY / (cardHeightPx / 2)).toInt()
                                                    val targetIndex = (index + moveIndex).coerceIn(0, cards.size - 1)

                                                    if (targetIndex != index) {
                                                        val item = cards.removeAt(index)
                                                        cards.add(targetIndex, item)
                                                    }
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                },
                                                onDragCancel = {
                                                    isDragging = false
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                }
                                            )
                                        },
                                    onClick = { expandedCardId = card.id },
                                    onLongClick = { cards.remove(card) }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = expandedCardId != null,
                enter = fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.85f, animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 0.85f, animationSpec = tween(400)),
                modifier = Modifier.zIndex(200f)
            ) {
                activeCard?.let { card ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.9f))
                            .clickable { expandedCardId = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            WalletCard(
                                card = card,
                                isExpanded = true,
                                isFlipped = isCardFlipped,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1.586f),
                                onClick = { isCardFlipped = !isCardFlipped }
                            )

                            Spacer(modifier = Modifier.height(40.dp))

                            IconButton(
                                onClick = { expandedCardId = null },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }

            if (showForm) {
                AddCardDialog(
                    onAdd = { cards.add(it); showForm = false },
                    onDismiss = { showForm = false }
                )
            }
        }
    }
}

@Composable
fun WalletCard(
    card: CardItem,
    isExpanded: Boolean,
    isFlipped: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val tapScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        label = "tapScale"
    )

    val rotationXAnim = remember { Animatable(if (isExpanded) -180f else 2f) }
    val scaleAnim = remember { Animatable(if (isExpanded) 0.6f else 1f) }
    val translationYAnim = remember { Animatable(if (isExpanded) 150f else 0f) }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            launch { rotationXAnim.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
            launch { scaleAnim.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
            launch { translationYAnim.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
        } else {
            launch { rotationXAnim.animateTo(2f, tween(300, easing = FastOutSlowInEasing)) }
            launch { scaleAnim.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
            launch { translationYAnim.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
        }
    }

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "cardFlip"
    )

    val cardColors = if (card.bgColor != null) {
        listOf(card.bgColor, card.bgColor.copy(alpha = 0.85f))
    } else {
        getCardColors(card.type)
    }

    val contentColor = if (card.bgColor != null && isColorLight(card.bgColor)) Color.Black else Color.White

    Card(
        modifier = modifier
            .padding(horizontal = if (isExpanded) 0.dp else 12.dp)
            .graphicsLayer {
                rotationY = rotation
                rotationX = rotationXAnim.value
                scaleX = scaleAnim.value * tapScale
                scaleY = scaleAnim.value * tapScale
                translationY = translationYAnim.value
                cameraDistance = 12f * density
            }
            .shadow(elevation = if (isExpanded) 40.dp else 8.dp, shape = RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .semantics {
                role = Role.Button
                contentDescription = if (isExpanded) "Flip card" else "Expand card"
                onClick(label = if (isExpanded) "Flip" else "Expand") { onClick(); true }
                onLongClick(label = "Delete card") { onLongClick?.invoke(); true }
            }
            .indication(interactionSource, LocalIndication.current)
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = androidx.compose.foundation.interaction.PressInteraction.Press(offset)
                        interactionSource.emit(press)
                        tryAwaitRelease()
                        interactionSource.emit(androidx.compose.foundation.interaction.PressInteraction.Release(press))
                    },
                    onTap = { onClick() },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick?.invoke()
                    }
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        if (rotation <= 90f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = Brush.linearGradient(colors = cardColors))
            ) {
                if (card.photo != null) {
                    Image(
                        bitmap = card.photo.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Semi-transparent overlay to ensure text readability on captured photos
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height

                        for (i in 0..20) {
                            val path = Path().apply {
                                moveTo(0f, height * (0.1f + i * 0.04f))
                                quadraticTo(width * 0.4f, height * (0.05f + i * 0.04f), width * 0.7f, height * (0.2f + i * 0.04f))
                                quadraticTo(width * 0.9f, height * (0.3f + i * 0.04f), width, height * (0.25f + i * 0.04f))
                            }
                            drawPath(path = path, color = contentColor.copy(alpha = 0.04f), style = Stroke(width = 0.8.dp.toPx()))
                        }

                        drawRect(
                            brush = Brush.linearGradient(
                                0.0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                0.5f to contentColor.copy(alpha = 0.05f),
                                0.55f to Color.Transparent,
                                1.0f to Color.Transparent,
                                start = Offset(0f, 0f),
                                end = Offset(width, height)
                            ),
                            size = size
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(
                                text = card.type.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = card.bank,
                                style = if (isExpanded) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                                color = contentColor,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        if (card.photo == null) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp, 30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFFCFB53B), Color(0xFFFFD700), Color(0xFF8B7500))))
                                    .border(0.5.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height
                                    drawLine(Color.Black.copy(0.15f), Offset(w*0.3f, 0f), Offset(w*0.3f, h), 1.5f)
                                    drawLine(Color.Black.copy(0.15f), Offset(w*0.7f, 0f), Offset(w*0.7f, h), 1.5f)
                                    drawLine(Color.Black.copy(0.15f), Offset(0f, h*0.5f), Offset(w, h*0.5f), 1.5f)
                                    drawCircle(Color.Black.copy(0.05f), radius = h*0.2f, center = Offset(w*0.5f, h*0.5f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    val cardNumberText = if (isExpanded) card.number else "**** **** **** " + card.number.takeLast(4)
                    Text(
                        text = cardNumberText,
                        style = if (isExpanded) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                        color = contentColor,
                        letterSpacing = 2.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.graphicsLayer(shadowElevation = 4f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("VALID THRU", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.5f), fontSize = 8.sp)
                            Text(card.expiry, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        if (card.type.lowercase().contains("travel")) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "VISA", style = MaterialTheme.typography.titleLarge, color = contentColor.copy(alpha = 0.95f), fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
                                Text("PLATINUM", color = contentColor.copy(alpha = 0.8f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .background(brush = Brush.verticalGradient(colors = listOf(cardColors.first().copy(alpha = 0.98f), Color.Black)))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(44.dp).background(Color(0xFF0D0D0D)))
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f).height(36.dp).background(Brush.horizontalGradient(listOf(Color(0xFFF5F5F5), Color(0xFFE0E0E0))))) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                for (i in 0..3) {
                                    drawLine(Color.Gray.copy(alpha = 0.2f), Offset(10f, 10f + i * 8f), Offset(size.width - 10f, 10f + i * 8f), 1.5f)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("CVV", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                            Text(text = card.cvv, style = MaterialTheme.typography.titleMedium, color = Color.Black, fontWeight = FontWeight.ExtraBold, modifier = Modifier.background(Color.White, RoundedCornerShape(2.dp)).padding(horizontal = 10.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = card.number, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.9f), letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "Global Customer Assistance: +1-800-WALLET", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

fun isColorLight(color: Color): Boolean {
    val luminance = 0.2126 * color.red + 0.7152 * color.green + 0.0722 * color.blue
    return luminance > 0.5
}

@Composable
fun AddCardDialog(onAdd: (CardItem) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }

    if (showScanner) {
        CardScanner(onResult = { scannedCard -> onAdd(scannedCard); showScanner = false }, onClose = { showScanner = false })
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                Button(onClick = { if (type.isNotBlank() && number.isNotBlank()) onAdd(CardItem((0..10000).random(), type, number, bank, expiry, cvv)) }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add New Card")
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { showScanner = true }) { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Card") }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bank, onValueChange = { bank = it }, label = { Text("Bank") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("Number") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = expiry, onValueChange = { expiry = it }, label = { Text("Expiry") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = cvv, onValueChange = { cvv = it }, label = { Text("CVV") }, modifier = Modifier.weight(1f))
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun CardScanner(onResult: (CardItem) -> Unit, onClose: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var hasPermission by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    
    var isCapturing by remember { mutableStateOf(false) }
    var detectedCardData by remember { mutableStateOf<CardItem?>(null) }
    
    // Hold Timer logic
    var holdProgress by remember { mutableStateOf(0f) }
    val requiredHoldTime = 4000L 
    
    // Stability tracking to avoid flickering resets
    var missedFrames by remember { mutableIntStateOf(0) }
    val maxMissedFrames = 8

    // Effect to handle the auto-capture after hold time
    LaunchedEffect(detectedCardData?.number) {
        if (detectedCardData != null) {
            val startTime = System.currentTimeMillis()
            while (holdProgress < 1f && detectedCardData != null) {
                delay(50)
                holdProgress = ((System.currentTimeMillis() - startTime).toFloat() / requiredHoldTime).coerceIn(0f, 1f)
            }
            if (holdProgress >= 1f && detectedCardData != null) {
                isCapturing = true
                onResult(detectedCardData!!)
            }
        } else {
            holdProgress = 0f
        }
    }

    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.CAMERA) }

    if (hasPermission) {
        Box(modifier = Modifier.fillMaxSize().zIndex(500f)) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    ProcessCameraProvider.getInstance(ctx).addListener({
                        val cameraProvider = ProcessCameraProvider.getInstance(ctx).get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build().also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                if (isCapturing) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }

                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    textRecognizer.process(inputImage).addOnSuccessListener { visionText ->
                                        // Combine all text for better detection of split fields
                                        val fullText = visionText.text.replace("\n", " ")
                                        
                                        // More flexible regex for card numbers (13-19 digits, allows spaces/dashes)
                                        val numMatch = Regex("\\b(?:\\d[ -]*?){13,19}\\b").find(fullText)
                                        
                                        if (numMatch != null) {
                                            val cleanNum = numMatch.value.replace(" ", "").replace("-", "")
                                            if (cleanNum.length >= 13) {
                                                missedFrames = 0
                                                val formattedNumber = cleanNum.chunked(4).joinToString(" ")
                                                
                                                // Only update the core card item if the number changes
                                                if (detectedCardData?.number != formattedNumber) {
                                                    val bitmap = imageProxy.yuvToBitmap()
                                                    if (bitmap != null) {
                                                        val color = getAverageColor(bitmap)
                                                        
                                                        var foundExpiry: String? = null
                                                        var foundBank = "Unknown Bank"
                                                        var foundType = "Debit Card"
                                                        val bankKeywords = listOf("HDFC", "SBI", "ICICI", "AXIS", "KOTAK", "CITI", "HSBC", "DMRC", "VISA", "MASTERCARD")
                                                        
                                                        // Better expiry search: allows spaces and different separators
                                                        val expMatch = Regex("\\b(0[1-9]|1[0-2])[ /]+([0-9]{2})\\b").find(fullText)
                                                        if (expMatch != null) {
                                                            foundExpiry = "${expMatch.groupValues[1]}/${expMatch.groupValues[2]}"
                                                        }
                                                        
                                                        for (keyword in bankKeywords) {
                                                            if (fullText.uppercase().contains(keyword)) {
                                                                if (keyword == "DMRC") { foundBank = "DMRC"; foundType = "Metro Card" }
                                                                else if (keyword != "VISA" && keyword != "MASTERCARD") foundBank = keyword
                                                            }
                                                        }
                                                        
                                                        val finalType = when {
                                                            foundType == "Metro Card" -> "Metro Card"
                                                            cleanNum.startsWith("4") || cleanNum.startsWith("5") -> "Credit Card"
                                                            cleanNum.startsWith("3") -> "Travel Card"
                                                            else -> "Debit Card"
                                                        }
                                                        
                                                        // Use the full captured bitmap to fill the card completely
                                                        detectedCardData = CardItem((0..10000).random(), finalType, formattedNumber, foundBank, foundExpiry ?: "--", (100..999).random().toString(), bitmap, color)
                                                    } else {
                                                        // Same card, check if we can fill in missing expiry
                                                        if (detectedCardData?.expiry == "--") {
                                                            val expMatch = Regex("\\b(0[1-9]|1[0-2])[ /]+([0-9]{2})\\b").find(fullText)
                                                            if (expMatch != null) {
                                                                detectedCardData = detectedCardData?.copy(expiry = "${expMatch.groupValues[1]}/${expMatch.groupValues[2]}")
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                missedFrames++
                                            }
                                        } else {
                                            missedFrames++
                                        }
                                        
                                        // Reset detection if we consistently miss the card for several frames
                                        if (missedFrames > maxMissedFrames) {
                                            detectedCardData = null
                                        }
                                    }.addOnCompleteListener { 
                                        imageProxy.close() 
                                    }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                        } catch (e: Exception) { Log.e("CardScanner", "Binding failed", e) }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isCapturing) Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f)))

            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.size(320.dp, 200.dp).align(Alignment.Center)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))) }
                        val patternColor = if (holdProgress >= 1f) Color.Green else Color.Blue.copy(alpha = 0.8f)
                        
                        drawPath(path = path, color = Color.Blue.copy(alpha = 0.2f))
                        drawPath(path = path, color = patternColor, style = Stroke(width = 2.dp.toPx()))
                        
                        for (i in 1..5) drawLine(Color.Blue.copy(alpha = 0.15f), Offset(0f, size.height * i / 6f), Offset(size.width, size.height * i / 6f), 1.dp.toPx())
                    }
                    if (detectedCardData != null) {
                        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (holdProgress < 1f) "HOLD STEADY..." else "CAPTURING", color = if (holdProgress < 1f) Color.Yellow else Color.Green, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { holdProgress }, color = Color.Green, trackColor = Color.White.copy(alpha = 0.3f), modifier = Modifier.width(100.dp))
                        }
                    }
                }
                Text(text = if (detectedCardData != null) "Keep holding for 4 seconds" else "Center card inside frame", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))
                
                // Manual capture button is now enabled whenever a card is detected
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).size(80.dp).clip(CircleShape).background(if (detectedCardData != null) Color.Green else Color.White.copy(alpha = 0.3f)).clickable(enabled = detectedCardData != null) { detectedCardData?.let { isCapturing = true; onResult(it) } }, contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape).border(4.dp, Color.Black, CircleShape))
                }
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
            }
        }
    }
}

fun ImageProxy.yuvToBitmap(): Bitmap? {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

fun getAverageColor(bitmap: Bitmap): Color {
    var r = 0L; var g = 0L; var b = 0L; var count = 0L
    val startX = (bitmap.width * 0.25).toInt()
    val startY = (bitmap.height * 0.25).toInt()
    val endX = (bitmap.width * 0.75).toInt()
    val endY = (bitmap.height * 0.75).toInt()
    for (y in startY until endY step 20) {
        for (x in startX until endX step 20) {
            val c = bitmap.getPixel(x, y)
            r += android.graphics.Color.red(c); g += android.graphics.Color.green(c); b += android.graphics.Color.blue(c)
            count++
        }
    }
    if (count == 0L) return Color.Blue
    return Color((r/count).toInt(), (g/count).toInt(), (b/count).toInt())
}

fun getCardColors(type: String): List<Color> {
    return when (type.lowercase()) {
        "credit card" -> listOf(Color(0xFF0D0F21), Color(0xFF1B1D3A))
        "debit card" -> listOf(Color(0xFF0A2612), Color(0xFF144D24))
        "metro card" -> listOf(Color(0xFF4D1700), Color(0xFF8E2B00))
        "driving license" -> listOf(Color(0xFF06234D), Color(0xFF0D47A1))
        "travel card" -> listOf(Color(0xFF1F073A), Color(0xFF4A148C))
        else -> listOf(Color(0xFF121212), Color(0xFF2C2C2C))
    }
}

@ComposePreview(showBackground = true)
@Composable
fun WalletAppPreview() { WalletTheme { Surface(color = Color(0xFF121212)) { WalletApp() } } }
