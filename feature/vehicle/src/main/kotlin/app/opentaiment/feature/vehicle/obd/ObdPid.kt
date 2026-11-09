package app.opentaiment.feature.vehicle.obd

import java.util.Locale

/** OBD-II mode 01 PIDs we use, with SAE J1979 decoding formulas (A, B = data bytes). */
enum class ObdPid(val code: Int, private val bytes: Int, private val decode: (IntArray) -> Double) {
    ENGINE_LOAD(0x04, 1, { it[0] * 100.0 / 255 }),
    COOLANT_TEMP(0x05, 1, { it[0] - 40.0 }),
    INTAKE_MAP(0x0B, 1, { it[0].toDouble() }),
    RPM(0x0C, 2, { (it[0] * 256 + it[1]) / 4.0 }),
    SPEED(0x0D, 1, { it[0].toDouble() }),
    INTAKE_TEMP(0x0F, 1, { it[0] - 40.0 }),
    MAF(0x10, 2, { (it[0] * 256 + it[1]) / 100.0 }),
    THROTTLE(0x11, 1, { it[0] * 100.0 / 255 }),
    FUEL_LEVEL(0x2F, 1, { it[0] * 100.0 / 255 }),
    AMBIENT_TEMP(0x46, 1, { it[0] - 40.0 }),
    OIL_TEMP(0x5C, 1, { it[0] - 40.0 }),
    FUEL_RATE(0x5E, 2, { (it[0] * 256 + it[1]) / 20.0 }),
    ;

    val command: String get() = String.format(Locale.US, "01%02X", code)

    fun decodeOrNull(data: IntArray): Double? = if (data.size >= bytes) decode(data) else null
}
