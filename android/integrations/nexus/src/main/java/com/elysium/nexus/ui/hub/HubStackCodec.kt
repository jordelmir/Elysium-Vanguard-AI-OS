package com.elysium.nexus.ui.hub

import com.elysium.nexus.core.device.ConsoleSubcategory
import com.elysium.nexus.core.device.DeviceCatalog
import com.elysium.nexus.core.device.DeviceCategory
import com.elysium.nexus.core.device.DeviceTemplate

/**
 * Serializes [HubDestination] values to stable strings so the
 * navigation stack survives Activity recreation (rotation, process
 * death) and the app reopens where the user left it instead of
 * "from zero".
 *
 * Destinations that hold live objects (MacPairing/MacControl hosts,
 * AutomationEditor/SceneEditor payloads) are deliberately not
 * restorable — they degrade to their parent list. Everything the
 * user does in the store (TV controls, console pickers, universal
 * probe, learned signals) is fully restorable.
 */
object HubStackCodec {

    private val ALL_SUBCATEGORIES: List<ConsoleSubcategory> =
        DeviceCategory.playstationSubcategories +
            DeviceCategory.xboxSubcategories +
            DeviceCategory.nintendoSubcategories

    /** @return stable string for [destination], or `null` when not restorable. */
    fun encode(destination: HubDestination): String? = when (destination) {
        HubDestination.Hub -> "hub"
        HubDestination.TvControls -> "tvc"
        is HubDestination.Category -> "cat:${destination.category.name}"
        is HubDestination.ConsolePicker ->
            "cpk:${destination.category.name}:${destination.subcategory.id}"
        is HubDestination.ConsoleDevice -> "cdev:${destination.template.id}"
        is HubDestination.Connect -> "con:${destination.template.id}"
        HubDestination.InstalledProfiles -> "ip"
        is HubDestination.Control -> "ctl:${destination.profileId}"
        HubDestination.UniversalRemote -> "ur"
        HubDestination.WifiUniversal -> "wifi"
        HubDestination.UsbC -> "usbc"
        is HubDestination.AcControl -> "ac:${destination.template.id}"
        is HubDestination.IrLearner -> "learn"
        HubDestination.AutomationList -> "al"
        HubDestination.SceneList -> "sl"
        HubDestination.MacDiscovery -> "macd"
        HubDestination.MacInternet -> "macnet"
        // Live objects — degrade by omission.
        is HubDestination.MacPairing -> null
        is HubDestination.MacControl -> null
        is HubDestination.AutomationEditor -> null
        is HubDestination.SceneEditor -> null
    }

    /** @return decoded destination, or `null` when the code is unknown/unresolvable. */
    fun decode(code: String): HubDestination? {
        if (code.isEmpty()) return null
        val head = code.substringBefore(":")
        return when (head) {
            "hub" -> HubDestination.Hub
            "tvc" -> HubDestination.TvControls
            "cat" -> byName(code.removePrefix("cat:"))?.let { HubDestination.Category(it) }
            "cpk" -> {
                val rest = code.removePrefix("cpk:")
                val catName = rest.substringBefore(":")
                val subId = rest.substringAfter(":", "")
                val cat = byName(catName) ?: return null
                val sub = ALL_SUBCATEGORIES.firstOrNull { it.id == subId } ?: return null
                HubDestination.ConsolePicker(cat, sub)
            }
            "cdev" -> templateById(code.removePrefix("cdev:"))?.let { HubDestination.ConsoleDevice(it) }
            "con" -> templateById(code.removePrefix("con:"))?.let { HubDestination.Connect(it) }
            "ip" -> HubDestination.InstalledProfiles
            "ctl" -> {
                val id = code.removePrefix("ctl:")
                if (id.isBlank()) null else HubDestination.Control(id)
            }
            "ur" -> HubDestination.UniversalRemote
            "wifi" -> HubDestination.WifiUniversal
            "usbc" -> HubDestination.UsbC
            "ac" -> templateById(code.removePrefix("ac:"))?.let { HubDestination.AcControl(it) }
            "learn" -> HubDestination.IrLearner(null)
            "al" -> HubDestination.AutomationList
            "sl" -> HubDestination.SceneList
            "macd" -> HubDestination.MacDiscovery
            "macnet" -> HubDestination.MacInternet
            else -> null
        }
    }

    fun encodeStack(stack: List<HubDestination>): List<String> =
        stack.mapNotNull { encode(it) }

    fun decodeStack(codes: List<String>): List<HubDestination> =
        codes.mapNotNull { decode(it) }.ifEmpty { listOf(HubDestination.Hub) }

    private fun byName(name: String): DeviceCategory? =
        DeviceCategory.entries.firstOrNull { it.name == name }

    private fun templateById(id: String): DeviceTemplate? =
        DeviceCatalog.all.firstOrNull { it.id == id }
}
