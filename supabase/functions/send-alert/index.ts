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

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

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
    const personName = payload.personName?.trim() ?? "Persona monitoreada";

    if (channel !== "sms" && channel !== "whatsapp") {
      return json({ error: "channel must be sms or whatsapp" }, 400);
    }
    if (eventType !== "test" && eventType !== "heart_rate_low" && eventType !== "oxygen_low") {
      return json({ error: "Unsupported eventType" }, 400);
    }
    if (!/^\+[1-9][0-9]{7,14}$/.test(to)) {
      return json({ error: "Destination must use E.164 format, e.g. +56912345678" }, 400);
    }

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
      return json({ error: "Messaging provider rejected the request", providerStatus: twilioResponse.status }, 502);
    }

    const sid = typeof twilioBody === "object" && twilioBody !== null ? twilioBody.sid : undefined;
    const status = typeof twilioBody === "object" && twilioBody !== null ? twilioBody.status : undefined;
    return json({ ok: true, channel, sid, status });
  } catch (error) {
    console.error("send-alert failed", error);
    return json({ error: error instanceof Error ? error.message : "Unexpected error" }, 500);
  }
});
