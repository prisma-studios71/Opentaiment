package app.opentaiment.feature.vehicle.data

import app.opentaiment.feature.vehicle.obd.ObdPid

/** Diagnostic trouble codes read from the car. */
data class DtcReport(
    /** Check Engine / MIL lamp state, null if the car didn't report it. */
    val milOn: Boolean?,
    /** Confirmed codes (mode 03) – these light up Check Engine. */
    val stored: List<String>,
    /** Pending codes (mode 07) – detected once, not confirmed yet. */
    val pending: List<String>,
    /** Permanent codes (mode 0A) – can't be cleared by a scan tool, the ECU clears them itself. */
    val permanent: List<String>,
    /** Engine values saved by the ECU at the moment the first fault was stored. */
    val freezeFrame: FreezeFrame? = null,
    /** Emission self-tests – what an inspection station checks. */
    val readiness: Readiness? = null,
) {
    val totalCount: Int get() = (stored + pending + permanent).distinct().size
}

data class FreezeFrame(val dtc: String, val values: Map<ObdPid, Double>)

data class ReadinessMonitor(val name: String, val complete: Boolean)

data class Readiness(val diesel: Boolean, val monitors: List<ReadinessMonitor>) {
    val incomplete: List<ReadinessMonitor> get() = monitors.filter { !it.complete }
    val ready: Boolean get() = incomplete.isEmpty()

    companion object {
        private val CONTINUOUS = listOf("Wypadanie zapłonów", "Układ paliwowy", "Podzespoły silnika")
        private val SPARK = listOf(
            "Katalizator", "Podgrzewany katalizator", "Układ EVAP (opary paliwa)", "Wtórne powietrze",
            "Klimatyzacja", "Sonda lambda", "Grzałka sondy lambda", "EGR / zmienne fazy",
        )
        private val DIESEL = listOf(
            "Katalizator NMHC", "Układ NOx / SCR (AdBlue)", null, "Ciśnienie doładowania",
            null, "Czujnik spalin", "Filtr cząstek stałych (DPF)", "EGR / zmienne fazy",
        )

        /**
         * Decodes bytes B, C, D of PID 01 01. B holds the continuous monitors (bits 0–2 available,
         * 4–6 incomplete) and the engine type (bit 3); C = available, D = incomplete non-continuous ones.
         */
        fun decode(data: IntArray): Readiness? {
            if (data.size < 4) return null
            val b = data[1]
            val c = data[2]
            val d = data[3]
            val diesel = b and 0x08 != 0
            val monitors = buildList {
                CONTINUOUS.forEachIndexed { i, name ->
                    if (b and (1 shl i) != 0) add(ReadinessMonitor(name, complete = b and (1 shl (i + 4)) == 0))
                }
                (if (diesel) DIESEL else SPARK).forEachIndexed { i, name ->
                    if (name != null && c and (1 shl i) != 0) add(ReadinessMonitor(name, complete = d and (1 shl i) == 0))
                }
            }
            return Readiness(diesel, monitors)
        }
    }
}

enum class DtcKind(val label: String, val explanation: String) {
    STORED("Zapisane", "Potwierdzone usterki – to one zapalają Check Engine."),
    PENDING("Oczekujące", "Wykryte raz, sterownik czeka czy się powtórzą."),
    PERMANENT("Stałe", "Znikną same, gdy sterownik potwierdzi naprawę – nie da się ich skasować."),
}

/** Polish descriptions of the most common generic (SAE J2012) codes, with a category fallback. */
object DtcDictionary {

    fun describe(code: String): String = KNOWN[code.uppercase()] ?: fallback(code.uppercase())

    fun system(code: String): String = when (code.firstOrNull()?.uppercaseChar()) {
        'P' -> "Napęd (silnik, skrzynia)"
        'C' -> "Podwozie (ABS, zawieszenie)"
        'B' -> "Nadwozie (poduszki, klimatyzacja)"
        'U' -> "Komunikacja między sterownikami"
        else -> "Nieznany układ"
    }

    /** P1xxx, P3xxx, B1xxx… are defined by each manufacturer, not by the standard. */
    fun isManufacturerSpecific(code: String): Boolean = code.getOrNull(1) in setOf('1', '3')

    private fun fallback(code: String): String {
        if (code.length != 5) return "Nieznany kod"
        if (isManufacturerSpecific(code)) return "Kod producenta – opis zależy od marki auta"
        if (code[0] != 'P') return "Usterka: ${system(code).lowercase()}"
        return when (code[2]) {
            '0', '1' -> "Usterka w układzie dozowania paliwa i powietrza"
            '2' -> "Usterka obwodu wtryskiwaczy / dozowania paliwa"
            '3' -> "Usterka zapłonu lub wypadanie zapłonów"
            '4' -> "Usterka układu kontroli emisji spalin"
            '5' -> "Usterka kontroli prędkości / biegu jałowego"
            '6' -> "Usterka sterownika lub jego obwodów"
            '7', '8', '9' -> "Usterka skrzyni biegów"
            else -> "Nieznana usterka układu napędowego"
        }
    }

    private val KNOWN = mapOf(
        "P0010" to "Obwód sterowania zmiennych faz rozrządu (wałek A, rząd 1)",
        "P0011" to "Zmienne fazy rozrządu – wałek A zbyt wyprzedzony (rząd 1)",
        "P0016" to "Niezgodność położenia wału korbowego i wałka rozrządu",
        "P0087" to "Za niskie ciśnienie paliwa w szynie",
        "P0088" to "Za wysokie ciśnienie paliwa w szynie",
        "P0100" to "Usterka obwodu przepływomierza (MAF)",
        "P0101" to "Przepływomierz (MAF) – wartości poza zakresem",
        "P0102" to "Przepływomierz (MAF) – za niski sygnał",
        "P0103" to "Przepływomierz (MAF) – za wysoki sygnał",
        "P0106" to "Czujnik ciśnienia w kolektorze (MAP) – wartości poza zakresem",
        "P0110" to "Usterka czujnika temperatury powietrza dolotowego",
        "P0113" to "Czujnik temperatury powietrza dolotowego – za wysoki sygnał",
        "P0115" to "Usterka czujnika temperatury płynu chłodniczego",
        "P0117" to "Czujnik temperatury płynu chłodniczego – za niski sygnał",
        "P0118" to "Czujnik temperatury płynu chłodniczego – za wysoki sygnał",
        "P0120" to "Usterka czujnika położenia przepustnicy",
        "P0121" to "Czujnik położenia przepustnicy – wartości poza zakresem",
        "P0122" to "Czujnik położenia przepustnicy – za niski sygnał",
        "P0128" to "Silnik za wolno się nagrzewa (najczęściej termostat)",
        "P0130" to "Sonda lambda przed katalizatorem (rząd 1) – usterka obwodu",
        "P0133" to "Sonda lambda przed katalizatorem (rząd 1) – wolna reakcja",
        "P0134" to "Sonda lambda przed katalizatorem (rząd 1) – brak aktywności",
        "P0135" to "Grzałka sondy lambda przed katalizatorem (rząd 1)",
        "P0136" to "Sonda lambda za katalizatorem (rząd 1) – usterka obwodu",
        "P0141" to "Grzałka sondy lambda za katalizatorem (rząd 1)",
        "P0171" to "Za uboga mieszanka (rząd 1) – często nieszczelność dolotu",
        "P0172" to "Za bogata mieszanka (rząd 1)",
        "P0174" to "Za uboga mieszanka (rząd 2)",
        "P0175" to "Za bogata mieszanka (rząd 2)",
        "P0190" to "Usterka czujnika ciśnienia paliwa w szynie",
        "P0201" to "Usterka obwodu wtryskiwacza – cylinder 1",
        "P0202" to "Usterka obwodu wtryskiwacza – cylinder 2",
        "P0203" to "Usterka obwodu wtryskiwacza – cylinder 3",
        "P0204" to "Usterka obwodu wtryskiwacza – cylinder 4",
        "P0234" to "Za wysokie ciśnienie doładowania (turbo)",
        "P0299" to "Za niskie ciśnienie doładowania (turbo)",
        "P0300" to "Losowe wypadanie zapłonów",
        "P0301" to "Wypadanie zapłonów – cylinder 1",
        "P0302" to "Wypadanie zapłonów – cylinder 2",
        "P0303" to "Wypadanie zapłonów – cylinder 3",
        "P0304" to "Wypadanie zapłonów – cylinder 4",
        "P0305" to "Wypadanie zapłonów – cylinder 5",
        "P0306" to "Wypadanie zapłonów – cylinder 6",
        "P0325" to "Usterka czujnika spalania stukowego",
        "P0335" to "Usterka czujnika położenia wału korbowego",
        "P0340" to "Usterka czujnika położenia wałka rozrządu",
        "P0380" to "Usterka obwodu świec żarowych",
        "P0400" to "Usterka układu recyrkulacji spalin (EGR)",
        "P0401" to "Za mały przepływ recyrkulacji spalin (EGR)",
        "P0402" to "Za duży przepływ recyrkulacji spalin (EGR)",
        "P0403" to "Usterka obwodu zaworu EGR",
        "P0420" to "Niska sprawność katalizatora (rząd 1)",
        "P0430" to "Niska sprawność katalizatora (rząd 2)",
        "P0440" to "Usterka układu odparowania paliwa (EVAP)",
        "P0442" to "Mały wyciek w układzie EVAP (często korek wlewu)",
        "P0455" to "Duży wyciek w układzie EVAP (często korek wlewu)",
        "P0456" to "Bardzo mały wyciek w układzie EVAP",
        "P0500" to "Usterka czujnika prędkości pojazdu",
        "P0505" to "Usterka sterowania biegiem jałowym",
        "P0506" to "Za niskie obroty biegu jałowego",
        "P0507" to "Za wysokie obroty biegu jałowego",
        "P0560" to "Nieprawidłowe napięcie instalacji",
        "P0562" to "Za niskie napięcie instalacji (akumulator / alternator)",
        "P0563" to "Za wysokie napięcie instalacji",
        "P0571" to "Usterka włącznika świateł stopu",
        "P0600" to "Błąd komunikacji wewnętrznej sterownika",
        "P0606" to "Usterka procesora sterownika silnika",
        "P0700" to "Usterka skrzyni biegów – szczegóły w sterowniku skrzyni",
        "P0715" to "Usterka czujnika prędkości wejściowej skrzyni",
        "P0741" to "Sprzęgło blokujące konwertera – za niska skuteczność",
        "P2002" to "Niska sprawność filtra cząstek stałych (DPF)",
        "P2463" to "Za dużo sadzy w filtrze cząstek stałych (DPF)",
        "P242F" to "Ograniczenie przepływu przez DPF – nagromadzony popiół",
        "U0001" to "Usterka magistrali CAN (szybkiej)",
        "U0100" to "Brak komunikacji ze sterownikiem silnika",
        "U0101" to "Brak komunikacji ze sterownikiem skrzyni biegów",
        "U0121" to "Brak komunikacji ze sterownikiem ABS",
        "U0140" to "Brak komunikacji ze sterownikiem nadwozia (BCM)",
        "U0155" to "Brak komunikacji z zestawem wskaźników",
    )
}

/** Polish plural: 1 błąd, 2–4 błędy, 5+ błędów (but 12–14 błędów). */
fun dtcCountLabel(count: Int): String {
    val word = when {
        count == 1 -> "błąd"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "błędy"
        else -> "błędów"
    }
    return "$count $word"
}
