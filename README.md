# GPS Controller

An Android app that sets a fake GPS location for the whole phone. Pick a point on the map, press Start, and other apps see that point as the phone's current location.

It needs no root. It uses Android's official mock location feature, so it must be chosen as the mock location app in Developer options.

## Features

- Pick a location by tapping the map, typing coordinates (`12.9716, 77.5946`) or searching a place name.
- Start and stop the fake location; it keeps running in the background with a notification.
- Save named locations and switch between them with one tap.
- Route simulation: tap several points and the location travels between them at walking, cycling or driving speed.
- Floating joystick: move the location by hand while you are in another app.
- Feeds the GPS, network and Google fused location providers, so most apps pick it up.

## Install

Requires Android 8.0 (API 26) or newer.

1. Download [`apk/GpsController-0.3-debug.apk`](apk/GpsController-0.3-debug.apk) to your phone.
2. Open the file and allow installing from this source when Android asks.

## One-time setup

1. Turn on Developer options: **Settings > About phone**, then tap **Build number** 7 times.
2. Open **Settings > System > Developer options**.
3. Tap **Select mock location app** and choose **GPS Controller**.

The app shows a setup card with a shortcut to Developer options until this is done.

## Use

1. Open GPS Controller.
2. Tap the map, or search for a place or coordinates, to drop the pin.
3. Press **Start fake location** and allow the location and notification permissions.
4. Open any other app; it now sees the pinned location.
5. Press **Stop fake location** (in the app or on the notification) to return to your real location.

## Route simulation

1. Tap **Route**, then tap the map to add two or more points. **Undo** removes the last point and **Clear** removes them all.
2. Pick a speed: Walk 5, Cycle 15, Drive 40 or Fast 100 km/h.
3. Press **Start route**. The location starts at the first point and travels to each point in turn, then stays at the last one.
4. Turn on **Loop** to go back to the first point and repeat.

The route runs in straight lines between your points; it does not follow roads. Add more points to trace a road.

## Joystick

1. Tap **Joystick**. The first time, Android asks for **Display over other apps**; allow it and tap **Joystick** again.
2. Start the fake location. A joystick appears on screen and stays there when you switch to other apps.
3. Push the knob to move: up is north, right is east. Pushing further moves faster, up to the chosen speed.
4. Drag the **move** bar to reposition the joystick. Tap **Joystick** again in the app to hide it.

While a route is running, the joystick takes over when you hold it and the route carries on when you let go.

## JakshyaOS (system mode)

On [JakshyaOS](https://github.com/venkateshguggilla/JakshyaOS) the app is preinstalled and the OS trusts it, so it works without root and without Developer options:

- No one-time setup: the app can set the location as soon as it is opened.
- The OS does not flag its locations as mock, so other apps see them as ordinary GPS and network locations.
- The app leaves Google's fused provider alone there (Play services always flags its own mock locations) and lets it follow the GPS and network providers.

This needs the JakshyaOS location-service patch and the `ro.jakshya.mock_location_package` property; on any other OS the app behaves as described above. The system-image copy is the release build (`./gradlew assembleRelease`).

## Limits

- Apps can tell the location is mocked. Some banking, payment, ride and delivery apps and some games refuse mock locations.
- It does not change IP-based location. Websites can still estimate your city from your IP address; that needs a VPN.
- Faking your location can break the terms of service of other apps. Use it for testing, development and privacy.

## Build from source

Requires Android Studio (or JDK 17+ and the Android SDK with platform 37).

```
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## How it works

- `MockLocationEngine` registers test providers for GPS and network through `LocationManager`, and turns on mock mode for the fused location provider.
- `MockLocationService` is a foreground service that pushes the position twice a second and moves it along the route or by the joystick.
- `JoystickOverlay` is the floating joystick window.
- `MainActivity` is the Compose UI with an OpenStreetMap map (osmdroid).

Map data © OpenStreetMap contributors.
