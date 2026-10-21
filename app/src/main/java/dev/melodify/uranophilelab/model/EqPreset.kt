package dev.melodify.uranophilelab.model

data class EqPreset(
    val id: String,
    val name: String,
    val gains: FloatArray, // 10 band gains in dB (-12 to +12)
    val description: String,
    val category: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EqPreset
        if (id != other.id) return false
        if (!gains.contentEquals(other.gains)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + gains.contentHashCode()
        return result
    }
}

object EqPresets {
    val kEqFrequencies = intArrayOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val kEqLabels = arrayOf("31", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    val kEqPresetCategories = linkedMapOf(
        "Reference" to listOf("flat", "studio-monitor", "audiophile", "studio-elite"),
        "Genres" to listOf("rock", "pop", "jazz", "classical", "hip-hop", "electronic"),
        "Focus" to listOf("bass-boost", "vocal-clarity", "treble-boost", "bass-texture", "midrange-magic", "high-definition", "presence-boost"),
        "Devices" to listOf("headphones", "earbuds", "speakers"),
        "Moods" to listOf("live-concert", "midnight-mode", "workout", "lounge-chill", "energy-surge"),
        "Signature" to listOf("warm-analog", "crystal-clear", "rich-full", "atmospheric", "vibrant-pop", "deep-impact", "silk-smooth", "balanced-premium", "dynamic-punch")
    )

    val kEqPresets = listOf(
        // Reference
        EqPreset(
            id = "flat",
            name = "Flat",
            gains = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            description = "Pure, uncolored reference. The truth of your audio, exactly as intended.",
            category = "Reference"
        ),
        EqPreset(
            id = "studio-monitor",
            name = "Studio Monitor",
            gains = floatArrayOf(1f, 0f, -1f, 1f, 2f, 3f, 2f, 3f, 4f, 2f),
            description = "Mix engineer's choice. Reveals flaws and beauty equally, honest and unforgiving.",
            category = "Reference"
        ),
        EqPreset(
            id = "audiophile",
            name = "Audiophile",
            gains = floatArrayOf(3f, 2f, 1f, 2f, 0f, 1f, 3f, 5f, 6f, 4f),
            description = "High-resolution refinement. Micro-details emerge, soundstage expands, realism heightened.",
            category = "Reference"
        ),
        EqPreset(
            id = "studio-elite",
            name = "Studio Elite",
            gains = floatArrayOf(2f, 1f, 0f, 2f, 1f, 3f, 4f, 6f, 7f, 5f),
            description = "Mastering-grade precision with artistic touch. Clinical accuracy meets musical emotion.",
            category = "Reference"
        ),

        // Genres
        EqPreset(
            id = "rock",
            name = "Rock",
            gains = floatArrayOf(5f, 3f, -2f, -3f, -2f, 2f, 5f, 7f, 6f, 4f),
            description = "Aggressive punch with scooped mids and soaring highs. Guitar riffs cut through, drums hit hard.",
            category = "Genres"
        ),
        EqPreset(
            id = "pop",
            name = "Pop",
            gains = floatArrayOf(3f, 4f, 5f, 4f, 2f, 1f, 4f, 6f, 5f, 3f),
            description = "Radio-ready sparkle with forward vocals and controlled bass. Bright, commercial, addictive.",
            category = "Genres"
        ),
        EqPreset(
            id = "jazz",
            name = "Jazz",
            gains = floatArrayOf(4f, 3f, 2f, 4f, 1f, -1f, 2f, 4f, 5f, 6f),
            description = "Warm club intimacy with natural instrument timbre. Brushes shimmer, upright bass resonates deeply.",
            category = "Genres"
        ),
        EqPreset(
            id = "classical",
            name = "Classical",
            gains = floatArrayOf(2f, 1f, 0f, 1f, -2f, -1f, 2f, 5f, 7f, 8f),
            description = "Concert hall grandeur with pristine string detail. Wide dynamic range reveals every orchestral layer.",
            category = "Genres"
        ),
        EqPreset(
            id = "hip-hop",
            name = "Hip-Hop",
            gains = floatArrayOf(8f, 6f, 3f, 1f, -1f, 0f, 2f, 4f, 6f, 5f),
            description = "Subwoofer-crushing lows with crystalline hi-hats. 808s rumble, vocals sit perfectly in the pocket.",
            category = "Genres"
        ),
        EqPreset(
            id = "electronic",
            name = "Electronic",
            gains = floatArrayOf(6f, 5f, 2f, 0f, 1f, 3f, 4f, 6f, 8f, 6f),
            description = "Synthesizer sweetness with extended sub-bass. Laser-sharp highs, pulsing energy throughout.",
            category = "Genres"
        ),

        // Focus
        EqPreset(
            id = "bass-boost",
            name = "Bass Boost",
            gains = floatArrayOf(9f, 7f, 4f, 2f, 0f, 0f, 1f, 2f, 1f, 0f),
            description = "Deep rumble without mud. Feel the kick drum in your chest, basslines become visceral.",
            category = "Focus"
        ),
        EqPreset(
            id = "vocal-clarity",
            name = "Vocal Clarity",
            gains = floatArrayOf(-3f, -2f, 2f, 6f, 7f, 6f, 5f, 3f, 1f, -1f),
            description = "Every word crystal clear, like the singer is beside you. Perfect for podcasts and storytelling.",
            category = "Focus"
        ),
        EqPreset(
            id = "treble-boost",
            name = "Treble Boost",
            gains = floatArrayOf(0f, 0f, 0f, 0f, 2f, 4f, 6f, 8f, 9f, 7f),
            description = "Air and sparkle cascade from above. Cymbals shimmer endlessly, acoustic guitars breathe life.",
            category = "Focus"
        ),
        EqPreset(
            id = "bass-texture",
            name = "Bass Texture",
            gains = floatArrayOf(7f, 6f, 5f, 3f, 1f, 0f, 2f, 3f, 4f, 3f),
            description = "Articulate low-end definition. Hear individual bass notes, not just rumble. Musical clarity.",
            category = "Focus"
        ),
        EqPreset(
            id = "midrange-magic",
            name = "Midrange Magic",
            gains = floatArrayOf(1f, 2f, 3f, 5f, 6f, 6f, 5f, 4f, 3f, 2f),
            description = "Where music lives and breathes. Guitars sing, pianos resonate, voices connect emotionally.",
            category = "Focus"
        ),
        EqPreset(
            id = "high-definition",
            name = "High Definition",
            gains = floatArrayOf(2f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 8f, 6f),
            description = "Ultra-detailed top-end extension. Hear into the music, revealing layers you never knew existed.",
            category = "Focus"
        ),
        EqPreset(
            id = "presence-boost",
            name = "Presence Boost",
            gains = floatArrayOf(1f, 2f, 3f, 5f, 7f, 8f, 7f, 6f, 5f, 3f),
            description = "Intimate forward projection. Vocalists whisper in your ear, instruments surround you closely.",
            category = "Focus"
        ),

        // Devices
        EqPreset(
            id = "headphones",
            name = "Headphones",
            gains = floatArrayOf(4f, 3f, 2f, 3f, 4f, 4f, 3f, 5f, 6f, 5f),
            description = "Immersive headphone optimization. Compensates for driver proximity, creates natural space.",
            category = "Devices"
        ),
        EqPreset(
            id = "earbuds",
            name = "Earbuds",
            gains = floatArrayOf(5f, 4f, 2f, 2f, 3f, 4f, 5f, 7f, 6f, 4f),
            description = "Earbud sweetening with enhanced presence. Small drivers deliver big sound, fatigue-free.",
            category = "Devices"
        ),
        EqPreset(
            id = "speakers",
            name = "Speakers",
            gains = floatArrayOf(3f, 2f, 2f, 4f, 3f, 2f, 3f, 5f, 6f, 4f),
            description = "Room-aware tuning for optimal speaker response. Balances reflections, tames resonances.",
            category = "Devices"
        ),

        // Moods
        EqPreset(
            id = "live-concert",
            name = "Live Concert",
            gains = floatArrayOf(4f, 3f, 2f, 3f, 4f, 3f, 6f, 8f, 7f, 5f),
            description = "Front-row energy without the crowd. Stadium reverb, amplifier grit, real performance presence.",
            category = "Moods"
        ),
        EqPreset(
            id = "midnight-mode",
            name = "Midnight Mode",
            gains = floatArrayOf(-4f, -2f, 2f, 5f, 4f, 4f, 3f, 2f, 0f, -2f),
            description = "Whisper-quiet dynamics for late hours. Explosions tamed, dialogue clear, neighbors sleeping.",
            category = "Moods"
        ),
        EqPreset(
            id = "workout",
            name = "Workout",
            gains = floatArrayOf(7f, 6f, 4f, 2f, 3f, 4f, 6f, 5f, 4f, 3f),
            description = "Adrenaline-pumping energy surge. Motivating punch pushes you harder, rhythm drives you forward.",
            category = "Moods"
        ),
        EqPreset(
            id = "lounge-chill",
            name = "Lounge Chill",
            gains = floatArrayOf(4f, 4f, 3f, 3f, 2f, 2f, 3f, 4f, 5f, 4f),
            description = "Relaxed sophistication for laid-back moments. Smooth jazz, downtempo, evening cocktails.",
            category = "Moods"
        ),
        EqPreset(
            id = "energy-surge",
            name = "Energy Surge",
            gains = floatArrayOf(6f, 6f, 5f, 3f, 4f, 5f, 7f, 6f, 5f, 4f),
            description = "High-octane motivation boost. Driving rhythm section propels you forward with unstoppable momentum.",
            category = "Moods"
        ),

        // Signature
        EqPreset(
            id = "warm-analog",
            name = "Warm Analog",
            gains = floatArrayOf(5f, 4f, 2f, 2f, 0f, 1f, 3f, 5f, 4f, 2f),
            description = "Vintage tape saturation warmth. Nostalgic tube glow, musical harmonic richness throughout.",
            category = "Signature"
        ),
        EqPreset(
            id = "crystal-clear",
            name = "Crystal Clear",
            gains = floatArrayOf(0f, 1f, 2f, 4f, 6f, 7f, 8f, 7f, 6f, 5f),
            description = "Transparent brilliance from top to bottom. Resolution so high, you hear the room, the breath.",
            category = "Signature"
        ),
        EqPreset(
            id = "rich-full",
            name = "Rich & Full",
            gains = floatArrayOf(5f, 4f, 3f, 3f, 4f, 5f, 4f, 6f, 5f, 3f),
            description = "Luxurious body and weight. Every frequency dense with harmonic content, musically satisfying.",
            category = "Signature"
        ),
        EqPreset(
            id = "atmospheric",
            name = "Atmospheric",
            gains = floatArrayOf(2f, 1f, 0f, 1f, 2f, 3f, 6f, 8f, 9f, 7f),
            description = "Ethereal soundscape expansion. Creates three-dimensional space, perfect for ambient immersion.",
            category = "Signature"
        ),
        EqPreset(
            id = "vibrant-pop",
            name = "Vibrant Pop",
            gains = floatArrayOf(4f, 5f, 6f, 5f, 3f, 4f, 6f, 8f, 7f, 5f),
            description = "Technicolor audio excitement. Pop hooks explode with energy, infectious and irresistible.",
            category = "Signature"
        ),
        EqPreset(
            id = "deep-impact",
            name = "Deep Impact",
            gains = floatArrayOf(10f, 8f, 5f, 3f, 1f, 2f, 4f, 6f, 7f, 6f),
            description = "Cinematic low-frequency authority. Explosions shake foundations, thunder rolls with power.",
            category = "Signature"
        ),
        EqPreset(
            id = "silk-smooth",
            name = "Silk Smooth",
            gains = floatArrayOf(3f, 3f, 2f, 3f, 4f, 4f, 3f, 2f, 0f, -1f),
            description = "Velvet-soft refinement removes all harshness. Hours of listening without a hint of fatigue.",
            category = "Signature"
        ),
        EqPreset(
            id = "balanced-premium",
            name = "Balanced Premium",
            gains = floatArrayOf(4f, 3f, 3f, 4f, 3f, 4f, 5f, 6f, 6f, 4f),
            description = "Sophisticated all-rounder with refined taste. Nothing exaggerated, everything enhanced perfectly.",
            category = "Signature"
        ),
        EqPreset(
            id = "dynamic-punch",
            name = "Dynamic Punch",
            gains = floatArrayOf(6f, 5f, 3f, 2f, 2f, 3f, 5f, 7f, 6f, 4f),
            description = "Explosive transient impact. Drums crack with authority, attacks are lightning-fast and precise.",
            category = "Signature"
        )
    )

    fun getPresetById(id: String?): EqPreset? {
        if (id == null) return null
        return kEqPresets.firstOrNull { it.id == id }
    }
}
