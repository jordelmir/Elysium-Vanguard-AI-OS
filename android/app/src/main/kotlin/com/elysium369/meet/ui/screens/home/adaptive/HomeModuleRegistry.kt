package com.elysium369.meet.ui.screens.home.adaptive

import com.elysium369.meet.ui.navigation.MeetDestinations

object HomeModuleRegistry {

    fun getModulesByCategory(
        userRole: String,
        isPlatformOwner: Boolean = false
    ): Map<HomeSectionCategory, List<HomeModuleItem>> {
        val map = mutableMapOf<HomeSectionCategory, MutableList<HomeModuleItem>>()

        HomeSectionCategory.entries.forEach { section ->
            map[section] = mutableListOf()
        }

        // ── DIAGNOSTICS & CONTROL ──
        map[HomeSectionCategory.DIAGNOSTICS]?.addAll(
            listOf(
                HomeModuleItem("scanner", "Escáner en Vivo", "Telemetría de sensores y PIDs", MeetDestinations.SCANNER, HomeSectionCategory.DIAGNOSTICS, "scanner", isHighlight = true),
                HomeModuleItem("dtcs", "Códigos DTC", "Lectura y borrado de fallas ECU", MeetDestinations.DTCS, HomeSectionCategory.DIAGNOSTICS, "dtc", isHighlight = true),
                HomeModuleItem("perito", "Vanguard Perito", "Inspección forense pre-compra", MeetDestinations.PERITO, HomeSectionCategory.DIAGNOSTICS, "vanguard_perito"),
                HomeModuleItem("dna", "Vanguard DNA", "Perfil biométrico y gemelo digital", MeetDestinations.DNA, HomeSectionCategory.DIAGNOSTICS, "vanguard_dna"),
                HomeModuleItem("access_immo", "Acceso & IMMO", "Llaves digitales, transponders y BCM", MeetDestinations.VEHICLE_ACCESS, HomeSectionCategory.DIAGNOSTICS, "vehicle_access", badgeText = "NUEVO"),
                HomeModuleItem("findings", "Hallazgos", "Evidencias y registros técnicos", MeetDestinations.FINDINGS, HomeSectionCategory.DIAGNOSTICS, "findings"),
                HomeModuleItem("terminal", "Terminal OBD", "Comandos crudos ELM/AT y ST", MeetDestinations.TERMINAL, HomeSectionCategory.DIAGNOSTICS, "terminal")
            )
        )

        // ── VEHICLE & HISTORY ──
        map[HomeSectionCategory.VEHICLE]?.addAll(
            listOf(
                HomeModuleItem("garage", "Garaje Digital", "Vehículos registrados y perfiles", MeetDestinations.GARAGE, HomeSectionCategory.VEHICLE, "garage", isHighlight = true),
                HomeModuleItem("health", "Score de Salud", "Algoritmo integral de condición", MeetDestinations.HEALTH_SCORE, HomeSectionCategory.VEHICLE, "health_score"),
                HomeModuleItem("maintenance", "Mantenimiento", "Alertas de fluidos, filtros y servicios", MeetDestinations.MAINTENANCE, HomeSectionCategory.VEHICLE, "maintenance"),
                HomeModuleItem("trips", "Bitácora de Viajes", "Consumo, distancias y telemetría", MeetDestinations.TRIP_LOG, HomeSectionCategory.VEHICLE, "trip_log"),
                HomeModuleItem("dvir", "Inspección DVIR", "Reporte de inspección diaria", MeetDestinations.DVIR, HomeSectionCategory.VEHICLE, "dvir"),
                HomeModuleItem("battery", "Salud de Batería", "Diagnóstico de acumulador y carga", MeetDestinations.BATTERY_HEALTH, HomeSectionCategory.VEHICLE, "battery_health")
            )
        )

        // ── SERVICES & ASSISTANCE ──
        map[HomeSectionCategory.SERVICES]?.addAll(
            listOf(
                HomeModuleItem("linksphere", "Elysium LinkSphere", "Red social universal de enlaces (YouTube, TikTok, Spotify, X...)", MeetDestinations.LINKSPHERE, HomeSectionCategory.SERVICES, "linksphere", isHighlight = true, badgeText = "SOCIAL"),
                HomeModuleItem("learning", "Elysium Educación OS", "Educación global, OCDE PISA Nivel 6, oficios y simulaciones", MeetDestinations.ELYSIUM_LEARNING_OS, HomeSectionCategory.SERVICES, "learning_hub", isHighlight = true, badgeText = "PISA 6"),
                HomeModuleItem("messages", "Radar Mesh & Chat", "Chat P2P y llamadas 100% off-grid", MeetDestinations.MESSAGES, HomeSectionCategory.SERVICES, "messages", isHighlight = true, badgeText = "OFF-GRID"),
                HomeModuleItem("mechanic", "Servicios Mecánicos", "Red de talleres y cotizaciones", MeetDestinations.MECHANIC_SERVICES, HomeSectionCategory.SERVICES, "mechanic_services", isHighlight = true),
                HomeModuleItem("parts", "Repuestos & Piezas", "Compatibilidad técnica VIN-DTC", MeetDestinations.PARTS_STORE, HomeSectionCategory.SERVICES, "parts_store"),
                HomeModuleItem("tow_truck", "Asistencia & Grúa", "Auxilio vial geolocalizado", MeetDestinations.TOW_TRUCK, HomeSectionCategory.SERVICES, "tow_truck"),
                HomeModuleItem("live_link", "Live Link PRO", "Sesión remota perito/mecánico", MeetDestinations.LIVE_STREAM, HomeSectionCategory.SERVICES, "live_stream"),
                HomeModuleItem("ride", "Elysium Rides", "Transporte inteligente y conductor", MeetDestinations.RIDE_HOME, HomeSectionCategory.SERVICES, "ride_home"),
                HomeModuleItem("deliveries", "Comida y Entregas", "Comida, tiendas y paquetes pequeños en línea", MeetDestinations.ELYSIUM_DELIVERIES, HomeSectionCategory.SERVICES, "elysium_services", isHighlight = true),
                HomeModuleItem("legal_vanguard", "Legal Vanguard", "Abogados, bufetes y expediente protegido", MeetDestinations.LEGAL_VANGUARD, HomeSectionCategory.SERVICES, "legal_vanguard", isHighlight = true, badgeText = "NUEVO"),
                HomeModuleItem("elysium_properties", "Elysium Properties", "Property Passport, venta y alquiler", MeetDestinations.PROPERTIES, HomeSectionCategory.SERVICES, "elysium_properties", isHighlight = true, badgeText = "NUEVO"),
                HomeModuleItem("fuel_rewards", "Fuel Rewards", "Wallet, campañas y Station OS", MeetDestinations.FUEL_REWARDS, HomeSectionCategory.SERVICES, "fuel_rewards", isHighlight = true, badgeText = "NUEVO"),
                HomeModuleItem("elysium_services", "Servicios Elysium", "Subasta dual, radar técnico y contra-ofertas", MeetDestinations.ELYSIUM_SERVICES, HomeSectionCategory.SERVICES, "elysium_services", isHighlight = true, badgeText = "NUEVO"),
                HomeModuleItem("agent_store", "Agent Store", "IA especializada, agentes 3D y DigiSouls", MeetDestinations.AGENT_STORE, HomeSectionCategory.SERVICES, "agent_store", isHighlight = true, badgeText = "NUEVO"),
                HomeModuleItem("uap_xenology", "🛸 OVNIs y Razas", "Razas extraterrestres, naves UAP y plasmoides", MeetDestinations.UAP_XENOLOGY, HomeSectionCategory.SERVICES, "uap_xenology", isHighlight = true, badgeText = "NUEVO")
            )
        )

        // ── ADVANCED TOOLS ──
        map[HomeSectionCategory.TOOLS]?.addAll(
            listOf(
                HomeModuleItem("ai_copilot", "IA Especialista", "Diagnóstico guiado por inteligencia", MeetDestinations.AI, HomeSectionCategory.TOOLS, "ai", isHighlight = true),
                HomeModuleItem("dragon_calc", "DragonCalc Elysium", "Calculadora científica y simbólica", MeetDestinations.DRAGON_CALC, HomeSectionCategory.TOOLS, "dragon_calc", isHighlight = true),
                HomeModuleItem("nexxus_control", "Elysium Nexus Control", "Control de dispositivos, IR y Bluetooth", MeetDestinations.NEXUS_CONTROL, HomeSectionCategory.TOOLS, "nexxus_control", isHighlight = true),
                HomeModuleItem("file_manager", "Elysium FileManager", "Archivos, multimedia y almacenamiento", MeetDestinations.FILE_MANAGER, HomeSectionCategory.TOOLS, "file_manager", isHighlight = true),
                HomeModuleItem("record_shield", "Elysium RecordShield", "Grabación local con aviso visible", MeetDestinations.RECORD_SHIELD, HomeSectionCategory.TOOLS, "record_shield", isHighlight = true),
                HomeModuleItem("engine_3d", "Motor 3D Interactivo", "Visor tridimensional de componentes", MeetDestinations.ENGINE_3D, HomeSectionCategory.TOOLS, "engine_3d"),
                HomeModuleItem("hud", "Modo HUD", "Proyección en parabrisas", MeetDestinations.HUD, HomeSectionCategory.TOOLS, "hud"),
                HomeModuleItem("protocol", "Protocolos", "Detección y parámetros de bus", MeetDestinations.PROTOCOL_LEARNING, HomeSectionCategory.TOOLS, "protocol_learning"),
                HomeModuleItem("adapter", "Test Adaptador", "Rendimiento y clones ELM327", MeetDestinations.ADAPTER_DIAGNOSTICS, HomeSectionCategory.TOOLS, "adapter_diagnostics")
            )
        )

        // ── PROFESSIONAL & FLEET ──
        val proList = mutableListOf(
            HomeModuleItem("pro_hub", "Vanguard PRO Hub", "Topología CAN y test de actuadores", MeetDestinations.PRO_HUB, HomeSectionCategory.PROFESSIONAL, "pro_hub", isHighlight = true),
            HomeModuleItem("fleet", "Gestión de Flota", "Supervisión y despacho multi-unidad", "fleet", HomeSectionCategory.PROFESSIONAL, "fleet")
        )

        if (isPlatformOwner) {
            proList.addAll(
                listOf(
                    HomeModuleItem("trust_center", "Centro de Confianza", "Revisión privada de la plataforma", MeetDestinations.TRUST_CENTER, HomeSectionCategory.PROFESSIONAL, "trust_center"),
                    HomeModuleItem("command_center", "Command Center", "Inteligencia ejecutiva privada", "meet_command_center", HomeSectionCategory.PROFESSIONAL, "command_center"),
                    HomeModuleItem("owner_cockpit", "Owner Control", "Cockpit global de plataforma", "platform_owner_cockpit", HomeSectionCategory.PROFESSIONAL, "platform_owner_cockpit"),
                    HomeModuleItem("system_health", "System Health", "Estado del clúster y nodos", "system_health_dashboard", HomeSectionCategory.PROFESSIONAL, "system_health_dashboard")
                )
            )
        }
        map[HomeSectionCategory.PROFESSIONAL]?.addAll(proList)

        // ── AUDIO ──
        map[HomeSectionCategory.AUDIO]?.addAll(
            listOf(
                HomeModuleItem("equalizer", "Ecualizador", "Ajustes de frecuencia y bandas", "audio_equalizer", HomeSectionCategory.AUDIO, "equalizer"),
                HomeModuleItem("playlist", "Reproducción", "Listas de reproducción y audio", "audio_playlist", HomeSectionCategory.AUDIO, "playlist"),
                HomeModuleItem("fm", "Sintonizador FM", "Radio FM y bandas", "audio_fm", HomeSectionCategory.AUDIO, "fm")
            )
        )

        // ── SUPREME BASS (Neon Integration) ──
        map[HomeSectionCategory.SUPREME_BASS]?.addAll(
            listOf(
                HomeModuleItem("supreme_bass", "SupremeBass Neon", "Control de audio y boost 100–400%", MeetDestinations.SUPREME_BASS, HomeSectionCategory.SUPREME_BASS, "bass_boost", isHighlight = true)
            )
        )

        // ── SAFETY ──
        map[HomeSectionCategory.SAFETY]?.addAll(
            listOf(
                HomeModuleItem("safety_hub", "Elysium Seguridad", "Evidencia, casos y rendición de cuentas", MeetDestinations.SAFETY_HOME, HomeSectionCategory.SAFETY, "safety", isHighlight = true, badgeText = "NUEVO"),
                HomeModuleItem("safety_research", "Plataforma Científica", "Evidencia forense, claims, replicación", MeetDestinations.SAFETY_RESEARCH, HomeSectionCategory.SAFETY, "research", isHighlight = true, badgeText = "CIENCIA"),
                HomeModuleItem("safety_map", "Mapa de Seguridad", "Puntos públicos de seguridad", MeetDestinations.SAFETY_MAP, HomeSectionCategory.SAFETY, "safety_map"),
                HomeModuleItem("safety_report", "Reportar", "Crear reporte de seguridad", MeetDestinations.SAFETY_REPORT, HomeSectionCategory.SAFETY, "safety_report"),
                HomeModuleItem("safety_my_reports", "Mis Reportes", "Estado de tus reportes", MeetDestinations.SAFETY_MY_REPORTS, HomeSectionCategory.SAFETY, "safety_my_reports"),
                HomeModuleItem("safety_cases", "Casos Públicos", "Casos documentados", MeetDestinations.SAFETY_CASES, HomeSectionCategory.SAFETY, "safety_cases"),
                HomeModuleItem("safety_accountability", "Accountability", "Seguimiento institucional", MeetDestinations.SAFETY_ACCOUNTABILITY, HomeSectionCategory.SAFETY, "safety_accountability"),
                HomeModuleItem("safety_observatory", "Observatorio", "Métricas y tendencias", MeetDestinations.SAFETY_OBSERVATORY, HomeSectionCategory.SAFETY, "safety_observatory")
            )
        )

        return map.filterValues { it.isNotEmpty() }
    }
}
