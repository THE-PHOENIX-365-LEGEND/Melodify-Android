# Melodify

Melodify is a modern, lightweight, privacy-focused Android music streaming app built with Kotlin. It offers an ad-free listening experience, a vibrant Spotify Green & Deep Dark UI theme, and seamless playback without requiring an account.

## Features

- **Ad-Free & Privacy-First**: Zero ads, no account registration required, no telemetry, and zero data tracking.
- **Spotify Green & OLED Deep Dark Theme**: Beautiful, vibrant UI with Spotify Green accents, sleek vector branding, and light, dark, or system OLED deep dark theme support.
- **10-Band Built-in Equalizer**: Custom 10-band Equalizer featuring live EQ response curves (`EqCurveView`), custom band sliders, and built-in preset audio profiles (Bass Boost, Vocal Booster, Acoustic, Rock, Pop, etc.).
- **Smart Recommendation Engine**: Intelligent algorithmic recommendation system that automatically cues similar, non-repeating tracks based on your listening history and preferences.
- **Dual-Source Synchronized Lyrics**: Dynamic LRC synchronized lyrics powered by JioSaavn and LRCLIB integrations with live line tracking and tap-to-toggle view.
- **Multi-Format Downloads & ID3 Metadata Tagging**: Download tracks in MP3, M4A, or OGG formats up to 320kbps / Lossless quality, complete with embedded album artwork and ID3 metadata via JAudioTagger.
- **Dynamic Home Screen**: Time-based personalized greetings ("Good Morning / Afternoon / Evening"), 6-card quick access grid, trending songs, popular playlists, top artists, and bottom navigation bar (Home, Search, Library).
- **Smart Floating Mini-Player & Queue**: Persistent mini-player with live playback progress bar, album art swipe gestures, drag-and-drop queue reordering, and smart shuffle algorithms.
- **In-Playlist & Global Search**: Instant global search across songs, albums, artists, playlists, and lyrics with recent query chips and real-time live search inside playlist and album pages.
- **Library Management & JSON Backup**: Create personalized playlists, save favorite tracks and albums, with full JSON **Export & Import** backup and restore support.
- **Listening History & Analytics**: Track recently played songs and albums with history views and listening analytics.
- **Interactive Home Screen Widget**: App widget for the Android home screen featuring real-time album artwork display and player control buttons.
- **Sleep Timer**: Built-in sleep timer to automatically pause playback after a configured duration.
- **Deep Link Integration**: Direct link handling for opening JioSaavn and Melodify track, album, and playlist URLs directly inside the app.
- **In-App Auto Updates**: Built-in release checker to automatically notify and update the app directly from GitHub releases.
- **Background Playback & Quality Controls**: Customizable streaming/download audio quality options (Lossless down to 12kbps) and background playback persistence settings.

## Getting Started

Follow these instructions to get a copy of the project up and running on your local machine for development and testing.

### Prerequisites

* Android Studio (latest version recommended)
* Java 17+
* Kotlin 2.4.0
* An Android device or emulator

## Built With

*   [Java 17](https://openjdk.org/projects/jdk/17/) & [Kotlin 2.4.0](https://kotlinlang.org/docs/whatsnew24.html) - The programming language used.
*   [saavn.sumit.co](https://github.com/sumitkolhe/jiosaavn-api) - The unofficial JioSaavn API.
*   [Glide](https://github.com/bumptech/glide) - An image loading and caching library.
*   [Picasso](https://github.com/square/picasso) - A powerful image downloading and caching library.
*   [Gson](https://github.com/google/gson) - A Java library to convert Java Objects into their JSON representation.
*   [OkHttp](https://github.com/square/okhttp) - An HTTP client for Android and Java applications.
*   [SwipeRefreshLayout](https://developer.android.com/jetpack/androidx/releases/swiperefreshlayout) - Provides a "pull-to-refresh" gesture.
*   [Shimmer](https://github.com/facebookarchive/shimmer-android) - An Android library that provides an easy way to add a shimmer effect to any view.
*   [Overscroll-decor](https://github.com/EverythingMe/overscroll-decor) - An Android library for an iOS-like over-scrolling effect.
*   [SSP](https://github.com/intuit/ssp) & [SDP](https://github.com/intuit/sdp) - Libraries for responsive UI dimensions.
*   [JAudioTagger](https://github.com/RouHim/jaudiotagger) - A Java library for reading and writing audio metadata.

## Special Thanks

- Special thanks to [Harsh Shah (SaavnMp3-Android)](https://github.com/Harshshah6/SaavnMp3-Android?hl=en-IN) for the base project foundation.
- Featured on [PlayBook](https://github.com/Goodwy/PlayBook).

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
