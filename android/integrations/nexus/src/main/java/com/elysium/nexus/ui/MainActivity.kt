package com.elysium.nexus.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.elysium.nexus.R
import com.elysium.nexus.core.device.DeviceTemplate
import com.elysium.nexus.core.engine.CanonicalInputEngine
import com.elysium.nexus.core.engine.EngineState
import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.engine.TransportBinding
import com.elysium.nexus.core.filter.StickConfig
import com.elysium.nexus.core.haptics.AndroidHaptics
import com.elysium.nexus.core.haptics.Haptics
import com.elysium.nexus.core.haptics.SettingsAwareHaptics
import com.elysium.nexus.core.latency.LatencyTracker
import com.elysium.nexus.core.motion.AndroidMotionSensorSource
import com.elysium.nexus.core.motion.MotionSensorSource
import com.elysium.nexus.core.motion.NullMotionSensorSource
import com.elysium.nexus.core.posture.AndroidPostureObserver
import com.elysium.nexus.core.posture.NullPostureObserver
import com.elysium.nexus.core.posture.PostureObserver
import com.elysium.nexus.core.profile.AndroidProfileShareLauncher
import com.elysium.nexus.core.profile.LastDevice
import com.elysium.nexus.core.profile.LastDeviceMemory
import com.elysium.nexus.core.profile.Profile
import com.elysium.nexus.core.profile.ProfileActions
import com.elysium.nexus.core.profile.ProfileImportResult
import com.elysium.nexus.core.profile.ProfileImporter
import com.elysium.nexus.core.profile.ProfileShareBuilder
import com.elysium.nexus.core.settings.AndroidAppSettingsStore
import com.elysium.nexus.core.settings.AppSettings
import com.elysium.nexus.core.settings.AppSettingsStore
import com.elysium.nexus.core.transport.LocalEchoTransport
import com.elysium.nexus.core.transport.mac.MacTransport
import com.elysium.nexus.databases.profile.ProfileDatabase
import com.elysium.nexus.databases.profile.ProfileRepository
import com.elysium.nexus.databases.profile.RoomProfileRepository
import com.elysium.nexus.fabric.infrared.AndroidIrTransmitter
import com.elysium.nexus.ui.splash.SplashScreen
import com.elysium.nexus.ui.connect.IrConnectFlow
import com.elysium.nexus.ui.control.TvControlScreen
import com.elysium.nexus.ui.help.GuidedTourOverlay
import com.elysium.nexus.ui.hub.ConsoleDeviceScreen
import com.elysium.nexus.ui.hub.ConsoleSubcategoryScreen
import com.elysium.nexus.ui.hub.DeviceCategoryScreen
import com.elysium.nexus.ui.hub.HubDestination
import com.elysium.nexus.ui.hub.HubScreen
import com.elysium.nexus.ui.hub.TvControlsSection
import com.elysium.nexus.ui.mac.MacControlSurfaceScreen
import com.elysium.nexus.ui.mac.MacDiscoveryScreen
import com.elysium.nexus.ui.mac.MacPairingScreen
import com.elysium.nexus.ui.mac.NexusInternetScreen
import com.elysium.nexus.ui.mac.ManualAddHostDialog
import com.elysium.nexus.ui.settings.SettingsDialog
import com.elysium.nexus.ui.theme.ElysiumTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * The first `Activity`. The first end-to-end milestone.
 *
 * ## Phase ULT.3 — Hierarchical navigation
 *
 * The activity hosts a **navigation stack** of
 * [HubDestination]s. The user starts on the
 * Hub (home), taps a category, picks a device,
 * connects it via the IR flow, and ends up on
 * the control surface. The back button pops the
 * stack.
 *
 * The activity is a thin shell. The visual
 * hierarchy is in
 * [com.elysium.nexus.ui.hub.HubScreen],
 * [com.elysium.nexus.ui.hub.DeviceCategoryScreen],
 * [com.elysium.nexus.ui.connect.IrConnectFlow],
 * and
 * [com.elysium.nexus.ui.control.TvControlScreen].
 * The activity wires them together and provides
 * the shared state (the engine, the profile
 * repository, the IR transmitter, the haptic
 * feedback).
 *
 * ## First-launch guided tour
 *
 * The activity shows a 3-step
 * [GuidedTourOverlay] on the very first launch
 * (when the SharedPreferences `elysium.firstLaunch`
 * is `true`). After the tour, the preference is
 * set to `false` and the tour is never shown
 * again.
 */
class MainActivity : ComponentActivity() {

    private val tag = "ElysiumNexus"
    private var engine: CanonicalInputEngine? = null
    private var latencyTracker: LatencyTracker? = null
    private var activityScope: CoroutineScope? = null
    private var sceneEngine: com.elysium.nexus.fabric.automation.ConcreteAutomationEngineService? = null
    private var sceneRegistry: com.elysium.nexus.fabric.automation.SceneRegistry? = null
    private var driverJob: Job? = null
    private var latencyJob: Job? = null
    private var motionJob: Job? = null
    private var motionSource: MotionSensorSource? = null
    private var postureSource: PostureObserver? = null
    private var postureJob: Job? = null
    private var profileRepository: ProfileRepository? = null
    private var transportBinding: TransportBinding? = null
    private var transportJob: Job? = null
    private var shareLauncher: AndroidProfileShareLauncher? = null
    private var settingsStore: AppSettingsStore? = null
    private var haptics: Haptics? = null
    private var irTransmitter: AndroidIrTransmitter? = null
    private var acStateStore: com.elysium.nexus.core.settings.AcStateStore? = null
    private var irRepository: com.elysium.nexus.databases.ir.IrRepository? = null
    private var automationDefinitionStore: com.elysium.nexus.fabric.automation.AutomationDefinitionStore? = null
    private var automationStore: List<com.elysium.nexus.fabric.automation.Automation> = emptyList()
    private val inMemoryAutomationStore = com.elysium.nexus.fabric.automation.DefaultAutomationStore()
    private val profileFlow: MutableStateFlow<Profile?> = MutableStateFlow(null)
    private val allProfilesFlow: MutableStateFlow<List<Profile>> = MutableStateFlow(emptyList())
    private val transportFlow: MutableStateFlow<com.elysium.nexus.core.transport.ControllerTransport?> = MutableStateFlow(null)
    private val settingsFlow: MutableStateFlow<AppSettings> = MutableStateFlow(AppSettings())
    private val connectedDeviceFlow: MutableStateFlow<DeviceTemplate?> = MutableStateFlow(null)
    private val postureFlow: MutableStateFlow<com.elysium.nexus.core.posture.Posture> =
        MutableStateFlow(com.elysium.nexus.core.posture.Posture.UNKNOWN)
    private val lastDeviceFlow: MutableStateFlow<com.elysium.nexus.core.profile.LastDevice?> =
        MutableStateFlow(null)
    private val scenesFlow: MutableStateFlow<List<com.elysium.nexus.fabric.automation.Scene>> =
        MutableStateFlow(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(tag, "MainActivity.onCreate — Phase ULT.3 hierarchical navigation")
        // Phase ULT.8 — load the last connected
        // device (Mac/PC or BT) so the Hub can
        // show a Quick Connect card.
        try {
            lastDeviceFlow.value = LastDeviceMemory(this).get()
        } catch (e: Throwable) {
            Log.w(tag, "LastDeviceMemory load failed: ${e.message}")
        }

        val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val latencyTracker = LatencyTracker()
        val engine = CanonicalInputEngine(
            leftStickConfig = StickConfig(),
            rightStickConfig = StickConfig(),
            scope = engineScope,
            latencyTracker = latencyTracker
        )
        this.engine = engine
        this.latencyTracker = latencyTracker
        driveEngineToActive(engine)

        val profileRepo: ProfileRepository = RoomProfileRepository(
            ProfileDatabase.getInstance(this).profileDao()
        )
        this.profileRepository = profileRepo
        runBlocking {
            if (profileRepo.count() == 0) {
                profileRepo.upsert(
                    Profile.defaultProfile(now = System.currentTimeMillis())
                )
            }
            profileFlow.value = profileRepo.firstOrNull()
            allProfilesFlow.value = profileRepo.all()
        }

        val settingsStore: AppSettingsStore = AndroidAppSettingsStore(this)
        this.settingsStore = settingsStore
        settingsFlow.value = settingsStore.current
        val haptics: Haptics = SettingsAwareHaptics(
            inner = AndroidHaptics(this),
            settingsFlow = settingsFlow
        )
        this.haptics = haptics

        val defaultTransport = LocalEchoTransport()
        runBlocking {
            defaultTransport.start()
            defaultTransport.connect()
        }
        transportFlow.value = defaultTransport

        val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        this.activityScope = activityScope

        // §34 Automation engine — wired to the Run button. The action
        // dispatcher is honest: no device adapters are registered at
        // this phase, so every dispatch is reported as not delivered
        // (the engine records it; we never fabricate execution).
        sceneEngine = com.elysium.nexus.fabric.automation.ConcreteAutomationEngineService(
            actionDispatcher = com.elysium.nexus.fabric.automation.UniversalActionDispatcher { _, _ ->
                android.util.Log.w(tag, "Automation dispatch: no device adapter registered")
                false
            }
        )

        // §36 Durable scenes — Room-backed registry. Scenes saved here
        // survive process death and app restarts (schema v8, table `scenes`).
        sceneRegistry = com.elysium.nexus.fabric.automation.RoomSceneRegistry(
            com.elysium.nexus.fabric.profile.db.ElysiumUserDatabase.getInstance(this).sceneDao()
        )
        activityScope.launch {
            sceneRegistry?.observeScenes()?.collect { scenesFlow.value = it }
        }

        // PHASE 2 wiring: revalidate installed IR profiles against the
        // current catalog. Runs once at startup on the main scope (the
        // service hops to Dispatchers.IO internally). If the catalog
        // changed since install: bindings are resolved again, migrated
        // when a fingerprint-unique equivalent exists, and the profile
        // hash is only advanced when EVERY binding is valid — a broken
        // binding never destroys a profile (per-binding decisions).
        activityScope.launch {
            try {
                val installedRepo = com.elysium.nexus.fabric.profile
                    .InstalledIrProfileRepository(this@MainActivity)
                val installed = installedRepo.getAllProfilesSuspend()
                if (installed.isEmpty()) return@launch
                val revalidation = com.elysium.nexus.fabric.profile
                    .ProfileRevalidationService(this@MainActivity)
                for (profile in installed) {
                    val result = revalidation.revalidateProfile(profile)
                    if (result.catalogHashMatches) continue
                    if (result.allBindingsValid) {
                        revalidation.applyRevalidation(profile, result)
                        android.util.Log.i(
                            tag,
                            "Profile ${profile.brand}/${profile.model} revalidated " +
                                "(${result.bindingResults.size} bindings, hash advanced)"
                        )
                    } else {
                        android.util.Log.w(
                            tag,
                            "Profile ${profile.brand}/${profile.model}: " +
                                "revalidation action needed — ${result.bindingResults.size} " +
                                "bindings checked, userAction=${result.needsUserAction}"
                        )
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.w(tag, "Profile revalidation skipped: ${e.message}")
            }
        }

        val transportBinding = TransportBinding(defaultTransport)
        this.transportBinding = transportBinding
        transportJob = engine.state
            .onEach { state -> transportBinding.forwardRealtime(state) }
            .launchIn(activityScope)

        shareLauncher = AndroidProfileShareLauncher(this)

        // §6 Debug-only file telemetry — readable via
        // `adb shell run-as com.elysium.nexus.controller cat files/elysium-ir.log`
        // because MagicOS encrypts app-process logcat (`(HKS)`).
        com.elysium.nexus.fabric.infrared.FileLog.initialize(this)
        // V0.6.3 Phase 1: structured IR diagnostics
        com.elysium.nexus.fabric.infrared.IrRuntimeDiagnostics.initialize(
            isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        )

        // IR transmitter — the FAB equivalent for
        // the TV connection flow. The transmitter
        // is a no-op if the phone has no IR
        // blaster.
        val ir = AndroidIrTransmitter(this)
        this.irTransmitter = ir
        this.acStateStore = com.elysium.nexus.core.settings.AcStateStore(this)

        // IR command database — persists learned
        // IR signals from the IR Learner screen.
        val irDb = com.elysium.nexus.databases.ir.IrCommandDatabase.getInstance(this)
        this.irRepository = com.elysium.nexus.databases.ir.RoomIrRepository(
            irDb.learnedIrCommandDao()
        )

        // Automation definition store — persists
        // user-created automations across sessions.
        val autoStore = com.elysium.nexus.fabric.automation.AutomationDefinitionStore(this)
        this.automationDefinitionStore = autoStore
        this.automationStore = autoStore.loadAll()

        driverJob = engine.state
            .onEach { state -> logState(state) }
            .launchIn(activityScope)

        latencyJob = activityScope.launch {
            while (true) {
                delay(1000L)
                val snapshot = latencyTracker.snapshot()
                if (snapshot.count > 0) {
                    Log.i(
                        tag,
                        "latency[count=${snapshot.count}]: " +
                            "p50=${snapshot.p50 / 1_000_000f}ms, " +
                            "p95=${snapshot.p95 / 1_000_000f}ms, " +
                            "p99=${snapshot.p99 / 1_000_000f}ms, " +
                            "max=${snapshot.max / 1_000_000f}ms"
                    )
                }
            }
        }

        val motionSource: MotionSensorSource = try {
            AndroidMotionSensorSource(this)
        } catch (e: Throwable) {
            Log.w(tag, "AndroidMotionSensorSource failed; using NullMotionSensorSource.", e)
            NullMotionSensorSource()
        }
        this.motionSource = motionSource
        motionJob = activityScope.launch {
            try {
                motionSource.samples().collect { sample ->
                    engine.submitMotion(sample)
                }
            } catch (e: Throwable) {
                Log.w(tag, "Motion sample collection failed; motion is dormant.", e)
            }
        }

        val postureSource: PostureObserver = try {
            AndroidPostureObserver(this, activityScope)
        } catch (e: Throwable) {
            Log.w(tag, "AndroidPostureObserver failed; using NullPostureObserver.", e)
            NullPostureObserver()
        }
        this.postureSource = postureSource
        postureJob = activityScope.launch {
            try {
                postureSource.postures().collect { posture ->
                    // Phase ULT.6 — foldable posture drives the
                    // control surface layout. In HALF_OPENED
                    // mode the screen is split along the
                    // hinge; in CLOSED mode the cover-screen
                    // view is shown.
                    postureFlow.value = posture
                }
            } catch (e: Throwable) {
                Log.w(tag, "Posture observation failed; posture is dormant.", e)
            }
        }

        // === Compose UI ===========================================
        setContent {
            ElysiumTheme {
                val connectedDevice by connectedDeviceFlow.collectAsState()
                val settings by settingsFlow.collectAsState()
                val posture by postureFlow.collectAsState()
                val lastDevice by lastDeviceFlow.collectAsState()
                val navStack = rememberSaveable(
                    stateSaver = Saver<List<HubDestination>, List<String>>(
                        save = { stack -> com.elysium.nexus.ui.hub.HubStackCodec.encodeStack(stack) },
                        restore = { codes ->
                            val restored = com.elysium.nexus.ui.hub.HubStackCodec.decodeStack(codes)
                            // MacTransport is intentionally in-memory and cannot
                            // survive Android process/activity recreation. Never
                            // restore a control surface whose socket and keys no
                            // longer exist; that produces a convincing but dead UI.
                            if (restored.any {
                                    it is HubDestination.MacPairing ||
                                        it is HubDestination.MacControl
                                }
                            ) {
                                Log.w(tag, "Mac route restored without its transport; returning Nexus to Home")
                                listOf(HubDestination.Hub)
                            } else {
                                restored.ifEmpty { listOf(HubDestination.Hub) }
                            }
                        }
                    )
                ) { mutableStateOf(listOf(HubDestination.Hub)) }
                val settingsVisible = remember { mutableStateOf(false) }
                val tourVisible = remember { mutableStateOf(isFirstLaunch()) }
                val manualAddVisible = remember { mutableStateOf(false) }
                var splashVisible by rememberSaveable { mutableStateOf(true) }
                // §5 Learning — the live capture result arrives
                // asynchronously from IrCaptureBridge (Nexus Receiver
                // over Wi-Fi / USB-C). Survives recomposition while
                // the IrLearner screen is on the stack.
                val learnerResultState = remember {
                    mutableStateOf<com.elysium.nexus.fabric.infrared.IrLearner.LearnResult?>(null)
                }
                // Phase ULT.4 — a single Mac transport
                // is created when the user enters the
                // pairing flow and lives until they
                // leave the control surface.
                val macTransport = remember { MacTransport() }
                val current = navStack.value.last()

                // This screen is hosted by Elysium. At the Nexus root,
                // finish only this activity to reveal Elysium Home again.
                // Nested screens still pop their own navigation stack.
                BackHandler {
                    when (val top = current) {
                        is HubDestination.IrLearner -> {
                            com.elysium.nexus.fabric.infrared.IrCaptureBridge.stop()
                            learnerResultState.value = null
                        }
                        is HubDestination.MacPairing,
                        is HubDestination.MacControl -> {
                            runCatching { macTransport.disconnect() }
                        }
                        else -> Unit
                    }
                    if (navStack.value.size > 1) {
                        navStack.value = navStack.value.dropLast(1)
                    } else {
                        finish()
                    }
                }

                // Phase ULT.9 — Startup animation.
                // The splash plays on every cold start
                // (~2.8s) then fades to reveal the
                // tour or the Hub.
                if (splashVisible) {
                    SplashScreen(
                        onSplashComplete = {
                            splashVisible = false
                            // An adb reverse localhost route only proves that a
                            // bridge exists; it does not identify whether ADB is
                            // using USB-C or Wi-Fi. Keep startup on Home and let
                            // the user choose the intended transport explicitly.
                            Log.i(tag, "Nexus startup complete; transport selection remains explicit")
                        }
                    )
                    return@ElysiumTheme
                }

                if (tourVisible.value) {
                    GuidedTourOverlay(
                        onComplete = {
                            markLaunched()
                            tourVisible.value = false
                        }
                    )
                    return@ElysiumTheme
                }


                when (current) {
                    is HubDestination.Hub -> HubScreen(
                        onCategorySelected = { cat ->
                            when (cat) {
                                com.elysium.nexus.core.device.DeviceCategory.AIR_CONDITIONER -> {
                                    // AC goes directly to the
                                    // specialized AcControl screen.
                                    val acTemplate = com.elysium.nexus.core.device.DeviceCatalog.all
                                        .firstOrNull { it.category == cat }
                                        ?: return@HubScreen
                                    navStack.value = navStack.value + HubDestination.AcControl(acTemplate)
                                }
                                com.elysium.nexus.core.device.DeviceCategory.PLAYSTATION,
                                com.elysium.nexus.core.device.DeviceCategory.XBOX,
                                com.elysium.nexus.core.device.DeviceCategory.NINTENDO -> {
                                    // Consoles: open the
                                    // sub-category screen
                                    // (PS5 / PS4, etc.).
                                    // We use the Category
                                    // destination as a
                                    // marker; the actual
                                    // sub-category screen
                                    // is reached via
                                    // ConsolePicker.
                                    navStack.value = navStack.value + HubDestination.Category(cat)
                                }
                                else -> {
                                    navStack.value = navStack.value + HubDestination.Category(cat)
                                }
                            }
                        },
                        onTvControlsSelected = {
                            navStack.value = navStack.value + HubDestination.TvControls
                        },
                        onLearnSelected = {
                            navStack.value = navStack.value + HubDestination.IrLearner(null)
                        },
                        onInstalledProfilesSelected = {
                            navStack.value = navStack.value + HubDestination.InstalledProfiles
                        },
                        onMacSelected = {
                            // Wi-Fi opens the live discovery screen. It refreshes
                            // stale addresses and keeps manual IP entry available.
                            navStack.value = navStack.value + HubDestination.MacDiscovery
                        },
                        onUsbCSelected = {
                            navStack.value = navStack.value + HubDestination.UsbC
                        },
                        onUniversalRemoteSelected = {
                            navStack.value = navStack.value + HubDestination.UniversalRemote
                        },
                        onWifiUniversalSelected = {
                            navStack.value = navStack.value + HubDestination.WifiUniversal
                        },
                        onAutomationSelected = {
                            navStack.value = navStack.value + HubDestination.AutomationList
                        },
                        onScenesSelected = {
                            navStack.value = navStack.value + HubDestination.SceneList
                        },
                        onSettings = { settingsVisible.value = true },
                        onShowHelp = { tourVisible.value = true },
                        firstDeviceLabel = connectedDevice?.let { "${it.brand} ${it.model}" },
                        quickConnect = lastDevice,
                        onQuickConnect = {
                            when (lastDevice) {
                                is com.elysium.nexus.core.profile.LastDevice.Mac -> {
                                    // A saved IP can change when the Mac joins a
                                    // different Wi-Fi network or receives a DHCP
                                    // lease. Re-scan before pairing; discovery
                                    // also exposes the manual IP path.
                                    navStack.value = navStack.value + HubDestination.MacDiscovery
                                }
                                is com.elysium.nexus.core.profile.LastDevice.Bluetooth -> {
                                    // The BT connect is
                                    // interactive (user
                                    // has to confirm the
                                    // pairing on the host).
                                    // Jump to the
                                    // Universal Remote
                                    // screen; the user
                                    // taps Conectar on the
                                    // remembered device.
                                    navStack.value = navStack.value + HubDestination.UniversalRemote
                                }
                                null -> Unit
                            }
                        },
                        onForgetQuickConnect = {
                            try {
                                LastDeviceMemory(this@MainActivity).clear()
                            } catch (_: Throwable) {}
                            lastDeviceFlow.value = null
                        }
                    )
                    is HubDestination.TvControls -> TvControlsSection(
                        onBack = { navStack.value = navStack.value.dropLast(1) },
                        onLearnRequested = {
                            navStack.value = navStack.value + HubDestination.IrLearner(null)
                        },
                        onDeviceSelected = { template ->
                            navStack.value = navStack.value.dropLast(1) + HubDestination.Connect(template)
                        }
                    )
                    is HubDestination.Category -> {
                        val cat = current.category
                        if (cat == com.elysium.nexus.core.device.DeviceCategory.PLAYSTATION ||
                            cat == com.elysium.nexus.core.device.DeviceCategory.XBOX ||
                            cat == com.elysium.nexus.core.device.DeviceCategory.NINTENDO
                        ) {
                            // For consoles, show the
                            // sub-category picker (PS5,
                            // PS4, etc.) instead of the
                            // brand list. Pick the first
                            // sub-category as a default;
                            // the picker will let the
                            // user pick a different one.
                            val subs = when (cat) {
                                com.elysium.nexus.core.device.DeviceCategory.PLAYSTATION -> com.elysium.nexus.core.device.DeviceCategory.playstationSubcategories
                                com.elysium.nexus.core.device.DeviceCategory.XBOX -> com.elysium.nexus.core.device.DeviceCategory.xboxSubcategories
                                else -> com.elysium.nexus.core.device.DeviceCategory.nintendoSubcategories
                            }
                            // Push the ConsolePicker with
                            // the first sub-category as a
                            // default; the screen will let
                            // the user navigate.
                            navStack.value = navStack.value.dropLast(1) +
                                HubDestination.ConsolePicker(cat, subs.first())
                        } else {
                            DeviceCategoryScreen(
                                category = cat,
                                onBack = { navStack.value = navStack.value.dropLast(1) },
                                onDeviceSelected = { template ->
                                    navStack.value = navStack.value.dropLast(1) + HubDestination.Connect(template)
                                }
                            )
                        }
                    }
                    is HubDestination.ConsolePicker -> ConsoleSubcategoryScreen(
                        category = current.category,
                        onBack = { navStack.value = navStack.value.dropLast(1) },
                        onSubcategorySelected = { sub ->
                            // Find the first device
                            // template for the chosen
                            // sub-category. The
                            // subcategory id is a
                            // prefix (e.g. "ps5" matches
                            // "ps5-generic", "ps5-digital").
                            val template = com.elysium.nexus.core.device.DeviceCatalog.all.firstOrNull {
                                it.category == current.category &&
                                it.id.startsWith(sub.id)
                            } ?: com.elysium.nexus.core.device.DeviceCatalog.byId("${sub.id}-generic")
                            if (template != null) {
                                navStack.value = navStack.value.dropLast(1) +
                                    HubDestination.ConsoleDevice(template)
                            }
                        }
                    )
                    is HubDestination.ConsoleDevice -> ConsoleDeviceScreen(
                        templateId = current.template.id,
                        onBack = { navStack.value = navStack.value.dropLast(1) }
                    )
                    is HubDestination.Connect -> {
                        val ir = irTransmitter
                        if (ir != null) {
                            IrConnectFlow(
                                template = current.template,
                                onBack = { navStack.value = navStack.value.dropLast(1) },
                                onProfileInstalled = { installedProfile ->
                                    connectedDeviceFlow.value = current.template
                                    navStack.value = navStack.value.dropLast(1) + HubDestination.Control(
                                        profileId = installedProfile.id
                                    )
                                },
                                onTryOther = { navStack.value = navStack.value.dropLast(1) },
                                irTransmitter = ir,
                                hasIrBlaster = ir.hasEmitter()
                            )
                        }
                    }
                    is HubDestination.InstalledProfiles -> com.elysium.nexus.ui.hub.InstalledProfilesScreen(
                        onBack = { navStack.value = navStack.value.dropLast(1) },
                        onProfileSelected = { profileId ->
                            navStack.value = navStack.value.dropLast(1) + HubDestination.Control(
                                profileId = profileId
                            )
                        }
                    )
                    is HubDestination.Control -> {
                        val ir = irTransmitter
                        if (ir != null) {
                            TvControlScreen(
                                profileId = current.profileId,
                                onBack = { navStack.value = navStack.value.dropLast(1) },
                                irTransmitter = ir,
                                hasEmitter = ir.hasEmitter()
                            )
                        }
                    }
                    is HubDestination.MacDiscovery -> MacDiscoveryScreen(
                        onBack = { navStack.value = navStack.value.dropLast(1) },
                        onHostSelected = { host ->
                            navStack.value = navStack.value.dropLast(1) +
                                HubDestination.MacPairing(host)
                        },
                        onManualAdd = {
                            // Manual add: show a dialog
                            // to enter IP + port. We
                            // use the discovery screen
                            // dialog state (a real IP
                            // text field + port spinner).
                            manualAddVisible.value = true
                        }
                    )
                    HubDestination.MacInternet -> NexusInternetScreen(
                        onBack = { navStack.value = navStack.value.dropLast(1) }
                    )
                    is HubDestination.MacPairing -> MacPairingScreen(
                        host = current.host,
                        onBack = {
                            macTransport.disconnect()
                            navStack.value = navStack.value.dropLast(1)
                        },
                        onPaired = {
                            // Phase ULT.8 — remember this
                            // host so the Hub can offer
                            // Quick Connect next time.
                            try {
                                val memory = LastDeviceMemory(this@MainActivity)
                                memory.set(
                                    LastDevice.Mac(
                                        name = current.host.name,
                                        host = current.host.host,
                                        port = current.host.port
                                    )
                                )
                                lastDeviceFlow.value = memory.get()
                            } catch (e: Throwable) {
                                Log.w(tag, "LastDeviceMemory save failed: ${e.message}")
                            }
                            // Transition to the control
                            // surface; the transport is
                            // already in Ready state.
                            navStack.value = navStack.value.dropLast(1) +
                                HubDestination.MacControl(current.host)
                        },
                        transport = macTransport
                    )
                    is HubDestination.MacControl -> {
                        // The transport is already
                        // connected (the PIN flow drove
                        // it to Ready). The control
                        // surface just renders and
                        // dispatches gestures.
                        MacControlSurfaceScreen(
                            host = current.host,
                            transport = macTransport,
                            onBack = {
                                macTransport.disconnect()
                                navStack.value = navStack.value.dropLast(1)
                            }
                        )
                    }
                    is HubDestination.UniversalRemote -> com.elysium.nexus.ui.universal.UniversalControlScreen(
                        onBack = { navStack.value = navStack.value.dropLast(1) }
                    )
                    is HubDestination.UsbC -> com.elysium.nexus.ui.usb.UsbCConnectionScreen(
                        onBack = { navStack.value = navStack.value.dropLast(1) },
                        onFallbackToWifi = {
                            navStack.value = navStack.value.dropLast(1) + HubDestination.MacDiscovery
                        },
                        transport = macTransport
                    )
                    is HubDestination.WifiUniversal -> com.elysium.nexus.ui.wifi.WifiUniversalScreen(
                        onBack = { navStack.value = navStack.value.dropLast(1) }
                    )
                    is HubDestination.AcControl -> {
                        val ir = irTransmitter
                        if (ir != null) {
                            com.elysium.nexus.ui.control.AcControlScreen(
                                template = current.template,
                                onBack = { navStack.value = navStack.value.dropLast(1) },
                                irTransmitter = ir,
                                hasEmitter = ir.hasEmitter(),
                                acStateStore = acStateStore
                            )
                        }
                    }
                    is HubDestination.IrLearner -> {
                        // §5 Real capture loop: the bridge listens for
                        // waveforms from the Nexus Receiver / agent
                        // (Wi-Fi or USB-C) and decodes them live.
                        androidx.compose.runtime.LaunchedEffect(current) {
                            val scope = activityScope
                            if (scope != null && !com.elysium.nexus.fabric.infrared.IrCaptureBridge.isRunning()) {
                                learnerResultState.value = null
                                com.elysium.nexus.fabric.infrared.IrCaptureBridge.start(
                                    port = com.elysium.nexus.fabric.infrared.IrCaptureBridge.DEFAULT_PORT,
                                    scope = scope,
                                    onLearned = { result ->
                                        learnerResultState.value = result
                                    }
                                )
                            }
                        }
                        val shownResult = learnerResultState.value ?: current.learnResult
                        com.elysium.nexus.ui.control.IrLearnerScreen(
                            learnResult = shownResult,
                            onBack = {
                                com.elysium.nexus.fabric.infrared.IrCaptureBridge.stop()
                                learnerResultState.value = null
                                navStack.value = navStack.value.dropLast(1)
                            },
                            onRetry = {
                                // Clear the decoded result; the bridge
                                // keeps listening for another waveform.
                                learnerResultState.value = null
                                android.util.Log.i(tag, "DIAG LEARN_RETRY requested; listening continues")
                            },
                            onSave = { result ->
                                // Persist the learned IR command to the Room database.
                                com.elysium.nexus.fabric.infrared.IrCaptureBridge.stop()
                                val repo = irRepository
                                val cmd = result.command
                                if (repo != null && cmd != null) {
                                    activityScope?.launch {
                                        val patternStr = result.rawWaveform.pattern.joinToString(",")
                                        val extrasStr = cmd.extras.entries.joinToString("|") {
                                            "${it.key}=${it.value}"
                                        }
                                        repo.save(
                                            com.elysium.nexus.databases.ir.LearnedIrCommandEntity(
                                                label = "${cmd.protocol.name} ${cmd.address}#${cmd.command}",
                                                templateId = "learned",
                                                protocolName = cmd.protocol.name,
                                                address = cmd.address,
                                                command = cmd.command,
                                                carrierHz = result.carrierHz,
                                                rawPattern = patternStr,
                                                confidence = result.confidence,
                                                capturedAtMs = System.currentTimeMillis(),
                                                extras = extrasStr
                                            )
                                        )
                                        android.util.Log.i(
                                            tag,
                                            "DIAG LEARN_SAVED protocol=${cmd.protocol.name} " +
                                                "addr=${cmd.address} cmd=${cmd.command} " +
                                                "confidence=${result.confidence} slices=${result.rawWaveform.pattern.size}"
                                        )
                                    }
                                } else {
                                    android.util.Log.w(
                                        tag,
                                        "DIAG LEARN_SAVE_SKIPPED cmd=${cmd != null} repo=${repo != null}"
                                    )
                                }
                                learnerResultState.value = null
                                navStack.value = navStack.value.dropLast(1)
                            },
                            onTransmit = { result ->
                                // §5 Physical verification: fire the just-captured
                                // waveform through the phone's own IR emitter so
                                // the learned signal is usable immediately.
                                val tx = ir
                                if (tx != null) {
                                    activityScope?.launch {
                                        val outcome = tx.transmit(result.rawWaveform)
                                        android.util.Log.i(
                                            tag,
                                            "DIAG LEARN_TX_OUTCOME $outcome"
                                        )
                                    }
                                } else {
                                    android.util.Log.w(tag, "DIAG LEARN_TX_SKIPPED no transmitter")
                                }
                            }
                        )
                    }
                    is HubDestination.AutomationList -> com.elysium.nexus.ui.automation.AutomationListScreen(
                        automations = automationStore,
                        onBack = { navStack.value = navStack.value.dropLast(1) },
                        onCreateNew = {
                            navStack.value = navStack.value + HubDestination.AutomationEditor()
                        },
                        onEditAutomation = { auto ->
                            navStack.value = navStack.value + HubDestination.AutomationEditor(auto)
                        },
                        onDeleteAutomation = { auto ->
                            automationStore = automationStore.filter { it.id != auto.id }
                            automationDefinitionStore?.delete(auto.id)
                        },
                        onRunAutomation = { auto ->
                            val engine = sceneEngine
                            val scope = activityScope
                            if (engine == null || scope == null) {
                                android.util.Log.w(tag, "Run automation skipped: engine or scope not ready")
                            } else {
                                scope.launch {
                                    val mapped = com.elysium.nexus.fabric.automation.AutomationSceneMapper
                                        .toMacroTransaction(auto)
                                    val result = engine.executeMacro(mapped.transaction)
                                    val summary = com.elysium.nexus.fabric.automation
                                        .summarizeExecution(result)
                                    android.util.Log.i(
                                        tag,
                                        "Automation '${auto.name}' → $summary" +
                                            " (canonical=${mapped.classifiedCanonical()}, custom=${mapped.customKeyCount})"
                                    )
                                }
                            }
                        }
                    )
                    is HubDestination.AutomationEditor -> com.elysium.nexus.ui.automation.AutomationEditorScreen(
                        existingAutomation = current.automation,
                        onBack = { navStack.value = navStack.value.dropLast(1) },
                        onSave = { saved ->
                            if (current.automation != null) {
                                automationStore = automationStore.map {
                                    if (it.id == saved.id) saved else it
                                }
                                automationDefinitionStore?.update(saved)
                            } else {
                                automationStore = automationStore + saved
                                automationDefinitionStore?.add(saved)
                            }
                            navStack.value = navStack.value.dropLast(1)
                        }
                    )
                    // §36 Scenes — durable via Room-backed SceneRegistry.
                    // List: run/delete/edit. Run executes through the
                    // concrete engine (honest dispatcher: without device
                    // adapters every dispatch is recorded as not delivered).
                    is HubDestination.SceneList -> com.elysium.nexus.ui.scenes.SceneListScreen(
                        scenes = scenesFlow.collectAsState().value,
                        onBack = { navStack.value = navStack.value.dropLast(1) },
                        onCreateNew = {
                            navStack.value = navStack.value + HubDestination.SceneEditor()
                        },
                        onEditScene = { scene ->
                            navStack.value = navStack.value + HubDestination.SceneEditor(scene)
                        },
                        onDeleteScene = { scene ->
                            val registry = sceneRegistry
                            val scope = activityScope
                            if (registry == null || scope == null) {
                                android.util.Log.w(tag, "Delete scene skipped: registry or scope not ready")
                            } else {
                                scope.launch {
                                    registry.deleteScene(scene.id)
                                    android.util.Log.i(tag, "Scene '${scene.name}' deleted (${scene.id})")
                                }
                            }
                        },
                        onRunScene = { scene ->
                            val engine = sceneEngine
                            val scope = activityScope
                            if (engine == null || scope == null) {
                                android.util.Log.w(tag, "Run scene skipped: engine or scope not ready")
                            } else {
                                scope.launch {
                                    val result = engine.executeScene(scene)
                                    val summary = com.elysium.nexus.fabric.automation
                                        .summarizeExecution(result)
                                    android.util.Log.i(
                                        tag,
                                        "Scene '${scene.name}' → $summary " +
                                            "(${scene.steps.size} pasos, ${scene.id})"
                                    )
                                }
                            }
                        }
                    )
                    is HubDestination.SceneEditor -> com.elysium.nexus.ui.scenes.SceneEditorScreen(
                        existingScene = current.scene,
                        onBack = { navStack.value = navStack.value.dropLast(1) },
                        onSave = { saved ->
                            val registry = sceneRegistry
                            val scope = activityScope
                            if (registry == null || scope == null) {
                                android.util.Log.w(tag, "Save scene skipped: registry or scope not ready")
                            } else {
                                scope.launch {
                                    registry.saveScene(saved)
                                    android.util.Log.i(
                                        tag,
                                        "Scene '${saved.name}' saved (${saved.steps.size} pasos, ${saved.id})"
                                    )
                                }
                            }
                            navStack.value = navStack.value.dropLast(1)
                        }
                    )
                }

                if (settingsVisible.value) {
                    SettingsDialog(
                        settings = settings,
                        onSettingsChange = { updated ->
                            settingsStore.update(updated)
                            settingsFlow.value = updated
                            val left = StickConfig(
                                innerDeadzone = 0.10f,
                                outerThreshold = 0.95f,
                                sensitivity = updated.leftStickSensitivity,
                                invertX = updated.invertLeftX,
                                invertY = updated.invertLeftY
                            )
                            val right = StickConfig(
                                innerDeadzone = 0.10f,
                                outerThreshold = 0.95f,
                                sensitivity = updated.rightStickSensitivity,
                                invertX = updated.invertRightX,
                                invertY = updated.invertRightY
                            )
                            runCatching {
                                engine.updateStickConfig(StickSide.Left, left)
                                engine.updateStickConfig(StickSide.Right, right)
                            }
                        },
                        onDismiss = { settingsVisible.value = false }
                    )
                }

                if (manualAddVisible.value) {
                    ManualAddHostDialog(
                        onDismiss = { manualAddVisible.value = false },
                        onConnect = { host, port ->
                            manualAddVisible.value = false
                            val synthetic = com.elysium.nexus.ui.mac.DiscoveredHost(
                                id = "manual-$host-$port",
                                name = "$host:$port",
                                type = com.elysium.nexus.ui.mac.HostType.MAC_DESKTOP,
                                signalStrength = 3,
                                isOnline = true,
                                host = host,
                                port = port
                            )
                            navStack.value = navStack.value.dropLast(1) +
                                HubDestination.MacPairing(synthetic)
                        }
                    )
                }
            }
        }
    }

    /**
     * The first-launch flag is stored in
     * SharedPreferences. The key is
     * `elysium.firstLaunch`; the value is `true`
     * on the very first launch and `false`
     * afterwards.
     */
    private fun isFirstLaunch(): Boolean {
        val prefs = getSharedPreferences("elysium", MODE_PRIVATE)
        return prefs.getBoolean("firstLaunch", true)
    }

    private fun markLaunched() {
        getSharedPreferences("elysium", MODE_PRIVATE)
            .edit()
            .putBoolean("firstLaunch", false)
            .apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(tag, "MainActivity.onDestroy — §38 disconnect path")
        val engine = this.engine ?: return
        runCatching { engine.transitionTo(EngineState.Suspended) }
        runCatching { engine.transitionTo(EngineState.Reconnecting) }
        runCatching { engine.transitionTo(EngineState.Disconnected) }
        engine.neutralize()
        runBlocking { transportBinding?.transport?.value?.releaseAll() }
        driverJob?.cancel()
        latencyJob?.cancel()
        motionJob?.cancel()
        motionSource?.close()
        postureJob?.cancel()
        postureSource?.close()
        transportJob?.cancel()
        runBlocking { transportBinding?.transport?.value?.stop() }
        activityScope?.cancel()
        this.engine = null
        this.latencyTracker = null
        this.activityScope = null
        this.driverJob = null
        this.latencyJob = null
        this.motionJob = null
        this.motionSource = null
        this.postureJob = null
        this.postureSource = null
        this.transportBinding = null
        this.transportJob = null
        this.shareLauncher = null
        this.settingsStore = null
        this.haptics = null
        this.irTransmitter = null
        this.acStateStore = null
        this.irRepository = null
        this.automationDefinitionStore = null
    }

    private fun driveEngineToActive(engine: CanonicalInputEngine) {
        engine.transitionTo(EngineState.Discovering)
        engine.transitionTo(EngineState.Pairing)
        engine.transitionTo(EngineState.Authenticating)
        engine.transitionTo(EngineState.Negotiating)
        engine.transitionTo(EngineState.Connected)
        engine.transitionTo(EngineState.Active)
    }

    private var lastLogMs: Long = System.currentTimeMillis()

    private fun logState(state: com.elysium.nexus.core.model.UniversalControllerState) {
        val now = System.currentTimeMillis()
        val deltaMs = now - lastLogMs
        lastLogMs = now
        Log.d(
            tag,
            "state[seq=${state.sequence}, ts=${state.timestampNs}, " +
                "Δt=${deltaMs}ms]: " +
                "buttons=${state.buttons.size()}, " +
                "dpad=${state.dpad}, " +
                "L=(${state.leftStick.x}, ${state.leftStick.y}), " +
                "R=(${state.rightStick.x}, ${state.rightStick.y}), " +
                "LT=${state.leftTrigger.value}, " +
                "RT=${state.rightTrigger.value}, " +
                "touches=${state.touches.size()}, " +
                "motion=${state.motion != null}"
        )
    }
}
