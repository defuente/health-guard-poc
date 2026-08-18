# Entrega remota de alertas

Health Guard v0.4 no usa `SEND_SMS`. La app envía el evento por HTTPS a un backend y el backend entrega el mensaje mediante Twilio SMS o WhatsApp.

## Flujo

`Smart Band -> Mi Fitness -> Health Connect -> Health Guard -> HTTPS -> Supabase Edge Function -> Twilio -> SMS/WhatsApp`

## Proyecto Supabase de la POC

Proyecto: `health-guard`

Project ref: `apxssxmbpozqbdbhdnle`

Endpoint desplegado:

`https://apxssxmbpozqbdbhdnle.supabase.co/functions/v1/send-alert`

La Edge Function `send-alert` ya está desplegada con `verify_jwt = false` porque implementa autenticación propia mediante `Authorization: Bearer <HEALTH_GUARD_DEVICE_TOKEN>`.

## Código de la Edge Function

El código versionado está en:

`supabase/functions/send-alert/index.ts`

La función:

- valida el token del dispositivo;
- valida canal, tipo de evento y teléfono E.164;
- construye el mensaje de FC/SpO₂;
- entrega el mensaje mediante Twilio;
- registra auditoría en `public.alert_deliveries`;
- guarda solo los últimos 4 dígitos del destino en el registro de auditoría.

## Auditoría

La migración está versionada en:

`supabase/migrations/20260818133500_create_alert_deliveries.sql`

La tabla `public.alert_deliveries` tiene RLS habilitado y no posee políticas públicas intencionalmente. Las escrituras se realizan desde la Edge Function usando el service role interno de Supabase.

## Secretos requeridos

Configura estos secretos únicamente en el backend; nunca los agregues al APK ni al repositorio:

- `HEALTH_GUARD_DEVICE_TOKEN`: token aleatorio compartido con el teléfono de la POC.
- `TWILIO_ACCOUNT_SID`: Account SID de Twilio.
- `TWILIO_AUTH_TOKEN`: Auth Token de Twilio.
- `TWILIO_SMS_FROM`: número/sender habilitado para SMS, en formato E.164 cuando corresponda.
- `TWILIO_WHATSAPP_FROM`: sender de WhatsApp; para pruebas puede ser el sender del Twilio Sandbox.

Ejemplo con Supabase CLI:

```bash
supabase secrets set \
  HEALTH_GUARD_DEVICE_TOKEN="un-token-largo-y-aleatorio" \
  TWILIO_ACCOUNT_SID="AC..." \
  TWILIO_AUTH_TOKEN="..." \
  TWILIO_SMS_FROM="+1..." \
  TWILIO_WHATSAPP_FROM="+14155238886" \
  --project-ref apxssxmbpozqbdbhdnle
```

También puedes cargarlos desde el dashboard de Supabase en la configuración de Edge Functions/Secrets del proyecto `health-guard`.

## Configuración en Android

La URL del backend queda precargada en la app. Completa:

- nombre de la persona monitoreada;
- teléfono destino en formato internacional, por ejemplo `+56912345678`;
- canal `SMS` o `WhatsApp`;
- el mismo valor de `HEALTH_GUARD_DEVICE_TOKEN`;
- activa `Enviar alertas automáticamente`.

Luego usa **Enviar alerta de prueba** antes de iniciar el monitoreo nocturno.

## Seguridad de la POC

El token del dispositivo limita quién puede llamar la función, pero sigue siendo una credencial almacenada en el dispositivo y podría extraerse de un teléfono comprometido. Para una versión productiva se recomienda autenticar usuarios/dispositivos, rotar credenciales, aplicar rate limiting y no permitir destinos arbitrarios sin autorización previa.

## WhatsApp

WhatsApp requiere un sender habilitado por el proveedor. Para desarrollo, Twilio ofrece un Sandbox; para producción se debe completar el onboarding/registro correspondiente del canal de WhatsApp Business.
