package nl.medicatietracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.medicatietracker.MedApp
import nl.medicatietracker.R
import nl.medicatietracker.data.Accessory
import nl.medicatietracker.data.AccessorySlot
import nl.medicatietracker.data.DUTCH
import nl.medicatietracker.data.FeedResult
import nl.medicatietracker.data.Food
import nl.medicatietracker.data.PetMood
import nl.medicatietracker.data.PetRepository
import nl.medicatietracker.data.PetRules
import nl.medicatietracker.data.PetState
import kotlin.math.floor
import kotlin.math.min

private enum class PetTab(val label: String) { ETEN("Eten"), WINKEL("Winkel"), KAST("Kast") }

/** Tekenvolgorde van accessoires: sjaal onder de bril, hoed erboven, pollepel als laatste. */
private val DRAW_ORDER = listOf(AccessorySlot.NEK, AccessorySlot.OGEN, AccessorySlot.HOOFD, AccessorySlot.HAND)

private const val EAT_DURATION_MS = 1600L

@Composable
fun PetScreen(app: MedApp) {
    val repo = app.petRepository
    val pet by repo.pet.collectAsStateWithLifecycle(null)
    val food by repo.food.collectAsStateWithLifecycle(emptyMap())
    val owned by repo.owned.collectAsStateWithLifecycle(emptySet())
    LaunchedEffect(Unit) { repo.ensurePet() }

    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(PetTab.ETEN) }
    var eating by remember { mutableStateOf<Pair<Food, Long>?>(null) }
    var bubble by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var namePromptDismissed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(bubble) {
        if (bubble != null) {
            delay(2500)
            bubble = null
        }
    }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    Box(Modifier.fillMaxSize().background(Px.navy)) {
        val state = pet ?: return@Box
        val fullness = state.currentFullness(now)
        val mood = PetRules.mood(fullness)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelText(
                    state.displayName,
                    size = 14.sp,
                    color = Px.white,
                    modifier = Modifier.weight(1f).clickable { renaming = true },
                )
                PixelImage(R.drawable.pet_coin, "Muntjes", Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                PixelText("${state.coins}", size = 14.sp, color = Px.yellow)
            }

            Box(Modifier.fillMaxWidth().aspectRatio(64f / 48f)) {
                PetScene(state, mood, eating, Modifier.fillMaxSize())
                bubble?.let {
                    PixelPanel(Modifier.align(Alignment.TopCenter).padding(top = 8.dp), contentPadding = 8.dp) {
                        PixelText(it, size = 9.sp)
                    }
                }
            }

            PixelPanel(Modifier.fillMaxWidth()) {
                PixelText("${state.displayName} ${mood.label}.", size = 9.sp)
                Spacer(Modifier.height(10.dp))
                HungerBar(fullness)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PetTab.entries.forEach { t ->
                    PixelButton(
                        t.label,
                        onClick = { tab = t },
                        modifier = Modifier.weight(1f),
                        fill = if (tab == t) Px.orange else Px.blue,
                    )
                }
            }

            when (tab) {
                PetTab.ETEN -> FoodTab(food) { item ->
                    scope.launch {
                        when (repo.feed(item)) {
                            FeedResult.GEGETEN -> {
                                eating = item to System.currentTimeMillis()
                                bubble = "Mmm, ${item.label.lowercase(DUTCH)}!"
                            }
                            FeedResult.VOL -> bubble = "Ik zit vol!"
                            FeedResult.GEEN_ETEN -> Unit
                        }
                    }
                }
                PetTab.WINKEL -> ShopTab(state, owned, onBuyFood = { item ->
                    scope.launch {
                        bubble = if (repo.buyFood(item)) "Gekocht: ${item.label.lowercase(DUTCH)}!" else "Te weinig muntjes..."
                    }
                }, onBuyAccessory = { item ->
                    scope.launch {
                        bubble = if (repo.buyAccessory(item)) "Wat staat dat mooi!" else "Te weinig muntjes..."
                    }
                })
                PetTab.KAST -> WardrobeTab(state, owned) { item -> scope.launch { repo.toggleEquipped(item) } }
            }
        }

        if (renaming || (state.name.isBlank() && !namePromptDismissed)) {
            NameDialog(
                initial = state.name,
                onDismiss = {
                    renaming = false
                    namePromptDismissed = true
                },
                onSave = { name ->
                    scope.launch { repo.rename(name) }
                    renaming = false
                    namePromptDismissed = true
                },
            )
        }
    }
}

private fun moodSprite(mood: PetMood): String = when (mood) {
    PetMood.BLIJ -> "happy"
    PetMood.TEVREDEN -> "neutral"
    PetMood.VERDRIETIG -> "sad"
}

@Composable
private fun blobFrames(): Map<String, List<ImageBitmap>> = mapOf(
    "happy" to listOf(ImageBitmap.imageResource(R.drawable.pet_blob_happy_0), ImageBitmap.imageResource(R.drawable.pet_blob_happy_1)),
    "neutral" to listOf(ImageBitmap.imageResource(R.drawable.pet_blob_neutral_0), ImageBitmap.imageResource(R.drawable.pet_blob_neutral_1)),
    "sad" to listOf(ImageBitmap.imageResource(R.drawable.pet_blob_sad_0), ImageBitmap.imageResource(R.drawable.pet_blob_sad_1)),
    "eat" to listOf(ImageBitmap.imageResource(R.drawable.pet_blob_eat_0), ImageBitmap.imageResource(R.drawable.pet_blob_eat_1)),
)

/** De kamer met het blobje, getekend op een raster van 64×48 pixels en met hele factoren vergroot. */
@Composable
private fun PetScene(pet: PetState, mood: PetMood, eating: Pair<Food, Long>?, modifier: Modifier) {
    val background = ImageBitmap.imageResource(R.drawable.pet_background)
    val heart = ImageBitmap.imageResource(R.drawable.pet_heart)
    val frames = blobFrames()
    val accessories = DRAW_ORDER.mapNotNull { pet.equipped(it) }.map { ImageBitmap.imageResource(it.drawable) }
    val foodImage = eating?.let { ImageBitmap.imageResource(it.first.drawable) }
    val time by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(100)
            value = System.currentTimeMillis()
        }
    }

    Canvas(modifier) {
        val scale = floor(min(size.width / 64f, size.height / 48f)).toInt().coerceAtLeast(1)
        val ox = ((size.width - 64 * scale) / 2).toInt()
        val oy = ((size.height - 48 * scale) / 2).toInt()
        fun draw(image: ImageBitmap, x: Int, y: Int) = drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(ox + x * scale, oy + y * scale),
            dstSize = IntSize(image.width * scale, image.height * scale),
            filterQuality = FilterQuality.None,
        )

        draw(background, 0, 0)
        val since = eating?.let { time - it.second } ?: Long.MAX_VALUE
        val isEating = since in 0 until EAT_DURATION_MS
        val frame = ((time / 500) % 2).toInt()
        val face = when {
            isEating -> if ((time / 200) % 2 == 0L) "eat" else "happy"
            else -> moodSprite(mood)
        }
        draw(frames.getValue(face)[frame], 16, 13)
        accessories.forEach { draw(it, 16, 13 + frame * 2) }

        if (isEating) {
            if (since < 700 && foodImage != null) {
                draw(foodImage, 24, (since / 700f * 14).toInt())
            } else {
                val rise = ((since - 700) / 900f * 10).toInt()
                draw(heart, 12, 14 - rise)
                draw(heart, 45, 12 - rise)
            }
        }
    }
}

@Composable
private fun HungerBar(fullness: Int) {
    val filled = (fullness + 9) / 10
    val color = when (PetRules.mood(fullness)) {
        PetMood.BLIJ -> Px.green
        PetMood.TEVREDEN -> Px.yellow
        PetMood.VERDRIETIG -> Px.red
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PixelText("BUIK", size = 8.sp)
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(10) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .drawBehind {
                            val u = 2.dp.toPx()
                            drawRect(Px.ink)
                            drawRect(if (i < filled) color else Px.silver, Offset(u, u), Size(size.width - 2 * u, size.height - 2 * u))
                            if (i < filled) drawRect(Color.White.copy(alpha = 0.35f), Offset(u, u), Size(size.width - 2 * u, u))
                        },
                )
            }
        }
    }
}

/** Zet [items] in rijen van [columns] gelijke vakken. */
@Composable
private fun <T> PixelGrid(items: List<T>, columns: Int, cell: @Composable RowScope.(T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell(it) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    PixelText(text, size = 10.sp, color = Px.yellow, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun FoodTab(food: Map<Food, Int>, onFeed: (Food) -> Unit) {
    if (food.isEmpty()) {
        PixelPanel(Modifier.fillMaxWidth()) {
            PixelText("Nog geen eten. Vink medicatie of taken af om eten te verdienen!", size = 9.sp)
        }
        return
    }
    SectionTitle("Tik om te voeren")
    PixelGrid(Food.entries.filter { (food[it] ?: 0) > 0 }, columns = 4) { item ->
        PixelPanel(Modifier.weight(1f).clickable { onFeed(item) }, contentPadding = 8.dp) {
            PixelImage(item.drawable, item.label, Modifier.fillMaxWidth().aspectRatio(1f))
            PixelText("x${food[item]}", size = 9.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PriceTag(price: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PixelImage(R.drawable.pet_coin, null, Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        PixelText("$price", size = 9.sp)
    }
}

/** Het blobje met één accessoire, voor in de winkel en de kast. */
@Composable
private fun AccessoryPreview(accessory: Accessory, modifier: Modifier = Modifier) {
    Box(modifier) {
        PixelImage(R.drawable.pet_blob_happy_0, null, Modifier.fillMaxSize())
        PixelImage(accessory.drawable, accessory.label, Modifier.fillMaxSize())
    }
}

@Composable
private fun ShopTab(
    pet: PetState,
    owned: Set<Accessory>,
    onBuyFood: (Food) -> Unit,
    onBuyAccessory: (Accessory) -> Unit,
) {
    SectionTitle("Lekker eten")
    PixelGrid(Food.SHOP, columns = 4) { item ->
        val price = item.price ?: 0
        PixelPanel(
            Modifier.weight(1f).clickable(enabled = pet.coins >= price) { onBuyFood(item) },
            contentPadding = 8.dp,
            fill = if (pet.coins >= price) Px.white else Px.silver,
        ) {
            PixelImage(item.drawable, item.label, Modifier.fillMaxWidth().aspectRatio(1f))
            PriceTag(price)
        }
    }
    SectionTitle("Accessoires")
    PixelGrid(Accessory.entries.toList(), columns = 2) { item ->
        val isOwned = item in owned
        PixelPanel(Modifier.weight(1f), contentPadding = 8.dp) {
            AccessoryPreview(item, Modifier.fillMaxWidth().aspectRatio(1f))
            PixelText(item.label, size = 8.sp, modifier = Modifier.height(36.dp))
            if (isOwned) {
                PixelText("In je kast", size = 8.sp, color = Px.teal)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriceTag(item.price)
                    Spacer(Modifier.weight(1f))
                    PixelButton("Koop", onClick = { onBuyAccessory(item) }, enabled = pet.coins >= item.price)
                }
            }
        }
    }
}

@Composable
private fun WardrobeTab(pet: PetState, owned: Set<Accessory>, onToggle: (Accessory) -> Unit) {
    if (owned.isEmpty()) {
        PixelPanel(Modifier.fillMaxWidth()) {
            PixelText("Je kast is nog leeg. Verdien muntjes met mijlpalen en koop iets in de winkel!", size = 9.sp)
        }
        return
    }
    SectionTitle("Tik om op of af te zetten")
    AccessorySlot.entries.forEach { slot ->
        val items = Accessory.entries.filter { it.slot == slot && it in owned }
        if (items.isEmpty()) return@forEach
        PixelText(slot.label, size = 8.sp, color = Px.white)
        PixelGrid(items, columns = 3) { item ->
            val worn = pet.equipped(slot) == item
            PixelPanel(
                Modifier.weight(1f).clickable { onToggle(item) },
                contentPadding = 6.dp,
                fill = if (worn) Px.lime else Px.white,
            ) {
                AccessoryPreview(item, Modifier.fillMaxWidth().aspectRatio(1f))
                PixelText(if (worn) "Op" else "Af", size = 8.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        PixelPanel(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
            PixelText("Hoe heet je blobje?", size = 11.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= PetRepository.MAX_NAME_LENGTH) name = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = PixelFont, fontSize = 12.sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton("Later", onClick = onDismiss, modifier = Modifier.weight(1f), fill = Px.slate)
                PixelButton("OK", onClick = { onSave(name) }, modifier = Modifier.weight(1f), enabled = name.isNotBlank())
            }
        }
    }
}
