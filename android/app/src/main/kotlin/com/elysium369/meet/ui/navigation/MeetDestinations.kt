package com.elysium369.meet.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * Elysium Centralized Destinations Contract.
 * Guarantees that both Vanguard Classic and Vanguard Command navigate to identical targets.
 */
object MeetDestinations {
    const val HOME = "home"
    const val SCANNER = "scanner"
    const val DTCS = "dtc"
    const val GARAGE = "garage"
    const val SETTINGS = "settings"
    const val TERMINAL = "terminal"
    const val AI = "ai"
    const val HEALTH_SCORE = "health_score"
    const val MAINTENANCE = "maintenance"
    const val TRIP_LOG = "trips"
    const val ENGINE_3D = "component_locator"
    const val PERITO = "meet_perito"
    const val DNA = "meet_dna"
    const val VEHICLE_ACCESS = "vehicle_access"
    const val FINDINGS = "findings"
    const val HUD = "hud"
    const val DVIR = "dvir"
    const val COMPONENT_LOCATOR = "component_locator"
    const val PROTOCOL_LEARNING = "adaptation"
    const val ADAPTER_DIAGNOSTICS = "clone_test"
    const val LIVE_STREAM = "live_link"
    const val MESSAGES = "messages"
    const val MECHANIC_SERVICES = "repair_network"
    const val TOW_TRUCK = "tow_truck_service"
    const val PARTS_STORE = "marketplace"
    const val PRO_HUB = "pro_hub"
    const val TRUST_CENTER = "platform_trust_center"
    const val RIDE_HOME = "ride_service"
    /** Passenger and driver entry points converge on the wired Ride product. */
    const val RIDE_PASSENGER_REQUEST = RIDE_HOME
    const val RIDE_DRIVER_MODE = RIDE_HOME
    const val RIDE_DRIVER_REGISTRATION = "ride_driver_registration"
    const val RIDE_ACTIVE_TRACKING = "ride_active_tracking"
    const val RIDE_SCHEDULE = "ride_schedule"
    const val RIDE_CENTER = "ride_center"

    /** Unwired prototypes retained for strangler migration, never as Home entry points. */
    const val RIDE_PASSENGER_EXPERIMENT = "ride_passenger_request"
    const val RIDE_DRIVER_EXPERIMENT = "ride_driver_cockpit"
    const val CAMPAIGNS = "reports"
    const val BATTERY_HEALTH = "health_score"
    const val CONNECT = "connect"
    const val LEARNING_HUB = "learning_hub"
    const val ELYSIUM_LEARNING_OS = "elysium_learning_os"
    const val MISSION_DETAIL = "mission_detail"
    const val MULTIMETER_SIMULATION = "multimeter_simulation"
    const val CAPABILITY_PASSPORT = "capability_passport"
    const val LEGAL_VANGUARD = "legal_vanguard"
    const val PROPERTIES = "elysium_properties"
    const val FUEL_REWARDS = "fuel_rewards"
    const val ELYSIUM_SERVICES = "elysium_services"
    const val SERVICES_ACTIVE = "services_active"
    const val ELYSIUM_DELIVERIES = "elysium_deliveries"
    const val SERVICES_COMPLETED = "services_completed"
    const val PROVIDER_SERVICES_CONFIG = "provider_services_config"
    const val AGENT_STORE = "agent_store"
    const val UAP_XENOLOGY = "uap_xenology"
    const val DRAGON_CALC = "dragon_calc"
    const val NEXUS_CONTROL = "elysium_nexxus_control"
    const val SCREEN_MIRROR = "elysium_screen_mirror"
    const val FILE_MANAGER = "elysium_file_manager"
    const val RECORD_SHIELD = "elysium_record_shield"

    // ELYSIUM LINKSPHERE (Universal Social Link Layer)
    const val LINKSPHERE = "linksphere"
    const val LINKSPHERE_COMPOSER = "linksphere_composer"
    const val LINKSPHERE_SETTINGS = "linksphere_settings"

    // SUPREME BASS (Neon Integration)
    const val SUPREME_BASS = "supreme_bass"
    const val SUPREME_BASS_BOOST = "supreme_bass_boost"
    const val SUPREME_SUBWOOFER_TUNE = "supreme_subwoofer_tune"
    const val SUPREME_AUDIO_VIZ = "supreme_audio_viz"
    const val SUPREME_CAR_PRESETS = "supreme_car_presets"
    const val SUPREME_FREQ_RESPONSE = "supreme_freq_response"

    // SAFETY FOUNDATION V1
    const val SAFETY_HOME = "safety"
    const val SAFETY_MAP = "safety/map"
    const val SAFETY_REPORT = "safety/report"
    const val SAFETY_REPORT_LOCATION = "safety/report/location/{mode}"
    const val SAFETY_MY_REPORTS = "safety/my-reports"
    const val SAFETY_CASES = "safety/cases"
    const val SAFETY_TIMELINES = "safety/timelines"
    const val SAFETY_CASE_DETAIL = "safety/case/{caseId}"
    const val SAFETY_ACCOUNTABILITY = "safety/accountability"
    const val SAFETY_OBSERVATORY = "safety/observatory"
    const val SAFETY_RESEARCH = "safety/research"
    const val SAFETY_INSTITUTIONAL = "safety/institutional"
    const val SAFETY_INVESTIGATIVE_WORKSPACE = "safety/investigative-workspace"
    const val SAFETY_REVIEWER_ACCREDITATION = "safety/reviewer-accreditation"
    const val SAFETY_INSTITUTIONAL_PRESENTATION = "safety/institutional-presentation"
}

fun androidx.navigation.NavController.safeNavigate(route: String) {
    try {
        navigate(route)
    } catch (e: Exception) {
        android.util.Log.e("Navigation", "Failed to navigate to route: $route", e)
    }
}

/**
 * One navigation contract for every visible back affordance.
 *
 * A regular visit pops exactly one entry. A destination reached without a
 * usable parent (for example from a deep link) returns to Home instead of
 * becoming a dead end. Home itself is never duplicated.
 */
fun NavController.backOrHome(): Boolean {
    val currentRoute = currentDestination?.route
    val hasPrevious = previousBackStackEntry != null
    val startRoute = runCatching { graph.findStartDestination().route }.getOrNull() ?: MeetDestinations.HOME
    return when (
        MeetBackStackPolicy.action(
            currentRoute = currentRoute,
            hasPreviousEntry = hasPrevious,
            startRoute = startRoute,
        )
    ) {
        MeetBackStackPolicy.Action.POP_ONE -> {
            if (popBackStack()) true else navigateHomeFallback(startRoute)
        }
        MeetBackStackPolicy.Action.NAVIGATE_HOME -> navigateHomeFallback(startRoute)
        MeetBackStackPolicy.Action.STAY_HOME -> false
    }
}

/** Save and restore each top-level branch instead of recreating it on every tap. */
fun NavController.navigateTopLevel(route: String) {
    if (currentDestination?.route == route) return
    // Prefer returning to an existing top-level entry. This is reliable even
    // when the current screen was opened from a nested ride/profile flow.
    if (popBackStack(route, false)) return
    runCatching {
        navigate(route) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }.onFailure { error ->
        android.util.Log.w("Navigation", "Top-level navigation fallback for $route", error)
        navigate(route) { launchSingleTop = true }
    }
}

private fun NavController.navigateHomeFallback(startRoute: String = MeetDestinations.HOME): Boolean {
    val target = MeetDestinations.HOME
    navigate(target) {
        popUpTo(graph.findStartDestination().id) { inclusive = false }
        launchSingleTop = true
        restoreState = true
    }
    return true
}

object MeetBackStackPolicy {
    enum class Action { POP_ONE, NAVIGATE_HOME, STAY_HOME }

    fun action(
        currentRoute: String?,
        hasPreviousEntry: Boolean,
        startRoute: String = MeetDestinations.HOME,
    ): Action = when {
        currentRoute == MeetDestinations.HOME -> Action.STAY_HOME
        currentRoute == startRoute && startRoute == MeetDestinations.HOME -> Action.STAY_HOME
        hasPreviousEntry -> Action.POP_ONE
        else -> Action.NAVIGATE_HOME
    }
}
