// Grab share-link resolution + order tracking.
//
// VERIFIED (probe 2026-11-11):
//   GET https://app.grab.com/s/<code>
//     -> 302 Location: https://app.grab.com/open?screenType=GRABFOOD
//            &shareOrderLink=lgb0LF7dy7TL9NIyk5l5&...
//   shareOrderLink = "lgb" + <digit> + <prefix> + <10-char id>
//   The last 10 chars match the order id stored in the production DB
//   (shareId lgb0LF7dy7TL9NIyk5l5 -> order id TL9NIyk5l5).
//
// EXTENSION POINT (not yet reverse-engineered):
//   The live tracking page https://sharelocation.grab.com/o/<shareId> is a JS app
//   (S3-hosted, /static/js/app.*.js) that pulls driver/ETA/route data from an
//   internal Grab API. Implement fetchTrackingData() below to restore live
//   tracking; the poller in server.mjs calls it for every active order.
import crypto from 'node:crypto';

const UA =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1';

/**
 * Resolve a Grab share link (https://app.grab.com/s/<code>) to { shareId, orderId }.
 * Returns null when the link does not resolve (not a share link / expired).
 */
export async function resolveShareLink(link) {
  try {
    const res = await fetch(link, {
      redirect: 'manual',
      headers: { 'user-agent': UA },
      signal: AbortSignal.timeout(15000),
    });
    const location = res.headers.get('location');
    if (!location) return null;
    const u = new URL(location);
    const shareId = u.searchParams.get('shareOrderLink');
    if (!shareId || shareId.length < 13) return null;
    return { shareId, orderId: shareId.slice(-10) };
  } catch {
    return null;
  }
}

/**
 * Fetch current tracking data for a resolved shareId.
 *
 * EXTENSION POINT — return null (nothing to do) or an object with any of:
 *   { merchant, booking_code, status, status_message, eta_seconds,
 *     driver_name, driver_vehicle, driver_plate, driver_rating, driver_photo,
 *     driver_lat, driver_lng, dest_lat, dest_lng, dest_text,
 *     route: [[lat,lng],...], traffic: [n,...], multi_stop: 0|1,
 *     complete_time, dropoff_time, driver_arrival_time }
 * Status values used by the frontend:
 *   prepare | allocated | ontheway | completed | delivered | cancelled | expired
 */
export async function fetchTrackingData(shareId) {
  // Intentionally not implemented yet: the internal Grab tracking API behind
  // sharelocation.grab.com has not been reverse-engineered.
  return null;
}

/** 10-char url-safe id, same alphabet as production order ids (A-Za-z0-9). */
export function newOrderId() {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  const bytes = crypto.randomBytes(10);
  let out = '';
  for (let i = 0; i < 10; i++) out += alphabet[bytes[i] % alphabet.length];
  return out;
}
