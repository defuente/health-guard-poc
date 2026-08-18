# Entrega remota de alertas

Health Guard v0.4 no usa `SEND_SMS`. La app envía el evento por HTTPS a un backend y el backend entrega el mensaje mediante Twilio SMS o WhatsApp.

## Flujo

`Smart Band -> Mi Fitness -> Health Connect -> Health Guard -> HTTPS -> Supabase Edge Function -> Twilio -> SMS/WhatsApp`

## Edge Function

El código está en:

`supabase/functions/send-alert/index.ts`

Despliega la función con el nombre `send-alert`.

La función implementa autenticación propia mediante un token del dispositivo, por lo que al desplegar en Supabase debe configurarse con `verify_jwt = false`. La función valida el header `Authorization: Bearer <HEALTH_GUARD_DEVICE_TOKEN>` antes de aceptar una solicitud.

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
  --project-ref <PROJECT_REF>
```

## Configuración en Android

En Health Guard completa:

- nombre de la persona monitoreada;
- teléfono destino en formato internacional, por ejemplo `+56912345678`;
- canal `SMS` o `WhatsApp`;
- URL de la función, por ejemplo `https://<PROJECT_REF>.supabase.co/functions/v1/send-alert`;
- el mismo valor de `HEALTH_GUARD_DEVICE_TOKEN`;
- activa `Enviar alertas automáticamente`.

Luego usa **Enviar alerta de prueba** antes de iniciar el monitoreo nocturno.

## Seguridad de la POC

El token del dispositivo limita quién puede llamar la función, pero sigue siendo una credencial almacenada en el dispositivo y podría extraerse de un teléfono comprometido. Para una versión productiva se recomienda autenticar usuarios/dispositivos, rotar credenciales, registrar eventos, aplicar rate limiting y no permitir destinos arbitrarios sin autorización previa.

## WhatsApp

WhatsApp requiere un sender habilitado por el proveedor. Para desarrollo, Twilio ofrece un Sandbox; para producción se debe completar el onboarding/registro correspondiente del canal de WhatsApp Business.
