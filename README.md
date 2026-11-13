<p align="center">
  <img src="https://img.shields.io/badge/Elysium%20Vanguard-AI%20OS-00FFD1?style=for-the-badge&labelColor=0A0E1A" alt="Elysium Vanguard AI OS" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-4.30.1%20%7C%20code%2065-00BCD4?style=flat-square&logo=android&logoColor=white" alt="Android version" />
  <img src="https://img.shields.io/badge/Room-schema%2091-39FF14?style=flat-square" alt="Room schema" />
  <img src="https://img.shields.io/badge/Architecture-offline--first-7F52FF?style=flat-square" alt="Offline first" />
  <img src="https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/AGP-9.2.1-3DDC84?style=flat-square&logo=gradle&logoColor=white" alt="AGP" />
  <img src="https://img.shields.io/badge/Gradle-9.4.1-02303A?style=flat-square&logo=gradle&logoColor=white" alt="Gradle" />
  <img src="https://img.shields.io/badge/Compose%20BOM-2026.05.01-4285F4?style=flat-square" alt="Compose BOM" />
  <img src="https://img.shields.io/badge/Agents-9%20(6%20Canvas%20%2B%203%20Bitmap)-E040FB?style=flat-square" alt="Agents" />
</p>

# Elysium Vanguard AI OS

Elysium Vanguard AI OS coordina diagnóstico automotriz, conocimiento técnico,
reparación, comercio, movilidad y evidencia verificable. `MEET` se conserva
solamente como identificador técnico de compatibilidad: paquete Android,
protocolos, rutas, tablas, base Room y repositorio.

La regla del producto es simple: no se presenta una intención local, una
estimación o un dato de demostración como un hecho físico o una confirmación
del servidor.

## Estado del código en este checkpoint (Elysium Nexus Universal Mac Mirroring, Trackpad Selection & Multiplatform Interactive Notifications)

- **Android:** `versionName 4.30.1`, `versionCode 65`.
- **Contrato de versión del paquete:** `4.30.1` (sincronizado en `android/app/build.gradle.kts`, `package.json` y `package-lock.json`).
- **Room Database:** `version 91` con 91 migraciones registradas (`MIGRATION_1_6` hasta `MIGRATION_90_91`).

### Stack Tecnológico

| Componente | Versión |
|---|---|
| Kotlin | 2.4.0 |
| AGP (Android Gradle Plugin) | 9.2.1 |
| Gradle | 9.4.1 |
| Compose BOM | 2026.05.01 |
| Room | 2.8.4 (schema 90, migraciones 1..90) |
| Hilt / Dagger | 2.60.1 |
| Ktor | 2.3.13 |
| Coroutines | 1.9.0 |
| compileSdk | 37 |
| targetSdk | 36 |
| minSdk | 26 |

### Agentes Coleccionables (9 agentes — Agent3dAvatarCanvas)

| # | Agente | Diseño | Tipo Avatar | Color |
|---|---|---|---|---|
| 1 | 🐉 Draco Ignis | Serpiente de Magma Volcánica | Canvas | Carmesí |
| 2 | ⚡ Volt Aether | Sprite de Plasma Teal | Canvas | Teal |
| 3 | 🛡️ Titan Vanguard | Robot Acorazado (radar, cañones, visor) | Canvas | Rojo |
| 4 | ⚛️ Cyber Mecha | Sentinel Cuántico (constelación hexagonal) | Canvas | Cyan |
| 5 | 🌌 Laya Celestial | Tejedora de Nebulosa (gas interestelar) | Canvas | Violeta |
| 6 | ✨ EVAIR Core | Simbionte Bioluminiscente (medusa abisal) | Canvas | Azul |
| 7 | 🦎 Reptiliano | Entidad Dracónica — Nave: Tic-Tac UAP | Bitmap + Glow | Verde |
| 8 | 👤 Nórdico | Guía Estelar Benevolente — Nave: Crescent UAP | Bitmap + Glow | Azul |
| 9 | 👽 Gris | Observador Silencioso — Nave: Lenticular UAP | Bitmap + Glow | Gris |

- **Avatar Hybrid System:** Los 6 agentes originales usan Canvas API con DrawScope animado (pseudo-3D con azimuth/elevation/wavePhase). Los 3 agentes alien usan imágenes bitmap reales con glow pulsante animado y borde sweep-gradient sobre CircleShape.
- **Categoría XENOLOGY:** Nueva categoría de agente para las razas extraterrestres con filtro dedicado en el Agent Store.

### 🛸 OVNIs y Razas (Xenología Elysium)

Nueva sección accesible desde Home con galería completa:
- **3 Razas Extraterrestres** con su nave asignada (Reptiliano→Tic-Tac, Nórdico→Crescent/Wedge, Gris→Lenticular).
- **4 Plasmoides** (avistamientos: rojo, blanco, rosa, verde).
- **Infografía completa** de referencia de razas y tipología UAP.
- 11 imágenes en `res/drawable/` como recursos nativos.
- Navegación: `MeetDestinations.UAP_XENOLOGY` → `UapXenologyScreen`.

### Rediseño Completo de Avatares (IP-Safe, Propietarios Elysium)

Todos los avatares fueron rediseñados desde cero como diseños propietarios:
- **Draco Ignis:** Serpiente de obsidiana volcánica enrollada, venas de lava incandescente, corona de cristales volcánicos, corazón de magma pulsante.
- **Volt Aether:** Sprite de plasma teal con antenas de pararrayos, reactor arc, anillos orbitales de electrones, escudo hexagonal rotatorio.
- **Titan Vanguard:** Robot acorazado con campo de radar sweep, cañones de hombro, casco angular con visor LED panorámico, reactor triangular, brazos mecánicos con garras, propulsores.
- **Cyber Mecha (Sentinel Cuántico):** Entidad geométrica no-humanoide de placas hexagonales flotantes orbitando un core de consciencia cuántica.
- **Laya (Tejedora de Nebulosa):** Entidad cósmica de gas interestelar con patrones de constelación, alas de refracción prismática, ojos de estrella binaria.
- **EVAIR (Simbionte Bioluminiscente):** Criatura abisal tipo medusa con campana translúcida, venas de red neural, cromatóforos, tentáculos bioluminiscentes.

- **Navegación Waze 1-Click en Safety:**
  - Ícono Waze integrado dentro de la tarjeta de coordenadas en `PublicPointDetail` y `PrivateReportDetailSheet`.
  - Deep-link nativo `waze://?ll=lat,lng&navigate=yes` con cascada de fallback: Google Maps geo → Play Store Waze.
  - Helper `openWazeNavigation()` con seguridad de excepciones completa.
- **Nombre comercial:** Elysium Vanguard AI OS. No se deben renombrar los
  contratos técnicos heredados `MEET` durante una actualización normal.

Este checkpoint es código sincronizado, no una declaración de lanzamiento en
producción. Un APK debug, una compilación local o un workflow de firma efímera
no sustituyen una firma de producción, una prueba física de dos cuentas ni una
evidencia remota de PostgreSQL.

## Capacidades integradas

### Diagnóstico y reparación automotriz

- Conexión OBD-II por Bluetooth Classic, BLE, Wi-Fi TCP y DoIP/UDS.
- **Laboratorio de Emisiones y Pre-ITV Costa Rica:**
  - Decodificador estricto SAE J1979 / SAE J1979-DA de Modo $06 (sin límites inventados ni pases falsos sin límites).
  - Estimador físico de Flujo de Combustible y CO₂ (Speed-Density MAP/MAF + estequiometría de gasolina/etanol).
  - Analizador Virtual de Gases con etiquetado explícito de proveniencia (`MODEL_ESTIMATED` para CO/HC sin sonda física de 4/5 gases).
  - Osciloscopio dual O₂ en tiempo real con monitoreo de cruces por segundo y eficiencia catalítica.
  - Asistente guiado de inspección Pre-ITV con tacómetro dinámico (Ralentí 700-1000 RPM vs Acelerado 2500 RPM) y verificación de corte de fecha Lambda (Decreto 37372-MOPT).
- Lectura de DTC, telemetría y acciones bidireccionales sólo cuando existe un
  enlace físico válido; el modo de entrenamiento se identifica como demo.
- Guías de diagnóstico, reparación, piezas y visualización 3D enlazadas por
  identificadores técnicos. La compatibilidad no se marca como exacta sin
  evidencia VIN/OEM o una tupla técnica cerrada.
- Reportes certificados con cadena hash y QR de seis campos sin VIN, placa ni
  teléfono completos.

### Viajes

- PostgreSQL/Supabase es la autoridad para solicitud, asignación, PIN,
  transiciones, comisión y liquidación. Android usa Room como proyección local
  y outbox durable; no puede proyectar un `ACCEPTED` optimista.
- La aceptación usa control de versión, idempotencia y una identidad de actor
  validada en el servidor. PIN, inicio y finalización vuelven al cliente sólo
  después de confirmación remota.
- Los datos de ruta, ubicación, saldo, vehículo y estado remoto se muestran
  con su fuente y disponibilidad. La interfaz no inventa GPS, vehículos,
  calificaciones ni resultados de pago.
- Preferencias de pasajeros, calificaciones idempotentes y Objetos Olvidados
  están conectados al viaje y chat protegido correspondiente. Objetos Olvidados
  no revela teléfonos ni inventa compensaciones o cobros de entrega.
- La programación de viaje conserva una intención local con lugares geográficos
  válidos, fecha/hora, recurrencia e indicaciones. No afirma un conductor
  asignado hasta que el servidor lo confirme.

### Seguridad, identidad y dinero

- Las proyecciones de seguridad y los centros restringidos fallan cerrados;
  una pantalla visible no concede autoridad.
- Billeteras, comisiones, ingresos y recargas sólo se muestran como disponibles
  tras el recibo correspondiente de Supabase. No se fabrican saldos locales.
- Los pagos externos requieren una capacidad de proveedor confirmada. Los
  asientos financieros usan importes enteros y un ledger inmutable.

### Elysium Safety Scientific Core & Forensic Research Hub

- **Epistemología Forense y Rigor Popperiano:**
  - Formulación de Hipótesis de Trabajo ($H_1$) con contraparte explícita de Hipótesis Nula ($H_0$).
  - Criterios Popperianos de Falsabilidad obligatorios: definición de qué evidencia física, documental o pericial refutaría categóricamente la proposición.
  - Aislamiento riguroso entre proposición fáctica observacional (Claim) y juicio subjetivo.
  - Cumplimiento de la Constitución de Seguridad: *Evidence ≠ Guilt*, *Claim ≠ Conviction*. Estados epistémicos iniciales estrictamente `PROPOSED` y `OBSERVED`.
- **Proyección Automática e Inmediata en Creación de Reportes:**
  - Persistencia atómica en Room al enviar el reporte:
    - `SciClaimEntity` (proposición observada y estado causal).
    - `SciHypothesisEntity` (hipótesis explicativa, nula y criterios de falsación JSON).
    - `SciEventEntity` (evento en línea de tiempo con timestamps de ocurrencia y registro).
  - Vinculación de evidencia multimedia (fotografías, videos, documentos) mediante el Evidence Bridge (`safety_scientific_evidence_bridge`).
  - Botón de navegación directa `🔬 Abrir en Plataforma Científica & Research` en la pantalla de recibo del reporte.
- **Integración Territorial en el Mapa (`SafetyMapScreen`):**
  - Al pulsar cualquier marcador territorial (público o privado), se despliega la tarjeta **Dimensión Científica & Análisis Forense**.
  - Principio estricto de adición sin pérdida: se conservan intactos la clasificación de fuente (testigo directo, familiar, periodístico, institucional), coordenadas exactas con navegación Waze 1-click, conteo de víctimas, desglose de impunidad, narrativa completa y archivos multimedia.
  - Despliegue de hipótesis registradas, criterios de falsación en advertencia visual, claims observados y botón de enlace directo al Hub Científico.
- **Cadena de Custodia Criptográfica Protocol V2:**
  - Árboles de Merkle para agregación de lotes de evidencias.
  - Firmas digitales Ed25519 con verificación en tiempo de ejecución.
  - Paridad de hashing SHA-256 canónico byte-exacto entre TypeScript y Kotlin (`scripts/verify-custody-parity.sh`).
  - Invariantes negativos probados: inmutabilidad de evidencias atestiguadas, rechazo de estados optimistas y preservación de linaje de fuentes.

### 🎮 Elysium Nexus Universal Control & Screen Mirroring (Mac & PC)

- **Transmisión de Pantalla en Tiempo Real (1080p Stream):**
  - Espejo fluido de pantalla completa de macOS a Android con latencia sub-50ms y decodificación eficiente `RGB_565`.
  - Protocolo binario seguro sobre canal TCP 7878 cifrado con X25519 ECDH + ChaCha20-Poly1305.
- **Trackpad de Precisión y Control Táctil Completo:**
  - Desplazamiento relativo suave, doble-clic nativo de Cocoa (`mouseEventClickState` 1..3) para apertura de apps y archivos.
  - Detección de arrastre y selección por pulsación sostenida (long-press drag) para selección precisa de texto, ventanas e íconos.
  - Botón táctil dedicado `[ 🔒 SELECCIONAR ]` / `[ 🔓 ARRASTRAR ]` con bloqueo sostenido de botón izquierdo para selecciones complejas.
  - Mapeo táctil directo (Direct Touch Screen) con sincronización de coordenadas absolutas y clics derecho/izquierdo.
- **Notificaciones Interactivas del Sistema (Android & macOS):**
  - **Android:** Notificación persistente interactiva con acción de desconexión en un toque (`MacControlNotificationManager`).
  - **macOS:** Notificaciones nativas vía `UNUserNotificationCenter` (`NEXUS_REMOTE_SESSION`) con estado de emparejamiento, streaming y acción "Desconectar Sesión".
- **Guía de Instalación Paso a Paso Integrada (`MacAgentDownloadGuide`):**
  - Tarjeta interactiva integrada en `MacDiscoveryScreen` y `MacPairingScreen` con descarga directa del instalador firmado `Elysium-Nexus-Universal-Controller.dmg` desde GitHub Releases.
  - Botones de acción directa: "Abrir GitHub" con intent nativo y "Copiar Enlace".
  - Instrucciones guiadas para permisos de macOS (Grabación de pantalla y Accesibilidad) y emparejamiento con PIN de 6 dígitos.

## Arquitectura de verdad

```text
Intención local
  → outbox durable
  → RPC autenticado y validación de servidor
  → recibo/evento/ledger cuando corresponda
  → proyección Supabase
  → Room
  → interfaz
```

La interfaz puede mostrar `pendiente`, `sin conexión`, `dato no capturado` o
`requiere prueba física`. Nunca debe convertir esas condiciones en éxito.

## Evidencia física registrada

Las rondas anteriores registran instalación, apertura y proceso estable en
**Honor VER-N49** y **Xiaomi M2101K6R**. La
[nota 4.26.0](docs/releases/2026-09-18-android-4.26.0-rides-lost-found-sync.md)
documenta la instalación y apertura en ambos equipos; las notas de
[4.25.0](docs/releases/2026-09-17-android-4.25.0-rides-v9-recovery.md) y
[4.26.1](docs/releases/2026-09-20-elysium-vanguard-ai-os-4.26.1-authority-hardening.md)
registran también arranque Android en Honor. Esta evidencia pertenece a esos
artefactos y fechas, no prueba automáticamente un APK posterior.

El recorrido económico Golden E2E de dos cuentas —descubrimiento, doble claim,
dos recuperaciones de proceso, PIN, inicio, cierre e invariantes
exactly-once— debe repetirse sobre el SHA y el artefacto que se vaya a lanzar.

## Verificación

Ejecuta las verificaciones desde una copia limpia del repositorio. En esta Mac,
Gradle debe usar un solo worker y no mantener un daemon persistente.

```bash
npm run check:versions
bash tests/parity/ci-verify.sh

cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  --no-daemon --max-workers=1
```

Para cambios de Viajes, añade los gates del dominio:

```bash
bash tests/ride/verify-ride-android-authority.sh
bash tests/ride/verify-ride-command-authority-postgres.sh
```

La validación física requiere una instalación en un dispositivo real, apertura
verificada, proceso en primer plano y revisión de logs. Para un lanzamiento de
movilidad se requieren dos cuentas independientes y evidencia PostgreSQL de
idempotencia y liquidación exactamente una vez.

## Estructura

```text
android/                         Aplicación Android, Room y Compose
supabase/migrations/             Esquema, RLS y RPCs autoritativos
supabase/functions/              Edge Functions
tests/                           Paridad, integración y gates de dominio
docs/                            Contratos, arquitectura, seguridad y releases
tools/                           Verificadores y utilidades de release
```

## Documentación principal

- [Visión de producto](docs/PRODUCT_VISION.md)
- [Reglas de producto](docs/PRODUCT_OS_ROADMAP.md)
- [Contrato de paridad TypeScript/Kotlin](docs/architecture/CROSS-RUNTIME-PARITY.md)
- [Constitución de seguridad](docs/safety/SAFETY_CONSTITUTION.md)
- [Especificación de recuperación de Viajes](docs/rides/MEET_RIDES_V9_APK_RECOVERY_SPEC.md)
- [Ruta vial y programación](docs/rides/ROAD_ROUTE_AND_SCHEDULING_2026-09-21.md)
- [Hardening de autoridad 4.26.1](docs/releases/2026-09-20-elysium-vanguard-ai-os-4.26.1-authority-hardening.md)
- [Comercio Local y Delivery Triangular V1](docs/commerce/MEET_LOCAL_COMMERCE_AND_DELIVERY_V1.md)
- [Release 4.27.0 — Comercio Local, Delivery y Hub de Servicios Activos](docs/releases/2026-09-25-elysium-vanguard-ai-os-4.27.0-commerce-and-active-services.md)

## Descarga y Releases

[**DESCARGAR ELYSIUM PARA ANDROID**](https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo/releases/download/v4.28.0/elysium-v4.28.0-android.apk)

Entrega 4.28.0 para pruebas internas. Consultar [release y SHA256SUMS](https://github.com/jordelmir/MEET-Mecanicos-Especialistas-En-Todo/releases/tag/v4.28.0) para comprobar publicación y archivos. La evidencia y límites de servicios/mensajes están en `android/docs/ascension/`.

## Límites de publicación

No publiques un `app-debug.apk` como artefacto de producción. Una entrega
oficial necesita un tag sobre el SHA final de `main`, una AAB/APK firmada con la
clave de producción, manifiesto, hashes, SBOM, provenance y los gates requeridos
en verde. Los secretos de Supabase y firma nunca pertenecen al repositorio.

## Licencia

MIT
