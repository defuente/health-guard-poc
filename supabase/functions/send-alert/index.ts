const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type AlertChannel = "sms" | "whatsapp";
type EventType = "test" | "heart_rate_low" | "oxygen_low";

type AlertRequest = {
  channel?: AlertChannel;
  to?: string;
  personName?: string;
  eventType?: EventType;
  latestValue?: number;
  minimumValue?: number;
  durationMinutes?: number;
};

type DeliveryAudit = {
  person_name: string;
  event_type: EventType;
  channel: AlertChannel;
  destination_last4: string;
  latest_value?: number;
  minimum_value?: number;
  duration_minutes?: number;
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8" },
  });
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`Missing server secret: ${name}`);
  return value;
}

function normalizeWhatsAppAddress(value: string): string {
  return value.startsWith("whatsapp:") ? value : `whatsapp:${value}`;
}

function formatNumber(value: number | undefined): string {
  return typeof value === "number" && Number.isFinite(value) ? Math.round(value).toString() : "?";
}

function buildMessage(payload: Required<Pick<AlertRequest, "channel" | "to" | "personName" | "eventType">> & AlertRequest): string {
  const person = payload.personName.trim().slice(0, 50) || "Persona monitoreada";

  switch (payload.eventType) {
    case "test":
      return `Health Guard: alerta de prueba para ${person}. Canal ${payload.channel.toUpperCase()} funcionando correctamente.`;
    case "heart_rate_low":
      return `Health Guard ALERTA - ${person}: FC baja ${formatNumber(payload.latestValue)} BPM (mínima ${formatNumber(payload.minimumValue)}) durante ${Math.max(0, Math.round(payload.durationMinutes ?? 0))} min. Verifica su estado.`;
    case "oxygen_low":
      return `Health Guard ALERTA - ${person}: SpO2 baja ${formatNumber(payload.latestValue)}% (mínima ${formatNumber(payload.minimumValue)}%) durante ${Math.max(0, Math.round(payload.durationMinutes ?? 0))} min. Verifica su estado.`;
  }
}

async function logDelivery(
  audit: DeliveryAudit,
  result: {
    delivery_status: "provider_accepted" | "provider_rejected" | "server_error";
    provider_http_status?: number;
    provider_sid?: string;
    provider_message_status?: string;
    error_message?: string;
  },
): Promise<void> {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();

  if (!supabaseUrl || !serviceRoleKey) {
    console.error("Audit log unavailable: missing Supabase service environment");
    return;
  }

  const response = await fetch(`${supabaseUrl}/rest/v1/alert_deliveries`, {
    method: "POST",
    headers: {
      apikey: serviceRoleKey,
      Authorization: `Bearer ${serviceRoleKey}`,
      "Content-Type": "application/json",
      Prefer: "return=minimal",
    },
    body: JSON.stringify({ ...audit, ...result }),
  });

  if (!response.ok) {
    console.error("Failed to persist alert delivery audit", response.status, await response.text());
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  let audit: DeliveryAudit | null = null;

  try {
    const expectedDeviceToken = requiredEnv("HEALTH_GUARD_DEVICE_TOKEN");
    const authorization = req.headers.get("Authorization") ?? "";
    if (authorization !== `Bearer ${expectedDeviceToken}`) {
      return json({ error: "Unauthorized" }, 401);
    }

    const payload = (await req.json()) as AlertRequest;
    const channel = payload.channel;
    const eventType = payload.eventType;
    const to = payload.to?.trim() ?? "";
    const personName = payload.personName?.trim().slice(0, 50) || "Persona monitoreada";

    if (channel !== "sms" && channel !== "whatsapp") {
      return json({ error: "channel must be sms or whatsapp" }, 400);
    }
    if (eventType !== "test" && eventType !== "heart_rate_low" && eventType !== "oxygen_low") {
      return json({ error: "Unsupported eventType" }, 400);
    }
    if (!/^\+[1-9][0-9]{7,14}$/.test(to)) {
      return json({ error: "Destination must use E.164 format, e.g. +56912345678" }, 400);
    }

    audit = {
      person_name: personName,
      event_type: eventType,
      channel,
      destination_last4: to.slice(-4),
      ...(typeof payload.latestValue === "number" ? { latest_value: payload.latestValue } : {}),
      ...(typeof payload.minimumValue === "number" ? { minimum_value: payload.minimumValue } : {}),
      ...(typeof payload.durationMinutes === "number"
        ? { duration_minutes: Math.max(0, Math.round(payload.durationMinutes)) }
        : {}),
    };

    const accountSid = requiredEnv("TWILIO_ACCOUNT_SID");
    const authToken = requiredEnv("TWILIO_AUTH_TOKEN");
    const from = channel === "sms"
      ? requiredEnv("TWILIO_SMS_FROM")
      : normalizeWhatsAppAddress(requiredEnv("TWILIO_WHATSAPP_FROM"));
    const destination = channel === "sms" ? to : normalizeWhatsAppAddress(to);

    const message = buildMessage({ ...payload, channel, to, personName, eventType });
    const form = new URLSearchParams({
      To: destination,
      From: from,
      Body: message,
    });

    const twilioResponse = await fetch(
      `https://api.twilio.com/2010-04-01/Accounts/${encodeURIComponent(accountSid)}/Messages.json`,
      {
        method: "POST",
        headers: {
          Authorization: `Basic ${btoa(`${accountSid}:${authToken}`)}`,
          "Content-Type": "application/x-www-form-urlencoded",
        },
        body: form.toString(),
      },
    );

    const twilioText = await twilioResponse.text();
    let twilioBody: Record<string, unknown> | string = twilioText;
    try {
      twilioBody = JSON.parse(twilioText) as Record<string, unknown>;
    } catch {
      // Keep raw response when Twilio does not return JSON.
    }

    if (!twilioResponse.ok) {
      console.error("Twilio delivery failed", twilioResponse.status, twilioBody);
      await logDelivery(audit, {
        delivery_status: "provider_rejected",
        provider_http_status: twilioResponse.status,
        error_message: "Messaging provider rejected the request",
      });
      return json({ error: "Messaging provider rejected the request", providerStatus: twilioResponse.status }, 502);
    }

    const sid = typeof twilioBody === "object" && twilioBody !== null && typeof twilioBody.sid === "string"
      ? twilioBody.sid
      : undefined;
    const status = typeof twilioBody === "object" && twilioBody !== null && typeof twilioBody.status === "string"
      ? twilioBody.status
      : undefined;

    await logDelivery(audit, {
      delivery_status: "provider_accepted",
      provider_http_status: twilioResponse.status,
      ...(sid ? { provider_sid: sid } : {}),
      ...(status ? { provider_message_status: status } : {}),
    });

    return json({ ok: true, channel, sid, status });
  } catch (error) {
    console.error("send-alert failed", error);
    if (audit) {
      await logDelivery(audit, {
        delivery_status: "server_error",
        error_message: error instanceof Error ? error.message.slice(0, 250) : "Unexpected error",
      });
    }
    return json({ error: error instanceof Error ? error.message : "Unexpected error" }, 500);
  }
});
