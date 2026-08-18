create table if not exists public.alert_deliveries (
    id uuid primary key default gen_random_uuid(),
    created_at timestamptz not null default now(),
    person_name text not null default 'Persona monitoreada',
    event_type text not null check (event_type in ('test', 'heart_rate_low', 'oxygen_low')),
    channel text not null check (channel in ('sms', 'whatsapp')),
    destination_last4 text,
    latest_value numeric,
    minimum_value numeric,
    duration_minutes integer,
    delivery_status text not null check (delivery_status in ('provider_accepted', 'provider_rejected', 'server_error')),
    provider_http_status integer,
    provider_sid text,
    provider_message_status text,
    error_message text
);

create index if not exists alert_deliveries_created_at_idx
    on public.alert_deliveries (created_at desc);

create index if not exists alert_deliveries_event_type_idx
    on public.alert_deliveries (event_type, created_at desc);

alter table public.alert_deliveries enable row level security;

comment on table public.alert_deliveries is
    'Audit log for Health Guard remote alert delivery attempts. No public RLS policies are defined; writes are performed by the Edge Function with the service role.';
