# Health Guard POC

POC Android para monitoreo familiar con datos disponibles en Health Connect y alertas remotas.

## Flujo validado

`Xiaomi Smart Band 8 -> Mi Fitness -> Health Connect -> Health Guard`

La POC ya valida lectura real de:

- frecuencia cardíaca (`HeartRateRecord`);
- saturación de oxígeno SpO₂ (`OxygenSaturationRecord`);
- lectura en segundo plano;
- monitoreo nocturno mediante foreground service.

Health Guard no es un dispositivo médico. Los umbrales configurables son para pruebas y no constituyen recomendaciones clínicas.

## v0.4

La versión 0.4 elimina el envío directo de SMS desde Android. La entrega remota usa:

`Health Guard -> HTTPS -> backend -> proveedor de mensajería -> familiar`

La app soporta como canales configurables:

- SMS;
- WhatsApp.

El backend de referencia está en:

`supabase/functions/send-alert/index.ts`

La función usa Twilio Programmable Messaging y guarda todas las credenciales sensibles del proveedor en secretos del backend, nunca en el APK.

Consulta `docs/remote-alert-delivery.md` para el despliegue y configuración.

## Funciones actuales

- lectura de frecuencia cardíaca y SpO₂ desde Health Connect;
- visualización de fuente y últimas muestras;
- reglas independientes para FC y SpO₂;
- protección contra datos antiguos y huecos excesivos entre muestras;
- simulación de FC baja y SpO₂ baja;
- monitoreo en segundo plano aproximadamente una vez por minuto;
- notificaciones locales de alerta y recuperación;
- una entrega remota por cada episodio nuevo;
- configuración de nombre, teléfono, canal, URL HTTPS y token del dispositivo;
- botón para enviar una alerta remota de prueba;
- GitHub Actions para tests y APK debug.

## Requisitos Android

- Android Studio reciente con soporte para AGP 9.3;
- JDK 17 o superior;
- Android SDK 37;
- teléfono Android con Health Connect disponible;
- Mi Fitness compartiendo los datos requeridos con Health Connect.

El proyecto usa Android Gradle Plugin 9.3.0, Gradle 9.5.0, Compose BOM 2026.06.00 y Health Connect 1.1.0.

## Compilar en Windows

La primera vez:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap-gradle.ps1
```

Luego:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

El APK queda en:

`app\build\outputs\apk\debug\app-debug.apk`

## Configuración de Health Connect

1. Vincula la Smart Band con Mi Fitness.
2. Activa la sincronización de frecuencia cardíaca y oxígeno cuando esté disponible.
3. Instala Health Guard.
4. Pulsa **Solicitar acceso**.
5. Autoriza frecuencia cardíaca, SpO₂ y lectura en segundo plano.
6. Pulsa **Actualizar** y verifica que la fuente sea Mi Fitness/Xiaomi.

## Alertas remotas

Para habilitarlas necesitas un backend desplegado. En la app configura:

- nombre de la persona monitoreada;
- teléfono destino en formato E.164, por ejemplo `+56912345678`;
- SMS o WhatsApp;
- URL HTTPS del endpoint;
- token del dispositivo;
- switch **Enviar alertas automáticamente**.

Usa **Enviar alerta de prueba** antes de iniciar el monitoreo nocturno.

## Próximas etapas

- desplegar un proyecto Supabase independiente para Health Guard;
- configurar Twilio y probar SMS/WhatsApp reales;
- registrar historial de episodios y entregas;
- agregar confirmación de recepción por un familiar;
- reemplazar el token compartido por autenticación de usuario/dispositivo;
- agregar rate limiting y destinos familiares previamente autorizados.
