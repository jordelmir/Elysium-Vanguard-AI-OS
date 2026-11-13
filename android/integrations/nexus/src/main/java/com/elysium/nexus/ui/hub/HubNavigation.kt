package com.elysium.nexus.ui.hub

import com.elysium.nexus.core.device.ConsoleSubcategory
import com.elysium.nexus.core.device.DeviceCategory
import com.elysium.nexus.core.device.DeviceTemplate

/**
 * §15/§3 Authoritative navigation state.
 *
 * §3: Control destination carries ONLY profileId.
 * The screen loads the profile from Room by this ID.
 * No object transport. No temporary state. No DeviceTemplate in Control.
 */
sealed class HubDestination {
    object Hub : HubDestination()
    object TvControls : HubDestination()
    data class Category(val category: DeviceCategory) : HubDestination()
    data class ConsolePicker(val category: DeviceCategory, val subcategory: ConsoleSubcategory) : HubDestination()
    data class ConsoleDevice(val template: DeviceTemplate) : HubDestination()
    data class Connect(val template: DeviceTemplate) : HubDestination()
    object InstalledProfiles : HubDestination()

    /**
     * §3 Authoritative Control destination — profileId ONLY.
     * TvControlScreen loads the profile from Room by this ID.
     */
    data class Control(val profileId: String) : HubDestination()

    object MacDiscovery : HubDestination()
    /** Restorable route; the pairing secret remains only in the screen's transient state. */
    object MacInternet : HubDestination()
    data class MacPairing(val host: com.elysium.nexus.ui.mac.DiscoveredHost) : HubDestination()
    data class MacControl(val host: com.elysium.nexus.ui.mac.DiscoveredHost) : HubDestination()
    object UniversalRemote : HubDestination()
    object WifiUniversal : HubDestination()
    object UsbC : HubDestination()
    data class AcControl(val template: DeviceTemplate) : HubDestination()
    data class IrLearner(val learnResult: com.elysium.nexus.fabric.infrared.IrLearner.LearnResult?) : HubDestination()
    object AutomationList : HubDestination()
    data class AutomationEditor(val automation: com.elysium.nexus.fabric.automation.Automation? = null) : HubDestination()
    object SceneList : HubDestination()
    data class SceneEditor(val scene: com.elysium.nexus.fabric.automation.Scene? = null) : HubDestination()
}

class HubStack(initial: HubDestination = HubDestination.Hub) {
    private val items: MutableList<HubDestination> = mutableListOf(initial)
    fun current(): HubDestination = items.last()
    fun push(destination: HubDestination) { items.add(destination) }
    fun pop() { if (items.size > 1) items.removeAt(items.lastIndex) }
    fun replaceTop(destination: HubDestination) { items.removeAt(items.lastIndex); items.add(destination) }
    fun snapshot(): List<HubDestination> = items.toList()
}
