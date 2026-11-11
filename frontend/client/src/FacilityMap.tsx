import L from "leaflet";
import { useEffect, useMemo } from "react";
import {
  Circle,
  CircleMarker,
  MapContainer,
  Marker,
  Popup,
  TileLayer,
  useMap,
  useMapEvents,
} from "react-leaflet";
import type { ParkingSlot } from "./types";

const defaultCenter: [number, number] = [20.5937, 78.9629];

L.Icon.Default.mergeOptions({
  iconUrl: new URL("leaflet/dist/images/marker-icon.png", import.meta.url).href,
  iconRetinaUrl: new URL("leaflet/dist/images/marker-icon-2x.png", import.meta.url).href,
  shadowUrl: new URL("leaflet/dist/images/marker-shadow.png", import.meta.url).href,
});

type Coordinates = { latitude: number; longitude: number };

function MapCenter({ center, zoom }: { center: [number, number]; zoom: number }) {
  const map = useMap();
  useEffect(() => {
    map.setView(center, zoom);
  }, [center, map, zoom]);
  return null;
}

function FitFacilityMarkers({
  points,
  active,
}: {
  points: [number, number][];
  active: boolean;
}) {
  const map = useMap();
  useEffect(() => {
    if (active || points.length === 0) return;
    if (points.length === 1) {
      map.setView(points[0], 14);
      return;
    }
    map.fitBounds(L.latLngBounds(points), { padding: [28, 28], maxZoom: 14 });
  }, [active, map, points]);
  return null;
}

function MapClick({ onPick }: { onPick: (coordinates: Coordinates) => void }) {
  useMapEvents({
    click(event) {
      onPick({ latitude: event.latlng.lat, longitude: event.latlng.lng });
    },
  });
  return null;
}

export function FacilityLocationPicker({
  coordinates,
  onPick,
}: {
  coordinates: Coordinates | null;
  onPick: (coordinates: Coordinates) => void;
}) {
  const center: [number, number] = coordinates
    ? [coordinates.latitude, coordinates.longitude]
    : defaultCenter;

  return (
    <div className="facility-map-picker">
      <p className="panel-description">Click the map to place or move your facility pin. You can also drag the pin to fine-tune its position.</p>
      <MapContainer center={center} zoom={coordinates ? 15 : 5} scrollWheelZoom className="facility-map">
        <MapCenter center={center} zoom={coordinates ? 15 : 5} />
        <MapClick onPick={onPick} />
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          maxZoom={19}
        />
        {coordinates && (
          <Marker
            position={[coordinates.latitude, coordinates.longitude]}
            draggable
            eventHandlers={{
              dragend(event) {
                const marker = event.target as L.Marker;
                const point = marker.getLatLng();
                onPick({ latitude: point.lat, longitude: point.lng });
              },
            }}
          >
            <Popup>Facility location</Popup>
          </Marker>
        )}
      </MapContainer>
    </div>
  );
}

interface FacilityGroup {
  key: string;
  coordinates: [number, number];
  name: string;
  address: string;
  slots: ParkingSlot[];
}

export function AvailableFacilitiesMap({
  slots,
  searchCenter,
  radiusKm,
  onSelect,
}: {
  slots: ParkingSlot[];
  searchCenter: Coordinates | null;
  radiusKm: number | null;
  onSelect: (slot: ParkingSlot) => void;
}) {
  const facilities = useMemo(() => {
    const groups = new Map<string, FacilityGroup>();
    for (const slot of slots) {
      if (slot.latitude == null || slot.longitude == null) continue;
      const key = slot.facilityId == null
        ? `${slot.latitude.toFixed(5)},${slot.longitude.toFixed(5)}`
        : String(slot.facilityId);
      const group = groups.get(key);
      if (group) {
        group.slots.push(slot);
      } else {
        groups.set(key, {
          key,
          coordinates: [slot.latitude, slot.longitude],
          name: slot.facilityName || "Parking facility",
          address: slot.facilityAddress || "",
          slots: [slot],
        });
      }
    }
    return [...groups.values()];
  }, [slots]);

  const center: [number, number] = searchCenter
    ? [searchCenter.latitude, searchCenter.longitude]
    : facilities[0]?.coordinates || defaultCenter;
  const points = useMemo(
    () => facilities.map((facility) => facility.coordinates),
    [facilities],
  );
  const mapZoom = searchCenter
    ? radiusKm == null || radiusKm <= 1 ? 14
      : radiusKm <= 5 ? 12
        : radiusKm <= 20 ? 10
          : radiusKm <= 75 ? 8 : 6
    : 13;

  if (!facilities.length) {
    return (
      <div className="map-unavailable">
        <p>Map pins are not available for these results yet. Facility owners can add coordinates in their facility profile.</p>
      </div>
    );
  }

  return (
    <div className="facility-map-results">
      <MapContainer center={center} zoom={searchCenter ? 12 : 13} scrollWheelZoom className="facility-map">
        <MapCenter center={center} zoom={mapZoom} />
        <FitFacilityMarkers points={points} active={searchCenter !== null} />
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          maxZoom={19}
        />
        {searchCenter && radiusKm != null && (
          <Circle
            center={center}
            radius={radiusKm * 1000}
            pathOptions={{ color: "#9a77ff", fillColor: "#9a77ff", fillOpacity: 0.08 }}
          />
        )}
        {searchCenter && (
          <CircleMarker
            center={center}
            radius={6}
            pathOptions={{ color: "#ffffff", weight: 2, fillColor: "#4b8ff5", fillOpacity: 1 }}
          >
            <Popup>Your search location</Popup>
          </CircleMarker>
        )}
        {facilities.map((facility) => (
          <Marker key={facility.key} position={facility.coordinates}>
            <Popup>
              <strong>{facility.name}</strong>
              {facility.address && <div>{facility.address}</div>}
              <div>{facility.slots.length} available {facility.slots.length === 1 ? "space" : "spaces"}</div>
              <div className="map-popup-slots">
                {facility.slots.slice(0, 5).map((slot) => (
                  <button type="button" key={slot.id} onClick={() => onSelect(slot)}>
                    Choose {slot.slotNumber}
                  </button>
                ))}
                {facility.slots.length > 5 && <span>and {facility.slots.length - 5} more</span>}
              </div>
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
}
